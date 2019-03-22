package net.samvankooten.finnstickers.ar;

import android.animation.Animator;
import android.annotation.TargetApi;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.icu.text.SimpleDateFormat;
import android.media.CamcorderProfile;
import android.media.MediaActionSound;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.ImageView;
import android.widget.Toast;

import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.ux.FootprintSelectionVisualizer;
import com.google.ar.sceneform.ux.TransformableNode;
import com.stfalcon.imageviewer.StfalconImageViewer;

import net.samvankooten.finnstickers.LightboxOverlayView;
import net.samvankooten.finnstickers.R;
import net.samvankooten.finnstickers.misc_classes.GlideApp;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;

public class PhotoVideoHelper {
    private static final String TAG = "PhotoVideoHelper";
    
    private ARActivity arActivity;
    private VideoRecorder videoRecorder;
    private boolean videoMode = false;
    private List<Uri> imageUris;
    private List<File> imagePaths;
    private FloatingActionButton shutterButton;
    private FloatingActionButton videoModeButton;
    private ImageView photoPreview;
    private ImageView shutterFlash;
    private int saveImageCountdown = -1;
    private Scene.OnUpdateListener listener;
    
    PhotoVideoHelper(ARActivity activity) {
        arActivity = activity;
        
        shutterButton = arActivity.findViewById(R.id.shutter_button);
        shutterButton.setOnClickListener(view -> onCapture());
    
        videoModeButton = arActivity.findViewById(R.id.mode_switch);
        videoModeButton.setOnClickListener(view -> toggleVideoMode());
    
        videoRecorder = new VideoRecorder();
        videoRecorder.setSceneView(arActivity.getArFragment().getArSceneView());
        videoRecorder.setGenerateFilenameCallback(() -> generateFilename("mp4"));
        videoRecorder.setPostSaveCallback(this::onVideoSaved);
        
        shutterFlash = arActivity.findViewById(R.id.shutter_flash);
        
        photoPreview = arActivity.findViewById(R.id.photo_preview);
        photoPreview.setVisibility(View.GONE);
    
        // Launch a full-screen image viewer when the preview is clicked.
        photoPreview.setClickable(true);
        photoPreview.setOnClickListener((v) -> {
            if (imageUris.size() == 0)
                return;
            LightboxOverlayView overlay = new LightboxOverlayView(
                    arActivity, imageUris, imagePaths, 0, true, true);
        
            StfalconImageViewer viewer = new StfalconImageViewer.Builder<>(arActivity, imageUris,
                    (view, image) -> GlideApp.with(arActivity).load(image).into(view))
                    .withStartPosition(0)
                    .withOverlayView(overlay)
                    .withImageChangeListener(overlay::setPos)
                    .withHiddenStatusBar(false)
                    .withTransitionFrom(photoPreview)
                    .show();
            overlay.setViewer(viewer);
            overlay.setGetTransitionImageCallback(pos -> photoPreview);
        
            overlay.setOnDeleteCallback(this::updatePhotoPreview);
        });
    
        imageUris = new LinkedList<>();
        imagePaths = new LinkedList<>();
        populatePastImages();
    }
    
    private void toggleVideoMode() {
        if (videoRecorder.isRecording())
            return;
        
        videoMode = !videoMode;
        
        updateShutterButton();
    }
    
    @TargetApi(24)
    private void updateShutterButton() {
        if (videoMode)
            drawShutterVideoReady();
        else
            drawShutterPhotoMode();
    }
    
    @TargetApi(24)
    private void drawShutterPhotoMode() {
        shutterButton.setBackgroundTintList(
                ColorStateList.valueOf(arActivity.getColor(R.color.colorAccent)));
        shutterButton.setImageResource(R.drawable.icon_camera);
        shutterButton.setContentDescription(arActivity.getString(R.string.take_photo));
        videoModeButton.setContentDescription(arActivity.getString(R.string.switch_to_video));
    }
    
