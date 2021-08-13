package net.samvankooten.finnstickers.editor;

import android.content.ClipData;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
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
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.slider.Slider;
import com.google.android.material.snackbar.Snackbar;

import net.samvankooten.finnstickers.Constants;
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.content.FileProvider;

public class EditorActivity extends AppCompatActivity {
    public static final String TAG = "EditorActivity";
    public static final String PACK_NAME = "packname";
    public static final String STICKER_URI = "uri";
    private static final String PERSISTED_TEXT = "textObjects";
    public static final String ADDED_STICKER_URI = "addedStickerUri";
    public static final int RESULT_STICKER_SAVED = 157;
    private static final String COPY_OF_EXT_FILE = "copy_of_ext_file";
    
    private static final float WIDTH_SCALE_LOG_MIN = -1.4f;
    private static final float WIDTH_SCALE_LOG_MAX = 1.4f;
    
    private StickerPack pack;
    private Sticker sourceSticker;
    private ImageView imageView;
    private ImageView deleteButton;
    private ImageView colorButton;
    private ImageView widthButton;
    private ImageView sendButton;
    private ImageView saveButton;
    private ImageView flipStickerButton;
    private ImageView flipTextButton;
    private Slider widthSlider;
    private View spinner;
    private DraggableTextManager draggableTextManager;
    private String baseImage;
    private String basePath;
    
