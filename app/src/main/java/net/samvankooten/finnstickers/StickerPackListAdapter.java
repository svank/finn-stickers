package net.samvankooten.finnstickers;

import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.carousel.CarouselLayoutManager;
import com.stfalcon.imageviewer.StfalconImageViewer;

import net.samvankooten.finnstickers.misc_classes.GlideApp;
import net.samvankooten.finnstickers.misc_classes.GlideRequest;
import net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerAdapter;
import net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerViewModel;
import net.samvankooten.finnstickers.utils.ChangeOnlyObserver;
import net.samvankooten.finnstickers.utils.StickerPackRepository;

import java.util.ArrayList;
import java.util.List;

public class StickerPackListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_PACK = 1;
    private static final int TYPE_HEADER = 2;
    private static final int TYPE_FOOTER = 3;
    private static final int TYPE_CAROUSEL = 4;
    
    private static final String TAG = "StickerPackListAdapter";
    
    private List<StickerPack> packs;
    private AppCompatActivity context;
    private StickerPackListViewModel mainViewModel;
    OnClickListener clickListener;
    OnRefreshListener refreshListener;
    private int nHeaders = 1;
    private int nFooters = 1;
    private int nCarousels = 1;
    private String overrideHeaderText;
    
    public interface OnClickListener {
        void onClick(StickerPack pack);
    }
    
    public interface OnRefreshListener{
        void onRefresh();
    }

    public static class CarouselViewHolder extends RecyclerView.ViewHolder {
        public View view;
        public CarouselViewHolder(View v) {
            super(v);
            view = v;
        }
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
    
    StickerPackListAdapter(
            List<StickerPack> packs,
            AppCompatActivity context,
            StickerPackListViewModel mainViewModel) {
        this.packs = packs;
        this.context = context;
        this.mainViewModel = mainViewModel;
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

            case TYPE_CAROUSEL:
                View v = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.pack_list_carousel, parent, false);
                return new CarouselViewHolder(v);

            case TYPE_HEADER:
                v = LayoutInflater.from(parent.getContext())
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
        } else if (holder instanceof HeaderViewHolder && overrideHeaderText != null) {
            ((TextView) ((HeaderViewHolder) holder).view).setText(overrideHeaderText);
        } else if (holder instanceof CarouselViewHolder) {
            RecyclerView rv = ((CarouselViewHolder) holder).view.findViewById(R.id.carousel);
            StickerPackViewerViewModel carouselModel = new ViewModelProvider(context).get(StickerPackViewerViewModel.class);
            carouselModel.setAllPacks();
            StickerPackViewerAdapter adapter = new StickerPackViewerAdapter(null, context, true);
            if (mainViewModel.getCarouselUris() == null || mainViewModel.getCarouselUris().size() == 0) {
                List<String> uris = adapter.setPack(carouselModel.getPack(), true);
                mainViewModel.setCarouselUris(uris);
            } else
                adapter.setPack(carouselModel.getPack(), false, mainViewModel.getCarouselUris());
            StickerPackRepository.getLiveInstalledPacks(context).observe(context, new ChangeOnlyObserver<>(packList -> {
                carouselModel.setAllPacks();
                List<String> uris = adapter.setPack(carouselModel.getPack(), true);
                mainViewModel.setCarouselUris(uris);
                setShowCarousel(packList.size() != 0);
            }));
            rv.setAdapter(adapter);
            rv.setLayoutManager(new CarouselLayoutManager());
            adapter.setOnClickListener(((innerHolder, uri) ->
                startLightBox(adapter, innerHolder, Uri.parse(uri), adapter.getUris(), rv)
            ));
        }
    }

    private void startLightBox(StickerPackViewerAdapter adapter,
                               StickerPackViewerAdapter.StickerViewHolder holder,
                               Uri uri,
                               List<String> uris,
                               RecyclerView rv) {
        if (uris.size() == 0)
            return;

        // Ensure no problems if uris changes while lightbox is open
        final List<Uri> actualUris = new ArrayList<>();
        for (String item : uris)
            actualUris.add(Uri.parse(item));

        int position = actualUris.indexOf(uri);

        LightboxOverlayView viewerOverlay = new LightboxOverlayView(
                context, actualUris, position, false);

        viewerOverlay.setGetTransitionImageCallback(item -> {
            int pos = adapter.getPosOfItem(item.toString());
            StickerPackViewerAdapter.StickerViewHolder vh = (StickerPackViewerAdapter.StickerViewHolder) rv.findViewHolderForAdapterPosition(pos);
            return (vh == null) ? null : vh.imageView;
        });

        StfalconImageViewer<Uri> viewer = new StfalconImageViewer.Builder<>(context, actualUris,
                (v, src) -> {
                    GlideRequest<Drawable> request = GlideApp.with(context).load(src);
                    request.into(v);
                })
                .withStartPosition(position)
                .withOverlayView(viewerOverlay)
                .withImageChangeListener(viewerOverlay::setPos)
                .withHiddenStatusBar(false)
                .withTransitionFrom(holder == null ? null : holder.imageView)
                .show(true);

        viewerOverlay.setViewer(viewer);
        viewerOverlay.setAreDeletable(false);
        viewerOverlay.setAreEditable(false);
    }
    
    public StickerPack getPackAtAdapterPos(int position) {
        return packs.get(position - nHeaders - nCarousels);
    }
    
    public int getAdapterPositionOfPack(StickerPack pack) {
        return nCarousels + nHeaders + packs.indexOf(pack);
    }
    
    @Override
    public int getItemCount() {
        return packs.size() + nCarousels + nHeaders + nFooters;
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
        if (position < nCarousels)
            return TYPE_CAROUSEL;
        if (position < (nCarousels + nHeaders))
            return TYPE_HEADER;
        if (position >= packs.size() + nHeaders + nCarousels)
            return TYPE_FOOTER;
        return TYPE_PACK;
    }
    
    public long getItemId(int position) {
        switch (getItemViewType(position)) {
            case TYPE_CAROUSEL:
                return 0;
            case TYPE_HEADER:
                return 1;
            case TYPE_FOOTER:
                return -1;
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
            notifyItemInserted(nCarousels);
        }
        
        if (!show && nHeaders == 1) {
            nHeaders = 0;
            notifyItemRemoved(nCarousels);
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

    public void setShowCarousel(boolean show) {
        if (show && nCarousels == 0) {
            nCarousels = 1;
            notifyItemInserted(0);
        }

        if (!show && nCarousels == 1) {
            nCarousels = 0;
            notifyItemRemoved(0);
        }
    }
}