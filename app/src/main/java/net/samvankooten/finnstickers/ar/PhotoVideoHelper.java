package net.samvankooten.finnstickers.ar;

import android.Manifest;
import android.animation.Animator;
import android.annotation.TargetApi;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.media.CamcorderProfile;
import android.media.MediaActionSound;
import android.net.Uri;
import android.os.Build;
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
import com.google.android.material.snackbar.Snackbar;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.Scene;
import com.stfalcon.imageviewer.StfalconImageViewer;

import net.samvankooten.finnstickers.LightboxOverlayView;
import net.samvankooten.finnstickers.R;
import net.samvankooten.finnstickers.editor.EditorActivity;
import net.samvankooten.finnstickers.misc_classes.GlideApp;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import static net.samvankooten.finnstickers.ar.AROnboardActivity.LAUNCH_AR;
import static net.samvankooten.finnstickers.ar.AROnboardActivity.ONLY_PERMISSIONS;
import static net.samvankooten.finnstickers.ar.AROnboardActivity.PROMPT_ARCORE_INSTALL;

@TargetApi(24)
class PhotoVideoHelper {
    private static final String TAG = "PhotoVideoHelper";
    
    private final ARActivity arActivity;
    private final VideoRecorder videoRecorder;
    private final IOHelper ioHelper;
    private boolean videoMode = false;
    private List<Uri> imageUris;
    private final FloatingActionButton shutterButton;
    private final FloatingActionButton videoModeButton;
    private final ImageView photoPreview;
    private final ImageView shutterFlash;
    private int saveImageCountdown = -1;
    private Scene.OnUpdateListener listener;
    private final MediaActionSound mediaActionSound = new MediaActionSound();
    
    PhotoVideoHelper(ARActivity activity) {
        arActivity = activity;
        if (Build.VERSION.SDK_INT >= 30)
            ioHelper = new IOHelperSAF(activity);
        else
            ioHelper = new IOHelperDirect(activity);
        
        shutterButton = arActivity.findViewById(R.id.shutter_button);
        shutterButton.setOnClickListener(view -> onCapture());
        TooltipCompat.setTooltipText(shutterButton, activity.getString(R.string.take_photo));
    
        videoModeButton = arActivity.findViewById(R.id.mode_switch);
        videoModeButton.setOnClickListener(view -> toggleVideoMode());
        TooltipCompat.setTooltipText(videoModeButton, activity.getString(R.string.switch_to_video));
    
        videoRecorder = new VideoRecorder();
        videoRecorder.setSceneView(arActivity.getArFragment().getArSceneView());
        videoRecorder.setPostSaveCallback(this::onVideoSaved);
        
        shutterFlash = arActivity.findViewById(R.id.shutter_flash);
        
        photoPreview = arActivity.findViewById(R.id.photo_preview);
        photoPreview.setVisibility(View.GONE);
        TooltipCompat.setTooltipText(photoPreview, activity.getString(R.string.photo_preview));
    
        // Launch a full-screen image viewer when the preview is clicked.
        photoPreview.setClickable(true);
        photoPreview.setOnClickListener((v) -> {
            if (imageUris.size() == 0)
                return;
            if (videoRecorder.isRecording())
                return;
            LightboxOverlayView overlay = new LightboxOverlayView(
                    arActivity, imageUris, 0, true);
        
            StfalconImageViewer<Uri> viewer = new StfalconImageViewer.Builder<Uri>(arActivity, imageUris,
                    (view, image) -> GlideApp.with(arActivity).load(image).into(view),
                    CustomViewHolder::buildViewHolder)
                    .withStartPosition(0)
                    .withOverlayView(overlay)
                    .withImageChangeListener(overlay::setPos)
                    .withHiddenStatusBar(false)
                    .withTransitionFrom(photoPreview)
                    .show();
            overlay.setViewer(viewer);
            overlay.setGetTransitionImageCallback(pos -> photoPreview);
        
            overlay.setOnDeleteCallback(item -> {
                int success =
                        arActivity.getContentResolver().delete(item, null, null);
                if (success == 0) {
                    Log.e(TAG, "Error deleting file: " + item);
                    Toast.makeText(arActivity,
                            "Failed to save image", Toast.LENGTH_LONG).show();
                    return false;
                }
                
                // imageUris is updated by the overlay, and we can't update the preview
                // until that happens
                photoPreview.postDelayed(this::updatePhotoPreview, 10);
                return true;
            });
            
            List<Boolean> editable = new ArrayList<>(imageUris.size());
            for (Uri imageUri : imageUris) {
                editable.add(imageUri.toString().endsWith(".jpg"));
            }
            
            overlay.setAreEditable(editable);
            
            overlay.setOnEditCallback(pos -> {
                Intent intent = new Intent(arActivity, EditorActivity.class);
                intent.setAction(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_STREAM, imageUris.get(pos));
                intent.setType("image/jpeg");
                arActivity.startActivity(intent);
            });
        });
    
        imageUris = new LinkedList<>();
        populatePastImages();
        
        mediaActionSound.load(MediaActionSound.START_VIDEO_RECORDING);
        mediaActionSound.load(MediaActionSound.STOP_VIDEO_RECORDING);
        mediaActionSound.load(MediaActionSound.SHUTTER_CLICK);
    }
    
