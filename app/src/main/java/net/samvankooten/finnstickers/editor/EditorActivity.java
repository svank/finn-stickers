package net.samvankooten.finnstickers.editor;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.Toast;

import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.snackbar.Snackbar;

import net.samvankooten.finnstickers.R;
import net.samvankooten.finnstickers.Sticker;
import net.samvankooten.finnstickers.StickerPack;
import net.samvankooten.finnstickers.editor.renderer.StickerRenderer;
import net.samvankooten.finnstickers.misc_classes.GlideApp;
import net.samvankooten.finnstickers.misc_classes.GlideRequest;
import net.samvankooten.finnstickers.utils.StickerPackRepository;
import net.samvankooten.finnstickers.utils.Util;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.content.FileProvider;

public class EditorActivity extends Activity {
    public static final String TAG = "EditorActivity";
    public static final String PACK_NAME = "packname";
    public static final String STICKER_URI = "uri";
    private static final String PERSISTED_TEXT = "textObjects";
    public static final String ADDED_STICKER_URI = "addedStickerUri";
    public static final int RESULT_STICKER_SAVED = 157;
    
    private StickerPack pack;
    private Sticker sticker;
    private ImageView imageView;
    private ImageView deleteButton;
    private ImageView sendButton;
    private ImageView saveButton;
    private View spinner;
    private DraggableTextManager draggableTextManager;
    private String baseImage;
    private String basePath;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);
        
        String packName = getIntent().getStringExtra(PACK_NAME);
        pack = StickerPackRepository.getInstalledOrCachedPackByName(packName, this);
        String uri = getIntent().getStringExtra(STICKER_URI);
        if (pack == null || uri == null || uri.equals("")) {
            Log.e(TAG, "Error loading pack " + packName);
            Snackbar.make(findViewById(R.id.rootContainer), getString(R.string.unexpected_error),
                    Snackbar.LENGTH_LONG).show();
            return;
        }
        
        sticker = pack.getStickerByUri(uri);
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
        
        spinner = findViewById(R.id.progress_indicator);
    
        imageView = findViewById(R.id.main_image);
        if (stickerIsUnedited) {
            baseImage = sticker.getCurrentLocation();
            basePath = sticker.getRelativePath();
        } else {
            baseImage = Sticker.generateUri(packName, sticker.getCustomTextBaseImage()).toString();
            baseImage = StickerRenderer.makeUrlIfNeeded(baseImage, packName, this);
            basePath = sticker.getCustomTextBaseImage();
        }
        
        Util.enableGlideCacheIfRemote(GlideApp.with(this).load(baseImage), baseImage, 0)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        if (Util.stringIsURL(baseImage) && !Util.connectedToInternet(EditorActivity.this)) {
                            Toast.makeText(EditorActivity.this, getString(R.string.internet_required), Toast.LENGTH_LONG).show();
                            onBackPressed();
                        }
                        return false; }
                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        spinner.setVisibility(View.GONE);
                        
                        if (Util.stringIsURL(baseImage)) {
                            // If the remote image is now in Glide's cache, grab it for any
                            // future rendering.
                            // .submit().get() must be called from a background thread
                            new Thread(() -> {
                                GlideRequest request = GlideApp.with(EditorActivity.this).asFile().load(baseImage);
                                try {
                                    baseImage = Uri.fromFile(new File(
                                            Util.enableGlideCacheIfRemote(request, baseImage, 0).submit().get().toString()
                                            )).toString();
                                } catch (ExecutionException | InterruptedException e) {
                                    Log.e(TAG, "Error in getting Glide cache file location", e);
                                }
                            }).start();
                        }
                        
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
                                Snackbar.make(spinner, getString(R.string.unexpected_error),
                                        Snackbar.LENGTH_LONG).show();
                            }
                        }
                        return false;
                    }
                })
                .into(imageView);
    }
    
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(PERSISTED_TEXT, draggableTextManager.toJSON().toString());
    }
    
    private void send() {
        spinner.setVisibility(View.VISIBLE);
        
        new Thread( () -> {
            File path = new File(getCacheDir(), "shared");
            path.mkdirs();
            File file = new File(path,
                    "sent_sticker"
                            + basePath.substring(basePath.lastIndexOf(".")));
    
            boolean success = renderToFile(file);
            if (!success) {
                runOnUiThread(() -> {
                    Snackbar.make(spinner, getString(R.string.unexpected_error),
                            Snackbar.LENGTH_LONG).show();
                    spinner.setVisibility(View.GONE);
                });
                return;
            }
            
            runOnUiThread(() -> {
                spinner.setVisibility(View.GONE);
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
            });
            
        }).start();
    }
    
    private void save() {
        // Avoid double-taps
        saveButton.setOnClickListener(null);
        
        spinner.setVisibility(View.VISIBLE);
        
        new Thread( () -> {
            File relativePath = new File(pack.getPackBaseDir(), Util.USER_STICKERS_DIR);
            File absPath = new File(getFilesDir(), relativePath.toString());
            absPath.mkdirs();
            File file = new File(absPath, Util.generateUniqueFileName(
                    relativePath.toString(),
                    basePath.substring(basePath.lastIndexOf("."))));
            File relativeName = new File(Util.USER_STICKERS_DIR, file.getName());
    
            boolean success = renderToFile(file);
            if (!success) {
                runOnUiThread(() -> {
                    Snackbar.make(spinner, getString(R.string.unexpected_error),
                            Snackbar.LENGTH_LONG).show();
                    spinner.setVisibility(View.GONE);
                });
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
    
            runOnUiThread(() -> {
                // It seems like addSticker() should be able to be called from the BG thread,
                // but I seem to be hitting weird race conditions when I do so.
                pack.addSticker(newSticker, sticker, this);
                Intent data = new Intent();
                data.putExtra(ADDED_STICKER_URI, newSticker.getURI().toString());
                setResult(RESULT_STICKER_SAVED, data);
                onBackPressed();
            });
        }).start();
    }
    
    private boolean renderToFile(File file) {
        return StickerRenderer.renderToFile(baseImage, pack.getPackname(), draggableTextManager.toJSON(), file, this);
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
