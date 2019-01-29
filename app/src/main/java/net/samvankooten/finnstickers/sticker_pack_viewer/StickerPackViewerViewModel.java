package net.samvankooten.finnstickers.sticker_pack_viewer;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import net.samvankooten.finnstickers.StickerPack;
import net.samvankooten.finnstickers.utils.DownloadCallback;

import java.net.URL;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

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