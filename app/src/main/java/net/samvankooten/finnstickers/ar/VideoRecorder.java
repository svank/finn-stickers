/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.samvankooten.finnstickers.ar;

import android.content.res.Configuration;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import com.google.ar.sceneform.SceneView;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;

/**
 * From SceneForm video recording demo
 * https://github.com/google-ar/sceneform-android-sdk/blob/v1.15.0/samples/videorecording/app/src/main/java/com/google/ar/sceneform/samples/videorecording/VideoRecorder.java
 * Video Recorder class handles recording the contents of a SceneView. It uses MediaRecorder to
 * encode the video. The quality settings can be set explicitly or simply use the CamcorderProfile
 * class to select a predefined set of parameters.
 */
class VideoRecorder {
    private static final String TAG = "VideoRecorder";
    private static final int DEFAULT_VIDEO_BITRATE = 2500000;
    private static final int DEFAULT_FRAMERATE = 30;
    private static final int DEFAULT_AUDIO_BITRATE = 14250;
    private static final int DEFAULT_AUDIO_SAMPLE_RATE = 48000;
    
    // recordingVideoFlag is true when the media recorder is capturing video.
    private boolean recordingVideoFlag;
    
    private MediaRecorder mediaRecorder;
    
    private Size videoSize;
    
    private SceneView sceneView;
    private int videoCodec;
    private File videoPath;
    private FileDescriptor videoFileDescriptor;
    private Uri videoUri;
    private int bitRate = DEFAULT_VIDEO_BITRATE;
    private int frameRate = DEFAULT_FRAMERATE;
    private int videoRotation = 0;
    private Surface encoderSurface;
    private PostSaveCallback postSaveCallback;
    
    interface PostSaveCallback {
        void onSaveCompleted();
    }
    
    private static final int[] FALLBACK_QUALITY_LEVELS = {
            CamcorderProfile.QUALITY_HIGH,
            CamcorderProfile.QUALITY_2160P,
            CamcorderProfile.QUALITY_1080P,
            CamcorderProfile.QUALITY_720P,
            CamcorderProfile.QUALITY_480P
    };
    
    public VideoRecorder() {
        recordingVideoFlag = false;
    }
    
    public File getVideoPath() {
        return videoPath;
    }
    
    public Uri getVideoUri() {
        return videoUri;
    }
    
    public void setVideoPath(File path) {
        videoPath = path;
        videoFileDescriptor = null;
        videoUri = null;
    }
    
    public void setVideoFileDescriptor(FileDescriptor fd, Uri uri) {
        videoFileDescriptor = fd;
        videoUri = uri;
        videoPath = null;
    }
    
    public void setBitRate(int bitRate) {
        this.bitRate = bitRate;
    }
    
    public void setFrameRate(int frameRate) {
        this.frameRate = frameRate;
    }
    
    public void setSceneView(SceneView sceneView) {
        this.sceneView = sceneView;
    }
    
    public void setPostSaveCallback(PostSaveCallback callback) {
        postSaveCallback = callback;
    }
    
    /**
     * Toggles the state of video recording.
     *
     * @return true if recording is now active.
     */
    public boolean onToggleRecord(boolean recordAudio, boolean stopSynchronously) {
        if (recordingVideoFlag) {
            stopRecordingVideo(stopSynchronously);
        } else {
            startRecordingVideo(recordAudio);
        }
        return recordingVideoFlag;
    }
    
    private void startRecordingVideo(boolean recordAudio) {
        if (mediaRecorder == null) {
            mediaRecorder = new MediaRecorder();
        }
        
        try {
            setUpMediaRecorder(recordAudio);
        } catch (IOException e) {
            Log.e(TAG, "Exception setting up recorder", e);
            return;
        }
        
        // Set up Surface for the MediaRecorder
        encoderSurface = mediaRecorder.getSurface();
        
        sceneView.startMirroringToSurface(
                encoderSurface, 0, 0, videoSize.getWidth(), videoSize.getHeight());
        
        recordingVideoFlag = true;
    }
    
    private void stopRecordingVideo(boolean synchronous) {
        // UI
        recordingVideoFlag = false;
        
        if (encoderSurface != null) {
            sceneView.stopMirroringToSurface(encoderSurface);
            encoderSurface = null;
        }
        
        // Stop recording
        if (synchronous)
            doStop();
        else {
            // This task takes ~half a second, so I'm doing it in a background thread
            AsyncTask.execute(this::doStop);
        }
    }
    
    private void doStop() {
        mediaRecorder.stop();
        mediaRecorder.reset();
        postSaveCallback.onSaveCompleted();
    }
    
    private void setUpMediaRecorder(boolean recordAudio) throws IOException {
    
        if (recordAudio)
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
    
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        
        if (videoFileDescriptor != null)
            mediaRecorder.setOutputFile(videoFileDescriptor);
        else
            mediaRecorder.setOutputFile(videoPath.getAbsolutePath());
        mediaRecorder.setVideoEncodingBitRate(bitRate);
        mediaRecorder.setVideoFrameRate(frameRate);
        mediaRecorder.setVideoSize(videoSize.getWidth(), videoSize.getHeight());
        mediaRecorder.setVideoEncoder(videoCodec);
        
        if (recordAudio) {
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB);
            mediaRecorder.setAudioEncodingBitRate(DEFAULT_AUDIO_BITRATE);
            mediaRecorder.setAudioSamplingRate(DEFAULT_AUDIO_SAMPLE_RATE);
        }
        
        mediaRecorder.setOrientationHint(videoRotation);
        
        mediaRecorder.prepare();
        
        // Delay to avoid catching the "start recording" sound effect
        sceneView.postDelayed(() -> {
            try {
                mediaRecorder.start();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Exception starting capture: " + e.getMessage(), e);
            }
        }, 450);
    }
    
    public void setVideoSize(int width, int height) {
        videoSize = new Size(width, height);
    }
    
    public void setVideoQuality(int quality, int orientation) {
        CamcorderProfile profile = null;
        if (CamcorderProfile.hasProfile(quality)) {
            profile = CamcorderProfile.get(quality);
        }
        if (profile == null) {
            // Select a quality  that is available on this device.
            for (int level : FALLBACK_QUALITY_LEVELS) {
                if (CamcorderProfile.hasProfile(level)) {
                    profile = CamcorderProfile.get(level);
                    break;
                }
            }
        }
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight);
        } else {
            //noinspection SuspiciousNameCombination
            setVideoSize(profile.videoFrameHeight, profile.videoFrameWidth);
        }
        setVideoCodec(profile.videoCodec);
        setBitRate(profile.videoBitRate);
        setFrameRate(profile.videoFrameRate);
    }
    
    public void setVideoRotation(int rotation) {
        videoRotation = rotation;
    }
    
    public void setVideoCodec(int videoCodec) {
        this.videoCodec = videoCodec;
    }
    
    public boolean isRecording() {
        return recordingVideoFlag;
    }
}