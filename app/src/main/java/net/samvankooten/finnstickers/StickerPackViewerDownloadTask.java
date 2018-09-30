package net.samvankooten.finnstickers;

/**
 * Created by sam on 10/22/17.
 */


import android.content.Context;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Implementation of AsyncTask designed to fetch data from the network.
 */
public class StickerPackViewerDownloadTask extends AsyncTask<Object, Integer, StickerPackViewerDownloadTask.Result> {
    
    public static final String TAG = "StkrPkVwrDownloadTask";
    
    private DownloadCallback<StickerPackViewerDownloadTask.Result> mCallback;
    private StickerPack pack;
    private Context mContext;
    
    StickerPackViewerDownloadTask(DownloadCallback<StickerPackViewerDownloadTask.Result> callback, StickerPack pack, Context context) {
        this.pack = pack;
        setCallback(callback);
        mContext = context;
    }
    
    void setCallback(DownloadCallback<StickerPackViewerDownloadTask.Result> callback) {
        mCallback = callback;
    }
    
    /**
     * Wrapper class that serves as a union of a result value and an exception. When the download
     * task has completed, either the result value or exception can be a non-null value.
     * This allows you to pass exceptions to the UI thread that were thrown during doInBackground().
     */
    class Result {
        public List<String> urls;
        public Exception mException;
        public Result(List<String> resultValue) {
            urls = resultValue;
        }
        public Result(Exception exception) {
            mException = exception;
        }
    }
    
    /**
     * Cancel background network operation if we do not have network connectivity.
     */
    @Override
    protected void onPreExecute() {
        if (mCallback != null) {
            NetworkInfo networkInfo = mCallback.getActiveNetworkInfo(mContext);
            if (networkInfo == null || !networkInfo.isConnected() ||
                    (networkInfo.getType() != ConnectivityManager.TYPE_WIFI
                            && networkInfo.getType() != ConnectivityManager.TYPE_MOBILE)) {
                // If no connectivity, cancel task and update Callback with null data.
                mCallback.updateFromDownload(null, mContext);
                cancel(true);
            }
        }
    }
    
    /**
     * Defines work to perform on the background thread.
     */
    @Override
    protected StickerPackViewerDownloadTask.Result doInBackground(Object... urls) {
        Result result = null;
        List<Bitmap> images = new LinkedList<>();
        
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
                    List<Sticker> stickerList = processor.getStickerList(dResult);
                    dResult.close();
                    
                    List<String> stickerUrls = new ArrayList<>(stickerList.size());
                    
                    for (int i=0; i<stickerList.size(); i++) {
                        stickerUrls.add(pack.buildURLString(stickerList.get(i).getPath()));
                    }
                    result = new Result(stickerUrls);
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
            if (result.mException != null) {
                mCallback.updateFromDownload(result, mContext);
            } else if (result.urls != null) {
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