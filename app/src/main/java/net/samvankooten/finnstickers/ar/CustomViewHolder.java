package net.samvankooten.finnstickers.ar;

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
    private boolean active = false;
    private boolean haveVideo = false;
    
    public static CustomViewHolder<Uri> buildViewHolder(ImageView imageView) {
        FrameLayout parent = new FrameLayout(imageView.getContext());
        VideoView videoView = new VideoView(imageView.getContext());
        
        parent.addView(videoView);
        parent.addView(imageView);
        
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) videoView.getLayoutParams();
        params.gravity = Gravity.CENTER;
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        videoView.setLayoutParams(params);
        
        return new CustomViewHolder<>(parent, imageView, videoView);
    }
    
    private CustomViewHolder(View parentView, ImageView iv, VideoView vv) {
        super(parentView);
        imageView = iv;
        videoView = vv;
    }
    
    @Override
    public void bind(int position, T uri) {
        String src = uri.toString();
        if (videoView.isPlaying())
            videoView.stopPlayback();
        
        if (src.endsWith(".mp4")) {
            haveVideo = true;
            imageView.setVisibility(View.GONE);
            
            videoView.setVideoURI(Uri.parse(src));
            videoView.setOnPreparedListener(mediaPlayer -> {
                mediaPlayer.setLooping(true);
                mediaPlayer.start();
                if (!active)
                    videoView.pause();
                // Show videoView here to avoid flicker after the open transition
                videoView.setVisibility(View.VISIBLE);
            });
            videoView.setOnErrorListener((mp, what, extra) -> {
                videoView.stopPlayback();
                imageView.setImageDrawable(imageView.getContext().getDrawable(R.drawable.icon_error));
                imageView.setVisibility(View.VISIBLE);
                videoView.setVisibility(View.GONE);
                return true;
            });
            videoView.start();
        } else {
            haveVideo = false;
            imageView.setVisibility(View.VISIBLE);
            videoView.setVisibility(View.GONE);
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
        
        if (!haveVideo)
            return;
        
        // Pause videos as they scroll off-screen, and play videos as they scroll on-screen
        if (isVisible)
            videoView.resume();
        else {
            videoView.pause();
            videoView.seekTo(0);
        }
    }
}