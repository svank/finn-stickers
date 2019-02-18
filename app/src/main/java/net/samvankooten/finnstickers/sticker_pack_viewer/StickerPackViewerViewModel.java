package net.samvankooten.finnstickers.sticker_pack_viewer;

import android.app.Application;
import android.content.Context;

import net.samvankooten.finnstickers.StickerPack;
import net.samvankooten.finnstickers.utils.DownloadCallback;

import java.util.List;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

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
    
    void downloadData() {
        if (downloadRunning.getValue())
            return;
        downloadRunning.setValue(true);
        new StickerPackViewerDownloadTask(this, pack, context).execute();
    }
    
    @Override
    public void finishDownloading() {
        downloadRunning.setValue(false);
    }
    
    @Override
    public void updateFromDownload(StickerPackViewerDownloadTask.Result result, Context context) {
        downloadSuccess.setValue(result.networkSucceeded);
        downloadException.setValue(result.exception);
        
        if (result.urls != null)
            uris.setValue(result.urls);
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