    private void toggleVideoMode() {
        if (videoRecorder.isRecording())
            return;
        
        videoMode = !videoMode;
        
        updateShutterButton();
    }
    
    
    private void updateShutterButton() {
        if (videoMode)
            drawShutterVideoReady();
        else
            drawShutterPhotoMode();
    }
    
    private void drawShutterPhotoMode() {
        shutterButton.setBackgroundTintList(
                ColorStateList.valueOf(arActivity.getColor(R.color.colorAccent)));
        shutterButton.setSupportImageTintList(
                ColorStateList.valueOf(arActivity.getColor(android.R.color.white)));
        shutterButton.setImageResource(R.drawable.icon_camera);
        shutterButton.setContentDescription(arActivity.getString(R.string.take_photo));
        TooltipCompat.setTooltipText(shutterButton, arActivity.getString(R.string.take_photo));
        videoModeButton.setContentDescription(arActivity.getString(R.string.switch_to_video));
        TooltipCompat.setTooltipText(videoModeButton, arActivity.getString(R.string.switch_to_video));
    }
    
    private void drawShutterVideoReady() {
        shutterButton.setBackgroundTintList(
                ColorStateList.valueOf(arActivity.getColor(R.color.recordBackground)));
        shutterButton.setSupportImageTintList(
                ColorStateList.valueOf(arActivity.getColor(R.color.recordForeground)));
        shutterButton.setImageResource(R.drawable.icon_record_start);
        shutterButton.setContentDescription(arActivity.getString(R.string.take_video));
        TooltipCompat.setTooltipText(shutterButton, arActivity.getString(R.string.take_video));
        videoModeButton.setContentDescription(arActivity.getString(R.string.switch_to_photo));
        TooltipCompat.setTooltipText(videoModeButton, arActivity.getString(R.string.switch_to_photo));
    }
    
    private void drawShutterVideoRecording() {
        shutterButton.setBackgroundTintList(
                ColorStateList.valueOf(arActivity.getColor(R.color.recordForeground)));
        shutterButton.setSupportImageTintList(
                ColorStateList.valueOf(arActivity.getColor(R.color.recordBackground)));
        shutterButton.setImageResource(R.drawable.icon_record_stop);
        shutterButton.setContentDescription(arActivity.getString(R.string.take_video_stop));
        TooltipCompat.setTooltipText(shutterButton, arActivity.getString(R.string.take_video_stop));
        videoModeButton.setContentDescription(arActivity.getString(R.string.switch_to_photo));
        TooltipCompat.setTooltipText(videoModeButton, arActivity.getString(R.string.switch_to_photo));
    }
    
    /**
     * Finds all previously-taken photos and sets up the preview widget.
     */
    private void populatePastImages() {
        ioHelper.loadPastImages(imageUris);
        
        if (imageUris.size() > 0)
            updatePhotoPreview();
    }
    
