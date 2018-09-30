package net.samvankooten.finnstickers;

/**
 * Created by sam on 10/22/17.
 */


import android.content.Context;
import android.os.AsyncTask;

import java.io.IOException;
import java.net.URL;

public class StickerPackDownloadTask extends AsyncTask<Object, Void, StickerPackDownloadTask.Result> {
    
    private static final String TAG = "StickerPackDownloadTask";
    private DownloadCallback<StickerPackDownloadTask.Result> mCallback;
    private StickerPack pack;
    private Context mContext;
    
    StickerPackDownloadTask(DownloadCallback<StickerPackDownloadTask.Result> callback, StickerPack pack, Context context) {
        this.pack = pack;
        mCallback = callback;
        mContext = context;
    }
    
    /**
     * Wrapper class that serves as a union of a result value and an exception. When the download
     * task has completed, either the result value or exception can be a non-null value.
     * This allows you to pass exceptions to the UI thread that were thrown during doInBackground().
     */
    class Result {
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
        if (!Util.connectedToInternet(mContext)) {
                // If no connectivity, cancel task and update Callback with null data.
                mCallback.updateFromDownload(null, mContext);
                mCallback.finishDownloading();
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
                    StickerProcessor processor = new StickerProcessor(pack, mContext);
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
        if (result != null && mCallback != null) {
            if (result.exception != null) {
                mCallback.updateFromDownload(result, mContext);
            } else if (result.success) {
                mCallback.updateFromDownload(result, mContext);
            }
            mCallback.finishDownloading();
        }
        mCallback = null;
        mContext = null;
    }
    
    /**
     * Override to add special behavior for cancelled AsyncTask.
     */
    @Override
    protected void onCancelled(Result result) {
    }
}