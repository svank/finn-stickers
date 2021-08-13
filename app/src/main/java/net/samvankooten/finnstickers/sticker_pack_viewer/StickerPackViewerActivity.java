package net.samvankooten.finnstickers.sticker_pack_viewer;

import android.animation.ObjectAnimator;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutManager;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.transition.Transition;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.snackbar.Snackbar;
import com.stfalcon.imageviewer.StfalconImageViewer;

import net.samvankooten.finnstickers.LightboxOverlayConfirmDeleteFragment;
import net.samvankooten.finnstickers.LightboxOverlayView;
import net.samvankooten.finnstickers.R;
import net.samvankooten.finnstickers.Sticker;
import net.samvankooten.finnstickers.StickerPack;
import net.samvankooten.finnstickers.StickerPackViewHolder;
import net.samvankooten.finnstickers.editor.EditorActivity;
import net.samvankooten.finnstickers.misc_classes.GlideApp;
import net.samvankooten.finnstickers.misc_classes.GlideRequest;
import net.samvankooten.finnstickers.misc_classes.TransitionListenerAdapter;
import net.samvankooten.finnstickers.utils.ChangeOnlyObserver;
import net.samvankooten.finnstickers.utils.Util;
import net.samvankooten.finnstickers.utils.ViewUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.SearchView;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.selection.Selection;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.StorageStrategy;
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
    public static final String PICKER_ALLOW_MULTIPLE = "picker_allow_multiple";
    public static final String ALL_PACKS = "allpacks";
    public static final String FADE_PACK_BACK_IN = "fadePackBackIn";
    
    private static final String CURRENTLY_SHOWING = "currently_showing";
    
    private StickerPack pack;
    private StickerPackViewerViewModel model;
    private SwipeRefreshLayout swipeRefresh;
    private View transitionView;
    private LockableRecyclerView mainView;
    private StickerPackViewerAdapter adapter;
    private SelectionTracker<String> selectionTracker;
    private final ActionModeCallback actionModeCallback = new ActionModeCallback();
    private ActionMode actionMode;
    private final ArrayList<Uri> urisNoHeaders = new ArrayList<>();
    
    private boolean allPackMode;
    private boolean firstStart;
    private boolean picker;
    private boolean pickerAllowMultiple;
    
    private int popupViewerCurrentlyShowing = -1;
    private StfalconImageViewer<Uri> viewer;
    private LightboxOverlayView viewerOverlay;
    private Bundle pendingSavedInstanceState;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        setContentView(R.layout.activity_sticker_pack_viewer);
        postponeEnterTransition();
        
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        final ViewUtils.LayoutData toolbarPadding = ViewUtils.recordLayoutData(toolbar);
        toolbar.setOnApplyWindowInsetsListener((v, windowInsets) -> {
            // The toolbar needs top padding to handle the status bar properly
            ViewUtils.updatePaddingTop(toolbar, windowInsets.getSystemWindowInsetTop(),
                    toolbarPadding);
            return windowInsets;
        });
        
        View appBarLayout = findViewById(R.id.app_bar_layout);
        final ViewUtils.LayoutData appBarLayoutPadding = ViewUtils.recordLayoutData(appBarLayout);
        appBarLayout.setOnApplyWindowInsetsListener((v, windowInsets) -> {
            // The appBarLayout needs side margin so it doesn't draw under the
            // transparent nav bar in landscape mode
            ViewUtils.updateMarginSides(appBarLayout,
                    windowInsets.getSystemWindowInsetLeft(),
                    windowInsets.getSystemWindowInsetRight(),
                    appBarLayoutPadding);
            return windowInsets;
        });
        
        firstStart = savedInstanceState == null;
        pendingSavedInstanceState = savedInstanceState;
        
        allPackMode = getIntent().getBooleanExtra(ALL_PACKS, false);
        picker = getIntent().getBooleanExtra(PICKER, false);
        if (picker)
            pickerAllowMultiple = getIntent().getBooleanExtra(PICKER_ALLOW_MULTIPLE, false);
        
        model = new ViewModelProvider(this).get(StickerPackViewerViewModel.class);
        model.getLivePack().observe(this, this::packRelatedSetup);
        
        if (!model.isInitialized()) {
            if (allPackMode) {
                model.setAllPacks();
            } else {
                String packName = getIntent().getStringExtra(PACK);
                model.setPack(packName);
            }
        } else
            // A local refresh is cheap, and this handles the case that we're rebuilding
            // this activity after the screen was rotated while in EditorActivity and a
            // sticker was saved. The old ViewActivity will have been in the background
            // and so won't have gotten a LiveData update, so we won't see the newly-saved
            // sticker unless we update our model.
            model.refreshLocalData();
        
        setTitle("");
        
        setDarkStatusBarText(true);
        
        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setOnRefreshListener(this::refresh);
        swipeRefresh.setColorSchemeResources(R.color.colorAccent);
        
        mainView = findViewById(R.id.main_view);
        
        final ViewUtils.LayoutData mainViewPadding = ViewUtils.recordLayoutData(mainView);
        mainView.setOnApplyWindowInsetsListener((v, windowInsets) -> {
            // mainView needs bottom padding for the transparent nav bar
            ViewUtils.updatePaddingBottom(mainView,
                    windowInsets.getSystemWindowInsetBottom(),
                    mainViewPadding);
            return windowInsets;
        });
    
        transitionView = findViewById(R.id.transition);
        
        final ViewUtils.LayoutData transitionViewPadding = ViewUtils.recordLayoutData(transitionView);
        transitionView.setOnApplyWindowInsetsListener((v, windowInsets) -> {
            // transitionView needs side margin for the transparent nav bar in landscape
            ViewUtils.updateMarginSides(transitionView,
                    windowInsets.getSystemWindowInsetLeft(),
                    windowInsets.getSystemWindowInsetRight(),
                    transitionViewPadding);
            return windowInsets;
        });
        
        model.getDownloadException().observe(this, this::showDownloadException);
        model.getUris().observe(this, this::showImages);
        model.getDownloadRunning().observe(this, this::showProgress);
        
        DisplayMetrics displayMetrics = this.getResources().getDisplayMetrics();
        float targetSize = getResources().getDimension(R.dimen.sticker_pack_viewer_target_image_size);
        int nColumns = (int) (displayMetrics.widthPixels / targetSize + 0.5); // +0.5 for correct rounding to int.
        
        GridLayoutManager layoutManager = new GridLayoutManager(this, nColumns);
        mainView.setLayoutManager(layoutManager);
        
        List<String> starterList;
        if (allPackMode || model.getPack() == null)
            starterList = null;
        else if (model.getUris().getValue() != null) {
            starterList = model.getUris().getValue();
            setUrisNoHeaders(starterList);
        } else
            starterList = Collections.singletonList(PACK_CODE);
        
        adapter = new StickerPackViewerAdapter(starterList, this);
        if (model.getPack() != null)
            adapter.setPack(model.getPack());
        if (firstStart)
            adapter.setShouldAnimateIn(true);
        mainView.setAdapter(adapter);
        layoutManager.setSpanSizeLookup(adapter.getSpaceSizeLookup(nColumns));
    
        selectionTracker = setupSelectionTracker();
        if (selectionTracker != null) {
            selectionTracker.onRestoreInstanceState(savedInstanceState);
            adapter.setTracker(selectionTracker);
        }
        
        if (picker) {
            adapter.setOnClickListener(((holder, uri) -> {
                if (Util.stringIsURL(uri))
                    return;
                Intent data = new Intent();
                data.setData(Uri.parse(uri));
                setResult(RESULT_OK, data);
                finish();
            }));
        } else {
            adapter.setOnClickListener(((holder, uri) -> {
                InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null && getCurrentFocus() != null)
                    imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
                if (actionMode == null)
                    startLightBox(adapter, holder, Uri.parse(uri));
            }
            ));
        }
        adapter.setOnRefreshListener(this::refresh);
    
        /*
         * Reload gifs after window transition completes. See
         * StickerViewHolder#onWindowTransitionComplete for details.
         */
        getWindow().getSharedElementEnterTransition().addListener(new TransitionListenerAdapter() {
            @Override
            public void onTransitionEnd(Transition transition) {
                mainView.postDelayed(() -> {
                    if (firstStart) {
                        for (int i = 0; i < adapter.getItemCount(); i++) {
                            RecyclerView.ViewHolder holder = mainView.findViewHolderForAdapterPosition(i);
                            if (holder instanceof StickerPackViewerAdapter.StickerViewHolder)
                                ((StickerPackViewerAdapter.StickerViewHolder) holder)
                                        .onWindowTransitionComplete();
                        }
                    }
                }, 100);
            }
        });
        
        if (model.isShowingFilterDialog()) {
            showFilterDialog();
        }
        
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
                if (pendingSavedInstanceState != null
                        && pendingSavedInstanceState.containsKey(CURRENTLY_SHOWING)
                        && starterList != null) {
                    popupViewerCurrentlyShowing = pendingSavedInstanceState.getInt(CURRENTLY_SHOWING);
                    pendingSavedInstanceState = null;
                    int adapterPos = starterList.indexOf(urisNoHeaders.get(popupViewerCurrentlyShowing).toString());
                    startLightBox(adapter,
                            (StickerPackViewerAdapter.StickerViewHolder) mainView.findViewHolderForAdapterPosition(adapterPos),
                            urisNoHeaders.get(popupViewerCurrentlyShowing));
                    // It appears we need a bit more time before the viewer can find the imageView,
                    // but I'm not sure just what we're waiting for or how to listen for that happening.
                    mainView.postDelayed(new Runnable() {
                             @Override
                             public void run() {
                                 StickerPackViewerAdapter.StickerViewHolder viewHolder =
                                         (StickerPackViewerAdapter.StickerViewHolder) mainView.findViewHolderForAdapterPosition(adapterPos);
                                 if (viewHolder == null)
                                     // Try again later
                                     mainView.postDelayed(this,
                                             50);
                                 else
                                     viewer.updateTransitionImage(viewHolder.imageView);
                             }
                         },
                        0);
                }
            }
        });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        if (viewer != null)
            viewer.dismiss();
    }
    
    private void packRelatedSetup(StickerPack pack) {
        if (pack == null || this.pack != null)
            return;
        
        this.pack = pack;
        
        if (adapter != null)
            adapter.setPack(pack);
        
        if (firstStart && !allPackMode && Build.VERSION.SDK_INT >= 25) {
            ShortcutManager sm = getSystemService(ShortcutManager.class);
            if (sm != null)
                sm.reportShortcutUsed(pack.getPackname());
        }
        
        pack.getLiveStatus().observe(this, new ChangeOnlyObserver<>(
                status -> onPackStatusChange()));
    }
    
    private void commonTransitionDetails(boolean holderSoloStatus, boolean holderShouldAnimate) {
        View navBg = findViewById(android.R.id.navigationBarBackground);
        if (navBg != null)
            navBg.setTransitionName("navbar");
        // Views involved in shared element transitions live in a layer above everything else
        // for the duration of the transition, causing the animating sticker pack to cover up
        // the nav bar, which then snaps below it once the transition ends. So we hide the nav
        // bar background during the transition, then fade it in afterward. The delay before
        // fade-in needs to be long enough, or there's still a visual snap, but I'm not sure just
        // how long it needs to be or what we're waiting for.
        if (firstStart
                && getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT
                && navBg != null) {
            navBg.setAlpha(0);
            navBg.postDelayed( () ->
                    ObjectAnimator.ofFloat(navBg, View.ALPHA, 0f, 1f)
                            .setDuration(450)
                            .start(),
                    getResources().getInteger(R.integer.pack_view_navbar_fade_in_delay));
        }
        
        // Set up transition details
        if (!allPackMode && model.isShowingAllStickers()) {
            RecyclerView.ViewHolder vh = mainView.findViewHolderForAdapterPosition(0);
            if (vh instanceof StickerPackViewHolder) {
                StickerPackViewHolder holder = (StickerPackViewHolder) vh;
                // holder might be null if the user has scrolled down and then rotated the
                // device, so use a static method to get the transition name
                if (pack != null)
                    transitionView.setTransitionName(
                            StickerPackViewHolder.getTransitionName(pack.getPackname()));
                holder.setSoloItem(holderSoloStatus, holderShouldAnimate);
            }
        }
    }
    
    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (popupViewerCurrentlyShowing >= 0)
            outState.putInt(CURRENTLY_SHOWING, popupViewerCurrentlyShowing);
        if (selectionTracker != null)
            selectionTracker.onSaveInstanceState(outState);
    }
    
    private void setupSwipeRefresh() {
        if (pack == null
                || pack.getStatus() == StickerPack.Status.INSTALLING
                || pack.getStatus() == StickerPack.Status.INSTALLED)
            swipeRefresh.setEnabled(false);
        else
            swipeRefresh.setEnabled(true);
    }
    
    private void onPackStatusChange() {
        refresh();
        if (!model.isSearching())
            invalidateOptionsMenu();
    }
    
    private void refresh() {
        model.refreshData();
    }
    
    private void startLightBox(StickerPackViewerAdapter adapter,
                               StickerPackViewerAdapter.StickerViewHolder holder,
                               Uri uri) {
        if (urisNoHeaders.size() == 0)
            return;
        int position = urisNoHeaders.indexOf(uri);
        
        // Ensure no problems if urisNoHeaders changes while lightbox is open
        final List<Uri> uris = new ArrayList<>(urisNoHeaders);
        
        viewerOverlay = new LightboxOverlayView(
                this, uris, position, false);
        
        viewerOverlay.setGetTransitionImageCallback(item -> {
            int pos = adapter.getPosOfItem(item.toString());
            StickerPackViewerAdapter.StickerViewHolder vh = (StickerPackViewerAdapter.StickerViewHolder) mainView.findViewHolderForAdapterPosition(pos);
            return (vh == null) ? null : vh.imageView;
        });
        
        viewer = new StfalconImageViewer.Builder<>(this, uris,
                (v, src) -> {
                    GlideRequest<Drawable> request = GlideApp.with(this).load(src);
                    
                    Util.enableGlideCacheIfRemote(request, src.toString(), pack.getVersion());
                    
                    request.into(v);
                })
                .withStartPosition(position)
                .withOverlayView(viewerOverlay)
                .withImageChangeListener(pos -> {
                    viewerOverlay.setPos(pos);
                    popupViewerCurrentlyShowing = pos;
                })
                .withHiddenStatusBar(false)
                .withTransitionFrom(holder == null ? null : holder.imageView)
                .withDismissListener(() -> {
                    setDarkStatusBarText(true);
                    popupViewerCurrentlyShowing = -1;
                    viewer = null;
                    viewerOverlay = null;})
                .show(popupViewerCurrentlyShowing < 0);
    
        viewerOverlay.setViewer(viewer);
        updateViewerOverlay();
        viewerOverlay.setOnEditCallback(this::startEditing);
        viewerOverlay.setOnDeleteCallback(this::onDeleteSticker);
        setDarkStatusBarText(false);
        popupViewerCurrentlyShowing = position;
    }
    
    private void updateViewerOverlay() {
        viewerOverlay.setAreDeletable(model.getAreDeletable());
        viewerOverlay.setAreEditable(model.getAreEditable());
    }
    
    private void startEditing(int pos) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null)
            imm.hideSoftInputFromWindow(mainView.getWindowToken(), 0);
        
        Intent intent = new Intent(this, EditorActivity.class);
        
        String uri = urisNoHeaders.get(pos).toString();
        
        intent.putExtra(EditorActivity.PACK_NAME,
                model.getPack().getStickerByUri(uri).getPackname());
        intent.putExtra(EditorActivity.STICKER_URI, uri);
        
        launchEditor.launch(intent);
        overridePendingTransition(R.anim.fade_in, R.anim.no_fade);
    }
    
    final ActivityResultLauncher<Intent> launchEditor = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == EditorActivity.RESULT_STICKER_SAVED) {
                    onStickerSaved(
                            result.getData().getStringExtra(EditorActivity.ADDED_STICKER_URI));
                }
            }
    );
    
    private boolean onDeleteSticker(Uri item) {
        return deleteStickerByUri(item.toString());
    }
    
    private boolean deleteStickerByUri(String uri) {
        // If, by some bug, we're trying to delete a sticker that's not custom,
        // don't.
        if (!pack.getStickerByUri(uri).isCustomized())
            return false;
        
        // If new stickers have been shuffled to the top, we need the sticker's
        // position inside the sticker pack's list
        int pos = pack.getStickerURIs().indexOf(uri);
        
        boolean success = pack.deleteSticker(pos, this);
        
        if (success && model.getShownStickers().size() == 1 && viewer != null)
            // That was the last visible sticker we just deleted
            viewer.updateTransitionImage(null);
        
        return success;
    }
    
    public void onStickerSaved(String newSticker) {
        if (pendingSavedInstanceState != null) {
            pendingSavedInstanceState.putInt(CURRENTLY_SHOWING,
                    pendingSavedInstanceState.getInt(CURRENTLY_SHOWING)+1);
            return;
        }
        
        adapter.setOnBindListener((item, pos, holder) -> {
            if (item.equals(newSticker)) {
                adapter.setOnBindListener(null);
                if (viewer != null) {
                    final List<Uri> uris = new ArrayList<>(urisNoHeaders);
                    viewer.updateImages(uris);
                    viewerOverlay.updateUris(uris);
                    updateViewerOverlay();
                    viewer.setCurrentPosition(viewer.currentPosition() + 1, false);
                }
                // It appears we need a bit more time before the viewer can find the imageView,
                // but I'm not sure just what we're waiting for or how to listen for that happening.
                mainView.postDelayed(() -> viewer.updateTransitionImage(
                        ((StickerPackViewerAdapter.StickerViewHolder) holder).imageView),
                        200);
            }
        });
        // The saved sticker might not still be visible if we were searching by text and
        // that text was removed. So don't let the listener sit there too long.
        mainView.postDelayed(() -> adapter.setOnBindListener(null), 300);
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
    
    private void showImages(List<String> urls) {
        if (urls == null)
            return;
        
        // Ensure swipeRefresh is enabled/disabled when the pack is installed/removed
        setupSwipeRefresh();
        
        if (allPackMode && urls.size() > 0 && isPack(urls.get(0)))
            urls.remove(0);
        
        if (!model.getDownloadSuccess().getValue()) {
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
        
        setUrisNoHeaders(urls);
        adapter.replaceDataSource(urls);
    }
    
    private void setUrisNoHeaders(List<String> items) {
        List<String> noHeaders = removeSpecialItems(items);
        urisNoHeaders.clear();
        urisNoHeaders.ensureCapacity(noHeaders.size());
        for (String item : noHeaders)
            urisNoHeaders.add(Uri.parse(item));
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
    
        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (id == R.id.add_shortcut) {
            if (pack != null)
                Util.pinAppShortcut(pack, this);
            return true;
        } else if (id == R.id.filter) {
            showFilterDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private void showFilterDialog() {
        model.setShowingFilterDialog(true);
        FilterDialog dialog = new FilterDialog(this, model);
        dialog.show();
        dialog.setOnDismissListener((d) -> model.setShowingFilterDialog(false));
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
        getMenuInflater().inflate(R.menu.stickerpack_viewer_menu_items, menu);
        
        MenuItem search = menu.findItem(R.id.search);
        SearchView searchView = (SearchView) search.getActionView();
        
        if (allPackMode) {
            searchView.setQueryHint(getString(R.string.search_installed_hint));
            menu.findItem(R.id.add_shortcut).setVisible(false);
        } else {
            searchView.setQueryHint(getString(R.string.search_hint));
            menu.findItem(R.id.add_shortcut).setVisible((pack.getStatus() == StickerPack.Status.INSTALLED
                                                        || pack.getStatus() == StickerPack.Status.UPDATABLE)
                                                        && Build.VERSION.SDK_INT >= 26);
        }
        
        if (model.isSearching() || allPackMode) {
            search.expandActionView();
            searchView.setQuery(model.getFilterString(), false);
        }
        
        searchView.setOnQueryTextListener(model);
        search.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem menuItem) {
                return model.onMenuItemActionExpand();
            }
    
            @Override
            public boolean onMenuItemActionCollapse(MenuItem menuItem) {
                if (allPackMode) {
                    onBackPressed();
                    return false;
                }
                model.onMenuItemActionCollapse();
                return true;
            }
        });
        
        if (allPackMode && !model.isSearching())
            model.onMenuItemActionExpand();
        
        return super.onCreateOptionsMenu(menu);
    }
    
    @Override
    public void onBackPressed() {
        if (selectionTracker != null && selectionTracker.hasSelection()) {
            selectionTracker.clearSelection();
            return;
        }
        if (model.isFiltering()) {
            model.resetFilters();
            return;
        }
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
        if (manager != null) {
            if (manager.findFirstCompletelyVisibleItemPosition() != 0
                    || !(mainView.findViewHolderForAdapterPosition(0) instanceof StickerPackViewHolder)) {
                ObjectAnimator.ofFloat(transitionView, View.ALPHA, 1f, 0f)
                        .setDuration(getResources().getInteger(R.integer.pack_view_fade_out_duration))
                        .start();
        
                data.putExtra(FADE_PACK_BACK_IN, true);
            } else {
                StickerPackViewHolder topHolder =
                        (StickerPackViewHolder) mainView.findViewHolderForAdapterPosition(0);
                if (topHolder != null) {
                    commonTransitionDetails(false, true);
    
                    // For wide screens, where MainActivity list items don't span the whole screen
                    topHolder.getTopLevelView().setGravity(Gravity.START);
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
            }
        }
        
        setResult(RESULT_CANCELED, data);
        finishAfterTransition();
    }
    
    private void setDarkStatusBarText(boolean dark) {
        if (Build.VERSION.SDK_INT < 23)
            return;
        
        boolean nightMode = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        
        int flags = getWindow().getDecorView().getSystemUiVisibility();
        
        if (dark && !nightMode)
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        else
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }
    
    
    private SelectionTracker<String> setupSelectionTracker() {
        if (picker && !pickerAllowMultiple)
            return null;
        SelectionTracker<String> selectionTracker = new SelectionTracker.Builder<>(
                "mySelection",
                mainView,
                new MyKeyProvider(adapter),
                new MyDetailsLookup(mainView),
                StorageStrategy.createStringStorage()
            ).withSelectionPredicate(new SelectionTracker.SelectionPredicate<String>() {
                @Override
                public boolean canSetStateForKey(@NonNull String key, boolean nextState) {
                    return StickerPackViewerAdapter.isImage(key)
                            && model.getPack() != null
                            && model.getPack().getStickerByUri(key) != null;
                }
                
                @Override
                public boolean canSetStateAtPosition(int position, boolean nextState) {
                    if (position < 0)
                        return false;
                    String key = adapter.getItem(position);
                    return StickerPackViewerAdapter.isImage(key)
                            && model.getPack() != null
                            && model.getPack().getStickerByUri(key) != null;
                }
                
                @Override
                public boolean canSelectMultiple() { return true; }
            })
            .build();
        
        selectionTracker.addObserver(new SelectionTracker.SelectionObserver() {
            public void onSelectionChanged() {
                InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null && getCurrentFocus() != null)
                    imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
                
                if (actionMode != null && selectionTracker.hasSelection())
                    actionMode.invalidate();
                
                if (actionMode == null && selectionTracker.hasSelection()) {
                    actionMode = startSupportActionMode(actionModeCallback);
                    adapter.notifyDataSetChanged();
                }
                
                if (actionMode != null && !selectionTracker.hasSelection()) {
                    actionMode.finish();
                    actionMode = null;
                    adapter.notifyDataSetChanged();
                }
            }
            public void onSelectionRestored() {
                onSelectionChanged();
            }
        });
        return selectionTracker;
    }
    
    
    private class ActionModeCallback implements ActionMode.Callback {
        
        private boolean hasAdjustedPadding = false;
        
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.stickerpack_viewer_action_mode, menu);
            return true;
        }
        
        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            MenuItem deleteItem = menu.findItem(R.id.delete);
            deleteItem.setVisible(true);
            for (String uri : selectionTracker.getSelection()) {
                Sticker sticker = model.getPack() == null
                                    ? null
                                    : model.getPack().getStickerByUri(uri);
                if (sticker == null || !sticker.isCustomized()) {
                    deleteItem.setVisible(false);
                    break;
                }
            }
            
            if (hasAdjustedPadding)
                return false;
            View toolbar = findViewById(R.id.action_mode_bar);
            if (toolbar == null)
                return false;
            final ViewUtils.LayoutData toolbarPadding = ViewUtils.recordLayoutData(toolbar);
            toolbar.setOnApplyWindowInsetsListener((v, windowInsets) -> {
                ViewUtils.updateMarginSides(toolbar,
                        windowInsets.getSystemWindowInsetLeft(),
                        windowInsets.getSystemWindowInsetRight(),
                        toolbarPadding);
                return windowInsets;
            });
            hasAdjustedPadding = true;
            
            return true;
        }
        
        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            int id = item.getItemId();
            if (id == R.id.delete) {
                LightboxOverlayConfirmDeleteFragment confirmDialog =
                        LightboxOverlayConfirmDeleteFragment.newInstance(
                                () -> {},
                                (v) -> {
                                    for (String uri : selectionTracker.getSelection())
                                        deleteStickerByUri(uri);
                                    mode.finish();
                                },
                                false
                        );
                
                confirmDialog.show(getSupportFragmentManager(), "confirm_delete");
                return true;
            
            } else if (id == R.id.send) {
                Selection<String> selection = selectionTracker.getSelection();
                if (selection.size() < 1) {
                    mode.finish();
                    return true;
                }
                ArrayList<Uri> uris = new ArrayList<>(selection.size());
                for (String uri : selection)
                    uris.add(Uri.parse(uri));
                Intent sendIntent = new Intent();
                if (picker) {
                    ContentResolver cr = getContentResolver();
                    String[] mimeTypes = new String[uris.size()];
                    for (int i = 0; i < uris.size(); i++)
                        mimeTypes[i] = cr.getType(uris.get(i));
            
                    ClipData cd = new ClipData(getString(R.string.selected_stickers),
                            mimeTypes,
                            new ClipData.Item(uris.get(0)));
                    for (int i = 1; i < uris.size(); i++)
                        cd.addItem(new ClipData.Item(uris.get(i)));
            
                    sendIntent.setClipData(cd);
                    setResult(RESULT_OK, sendIntent);
                    finish();
                } else {
                    sendIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
                    sendIntent.setType(getContentResolver().getType(uris.get(0)));
                    sendIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
                    sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(
                            Intent.createChooser(sendIntent, getString(R.string.share_text)));
                }
                return true;
            }
            return false;
        }
        
        @Override
        public void onDestroyActionMode(ActionMode mode) {
            if (selectionTracker.hasSelection())
                selectionTracker.clearSelection();
            actionMode = null;
        }
    }
}