package net.samvankooten.finnstickers.sticker_pack_viewer;

import android.content.Intent;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.signature.ObjectKey;
import com.google.android.material.snackbar.Snackbar;
import com.stfalcon.imageviewer.StfalconImageViewer;

import net.samvankooten.finnstickers.LightboxOverlayView;
import net.samvankooten.finnstickers.R;
import net.samvankooten.finnstickers.StickerPack;
import net.samvankooten.finnstickers.misc_classes.GlideApp;
import net.samvankooten.finnstickers.misc_classes.GlideRequest;
import net.samvankooten.finnstickers.utils.Util;

import java.util.LinkedList;
import java.util.List;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import static net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerAdapter.DIVIDER_CODE;
import static net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerAdapter.HEADER_PREFIX;
import static net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerAdapter.TEXT_PREFIX;
import static net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerAdapter.TYPE_DIVIDER;
import static net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerAdapter.TYPE_HEADER;
import static net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerAdapter.TYPE_IMAGE;
import static net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerAdapter.TYPE_TEXT;
import static net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerAdapter.isImage;

public class StickerPackViewerActivity extends AppCompatActivity {
    
    private static final String TAG = "StckrPackViewerActivity";
    
    private StickerPack pack;
    private boolean picker;
    private StickerPackViewerViewModel model;
    private SwipeRefreshLayout swipeRefresh;
    private Button refreshButton;
    private RecyclerView mainView;
    private boolean remote;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Util.performNeededMigrations(this);
        setContentView(R.layout.activity_sticker_pack_viewer);
        pack = (StickerPack) this.getIntent().getSerializableExtra("pack");
        picker = this.getIntent().getBooleanExtra("picker", false);
        if (pack.getStatus() == StickerPack.Status.UPDATEABLE)
            // TODO: If an update is available, we should display the stickers to be added.
            // For now, just show what's currently installed.
            pack = pack.getReplaces();
        remote = pack.getStatus() == StickerPack.Status.UNINSTALLED;
    
        setTitle(pack.getPackname() + " Sticker Pack");
    
        refreshButton = findViewById(R.id.refresh_button);
        refreshButton.setOnClickListener(v -> refresh());
    
        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setOnRefreshListener(this::refresh);
        swipeRefresh.setColorSchemeResources(R.color.colorAccent);
        if (!remote)
            swipeRefresh.setEnabled(false);
        
        model = ViewModelProviders.of(this).get(StickerPackViewerViewModel.class);
    
        List<String> uris = pack.getStickerURIs();
        if (pack.wasUpdatedRecently())
            uris = formatUpdatedUris(uris, pack.getUpdatedURIs());
        
