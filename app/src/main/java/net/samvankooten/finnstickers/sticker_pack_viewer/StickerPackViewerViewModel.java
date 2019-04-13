package net.samvankooten.finnstickers.sticker_pack_viewer;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.view.MenuItem;

import net.samvankooten.finnstickers.R;
import net.samvankooten.finnstickers.Sticker;
import net.samvankooten.finnstickers.StickerPack;
import net.samvankooten.finnstickers.updating.UpdateUtils;
import net.samvankooten.finnstickers.utils.DownloadCallback;
import net.samvankooten.finnstickers.utils.StickerPackRepository;
import net.samvankooten.finnstickers.utils.Util;

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

public class StickerPackViewerViewModel extends AndroidViewModel
                                        implements DownloadCallback<StickerPackViewerDownloadTask.Result>,
                                                   SearchView.OnQueryTextListener,
                                                   MenuItem.OnActionExpandListener {
    private static final String TAG = "StickerPackVwrViewModel";
    
    private List<Sticker> searchableStickers;
    private List<Sticker> stickersToSearch;
    private List<String> originalUris;
    private final Handler handler = new Handler();
    private boolean filterTaskQueued = false;
    
    private final MutableLiveData<List<String>> uris = new MutableLiveData<>();
    private final MutableLiveData<Boolean> downloadSuccess = new MutableLiveData<>();
    private final MutableLiveData<Exception> downloadException = new MutableLiveData<>();
    private final MutableLiveData<Boolean> downloadRunning = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isSearching = new MutableLiveData<>();
    private final MutableLiveData<StickerPack> pack = new MutableLiveData<>();
    
    private final Application context;
    private String filterString = "";
    
    public StickerPackViewerViewModel(Application application) {
        super(application);
        context = application;
        
        downloadRunning.setValue(false);
        isSearching.setValue(false);
        downloadException.setValue(null);
        downloadSuccess.setValue(true);
        
    }
    
    void setAllPacks() {
        pack.setValue(StickerPackRepository.getInstalledStickersAsOnePack(context));
        refreshData();
    }
    
    void setPack(String packName) {
        StickerPack cachedPack =
                StickerPackRepository.getInstalledOrCachedPackByName(packName, context);
        if (cachedPack != null)
            pack.setValue(cachedPack);
        else {
            downloadRunning.setValue(true);
            new Thread(() -> {
                try {
                    StickerPack loadedPack =
                            StickerPackRepository.getInstalledOrRemotePackByName(packName, context);
                    handler.post(() -> {
                        pack.setValue(loadedPack);
                        uris.setValue(Collections.singletonList(PACK_CODE));
                        refreshData(false);
                    });
                } catch (Exception e) {
                    downloadException.postValue(e);
                }
            }).start();
        }
    }
    
    void refreshData() {
        refreshData(true);
    }
    
    private void refreshData(boolean requireNoDownloadRunning) {
        if ((requireNoDownloadRunning && downloadRunning.getValue())
                || getPack() == null)
            return;
        
        switch(getPack().getStatus()) {
            case INSTALLED:
                uris.setValue(formatCurrentUris());
                searchableStickers = getPack().getStickers();
                downloadSuccess.setValue(true);
                downloadException.setValue(null);
                return;
                
            case UNINSTALLED:
                downloadRunning.setValue(true);
                new StickerPackViewerDownloadTask(this, getPack(), context).execute();
                return;
                
            case UPDATEABLE:
                downloadRunning.setValue(true);
                new StickerPackViewerDownloadTask(this, getPack().getRemoteVersion(), context).execute();
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
                result.urls.add(0, TEXT_PREFIX + context.getString(R.string.uninstalled_stickers_warning));
                result.urls.add(0, PACK_CODE);
                uris.setValue(result.urls);
            } else
                uris.setValue(Collections.singletonList(PACK_CODE));
        }
        
        if (getPack().getStatus() == StickerPack.Status.UPDATEABLE) {
            if (result.urls == null)
                uris.setValue(getPack().getStickerURIs());
            else {
                List<String> newStickers = findUpdateAvailableUris(result);
                uris.setValue(formatUpdateAvailableUris(formatCurrentUris(), newStickers));
            }
            searchableStickers = getPack().getStickers();
        }
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
        String base = Util.URL_BASE + getPack().getPackname();
        for (int i=0; i < availableStickers.size(); i++) {
            String val = availableStickers.get(i);
            val = val.substring(base.length());
            availableStickers.set(i, val);
        }
        
        List<String> newStickers = UpdateUtils.findNewStickers(
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
            // As the search string is typed in, we cache the filtered list of stickers so that
            // future filters don't have to keep removing stickers that we already know don't
            // match the query string. But if newText isn't just an addition to filterString,
            // invalidate that cache and re-search all stickers.
            if (!newText.startsWith(filterString))
                stickersToSearch = searchableStickers;
            
            filterString = newText;
            
            // If we're updating the adapter every time a new character is typed, and if the user is
            // typing fast, the animation can be a little funky. So we rate-limit adapter updates.
            if (!filterTaskQueued) {
                filterTaskQueued = true;
                handler.postDelayed(() -> {
                    filterTaskQueued = false;
                    filterUris();
                }, delay);
            }
        }
        return true;
    }
    
    @Override
    public boolean onQueryTextSubmit(String query) {
        return true;
    }
    
    @Override
    public boolean onMenuItemActionCollapse(MenuItem item) {
        isSearching.setValue(false);
        this.uris.setValue(originalUris);
        originalUris = null;
        return true;
    }
    
    @Override
    public boolean onMenuItemActionExpand(MenuItem item) {
        if (searchableStickers == null || downloadRunning.getValue())
            return false;
        
        if (isSearching())
            return true;
        
        isSearching.setValue(true);
        originalUris = uris.getValue();
        stickersToSearch = new LinkedList<>(searchableStickers);
        onQueryTextChange("", 0);
        return true;
    }
    
    public void startSearching() {
        onMenuItemActionExpand(null);
    }
    
    private void filterUris() {
        if (filterString.length() == 0) {
            List<String> uris = new ArrayList<>(stickersToSearch.size());
            for (Sticker sticker : stickersToSearch)
                uris.add(sticker.getCurrentLocation());
            this.uris.setValue(uris);
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
                    if (keyword.startsWith(searchTerm)) {
                        add = true;
                        break;
                    }
                }
                if (!add) break;
            }
            
            if (add)
                selectedStickers.add(sticker);
        }
    
        List<String> uris = new ArrayList<>(selectedStickers.size());
        for (Sticker sticker : selectedStickers)
            uris.add(sticker.getCurrentLocation());
        
        stickersToSearch = selectedStickers;
        
        if (uris.size() == 0)
            uris.add(CENTERED_TEXT_PREFIX + context.getString(R.string.sticker_pack_viewer_no_matches));
        
        this.uris.setValue(uris);
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
    
    public String getFilterString() {
        return filterString;
    }
    
    public StickerPack getPack() {
        return pack.getValue();
    }
    
    public boolean isSearching() {
        return isSearching.getValue();
    }
    
    public LiveData<Boolean> getLiveIsSearching() {
        return isSearching;
    }
    
    public void clearException() {
        downloadException.setValue(null);
    }
    
    public boolean isInitialized() {
        return getPack() != null;
    }
}