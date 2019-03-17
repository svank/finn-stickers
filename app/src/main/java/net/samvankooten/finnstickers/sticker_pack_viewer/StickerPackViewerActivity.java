package net.samvankooten.finnstickers.sticker_pack_viewer;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;

import com.google.android.material.snackbar.Snackbar;
import com.stfalcon.imageviewer.StfalconImageViewer;

import net.samvankooten.finnstickers.LightboxOverlayView;
import net.samvankooten.finnstickers.R;
import net.samvankooten.finnstickers.StickerPack;
import net.samvankooten.finnstickers.misc_classes.GlideApp;
import net.samvankooten.finnstickers.misc_classes.GlideRequest;
import net.samvankooten.finnstickers.utils.StickerPackRepository;
import net.samvankooten.finnstickers.utils.Util;

import org.json.JSONException;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.view.MenuItemCompat;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import static net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerAdapter.CENTERED_TEXT_PREFIX;
import static net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerAdapter.PACK_CODE;
import static net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerAdapter.isPack;
import static net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerAdapter.removeSpecialItems;

public class StickerPackViewerActivity extends AppCompatActivity {
    
    private static final String TAG = "StckrPackViewerActivity";
    public static final String PACK = "pack";
    public static final String PICKER = "picker";
    public static final String ALL_PACKS = "allpacks";
    
    private StickerPack pack;
    private boolean picker;
    private StickerPackViewerViewModel model;
    private SwipeRefreshLayout swipeRefresh;
    private Button refreshButton;
    private RecyclerView mainView;
    private StickerPackViewerAdapter adapter;
    private List<String> urisNoHeaders;
    private boolean allPackMode;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Util.performNeededMigrations(this);
        setContentView(R.layout.activity_sticker_pack_viewer);
    
        setSupportActionBar(findViewById(R.id.toolbar));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    
        model = ViewModelProviders.of(this).get(StickerPackViewerViewModel.class);
    
        if (!model.isInitialized()) {
            try {
                if (getIntent().getBooleanExtra(ALL_PACKS, false)) {
                    pack = StickerPackRepository.getInstalledStickersAsOnePack(this);
                    setTitle(getString(R.string.sticker_pack_viewer_toolbar_title_all_packs));
                } else {
                    String packName = getIntent().getStringExtra(PACK);
                    pack = StickerPackRepository.getInstalledOrCachedPackByName(packName, this);
                    setTitle(String.format(getString(R.string.sticker_pack_viewer_toolbar_title),
                            pack.getPackname()));
                }
                model.setPack(pack);
                refresh();
            } catch (JSONException e) {
                Log.e(TAG, "Error loading pack", e);
                Snackbar.make(findViewById(R.id.main_view), getString(R.string.unexpected_error),
                        Snackbar.LENGTH_LONG).show();
            }
        } else
            pack = model.getPack();
        
        allPackMode = getIntent().getBooleanExtra(ALL_PACKS, false);
        picker = getIntent().getBooleanExtra(PICKER, false);
        
        refreshButton = findViewById(R.id.refresh_button);
        refreshButton.setOnClickListener(v -> refresh());
        
        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setOnRefreshListener(this::refresh);
        swipeRefresh.setColorSchemeResources(R.color.colorAccent);
    
        mainView = findViewById(R.id.main_view);
        mainView.setHasFixedSize(true);
        
        model.getDownloadException().observe(this, this::showDownloadException);
        model.getDownloadSuccess().observe(this, this::showDownloadSuccess);
        model.getUris().observe(this, this::showDownloadedImages);
        model.getDownloadRunning().observe(this, this::showProgress);
        pack.getLiveStatus().observe(this, (status) -> refresh());
        
        DisplayMetrics displayMetrics = this.getResources().getDisplayMetrics();
        float targetSize = getResources().getDimension(R.dimen.sticker_pack_viewer_target_image_size);
        int nColumns = (int) (displayMetrics.widthPixels / targetSize + 0.5); // +0.5 for correct rounding to int.
    
