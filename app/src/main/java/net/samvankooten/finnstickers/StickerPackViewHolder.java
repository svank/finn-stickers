package net.samvankooten.finnstickers;


import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import net.samvankooten.finnstickers.misc_classes.GlideApp;
import net.samvankooten.finnstickers.misc_classes.GlideRequest;
import net.samvankooten.finnstickers.utils.Util;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

public class StickerPackViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener{
    
    private final TextView titleTextView;
    private final TextView subtitleTextView;
    private final TextView infoTextView;
    private final TextView updatedTextView;
    private final ImageView thumbnailImageView;
    private final Button installButton;
    private final Button updateButton;
    private final Button deleteButton;
    private final ProgressBar spinner;
    
    private StickerPack pack;
    private boolean solo = false;
    private final boolean showButtons;
    private final StickerPackListAdapter adapter;
    private final AppCompatActivity context;
    private final RelativeLayout rootView;
    
    public StickerPackViewHolder(LinearLayout v, boolean showButtons, StickerPackListAdapter adapter, AppCompatActivity context) {
        super(v);
        rootView = v.findViewById(R.id.main_content);
        this.showButtons = showButtons;
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
        rootView.setOnClickListener(this);
    }
    
    public void setSoloItem(boolean solo) {
        this.solo = solo;
        
        infoTextView.setVisibility(solo ? View.VISIBLE : View.GONE);
    }
    
    public void setPack(StickerPack pack) {
        if (this.pack != null)
            this.pack.getLiveStatus().removeObserver(this::statusDependentSetup);
        
        this.pack = pack;
        
        pack.getLiveStatus().observe(context, this::statusDependentSetup);
        
        titleTextView.setText(pack.getPackname());
        subtitleTextView.setText(pack.getExtraText());
        
        if (pack.getIconLocation() != null) {
            GlideRequest request = GlideApp.with(context).load(pack.getIconLocation());
            
            Util.enableGlideCacheIfRemote(request, pack.getIconLocation(), pack.getVersion());
            
            request.placeholder(context.getDrawable(R.drawable.pack_viewer_placeholder))
                    .into(thumbnailImageView);
        }
        
        if (solo)
            infoTextView.setText(context.getResources().getQuantityString(R.plurals.pack_info,
                    pack.getStickerCount(),
                    pack.getStickerCount(), pack.getTotalSizeInMB()));
        else
            infoTextView.setText("");
    }
    
    private void statusDependentSetup(StickerPack.Status status) {
        if (pack.wasUpdatedRecently() && !solo) {
            int nNewStickers = pack.getUpdatedURIs().size();
            updatedTextView.setText(String.format(context.getString(R.string.new_stickers_report),
                    nNewStickers,
                    (nNewStickers > 1) ? "s" : ""));
            updatedTextView.setVisibility(View.VISIBLE);
        } else
            updatedTextView.setVisibility(View.GONE);
        
        if (!showButtons)
            return;
        
        installButton.setVisibility(View.GONE);
        updateButton.setVisibility(View.GONE);
        deleteButton.setVisibility(View.GONE);
        spinner.setVisibility(View.GONE);
        
        switch (status) {
            case UNINSTALLED:
                installButton.setVisibility(View.VISIBLE);
                break;
            
            case INSTALLED:
                deleteButton.setVisibility(View.VISIBLE);
                break;
            
            case UPDATEABLE:
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
        if (pack.getStatus() == StickerPack.Status.INSTALLING)
            return;
        
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
        
        switch (view.getId()) {
            case R.id.uninstallButton:
                pack.uninstall(view.getContext());
                break;
            case R.id.installButton:
                pack.install(view.getContext(), null, true);
                break;
            case R.id.updateButton:
                pack.update(view.getContext(), null, true);
        }
    }
}
