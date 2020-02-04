package net.samvankooten.finnstickers.sticker_pack_viewer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.selection.ItemKeyProvider;

public class MyKeyProvider extends ItemKeyProvider<String> {
    
    private StickerPackViewerAdapter adapter;
    
    MyKeyProvider(StickerPackViewerAdapter adapter) {
        super(ItemKeyProvider.SCOPE_MAPPED);
        this.adapter = adapter;
    }
    
    @Nullable
    @Override
    public String getKey(int position) {
        return adapter.getItem(position);
    }
    
    @Override
    public int getPosition(@NonNull String key) {
        return adapter.getPosOfItem(key);
    }
}
