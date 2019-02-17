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
    private final Application context;
    private boolean taskRunning = false;
    private StickerPack pack;
    
    public StickerPackViewerViewModel(Application application) {
        super(application);
        this.context = application;
    }
    
    void setPack(StickerPack pack) {
        if (this.pack == null) {
            this.pack = pack;
            downloadData();
        }
    }
    
    void downloadData() {
        if (taskRunning)
            return;
        taskRunning = true;
        new StickerPackViewerDownloadTask(this, pack, context).execute();
    }
    
    @Override
    public void finishDownloading() {
        taskRunning = false;
    }
    
    @Override
    public void updateFromDownload(StickerPackViewerDownloadTask.Result result, Context c) {
        if (result == null) {
            downloadSuccess.setValue(false);
            downloadException.setValue(null);
        } else if (result.exception != null) {
            downloadException.setValue(result.exception);
        } else {
            downloadException.setValue(null);
            downloadSuccess.setValue(true);
            uris.setValue(result.urls);
        }
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
    
    public void clearFailures() {
        downloadSuccess.setValue(true);
        downloadException.setValue(null);
    }
    
    public boolean isInitialized() {
        return pack != null;
    }
}