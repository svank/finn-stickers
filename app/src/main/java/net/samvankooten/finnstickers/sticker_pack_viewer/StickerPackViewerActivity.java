package net.samvankooten.finnstickers.sticker_pack_viewer;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.pm.ShortcutManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
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
    public static final String SELECTED_STICKER = "selectedSticker";
    public static final String FADE_PACK_BACK_IN = "fadePackBackIn";
    
    private static final String CURRENTLY_SHOWING = "currently_showing";
    
    private StickerPack pack;
    private StickerPackViewerViewModel model;
    private SwipeRefreshLayout swipeRefresh;
    private LockableRecyclerView mainView;
    private StickerPackViewerAdapter adapter;
    private List<String> urisNoHeaders;
    private boolean allPackMode;
    private boolean showRefreshButton;
    
    private int popupViewerCurrentlyShowing = -1;
    
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
            if (allPackMode) {
                pack = StickerPackRepository.getInstalledStickersAsOnePack(this);
            } else {
                String packName = getIntent().getStringExtra(PACK);
                pack = StickerPackRepository.getInstalledOrCachedPackByName(packName, this);
            }
            if (pack == null) {
                Log.e(TAG, "Error loading pack");
                Snackbar.make(findViewById(R.id.main_view), getString(R.string.unexpected_error),
                        Snackbar.LENGTH_LONG).show();
                return;
            } else {
                model.setPack(pack);
                refresh();
            }
        } else
            pack = model.getPack();
    
        if (firstStart && !allPackMode && Build.VERSION.SDK_INT >= 25)
            getSystemService(ShortcutManager.class).reportShortcutUsed(pack.getPackname());
        
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
        pack.getLiveStatus().observe(this, status -> onPackStatusChange());
        
        DisplayMetrics displayMetrics = this.getResources().getDisplayMetrics();
        float targetSize = getResources().getDimension(R.dimen.sticker_pack_viewer_target_image_size);
        int nColumns = (int) (displayMetrics.widthPixels / targetSize + 0.5); // +0.5 for correct rounding to int.
        
        GridLayoutManager layoutManager = new GridLayoutManager(this, nColumns);
        mainView.setLayoutManager(layoutManager);
        
        List<String> starterList;
        if (model.getUris().getValue() != null) {
            starterList = model.getUris().getValue();
            urisNoHeaders = removeSpecialItems(starterList);
        } else if (allPackMode)
            starterList = null;
        else
            starterList = Collections.singletonList(PACK_CODE);
        
        adapter = new StickerPackViewerAdapter(starterList, this, pack);
        if (firstStart)
            adapter.setShouldAnimateIn(true);
        mainView.setAdapter(adapter);
        layoutManager.setSpanSizeLookup(adapter.getSpaceSizeLookup(nColumns));
        
        if (picker) {
            adapter.setOnClickListener(((holder, uri) -> {
                if (Util.stringIsURL(uri))
                    return;
                Intent data = new Intent();
                data.putExtra(SELECTED_STICKER, uri);
                setResult(RESULT_OK, data);
                finish();
            }));
        } else {
            adapter.setOnClickListener(((holder, uri) ->
                    startLightBox(adapter, holder, uri)
            ));
        }
        adapter.setOnRefreshListener(this::refresh);
        
        // Tasks to run once the RecyclerView has finished drawing its first batch of Views
        mainView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                // Only run this once
                mainView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                
                commonTransitionDetails(true, firstStart);
                startPostponedEnterTransition();
                
                if (firstStart)
                    mainView.postDelayed(() -> adapter.setShouldAnimateIn(false), 50);
                
                // Reshow the popup viewer if it was open and then the screen rotated
                // Do it here so things are initialized regarding transition images
                if (savedInstanceState != null && savedInstanceState.containsKey(CURRENTLY_SHOWING)
                        && starterList != null) {
                    popupViewerCurrentlyShowing = savedInstanceState.getInt(CURRENTLY_SHOWING);
                    int adapterPos = starterList.indexOf(urisNoHeaders.get(popupViewerCurrentlyShowing));
                    startLightBox(adapter,
                            (StickerPackViewerAdapter.StickerViewHolder) mainView.findViewHolderForAdapterPosition(adapterPos),
                            urisNoHeaders.get(popupViewerCurrentlyShowing));
                }
            }
        });
    }
    
    private void commonTransitionDetails(boolean holderSoloStatus, boolean holderShouldAnimate) {
        // Set up transition details
        View navBg = findViewById(android.R.id.navigationBarBackground);
        if (navBg != null)
            navBg.setTransitionName("navbar");
        
        if (!allPackMode && !model.isSearching()) {
            RecyclerView.ViewHolder vh = mainView.findViewHolderForAdapterPosition(0);
            if (vh instanceof StickerPackViewHolder) {
                StickerPackViewHolder holder = (StickerPackViewHolder) vh;
                // holder might be null if the user has scrolled down and then rotated the
                // device, so use a static method to get the transition name
                findViewById(R.id.transition).setTransitionName(
                        StickerPackViewHolder.getTransitionName(pack.getPackname()));
                holder.setSoloItem(holderSoloStatus, holderShouldAnimate);
            }
        }
    }
    
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (popupViewerCurrentlyShowing >= 0)
            outState.putInt(CURRENTLY_SHOWING, popupViewerCurrentlyShowing);
    }
    
    private void setupSwipeRefresh() {
        if (pack.getStatus() != StickerPack.Status.UNINSTALLED && pack.getStatus() != StickerPack.Status.UPDATEABLE)
            swipeRefresh.setEnabled(false);
        else if (model.isSearching())
            swipeRefresh.setEnabled(false);
        else
            swipeRefresh.setEnabled(true);
    }
    
    private void onPackStatusChange() {
        refresh();
        invalidateOptionsMenu();
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
                .withImageChangeListener(pos -> {
                    overlay.setPos(pos);
                    popupViewerCurrentlyShowing = pos;
                })
                .withHiddenStatusBar(false)
                .withTransitionFrom(holder == null ? null : holder.imageView)
                .withDismissListener(() -> {
                    setDarkStatusBarText(true);
                popupViewerCurrentlyShowing = -1;})
                .show(popupViewerCurrentlyShowing < 0);
        
        overlay.setViewer(viewer);
        setDarkStatusBarText(false);
        popupViewerCurrentlyShowing = position;
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
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.add_shortcut:
                Util.pinAppShortcut(pack, this);
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
            if (!allPackMode)
                search.expandActionView();
            searchView.setQuery(model.getFilterString(), false);
        } else if (allPackMode) {
            model.startSearching();
        }
        
        searchView.setOnQueryTextListener(model);
        search.setOnActionExpandListener(model);
        
        if (Build.VERSION.SDK_INT < 26
                || allPackMode
                || pack.getStatus() != StickerPack.Status.INSTALLED) {
            menu.removeItem(R.id.add_shortcut);
        }
        
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
    
        if (allPackMode) {
            finishAfterTransition();
            return;
        }
    
        Intent data = new Intent();
    
        GridLayoutManager manager = (GridLayoutManager) mainView.getLayoutManager();
        if (manager.findFirstCompletelyVisibleItemPosition() != 0) {
            ObjectAnimator.ofFloat(findViewById(R.id.transition), View.ALPHA, 1f, 0f).setDuration(400).start();
            
            data.putExtra(FADE_PACK_BACK_IN, true);
        } else {
            StickerPackViewHolder topHolder = (StickerPackViewHolder) mainView.findViewHolderForAdapterPosition(0);
            commonTransitionDetails(false, true);
    
            // For wide screens, where MainActivity list items don't span the whole screen
            topHolder.getTopLevelView().setGravity(Gravity.LEFT);
            View notTooWideView = topHolder.getNotTooWideView();
            notTooWideView.setPadding(0, 0, 2 * notTooWideView.getPaddingRight(), 0);

            for (int i = manager.findFirstVisibleItemPosition();
                 i <= manager.findLastVisibleItemPosition();
                 i++) {
                RecyclerView.ViewHolder holder = mainView.findViewHolderForAdapterPosition(i);
                if (holder instanceof StickerPackViewerAdapter.TransitionViewHolder)
                    ((StickerPackViewerAdapter.TransitionViewHolder) holder).animateOut(
                            getResources().getInteger(R.integer.pack_view_animate_out_duration));
            }
        }
        
        setResult(RESULT_OK, data);
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
