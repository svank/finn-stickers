package net.samvankooten.finnstickers.sticker_pack_viewer;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;

import com.google.android.material.snackbar.Snackbar;
import com.stfalcon.imageviewer.StfalconImageViewer;

import net.samvankooten.finnstickers.LightboxOverlayView;
import net.samvankooten.finnstickers.R;
import net.samvankooten.finnstickers.StickerPack;
import net.samvankooten.finnstickers.StickerPackViewHolder;
import net.samvankooten.finnstickers.misc_classes.GlideApp;
import net.samvankooten.finnstickers.misc_classes.GlideRequest;
import net.samvankooten.finnstickers.utils.StickerPackRepository;
import net.samvankooten.finnstickers.utils.Util;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import static net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerAdapter.CENTERED_TEXT_PREFIX;
import static net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerAdapter.PACK_CODE;
import static net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerAdapter.REFRESH_CODE;
import static net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerAdapter.isPack;
import static net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerAdapter.removeSpecialItems;

public class StickerPackViewerActivity extends AppCompatActivity {
    
    private static final String TAG = "StckrPackViewerActivity";
    public static final String PACK = "pack";
    public static final String PICKER = "picker";
    public static final String ALL_PACKS = "allpacks";
    
    private StickerPack pack;
    private StickerPackViewerViewModel model;
    private SwipeRefreshLayout swipeRefresh;
    private LockableRecyclerView mainView;
    private StickerPackViewerAdapter adapter;
    private List<String> urisNoHeaders;
    private boolean allPackMode;
    private boolean showRefreshButton;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Util.performNeededMigrations(this);
        setContentView(R.layout.activity_sticker_pack_viewer);
        postponeEnterTransition();
        
        setSupportActionBar(findViewById(R.id.toolbar));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        boolean firstStart = savedInstanceState == null;
    
        allPackMode = getIntent().getBooleanExtra(ALL_PACKS, false);
        boolean picker = getIntent().getBooleanExtra(PICKER, false);
        
        model = ViewModelProviders.of(this).get(StickerPackViewerViewModel.class);
        
        if (!model.isInitialized()) {
            try {
                if (allPackMode) {
                    pack = StickerPackRepository.getInstalledStickersAsOnePack(this);
                } else {
                    String packName = getIntent().getStringExtra(PACK);
                    pack = StickerPackRepository.getInstalledOrCachedPackByName(packName, this);
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
        
        setTitle(allPackMode ? getString(R.string.sticker_pack_viewer_toolbar_title_all_packs) : "");
        
        setDarkStatusBarText(true);
        
        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setOnRefreshListener(this::refresh);
        swipeRefresh.setColorSchemeResources(R.color.colorAccent);
        
        mainView = findViewById(R.id.main_view);
        
        model.getDownloadException().observe(this, this::showDownloadException);
        model.getDownloadSuccess().observe(this, this::showDownloadSuccess);
        model.getUris().observe(this, this::showDownloadedImages);
        model.getDownloadRunning().observe(this, this::showProgress);
        model.getLiveIsSearching().observe(this, v -> setupSwipeRefresh());
        pack.getLiveStatus().observe(this, status -> refresh());
        
        DisplayMetrics displayMetrics = this.getResources().getDisplayMetrics();
        float targetSize = getResources().getDimension(R.dimen.sticker_pack_viewer_target_image_size);
        int nColumns = (int) (displayMetrics.widthPixels / targetSize + 0.5); // +0.5 for correct rounding to int.
        
        GridLayoutManager layoutManager = new GridLayoutManager(this, nColumns);
        mainView.setLayoutManager(layoutManager);
        
        List<String> starterList;
        if (model.getUris().getValue() != null)
            starterList = model.getUris().getValue();
        else if (allPackMode)
            starterList = null;
        else
            starterList = Collections.singletonList(PACK_CODE);
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
        adapter.setOnRefreshListener(this::refresh);
        
        mainView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                View navBg = findViewById(android.R.id.navigationBarBackground);
                if (navBg != null)
                    navBg.setTransitionName("navbar");
                
                if (!allPackMode) {
                    StickerPackViewHolder holder = (StickerPackViewHolder) mainView.findViewHolderForAdapterPosition(0);
                    // holder might be null if the user has scrolled down and then rotated the
                    // device, so use a static method to get the transition name
                    findViewById(R.id.transition).setTransitionName(
                            StickerPackViewHolder.getTransitionName(pack.getPackname()));
                    if (holder != null && firstStart)
                        holder.setSoloItem(true, true);
                }
                
                startPostponedEnterTransition();
                mainView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });
    }
    
