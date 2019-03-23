package net.samvankooten.finnstickers.ar;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import net.samvankooten.finnstickers.R;
import net.samvankooten.finnstickers.StickerPack;
import net.samvankooten.finnstickers.StickerProvider;
import net.samvankooten.finnstickers.utils.Util;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import androidx.appcompat.widget.TooltipCompat;

public class StickerPackGallery extends LinearLayout {
    
    private static final String TAG = "StickerPackGallery";
    
    private GalleryRow packGallery;
    private List<GalleryRow> stickerGalleries;
    private ImageView backButton;
    private ImageView deleteButton;
    private ImageView helpButton;
    
    public StickerPackGallery(Context context) {
        super(context);
        setup(context);
    }
    
    public StickerPackGallery(Context context, AttributeSet attrs) {
        super(context, attrs);
        setup(context);
    }
    
    public StickerPackGallery(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setup(context);
    }
    
    private void setup(Context context) {
        setOrientation(LinearLayout.VERTICAL);
        setGravity(Gravity.TOP);
        LayoutInflater.from(context).inflate(R.layout.ar_sticker_pack_gallery, this, true);
    }
    
    public void init(Context context, List<StickerPack> packs, String[] models, int[] model_icons) {
        StickerProvider provider = new StickerProvider();
        provider.setRootDir(context);
        
        packGallery = findViewById(R.id.gallery_pack_picker);
        stickerGalleries = new ArrayList<>(packs.size());
        
        List<String> packIcons = new ArrayList<>(packs.size());
        for (StickerPack pack : packs)
            packIcons.add(pack.getIconLocation());
        // Add icon for the 3D model "pack"
        packIcons.add(Util.resourceToUri(context, R.drawable.ar_3d_pack_icon));
        
        // Set up the upper gallery, showing each installed pack
        packGallery.setup(packIcons, (position, view) -> {
            view.playSoundEffect(android.view.SoundEffectConstants.CLICK);
            hideStickerGalleries();
            if (getSelectedPack() == position) {
                // If the user taps the already-selected pack, close it.
                setSelectedPack(-1);
                return;
            }
            setSelectedPack(position);
            stickerGalleries.get(position).setVisibility(View.VISIBLE);
        });
        packGallery.setSelectedItem(-1);
        
        // Set up a gallery for each individual pack
        for (StickerPack pack : packs) {
            List<String> uris = pack.getStickerURIs();
            GalleryRow stickerGallery = buildGallery(uris, context);
            addView(stickerGallery);
            stickerGalleries.add(stickerGallery);
        }
        
        // Set up a gallery for the 3D models
        List<String> uris = new ArrayList<>(models.length);
        for (int i=0; i<models.length; i++) {
            uris.add(Util.resourceToUri(context, model_icons[i]));
        }
        GalleryRow stickerGallery = buildGallery(uris, context);
        addView(stickerGallery);
        stickerGalleries.add(stickerGallery);
    
        deleteButton = findViewById(R.id.delete_icon);
        backButton = findViewById(R.id.back_icon);
        helpButton = findViewById(R.id.help_icon);
        
        setDeleteInactive(false);
        
        TooltipCompat.setTooltipText(backButton, getResources().getString(R.string.back_button));
        TooltipCompat.setTooltipText(helpButton, getResources().getString(R.string.view_onboard));
    }
    
    public void setDeleteActive() {
        deleteButton.animate().alpha(1f).setDuration(100);
        deleteButton.setClickable(true);
    }
    
    public void setDeleteInactive() {
        setDeleteInactive(true);
    }
    
    public void setDeleteInactive(boolean animate) {
        if (animate)
            deleteButton.animate().alpha(0.5f).setDuration(100);
        else
            deleteButton.setAlpha(0.5f);
        deleteButton.setClickable(false);
        deleteButton.setLongClickable(true);
    }
    
    private GalleryRow buildGallery(List<String> uris, Context context) {
        GalleryRow gallery = new GalleryRow(context, null, 0, R.style.ARStickerPicker);
        gallery.setVisibility(View.GONE);
        gallery.setup(uris, (position, view) -> {
            view.playSoundEffect(android.view.SoundEffectConstants.CLICK);
            gallery.setSelectedItem(position);
        });
        
        return gallery;
    }
    
    public int getSelectedSticker() {
        if (getSelectedPack() >= 0)
            return stickerGalleries.get(getSelectedPack()).getSelectedItem();
        return -1;
    }
    
    public void setSelectedPack(int position) {
        packGallery.setSelectedItem(position);
    }
    
    public int getSelectedPack() {
        return packGallery.getSelectedItem();
    }
    
    public void hideStickerGalleries() {
        if (stickerGalleries != null)
            for (GalleryRow gallery : stickerGalleries)
                gallery.setVisibility(View.GONE);
    }
    
    public void setOnDeleteListener(OnClickListener listener) {
        deleteButton.setOnClickListener(listener);
    }
    
    public void setOnDeleteLongClicklistener(OnLongClickListener listener) {
        deleteButton.setOnLongClickListener(listener);
    }
    
    public void setOnBackListener(OnClickListener listener) {
        backButton.setOnClickListener(listener);
    }
    
    public void setOnHelpListener(OnClickListener listener) {
        helpButton.setOnClickListener(listener);
    }
    
    public ImageView getBackView() {
        return backButton;
    }
    
    public ImageView getDeleteView() {
        return deleteButton;
    }
    
    public ImageView getHelpView() {
        return helpButton;
    }
    
    public List<View> getViewsToAnimate() {
        List<View> views = new LinkedList<>(packGallery.getViews());
        
        if (getSelectedPack() >= 0) {
            GalleryRow gallery = stickerGalleries.get(getSelectedPack());
            views.addAll(gallery.getViews());
        }
        
        return views;
    }
    
    public List<View> getViewsToNotAnimate() {
        List<View> views = new LinkedList<>();
        
        for (int i=0; i<stickerGalleries.size(); i++) {
            if (i != getSelectedPack())
                views.addAll(stickerGalleries.get(i).getViews());
        }
        
        return views;
    }
}