package net.samvankooten.finnstickers.sticker_pack_viewer;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.pm.ShortcutManager;
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
import android.view.inputmethod.EditorInfo;

import com.google.android.material.snackbar.Snackbar;
import com.stfalcon.imageviewer.StfalconImageViewer;

import net.samvankooten.finnstickers.LightboxOverlayView;
import net.samvankooten.finnstickers.R;
import net.samvankooten.finnstickers.StickerPack;
import net.samvankooten.finnstickers.StickerPackViewHolder;
import net.samvankooten.finnstickers.editor.EditorActivity;
import net.samvankooten.finnstickers.misc_classes.GlideApp;
import net.samvankooten.finnstickers.misc_classes.GlideRequest;
import net.samvankooten.finnstickers.misc_classes.TransitionListenerAdapter;
import net.samvankooten.finnstickers.utils.ChangeOnlyObserver;
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
    private ArrayList<Uri> urisNoHeaders = new ArrayList<>();
    
    private boolean allPackMode;
    private boolean firstStart;
    private boolean shouldRunDialogDismissListener = true;
    
    private int popupViewerCurrentlyShowing = -1;
    private StfalconImageViewer<Uri> viewer;
    private Bundle pendingSavedInstanceState;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Util.performNeededMigrations(this);
        setContentView(R.layout.activity_sticker_pack_viewer);
        postponeEnterTransition();
        
        setSupportActionBar(findViewById(R.id.toolbar));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        firstStart = savedInstanceState == null;
        pendingSavedInstanceState = savedInstanceState;
        
        allPackMode = getIntent().getBooleanExtra(ALL_PACKS, false);
        boolean picker = getIntent().getBooleanExtra(PICKER, false);
        
        model = ViewModelProviders.of(this).get(StickerPackViewerViewModel.class);
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
        
        setTitle(allPackMode ? getString(R.string.sticker_pack_viewer_toolbar_title_all_packs) : "");
        
        setDarkStatusBarText(true);
        
        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setOnRefreshListener(this::refresh);
        swipeRefresh.setColorSchemeResources(R.color.colorAccent);
        
        mainView = findViewById(R.id.main_view);
        
        model.getDownloadException().observe(this, this::showDownloadException);
        model.getUris().observe(this, this::showDownloadedImages);
        model.getDownloadRunning().observe(this, this::showProgress);
        model.getLiveIsSearching().observe(this, v -> setupSwipeRefresh());
        
        DisplayMetrics displayMetrics = this.getResources().getDisplayMetrics();
        float targetSize = getResources().getDimension(R.dimen.sticker_pack_viewer_target_image_size);
        int nColumns = (int) (displayMetrics.widthPixels / targetSize + 0.5); // +0.5 for correct rounding to int.
        
        GridLayoutManager layoutManager = new GridLayoutManager(this, nColumns);
        mainView.setLayoutManager(layoutManager);
        
        List<String> starterList;
        if (model.getUris().getValue() != null) {
            starterList = model.getUris().getValue();
            setUrisNoHeaders(starterList);
        } else if (allPackMode || model.getPack() == null)
            starterList = null;
        else
            starterList = Collections.singletonList(PACK_CODE);
        
        adapter = new StickerPackViewerAdapter(starterList, this);
        if (model.getPack() != null)
            adapter.setPack(model.getPack());
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
                    startLightBox(adapter, holder, Uri.parse(uri))
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
                    mainView.postDelayed(() -> viewer.updateTransitionImage(
                            ((StickerPackViewerAdapter.StickerViewHolder) mainView.findViewHolderForAdapterPosition(adapterPos)).imageView),
                            200);
                }
            }
        });
    }
    
    private void packRelatedSetup(StickerPack pack) {
        if (pack == null || this.pack != null)
            return;
        
        this.pack = pack;
        
        if (adapter != null)
            adapter.setPack(pack);
        
        if (firstStart && !allPackMode && Build.VERSION.SDK_INT >= 25)
            getSystemService(ShortcutManager.class).reportShortcutUsed(pack.getPackname());
        
        pack.getLiveStatus().observe(this, new ChangeOnlyObserver<>(
                status -> onPackStatusChange()));
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
                if (pack != null)
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
        if (pack == null
                || pack.getStatus() != StickerPack.Status.UNINSTALLED
                && pack.getStatus() != StickerPack.Status.UPDATABLE)
            swipeRefresh.setEnabled(false);
        else if (model.isSearching())
            swipeRefresh.setEnabled(false);
        else
            swipeRefresh.setEnabled(true);
    }
    
    private void onPackStatusChange() {
        refresh();
    }
    
    private void refresh() {
        model.refreshData();
    }
    
    private void startLightBox(StickerPackViewerAdapter adapter,
                               StickerPackViewerAdapter.StickerViewHolder holder,
                               Uri uri) {
        if (urisNoHeaders == null || urisNoHeaders.size() == 0)
            return;
        int position = urisNoHeaders.indexOf(uri);
        LightboxOverlayView overlay = new LightboxOverlayView(
                this, urisNoHeaders, position, false);
        
        overlay.setGetTransitionImageCallback(pos -> {
            Uri item = urisNoHeaders.get(pos);
            pos = adapter.getPosOfItem(item.toString());
            StickerPackViewerAdapter.StickerViewHolder vh = (StickerPackViewerAdapter.StickerViewHolder) mainView.findViewHolderForAdapterPosition(pos);
            return (vh == null) ? null : vh.imageView;
        });
        
        viewer = new StfalconImageViewer.Builder<>(this, urisNoHeaders,
                (v, src) -> {
                    GlideRequest request = GlideApp.with(this).load(src);
                    
                    Util.enableGlideCacheIfRemote(request, src.toString(), pack.getVersion());
                    
                    request.into(v);
                })
                .withStartPosition(position)
                .withOverlayView(overlay)
                .withImageChangeListener(pos -> {
                    overlay.setPos(pos);
                    popupViewerCurrentlyShowing = pos;
                })
                .withHiddenStatusBar(false)
                .withTransitionFrom(holder == null ? null : holder.imageView)
                .withDismissListener(() -> {
                    if (shouldRunDialogDismissListener) {
                        setDarkStatusBarText(true);
                        popupViewerCurrentlyShowing = -1;
                        viewer = null;
                    } else
                        shouldRunDialogDismissListener = true;})
                .show(popupViewerCurrentlyShowing < 0);
        
        overlay.setViewer(viewer);
        overlay.setAreDeletable(model.getAreDeletable());
        overlay.setAreEditable(model.getAreEditable());
        overlay.setOnEditCallback(this::startEditing);
        overlay.setOnDeleteCallback(this::onDeleteSticker);
        setDarkStatusBarText(false);
        popupViewerCurrentlyShowing = position;
    }
    
    private void startEditing(int pos) {
        Intent intent = new Intent(this, EditorActivity.class);
        intent.putExtra(EditorActivity.PACK_NAME, pack.getPackname());
        // If new stickers have been shuffled to the top, we need the sticker's
        // position inside the sticker pack's list
        intent.putExtra(EditorActivity.STICKER_POSITION,
                pack.getStickerURIs().indexOf(urisNoHeaders.get(pos).toString()));
        
        startActivityForResult(intent, 157);
        overridePendingTransition(R.anim.fade_in, R.anim.no_fade);
    }
    
    private boolean onDeleteSticker(int pos) {
        // If new stickers have been shuffled to the top, we need the sticker's
        // position inside the sticker pack's list
        pos = pack.getStickerURIs().indexOf(urisNoHeaders.get(pos).toString());
        
        return pack.deleteSticker(pos, this);
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 157 && resultCode == EditorActivity.RESULT_STICKER_SAVED) {
            if (pendingSavedInstanceState != null) {
                pendingSavedInstanceState.putInt(CURRENTLY_SHOWING,
                        pendingSavedInstanceState.getInt(CURRENTLY_SHOWING)+1);
                return;
            }
            final String uri = data.getStringExtra(EditorActivity.ADDED_STICKER_URI);
            adapter.setOnBindListener((item, pos, holder) -> {
                if (item.equals(uri)) {
                    adapter.setOnBindListener(null);
                    StfalconImageViewer oldViewer = viewer;
                    startLightBox(adapter,
                            (StickerPackViewerAdapter.StickerViewHolder)
                                    holder,
                            Uri.parse(uri)
                    );
                    if (oldViewer != null) {
                        shouldRunDialogDismissListener = false;
                        oldViewer.dismiss();
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
            mainView.postDelayed(() -> adapter.setOnBindListener(null), 200);
        }
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
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.add_shortcut:
                if (pack != null)
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
                || allPackMode) {
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
        if (manager == null) {
            // Nothing to do
        } else if (manager.findFirstCompletelyVisibleItemPosition() != 0) {
            ObjectAnimator.ofFloat(findViewById(R.id.transition), View.ALPHA, 1f, 0f)
                    .setDuration(getResources().getInteger(R.integer.pack_view_fade_out_duration))
                    .start();
            
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
