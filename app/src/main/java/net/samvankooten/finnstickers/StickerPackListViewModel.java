package net.samvankooten.finnstickers;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.content.Context;

import java.io.File;
import java.net.URL;

public class StickerPackListViewModel extends AndroidViewModel implements DownloadCallback<StickerPackListDownloadTask.Result> {
    private static final String TAG = "StickerPackLstViewModel";
    
    private final MutableLiveData<StickerPackListDownloadTask.Result> downloadResult = new MutableLiveData<>();
    private final MutableLiveData<Integer> packStatusChange = new MutableLiveData<>();
    private File iconsDir;
    private File dataDir;
    private URL packListURL;
    private Application context;
    private boolean taskRunning = false;
    
    public StickerPackListViewModel(Application application) {
        super(application);
        this.context = application;
        packStatusChange.setValue(0);
    }
    
    boolean infoHasBeenSet() {
        return iconsDir != null && dataDir != null && packListURL != null;
    }
    
    void setInfo(URL packListURL, File iconsDir, File dataDir) {
        this.packListURL = packListURL;
        this.iconsDir = iconsDir;
        this.dataDir = dataDir;
    }
    
    void downloadData() {
        if (taskRunning)
            return;
        taskRunning = true;
        new StickerPackListDownloadTask(this, context,
                packListURL, iconsDir, dataDir).execute();
    }
    
    @Override
    public void finishDownloading() {
        taskRunning = false;
    }
    
    @Override
    public void updateFromDownload(StickerPackListDownloadTask.Result result, Context c) {
        this.downloadResult.setValue(result);
    }
    
    LiveData<StickerPackListDownloadTask.Result> getPacks() {
        return downloadResult;
    }
    
    LiveData<Integer> getPackStatusChange() {
        return packStatusChange;
    }
    
    void triggerPackStatusChange() {
        packStatusChange.setValue(0);
    }
}
