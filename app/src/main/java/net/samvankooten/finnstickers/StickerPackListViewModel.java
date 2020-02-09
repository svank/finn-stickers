package net.samvankooten.finnstickers;

import android.app.Application;
import android.content.Context;

import net.samvankooten.finnstickers.misc_classes.GlideApp;
import net.samvankooten.finnstickers.misc_classes.GlideRequest;
import net.samvankooten.finnstickers.utils.DownloadCallback;
import net.samvankooten.finnstickers.utils.StickerPackRepository;
import net.samvankooten.finnstickers.utils.Util;

import java.io.File;
import java.util.List;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

public class StickerPackListViewModel extends AndroidViewModel implements DownloadCallback<StickerPackRepository.AllPacksResult> {
    private static final String TAG = "StickerPackLstViewModel";
    
    private final MutableLiveData<List<StickerPack>> packs = new MutableLiveData<>();
    private final MutableLiveData<Boolean> downloadSuccess = new MutableLiveData<>();
    private final MutableLiveData<Exception> downloadException = new MutableLiveData<>();
    private final MutableLiveData<Boolean> downloadRunning = new MutableLiveData<>();
    private File dataDir;
    private final Application context;
    
    public StickerPackListViewModel(Application application) {
        super(application);
        this.context = application;
        downloadRunning.setValue(false);
    }
    
    boolean isInitialized() {
        return dataDir != null;
    }
    
    void setInfo(File dataDir) {
        this.dataDir = dataDir;
    }
    
    void downloadData() {
        if (downloadRunning.getValue())
            return;
        downloadRunning.setValue(true);
        new StickerPackListDownloadTask(this, context).execute();
    }
    
    @Override
    public void finishDownloading() {
        downloadRunning.setValue(false);
    }
    
    @Override
    public void updateFromDownload(StickerPackRepository.AllPacksResult result, Context context) {
        downloadSuccess.setValue(result.networkSucceeded);
        downloadException.setValue(result.exception);
        
        if (result.list != null) {
            packs.setValue(result.list);
    
            // When the app is first opened, MainActivity starts the pack list download, then starts
            // the onboarding activity. That download completes during onboarding, but the pack icons
            // aren't put into ImageViews until onboarding ends and MainActivity resumes. That means
            // the pack icons aren't downloaded until the user is viewing the pack list. Here we
            // tell Glide to start downloading the pack icons immediately, so they're ready and waiting
            // when onboarding is complete.
            if (!Util.appHasBeenOpenedBefore(context)) {
                for (StickerPack pack : result.list) {
                    GlideRequest<File> request = GlideApp.with(context).downloadOnly().load(pack.getIconLocation());
                    Util.enableGlideCacheIfRemote(request, pack.getIconLocation(), pack.getVersion());
                    request.submit(1, 1);
                }
            }
        }
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
}