package net.samvankooten.finnstickers;

import android.Manifest;
import android.animation.Animator;
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.icu.text.SimpleDateFormat;
import android.media.MediaActionSound;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.view.SimpleDraweeView;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.FootprintSelectionVisualizer;
import com.google.ar.sceneform.ux.ScaleController;
import com.google.ar.sceneform.ux.TransformableNode;
import com.google.ar.sceneform.ux.TransformationSystem;
import com.stfalcon.frescoimageviewer.ImageViewer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public class ARActivity extends AppCompatActivity {
    private static final String TAG = "ARActivity";
    private static final int EXT_STORAGE_REQ_CODE = 1;
    private static final double MIN_OPENGL_VERSION = 3.0;
    private ArFragment arFragment;
    private List<RecyclerView> stickerGalleries;
    private int selectedPack = -1;
    private int selectedSticker = -1;
    private List<Renderable[]> renderables;
    private List<AnchorNode> addedNodes;
    private RecyclerView packGallery;
    private StickerProvider provider;
    private int saveImageCountdown = -1;
    private Scene.OnUpdateListener listener;
    private Bitmap pendingBitmap;
    private List<Uri> imageUris;
    private List<File> imagePaths;
    
    @Override
    @SuppressWarnings({"FutureReturnValueIgnored"})
    @TargetApi(24)
    // CompletableFuture requires api level 24
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if (!checkIsSupportedDeviceOrFinish()) {
            return;
        }
        
        // Fresco is used for viewing photos after they're taken.
        Fresco.initialize(this);
        
        setContentView(R.layout.activity_ar);
        
        addedNodes = new LinkedList<>();
        provider = new StickerProvider();
        provider.setRootDir(this);
        
        packGallery = findViewById(R.id.gallery_pack_picker);
        // When we change the ImageView background color on selection, an animation is triggered
        // which causes the image itself to blink a bit. So disable the whole animation in lieu of
        // learning how to change it/make my own animation.
        packGallery.getItemAnimator().setChangeDuration(0);
        
        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.sceneform_fragment);
        
        initializeGallery();
        
        ImageView deleteButton = findViewById(R.id.delete_icon);
        deleteButton.setClickable(true);
        deleteButton.setOnClickListener(view -> {
            if (addedNodes == null)
                return;
            for (AnchorNode node : addedNodes) {
                node.setParent(null);
            }
        });
        
        ImageView backButton = findViewById(R.id.back_icon);
        backButton.setClickable(true);
        backButton.setOnClickListener(view -> finish());
    
        findViewById(R.id.fab).setOnClickListener(view -> takePicture());
        
        findViewById(R.id.photo_preview).setVisibility(View.GONE);
        
        imageUris = new LinkedList<>();
        imagePaths = new LinkedList<>();
        populatePastImages();
        
        // Create a new TransformationSystem that doesn't place rings under selected objects,
        // for use with flush-with-the-surface objects
        final TransformationSystem noRingTransformationSystem = new TransformationSystem(
                getResources().getDisplayMetrics(), new FootprintSelectionVisualizer());
        arFragment.getArSceneView().getScene().addOnPeekTouchListener(noRingTransformationSystem::onTouch);
        
        // Place objects when the user taps the screen
        arFragment.setOnTapArPlaneListener(
            (HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {
                if (selectedSticker < 0 || selectedPack < 0)
                    return;
                
                if (renderables.size() <= selectedPack || renderables.get(selectedPack) == null)
                    return;
                
                Renderable[] pack = renderables.get(selectedPack);
                
                if (pack[selectedSticker] == null)
                    return;
                
                Renderable renderable = pack[selectedSticker];
                
                // Create the Anchor
                AnchorNode anchorNode = new AnchorNode(hitResult.createAnchor());
                anchorNode.setParent(arFragment.getArSceneView().getScene());
    
                TransformableNode tnode;
                
                if (plane != null && plane.getType() == Plane.Type.VERTICAL) {
                    // If the user tapped a vertical surface, make the sticker appear
                    // flush with the wall, like a painting.
                    
                    tnode = new TransformableNode(noRingTransformationSystem);
                    // Scale must be set before the tnode's parent is set, or the scale
                    // setting doesn't take effect (per issue tracker)
                    setNodeScale(tnode);
                    // Parent must be set before the node below is created/set up,
                    // otherwise the rotation doesn't take effect.
                    tnode.setParent(anchorNode);
                    
                    // To avoid funniness with Sceneform's rotation system, we need to
                    // add another Node between the TransformableNode and the Renderable,
                    // and apply the flush-with-surface rotation to this Node.
                    Node node = new Node();
                    node.setParent(tnode);
                    node.setRenderable(renderable);
                    
                    // Apply the flush-to-wall rotation
                    Vector3 anchorUp = anchorNode.getUp();
                    node.setLookDirection(anchorUp);
                    
                    // Shift the node so that when we drag/rotate it, it moves about its
                    // center rather than its bottom (where the anchor would be otherwise)
                    Vector3 offset = new Vector3(0f, -.2f, 0f);
                    Vector3 shift = anchorNode.worldToLocalDirection(offset);
                    node.setLocalPosition(shift);
                }
                else {
                    // For floors, use the default card-upright-in-a-holder style
                    tnode = new TransformableNode(arFragment.getTransformationSystem());
                    tnode.setRenderable(renderable);
                    setNodeScale(tnode);
                    tnode.setParent(anchorNode);
                }
                tnode.select();
                addedNodes.add(anchorNode);
        });
    }
    
    private static void setNodeScale(TransformableNode tnode) {
        ScaleController scaleController = tnode.getScaleController();
        scaleController.setMinScale(.75f);
        scaleController.setMaxScale(12f);
        scaleController.setSensitivity(.2f);
        scaleController.setElasticity(.4f);
        // This is the default scale, but adjusting the scaleController range seems to shift
        // the default to somewhere in the middle of the scale range, so put it back to the
        // nice default.
        tnode.setLocalScale(new Vector3(1f, 1f, 1f));
    }
    
    /**
     * Returns false and displays an error message if Sceneform can not run, true if Sceneform can run
     * on this device.
     *
     * Sceneform requires Android N on the device as well as OpenGL 3.0 capabilities.
     *
     * Finishes the activity if Sceneform can not run.
     */
    private boolean checkIsSupportedDeviceOrFinish() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.e(TAG, "Sceneform requires Android N or later");
            Toast.makeText(this, "AR mode requires Android N or later", Toast.LENGTH_LONG).show();
            finish();
            return false;
        }
        String openGlVersionString =
                ((ActivityManager) getSystemService(Context.ACTIVITY_SERVICE))
                        .getDeviceConfigurationInfo()
                        .getGlEsVersion();
        if (Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
            Log.e(TAG, "Sceneform requires OpenGL ES 3.0 later");
            Toast.makeText(this, "AR mode requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
                    .show();
            finish();
            return false;
        }
        return true;
    }
    
    private void initializeGallery() {
        LinearLayout galleryLayout = findViewById(R.id.gallery_layout);
        
        // Load the list of StickerPacks and their icon Uris
        List<StickerPack> packs;
        try {
            packs = StickerPack.getInstalledPacks(getFilesDir());
        } catch (Exception e) {
            Log.e(TAG, "Error loading packs", e);
            return;
        }
        stickerGalleries = new ArrayList<>(packs.size());
        renderables = new ArrayList<>(packs.size());
        List<String> packIcons = new ArrayList<>(packs.size());
        for (StickerPack pack : packs)
            packIcons.add(provider.fileToUri(pack.getIconfile()).toString());
        
        // Set up the upper gallery, showing each installed pack
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
        packGallery.setLayoutManager(layoutManager);
        packGallery.setAdapter(new StickerPackViewerRecyclerAdapter(this, packIcons, 80, 10));
        
        // Have clicking a pack thumbnail activate the pack's gallery
        packGallery.addOnItemTouchListener(new RecyclerItemClickListener(this, (view, position) -> {
            view.playSoundEffect(android.view.SoundEffectConstants.CLICK);
            hideStickerGalleries();
            if (selectedPack == position) {
                // If the user taps the already-selected pack, close it.
                setSelectedSticker(RecyclerView.NO_POSITION);
                setSelectedPack(RecyclerView.NO_POSITION);
                return;
            }
            setSelectedPack(position);
            StickerPackViewerRecyclerAdapter adapter =
                    (StickerPackViewerRecyclerAdapter) stickerGalleries.get(position).getAdapter();
            selectedSticker = adapter.getSelectedPos();
            if (selectedSticker == RecyclerView.NO_POSITION)
                setSelectedSticker(0);
            stickerGalleries.get(position).setVisibility(View.VISIBLE);
        }));
        
        // Set up a gallery for each individual pack
        for (StickerPack pack : packs) {
            RecyclerView stickerGallery = new RecyclerView(this, null, R.attr.ARStickerPicker);
            // When we change the ImageView background color on selection, an animation is triggered
            // which causes the image itself to blink a bit. So disable the whole animation in lieu of
            // learning how to change it/make my own animation.
            stickerGallery.getItemAnimator().setChangeDuration(0);
            layoutManager = new LinearLayoutManager(this);
            layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
            stickerGallery.setLayoutManager(layoutManager);
            
            List<String> uris = pack.getStickerURIs();
            stickerGallery.setAdapter(new StickerPackViewerRecyclerAdapter(this, uris, 80, 10));
            galleryLayout.addView(stickerGallery);
            
            stickerGallery.setVisibility(View.GONE);
            stickerGalleries.add(stickerGallery);
            
            renderables.add(new Renderable[uris.size()]);
            for (int i = 0; i < uris.size(); i++) {
                loadStickerRenderable(renderables.size()-1, i, provider.uriToFile(uris.get(i)).toString());
            }
            
            // Select a sticker for placement when it is clicked
            stickerGallery.addOnItemTouchListener(new RecyclerItemClickListener(this, ((view, position) -> {
                view.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                setSelectedSticker(position);
            })));
        }
    }
    
    private void setSelectedSticker(int position) {
        StickerPackViewerRecyclerAdapter adapter =
                (StickerPackViewerRecyclerAdapter) stickerGalleries.get(selectedPack).getAdapter();
        adapter.setSelectedPos(position);
        selectedSticker = position;
    }
    
    private void setSelectedPack(int position) {
        StickerPackViewerRecyclerAdapter adapter =
                (StickerPackViewerRecyclerAdapter) packGallery.getAdapter();
        adapter.setSelectedPos(position);
        selectedPack = position;
    }
    
    private void hideStickerGalleries() {
        if (stickerGalleries != null) {
            for (RecyclerView gallery : stickerGalleries) {
                gallery.setVisibility(View.GONE);
            }
        }
    }
    
    @TargetApi(24)
    private void loadStickerRenderable(int pack, int pos, String path) {
        ViewRenderable.builder()
                .setView(this, R.layout.ar_sticker)
                .build()
                .thenAccept(renderable -> {
                    ImageView view = renderable.getView().findViewById(R.id.ar_sticker_image);
                    view.setImageBitmap(
                            BitmapFactory.decodeFile(path));
                    renderables.get(pack)[pos] = renderable;
                    renderable.setShadowCaster(false);
                }).exceptionally(
                throwable -> {
                    Toast toast =
                            Toast.makeText(ARActivity.this, "Unable to load sticker", Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.CENTER, 0, 0);
                    toast.show();
                    Log.e(TAG, "Error loading sticker", throwable);
                    return null;
                });
    }
    
    /**
     * Finds all previously-taken photos and sets up the preview widget.
     */
    private void populatePastImages() {
        if (!haveExtPermission())
            return;
        
        File path = new File(generatePhotoRootPath());
        if (!path.exists())
            return;
        
        File[] files = path.listFiles();
        if (files == null)
            return;
        
        if (files.length > 1)
            Arrays.sort(files, (object1, object2) -> Long.compare(object1.lastModified(), object2.lastModified()));
        
        for (File file : files) {
            String strFile = file.toString();
            if (strFile.substring(strFile.length()-4).equals(".jpg")) {
                imagePaths.add(0, file);
                imageUris.add(0, generateSharableUri(file));
            }
        }
        
        if (imageUris.size() > 0)
            updatePhotoPreview(false);
    }
    
    /**
     * Begin the process of taking a picture, which is finished in actuallyTakePicture().
     */
    private void takePicture() {
        // We don't want that grid of dots marking the surface in our image. We can disable that
        // and re-enable it after the image is saved. Unfortunately, disabling it doesn't take
        // effect until after another frame is rendered. We can set a callback for when that
        // rendering is complete, but the callback is triggered right *before* the frame is
        // updated, so we have to wait *two* frames.
        ArSceneView view = arFragment.getArSceneView();
        view.getPlaneRenderer().setEnabled(false);
        
        // If any objects are selected, we want to remove that ring from underneath them.
        FootprintSelectionVisualizer visualizer = ((FootprintSelectionVisualizer)
                arFragment.getTransformationSystem().getSelectionVisualizer());
        for (Node node : addedNodes) {
            TransformableNode tnode = (TransformableNode) node.getChildren().get(0);
            if (tnode.isSelected())
                visualizer.removeSelectionVisual(tnode);
        }
        
        shutterAnimation();
        
        saveImageCountdown = 2;
        listener = (time) -> actuallyTakePicture();
        view.getScene().addOnUpdateListener(listener);
    }
    
    /**
     * Finish taking a picture after takePicture() starts the process. Since we need to
     * run a countdown before the Sceneform dots and rings disappear, this is a separate function.
     */
    @TargetApi(24)
    private void actuallyTakePicture() {
        if (saveImageCountdown <= 0)
            // No countdown is set
            return;
        
        saveImageCountdown -= 1;
        
        if (saveImageCountdown > 0)
            // We're still counting down
            return;
        
        // Finally take that picture!
        
        // Remove the listener.
        ArSceneView view = arFragment.getArSceneView();
        view.getScene().removeOnUpdateListener(listener);
        listener = null;
        
        // Coming from https://codelabs.developers.google.com/codelabs/sceneform-intro/index.html?index=..%2F..%2Fio2018#14

        // Create a bitmap the size of the scene view.
        pendingBitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(),
                Bitmap.Config.ARGB_8888);
    
        // Create a handler thread to offload the processing of the image.
        final HandlerThread handlerThread = new HandlerThread("PixelCopier");
        handlerThread.start();
        // Make the request to copy.
        PixelCopy.request(view, pendingBitmap, (copyResult) -> {
            this.runOnUiThread(() -> view.getPlaneRenderer().setEnabled(true));
            if (copyResult == PixelCopy.SUCCESS) {
                if (!haveExtPermission())
                    this.runOnUiThread(this::requestExtStoragePermission);
                else {
                    if (savePendingBitmapToDisk())
                       this.runOnUiThread(() -> updatePhotoPreview(true));
                }
            } else {
                Log.e(TAG, "Failed to copy pixels: " + copyResult);
                Toast toast = Toast.makeText(ARActivity.this,
                        "Failed to save image", Toast.LENGTH_LONG);
                toast.show();
            }
            handlerThread.quitSafely();
        }, new Handler((handlerThread.getLooper())));
    }
    
    /**
     * To avoid having to ask for External Storage permissions as soon as the ARActivity is
     * opened, and to instead ask only if the user actually takes a picture, we save the image
     * bitmap is an instance variable and then perform the asynchronous permission request, if
     * needed. Once we have permissions, we save that image by calling back to this method.
     */
    private boolean savePendingBitmapToDisk() {
        // Coming from https://codelabs.developers.google.com/codelabs/sceneform-intro/index.html?index=..%2F..%2Fio2018#14
        String filename = generateFilename();
        File out = new File(filename);
        if (!out.getParentFile().exists()) {
            out.getParentFile().mkdirs();
        }
        try (FileOutputStream outputStream = new FileOutputStream(filename);
             ByteArrayOutputStream outputData = new ByteArrayOutputStream()) {
            pendingBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputData);
            outputData.writeTo(outputStream);
            outputStream.flush();
            
            File path = new File(filename);
            imagePaths.add(0, path);
            imageUris.add(0, generateSharableUri(path));
            return true;
        } catch (IOException ex) {
            Log.e(TAG, "Failed to save image " + ex.toString());
            Toast toast = Toast.makeText(this,
                    "Failed to save image", Toast.LENGTH_LONG);
            toast.show();
            return false;
        }
    }
    
    /**
     * Shows the most recently-taken photo in the screen corner.
     * @param useBitmap True: use pendingBitmap. False: use imagePaths.get(0)
     */
    private void updatePhotoPreview(boolean useBitmap) {
        SimpleDraweeView preview = findViewById(R.id.photo_preview);
        preview.setImageURI(imageUris.get(0));
    
        preview.setVisibility(View.VISIBLE);
        
        // Animate the preview image's appearance
        if (preview.isAttachedToWindow()) {
            int cx = preview.getLayoutParams().height / 2;
            int cy = preview.getLayoutParams().width / 2;
            Animator anim = ViewAnimationUtils.createCircularReveal(
                    preview, cx, cy, 0f, 2 * cx);
            anim.start();
        }
        
        // Launch a full-screen image viewer when the preview is clicked.
        preview.setClickable(true);
        preview.setOnClickListener((v) -> {
            LightboxOverlayView overlay = new LightboxOverlayView(
                    this, imageUris, imagePaths, 0);
            
            overlay.setOnDeleteCallback(() -> {
                if (imageUris.size() == 0)
                    preview.setVisibility(View.GONE);
                else
                    preview.setImageURI(imageUris.get(0));
            });
            
            ImageViewer viewer = new ImageViewer.Builder(this, imageUris)
                    .setStartPosition(0)
                    .setOverlayView(overlay)
                    .setImageChangeListener(overlay::setPos)
                    .show();
            overlay.setViewer(viewer);
        });
    }
    
    /**
     * Generates a date/time-based filename for a picture ready to be saved.
     */
    @TargetApi(24)
    private static String generateFilename() {
        String date =
                new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.getDefault()).format(new Date());
        return generatePhotoRootPath() + date + ".jpg";
    }
    
    private static String generatePhotoRootPath() {
        return Environment.getExternalStorageDirectory() + File.separator + "DCIM"
                + File.separator + "Finn Stickers/";
    }
    
    private Uri generateSharableUri(File path) {
        return FileProvider.getUriForFile(this, "net.samvankooten.finnstickers.fileprovider", path);
    }
    
    private boolean haveExtPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * Begins the process of asking for storage permission by explaining the necessity
     * to the user.
     */
    private void requestExtStoragePermission() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.need_ext_storage_perm)
                .setPositiveButton(android.R.string.ok, (d, i) -> finishRequestExtStoragePermission());
        builder.create().show();
    }
    
    /**
     * Once the user has dismissed the rationale dialog, ask for permission.
     */
    private void finishRequestExtStoragePermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                EXT_STORAGE_REQ_CODE);
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                                     @NonNull String permissions[],
                                                     @NonNull int[] results) {
        switch (requestCode) {
            case EXT_STORAGE_REQ_CODE: {
                if (results.length > 0
                        && results[0] == PackageManager.PERMISSION_GRANTED) {
                    // Now that we can, save the pending bitmap.
                    savePendingBitmapToDisk();
                    updatePhotoPreview(true);
                } else {
                    // Permission not granted---discard pending image.
                    pendingBitmap = null;
                }
            }
        }
    }
    
    /**
     * Performs a shutter animation by fading the AR view to black shortly. Also plays a
     * shutter sound.
     */
    private void shutterAnimation() {
        // Based on https://stackoverflow.com/questions/23960221/android-make-screen-flash-white
        final ImageView v = findViewById(R.id.shutter_flash);
        v.setImageAlpha(0);
        v.setVisibility(View.VISIBLE);
        
        Animation a = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime,
                                               Transformation t) {
                if (interpolatedTime == 1) {
                    v.setImageAlpha(0);
                    v.setVisibility(View.GONE);
                } else {
                    int newAlpha;
                    if (interpolatedTime < 0.5)
                        // Fade in
                        newAlpha = (int) (255 * (2*interpolatedTime));
                    else {
                        // Fade back out
                        interpolatedTime -= 0.5;
                        newAlpha = (int) (255 * (1 - 2*interpolatedTime));
                    }
                    v.setImageAlpha(newAlpha);
                }
            }
        
            @Override
            public boolean willChangeBounds() {
                return false;
            }
        };
        final int time = getResources().getInteger(
                android.R.integer.config_mediumAnimTime);
        a.setDuration(time);
        v.startAnimation(a);
        
        new MediaActionSound().play(MediaActionSound.SHUTTER_CLICK);
    }
}
