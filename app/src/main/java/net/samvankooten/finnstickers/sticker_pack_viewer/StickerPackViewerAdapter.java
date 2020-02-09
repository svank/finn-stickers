package net.samvankooten.finnstickers.sticker_pack_viewer;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
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
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class StickerPackViewerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_IMAGE = 1;
    private static final int TYPE_HEADER = 2;
    private static final int TYPE_TEXT = 3;
    private static final int TYPE_CENTERED_TEXT = 4;
    private static final int TYPE_DIVIDER = 5;
    private static final int TYPE_PACK = 6;
    private static final int TYPE_REFRESH = 7;
    public static final String DIVIDER_CODE = "divider";
    public static final String HEADER_PREFIX = "header_";
    public static final String TEXT_PREFIX = "text_";
    public static final String CENTERED_TEXT_PREFIX = "centeredtext_";
    public static final String PACK_CODE = "pack";
    public static final String REFRESH_CODE = "refresh";
    private static final String TAG = "StckrPckVwrRecyclrAdptr";
    
    private AppCompatActivity context;
    private int packVersion;
    private SelectionTracker<String> tracker;
    private OnClickListener listener;
    private OnRefreshListener refreshListener;
    private StickerPack pack;
    private boolean shouldAnimateIn = false;
    private OnBindListener onBindListener;
    
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
    
    abstract class TransitionViewHolder extends RecyclerView.ViewHolder {
        private final View view;
        
        TransitionViewHolder(View v) {
            super(v);
            view = v;
        }
    
        void animateIn(int duration) {
            if (view != null) {
                view.setAlpha(0f);
                ObjectAnimator.ofFloat(view, View.ALPHA, 1f).setDuration(duration).start();
            }
        }
    
        void animateOut(int duration) {
            if (view != null)
                view.animate().alpha(0f).setDuration(duration);
        }
    }
    
    class StickerViewHolder extends TransitionViewHolder implements View.OnClickListener{
        final ImageView imageView;
        final ImageView checkBox;
        String uri;
        ValueAnimator animator;
        
        
        StickerViewHolder(FrameLayout v) {
            super(v);
            imageView = v.findViewById(R.id.image);
            imageView.setOnClickListener(this);
            checkBox = v.findViewById(R.id.checkbox);
        }
        
        @Override
        public void onClick(View view) {
            int position = getAdapterPosition();
            if (listener != null)
                listener.onClick(this, getItem(position));
        }
        
        @SuppressLint("CheckResult")
        void onBind(String uri, boolean isSelectable, boolean isSelected) {
            boolean uriUnchanged = this.uri != null && this.uri.equals(uri);
            this.uri = uri;
            GlideRequest<Drawable> builder = GlideApp.with(context).load(uri);
            
            builder.centerCrop();
            TypedValue typedValue = new TypedValue();
            context.getTheme().resolveAttribute(R.attr.image_placeholder, typedValue, true);
            builder.placeholder(typedValue.resourceId);
            Util.enableGlideCacheIfRemote(builder, uri, packVersion);
            builder.into(imageView);
    
            checkBox.setActivated(isSelected);
    
            if (animator != null && animator.isRunning())
                animator.cancel();
    
            int padding = isSelected
                    ? (int) context.getResources().getDimension(R.dimen.sticker_pack_viewer_selection_padding)
                    : 0;
    
            // We want to animate padding changes when a sticker is (de)selected, but not when
            // a selected sticker is scrolling on-screen. LayoutManager gives weird values when
            // asking at this point for the first/last visible items. We could wait until after
            // layout and then check if we're onscreen, but then we risk having padding jump
            // after a frame. Instead, it seems that the same ViewHolder gets reused when rebinding
            // after a (de)selection, so we check if we're binding to the same URI as before to
            // decide if we should animate. Super hacky, but it (seems to) work!
            if (uriUnchanged && (imageView.getPaddingLeft() != padding)) {
                animator = ValueAnimator.ofInt(imageView.getPaddingRight(), padding);
                animator.addUpdateListener(valueAnimator ->
                        imageView.setPadding(
                                (Integer) valueAnimator.getAnimatedValue(),
                                (Integer) valueAnimator.getAnimatedValue(),
                                (Integer) valueAnimator.getAnimatedValue(),
                                (Integer) valueAnimator.getAnimatedValue()));
                animator.setDuration(context.getResources().getInteger(R.integer.pack_view_selection_animation_duration));
                animator.start();
            } else
                imageView.setPadding(padding, padding, padding, padding);
    
            float newAlpha = isSelectable ? 1f : 0f;
    
            if (uriUnchanged && newAlpha != checkBox.getAlpha()) {
                ObjectAnimator oa = ObjectAnimator.ofFloat(checkBox, View.ALPHA, checkBox.getAlpha(), newAlpha)
                        .setDuration(context.getResources().getInteger(R.integer.pack_view_selection_animation_duration));
                oa.setAutoCancel(true);
                oa.start();
            } else
                checkBox.setAlpha(newAlpha);
        }
        
        /**
         * There's an interaction between Glide & shared element transitions of some sort,
         * such that if the transition is too short, gifs don't start playing. The best work-around
         * I've found is to re-load the gifs after the transition completes, facilitated with
         * this method.
         */
        void onWindowTransitionComplete() {
            if (uri != null && uri.endsWith(".gif"))
                onBind(uri, checkBox.getAlpha() > 0, checkBox.isActivated());
        }
        
        ItemDetailsLookup.ItemDetails<String> getItemDetails() {
            return new ItemDetailsLookup.ItemDetails<String>() {
                @Override
                public int getPosition() { return getPosOfItem(uri); }
                
                @Nullable
                @Override
                public String getSelectionKey() { return uri; }
            };
        }
    }
    
    class DividerViewHolder extends TransitionViewHolder {
    
        DividerViewHolder(View v) {
            super(v);
        }
    }
    
    interface OnClickListener {
        void onClick(StickerViewHolder holder, String uri);
    }
    
    class TextViewHolder extends TransitionViewHolder {
        final TextView textView;
        TextViewHolder(TextView v) {
            super(v);
            textView = v;
        }
    }
    
    class RefreshViewHolder extends TransitionViewHolder {
        final Button refreshButton;
        RefreshViewHolder(FrameLayout v) {
            super(v);
            refreshButton = v.findViewById(R.id.refresh_button);
            refreshButton.setOnClickListener((b) -> refreshListener.onRefresh());
        }
    }
    
    interface OnRefreshListener{
        void onRefresh();
    }
    
    StickerPackViewerAdapter(List<String> uris, AppCompatActivity context) {
        if (uris == null)
            uris = new LinkedList<>();
        differ.submitList(uris);
        this.context = context;
        setHasStableIds(true);
    }
    
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        switch (viewType) {
            case TYPE_IMAGE:
                FrameLayout fl = (FrameLayout) LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.sticker_pack_viewer_sticker, parent, false);
                return new StickerViewHolder(fl);
            
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
                LinearLayout ll = (LinearLayout) LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.sticker_pack_viewer_divider, parent, false);
                return new DividerViewHolder(ll);
                
            case TYPE_REFRESH:
                fl = (FrameLayout) LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.sticker_pack_viewer_refresh, parent, false);
                return new RefreshViewHolder(fl);
                
            case TYPE_PACK:
                ll = (LinearLayout) LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.sticker_pack_viewer_pack, parent, false);
                StickerPackViewHolder holder = new StickerPackViewHolder(
                        ll, null, context);
                holder.setSoloItem(true, false);
                return holder;
            default:
                Log.e(TAG, "Viewholder type not recognized");
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
                boolean selectable = tracker != null && tracker.hasSelection()
                                     && pack != null && pack.getStickerByUri(item) != null;
                boolean selected = tracker != null && tracker.isSelected(item);
                ((StickerViewHolder) holder).onBind(item, selectable, selected);
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
        
        if (shouldAnimateIn && holder instanceof TransitionViewHolder)
            ((TransitionViewHolder) holder).animateIn(
                    context.getResources().getInteger(R.integer.pack_view_animate_in_duration));
        
        if (onBindListener != null) {
            onBindListener.onBind(item, position, holder);
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
    
    public void setShouldAnimateIn(boolean shouldAnimateIn) {
        this.shouldAnimateIn = shouldAnimateIn;
    }
    
    public void setOnClickListener(OnClickListener listener) {
        this.listener = listener;
    }
    
    public void setOnRefreshListener(OnRefreshListener listener) {
        this.refreshListener = listener;
    }
    
    public void setPack(StickerPack pack) {
        this.pack = pack;
        this.packVersion = pack.getVersion();
    }
    
    public void setTracker(SelectionTracker<String> tracker) {
        this.tracker = tracker;
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
        else if (isRefresh(item))
            return TYPE_REFRESH;
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
    
    public static boolean isRefresh(String uri) {
        return uri.equals(REFRESH_CODE);
    }
    
    public static boolean isImage(String uri) {
        return !isHeader(uri) && !isDivider(uri) && !isText(uri) && !isCenteredText(uri)
                && !isPack(uri) && !isRefresh(uri);
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
    
    public boolean hasStickers() {
        for (String uri : differ.getCurrentList()) {
            if (isImage(uri))
                return true;
        }
        return false;
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
                    case TYPE_REFRESH:
                        return nColumns;
                    default:
                        return -1;
                }
            }
        };
    }
    
    public void setOnBindListener(OnBindListener listener) {
        onBindListener = listener;
    }
    
    public interface OnBindListener {
        void onBind(String item, int position, RecyclerView.ViewHolder holder);
    }
}