package net.samvankooten.finnstickers.ar;

import android.content.Context;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.stfalcon.imageviewer.viewer.viewholder.DefaultViewHolder;

import net.samvankooten.finnstickers.R;
import net.samvankooten.finnstickers.misc_classes.GlideApp;

public class CustomViewHolder<T> extends DefaultViewHolder<T> {
    private final ImageView imageView;
    private final PlayerView playerView;
    private final ImageView playButton;
    private final SimpleExoPlayer player;
    private boolean active = false;
    private boolean videoLoaded = false;
    
    private T currentItem;
    
    @SuppressWarnings("SuspiciousNameCombination")
    static CustomViewHolder<Uri> buildViewHolder(ImageView imageView) {
        Context context = imageView.getContext();
        FrameLayout parent = new FrameLayout(context);
        PlayerView playerView = new PlayerView(context);
        ImageView playButton = new ImageView(context);
        
        parent.addView(playerView);
        parent.addView(imageView);
        parent.addView(playButton);
        
        playButton.setImageDrawable(context.getDrawable(R.drawable.icon_play_in_circle));
        playButton.setContentDescription(context.getString(R.string.play_button));
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) playButton.getLayoutParams();
        params.gravity = Gravity.CENTER;
        params.height = (int) context.getResources().getDimension(R.dimen.ar_play_button_size);
        params.width = params.height;
        playButton.setLayoutParams(params);
        
        return new CustomViewHolder<>(parent, imageView, playerView, playButton);
    }
    
    private CustomViewHolder(View parentView, ImageView iv, PlayerView pv, ImageView pb) {
        super(parentView);
        imageView = iv;
        playerView = pv;
        playButton = pb;
        
        player = new SimpleExoPlayer.Builder(playerView.getContext()).build();
        player.setHandleAudioBecomingNoisy(true);
        playerView.setPlayer(player);
        
        playerView.setUseController(false);
        
        playButton.setOnClickListener(view -> {
            playButton.setVisibility(View.GONE);
    
            // I'm getting double taps, maybe from an interaction with StfalconImageViewer
            playerView.postDelayed(() -> {
                playerView.setClickable(true);
                playerView.getVideoSurfaceView().setOnClickListener(v -> pauseVideo());
            }, 500);
            
            if (videoLoaded) {
                player.setPlayWhenReady(true);
                return;
            }
            
            videoLoaded = true;
    
            DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(
                    playerView.getContext(),
                    Util.getUserAgent(playerView.getContext(), "finnstickers"));
            MediaSource videoSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(Uri.parse(currentItem.toString()));
            player.prepare(videoSource);
            player.setRepeatMode(Player.REPEAT_MODE_ALL);
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
            
            player.addListener(new Player.EventListener(){
                @Override
                public void onLoadingChanged(boolean isLoading) {
                    if (!isLoading) {
                        playerView.setVisibility(View.VISIBLE);
                        imageView.setVisibility(View.GONE);
                    }
                }
                @Override
                public void onPlayerError(ExoPlaybackException error) {
                    player.release();
                    imageView.setImageDrawable(imageView.getContext().getDrawable(R.drawable.icon_error));
                    imageView.setVisibility(View.VISIBLE);
                    playerView.setVisibility(View.GONE);
                }
            });
            player.setPlayWhenReady(true);
        });
    }
    
    private void pauseVideo() {
        player.setPlayWhenReady(false);
        playerView.setClickable(false);
        playerView.setOnClickListener(null);
        playButton.setVisibility(View.VISIBLE);
    }
    
    @Override
    public void bind(int position, T uri) {
        currentItem = uri;
        String src = uri.toString();
        
        videoLoaded = false;
        if (src.endsWith(".mp4")) {
            playButton.setVisibility(View.VISIBLE);
        } else {
            playButton.setVisibility(View.GONE);
        }
        imageView.setVisibility(View.VISIBLE);
        playerView.setVisibility(View.GONE);
        GlideApp.with(imageView.getContext()).load(uri).error(R.drawable.icon_error).into(imageView);
    }
    
    @Override
    public void onDialogClosed() {
        // The VideoView must be stopped and hidden as the dialog begins to close,
        // or we'll keep seeing the video under the animation due to how SurfaceViews work.
        // But by delaying that, we avoid some flicker as the close animation starts.
        playerView.postDelayed(() -> {
            playerView.setVisibility(View.GONE);
            player.release();
        }, 10);
    }
    
    @Override
    public void setIsVisible(boolean isVisible) {
        if (isVisible == active)
            return;
        
        active = isVisible;
        
        // Stop videos as they scroll off-screen
        if (!active && videoLoaded) {
            player.stop(true);
            videoLoaded = false;
            playerView.setVisibility(View.GONE);
            playButton.setVisibility(View.VISIBLE);
            imageView.setVisibility(View.VISIBLE);
        }
    }
}