    private boolean showSpinnerPending = false;
    private boolean externalSource = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);
        
        String packName = "";
        if (getIntent().hasExtra(PACK_NAME) && getIntent().hasExtra(STICKER_URI)) {
            packName = getIntent().getStringExtra(PACK_NAME);
            pack = StickerPackRepository.getInstalledOrCachedPackByName(packName, this);
            String uri = getIntent().getStringExtra(STICKER_URI);
            if (pack == null || uri == null || uri.equals("")
                    || (sourceSticker = pack.getStickerByUri(uri)) == null) {
                Log.e(TAG, "Error loading pack " + packName);
                Snackbar.make(imageView, getString(R.string.unexpected_error),
                        Snackbar.LENGTH_LONG).show();
                return;
            }
        } else if (getIntent().hasExtra(Intent.EXTRA_STREAM)
                && getIntent().getType() != null
                && getIntent().getType().startsWith("image/")) {
            externalSource = true;
            handleExternalSource(getIntent().getParcelableExtra(Intent.EXTRA_STREAM),
                    getIntent().getType());
        } else if (getIntent().getData() != null
                && getIntent().getType() != null
                && getIntent().getType().startsWith("image/")) {
            externalSource = true;
            handleExternalSource(getIntent().getData(),
                    getIntent().getType());
        } else
            return;
    
        ImageView backButton = findViewById(R.id.back_icon);
        backButton.setOnClickListener(view -> onBackPressed());
        TooltipCompat.setTooltipText(backButton, getString(R.string.back_button));
        
        FloatingActionButton addText = findViewById(R.id.add_text);
        addText.setOnClickListener(view -> addText());
        TooltipCompat.setTooltipText(addText, getString(R.string.add_text_box));
        
        draggableTextManager = findViewById(R.id.editing_container);
        draggableTextManager.setOnStartEditCallback(this::onStartEditing);
        draggableTextManager.setOnStopEditCallback(this::onStopEditing);
        
        setupKeyboardHandling();
        
        deleteButton = findViewById(R.id.delete_icon);
        deleteButton.setOnClickListener(v -> draggableTextManager.deleteSelectedText());
        TooltipCompat.setTooltipText(deleteButton, getString(R.string.delete_button));
        
        colorButton = findViewById(R.id.color_icon);
        colorButton.setOnClickListener(v -> chooseColor());
        TooltipCompat.setTooltipText(colorButton, getString(R.string.color_button));
        
        widthButton = findViewById(R.id.width_icon);
        widthButton.setOnClickListener(v -> adjustTextWidth());
        TooltipCompat.setTooltipText(widthButton, getString(R.string.width_button));
        
        flipStickerButton = findViewById(R.id.flip_sticker_icon);
        flipStickerButton.setOnClickListener(v -> flipImage());
        TooltipCompat.setTooltipText(flipStickerButton, getString(R.string.flip_sticker_button));
        
        flipTextButton = findViewById(R.id.flip_text_icon);
        flipTextButton.setOnClickListener(v -> flipActiveText());
        TooltipCompat.setTooltipText(flipTextButton, getString(R.string.flip_text_button));
        
        sendButton = findViewById(R.id.send_icon);
        sendButton.setOnClickListener(v -> send());
        TooltipCompat.setTooltipText(sendButton, getString(R.string.send_button));
        
        saveButton = findViewById(R.id.save_icon);
        if (externalSource) {
            saveButton.setVisibility(View.GONE);
            saveButton = null;
        } else {
            saveButton.setOnClickListener(v -> save());
            TooltipCompat.setTooltipText(saveButton, getString(R.string.save_button));
        }
        
        widthSlider = findViewById(R.id.width_scale_bar);
        widthSlider.addOnChangeListener((slider, i, fromUser) -> {
            if (!fromUser)
                return;
            draggableTextManager.setSelectedTextWidthMultiplier((float) Math.exp(
                    i / 100f * (WIDTH_SCALE_LOG_MAX - WIDTH_SCALE_LOG_MIN) + WIDTH_SCALE_LOG_MIN
            ));
        });
        
        if (Build.VERSION.SDK_INT >= 29) {
            widthSlider.addOnLayoutChangeListener((view, i, i1, i2, i3, i4, i5, i6, i7) -> {
                List<Rect> exclusions = new ArrayList<>();
                exclusions.add(new Rect(0, 0, 100, widthSlider.getHeight()));
                exclusions.add(new Rect(widthSlider.getWidth() - 100, 0,
                        widthSlider.getWidth(), widthSlider.getHeight()));
                
                widthSlider.setSystemGestureExclusionRects(exclusions);
            });
        }
        
        spinner = findViewById(R.id.progress_indicator);
        
        imageView = findViewById(R.id.main_image);
        
        if (savedInstanceState != null
                && savedInstanceState.containsKey(PERSISTED_TEXT))
            loadJSON(savedInstanceState.getString(PERSISTED_TEXT));
        
        if (!externalSource) {
            if (!sourceSticker.isCustomized()) {
                baseImage = sourceSticker.getCurrentLocation();
                basePath = sourceSticker.getRelativePath();
            } else {
                if (savedInstanceState == null)
                    loadJSON(sourceSticker.getCustomData());
                baseImage = Sticker.generateUri(packName, basePath).toString();
                baseImage = StickerRenderer.makeUrlIfNeeded(baseImage, packName, this);
            }
            loadImage();
        }
        
        // Clean up any previously-shared stickers. At this point, if the editor's being
        // re-opened, any previously-shared sticker files are probably done being used.
        File shareDir = new File(getCacheDir(), Constants.DIR_FOR_SHARED_FILES);
        if (shareDir.exists()) {
            File[] files = shareDir.listFiles();
            if (files == null) {
                Log.e(TAG, "Got null file list");
            } else {
                for (File file : files) {
                    // If the file is less than an hour old, keep it, just to be safe
                    if (System.currentTimeMillis() - file.lastModified() > 60 * 60 * 1000) {
                        try {
                            Util.delete(shareDir);
                        } catch (IOException e) {
                            Log.e(TAG, "Error deleting " + shareDir);
                        }
                    }
                }
            }
        }
    }
    
    private void loadImage() {
        Util.enableGlideCacheIfRemote(GlideApp.with(this).load(baseImage), baseImage, 0)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        Log.e(TAG, "Image load failed", e);
                        if (Util.stringIsURL(baseImage) && !Util.connectedToInternet(EditorActivity.this)) {
                            Toast.makeText(EditorActivity.this, getString(R.string.internet_required), Toast.LENGTH_LONG).show();
                            onBackPressed();
                        }
                        return false; }
                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        hideSpinner();
                        
                        if (Util.stringIsURL(baseImage)) {
                            // If the remote image is now in Glide's cache, grab it for any
                            // future rendering.
                            // .submit().get() must be called from a background thread
                            new Thread(() -> {
                                GlideRequest<File> request = GlideApp.with(EditorActivity.this).asFile().load(baseImage);
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
                        
                        draggableTextManager.setVisibility(View.VISIBLE);
                        return false;
                    }
                })
                .into(imageView);
    }
    
    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(PERSISTED_TEXT, toJSON().toString());
    }
    
    private JSONObject toJSON() {
        JSONObject data = new JSONObject();
        try {
            data.put("basePath", basePath);
            data.put("textData", draggableTextManager.toJSON());
        } catch (JSONException e) {
            Log.e(TAG, "Error saving state to JSON", e);
            return new JSONObject();
        }
        return data;
    }
    
    private void loadJSON(String data) {
        try {
            loadJSON(new JSONObject(data));
        } catch (JSONException e) {
            Log.e(TAG, "Error loading state from JSON string", e);
        }
    }
    
    private void loadJSON(JSONObject data) {
        try {
            basePath = data.getString("basePath");
            draggableTextManager.loadJSON(data.getJSONObject("textData"));
        } catch (JSONException e) {
            Log.e(TAG, "Error loading state from JSON", e);
        }
    }
    
    private void handleExternalSource(Uri source, String type) {
        new Thread(() -> {
            basePath = "source";
            String suffix = "";
            // The "file name" in basePath is meaningless. If we have file type information, add it
            // as an extension so the file type can be detected later on.
            if (type.contains("/")) {
                int i = type.lastIndexOf("/");
                if (i != type.length()-1) {
                    suffix = type.substring(i + 1);
                    if (!suffix.equals("*")) {
                        suffix = "." + suffix;
                        basePath += suffix;
                    }
                }
            }
            
            File localCopy = new File(getCacheDir(), COPY_OF_EXT_FILE);
            localCopy = Util.generateUniqueFile(localCopy.toString(), suffix);
            try {
                Util.copy(source, localCopy, this);
            } catch (IOException e) {
                Log.e(TAG, "Error copying input file", e);
                Snackbar.make(imageView, getString(R.string.unexpected_error),
                        Snackbar.LENGTH_LONG).show();
                return;
            }
            baseImage = localCopy.toString();
            runOnUiThread(this::loadImage);
        }).start();
    }
    
    private void addText() {
        if (draggableTextManager.isDragging())
            return;
    
        closeWidthSliderIfOpen();
        
        draggableTextManager.addText();
    }
    
    private void send() {
        if (draggableTextManager.isDragging())
            return;
        
        showSpinner();
    
        draggableTextManager.clearFocus();
        
        new Thread( () -> {
            File path = new File(getCacheDir(), Constants.DIR_FOR_SHARED_FILES);
            path.mkdirs();
            String possibleSuffix = basePath.contains(".") ?
                    basePath.substring(basePath.lastIndexOf(".")) :
                    "";
            File file = Util.generateUniqueFile(path.toString(), possibleSuffix.length() <= 4 ? possibleSuffix : "");
    
            final File finalLocation = renderToFile(file);
            if (finalLocation == null) {
                runOnUiThread(() -> {
                    Snackbar.make(spinner, getString(R.string.unexpected_error),
                            Snackbar.LENGTH_LONG).show();
                    hideSpinner();
                });
                return;
            }
            
            runOnUiThread(() -> {
                hideSpinner();
                Uri contentUri = FileProvider.getUriForFile(
                        this, "net.samvankooten.finnstickers.fileprovider", finalLocation);
                if (contentUri != null) {
                    Intent shareIntent = new Intent();
                    shareIntent.setAction(Intent.ACTION_SEND);
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    shareIntent.setType(getContentResolver().getType(contentUri));
                    shareIntent.setClipData(ClipData.newRawUri(null, contentUri));
                    shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                    startActivity(
                            Intent.createChooser(shareIntent, getString(R.string.share_text)));
                }
            });
            
        }).start();
    }
    
    private void save() {
        if (draggableTextManager.isDragging())
            return;
    
        if (externalSource)
            return;
        
        // Avoid double-taps
        saveButton.setOnClickListener(null);
        
        showSpinner();
    
        draggableTextManager.clearFocus();
        
        new Thread( () -> {
            File relativePath = new File(pack.getPackBaseDir(), Constants.USER_STICKERS_DIR);
            File absPath = new File(getFilesDir(), relativePath.toString());
            absPath.mkdirs();
            File file = new File(absPath, Util.generateUniqueFileName(
                    relativePath.toString(),
                    basePath.substring(basePath.lastIndexOf("."))));
            File relativeName = new File(Constants.USER_STICKERS_DIR, file.getName());
    
            file = renderToFile(file);
            if (file == null) {
                runOnUiThread(() -> {
                    Snackbar.make(spinner, getString(R.string.unexpected_error),
                            Snackbar.LENGTH_LONG).show();
                    hideSpinner();
                });
                return;
            }
    
            List<String> keywords = new ArrayList<>();
            for (TextObject object : draggableTextManager.getTextObjects()) {
                if (object.getText() != null)
                    for (String word : object.getText().toString().split("\\s")) {
                        if (!keywords.contains(word))
                            keywords.add(word);
                    }
            }
            Sticker newSticker = new Sticker(relativeName.toString(), pack.getPackname(),
                    sourceSticker.getBaseKeywords(), keywords, toJSON(), this);
    
            runOnUiThread(() -> {
                // It seems like addSticker() should be able to be called from the BG thread,
                // but I seem to be hitting weird race conditions when I do so.
                pack.addSticker(newSticker, sourceSticker, this);
                Intent data = new Intent();
                data.putExtra(ADDED_STICKER_URI, newSticker.getURI().toString());
                setResult(RESULT_STICKER_SAVED, data);
                actuallyExit();
            });
        }).start();
    }
    
    private File renderToFile(File file) {
        return StickerRenderer.renderToFile(baseImage, pack == null ? null : pack.getPackname(),
                toJSON(), file, this);
    }
    
    private void chooseColor() {
        if (draggableTextManager.isDragging())
            return;
    
        closeWidthSliderIfOpen();
        ColorDialog dialog = new ColorDialog(this,
                draggableTextManager.getSelectedTextColor(),
                draggableTextManager.getSelectedTextOutlineColor());
        dialog.show();
        dialog.setOnDismissListener((d) -> {
            draggableTextManager.setSelectedTextColor(dialog.getTextColor());
            draggableTextManager.setSelectedTextOutlineColor(dialog.getOutlineColor());
        });
    }
    
    private boolean closeWidthSliderIfOpen() {
        if (widthSlider.getVisibility() == View.VISIBLE) {
            widthSlider.animate().alpha(0f)
                    .withEndAction(() -> widthSlider.setVisibility(View.GONE)).start();
            return true;
        }
        return false;
    }
    
    private void adjustTextWidth() {
        if (draggableTextManager.isDragging())
            return;
    
        if (closeWidthSliderIfOpen())
            return;
    
        widthSlider.setVisibility(View.VISIBLE);
        widthSlider.animate().alpha(1f).start();
        
        float currentTextMultiplier = draggableTextManager.getSelectedTextWidthMultiplier();
        currentTextMultiplier = (float) Math.log(currentTextMultiplier);
        
        if (currentTextMultiplier < WIDTH_SCALE_LOG_MIN) {
            currentTextMultiplier = WIDTH_SCALE_LOG_MIN;
            draggableTextManager.setSelectedTextWidthMultiplier(currentTextMultiplier);
        }
        if (currentTextMultiplier > WIDTH_SCALE_LOG_MAX) {
            currentTextMultiplier = WIDTH_SCALE_LOG_MAX;
            draggableTextManager.setSelectedTextWidthMultiplier(currentTextMultiplier);
        }
        
        widthSlider.setValue((int) ( 100 * (
                (currentTextMultiplier - WIDTH_SCALE_LOG_MIN) / (WIDTH_SCALE_LOG_MAX - WIDTH_SCALE_LOG_MIN) )));
    }
    
    private void flipImage() {
        if (draggableTextManager.isDragging())
            return;
    
        draggableTextManager.toggleFlipHorizontally();
    }
    
    private void flipActiveText() {
        if (draggableTextManager.isDragging())
            return;
        
        closeWidthSliderIfOpen();
    
        draggableTextManager.toggleSelectedTextFlipHorizontally();
    }
    
    private void onStartEditing() {
        deleteButton.setVisibility(View.VISIBLE);
        deleteButton.animate().alpha(1f).start();
        
        colorButton.setVisibility(View.VISIBLE);
        colorButton.animate().alpha(1f).start();
        
        widthButton.setVisibility(View.VISIBLE);
        widthButton.animate().alpha(1f).start();
        
        flipTextButton.setVisibility(View.VISIBLE);
        flipTextButton.animate().alpha(1f).start();
        
        flipStickerButton.animate().alpha(0f)
                .withEndAction(() -> flipStickerButton.setVisibility(View.GONE)).start();
        
        sendButton.animate().alpha(0f)
                .withEndAction(() -> sendButton.setVisibility(View.GONE)).start();
        
        if (saveButton != null)
            saveButton.animate().alpha(0f)
                    .withEndAction(() -> saveButton.setVisibility(View.GONE)).start();
    }
    
    private void onStopEditing() {
        deleteButton.animate().alpha(0f)
                .withEndAction(() -> deleteButton.setVisibility(View.GONE)).start();
        
        colorButton.animate().alpha(0f)
                .withEndAction(() -> colorButton.setVisibility(View.GONE)).start();
        
        widthButton.animate().alpha(0f)
                .withEndAction(() -> widthButton.setVisibility(View.GONE)).start();
        
        widthSlider.animate().alpha(0f)
                .withEndAction(() -> widthSlider.setVisibility(View.GONE)).start();
        
        flipTextButton.animate().alpha(0f)
                .withEndAction(() -> flipTextButton.setVisibility(View.GONE)).start();
        
        flipStickerButton.setVisibility(View.VISIBLE);
        flipStickerButton.animate().alpha(1f).start();
        
        sendButton.setVisibility(View.VISIBLE);
        sendButton.animate().alpha(1f).start();
        
        if (saveButton != null) {
            saveButton.setVisibility(View.VISIBLE);
            saveButton.animate().alpha(1f).start();
        }
    }
    
    private void showSpinner() {
        // We don't want the spinner to flash really quickly for super short renders,
        // so only start it once the job has gone on for some milliseconds.
        if (!showSpinnerPending) {
            spinner.postDelayed(() -> {
                if (showSpinnerPending) {
                    showSpinnerPending = false;
                    spinner.setVisibility(View.VISIBLE);
                }
            }, 100);
            showSpinnerPending = true;
        }
    }
    
    private void hideSpinner() {
        showSpinnerPending = false;
        spinner.setVisibility(View.GONE);
    }
    
    @Override
    public void onBackPressed() {
        if (draggableTextManager.isDragging())
            return;
    
        if (draggableTextManager.requestStopEdit())
            return;
        
        // Show a confirmation dialog only if there are changes relative to the sticker that
        // was opened for editing
        DraggableTextManager sourceManager = new DraggableTextManager(this, true);
        if (sourceSticker != null && sourceSticker.isCustomized())
            try {
                sourceManager.loadJSON(sourceSticker.getCustomData().getJSONObject("textData"));
            } catch (JSONException e) {
                Log.e(TAG, "Error extracting custom data", e);
            }
        sourceManager.setImageBounds(draggableTextManager.getImageBounds());
        
        if (!sourceManager.equals(draggableTextManager)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.editor_confirm_exit_title)
                    .setMessage(R.string.editor_confirm_exit_text)
                    .setPositiveButton(R.string.editor_confirm_exit_save,
                            (d, w) -> save())
                    .setNeutralButton(R.string.editor_confirm_exit_dont_save,
                            (d, w) -> actuallyExit())
                    .setNegativeButton(R.string.editor_confirm_exit_cancel,
                            (d, w) -> d.dismiss())
                    .show();
                    
        } else
            actuallyExit();
    }
    
    private void actuallyExit() {
        finish();
        overridePendingTransition(R.anim.no_fade, R.anim.fade_out);
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (isFinishing() && externalSource) {
            File image = new File(baseImage);
            if (image.exists()) {
                try {
                    Util.delete(image);
                } catch (IOException e) {
                    Log.e(TAG, "Error deleting file " + image);
                }
            }
        }
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
