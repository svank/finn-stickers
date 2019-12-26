package net.samvankooten.finnstickers.sticker_pack_viewer;

import android.app.Application;
import android.content.Context;
import android.os.Handler;

import net.samvankooten.finnstickers.CompositeStickerPack;
import net.samvankooten.finnstickers.Constants;
import net.samvankooten.finnstickers.R;
import net.samvankooten.finnstickers.Sticker;
import net.samvankooten.finnstickers.StickerPack;
import net.samvankooten.finnstickers.updating.UpdateUtils;
import net.samvankooten.finnstickers.utils.DownloadCallback;
import net.samvankooten.finnstickers.utils.StickerPackRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import androidx.appcompat.widget.SearchView;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import static net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerAdapter.CENTERED_TEXT_PREFIX;
import static net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerAdapter.DIVIDER_CODE;
import static net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerAdapter.HEADER_PREFIX;
import static net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerAdapter.PACK_CODE;
import static net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerAdapter.TEXT_PREFIX;
import static net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerAdapter.removeSpecialItems;

public class StickerPackViewerViewModel extends AndroidViewModel
                                        implements DownloadCallback<StickerPackViewerDownloadTask.Result>,
                                                   SearchView.OnQueryTextListener {
    private static final String TAG = "StickerPackVwrViewModel";
    
    private List<Sticker> searchableStickers;
    private List<Sticker> stickersToSearch;
    private List<String> originalUris;
    private final Handler handler = new Handler();
    private boolean filterTaskQueued = false;
    private boolean allPacksMode = false;
    private boolean allowDownloadRunningExemption = false;
    
    private final MutableLiveData<List<String>> uris = new MutableLiveData<>();
    private final MutableLiveData<Boolean> downloadSuccess = new MutableLiveData<>();
    private final MutableLiveData<Exception> downloadException = new MutableLiveData<>();
    private final MutableLiveData<Boolean> downloadRunning = new MutableLiveData<>();
    private final MutableLiveData<StickerPack> pack = new MutableLiveData<>();
    private List<Boolean> areDeletable = new ArrayList<>();
    private List<Boolean> areEditable = new ArrayList<>();
    
    private final Context context;
    
    private String filterString = "";
    private boolean showGifs = true;
    private boolean showStills = true;
    private boolean showEdited = true;
    private boolean showUnedited = true;
    private List<String> packsToShow;
    
    private boolean showingFilterDialog = false;
    private boolean isSearching = false;
    private boolean isFiltering = false;
    
    private StickerPackViewerDownloadTask.Result cachedRemoteResult;
    
    public StickerPackViewerViewModel(Application application) {
        super(application);
        context = application;
        
        downloadRunning.setValue(false);
        downloadException.setValue(null);
        downloadSuccess.setValue(true);
    }
    
    void setAllPacks() {
        allPacksMode = true;
        pack.setValue(StickerPackRepository.getInstalledStickersAsOnePack(context));
        packsToShow = ((CompositeStickerPack) pack.getValue()).getPackNames();
        refreshData();
    }
    
    void setPack(String packName) {
        allPacksMode = false;
        StickerPack cachedPack =
                StickerPackRepository.getInstalledOrCachedPackByName(packName, context);
        if (cachedPack != null) {
            pack.setValue(cachedPack);
            refreshData();
        } else {
            downloadRunning.setValue(true);
            new Thread(() -> {
                try {
                    StickerPack loadedPack =
                            StickerPackRepository.getInstalledOrRemotePackByName(packName, context);
                    handler.post(() -> {
                        pack.setValue(loadedPack);
                        uris.setValue(Collections.singletonList(PACK_CODE));
                        refreshData(false, false);
                    });
                } catch (Exception e) {
                    downloadException.postValue(e);
                }
            }).start();
        }
    }
    
    void refreshData() {
        refreshData(true, false);
    }
    
    void refreshLocalData() {
        refreshData(true, true);
    }
    
    private void refreshData(boolean requireNoDownloadRunning, boolean localOnly) {
        if (allowDownloadRunningExemption) {
            allowDownloadRunningExemption = false;
            downloadRunning.setValue(false);
        }
        if ((requireNoDownloadRunning && downloadRunning.getValue())
                || getPack() == null)
            return;
        
        switch(getPack().getStatus()) {
            case INSTALLED:
                searchableStickers = getPack().getStickers();
                downloadSuccess.setValue(true);
                downloadException.setValue(null);
                onStickersUpdated(formatCurrentUris());
                return;
                
            case UNINSTALLED:
                if (localOnly) {
                    updateFromDownload(cachedRemoteResult, context);
                } else {
                    downloadRunning.setValue(true);
                    new StickerPackViewerDownloadTask(this, getPack(), context).execute();
                }
                return;
                
            case UPDATABLE:
                if (localOnly) {
                    updateFromDownload(cachedRemoteResult, context);
                } else {
                    downloadRunning.setValue(true);
                    new StickerPackViewerDownloadTask(this, getPack().getRemoteVersion(), context).execute();
                }
                return;
                
            case INSTALLING:
                // If the activity was opened _while_ the pack was installing
                if (uris.getValue() == null) {
                    allowDownloadRunningExemption = true;
                    downloadRunning.setValue(true);
                }
                return;
        }
    }
    
    @Override
    public void finishDownloading() {
        downloadRunning.setValue(false);
    }
    
    @Override
    public void updateFromDownload(StickerPackViewerDownloadTask.Result result, Context context) {
        downloadSuccess.setValue(result.networkSucceeded);
        downloadException.setValue(result.exception);
        
        if (getPack().getStatus() == StickerPack.Status.UNINSTALLED) {
            if (result.urls != null) {
                searchableStickers = result.stickers;
                if (result.urls.get(0) != PACK_CODE) {
                    result.urls.add(0, TEXT_PREFIX + context.getString(R.string.uninstalled_stickers_warning));
                    result.urls.add(0, PACK_CODE);
                }
                onStickersUpdated(result.urls);
            } else if (uris.getValue() == null || uris.getValue().size() <= 1) {
                searchableStickers = new LinkedList<>();
                onStickersUpdated(Collections.singletonList(PACK_CODE));
            }
        }
        
        if (getPack().getStatus() == StickerPack.Status.UPDATABLE) {
            searchableStickers = getPack().getStickers();
            if (result.urls == null)
                onStickersUpdated(getPack().getStickerURIs());
            else {
                List<String> newStickers = findUpdateAvailableUris(result);
                onStickersUpdated(formatUpdateAvailableUris(formatCurrentUris(), newStickers));
            }
        }
        
        cachedRemoteResult = result;
    }
    
    private void onStickersUpdated(List<String> formattedUris) {
        originalUris = formattedUris;
        if (!isShowingAllStickers()) {
            stickersToSearch = searchableStickers;
            filterUris();
        } else
            uris.setValue(formattedUris);
        
        updateDeletableEditable();
    }
    
    private void updateDeletableEditable() {
        List<Sticker> stickers;
        if (!isShowingAllStickers())
            stickers = new LinkedList<>(stickersToSearch);
        else {
            stickers = new LinkedList<>(searchableStickers);
            // Recently-added stickers will appear at the top of the screen.
            // We need to replicate that order change in our deletable and
            // editable lists. So here we'll go through the sticker list, and
            // and stickers in the updated list will be moved to the front of
            // the sticker list
            List<String> updatedUris = pack.getValue().getUpdatedURIs();
            int nMoved = 0;
            for (int i = 0; i < stickers.size(); i++) {
                String uri = stickers.get(i).getURI().toString();
                if (updatedUris.indexOf(uri) >= 0) {
                    Sticker sticker = stickers.get(i);
                    stickers.remove(i);
                    stickers.add(nMoved, sticker);
                    nMoved++;
                }
            }
        }
    
        List<Boolean> editable = new ArrayList<>(stickers.size());
        List<Boolean> deletable = new ArrayList<>(stickers.size());
        
        if (isShowingAllStickers()) {
            // If an update is available that adds new stickers, those new stickers
            // will be at the beginning of uris but not present in searchableStickers
            int nRemote = removeSpecialItems(uris.getValue()).size() - stickers.size();
            for (int i = 0; i < nRemote; i++) {
                editable.add(false);
                deletable.add(false);
            }
        }
    
        for (int i=0; i<stickers.size(); i++) {
            if (getPack().getStatus() == StickerPack.Status.UNINSTALLED) {
                editable.add(false);
                deletable.add(false);
                continue;
            } else {
                editable.add(true);
            }
            if (stickers.get(i).isCustomized())
                deletable.add(true);
            else
                deletable.add(false);
        }
    
        areDeletable = deletable;
        areEditable = editable;
    }
    
    /**
     * Generates a List of Uris for currently-installed stickers. If appropriate, recently-added
     * stickers are pulled to the top and highlighted.
     */
    private List<String> formatCurrentUris() {
        List<String> uris;
        if (getPack().wasUpdatedRecently())
            uris = formatUpdatedUris(getPack().getStickerURIs(), getPack().getUpdatedURIs());
        else
            uris = getPack().getStickerURIs();
        
        uris.add(0, PACK_CODE);
        return uris;
    }
    
    /**
     * Given a Result containing info from the server about a StickerPack, this compares to the
     * currently-installed pack to determine which stickers will be added if the pack is updated.
     * @return Uris for the stickers that would be added
    */
    private List<String> findUpdateAvailableUris(StickerPackViewerDownloadTask.Result result) {
        List<String> currentStickers = new ArrayList<>(getPack().getStickerRelativePaths());
        List<String> availableStickers = new ArrayList<>(result.urls);
        
        // Strip all but the sticker's location within the path dir
        String base = Constants.URL_BASE + getPack().getPackname();
        for (int i=0; i < availableStickers.size(); i++) {
            String val = availableStickers.get(i);
            val = val.substring(base.length());
            availableStickers.set(i, val);
        }
        
        List<String> newStickers = UpdateUtils.findNewUris(
                currentStickers,
                availableStickers);
        
        for (int i=0; i < newStickers.size(); i++) {
            String val = newStickers.get(i);
            val = base + val;
            newStickers.set(i, val);
        }
        return newStickers;
    }
    
    /**
     * Takes currently-installed uris and those to be added if the pack is updated, and orders
     * and formats them for StickerPackViewer's RecyclerView
     */
    private List<String> formatUpdateAvailableUris(List<String> installedUris, List<String> availableUris) {
        if (availableUris.size() == 0)
            return installedUris;
        
        installedUris.remove(PACK_CODE);
        
        List<String> output = new ArrayList<>(installedUris.size() + availableUris.size() + 2);
        
        output.add(PACK_CODE);
        output.add(HEADER_PREFIX + context.getResources().getQuantityString(
                R.plurals.stickers_available_in_update, availableUris.size(), availableUris.size()));
        output.addAll(availableUris);
        output.add(DIVIDER_CODE);
        output.addAll(installedUris);
        
        return output;
    }
    
    /**
     * Given a list of all currently-installed sticker uris, and those added in a recent update,
     * pulls the new ones to the front and adds formatting for StickerPackViewer's RecyclerView
     */
    private List<String> formatUpdatedUris(List<String> allUris, List<String> updatedUris) {
        int nNewStickers = updatedUris.size();
        List<String> output = new LinkedList<>();
        
        // Make copy to mutate
        allUris = new LinkedList<>(allUris);
        
        for (String uri : updatedUris)
            allUris.remove(uri);
        
        output.add(HEADER_PREFIX + context.getResources().getQuantityString(
                R.plurals.new_stickers_in_update, nNewStickers, nNewStickers));
        output.addAll(updatedUris);
        output.add(DIVIDER_CODE);
        
        output.addAll(allUris);
        return output;
    }
    
    @Override
    public boolean onQueryTextChange(String newText) {
        return onQueryTextChange(newText, 200);
    }
    
    private boolean onQueryTextChange(String newText, int delay) {
        if (isSearching() && searchableStickers != null) {
            newText = newText.toLowerCase();
            // As the search string is typed in, we cache the filtered list of stickers so that
            // future filters don't have to keep removing stickers that we already know don't
            // match the query string. But if newText isn't just an addition to filterString,
            // invalidate that cache and re-search all stickers.
            if (!newText.startsWith(filterString))
                stickersToSearch = searchableStickers;
            
            filterString = newText;
            
            // If we're updating the adapter every time a new character is typed, and if the user is
            // typing fast, the animation can be a little funky. So we rate-limit adapter updates.
            scheduleFilter(delay);
        }
        return true;
    }
    
    private void scheduleFilter(int delay) {
        if (!filterTaskQueued) {
            filterTaskQueued = true;
            handler.postDelayed(() -> {
                if (filterTaskQueued) {
                    filterTaskQueued = false;
                    filterUris();
                }
            }, delay);
        }
    }
    
    @Override
    public boolean onQueryTextSubmit(String query) {
        return true;
    }
    
    public void onMenuItemActionCollapse() {
        // The search bar was closed
        filterString = "";
        isSearching = false;
        stickersToSearch = searchableStickers;
        filterTaskQueued = false;
        filterUris();
    }
    
    public boolean onMenuItemActionExpand() {
        // The search bar was opened
        if (searchableStickers == null || downloadRunning.getValue())
            return false;
        
        if (isSearching())
            return true;
        
        isSearching = true;
        stickersToSearch = searchableStickers;
        onQueryTextChange("", 0);
        
        return true;
    }
    
    private void filterUris() {
        if (stickersToSearch == null)
            return;
        
        if (isShowingAllStickers()) {
            uris.setValue(originalUris);
            updateDeletableEditable();
            return;
        }
        
        String[] searchTerms = filterString.split(" ");
        List<Sticker> selectedStickers = new ArrayList<>(stickersToSearch.size());
        
        for (int i=0; i<stickersToSearch.size(); i++) {
            Sticker sticker = stickersToSearch.get(i);
            boolean add = true;
            
            for (String searchTerm : searchTerms) {
                add = false;
                for (String keyword: sticker.getKeywords()) {
                    if (keyword.toLowerCase().startsWith(searchTerm)) {
                        add = true;
                        break;
                    }
                }
                if (!add) break;
            }
            
            if (!sticker.isCustomized() && !showUnedited)
                add = false;
            if (sticker.isCustomized() && !showEdited)
                add = false;
            boolean isGif = sticker.getRelativePath().toLowerCase().endsWith(".gif");
            if (isGif && !showGifs)
                add = false;
            if (!isGif && !showStills)
                add = false;
            
            if (packsToShow != null && packsToShow.indexOf(sticker.getPackname()) < 0)
                add = false;
            
            if (add)
                selectedStickers.add(sticker);
        }
        
        List<String> uris = new ArrayList<>(selectedStickers.size());
        for (Sticker sticker : selectedStickers)
            uris.add(sticker.getCurrentLocation());
        
        stickersToSearch = selectedStickers;
        
        if (uris.size() == 0)
            uris.add(CENTERED_TEXT_PREFIX + context.getString(R.string.sticker_pack_viewer_no_matches));
        if (isFiltering())
            uris.add(0, CENTERED_TEXT_PREFIX + buildFilterActiveString());
        if (getPack().getStatus() == StickerPack.Status.UNINSTALLED)
            uris.add(0, TEXT_PREFIX + context.getString(R.string.uninstalled_stickers_warning));
        
        
        this.uris.setValue(uris);
        updateDeletableEditable();
    }
    
    private String buildFilterActiveString() {
        StringBuilder builder = new StringBuilder();
        builder.append(context.getResources().getString(R.string.filter_active_prefix));
        List<String> thingsHidden = new LinkedList<>();
        if (!showStills)
            thingsHidden.add(context.getResources().getString(R.string.filter_active_stills));
        if (!showGifs)
            thingsHidden.add(context.getResources().getString(R.string.filter_active_gifs));
        if (!showEdited)
            thingsHidden.add(context.getResources().getString(R.string.filter_active_edited));
        if (!showUnedited)
            thingsHidden.add(context.getResources().getString(R.string.filter_active_unedited));
        if (isInAllPacksMode() && getPack() instanceof CompositeStickerPack) {
            List<String> packNames = ((CompositeStickerPack) getPack()).getPackNames();
            for (String name : packNames) {
                if (packsToShow.indexOf(name) < 0)
                    thingsHidden.add(String.format(context.getResources().getString(R.string.filter_active_pack), name));
            }
        }
        
        for (int i=0; i<thingsHidden.size(); i++) {
            builder.append(thingsHidden.get(i));
            int distFromEnd = thingsHidden.size() - 1 - i;
            if (distFromEnd == 1)
                builder.append(context.getResources().getString(R.string.filter_active_final_joiner));
            else if (distFromEnd > 1)
                builder.append(context.getResources().getString(R.string.filter_active_joiner));
        }
        
        builder.append(context.getResources().getString(R.string.filter_active_suffix));
        
        return builder.toString();
    }
    
    public LiveData<List<String>> getUris() {
        return uris;
    }
    
    public LiveData<Boolean> getDownloadSuccess() {
        return downloadSuccess;
    }
    
    public LiveData<Exception> getDownloadException() {
        return downloadException;
    }
    
    public LiveData<Boolean> getDownloadRunning() {
        return downloadRunning;
    }
    
    public LiveData<StickerPack> getLivePack() {
        return pack;
    }
    
    public List<Boolean> getAreEditable() {
        return new ArrayList<>(areEditable);
    }
    
    public List<Boolean> getAreDeletable() {
        return new ArrayList<>(areDeletable);
    }
    
    public String getFilterString() {
        return filterString;
    }
    
    public StickerPack getPack() {
        return pack.getValue();
    }
    
    public List<Sticker> getShownStickers() {
        if (!isShowingAllStickers())
            return stickersToSearch;
        return searchableStickers;
    }
    
    public boolean isSearching() {
        return isSearching;
    }
    
    public boolean isFiltering() {
        return isFiltering;
    }
    
    public boolean isShowingAllStickers() {
        return !isFiltering() && !isSearching();
    }
    
    private void updateIsFiltering() {
        isFiltering = !showGifs
                   || !showStills
                   || !showEdited
                   || !showUnedited
                   || (allPacksMode && getPack() instanceof CompositeStickerPack
                       && packsToShow.size() != ((CompositeStickerPack) getPack()).getPackNames().size());
    }
    
    public void clearException() {
        downloadException.setValue(null);
    }
    
    public boolean isInitialized() {
        return getPack() != null;
    }
    
    public boolean getShowGifs() {
        return showGifs;
    }
    
    public void setShowGifs(boolean showGifs) {
        this.showGifs = showGifs;
        stickersToSearch = searchableStickers;
        updateIsFiltering();
        scheduleFilter(20);
    }
    
    public boolean getShowStills() {
        return showStills;
    }
    
    public void setShowStills(boolean showStills) {
        this.showStills = showStills;
        stickersToSearch = searchableStickers;
        updateIsFiltering();
        scheduleFilter(20);
    }
    
    public boolean getShowEdited() {
        return showEdited;
    }
    
    public void setShowEdited(boolean showEdited) {
        this.showEdited = showEdited;
        stickersToSearch = searchableStickers;
        updateIsFiltering();
        scheduleFilter(20);
    }
    
    public boolean getShowUnedited() {
        return showUnedited;
    }
    
    public void setShowUnedited(boolean showUnedited) {
        this.showUnedited = showUnedited;
        stickersToSearch = searchableStickers;
        updateIsFiltering();
        scheduleFilter(20);
    }
    
    public void setShowPack(String packName, boolean show) {
        if (packsToShow == null)
            return;
        
        if (show && packsToShow.indexOf(packName) < 0)
            packsToShow.add(packName);
        else if (!show)
            packsToShow.remove(packName);
    
        stickersToSearch = searchableStickers;
        updateIsFiltering();
        scheduleFilter(20);
    }
    
    public void setShowPacks(List<String> packNames, boolean show) {
        for (String name : packNames)
            setShowPack(name, show);
    }
    
    public void resetFilters() {
        setShowGifs(true);
        setShowStills(true);
        setShowEdited(true);
        setShowUnedited(true);
    
        if (isInAllPacksMode())
            setShowPacks(((CompositeStickerPack) getPack()).getPackNames(), true);
    }
    
    public List<String> getShownPacks() {
        return new ArrayList<>(packsToShow);
    }
    
    public boolean isInAllPacksMode() {
        return allPacksMode;
    }
    
    public boolean isShowingFilterDialog() {
        return showingFilterDialog;
    }
    
    public void setShowingFilterDialog(boolean showingFilterDialog) {
        this.showingFilterDialog = showingFilterDialog;
    }
}