        mainView = findViewById(R.id.main_view);
        mainView.setHasFixedSize(true);
        if (remote) {
            model.clearFailures();
            model.getDownloadException().observe(this, this::showDownloadException);
            model.getDownloadSuccess().observe(this, this::showDownloadSuccess);
            model.getUris().observe(this, this::updateFromDownloadedUrls);
            if (!model.isInitialized()) {
                displayLoading();
                model.setPack(pack);
            }
        } else
            setupMainView(uris);
    }
    
    private void refresh() {
        displayLoading();
        model.downloadData();
    }
    
    private void setupMainView(List<String> uris) {
        DisplayMetrics displayMetrics = this.getResources().getDisplayMetrics();
        float targetSize = getResources().getDimension(R.dimen.sticker_pack_viewer_target_image_size);
        int nColumns = (int) (displayMetrics.widthPixels / targetSize + 0.5); // +0.5 for correct rounding to int.
        
        GridLayoutManager layoutManager = new GridLayoutManager(this, nColumns);
        mainView.setLayoutManager(layoutManager);
        
        StickerPackViewerAdapter adapter = new StickerPackViewerAdapter(uris, this, nColumns, pack.getVersion());
        mainView.setAdapter(adapter);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                switch (adapter.getItemViewType(position)) {
                    case TYPE_IMAGE:
                        return 1;
                    case TYPE_HEADER:
                    case TYPE_DIVIDER:
                    case TYPE_TEXT:
                        return nColumns;
                    default:
                        return -1;
                }
            }
        });
        
        if (picker) {
            adapter.setOnClickListener(((holder, uri) -> {
                Intent data = new Intent();
                data.putExtra("uri", uri);
                setResult(RESULT_OK, data);
                finish();
            }));
        } else {
            final List<String> urisNoHeaders = removeSpecialItems(uris);
            adapter.setOnClickListener(((holder, uri) ->
                startLightBox(urisNoHeaders, adapter, holder, uri)
            ));
        }
    }
    
    private void startLightBox(List<String> urisNoHeaders, StickerPackViewerAdapter adapter, StickerPackViewerAdapter.StickerViewHolder holder, String uri) {
        int position = urisNoHeaders.indexOf(uri);
        LightboxOverlayView overlay = new LightboxOverlayView(
                this, urisNoHeaders, null, position, false, !remote);
        
        overlay.setGetTransitionImageCallback(pos -> {
            String item = urisNoHeaders.get(pos);
            pos = adapter.getPosOfItem(item);
            StickerPackViewerAdapter.StickerViewHolder vh = (StickerPackViewerAdapter.StickerViewHolder) mainView.findViewHolderForAdapterPosition(pos);
            return (vh == null) ? null : vh.imageView;
        });
        
        StfalconImageViewer viewer = new StfalconImageViewer.Builder<>(this, urisNoHeaders,
                (v, src) -> {
                    GlideRequest request = GlideApp.with(this).load(src);
                    // Enable caching for remote loads---see CustomAppGlideModule
                    if (Util.stringIsURL(src))
                        request.signature(new ObjectKey(pack.getVersion())).diskCacheStrategy(DiskCacheStrategy.AUTOMATIC);
                    request.into(v);
                })
                .withStartPosition(urisNoHeaders.indexOf(uri))
                .withOverlayView(overlay)
                .withImageChangeListener(overlay::setPos)
                .withHiddenStatusBar(false)
                .withTransitionFrom(holder.imageView)
                .show();
        
        overlay.setViewer(viewer);
    }
    
    private List<String> formatUpdatedUris(List<String> uris, List<String> updatedUris) {
        int nNewStickers = updatedUris.size();
        List<String> output = new LinkedList<>();
        
        // Make copy to mutate
        uris = new LinkedList<>(uris);
        for (String uri : updatedUris)
            uris.remove(uri);
        
        output.add(HEADER_PREFIX + String.format(getString(R.string.new_stickers_report), nNewStickers, (nNewStickers > 1) ? "s" : ""));
        output.addAll(updatedUris);
        output.add(DIVIDER_CODE);
    
        output.addAll(uris);
        return output;
    }
    
    private List<String> removeSpecialItems(List<String> uris) {
        List<String> output = new LinkedList<>();
        for (String uri : uris) {
            if (isImage(uri))
                output.add(uri);
        }
        return output;
    }
    
    private void displayLoading() {
        refreshButton.setVisibility(View.GONE);
        swipeRefresh.setRefreshing(true);
    }
    
    private void showDownloadSuccess(Boolean downloadSuccess) {
        if (!downloadSuccess) {
            swipeRefresh.setRefreshing(false);
            Snackbar.make(refreshButton, getString(R.string.no_network), Snackbar.LENGTH_LONG).show();
            List<String> urls = model.getUris().getValue();
            if (urls == null || urls.size() == 0)
                refreshButton.setVisibility(View.VISIBLE);
        } else
            refreshButton.setVisibility(View.GONE);
    }
    
    private void showDownloadException(Exception e) {
        if (e != null) {
            swipeRefresh.setRefreshing(false);
            Log.e(TAG, "Download exception", e);
            Snackbar.make(refreshButton, getString(R.string.network_error), Snackbar.LENGTH_LONG).show();
            List<String> urls = model.getUris().getValue();
            if (urls == null || urls.size() == 0)
                refreshButton.setVisibility(View.VISIBLE);
        }
    }
    
    private void updateFromDownloadedUrls(List<String> urls) {
        if (urls != null) {
            swipeRefresh.setRefreshing(false);
            if (isImage(urls.get(0)))
                urls.add(0, TEXT_PREFIX + getString(R.string.uninstalled_stickers_warning));
            setupMainView(urls);
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (picker && item.getItemId() == android.R.id.home) {
            // In picker mode, go "back" to the pack picker rather
            // than "up" to the main activity
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
