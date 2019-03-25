package net.samvankooten.finnstickers.ar;

import android.content.Context;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.VideoView;

import com.bumptech.glide.Glide;
import com.stfalcon.imageviewer.viewer.viewholder.DefaultViewHolder;

import net.samvankooten.finnstickers.R;

public class CustomViewHolder<T> extends DefaultViewHolder<T> {
    private final ImageView imageView;
    private final VideoView videoView;
    private final ImageView playButton;
    private boolean active = false;
    private boolean haveVideo = false;
    
    private T currentItem;
    
    public static CustomViewHolder<Uri> buildViewHolder(ImageView imageView) {
        Context context = imageView.getContext();
        FrameLayout parent = new FrameLayout(context);
        VideoView videoView = new VideoView(context);
        ImageView playButton = new ImageView(context);
        
        parent.addView(videoView);
        parent.addView(imageView);
        parent.addView(playButton);
        
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) videoView.getLayoutParams();
        params.gravity = Gravity.CENTER;
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        videoView.setLayoutParams(params);
        
        playButton.setImageDrawable(context.getDrawable(R.drawable.icon_play_in_circle));
        playButton.setContentDescription(context.getString(R.string.play_button));
        params = (FrameLayout.LayoutParams) playButton.getLayoutParams();
        params.gravity = Gravity.CENTER;
        params.height = (int) context.getResources().getDimension(R.dimen.ar_play_button_size);
        params.width = params.height;
        playButton.setLayoutParams(params);
        
        return new CustomViewHolder<>(parent, imageView, videoView, playButton);
    }
    
    private CustomViewHolder(View parentView, ImageView iv, VideoView vv, ImageView pb) {
        super(parentView);
        imageView = iv;
        videoView = vv;
        playButton = pb;
        
        playButton.setOnClickListener(view -> {
            playButton.setVisibility(View.GONE);
            
            videoView.stopPlayback();
            videoView.setVideoURI(Uri.parse(currentItem.toString()));
            videoView.setOnPreparedListener(mediaPlayer -> {
                mediaPlayer.setLooping(true);
                mediaPlayer.start();
                // Show videoView here to avoid flicker after the open transition
                videoView.setVisibility(View.VISIBLE);
                imageView.setVisibility(View.GONE);
            });
            videoView.setOnErrorListener((mp, what, extra) -> {
                videoView.stopPlayback();
                imageView.setImageDrawable(imageView.getContext().getDrawable(R.drawable.icon_error));
                imageView.setVisibility(View.VISIBLE);
                videoView.setVisibility(View.GONE);
                return true;
            });
            videoView.start();
        });
    }
    
    @Override
    public void bind(int position, T uri) {
        currentItem = uri;
        
        String src = uri.toString();
        
        if (src.endsWith(".mp4")) {
            haveVideo = true;
//            imageView.setVisibility(View.GONE);
            playButton.setVisibility(View.VISIBLE);
            Glide.with(imageView.getContext()).load(uri).error(R.drawable.icon_error).into(imageView);
        } else {
            haveVideo = false;
            imageView.setVisibility(View.VISIBLE);
            videoView.setVisibility(View.GONE);
            playButton.setVisibility(View.GONE);
            Glide.with(imageView.getContext()).load(uri).error(R.drawable.icon_error).into(imageView);
        }
    }
    
    @Override
    public void onDialogClosed() {
        // The VideoView must be stopped and hidden as the dialog begins to close,
        // or we'll keep seeing the video under the animation due to how SurfaceViews work.
        // But by delaying that, we avoid some flicker as the close animation starts.
        videoView.postDelayed(() -> {
            videoView.setVisibility(View.GONE);
            if (videoView.isPlaying())
                videoView.stopPlayback();
        }, 10);
    }
    
    @Override
    public void setIsVisible(boolean isVisible) {
        if (isVisible == active)
            return;
        
        active = isVisible;
        
        // Stop videos as they scroll off-screen
        if (!active && haveVideo && videoView.isPlaying()) {
            videoView.pause();
            videoView.setVisibility(View.GONE);
            playButton.setVisibility(View.VISIBLE);
            imageView.setVisibility(View.VISIBLE);
        }
    }
}