        GridLayoutManager layoutManager = new GridLayoutManager(this, nColumns);
        mainView.setLayoutManager(layoutManager);
    
        List<String> starterList = allPackMode ? null : Collections.singletonList(PACK_CODE);
        adapter = new StickerPackViewerAdapter(starterList, this, pack);
        mainView.setAdapter(adapter);
        layoutManager.setSpanSizeLookup(adapter.getSpaceSizeLookup(nColumns));
    
        if (picker) {
            adapter.setOnClickListener(((holder, uri) -> {
                if (Util.stringIsURL(uri))
                    return;
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
    
    private void setupSwipeRefresh() {
        if (pack.getStatus() != StickerPack.Status.UNINSTALLED && pack.getStatus() != StickerPack.Status.UPDATEABLE)
            swipeRefresh.setEnabled(false);
        else
            swipeRefresh.setEnabled(true);
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
                    
                    Util.enableGlideCacheIfRemote(request, src, pack.getVersion());
                    
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
    
    private void showDownloadSuccess(Boolean downloadSuccess) {
        if (!downloadSuccess) {
            if ((pack.getStatus() == StickerPack.Status.UNINSTALLED &&
                    removeSpecialItems(model.getUris().getValue()).size() == 0)
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
        if (urls == null)
            return;
        
        setupSwipeRefresh();
        
        if (allPackMode && urls.size() > 0 && isPack(urls.get(0)))
            urls.remove(0);
        
        urisNoHeaders = StickerPackViewerAdapter.removeSpecialItems(urls);
        adapter.replaceDataSource(urls);
        
        if (allPackMode && pack.getStickers().size() == 0) {
            urls = new LinkedList<>();
            urls.add(CENTERED_TEXT_PREFIX + getString(R.string.sticker_pack_viewer_no_packs_installed));
            adapter.replaceDataSource(urls);
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        /*
        If we're viewing just a single pack, search is only an option. The SearchView should be
        collapsed by default, and the back button should just close it if it's open.
        If the user hits the Search button in MainActivity, we're showing _all_ installed stickers
        and we want to dive right into search. To make the SearchView expand by default and to
        have the back button end the activity rather than just collapse the search widget,
        some things have to be done differently.
         */
        if (allPackMode)
            getMenuInflater().inflate(R.menu.stickerpack_viewer_search_menu_items, menu);
        else
            getMenuInflater().inflate(R.menu.stickerpack_viewer_menu_items, menu);
        
        MenuItem search = menu.findItem(R.id.search);
        SearchView searchView = (SearchView) search.getActionView();
        
        if (allPackMode) {
            searchView.setQueryHint(getString(R.string.search_installed_hint));
            searchView.setIconifiedByDefault(false);
            searchView.requestFocus();
            /*
            It seems that if the search widget is open by default, then in landscape mode it turns
            into a full-screen text input field. We want it to stay in the toolbar.
             */
            int options = searchView.getImeOptions();
            searchView.setImeOptions(options | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        } else
            searchView.setQueryHint(getString(R.string.search_hint));
        
        if (model.isSearching()) {
            // So we want the search view to be (1) expanded and (2) pre-populated if we're
            // re-creating the activity after a rotation. If we do those two things right away,
            // I'm always having that if I then close the search widget, it doesn't collapse back
            // to a search icon like it should. Instead it collapses to the three-dots menu button,
            // but clicking that button doesn't do anything. I have no idea why, but just adding
            // this delay (on the idea that it lets the search view be a collapsed icon for a
            // little while before it expands) seems to fix the problem, and the delay is eaten up
            // by the rotation animation, so it's not perceptible.
            final String queryString = model.getFilterString();
            
            if (allPackMode) {
                searchView.setQuery(queryString, false);
            } else {
                new Handler().postDelayed(() -> {
                    MenuItemCompat.expandActionView(search);
                    searchView.setQuery(queryString, false);
                }, 200);
            }
        } else if (allPackMode) {
            model.startSearching();
        }
        
        searchView.setOnQueryTextListener(model);
        search.setOnActionExpandListener(model);
        
        return super.onCreateOptionsMenu(menu);
    }
}
