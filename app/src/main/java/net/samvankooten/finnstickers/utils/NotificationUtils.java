package net.samvankooten.finnstickers.utils;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import net.samvankooten.finnstickers.R;
import net.samvankooten.finnstickers.StickerPack;
import net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerActivity;

import java.io.File;
import java.io.IOException;
import java.util.List;

import androidx.core.app.NotificationCompat;

/**
 * Created by sam on 10/29/17.
 */

public class NotificationUtils {
    private static final String TAG = "NotificationUtils";
    
    private static final String CHANNEL_ID_STICKERS = "stickers";
    private static final String CHANNEL_ID_PACKS = "packs";
    
    /**
     * Ensure channels are configured.
     * If they're in place already, this is a no-op
     */
    public static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return;
        
        NotificationManager mNotificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        
        CharSequence name;
        String description;
        int importance;
        NotificationChannel mChannel;
        
        // New stickers in an existing pack
        name = context.getString(R.string.notif_channel_new_stickers_name);
        description = context.getString(R.string.notif_channel_new_stickers_description);
        importance = NotificationManager.IMPORTANCE_LOW;
        mChannel = new NotificationChannel(CHANNEL_ID_STICKERS, name, importance);
        mChannel.setDescription(description);
        mChannel.enableLights(false);
        mChannel.enableVibration(false);
        mNotificationManager.createNotificationChannel(mChannel);
        
        // Newly-available sticker pack
        name = context.getString(R.string.notif_channel_new_pack_name);
        description = context.getString(R.string.notif_channel_new_pack_description);
        importance = NotificationManager.IMPORTANCE_LOW;
        mChannel = new NotificationChannel(CHANNEL_ID_PACKS, name, importance);
        mChannel.setDescription(description);
        mChannel.enableLights(false);
        mChannel.enableVibration(false);
        mNotificationManager.createNotificationChannel(mChannel);
    
        // In case the Firebase message system is ever used
        name = context.getString(R.string.notif_channel_misc);
        description = context.getString(R.string.notif_channel_misc);
        importance = NotificationManager.IMPORTANCE_LOW;
        mChannel = new NotificationChannel(context.getString(R.string.notif_channel_misc), name, importance);
        mChannel.setDescription(description);
        mChannel.enableLights(false);
        mChannel.enableVibration(false);
        mNotificationManager.createNotificationChannel(mChannel);
    }
    
    public static Notification buildNewStickerNotification(Context context, StickerPack pack) {
        createChannels(context);
        
        List<String> newStickerList = pack.getUpdatedURIs();
        
        NotificationCompat.Builder n = new NotificationCompat.Builder(context, CHANNEL_ID_STICKERS)
                .setSmallIcon(R.drawable.icon_notif)
                .setContentTitle(context.getResources().getQuantityString(
                        R.plurals.notif_update_title,
                        newStickerList.size(),
                        pack.getPackname()))
                .setContentText(context.getResources().getQuantityString(
                        R.plurals.notif_update_text,
                        newStickerList.size(),
                        newStickerList.size()));
        
        if (newStickerList.size() > 0) {
            try {
                n.setLargeIcon(MediaStore.Images.Media.getBitmap(
                        context.getContentResolver(), Uri.parse(newStickerList.get(0))));
            } catch (IOException e) {
                Log.e(TAG, "Error loading sticker", e);
            }
        }
        
        Intent resultIntent = new Intent(context, StickerPackViewerActivity.class);
        resultIntent.putExtra(StickerPackViewerActivity.PACK, pack.getPackname());
        resultIntent.putExtra(StickerPackViewerActivity.PICKER, false);
        PendingIntent pi = TaskStackBuilder.create(context)
                .addNextIntentWithParentStack(resultIntent)
                .getPendingIntent((int) System.currentTimeMillis(), PendingIntent.FLAG_UPDATE_CURRENT);
        n.setContentIntent(pi);
        
        Notification notif = n.build();
        notif.flags |= Notification.FLAG_AUTO_CANCEL;
        return notif;
    }
    
    public static Notification buildNewStickerAvailNotification(Context context, StickerPack pack) {
        createChannels(context);
        
        NotificationCompat.Builder n = new NotificationCompat.Builder(context, CHANNEL_ID_STICKERS)
                .setSmallIcon(R.drawable.icon_notif)
                .setContentTitle(String.format(context.getString(R.string.notif_update_avail_title), pack.getPackname()))
                .setContentText(context.getString(R.string.notif_update_avail_text));
        
        try {
            n.setLargeIcon(MediaStore.Images.Media.getBitmap(
                    context.getContentResolver(), Uri.parse(pack.getIconLocation())));
        } catch (IOException e) {
            Log.e(TAG, "Error loading icon", e);
        }
        
        Intent resultIntent = new Intent(context, StickerPackViewerActivity.class);
        resultIntent.putExtra(StickerPackViewerActivity.PACK, pack.getPackname());
        resultIntent.putExtra(StickerPackViewerActivity.PICKER, false);
        PendingIntent pi = TaskStackBuilder.create(context)
                .addNextIntentWithParentStack(resultIntent)
                .getPendingIntent((int) System.currentTimeMillis(), PendingIntent.FLAG_UPDATE_CURRENT);
        n.setContentIntent(pi);
        
        Notification notif = n.build();
        notif.flags |= Notification.FLAG_AUTO_CANCEL;
        return notif;
    }
    
    public static Notification buildNewPackNotification(Context context, StickerPack pack, File icon) {
        createChannels(context);
        
        NotificationCompat.Builder n = new NotificationCompat.Builder(context, CHANNEL_ID_PACKS)
                .setSmallIcon(R.drawable.icon_notif)
                .setLargeIcon(BitmapFactory.decodeFile(icon.toString()))
                .setContentTitle(String.format(context.getString(R.string.notif_new_pack_title),
                                               pack.getPackname()))
                .setContentText(context.getString(R.string.notif_new_pack_text));
        
        Intent resultIntent = new Intent(context, StickerPackViewerActivity.class);
        resultIntent.putExtra(StickerPackViewerActivity.PACK, pack.getPackname());
        resultIntent.putExtra(StickerPackViewerActivity.PICKER, false);
        PendingIntent pi = TaskStackBuilder.create(context)
                .addNextIntentWithParentStack(resultIntent)
                .getPendingIntent((int) System.currentTimeMillis(), PendingIntent.FLAG_UPDATE_CURRENT);
        n.setContentIntent(pi);
        
        Notification notif = n.build();
        notif.flags |= Notification.FLAG_AUTO_CANCEL;
        return notif;
    }
    
    public static void showNotification(Context context, Notification n) {
        showNotification(context, n, (int) System.currentTimeMillis());
    }
    
    public static void showNotification(Context context, Notification n, int id) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(id, n);
    }
    
    public static void showUpdateNotif(Context context, StickerPack pack) {
        Notification n = buildNewStickerNotification(context, pack);
        showNotification(context, n);
    }
    
    public static void showUpdateAvailNotif(Context context, StickerPack pack) {
        Notification n = buildNewStickerAvailNotification(context, pack);
        showNotification(context, n, pack.hashCode());
    }
}
