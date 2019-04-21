package net.samvankooten.finnstickers.editor;

import android.app.Activity;
import android.graphics.Bitmap;
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

import androidx.annotation.Nullable;

public class EditorActivity extends Activity {
    public static final String TAG = "EditorActivity";
    public static final String PACK_NAME = "packname";
    public static final String STICKER_POSITION = "position";
    
    private StickerPack pack;
    private Sticker sticker;
    private ImageView image;
    private DraggableTextManager draggableTextManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);
        
        String packName = getIntent().getStringExtra(PACK_NAME);
        int pos = getIntent().getIntExtra(STICKER_POSITION, -1);
        pack = StickerPackRepository.getInstalledOrCachedPackByName(packName, this);
        if (pack == null) {
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
    
        image = findViewById(R.id.main_image);
        GlideApp.with(this).load(sticker.getCurrentLocation())
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        return false; }
                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        image.postDelayed(() ->
                                draggableTextManager.setImageBounds(
                                        image.getTop(), image.getBottom(),
                                        image.getLeft(), image.getRight()), 20);
                        return false;
                    }
                })
                .into(image);
        
        findViewById(R.id.snap).setOnClickListener(view -> {
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), Uri.parse(sticker.getCurrentLocation()));
                Bitmap outBitmap = draggableTextManager.render(bitmap, 500, 500);
                GlideApp.with(this).load(outBitmap).into(image);
            } catch (Exception e) {
                Log.e(TAG, "hi", e);
            }});
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
