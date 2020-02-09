package net.samvankooten.finnstickers.sticker_pack_viewer;


import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.widget.RecyclerView;

public class MyDetailsLookup extends ItemDetailsLookup<String> {
    
    private final RecyclerView mRecyclerView;
    
    MyDetailsLookup(RecyclerView recyclerView) {
        mRecyclerView = recyclerView;
    }
    
    public @Nullable
    ItemDetails<String> getItemDetails(@NonNull MotionEvent e) {
        View view = mRecyclerView.findChildViewUnder(e.getX(), e.getY());
        if (view != null) {
            RecyclerView.ViewHolder holder = mRecyclerView.getChildViewHolder(view);
            if (holder instanceof StickerPackViewerAdapter.StickerViewHolder) {
                return ((StickerPackViewerAdapter.StickerViewHolder) holder).getItemDetails();
            }
        }
        return null;
    }
}