    @TargetApi(24)
    private void drawShutterVideoReady() {
        shutterButton.setBackgroundTintList(
                ColorStateList.valueOf(arActivity.getColor(R.color.recordBackground)));
        shutterButton.setImageResource(R.drawable.icon_record_start);
        shutterButton.setContentDescription(arActivity.getString(R.string.take_video));
        videoModeButton.setContentDescription(arActivity.getString(R.string.switch_to_photo));
    }
    
    @TargetApi(24)
    private void drawShutterVideoRecording() {
        shutterButton.setBackgroundTintList(
                ColorStateList.valueOf(arActivity.getColor(R.color.recordForeground)));
        shutterButton.setImageResource(R.drawable.icon_record_stop);
        shutterButton.setContentDescription(arActivity.getString(R.string.take_video));
        videoModeButton.setContentDescription(arActivity.getString(R.string.switch_to_photo));
    }
    
    /**
     * Finds all previously-taken photos and sets up the preview widget.
     */
    void populatePastImages() {
        if (!arActivity.haveExtPermission())
            return;
        
        if (imageUris.size() > 0)
            // Past images must have been populated already.
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
            if (strFile.endsWith(".jpg") || strFile.endsWith(".mp4")) {
                imagePaths.add(0, file);
                imageUris.add(0, generateSharableUri(file));
            }
        }
        
