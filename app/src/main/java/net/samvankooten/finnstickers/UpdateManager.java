package net.samvankooten.finnstickers;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by sam on 10/29/17.
 */

public class UpdateManager {
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
                .setContentText(String.format("%d stickers", newStickerList.size()));
        
        Intent resultIntent = new Intent(context, StickerPackViewerActivity.class);
        PendingIntent resultPendingIntent =
                PendingIntent.getActivity(context, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        n.setContentIntent(resultPendingIntent);
        
        Log.d(TAG, "Built notif");
        return n.build();
    }
    
    public static void showNotification(Context context, Notification n) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify((int) System.currentTimeMillis(), n);
    }
}
