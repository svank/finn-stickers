package net.samvankooten.finnstickers.ar;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.stfalcon.imageviewer.viewer.viewholder.DefaultViewHolder;

import net.samvankooten.finnstickers.R;
import net.samvankooten.finnstickers.misc_classes.GlideApp;

import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.List;

@TargetApi(24)
public class CustomViewHolder<T> extends DefaultViewHolder<T> {
    public static final List<WeakReference<CustomViewHolder<Uri>>> holders = new LinkedList<>();
    
    private final ImageView imageView;
    private final PlayerView playerView;
    private final ImageView playButton;
    private final ExoPlayer player;
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
        
        playButton.setImageDrawable(ContextCompat.getDrawable(
                context, R.drawable.icon_play_in_circle));
        playButton.setContentDescription(context.getString(R.string.play_button));
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) playButton.getLayoutParams();
        params.gravity = Gravity.CENTER;
        params.height = (int) context.getResources().getDimension(R.dimen.ar_play_button_size);
        params.width = params.height;
        playButton.setLayoutParams(params);
        
        CustomViewHolder<Uri> vh = new CustomViewHolder<>(parent, imageView, playerView, playButton);
        holders.removeIf(wr -> wr.get() == null);
        holders.add(new WeakReference<>(vh));
        return vh;
    }
    
    private @OptIn(markerClass = UnstableApi.class) CustomViewHolder(View parentView, ImageView iv, PlayerView pv, ImageView pb) {
        super(parentView);
        imageView = iv;
        playerView = pv;
        playButton = pb;
        
        player = new ExoPlayer.Builder(playerView.getContext()).build();
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
    
            DataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(
                    playerView.getContext());
            MediaSource videoSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(currentItem.toString()));
            player.setMediaSource(videoSource);
            player.prepare();
            player.setRepeatMode(Player.REPEAT_MODE_ALL);
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
            
            player.addListener(new Player.Listener(){
                @Override
                public void onIsLoadingChanged(boolean isLoading) {
                    if (!isLoading) {
                        playerView.setVisibility(View.VISIBLE);
                        imageView.setVisibility(View.GONE);
                    }
                }
                
                @Override
                public void onPlayerError(@NonNull PlaybackException error) {
                    player.release();
                    imageView.setImageDrawable(ContextCompat.getDrawable(
                            imageView.getContext(), R.drawable.icon_error));
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
    
    public void suspendPlayback() {
        if (player.isPlaying()) {
            pauseVideo();
            player.seekToDefaultPosition();
            playerView.setVisibility(View.GONE);
            imageView.setVisibility(View.VISIBLE);
        }
    }
    
    @Override
    public void bind(int position, T uri) {
        currentItem = uri;
        
        videoLoaded = false;
        
        ContentResolver cr = imageView.getContext().getContentResolver();
        if (cr.getType((Uri) uri).startsWith("video")) {
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
            player.stop();
            player.clearMediaItems();
            videoLoaded = false;
            playerView.setVisibility(View.GONE);
            playButton.setVisibility(View.VISIBLE);
            imageView.setVisibility(View.VISIBLE);
        }
    }
}