package net.samvankooten.finnstickers.editor;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
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
import net.samvankooten.finnstickers.StickerProvider;
import net.samvankooten.finnstickers.misc_classes.GlideApp;
import net.samvankooten.finnstickers.utils.StickerPackRepository;
import net.samvankooten.finnstickers.utils.Util;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.content.FileProvider;

public class EditorActivity extends Activity {
    public static final String TAG = "EditorActivity";
    public static final String PACK_NAME = "packname";
    public static final String STICKER_POSITION = "position";
    private static final String PERSISTED_TEXT = "textObjects";
    public static final String ADDED_STICKER_URI = "addedStickerUri";
    public static final int RESULT_STICKER_SAVED = 157;
    
    private StickerPack pack;
    private Sticker sticker;
    private ImageView imageView;
    private ImageView deleteButton;
    private ImageView sendButton;
    private ImageView saveButton;
    private DraggableTextManager draggableTextManager;
    private String baseImage;
    private String basePath;
    
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
        boolean stickerIsUnedited = sticker.getCustomTextData() == null;
        
        ImageView backButton = findViewById(R.id.back_icon);
        backButton.setOnClickListener(view -> onBackPressed());
        TooltipCompat.setTooltipText(backButton, getResources().getString(R.string.back_button));
        
        findViewById(R.id.add_text).setOnClickListener(view -> draggableTextManager.addText());
        
        draggableTextManager = findViewById(R.id.editing_container);
        draggableTextManager.setOnStartEditCallback(this::onStartEditing);
        draggableTextManager.setOnStopEditCallback(this::onStopEditing);
        
        setupKeyboardHandling();
    
        deleteButton = findViewById(R.id.delete_icon);
        deleteButton.setOnClickListener(v -> draggableTextManager.deleteSelectedText());
        TooltipCompat.setTooltipText(deleteButton, getResources().getString(R.string.delete_button));
    
        sendButton = findViewById(R.id.send_icon);
        sendButton.setOnClickListener(v -> send());
        TooltipCompat.setTooltipText(sendButton, getResources().getString(R.string.send_button));
    
        saveButton = findViewById(R.id.save_icon);
        saveButton.setOnClickListener(v -> save());
        TooltipCompat.setTooltipText(saveButton, getResources().getString(R.string.save_button));
    