    private void setupSwipeRefresh() {
        if (pack.getStatus() != StickerPack.Status.UNINSTALLED && pack.getStatus() != StickerPack.Status.UPDATEABLE)
            swipeRefresh.setEnabled(false);
        else if (model.isSearching())
            swipeRefresh.setEnabled(false);
        else
            swipeRefresh.setEnabled(true);
    }
    
    private void refresh() {
        if (!model.isSearching())
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
                .withDismissListener(() -> setDarkStatusBarText(true))
                .show();
        
        overlay.setViewer(viewer);
        setDarkStatusBarText(false);
    }
    
    private void showDownloadSuccess(Boolean downloadSuccess) {
        showRefreshButton = !downloadSuccess;
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
            Snackbar.make(mainView, message, Snackbar.LENGTH_LONG).show();
            
            model.clearException();
        }
    }
    
    private void showProgress(Boolean inProgress) {
        swipeRefresh.setRefreshing(inProgress);
    }
    
    private void showDownloadedImages(List<String> urls) {
        if (urls == null)
            return;
    
        // Ensure swipeRefresh is enabled/disabled when the pack is installed/removed
        setupSwipeRefresh();
        
        if (allPackMode && urls.size() > 0 && isPack(urls.get(0)))
            urls.remove(0);
        
        if (showRefreshButton) {
            // Don't add a refresh button underneath a bunch of stickers
            if (removeSpecialItems(urls).size() == 0) {
                // If the stickers were loaded successfully in the past and now a refresh has failed,
                // don't clear the screen
                if (adapter.hasStickers())
                    return;
                urls = new ArrayList<>(urls);
                urls.add(1, REFRESH_CODE);
            }
        }
        
        if (allPackMode && pack.getStickers().size() == 0) {
            urls = new LinkedList<>();
            urls.add(CENTERED_TEXT_PREFIX + getString(R.string.sticker_pack_viewer_no_packs_installed));
        }
    
        urisNoHeaders = StickerPackViewerAdapter.removeSpecialItems(urls);
        adapter.replaceDataSource(urls);
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
                    search.expandActionView();
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
    
    @Override
    public void onBackPressed() {
        mainView.setLocked(true);
        
        /*
        Don't leave the status bar text dark until the transition is over (the default behavior).
        That looks bad. Changing the color now looks smoother.
         */
        setDarkStatusBarText(false);
        
        if (!allPackMode) {
            if (((GridLayoutManager) mainView.getLayoutManager()).findFirstCompletelyVisibleItemPosition() != 0)
                ObjectAnimator.ofFloat(findViewById(R.id.transition), View.ALPHA, 1f, 0f).setDuration(400).start();
            else {
                StickerPackViewHolder holder = (StickerPackViewHolder) mainView.findViewHolderForAdapterPosition(0);
                holder.setSoloItem(false, true);
        
                // For wide screens, where MainActivity list items don't span the whole screen
                holder.getTopLevelView().setGravity(Gravity.LEFT);
                View notTooWideView = holder.getNotTooWideView();
                notTooWideView.setPadding(0, 0, 2 * notTooWideView.getPaddingRight(), 0);
            }
        }
        
        finishAfterTransition();
    }
    
    private void setDarkStatusBarText(boolean dark) {
        if (Build.VERSION.SDK_INT < 23)
            return;
        
        int flags = getWindow().getDecorView().getSystemUiVisibility();
        if (dark)
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        else
            flags ^= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }
}
