package net.samvankooten.finnstickers;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

public class StickerPackListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_PACK = 1;
    private static final int TYPE_HEADER = 2;
    private static final int TYPE_FOOTER = 3;
    
    private static final String TAG = "StickerPackListAdapter";
    
    private List<StickerPack> packs;
    private AppCompatActivity context;
    OnClickListener clickListener;
    OnRefreshListener refreshListener;
    private int nHeaders = 1;
    private int nFooters = 1;
    private String overrideHeaderText;
    
    public interface OnClickListener {
        void onClick(StickerPack pack);
    }
    
    public interface OnRefreshListener{
        void onRefresh();
    }
    
    public static class HeaderViewHolder extends RecyclerView.ViewHolder {
        public View view;
        public HeaderViewHolder(View v) {
            super(v);
            view = v;
        }
    }
    
    public class FooterViewHolder extends RecyclerView.ViewHolder {
        public Button refreshButton;
        public FooterViewHolder(View v) {
            super(v);
            refreshButton = v.findViewById(R.id.refresh_button);
            refreshButton.setOnClickListener((b) -> refreshListener.onRefresh());
        }
    }
    
    StickerPackListAdapter(List<StickerPack> packs, AppCompatActivity context) {
        this.packs = packs;
        this.context = context;
        setHasStableIds(true);
    }
    
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        switch (viewType) {
            case TYPE_PACK:
                LinearLayout ll = (LinearLayout) LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.pack_list_item, parent, false);
                return new StickerPackViewHolder(ll, this, context);
            
            case TYPE_HEADER:
                View v = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.pack_list_header, parent, false);
                return new HeaderViewHolder(v);
                
            case TYPE_FOOTER:
                v = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.pack_list_footer, parent, false);
                return new FooterViewHolder(v);
                
            default:
                return null;
        }
    }
    
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof StickerPackViewHolder) {
            StickerPackViewHolder vh = (StickerPackViewHolder) holder;
            StickerPack pack = getPackAtAdapterPos(position);
            vh.setPack(pack);
        }
        
        if (holder instanceof HeaderViewHolder && overrideHeaderText != null) {
            ((TextView) ((HeaderViewHolder) holder).view).setText(overrideHeaderText);
        }
    }
    
    public StickerPack getPackAtAdapterPos(int position) {
        return packs.get(position-nHeaders);
    }
    
    public int getAdapterPositionOfPack(StickerPack pack) {
        return nHeaders + packs.indexOf(pack);
    }
    
    @Override
    public int getItemCount() {
        return packs.size() + nHeaders + nFooters;
    }
    
    public void setPacks(List<StickerPack> packs) {
        this.packs = packs;
    }
    
    public void setOnClickListener(OnClickListener listener) {
        clickListener = listener;
    }
    
    public void setOnRefreshListener(OnRefreshListener listener) {
        refreshListener = listener;
    }
    
    public void overrideHeaderText(String text) {
        overrideHeaderText = text;
    }
    
    @Override
    public int getItemViewType(int position) {
        if (position < nHeaders)
            return TYPE_HEADER;
        if (position >= packs.size() + nHeaders)
            return TYPE_FOOTER;
        return TYPE_PACK;
    }
    
    public long getItemId(int position) {
        switch (getItemViewType(position)) {
            case TYPE_HEADER:
                return position;
            case TYPE_FOOTER:
                return position - packs.size();
            case TYPE_PACK:
                return getPackAtAdapterPos(position).hashCode();
            default:
                Log.e(TAG, "Reached default case in getItemID");
                return 0;
        }
    }
    
    public void setShowHeader(boolean show) {
        if (show && nHeaders == 0) {
            nHeaders = 1;
            notifyItemInserted(0);
        }
        
        if (!show && nHeaders == 1) {
            nHeaders = 0;
            notifyItemRemoved(0);
        }
    }
    
    public void setShowFooter(boolean show) {
        if (show && nFooters == 0) {
            nFooters = 1;
            notifyItemInserted(nHeaders + packs.size());
        }
        
        if (!show && nFooters == 1) {
            nFooters = 0;
            notifyItemRemoved(nHeaders + packs.size());
        }
    }
}