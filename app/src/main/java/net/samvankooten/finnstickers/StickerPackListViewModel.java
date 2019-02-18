package net.samvankooten.finnstickers;

import android.app.Application;
import android.content.Context;

import net.samvankooten.finnstickers.utils.DownloadCallback;
import net.samvankooten.finnstickers.utils.Util;

import java.io.File;
import java.net.URL;
import java.util.List;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

public class StickerPackListViewModel extends AndroidViewModel implements DownloadCallback<Util.AllPacksResult> {
    private static final String TAG = "StickerPackLstViewModel";
    
    private final MutableLiveData<Integer> packStatusChange = new MutableLiveData<>();
    private final MutableLiveData<List<StickerPack>> packs = new MutableLiveData<>();
    private final MutableLiveData<Boolean> downloadSuccess = new MutableLiveData<>();
    private final MutableLiveData<Exception> downloadException = new MutableLiveData<>();
    private final MutableLiveData<Boolean> downloadRunning = new MutableLiveData<>();
    private File iconsDir;
    private File dataDir;
    private URL packListURL;
    private final Application context;
    
    public StickerPackListViewModel(Application application) {
        super(application);
        this.context = application;
        packStatusChange.setValue(0);
        downloadRunning.setValue(false);
    }
    
    boolean isInitialized() {
        return iconsDir != null && dataDir != null && packListURL != null;
    }
    
    void setInfo(URL packListURL, File iconsDir, File dataDir) {
        this.packListURL = packListURL;
        this.iconsDir = iconsDir;
        this.dataDir = dataDir;
    }
    
    void downloadData() {
        if (downloadRunning.getValue())
            return;
        downloadRunning.setValue(true);
        new StickerPackListDownloadTask(this, context,
                packListURL, iconsDir, dataDir).execute();
    }
    
    @Override
    public void finishDownloading() {
        downloadRunning.setValue(false);
    }
    
    @Override
    public void updateFromDownload(Util.AllPacksResult result, Context context) {
        downloadSuccess.setValue(result.networkSucceeded);
        downloadException.setValue(result.exception);
        
        if (result.list != null)
            packs.setValue(result.list);
    }
    
    LiveData<Integer> getPackStatusChange() {
        return packStatusChange;
    }
    
    public LiveData<List<StickerPack>> getPacks() {
        return packs;
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
    
    void triggerPackStatusChange() {
        packStatusChange.setValue(0);
    }
}