        imageView = findViewById(R.id.main_image);
        if (stickerIsUnedited) {
            baseImage = sticker.getCurrentLocation();
            basePath = sticker.getRelativePath();
        } else {
            baseImage = Sticker.generateUri(packName, sticker.getCustomTextBaseImage()).toString();
            basePath = sticker.getCustomTextBaseImage();
        }
        GlideApp.with(this).load(baseImage)
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
                        } else if (!stickerIsUnedited) {
                            try {
                                draggableTextManager.loadJSON(new JSONObject(sticker.getCustomTextData()));
                            } catch (JSONException e) {
                                Log.e(TAG, "Error loading saved JSON", e);
                                Snackbar.make(findViewById(R.id.main_view), getString(R.string.unexpected_error),
                                        Snackbar.LENGTH_LONG).show();
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
    
    private void send() {
        File path = new File(getCacheDir(), "shared");
        path.mkdirs();
        File file = new File(path, "sent_sticker.jpg");
        
        boolean success = renderToFile(file);
        if (!success) {
            Snackbar.make(findViewById(R.id.main_view), getString(R.string.unexpected_error),
                    Snackbar.LENGTH_LONG).show();
            return;
        }
        
        Uri contentUri = FileProvider.getUriForFile(
                this, "net.samvankooten.finnstickers.fileprovider", file);
        if (contentUri != null) {
            Intent shareIntent = new Intent();
            shareIntent.setAction(Intent.ACTION_SEND);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            shareIntent.setDataAndType(contentUri, getContentResolver().getType(contentUri));
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            startActivity(
                    Intent.createChooser(shareIntent, getResources().getString(R.string.share_text)));
        }
    }
    
    private void save() {
        // Avoid double-taps
        saveButton.setOnClickListener(null);
        
        File relativePath = new File(pack.getPackBaseDir(), Util.USER_STICKERS_DIR);
        File absPath = new File(getFilesDir(), relativePath.toString());
        absPath.mkdirs();
        File file = new File(absPath, Util.generateUniqueFileName(relativePath.toString(), ".jpg"));
        File relativeName = new File(Util.USER_STICKERS_DIR, file.getName());
        
        boolean success = renderToFile(file);
        if (!success) {
            Snackbar.make(findViewById(R.id.main_view), getString(R.string.unexpected_error),
                    Snackbar.LENGTH_LONG).show();
            return;
        }
        
        List<String> keywords = new ArrayList<>(sticker.getKeywords());
        for (TextObject object : draggableTextManager.getTextObjects()) {
            if (object.getText() != null)
                for (String word : object.getText().toString().split("\\s")) {
                    if (!keywords.contains(word))
                        keywords.add(word);
                }
        }
        Sticker newSticker = new Sticker(relativeName.toString(), pack.getPackname(), keywords);
        newSticker.setCustomTextData(draggableTextManager.toJSON().toString());
        newSticker.setCustomTextBaseImage(basePath);
        
        pack.addSticker(newSticker, sticker, this);
        
        Intent data = new Intent();
        data.putExtra(ADDED_STICKER_URI, newSticker.getURI().toString());
        setResult(RESULT_STICKER_SAVED, data);
        onBackPressed();
    }
    
    private boolean renderToFile(File file) {
        Bitmap image = render();
        if (image == null)
            return false;
        return saveToFile(image, file);
    }
    
    public static boolean renderToFile(String baseImage, String packname, JSONObject textData, File file, Context context) {
        Bitmap bitmap = render(baseImage, packname, textData, context);
        return saveToFile(bitmap, file);
    }
    
    private static boolean saveToFile(Bitmap bitmap, File file) {
        try {
            FileOutputStream stream = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream);
            stream.close();
        } catch (IOException e) {
            Log.e(TAG, "Error saving sticker", e);
            return false;
        }
        return true;
    }
    
    private Bitmap render() {
        return render(baseImage, pack.getPackname(), draggableTextManager.toJSON(), this);
    }
    
    public static Bitmap render(String baseImage, String packname, JSONObject textData, Context context) {
        Bitmap bg;
        baseImage = makeUrlIfNeeded(baseImage, packname, context);
        if (Util.stringIsURL(baseImage)) {
            String suffix = baseImage.substring(baseImage.lastIndexOf('.'));
            try {
                File dest = new File(context.getCacheDir(), "rendering_base" + suffix);
                Util.downloadFile(new URL(baseImage), dest);
                baseImage = Uri.fromFile(dest).toString();
            } catch (IOException e) {
                Log.e(TAG, "Error downloading from URL " + baseImage, e);
                return null;
            }
        }
        try {
            bg = MediaStore.Images.Media.getBitmap(context.getContentResolver(), Uri.parse(baseImage));
        } catch (IOException e) {
            Log.e(TAG, "Error loading bitmap", e);
            return null;
        }
        final int targetWidth = bg.getWidth();
        final int targetHeight = bg.getHeight();
        
        Bitmap text = DraggableTextManager.render(context, textData, targetWidth, targetHeight);
        Bitmap result = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
        Canvas resultCanvas = new Canvas(result);
        Matrix matrix = new Matrix();
        matrix.setScale(
                (float) targetWidth / bg.getWidth(),
                (float) targetHeight / bg.getHeight());
        resultCanvas.drawBitmap(bg, matrix, null);
        resultCanvas.drawBitmap(text, 0, 0, null);
        return result;
    }
    
    private static String makeUrlIfNeeded(String filename, String packname, Context context) {
        if (Util.stringIsURL(filename))
            return filename;
        if (new StickerProvider(context).uriToFile(filename).exists())
            return filename;
        return String.format("%s%s/%s", Util.URL_BASE, Util.URL_REMOVED_STICKER_DIR,
                filename.substring(filename.indexOf(packname)));
    }
    
    private void onStartEditing() {
        if (deleteButton != null) {
            deleteButton.setVisibility(View.VISIBLE);
            deleteButton.animate().alpha(1f).start();
        }
        
        if (sendButton != null)
            sendButton.animate().alpha(0f)
                    .withEndAction(() -> sendButton.setVisibility(View.GONE)).start();
        if (saveButton != null)
            saveButton.animate().alpha(0f)
                    .withEndAction(() -> saveButton.setVisibility(View.GONE)).start();
    }
    
    private void onStopEditing() {
        if (deleteButton != null)
            deleteButton.animate().alpha(0f)
                    .withEndAction(() -> deleteButton.setVisibility(View.GONE)).start();
        
        if (sendButton != null) {
            sendButton.setVisibility(View.VISIBLE);
            sendButton.animate().alpha(1f).start();
        }
    
        if (saveButton != null) {
            saveButton.setVisibility(View.VISIBLE);
            saveButton.animate().alpha(1f).start();
        }
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
