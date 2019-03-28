package net.samvankooten.finnstickers;

/*
  Created by sam on 10/22/17.
 */


import android.content.Context;
import android.os.AsyncTask;

import net.samvankooten.finnstickers.utils.DownloadCallback;
import net.samvankooten.finnstickers.utils.StickerPackProcessor;
import net.samvankooten.finnstickers.utils.Util;

import java.net.URL;

public class StickerPackDownloadTask extends AsyncTask<Object, Void, StickerPackDownloadTask.Result> {
    
    private static final String TAG = "StickerPackDownloadTask";
    private DownloadCallback<Result> callback;
    private final StickerPack pack;
    private final boolean showNotif;
    private Context context;
    
    public StickerPackDownloadTask(DownloadCallback<StickerPackDownloadTask.Result> callback,
                                   StickerPack pack,
                                   boolean showNotif,
                                   Context context) {
        this.pack = pack;
        this.callback = callback;
        this.showNotif = showNotif;
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
        return doWork();
    }
    
    public void doInForeground() {
        StickerPackDownloadTask.Result result = doWork();
        onPostExecute(result);
    }
    
    /**
     * The meat of this class, which can be called synchronously as well as run asynchronously
     * via the Task interface.
     */
    private StickerPackDownloadTask.Result doWork() {
        Result result = null;
        Util.DownloadResult dResult = null;
        if (isCancelled())
            return result;
        
        try {
            try {
                URL url = new URL(pack.buildURLString(pack.getDatafile()));
                dResult = Util.downloadFromUrl(url);
                StickerPackProcessor processor = new StickerPackProcessor(pack, context);
                processor.process(dResult.readString(), showNotif);
                result = new Result(true);
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