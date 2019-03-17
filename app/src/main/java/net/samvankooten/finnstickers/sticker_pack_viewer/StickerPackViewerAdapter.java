package net.samvankooten.finnstickers.sticker_pack_viewer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.samvankooten.finnstickers.R;
import net.samvankooten.finnstickers.StickerPack;
import net.samvankooten.finnstickers.StickerPackViewHolder;
import net.samvankooten.finnstickers.misc_classes.GlideApp;
import net.samvankooten.finnstickers.misc_classes.GlideRequest;
import net.samvankooten.finnstickers.utils.Util;

import java.util.LinkedList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class StickerPackViewerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public static final int TYPE_IMAGE = 1;
    public static final int TYPE_HEADER = 2;
    public static final int TYPE_TEXT = 3;
    public static final int TYPE_CENTERED_TEXT = 4;
    public static final int TYPE_DIVIDER = 5;
    public static final int TYPE_PACK = 6;
    public static final String DIVIDER_CODE = "divider";
    public static final String HEADER_PREFIX = "header_";
    public static final String TEXT_PREFIX = "text_";
    public static final String CENTERED_TEXT_PREFIX = "centeredtext_";
    public static final String PACK_CODE = "pack";
    public static final String TAG = "StckrPckVwrRecyclrAdptr";
    
    private AppCompatActivity context;
    private int packVersion;
    private OnClickListener listener;
    private StickerPack pack;
    
    private final AsyncListDiffer<String> differ = new AsyncListDiffer<>(this, new DiffUtil.ItemCallback<String>(){
        @Override
        public boolean areItemsTheSame(
                @NonNull String oldUri, @NonNull String newUri) {
            return oldUri.equals(newUri);
        }
        @Override
        public boolean areContentsTheSame(
                @NonNull String oldUri, @NonNull String newUri) {
            return oldUri.equals(newUri);
        }
    });
    
    public class StickerViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener{
        public ImageView imageView;
        public StickerViewHolder(LinearLayout v, boolean clickable) {
            super(v);
            imageView = v.findViewById(R.id.image);
            if (clickable)
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
    
    StickerPackViewerAdapter(List<String> uris, AppCompatActivity context, StickerPack pack) {
        if (uris == null)
            uris = new LinkedList<>();
        differ.submitList(uris);
        this.context = context;
        this.packVersion = pack.getVersion();
        this.pack = pack;
        setHasStableIds(true);
    }
    
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        switch (viewType) {
            case TYPE_IMAGE:
                LinearLayout ll = (LinearLayout) LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.sticker_pack_viewer_sticker, parent, false);
                return new StickerViewHolder(ll, true);
            
            case TYPE_HEADER:
                TextView tv = (TextView) LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.sticker_pack_viewer_header, parent, false);
                return new TextViewHolder(tv);
                
            case TYPE_TEXT:
            case TYPE_CENTERED_TEXT:
                tv = (TextView) LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.sticker_pack_viewer_text, parent, false);
                return new TextViewHolder(tv);
                
            case TYPE_DIVIDER:
                ll = (LinearLayout) LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.sticker_pack_viewer_divider, parent, false);
                return new StickerViewHolder(ll, false);
                
            case TYPE_PACK:
                ll = (LinearLayout) LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.sticker_pack_viewer_pack, parent, false);
                StickerPackViewHolder holder = new StickerPackViewHolder(
                        ll, null, context);
                holder.setSoloItem(true);
                return holder;
            default:
                return null;
        }
    }
    
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        String item = getItem(position);
        switch (getItemViewType(position)) {
            case TYPE_PACK:
                StickerPackViewHolder vh = (StickerPackViewHolder) holder;
                vh.setPack(pack);
                break;
            case TYPE_IMAGE:
                GlideRequest builder = GlideApp.with(context).load(item).centerCrop();
                builder.placeholder(R.drawable.pack_viewer_placeholder);
                
                Util.enableGlideCacheIfRemote(builder, item, packVersion);
                
                builder.into(((StickerViewHolder) holder).imageView);
                break;
            case TYPE_HEADER:
                ((TextViewHolder) holder).textView.setText(removeHeaderPrefix(item));
                break;
            case TYPE_TEXT:
                ((TextViewHolder) holder).textView.setText(removeTextPrefix(item));
                ((TextViewHolder) holder).textView.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
                break;
            case TYPE_CENTERED_TEXT:
                ((TextViewHolder) holder).textView.setText(removeCenteredTextPrefix(item));
                ((TextViewHolder) holder).textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                break;
        }
    }
    
    public String getItem(int position) {
        return differ.getCurrentList().get(position);
    }
    
    public int getPosOfItem(String item) {
        return differ.getCurrentList().indexOf(item);
    }
    
    @Override
    public int getItemCount() {
        return differ.getCurrentList().size();
    }
    
    public void setOnClickListener(OnClickListener listener) {
        this.listener = listener;
    }
    
    public void replaceDataSource(List<String> uris) {
        differ.submitList(uris);
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
        else if (isCenteredText(item))
            return TYPE_CENTERED_TEXT;
        else if (isPack(item))
            return TYPE_PACK;
        else
            return TYPE_IMAGE;
    }
    
    public static boolean isHeader(String uri) {
        return uri.startsWith(HEADER_PREFIX);
    }
    
    public static boolean isText(String uri) {
        return uri.startsWith(TEXT_PREFIX);
    }
    
    public static boolean isCenteredText(String uri) {
        return uri.startsWith(CENTERED_TEXT_PREFIX);
    }
    
    public static boolean isDivider(String uri) {
        return uri.equals(DIVIDER_CODE);
    }
    
    public static boolean isPack(String uri) {
        return uri.equals(PACK_CODE);
    }
    
    public static boolean isImage(String uri) {
        return !isHeader(uri) && !isDivider(uri) && !isText(uri) && !isPack(uri);
    }
    
    public static String removeHeaderPrefix(String uri) {
        return uri.substring(HEADER_PREFIX.length());
    }
    
    public static String removeTextPrefix(String uri) {
        return uri.substring(TEXT_PREFIX.length());
    }
    
    public static String removeCenteredTextPrefix(String uri) {
        return uri.substring(CENTERED_TEXT_PREFIX.length());
    }
    
    public static List<String> removeSpecialItems(List<String> uris) {
        List<String> output = new LinkedList<>();
        for (String uri : uris) {
            if (isImage(uri))
                output.add(uri);
        }
        return output;
    }
    
    @Override
    public long getItemId(int position) {
        if (getItemViewType(position) == TYPE_DIVIDER)
            return TYPE_DIVIDER + position;
        return getItem(position).hashCode();
    }
    
    public GridLayoutManager.SpanSizeLookup getSpaceSizeLookup(final int nColumns) {
        return new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                switch (getItemViewType(position)) {
                    case TYPE_IMAGE:
                        return 1;
                    case TYPE_HEADER:
                    case TYPE_DIVIDER:
                    case TYPE_TEXT:
                    case TYPE_CENTERED_TEXT:
                    case TYPE_PACK:
                        return nColumns;
                    default:
                        return -1;
                }
            }
        };
    }
}