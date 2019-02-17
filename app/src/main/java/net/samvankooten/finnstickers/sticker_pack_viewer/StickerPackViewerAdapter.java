package net.samvankooten.finnstickers.sticker_pack_viewer;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.signature.ObjectKey;

import net.samvankooten.finnstickers.R;
import net.samvankooten.finnstickers.misc_classes.GlideApp;
import net.samvankooten.finnstickers.misc_classes.GlideRequest;
import net.samvankooten.finnstickers.utils.Util;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class StickerPackViewerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public static final int TYPE_IMAGE = 1;
    public static final int TYPE_HEADER = 2;
    public static final int TYPE_TEXT = 3;
    public static final int TYPE_DIVIDER = 4;
    public static final String DIVIDER_CODE = "divider";
    public static final String HEADER_PREFIX = "header_";
    public static final String TEXT_PREFIX = "text_";
    public static final String TAG = "StckrPckVwrRecyclrAdptr";
    
    private List<String> uris;
    private Context context;
    private int packVersion;
    private int nColumns;
    private OnClickListener listener;
    
    public class StickerViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener{
        public ImageView imageView;
        public StickerViewHolder(LinearLayout v) {
            super(v);
            imageView = v.findViewById(R.id.image);
            imageView.setOnClickListener(this);
        }
        
        @Override
        public void onClick(View view) {
            int position = getAdapterPosition();
            if (listener != null)
                listener.onClick(this, getItem(position));
        }
    }
    
    public interface OnClickListener {
        void onClick(StickerViewHolder holder, String uri);
    }
    
    public class TextViewHolder extends RecyclerView.ViewHolder {
        public TextView textView;
        public TextViewHolder(TextView v) {
            super(v);
            textView = v;
        }
    }
    
    StickerPackViewerAdapter(List<String> uris, Context context, int nColumns, int packVersion) {
        this.uris = uris;
        this.context = context;
        this.nColumns = nColumns;
        this.packVersion = packVersion;
        setHasStableIds(true);
    }
    
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        switch (viewType) {
            case TYPE_IMAGE:
                LinearLayout ll = (LinearLayout) LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.sticker_pack_viewer_sticker, parent, false);
                return new StickerViewHolder(ll);
            
            case TYPE_HEADER:
                TextView tv = (TextView) LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.sticker_pack_viewer_header, parent, false);
                return new TextViewHolder(tv);
                
            case TYPE_TEXT:
                tv = (TextView) LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.sticker_pack_viewer_text, parent, false);
                return new TextViewHolder(tv);
                
                
            case TYPE_DIVIDER:
                ll = (LinearLayout) LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.sticker_pack_viewer_divider, parent, false);
                return new StickerViewHolder(ll);
            default:
                return null;
        }
    }
    
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        String item = getItem(position);
        switch (getItemViewType(position)) {
            case TYPE_IMAGE:
                GlideRequest builder = GlideApp.with(context).load(item).centerCrop();
                builder.placeholder(R.drawable.pack_viewer_placeholder);
                if (Util.stringIsURL(item)) {
                    // Enable disk caching for remote loads---see CustomAppGlideModule
                    builder.diskCacheStrategy(DiskCacheStrategy.AUTOMATIC);
                    // Ensure cached data is invalidated if pack version number changes
                    builder.signature(new ObjectKey(packVersion));
                }
                builder.into(((StickerViewHolder) holder).imageView);
                break;
            case TYPE_HEADER:
                ((TextViewHolder) holder).textView.setText(removeHeaderPrefix(item));
                break;
            case TYPE_TEXT:
                ((TextViewHolder) holder).textView.setText(removeTextPrefix(item));
                break;
        }
    }
    
    public String getItem(int position) {
        return uris.get(position);
    }
    
    public int getPosOfItem(String item) {
        return uris.indexOf(item);
    }
    
    @Override
    public int getItemCount() {
        return uris.size();
    }
    
    public void setOnClickListener(OnClickListener listener) {
        this.listener = listener;
    }
    
    @Override
    public int getItemViewType(int position) {
        String item = getItem(position);
        if (isHeader(item))
            return TYPE_HEADER;
        else if (isDivider(item))
            return TYPE_DIVIDER;
        else if (isText(item))
            return TYPE_TEXT;
        else
            return TYPE_IMAGE;
    }
    
    public static boolean isHeader(String uri) {
        return uri.length() > HEADER_PREFIX.length()
            && uri.substring(0, HEADER_PREFIX.length()).equals(HEADER_PREFIX);
    }
    
    public static boolean isText(String uri) {
        return uri.length() > TEXT_PREFIX.length()
            && uri.substring(0, TEXT_PREFIX.length()).equals(TEXT_PREFIX);
    }
    
    public static boolean isDivider(String uri) {
        return uri.equals(DIVIDER_CODE);
    }
    
    public static boolean isImage(String uri) {
        return !isHeader(uri) && !isDivider(uri) && !isText(uri);
    }
    
    public static String removeHeaderPrefix(String uri) {
        return uri.substring(HEADER_PREFIX.length());
    }
    
    public static String removeTextPrefix(String uri) {
        return uri.substring(TEXT_PREFIX.length());
    }
    
    @Override
    public long getItemId(int position) {
        return getItem(position).hashCode();
    }
}