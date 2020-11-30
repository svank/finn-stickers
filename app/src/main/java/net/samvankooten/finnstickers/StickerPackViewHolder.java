package net.samvankooten.finnstickers;


import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import net.samvankooten.finnstickers.misc_classes.GlideApp;
import net.samvankooten.finnstickers.misc_classes.GlideRequest;
import net.samvankooten.finnstickers.utils.Util;

import java.util.List;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import static net.samvankooten.finnstickers.StickerPack.Status.INSTALLING;

public class StickerPackViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener{
    private static final String TAG = "StickerPackViewHolder";
    private static final String TRANSITION_PREFIX = "transition";
    
    private final TextView titleTextView;
    private final TextView subtitleTextView;
    private final TextView infoTextView;
    private final TextView updatedTextView;
    private final ImageView thumbnailImageView;
    private final Button installButton;
    private final Button updateButton;
    private final Button deleteButton;
    private final ProgressBar spinner;
    private final View transitionView;
    private final LinearLayout topLevelView;
    
    private StickerPack pack;
    private boolean solo = false;
    private final StickerPackListAdapter adapter;
    private final AppCompatActivity context;
    
    public StickerPackViewHolder(LinearLayout v, StickerPackListAdapter adapter, AppCompatActivity context) {
        super(v);
        topLevelView = v;
        transitionView = v.findViewById(R.id.transition_view);
        View rootView = v.findViewById(R.id.main_content);
        this.adapter = adapter;
        this.context = context;
        titleTextView = rootView.findViewById(R.id.sticker_pack_title);
        subtitleTextView = rootView.findViewById(R.id.sticker_pack_subtitle);
        infoTextView = rootView.findViewById(R.id.sticker_pack_info);
        thumbnailImageView = rootView.findViewById(R.id.sticker_pack_thumbnail);
        updatedTextView = rootView.findViewById(R.id.sticker_pack_update_text);
        installButton = rootView.findViewById(R.id.installButton);
        updateButton = rootView.findViewById(R.id.updateButton);
        deleteButton = rootView.findViewById(R.id.uninstallButton);
        spinner = rootView.findViewById(R.id.progressBar);
        
        deleteButton.setOnClickListener(this::onButtonClick);
        installButton.setOnClickListener(this::onButtonClick);
        updateButton.setOnClickListener(this::onButtonClick);
        topLevelView.setOnClickListener(this);
    }
    
    public void setSoloItem(boolean solo, boolean animate) {
        // Ensure the text is set for the TextViews we'll be animating in & out
        if (animate)
            setSoloItem(!solo, false);
        
        this.solo = solo;
    
        if (pack == null)
            return;
        
        updateVisibilityBasedOnSoloStatus();
        
        if (!animate)
            return;
        
        if (solo) {
            infoTextView.setVisibility(View.VISIBLE);
            infoTextView.setAlpha(0f);
            infoTextView.animate()
                    .setDuration(context.getResources().getInteger(R.integer.pack_view_animate_in_duration))
                    .alpha(1f);
            
            if (shouldShowUpdatedText()) {
                updatedTextView.setVisibility(View.VISIBLE);
                updatedTextView.setAlpha(1f);
                updatedTextView.animate()
                        .setDuration(context.getResources().getInteger(R.integer.pack_view_animate_in_duration))
                        .alpha(0f)
                        .withEndAction(() -> updatedTextView.setVisibility(View.GONE));
            }
        } else {
            infoTextView.setVisibility(View.VISIBLE);
            infoTextView.setAlpha(1f);
            infoTextView.animate()
                    .setDuration(context.getResources().getInteger(R.integer.pack_view_animate_out_duration))
                    .alpha(0f)
                    .withEndAction(() -> infoTextView.setVisibility(View.GONE));
            
            if (shouldShowUpdatedText()) {
                updatedTextView.setAlpha(0f);
                updatedTextView.animate()
                        .setDuration(context.getResources().getInteger(R.integer.pack_view_animate_out_duration))
                        .alpha(1f);
            }
        }
    }
    
    private void updateVisibilityBasedOnSoloStatus() {
        setInfoText();
        setUpdatedText();
        topLevelView.setClickable(!solo);
        topLevelView.setFocusable(!solo);
    }
    
