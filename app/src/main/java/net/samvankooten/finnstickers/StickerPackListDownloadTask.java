package net.samvankooten.finnstickers;

import android.content.Context;
import android.os.AsyncTask;

import net.samvankooten.finnstickers.utils.DownloadCallback;
import net.samvankooten.finnstickers.utils.StickerPackRepository;
import net.samvankooten.finnstickers.utils.Util;

/**
 * Created by sam on 10/22/17.
 */

public class StickerPackListDownloadTask extends AsyncTask<Object, Void, StickerPackRepository.AllPacksResult> {
    private static final String TAG = "StckrPckLstDownloadTask";
    
    private DownloadCallback<StickerPackRepository.AllPacksResult> callback;
    private Context context;
    
    public StickerPackListDownloadTask(DownloadCallback<StickerPackRepository.AllPacksResult> callback, Context context) {
        this.callback = callback;
        this.context = context;
    }

    /**
     * Defines work to perform on the background thread.
     */
    @Override
    protected StickerPackRepository.AllPacksResult doInBackground(Object... params) {
        if (isCancelled()) {
            return null;
        }
        
        StickerPackRepository.AllPacksResult result =
                StickerPackRepository.getInstalledAndAvailablePacks(context);
        
        if (result.networkSucceeded)
            Util.checkForNewPacks(context, result.list);
        
        return result;
    }
    
    /**
     * Updates the DownloadCallback with the result.
     */
    @Override
    protected void onPostExecute(StickerPackRepository.AllPacksResult result) {
        if (callback != null && context != null) {
            callback.updateFromDownload(result, context);
            callback.finishDownloading();
        }
        callback = null;
        context = null;
    }
    
    protected void onCancelled(StickerPackRepository.AllPacksResult result) {
        onPostExecute(result);
    }
}