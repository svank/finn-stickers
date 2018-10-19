package net.samvankooten.finnstickers;

/*
  Created by sam on 10/22/17.
 */


import android.content.Context;
import android.os.AsyncTask;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class StickerPackViewerDownloadTask extends AsyncTask<Object, Void, StickerPackViewerDownloadTask.Result> {
    
    public static final String TAG = "StkrPkVwrDownloadTask";
    
    private DownloadCallback<StickerPackViewerDownloadTask.Result> callback;
    private StickerPack pack;
    private Context context;
    
    StickerPackViewerDownloadTask(DownloadCallback<StickerPackViewerDownloadTask.Result> callback, StickerPack pack, Context context) {
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
        List<String> urls;
        Exception exception;
        Result(List<String> resultValue) {
            urls = resultValue;
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
    protected StickerPackViewerDownloadTask.Result doInBackground(Object... params) {
        Result result = null;
        
        Util.DownloadResult dResult = null;
        if (isCancelled()) {
            return result;
        }
        
        try {
            try {
                URL url = new URL(pack.buildURLString(pack.getDatafile()));
                dResult = Util.downloadFromUrl(url);
                StickerProcessor processor = new StickerProcessor(pack, context);
                List<Sticker> stickerList = processor.getStickerList(dResult);
                dResult.close();
                
                List<String> stickerUrls = new ArrayList<>(stickerList.size());
                
                for (int i=0; i<stickerList.size(); i++) {
                    stickerUrls.add(pack.buildURLString(stickerList.get(i).getPath()));
                }
                result = new Result(stickerUrls);
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