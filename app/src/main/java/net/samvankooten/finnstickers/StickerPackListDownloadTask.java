package net.samvankooten.finnstickers;

import android.content.Context;
import android.os.AsyncTask;

import net.samvankooten.finnstickers.utils.DownloadCallback;
import net.samvankooten.finnstickers.utils.Util;

import java.io.File;
import java.net.URL;

/**
 * Created by sam on 10/22/17.
 */

public class StickerPackListDownloadTask extends AsyncTask<Object, Void, Util.AllPacksResult> {
    private static final String TAG = "StckrPckLstDownloadTask";
    
    private DownloadCallback<Util.AllPacksResult> callback;
    private final URL packListURL;
    private final File iconsDir;
    private Context context;
    
    public StickerPackListDownloadTask(DownloadCallback<Util.AllPacksResult> callback, Context context,
                                       URL packListURL, File iconsDir) {
        this.packListURL = packListURL;
        this.iconsDir = iconsDir;
        this.callback = callback;
        this.context = context;
    }

    /**
     * Defines work to perform on the background thread.
     */
    @Override
    protected Util.AllPacksResult doInBackground(Object... params) {
        if (isCancelled()) {
            return null;
        }
        
        Util.AllPacksResult result = Util.getInstalledAndAvailablePacks(packListURL, context);
        
        if (result.networkSucceeded)
            Util.checkForNewPacks(context, result.list);
        
        return result;
    }
    
    /**
     * Updates the DownloadCallback with the result.
     */
    @Override
    protected void onPostExecute(Util.AllPacksResult result) {
        if (callback != null && context != null) {
            callback.updateFromDownload(result, context);
            callback.finishDownloading();
        }
        callback = null;
        context = null;
    }
    
    protected void onCancelled(Util.AllPacksResult result) {
        onPostExecute(result);
    }
}