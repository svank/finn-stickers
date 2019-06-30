package net.samvankooten.finnstickers.sticker_pack_viewer;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import net.samvankooten.finnstickers.CompositeStickerPack;
import net.samvankooten.finnstickers.R;

import java.util.List;

public class FilterDialog extends Dialog {
    
    private final StickerPackViewerViewModel model;
    
    public FilterDialog(Activity a, StickerPackViewerViewModel model) {
        super(a);
        this.model = model;
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_filter);
        
        Chip gifs = findViewById(R.id.filter_gifs);
        gifs.setChecked(model.getShowGifs());
        gifs.setOnCheckedChangeListener((btn, isChecked) -> model.setShowGifs(isChecked));
        
        Chip stills = findViewById(R.id.filter_stills);
        stills.setChecked(model.getShowStills());
        stills.setOnCheckedChangeListener((btn, isChecked) -> model.setShowStills(isChecked));
    
        Chip edited = findViewById(R.id.filter_edited);
        edited.setChecked(model.getShowEdited());
        edited.setOnCheckedChangeListener((btn, isChecked) -> model.setShowEdited(isChecked));
    
        Chip unedited = findViewById(R.id.filter_unedtied);
        unedited.setChecked(model.getShowUnedited());
        unedited.setOnCheckedChangeListener((btn, isChecked) -> model.setShowUnedited(isChecked));
    
        findViewById(R.id.close).setOnClickListener(v -> dismiss());
    
        findViewById(R.id.reset).setOnClickListener(v -> {
            gifs.setChecked(true);
            model.setShowGifs(true);
            stills.setChecked(true);
            model.setShowStills(true);
            edited.setChecked(true);
            model.setShowEdited(true);
            unedited.setChecked(true);
            model.setShowUnedited(true);
            
            if (model.isInAllPacksMode())
                model.setShowPacks(((CompositeStickerPack) model.getPack()).getPackNames(), true);
        });
    
        LinearLayout fromPacks = findViewById(R.id.filter_from_packs);
        fromPacks.setVisibility(View.GONE);
        if (model.isInAllPacksMode()) {
            CompositeStickerPack pack = (CompositeStickerPack) model.getPack();
            List<String> names = pack.getPackNames();
            if (names.size() > 1) {
                fromPacks.setVisibility(View.VISIBLE);
                List<String> shownPacks = model.getShownPacks();
                ChipGroup group = findViewById(R.id.filter_from_packs_group);
                for (String name : names) {
                    Chip chip = new Chip(getContext());
                    chip.setCheckable(true);
                    chip.setChecked(shownPacks.indexOf(name) >= 0);
                    chip.setText(name);
                    chip.setOnCheckedChangeListener((btn, isChecked) -> model.setShowPack(name, isChecked));
                    group.addView(chip);
                }
            }
        }
    }
}
