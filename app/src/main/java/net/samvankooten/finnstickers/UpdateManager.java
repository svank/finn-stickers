package net.samvankooten.finnstickers;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.util.Log;

import java.net.URL;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by sam on 10/29/17.
 */

public class UpdateManager implements DownloadCallback<StickerPackListDownloadTask.Result> {
    public static final String TAG = "UpdateManager";
    
    public static List<String> findNewStickers(List<String> oldUris, List<String> newUris) {
        List<String> uris = new LinkedList<>();
        uris.addAll(newUris);
        
        for (String oldUri : oldUris) {
            for (int i=0; i<uris.size(); i++) {
                if (oldUri.equals(uris.get(i))) {
                    uris.remove(i);
                    break;
                }
            }
        }
        
        return uris;
    }
    
    public void backgroundUpdate(Context context) {
        try {
            // TODO: Should we check network connectivity first?
            // TODO: Postpone this to device idle/charging/wifi?
                AsyncTask packListTask = new StickerPackListDownloadTask(this, context,
                        new URL(MainActivity.PACK_LIST_URL), context.getCacheDir(),
                        context.getFilesDir());
                packListTask.execute(new Object());
        } catch (Exception e) {
            Log.e(TAG, "Bad pack list download effort", e);
        }
    }
    
    @Override
    public void updateFromDownload(StickerPackListDownloadTask.Result result, Context context) {
        if (result.mException != null) {
            Log.e(TAG, "Exception raised in pack list download; halting", result.mException);
            return;
        }
        
        StickerPack[] packs = result.mResultValue;
        for (StickerPack pack : packs) {
            if (pack.getStatus() == StickerPack.Status.UPDATEABLE) {
                pack.update(null, context);
            }
        }
    }
    
    @Override
    public NetworkInfo getActiveNetworkInfo(Context context) {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return connectivityManager.getActiveNetworkInfo();
    }
    
    @Override
    public void onProgressUpdate(int progressCode, int percentComplete) {
        switch(progressCode) {
            // TODO: add UI behavior for progress updates here.
            case Progress.ERROR:
                
                break;
            case Progress.CONNECT_SUCCESS:
                
                break;
            case Progress.GET_INPUT_STREAM_SUCCESS:
                
                break;
            case Progress.PROCESS_INPUT_STREAM_IN_PROGRESS:
                
                break;
            case Progress.PROCESS_INPUT_STREAM_SUCCESS:
                
                break;
        }
    }
    
    @Override
    public void finishDownloading() {
    }
}
