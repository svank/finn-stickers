package net.samvankooten.finnstickers;

/*
Based on the SampleSlide in the AppIntro library example app,
which is Apache 2.0-licensed
https://github.com/AppIntro/AppIntro
 */

import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class OnboardSlide extends Fragment {
    
    private static final String TAG = "OnboardSlide";
    private static final String ARG_LAYOUT_RES_ID = "layoutResId";
    private static final String VIDEO_URI = "videoUri";
    private static final String IMAGE_DRAWABLE_ID = "imageDrawableId";
    private static final String TITLE = "title";
    private static final String TITLE_ID = "title_id";
    private static final String TEXT = "text";
    private static final String TEXT_ID = "text_id";
    private static final String LAYOUT_RES_ID = "layoutResId";
    
    private int layoutResId;
    private Uri videoUri;
    private int imageDrawableId;
    private String title;
    private int titleId;
    private String text;
    private int textId;
    private VideoView videoView;
    
    public static OnboardSlide newInstance(int layoutResId) {
        OnboardSlide onboardSlide = new OnboardSlide();
        
        Bundle args = new Bundle();
        args.putInt(ARG_LAYOUT_RES_ID, layoutResId);
        onboardSlide.setArguments(args);
        
        return onboardSlide;
    }
    
    @Override
    public void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        
        outState.putInt(IMAGE_DRAWABLE_ID, imageDrawableId);
        if (videoUri != null)
            outState.putString(VIDEO_URI, videoUri.toString());
        outState.putString(TITLE, title);
        outState.putInt(TITLE_ID, titleId);
        outState.putString(TEXT, text);
        outState.putInt(TEXT_ID, textId);
        outState.putInt(LAYOUT_RES_ID, layoutResId);
    }
    
    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        
        loadBundle(savedInstanceState);
    }
    
    private void loadBundle(final Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            imageDrawableId = savedInstanceState.getInt(IMAGE_DRAWABLE_ID);
            String uri = savedInstanceState.getString(VIDEO_URI);
            if (uri != null)
                videoUri = Uri.parse(uri);
            title = savedInstanceState.getString(TITLE);
            titleId = savedInstanceState.getInt(TITLE_ID);
            text = savedInstanceState.getString(TEXT);
            textId = savedInstanceState.getInt(TEXT_ID);
            layoutResId = savedInstanceState.getInt(LAYOUT_RES_ID);
        }
    }
    
    public void setVideoUri(Uri uri) {
        videoUri = uri;
    }
    
    public void setImageDrawable(int drawable) {
        imageDrawableId = drawable;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public void setTitle(int title) {
        this.titleId = title;
    }
    
    public void setText(String text) {
        this.text = text;
    }
    
    public void setText(int text) {
        this.textId = text;
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
        
        Drawable imageDrawable = null;
        if (imageDrawableId != 0)
            imageDrawable = getResources().getDrawable(imageDrawableId);
        if (textId != 0)
            text = getResources().getString(textId);
        if (titleId != 0)
            title= getResources().getString(titleId);
        
        if (videoUri == null)
            view.findViewById(R.id.video).setVisibility(View.GONE);
        else {
            videoView = view.findViewById(R.id.video);
            videoView.setVideoURI(videoUri);
    
            if (Build.VERSION.SDK_INT >= 26)
                videoView.setAudioFocusRequest(AudioManager.AUDIOFOCUS_NONE);
            
            videoView .setOnPreparedListener(mediaPlayer -> {
                mediaPlayer.setLooping(true);
                mediaPlayer.start();
            });
            videoView.start();
        }
        
        if (imageDrawable == null)
            view.findViewById(R.id.image).setVisibility(View.GONE);
        else
            ((ImageView) view.findViewById(R.id.image)).setImageDrawable(imageDrawable);
        
        if (title != null)
            ((TextView) view.findViewById(R.id.title)).setText(title);
        
        if (text != null)
            ((TextView) view.findViewById(R.id.text)).setText(text);
        
        return view;
    }
    
    public void seekToStartIfVideo() {
        if (videoView != null)
            videoView.seekTo(0);
    }
}