    /**
     * Begin the process of taking a picture, which is finished in actuallyRecordMedia().
     */
    private void onCapture() {
        if (!ioHelper.haveExtPermission()) {
            onNoExtStoragePermission();
            return;
        }
        // We don't want that grid of dots marking the surface in our image. We can disable that
        // and re-enable it after the image is saved. Unfortunately, disabling it doesn't take
        // effect until after another frame is rendered. We can set a callback for when that
        // rendering is complete, but the callback is triggered right *before* the frame is
        // updated, so we have to wait *two* frames.
        ArSceneView view = arActivity.getArFragment().getArSceneView();
        view.getPlaneRenderer().setVisible(false);
        
        // If any objects are selected, we want to remove that ring from underneath them.
        arActivity.getArFragment().getTransformationSystem().getSelectionVisualizer().removeSelectionVisual(null);
        arActivity.setSelectedNode(null);
        
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
                Uri uri = ioHelper.saveImage(pendingBitmap, arActivity.getOrientation());
                if (uri != null) {
                    registerNewMedia(uri);
                    arActivity.runOnUiThread(this::updatePhotoPreview);
                } else {
                    arActivity.runOnUiThread(() -> Toast.makeText(arActivity,
                            "Failed to save image", Toast.LENGTH_LONG).show());
                }
            } else {
                Log.e(TAG, "Failed to copy pixels: " + copyResult);
                arActivity.runOnUiThread(() -> Toast.makeText(arActivity,
                        "Failed to save image", Toast.LENGTH_LONG).show());
            }
            handlerThread.quitSafely();
        }, new Handler((handlerThread.getLooper())));
    }
    
    private void handleVideoRecording() {
        if (!videoRecorder.isRecording()) {
            videoRecorder.setVideoQuality(CamcorderProfile.QUALITY_1080P,
                    arActivity.getResources().getConfiguration().orientation);
            videoRecorder.setVideoRotation(arActivity.getOrientation());
            ioHelper.prepareVideoRecorder(videoRecorder);
            mediaActionSound.play(MediaActionSound.START_VIDEO_RECORDING);
        }
        
        boolean micPerm = haveMicPermission();
        
        if (!micPerm && !videoRecorder.isRecording())
            onNoMicPermission();
        
        boolean recording = videoRecorder.onToggleRecord(micPerm, false);
        
        if (recording) {
            photoPreview.setClickable(false);
            CustomSelectionVisualizer.setShouldShowVisualizer(false);
            videoModeButton.animate().alpha(0f);
            drawShutterVideoRecording();
            
            // If the user rapidly stops recording, mediaRecorder will raise an exception.
            // Easy fix: add a cooldown
            shutterButton.setClickable(false);
            shutterButton.postDelayed(() -> shutterButton.setClickable(true), 500);
        } else {
            CustomSelectionVisualizer.setShouldShowVisualizer(true);
            videoModeButton.animate().alpha(1f);
            mediaActionSound.play(MediaActionSound.STOP_VIDEO_RECORDING);
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
            Uri uri = ioHelper.finishVideoRecording(videoRecorder);
            registerNewMedia(uri);
            updatePhotoPreview();
            arActivity.getArFragment().getArSceneView().getPlaneRenderer().setVisible(true);
            photoPreview.setClickable(true);
            shutterButton.setClickable(true);
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
                    .listener(new RequestListener<>() {
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
    
    private void stopIfRecordingVideo(boolean stopSynchronously) {
        if (videoRecorder.isRecording())
            videoRecorder.onToggleRecord(false, stopSynchronously);
    }
    
    void onPause() {
        stopIfRecordingVideo(true);
        for (WeakReference<CustomViewHolder<Uri>> wr : CustomViewHolder.holders) {
            CustomViewHolder<Uri> vh = wr.get();
            if (vh != null)
                vh.suspendPlayback();
        }
    }
    
    void ensureUIReady() {
        if (videoMode)
            drawShutterVideoReady();
        videoModeButton.setAlpha(1f);
        updatePhotoPreview();
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
        
        mediaActionSound.play(MediaActionSound.SHUTTER_CLICK);
    }
    
    private void registerNewMedia(Uri uri) {
        imageUris.add(0, uri);
    }
    
    List<View> getViewsToAnimate() {
        List<View> out = new ArrayList<>(3);
        out.add(videoModeButton);
        out.add(shutterButton);
        out.add(photoPreview);
        return out;
    }
    
    private boolean haveMicPermission() {
        return ContextCompat.checkSelfPermission(arActivity, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }
    
    private void onNoExtStoragePermission() {
        AlertDialog.Builder builder = new AlertDialog.Builder(arActivity);
        builder.setMessage(R.string.need_ext_storage_perm);
        builder.setPositiveButton(android.R.string.ok, (btn, which) ->
            launchPermRequest());
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.create().show();
    }
    
    private void onNoMicPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(arActivity,
                Manifest.permission.RECORD_AUDIO)) {
            Snackbar.make(arActivity.findViewById(R.id.main_layout),
                    R.string.need_mic_perm, Snackbar.LENGTH_LONG)
                    .setAction(R.string.need_mic_perm_action, (btn) -> launchPermRequest())
                    .show();
        }
    }
    
    private void launchPermRequest() {
        Intent intent = new Intent(arActivity, AROnboardActivity.class);
        intent.putExtra(LAUNCH_AR, false);
        intent.putExtra(ONLY_PERMISSIONS, true);
        intent.putExtra(PROMPT_ARCORE_INSTALL, false);
        arActivity.startActivity(intent);
    }
}
