package net.samvankooten.finnstickers;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.content.Context;
import android.util.Log;

import java.net.URL;

public class StickerPackViewerViewModel extends AndroidViewModel implements DownloadCallback<StickerPackViewerDownloadTask.Result> {
    private static final String TAG = "StickerPackVwrViewModel";
    
    private final MutableLiveData<StickerPackViewerDownloadTask.Result> result = new MutableLiveData<>();
    private URL packListURL;
    private Application context;
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
        Log.d(TAG, "in download");
        if (taskRunning)
            return;
        taskRunning = true;
        Log.d(TAG, "starting download");
        new StickerPackViewerDownloadTask(this, pack, context).execute();
    }
    
    @Override
    public void finishDownloading() {
        taskRunning = false;
        Log.d(TAG, "finished download");
    }
    
    @Override
    public void updateFromDownload(StickerPackViewerDownloadTask.Result result, Context c) {
        this.result.setValue(result);
    }
    
    LiveData<StickerPackViewerDownloadTask.Result> getResult() {
        return result;
    }
}