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
    private StickerPackViewerAdapter adapter;
    private List<String> urisNoHeaders;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Util.performNeededMigrations(this);
        setContentView(R.layout.activity_sticker_pack_viewer);
        pack = (StickerPack) this.getIntent().getSerializableExtra("pack");
        picker = this.getIntent().getBooleanExtra("picker", false);
        
        setSupportActionBar(findViewById(R.id.toolbar));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        setTitle(pack.getPackname() + " Sticker Pack");
        
        refreshButton = findViewById(R.id.refresh_button);
        refreshButton.setOnClickListener(v -> refresh());
        
        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setOnRefreshListener(this::refresh);
        swipeRefresh.setColorSchemeResources(R.color.colorAccent);
        
        if (pack.getStatus() != StickerPack.Status.UNINSTALLED && pack.getStatus() != StickerPack.Status.UPDATEABLE)
            swipeRefresh.setEnabled(false);
        
        model = ViewModelProviders.of(this).get(StickerPackViewerViewModel.class);
        
        List<String> uris = pack.getStickerURIs();
        
        mainView = findViewById(R.id.main_view);
        mainView.setHasFixedSize(true);
        
        model.getDownloadException().observe(this, this::showDownloadException);
        model.getDownloadSuccess().observe(this, this::showDownloadSuccess);
        model.getUris().observe(this, this::showDownloadedImages);
        model.getDownloadRunning().observe(this, this::showProgress);
        
        if (!model.isInitialized()) {
            model.setPack(pack);
            refresh();
        }
        
        DisplayMetrics displayMetrics = this.getResources().getDisplayMetrics();
        float targetSize = getResources().getDimension(R.dimen.sticker_pack_viewer_target_image_size);
        int nColumns = (int) (displayMetrics.widthPixels / targetSize + 0.5); // +0.5 for correct rounding to int.
    
        GridLayoutManager layoutManager = new GridLayoutManager(this, nColumns);
        mainView.setLayoutManager(layoutManager);
    
        adapter = new StickerPackViewerAdapter(uris, this, nColumns, pack.getVersion());
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
            adapter.setOnClickListener(((holder, uri) ->
                    startLightBox(adapter, holder, uri)
            ));
        }
    }
    
    private void refresh() {
        model.refreshData();
    }
    
    private void startLightBox(StickerPackViewerAdapter adapter, StickerPackViewerAdapter.StickerViewHolder holder, String uri) {
        if (urisNoHeaders == null || urisNoHeaders.size() == 0)
            return;
        int position = urisNoHeaders.indexOf(uri);
        LightboxOverlayView overlay = new LightboxOverlayView(
                this, urisNoHeaders, null, position, false, true);
        
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
    
    private List<String> removeSpecialItems(List<String> uris) {
        List<String> output = new LinkedList<>();
        for (String uri : uris) {
            if (isImage(uri))
                output.add(uri);
        }
        return output;
    }
    
    private void showDownloadSuccess(Boolean downloadSuccess) {
        if (!downloadSuccess) {
            if ((pack.getStatus() == StickerPack.Status.UNINSTALLED && !model.haveUrls())
                || (pack.getStatus() == StickerPack.Status.UPDATEABLE))
                refreshButton.setVisibility(View.VISIBLE);
        } else
            refreshButton.setVisibility(View.GONE);
    }
    
    private void showDownloadException(Exception e) {
        if (e != null) {
            String message;
            if (!Util.connectedToInternet(this)) {
                Log.w(TAG, "Not connected to internet");
                message = getString(R.string.no_network);
            } else {
                Log.e(TAG, "Download exception", e);
                message = getString(R.string.network_error);
            }
            Snackbar.make(refreshButton, message, Snackbar.LENGTH_LONG).show();
            
            model.clearException();
        }
    }
    
    private void showProgress(Boolean inProgress) {
        swipeRefresh.setRefreshing(inProgress);
    }
    
    private void showDownloadedImages(List<String> urls) {
        if (urls != null) {
            urisNoHeaders = removeSpecialItems(urls);
            adapter.replaceDataSource(urls);
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
