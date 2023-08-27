package net.samvankooten.finnstickers;

/*
Based on the SampleSlide in the AppIntro library example app,
which is Apache 2.0-licensed
https://github.com/AppIntro/AppIntro
 */

import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

public class OnboardSlide extends Fragment {
    
    private static final String TAG = "OnboardSlide";
    private static final String ARG_LAYOUT_RES_ID = "layoutResId";
    private static final String VIDEO_URI = "videoUri";
    private static final String IMAGE_DRAWABLE_ID = "imageDrawableId";
    private static final String FALLBACK_IMAGE_DRAWABLE_ID = "fallbackImageDrawableId";
    private static final String TITLE_ID = "title_id";
    private static final String TEXT_ID = "text_id";
    private static final String DISCLAIMER_ID = "discl_id";
    private static final String LAYOUT_RES_ID = "layoutResId";
    
    private int layoutResId;
    private Uri videoUri;
    private int imageDrawableId;
    private int fallbackImageDrawableId;
    private int titleId;
    private int textId;
    private int disclaimerId;
    private VideoView videoView;
    
    public static OnboardSlide newInstance(int layoutResId) {
        OnboardSlide onboardSlide = new OnboardSlide();
        
        Bundle args = new Bundle();
        args.putInt(ARG_LAYOUT_RES_ID, layoutResId);
        onboardSlide.setArguments(args);
        
        return onboardSlide;
    }
    
    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
    
        outState.putInt(IMAGE_DRAWABLE_ID, imageDrawableId);
        outState.putInt(FALLBACK_IMAGE_DRAWABLE_ID, fallbackImageDrawableId);
        if (videoUri != null)
            outState.putString(VIDEO_URI, videoUri.toString());
        outState.putInt(TITLE_ID, titleId);
        outState.putInt(TEXT_ID, textId);
        outState.putInt(DISCLAIMER_ID, disclaimerId);
        outState.putInt(LAYOUT_RES_ID, layoutResId);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        loadBundle(savedInstanceState);
    }
    
    private void loadBundle(final Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            imageDrawableId = savedInstanceState.getInt(IMAGE_DRAWABLE_ID);
            fallbackImageDrawableId = savedInstanceState.getInt(FALLBACK_IMAGE_DRAWABLE_ID);
            String uri = savedInstanceState.getString(VIDEO_URI);
            if (uri != null)
                videoUri = Uri.parse(uri);
            titleId = savedInstanceState.getInt(TITLE_ID);
            textId = savedInstanceState.getInt(TEXT_ID);
            disclaimerId = savedInstanceState.getInt(DISCLAIMER_ID);
            layoutResId = savedInstanceState.getInt(LAYOUT_RES_ID);
        }
    }
    
    public void setVideoUri(Uri uri) {
        videoUri = uri;
    }
    
    public void setImageDrawable(int drawable) {
        imageDrawableId = drawable;
    }
    
    public void setFallbackImageDrawable(int drawable) {
        fallbackImageDrawableId = drawable;
    }
    
    public void setTitle(int title) {
        this.titleId = title;
    }
    
    public void setText(int text) {
        this.textId = text;
    }
    
    public void setDisclaimer(int disclaimer) {
        this.disclaimerId = disclaimer;
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if (getArguments() != null && getArguments().containsKey(ARG_LAYOUT_RES_ID)) {
            layoutResId = getArguments().getInt(ARG_LAYOUT_RES_ID);
        }
        
        loadBundle(savedInstanceState);
    }
    
    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(layoutResId, container, false);
        
        if (videoUri == null)
            view.findViewById(R.id.video).setVisibility(View.GONE);
        else {
            videoView = view.findViewById(R.id.video);
            videoView.setVideoURI(videoUri);
    
            if (Build.VERSION.SDK_INT >= 26)
                videoView.setAudioFocusRequest(AudioManager.AUDIOFOCUS_NONE);
            
            videoView.setOnPreparedListener(mediaPlayer -> {
                mediaPlayer.setLooping(true);
                mediaPlayer.setScreenOnWhilePlaying(false);
                mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                    imageDrawableId = fallbackImageDrawableId;
                    videoUri = null;
                    videoView.setVisibility(View.GONE);
                    mediaPlayer.release();
                    setupImage(view);
                    return true;});
                mediaPlayer.start();
            });
            videoView.start();
        }
        
        setupImage(view);
        
        if (titleId != 0)
            ((TextView) view.findViewById(R.id.title)).setText(getText(titleId));
    
        if (textId != 0) {
            TextView tv = view.findViewById(R.id.text);
            tv.setText(getText(textId));
            tv.setMovementMethod(LinkMovementMethod.getInstance());
        }
        
        if (disclaimerId != 0) {
            TextView tv = view.findViewById(R.id.disclaimer);
            tv.setText(getText(disclaimerId));
            tv.setMovementMethod(LinkMovementMethod.getInstance());
        }
        
        return view;
    }
    
    private void setupImage(View view) {
        ImageView iv = view.findViewById(R.id.image);
        if (imageDrawableId == 0)
            iv.setVisibility(View.GONE);
        else {
            iv.setImageDrawable(ContextCompat.getDrawable(getContext(), imageDrawableId));
            iv.setVisibility(View.VISIBLE);
        }
    }
    
    public void seekToStartOfVideo() {
        if (videoView != null)
            videoView.seekTo(0);
    }
}