    public void setPack(StickerPack pack) {
        if (this.pack != null)
            this.pack.getLiveStatus().removeObserver(this::statusDependentSetup);
        
        this.pack = pack;
        
        if (transitionView != null)
            transitionView.setTransitionName(getTransitionName());
        
        pack.getLiveStatus().observe(context, this::statusDependentSetup);
        
        titleTextView.setText(pack.getPackname());
        subtitleTextView.setText(pack.getExtraText());
        
        if (pack.getIconLocation() != null) {
            GlideRequest<Drawable> request = GlideApp.with(context).load(pack.getIconLocation());
            
            Util.enableGlideCacheIfRemote(request, pack.getIconLocation(), pack.getVersion());
            
            TypedValue typedValue = new TypedValue();
            context.getTheme().resolveAttribute(R.attr.image_placeholder, typedValue, true);
            request.placeholder(ContextCompat.getDrawable(context, typedValue.resourceId))
                    .into(thumbnailImageView);
        }
        
        setInfoText();
    }
    
    private void setInfoText() {
        if (solo) {
            infoTextView.setText(context.getResources().getQuantityString(R.plurals.pack_info,
                    pack.getStickerCount(),
                    pack.getStickerCount(), pack.getTotalSizeInMB()));
            infoTextView.setVisibility(View.VISIBLE);
        } else
            infoTextView.setVisibility(View.GONE);
    }
    
    private boolean shouldShowUpdatedText() {
        return pack.wasUpdatedRecently();
    }
    
    private void setUpdatedText() {
        if (shouldShowUpdatedText() && !solo) {
            int nNewStickers = pack.getNewStickerCount();
            updatedTextView.setText(context.getResources()
                    .getQuantityString(R.plurals.new_stickers_report,
                            nNewStickers, nNewStickers));
            updatedTextView.setVisibility(View.VISIBLE);
        } else {
            updatedTextView.setVisibility(View.GONE);
        }
    }
    
    private void statusDependentSetup(StickerPack.Status status) {
        setUpdatedText();
        if (status != INSTALLING)
            setInfoText();
        
        installButton.setVisibility(View.GONE);
        updateButton.setVisibility(View.GONE);
        deleteButton.setVisibility(View.GONE);
        spinner.setVisibility(View.GONE);
        
        switch (pack.getStatus()) {
            case UNINSTALLED:
                installButton.setVisibility(View.VISIBLE);
                break;
            
            case INSTALLED:
                deleteButton.setVisibility(View.VISIBLE);
                break;
            
            case UPDATABLE:
                updateButton.setVisibility(View.VISIBLE);
                break;
            
            case INSTALLING:
                spinner.setVisibility(View.VISIBLE);
                break;
        }
    }
    
    @Override
    public void onClick(View view) {
        if (adapter == null || adapter.clickListener == null)
            return;
        
        StickerPack pack = adapter.getPackAtAdapterPos(getAdapterPosition());
        
        adapter.clickListener.onClick(pack);
    }
    
    private void onButtonClick(View view) {
        // This is written to be agnostic of which pack this ViewHolder is currently
        // representing. "If one of my buttons is clicked, I'll find out which
        // pack I'm currently representing, find out which of my buttons was pressed,
        // and perform the correct action on that pack."
        
        StickerPack pack;
        if (solo)
            pack = this.pack;
        else
            pack = adapter.getPackAtAdapterPos(getAdapterPosition());
    
        int id = view.getId();
        if (id == R.id.uninstallButton) {
            String message = context.getString(R.string.confirm_uninstall);
            List stickers = pack.getCustomStickers();
            if (stickers.size() > 0)
                message += context.getResources().getQuantityString(R.plurals.confirm_uninstall_n_custom_stickers,
                        stickers.size(), stickers.size());
            new AlertDialog.Builder(context)
                    .setTitle(context.getString(R.string.confirm_uninstall_title))
                    .setMessage(message)
                    .setPositiveButton(context.getString(R.string.remove_button), (d, i) -> pack.uninstall(view.getContext()))
                    .setNegativeButton(android.R.string.cancel, (d, i) -> {
                    })
                    .create().show();
        } else if (id == R.id.installButton) {
            pack.install(view.getContext(), null, true);
        } else if (id == R.id.updateButton) {
            pack.update(view.getContext(), null, true);
        }
    }
    
    public LinearLayout getTopLevelView() {
        return topLevelView;
    }
    
    public View getNotTooWideView() {
        return topLevelView.findViewById(R.id.notTooWide);
    }
    
    public View getTransitionView() {
        return transitionView;
    }
    
    public String getTransitionName() {
        return getTransitionName(pack.getPackname());
    }
    
    public static String getTransitionName(String packname) {
        return TRANSITION_PREFIX + packname;
    }
}