        if (imageUris.size() > 0)
            updatePhotoPreview();
    }
    
    /**
     * Begin the process of taking a picture, which is finished in actuallyRecordMedia().
     */
    void onCapture() {
        if (!arActivity.haveExtPermission())
            arActivity.requestExtStoragePermission();
        // We don't want that grid of dots marking the surface in our image. We can disable that
        // and re-enable it after the image is saved. Unfortunately, disabling it doesn't take
        // effect until after another frame is rendered. We can set a callback for when that
        // rendering is complete, but the callback is triggered right *before* the frame is
        // updated, so we have to wait *two* frames.
        ArSceneView view = arActivity.getArFragment().getArSceneView();
        view.getPlaneRenderer().setVisible(false);
        
        // If any objects are selected, we want to remove that ring from underneath them.
        FootprintSelectionVisualizer visualizer = ((FootprintSelectionVisualizer)
                arActivity.getArFragment().getTransformationSystem().getSelectionVisualizer());
        for (Node node : arActivity.getNodes()) {
            TransformableNode tnode = (TransformableNode) node.getChildren().get(0);
            if (tnode.isSelected())
                visualizer.removeSelectionVisual(tnode);
        }
        
        if (!videoMode)
            shutterAnimation();
        
        saveImageCountdown = 2;
        listener = (time) -> actuallyRecordMedia();
        view.getScene().addOnUpdateListener(listener);
    }
    
    /**
     * Finish taking a picture after onCapture() starts the process. Since we need to
     * run a countdown before the Sceneform dots and rings disappear, this is a separate function.
     */
    private void actuallyRecordMedia() {
        if (saveImageCountdown <= 0)
            // No countdown is set
            return;
    
        saveImageCountdown -= 1;
    
        if (saveImageCountdown > 0)
            // We're still counting down
            return;
    
        // Finally take that picture!
    
        // Remove the listener.
        arActivity.getArFragment().getArSceneView().getScene().removeOnUpdateListener(listener);
        listener = null;
    
        if (videoMode)
            handleVideoRecording();
        else
            recordImage();
    }
    
    @TargetApi(24)
    private void recordImage() {
        ArSceneView view = arActivity.getArFragment().getArSceneView();
        // Coming from https://codelabs.developers.google.com/codelabs/sceneform-intro/index.html?index=..%2F..%2Fio2018#14
    
        // Create a bitmap the size of the scene view.
        Bitmap pendingBitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(),
                Bitmap.Config.ARGB_8888);
    
        // Create a handler thread to offload the processing of the image.
        final HandlerThread handlerThread = new HandlerThread("PixelCopier");
        handlerThread.start();
        // Make the request to copy.
        PixelCopy.request(view, pendingBitmap, (copyResult) -> {
            arActivity.runOnUiThread(() -> view.getPlaneRenderer().setVisible(true));
            if (copyResult == PixelCopy.SUCCESS) {
                String filename = generateFilename("jpg");
                if (saveBitmapToDisk(filename, pendingBitmap)) {
                    notifySystemOfNewMedia(new File(filename));
                    arActivity.runOnUiThread(this::updatePhotoPreview);
                }
            } else {
                Log.e(TAG, "Failed to copy pixels: " + copyResult);
                Toast toast = Toast.makeText(arActivity,
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
    private boolean saveBitmapToDisk(String filename, Bitmap pendingBitmap) {
        // Coming from https://codelabs.developers.google.com/codelabs/sceneform-intro/index.html?index=..%2F..%2Fio2018#14
        
        try (FileOutputStream outputStream = new FileOutputStream(filename);
             ByteArrayOutputStream outputData = new ByteArrayOutputStream()) {
            pendingBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputData);
            outputData.writeTo(outputStream);
            outputStream.flush();
            outputStream.close();
            
            // Save image orientation based on device orientation
            ExifInterface exifInterface = new ExifInterface(filename);
            switch (arActivity.getOrientation()) {
                case 0:
                    exifInterface.setAttribute(ExifInterface.TAG_ORIENTATION,
                            String.valueOf(ExifInterface.ORIENTATION_NORMAL));
                    break;
                case 90:
                    exifInterface.setAttribute(ExifInterface.TAG_ORIENTATION,
                            String.valueOf(ExifInterface.ORIENTATION_ROTATE_90));
                    break;
                case 180:
                    exifInterface.setAttribute(ExifInterface.TAG_ORIENTATION,
                            String.valueOf(ExifInterface.ORIENTATION_ROTATE_180));
                    break;
                case 270:
                    exifInterface.setAttribute(ExifInterface.TAG_ORIENTATION,
                            String.valueOf(ExifInterface.ORIENTATION_ROTATE_270));
                    break;
            }
            exifInterface.saveAttributes();
            
            registerNewMedia(new File(filename));
            return true;
        } catch (IOException ex) {
            Log.e(TAG, "Failed to save image " + ex.toString());
            Toast toast = Toast.makeText(arActivity,
                    "Failed to save image", Toast.LENGTH_LONG);
            toast.show();
            return false;
        }
    }
    
    @TargetApi(24)
    private void handleVideoRecording() {
        if (!videoRecorder.isRecording()) {
            videoRecorder.setVideoQuality(CamcorderProfile.QUALITY_1080P,
                    arActivity.getResources().getConfiguration().orientation);
            videoRecorder.setVideoRotation(arActivity.getOrientation());
        }
        
        boolean recording = videoRecorder.onToggleRecord();
        
        if (recording) {
            videoModeButton.animate().alpha(0f);
            new MediaActionSound().play(MediaActionSound.START_VIDEO_RECORDING);
            drawShutterVideoRecording();
        } else {
            videoModeButton.animate().alpha(1f);
            new MediaActionSound().play(MediaActionSound.STOP_VIDEO_RECORDING);
            drawShutterVideoReady();
            // Don't let another recording start until this one has finished being written
            shutterButton.setClickable(false);
        }
    }
    
    /**
     * This is run once the video file has finished being written
     */
    private void onVideoSaved() {
        arActivity.runOnUiThread(() -> {
            shutterButton.setClickable(true);
            File path = videoRecorder.getVideoPath();
            
            registerNewMedia(path);
            updatePhotoPreview();
            notifySystemOfNewMedia(path);
            arActivity.getArFragment().getArSceneView().getPlaneRenderer().setVisible(true);
        });
    }
    
    /**
     * Shows the most recently-taken photo in the screen corner.
     */
    private void updatePhotoPreview() {
        if (imageUris.size() > 0) {
            // Loading thumbnails for videos can be slow, so make sure the animation doesn't
            // start until the thumbnail is ready
            GlideApp.with(arActivity).load(imageUris.get(0))
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            Log.e(TAG, "Glide load failed for preview image");
                            return false;
                        }
    
                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            photoPreview.setVisibility(View.VISIBLE);
    
                            // Animate the preview image's appearance
                            if (photoPreview.isAttachedToWindow()) {
                                int cx = photoPreview.getLayoutParams().height / 2;
                                int cy = photoPreview.getLayoutParams().width / 2;
                                Animator anim = ViewAnimationUtils.createCircularReveal(
                                        photoPreview, cx, cy, 0f, 2 * cx);
//                                anim.setInterpolator(new FastOutSlowInInterpolator());
                                anim.start();
                            }
                            return false;
                        }
                    })
                    .circleCrop().into(photoPreview);
        } else {
            // Animate the preview image's disappearance
            if (photoPreview.isAttachedToWindow()) {
                int cx = photoPreview.getLayoutParams().height / 2;
                int cy = photoPreview.getLayoutParams().width / 2;
                Animator anim = ViewAnimationUtils.createCircularReveal(
                        photoPreview, cx, cy, 2 * cx, 0f);
                anim.addListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animator) {}
            
                    @Override
                    public void onAnimationEnd(Animator animator) {
                        photoPreview.setVisibility(View.GONE);
                    }
            
                    @Override
                    public void onAnimationCancel(Animator animator) {
                        photoPreview.setVisibility(View.GONE);
                    }
            
                    @Override
                    public void onAnimationRepeat(Animator animator) {}
                });
                anim.setStartDelay(250);
                anim.start();
            } else {
                photoPreview.setVisibility(View.GONE);
            }
        }
    }
    
    /**
     * Generates a date/time-based filename for a picture ready to be saved.
     */
    @TargetApi(24)
    private static String generateFilename(String suffix) {
        if (!suffix.startsWith("."))
            suffix = "." + suffix;
        
        String date = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss",
                java.util.Locale.getDefault()).format(new Date());
        String base = generatePhotoRootPath() + date;
        if (new File(base + suffix).exists()) {
            int i = 2;
            while (new File(base + "_" + i + suffix).exists())
                i++;
            base += "_" + i;
        }
        String out = base + suffix;
        
        File dir = new File(out).getParentFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return out;
    }
    
    private static String generatePhotoRootPath() {
        return Environment.getExternalStorageDirectory() + File.separator + "DCIM"
                + File.separator + "Finn Stickers/";
    }
    
    private void notifySystemOfNewMedia(File path) {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        mediaScanIntent.setData(Uri.fromFile(path));
        arActivity.sendBroadcast(mediaScanIntent);
    }
    
    /**
     * Performs a shutter animation by fading the AR view to black shortly. Also plays a
     * shutter sound.
     */
    private void shutterAnimation() {
        // Based on https://stackoverflow.com/questions/23960221/android-make-screen-flash-white
        shutterFlash.setImageAlpha(0);
        shutterFlash.setVisibility(View.VISIBLE);
        
        Animation a = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime,
                                               Transformation t) {
                if (interpolatedTime == 1) {
                    shutterFlash.setImageAlpha(0);
                    shutterFlash.setVisibility(View.GONE);
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
                    shutterFlash.setImageAlpha(newAlpha);
                }
            }
            
            @Override
            public boolean willChangeBounds() {
                return false;
            }
        };
        
        final int time = arActivity.getResources().getInteger(
                android.R.integer.config_mediumAnimTime);
        a.setDuration(time);
        shutterFlash.startAnimation(a);
        
        new MediaActionSound().play(MediaActionSound.SHUTTER_CLICK);
    }
    
    private void registerNewMedia(File path) {
        imagePaths.add(0, path);
        imageUris.add(0, generateSharableUri(path));
    }
    
    private Uri generateSharableUri(File path) {
        return FileProvider.getUriForFile(arActivity,
                "net.samvankooten.finnstickers.fileprovider", path);
    }
    
    List<View> getViewsToAnimate() {
        List<View> out = new ArrayList<>(3);
        out.add(videoModeButton);
        out.add(shutterButton);
        out.add(photoPreview);
        return out;
    }
}
