package net.samvankooten.finnstickers.updating;

/*
  Created by sam on 10/22/17.
 */


import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import net.samvankooten.finnstickers.StickerPack;
import net.samvankooten.finnstickers.utils.DownloadCallback;
import net.samvankooten.finnstickers.utils.StickerPackRepository;
import net.samvankooten.finnstickers.utils.Util;

import java.net.MalformedURLException;
import java.net.URL;

public class StickerPackBackgroundUpdateTask extends AsyncTask<Object, Void, StickerPackBackgroundUpdateTask.Result> {
    
    private static final String TAG = "StickerPackDownloadTask";
    private DownloadCallback<Result> callback;
    private Context context;
    
    public StickerPackBackgroundUpdateTask(DownloadCallback<StickerPackBackgroundUpdateTask.Result> callback, Context context) {
        this.callback = callback;
        this.context = context;
    }
    
    /**
     * Wrapper class that serves as a union of a result value and an exception. When the download
     * task has completed, either the result value or exception can be a non-null value.
     * This allows you to pass exceptions to the UI thread that were thrown during doInBackground().
     */
    public class Result {
        public boolean success = false;
        public Exception exception;
        public Result(boolean resultValue) {
            success = resultValue;
        }
        public Result(Exception exception) {
            this.exception = exception;
        }
        public Result() {}
    }
    
    /**
     * Cancel background network operation if we do not have network connectivity.
     */
    @Override
    protected void onPreExecute() {
        if (!Util.connectedToInternet(context)) {
            cancel(true);
        }
    }
    
    /**
     * Defines work to perform on the background thread.
     */
    @Override
    protected StickerPackBackgroundUpdateTask.Result doInBackground(Object... params) {
        if (isCancelled())
            return new Result();
        
        URL url;
        try {
            url = new URL(Util.PACK_LIST_URL);
        } catch (MalformedURLException e) {
            Log.e(TAG, "Back pack list url", e);
            return new Result(e);
        }
        
        StickerPackRepository.AllPacksResult packs =
                StickerPackRepository.getInstalledAndAvailablePacks(context);
        if (packs.exception != null) {
            Log.e(TAG, "Error in packlist downlad", packs.exception);
            return new Result(packs.exception);
        }
        
        if (!packs.networkSucceeded) {
            Log.e(TAG, "Error downloading pack info");
            return new Result();
        }
        
        Util.checkForNewPacks(context, packs.list);
        
        for (StickerPack pack : packs.list) {
            if (isCancelled())
                return new Result();
            if (pack.getStatus() == StickerPack.Status.UPDATEABLE)
                pack.update(context, null, false);
        }
        
        return new Result(true);
    }
    
    /**
     * Updates the DownloadCallback with the result.
     */
    @Override
    protected void onPostExecute(Result result) {
        if (callback != null && context != null) {
            callback.updateFromDownload(result, context);
            callback.finishDownloading();
        }
        callback = null;
        context = null;
    }
    
    @Override
    protected void onCancelled(Result result) {
        onPostExecute(result);
    }
}