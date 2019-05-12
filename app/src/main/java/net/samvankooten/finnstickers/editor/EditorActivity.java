package net.samvankooten.finnstickers.editor;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.snackbar.Snackbar;

import net.samvankooten.finnstickers.R;
import net.samvankooten.finnstickers.Sticker;
import net.samvankooten.finnstickers.StickerPack;
import net.samvankooten.finnstickers.misc_classes.GlideApp;
import net.samvankooten.finnstickers.utils.StickerPackRepository;

import org.json.JSONException;
import org.json.JSONObject;

import androidx.annotation.Nullable;

public class EditorActivity extends Activity {
    public static final String TAG = "EditorActivity";
    public static final String PACK_NAME = "packname";
    public static final String STICKER_POSITION = "position";
    private static final String PERSISTED_TEXT = "textObjects";
    
    private StickerPack pack;
    private Sticker sticker;
    private ImageView imageView;
    private ImageView deleteButton;
    private DraggableTextManager draggableTextManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);
        
        String packName = getIntent().getStringExtra(PACK_NAME);
        int pos = getIntent().getIntExtra(STICKER_POSITION, -1);
        pack = StickerPackRepository.getInstalledOrCachedPackByName(packName, this);
        if (pack == null || pos < 0) {
            Log.e(TAG, "Error loading pack " + packName);
            Snackbar.make(findViewById(R.id.main_view), getString(R.string.unexpected_error),
                    Snackbar.LENGTH_LONG).show();
            return;
        }
        
        sticker = pack.getStickers().get(pos);
        
        findViewById(R.id.back_icon).setOnClickListener(view -> onBackPressed());
        
        findViewById(R.id.add_text).setOnClickListener(view -> draggableTextManager.addText());
        
        draggableTextManager = findViewById(R.id.editing_container);
        
        setupKeyboardHandling();
    
        deleteButton = findViewById(R.id.delete_icon);
        deleteButton.setOnClickListener((v) -> draggableTextManager.deleteSelectedText());
        draggableTextManager.setOnStartEditCallback(this::onStartEditing);
        draggableTextManager.setOnStopEditCallback(this::onStopEditing);
    
        imageView = findViewById(R.id.main_image);
        GlideApp.with(this).load(sticker.getCurrentLocation())
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        return false; }
                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        int left = imageView.getLeft();
                        int right = imageView.getRight();
                        int top = imageView.getTop();
                        int bottom = imageView.getBottom();
                        final float viewRatio = (float) imageView.getWidth() / imageView.getHeight();
                        final float imageRatio = (float) resource.getIntrinsicWidth() / resource.getIntrinsicHeight();
                        
                        if (viewRatio > imageRatio) {
                            // e.g. landscape phone, square image
                            float excessWidth = imageView.getWidth() - imageRatio * imageView.getHeight();
                            left = (int) (excessWidth / 2);
                            right = imageView.getWidth() - (int) (excessWidth / 2);
                        } else if (viewRatio < imageRatio) {
                            // e.g. portrait phone, square image
                            float excessHeight = imageView.getHeight() - 1 / imageRatio * imageView.getWidth();
                            top = (int) (excessHeight / 2);
                            bottom = imageView.getHeight() - (int) (excessHeight / 2);
                        }
                        // viewRatio == imageRatio requires no changes
                        draggableTextManager.setImageBounds(top, bottom, left, right);
                        
                        if (savedInstanceState != null && savedInstanceState.containsKey(PERSISTED_TEXT)) {
                            try {
                                draggableTextManager.loadJSON(new JSONObject(savedInstanceState.getString(PERSISTED_TEXT)));
                            } catch (JSONException e) {
                                Log.e(TAG, "Error loading JSON", e);
                            }
                        }
                        return false;
                    }
                })
                .into(imageView);
        
        findViewById(R.id.snap).setOnClickListener(view -> render());
    }
    
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(PERSISTED_TEXT, draggableTextManager.toJSON().toString());
    }
    
    private void render() {
        int targetSize = 500;
        Bitmap bg;
        try {
            bg = MediaStore.Images.Media.getBitmap(getContentResolver(), Uri.parse(sticker.getCurrentLocation()));
        } catch (Exception e) {
            Log.e(TAG, "Error loading bitmap", e);
            return;
        }
        
        Bitmap text = DraggableTextManager.render(this, draggableTextManager.toJSON(), targetSize, targetSize);
        Bitmap result = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888);
        Canvas resultCanvas = new Canvas(result);
        Matrix matrix = new Matrix();
        matrix.setScale(
                (float) targetSize / bg.getWidth(),
                (float) targetSize / bg.getHeight());
        resultCanvas.drawBitmap(bg, matrix, null);
        resultCanvas.drawBitmap(text, 0, 0, null);
        GlideApp.with(this).load(result).into(imageView);
    }
    
    private void onStartEditing() {
        if (deleteButton != null) {
            deleteButton.setVisibility(View.VISIBLE);
            deleteButton.animate().alpha(1f).start();
        }
    }
    
    private void onStopEditing() {
        if (deleteButton != null)
            deleteButton.animate().alpha(0f)
                    .withEndAction(() -> deleteButton.setVisibility(View.GONE)).start();
    }
    
    @Override
    public void onBackPressed() {
        finish();
        overridePendingTransition(R.anim.no_fade, R.anim.fade_out);
    }
    
    /**
     * Enables (hacky?) recognition of keyboard appearance/disappearance, and offsets
     * draggableTextManager so that the selected text is visible above the keyboard.
     */
    private void setupKeyboardHandling() {
        final View decorView = getWindow().getDecorView();
        ViewTreeObserver.OnGlobalLayoutListener onGlobalLayoutListener = () -> {
            Rect r = new Rect();
            decorView.getWindowVisibleDisplayFrame(r);
            
            int height = decorView.getContext().getResources().getDisplayMetrics().heightPixels;
            int coveredByKeyboard = height - r.bottom;
            
            if (coveredByKeyboard > 0)
                draggableTextManager.notifyKeyboardShowing(r.bottom);
            else
                draggableTextManager.notifyKeyboardGone();
        };
        
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(onGlobalLayoutListener);
    }
}
