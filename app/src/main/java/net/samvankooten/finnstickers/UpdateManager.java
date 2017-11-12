package net.samvankooten.finnstickers;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.support.v4.app.NotificationCompat;
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
        List<String> uris = new LinkedList<String>();
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
    
    public static Notification buildNotification(Context context, List<String> newStickerList, StickerPack pack) {
        // Ensure channels are configured.
        // If they're in place already, this is a no-op
        NotificationUtils.createChannels(context);
        NotificationCompat.Builder n = new NotificationCompat.Builder(context, NotificationUtils.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(String.format("New %s stickers installed!", pack.getPackname()))
                .setContentText(String.format("%d new stickers", newStickerList.size()));
        
        Intent resultIntent = new Intent(context, StickerPackViewerActivity.class);
        resultIntent.putExtra("packName", pack.getPackname());
        PendingIntent resultPendingIntent =
                PendingIntent.getActivity(context, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        n.setContentIntent(resultPendingIntent);
        
        Log.d(TAG, "Built notif");
        
        Notification notif = n.build();
        notif.flags |= Notification.FLAG_AUTO_CANCEL;
        return notif;
    }
    
    public static void showNotification(Context context, Notification n) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify((int) System.currentTimeMillis(), n);
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
