package net.samvankooten.finnstickers;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.facebook.drawee.view.SimpleDraweeView;

import java.util.List;

/**
 * Created by sam on 10/31/17.
 */

class StickerPackViewerRecyclerAdapter extends RecyclerView.Adapter<StickerPackViewerRecyclerAdapter.StickerPackViewHolder> {
    private final Context context;
    private final List<String> identifiers;
    private final StickerProvider provider;
    private int size;
    private int padding;
    private int selectedPos = RecyclerView.NO_POSITION;
    
    static class StickerPackViewHolder extends RecyclerView.ViewHolder {
        SimpleDraweeView view;
        StickerPackViewHolder(SimpleDraweeView v) {
            super(v);
            view = v;
        }
    }
    
    StickerPackViewerRecyclerAdapter(Context c, List<String> identifiers, int size, int padding) {
        context = c;
        this.identifiers = identifiers;
        this.size = size;
        this.padding = padding;
        provider = new StickerProvider();
        provider.setRootDir(context);
    }
    
    @Override
    @NonNull
    public StickerPackViewerRecyclerAdapter.StickerPackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        SimpleDraweeView v = new SimpleDraweeView(context);
        v.getHierarchy().setFadeDuration(0);
        int size = (int) (this.size * context.getResources().getDisplayMetrics().density);
        v.setLayoutParams(new RecyclerView.LayoutParams(size, size));
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        v.setPadding(padding, padding, padding, padding);
        return new StickerPackViewHolder(v);
    }
    
    @Override
    public void onBindViewHolder(@NonNull StickerPackViewHolder holder, int position) {
        String item = getItem(position);
        
        holder.view.setImageURI(item);
        
        if (position == selectedPos)
//            holder.view.setBackgroundColor(context.getResources().getColor(R.color.colorAccent));
            holder.view.setBackground(context.getDrawable(R.drawable.selection_square));
        else
            holder.view.setBackground(null);
        
    }
    
    @Override
    public int getItemCount() {
        return identifiers.size();
    }
    
    private String getItem(int position) {
        return identifiers.get(position);
    }
    
    void setSelectedPos(int position) {
        int oldPos = selectedPos;
        selectedPos = position;
        notifyItemChanged(oldPos);
        notifyItemChanged(position);
    }
    
    int getSelectedPos() {
        return selectedPos;
    }
}
