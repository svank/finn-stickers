package net.samvankooten.finnstickers.sticker_pack_viewer;

import android.app.Application;
import android.content.Context;

import net.samvankooten.finnstickers.R;
import net.samvankooten.finnstickers.Sticker;
import net.samvankooten.finnstickers.StickerPack;
import net.samvankooten.finnstickers.updating.UpdateUtils;
import net.samvankooten.finnstickers.utils.DownloadCallback;
import net.samvankooten.finnstickers.utils.Util;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import static net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerAdapter.DIVIDER_CODE;
import static net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerAdapter.HEADER_PREFIX;
import static net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerAdapter.TEXT_PREFIX;

public class StickerPackViewerViewModel extends AndroidViewModel implements DownloadCallback<StickerPackViewerDownloadTask.Result> {
    private static final String TAG = "StickerPackVwrViewModel";
    
    private final MutableLiveData<List<String>> uris = new MutableLiveData<>();
    private final MutableLiveData<Boolean> downloadSuccess = new MutableLiveData<>();
    private final MutableLiveData<Exception> downloadException = new MutableLiveData<>();
    private final MutableLiveData<Boolean> downloadRunning = new MutableLiveData<>();
    private final Application context;
    private StickerPack pack;
    
    public StickerPackViewerViewModel(Application application) {
        super(application);
        this.context = application;
        downloadRunning.setValue(false);
    }
    
    void setPack(StickerPack pack) {
        if (this.pack == null)
            this.pack = pack;
    }
    
    void refreshData() {
        if (downloadRunning.getValue())
            return;
        
        switch(pack.getStatus()) {
            case INSTALLED:
                uris.setValue(formatCurrentUris());
                return;
                
            case UNINSTALLED:
            case UPDATEABLE:
                downloadRunning.setValue(true);
                new StickerPackViewerDownloadTask(this, pack, context).execute();
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
        
        if (pack.getStatus() == StickerPack.Status.UNINSTALLED) {
            if (result.urls != null) {
                result.urls.add(0, TEXT_PREFIX + context.getString(R.string.uninstalled_stickers_warning));
                uris.setValue(result.urls);
            }
        }
        
        if (pack.getStatus() == StickerPack.Status.UPDATEABLE) {
            if (result.urls == null)
                uris.setValue(pack.getReplaces().getStickerURIs());
            else {
                List<String> newStickers = findUpdateAvailableUris(result);
                uris.setValue(formatUpdateAvailableUris(formatCurrentUris(), newStickers));
            }
        }
    }
    
    /**
     * Generates a List of Uris for currently-installed stickers. If appropriate, recently-added
     * stickers are pulled to the top and highlighted.
     */
    private List<String> formatCurrentUris() {
        StickerPack targetPack = pack;
        if (pack.getStatus() == StickerPack.Status.UPDATEABLE)
            targetPack = pack.getReplaces();
        
        if (targetPack.wasUpdatedRecently())
            return formatUpdatedUris(targetPack.getStickerURIs(), targetPack.getUpdatedURIs());
        else
            return targetPack.getStickerURIs();
    }
    
    /**
     * Given a Result containing info from the server about a StickerPack, this compares to the
     * currently-installed pack to determine which stickers will be added if the pack is updated.
     * @return Uris for the stickers that would be added
    */
    private List<String> findUpdateAvailableUris(StickerPackViewerDownloadTask.Result result) {
        List<String> currentStickers = new ArrayList<>(pack.getReplaces().getStickerURLs());
        List<String> availableStickers = new ArrayList<>(result.urls);
        
        // We have installed stickers with firebase-style URLs, and uninstalled stickers
        // with http URLs. But once you take off the prefix, the rest is the same and we can
        // find the new, available stickers.
        for (int i=0; i < currentStickers.size(); i++) {
            String val = currentStickers.get(i);
            val = val.substring(Sticker.STICKER_URL_PATTERN.length());
            currentStickers.set(i, val);
        }
        
        for (int i=0; i < availableStickers.size(); i++) {
            String val = availableStickers.get(i);
            val = val.substring(Util.URL_BASE.length());
            availableStickers.set(i, val);
        }
        
        List<String> newStickers = UpdateUtils.findNewStickers(
                currentStickers,
                availableStickers);
        
        for (int i=0; i < newStickers.size(); i++) {
            String val = newStickers.get(i);
            val = Util.URL_BASE + val;
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
        
        List<String> output = new ArrayList<>(installedUris.size() + availableUris.size() + 2);
        
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
    
    public void clearException() {
        downloadException.setValue(null);
    }
    
    public boolean isInitialized() {
        return pack != null;
    }
    
    public boolean haveUrls() {
        return uris.getValue() != null && uris.getValue().size() > 0;
    }
}