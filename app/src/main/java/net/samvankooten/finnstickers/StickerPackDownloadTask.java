package net.samvankooten.finnstickers;

/*
  Created by sam on 10/22/17.
 */


import android.content.Context;
import android.os.AsyncTask;

import java.io.IOException;
import java.net.URL;

public class StickerPackDownloadTask extends AsyncTask<Object, Void, StickerPackDownloadTask.Result> {
    
    private static final String TAG = "StickerPackDownloadTask";
    private DownloadCallback<StickerPackDownloadTask.Result> callback;
    private StickerPack pack;
    private Context context;
    
    StickerPackDownloadTask(DownloadCallback<StickerPackDownloadTask.Result> callback, StickerPack pack, Context context) {
        this.pack = pack;
        this.callback = callback;
        this.context = context;
    }
    
    /**
     * Wrapper class that serves as a union of a result value and an exception. When the download
     * task has completed, either the result value or exception can be a non-null value.
     * This allows you to pass exceptions to the UI thread that were thrown during doInBackground().
     */
    class Result {
        boolean success = false;
        Exception exception;
        Result(boolean resultValue) {
            success = resultValue;
        }
        Result(Exception exception) {
            this.exception = exception;
        }
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
    protected StickerPackDownloadTask.Result doInBackground(Object... params) {
        Result result = null;
        Util.DownloadResult dResult = null;
        if (isCancelled()) {
            return result;
        }
        
        try {
            try {
                URL url = new URL(pack.buildURLString(pack.getDatafile()));
                dResult = Util.downloadFromUrl(url);
                if (dResult.stream != null) {
                    StickerProcessor processor = new StickerProcessor(pack, context);
                    processor.process(dResult);
                    result = new Result(true);
                } else {
                    throw new IOException("No response received.");
                }
            } finally {
                if (dResult != null)
                    dResult.close();
            }
        } catch(Exception e) {
            result = new Result(e);
        }
        return result;
    }
    
    /**
     * Updates the DownloadCallback with the result.
     */
    @Override
    protected void onPostExecute(Result result) {
        if (callback != null && callback != null) {
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