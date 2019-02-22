package net.samvankooten.finnstickers;


import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import net.samvankooten.finnstickers.misc_classes.GlideApp;

import androidx.recyclerview.widget.RecyclerView;

public class StickerPackListViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener{
    public TextView titleTextView;
    public TextView subtitleTextView;
    public TextView updatedTextView;
    public ImageView thumbnailImageView;
    public Button installButton;
    public Button updateButton;
    public Button deleteButton;
    public ProgressBar spinner;
    
    private StickerPack pack;
    private boolean showButtons;
    private StickerPackListAdapter adapter;
    
    public StickerPackListViewHolder(LinearLayout v, boolean showButtons, StickerPackListAdapter adapter) {
        super(v);
        this.showButtons = showButtons;
        this.adapter = adapter;
        titleTextView = v.findViewById(R.id.sticker_pack_list_title);
        subtitleTextView = v.findViewById(R.id.sticker_pack_list_subtitle);
        thumbnailImageView = v.findViewById(R.id.sticker_pack_list_thumbnail);
        updatedTextView = v.findViewById(R.id.sticker_pack_list_update_text);
        installButton = v.findViewById(R.id.installButton);
        updateButton = v.findViewById(R.id.updateButton);
        deleteButton = v.findViewById(R.id.uninstallButton);
        spinner = v.findViewById(R.id.progressBar);
        deleteButton.setOnClickListener(this::onButtonClick);
        installButton.setOnClickListener(this::onButtonClick);
        updateButton.setOnClickListener(this::onButtonClick);
        v.setOnClickListener(this);
    }
    
    public void clear() {
        installButton.setVisibility(View.GONE);
        updateButton.setVisibility(View.GONE);
        deleteButton.setVisibility(View.GONE);
        spinner.setVisibility(View.GONE);
        updatedTextView.setVisibility(View.GONE);
    }
    
    public void setPack(StickerPack pack) {
        this.pack = pack;
        titleTextView.setText(pack.getPackname());
        subtitleTextView.setText(pack.getExtraText());
        
        if (pack.getIconfile() != null)
            GlideApp.with(adapter.getContext()).load(pack.getIconfile()).into(thumbnailImageView);
        
        setVariableParts();
    }
    
    /*
    Sets up parts of the view that vary depending on the pack's installation status.
     */
    public void setVariableParts() {
        clear();
        if (pack.wasUpdatedRecently()) {
            int nNewStickers = pack.getUpdatedURIs().size();
            updatedTextView.setText(String.format(adapter.getContext().getString(R.string.new_stickers_report),
                    nNewStickers,
                    (nNewStickers > 1) ? "s" : ""));
            updatedTextView.setVisibility(View.VISIBLE);
        }
        
        if (!showButtons)
            return;
        
        switch (pack.getStatus()) {
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
        StickerPack pack = adapter.getPackAtAdapterPos(getAdapterPosition());
        if (pack.getStatus() == StickerPack.Status.INSTALLING)
            return;
        
        adapter.clickListener.onClick(pack);
    }
    
    public void onButtonClick(View view) {
        // This is written to be agnostic of which pack this ViewHolder is currently
        // representing. "If one of my buttons is clicked, I'll find out which
        // pack I'm currently representing, find out which of my buttons was pressed,
        // and perform the correct action on that pack."
        StickerPack pack = adapter.getPackAtAdapterPos(getAdapterPosition());
        
        switch (view.getId()) {
            case R.id.uninstallButton:
                pack.uninstall(view.getContext());
                break;
            case R.id.installButton:
                pack.install(view.getContext(),
                        () -> adapter.notifyPackChanged(pack),
                        true);
                break;
            case R.id.updateButton:
                pack.update(view.getContext(),
                        () -> adapter.notifyPackChanged(pack),
                        true);
        }
        
        setVariableParts();
    }
}
