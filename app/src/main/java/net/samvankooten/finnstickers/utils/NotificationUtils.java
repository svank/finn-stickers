package net.samvankooten.finnstickers.utils;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;

import net.samvankooten.finnstickers.R;
import net.samvankooten.finnstickers.StickerPack;
import net.samvankooten.finnstickers.StickerProvider;
import net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerActivity;

import java.io.File;
import java.util.List;

import androidx.core.app.NotificationCompat;

/**
 * Created by sam on 10/29/17.
 */

public class NotificationUtils {
    
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
        mChannel.enableLights(true);
        mChannel.setLightColor(Color.WHITE);
        mChannel.enableVibration(false);
        mNotificationManager.createNotificationChannel(mChannel);
        
        // Newly-available sticker pack
        name = context.getString(R.string.notif_channel_new_pack_name);
        description = context.getString(R.string.notif_channel_new_pack_description);
        importance = NotificationManager.IMPORTANCE_LOW;
        mChannel = new NotificationChannel(CHANNEL_ID_PACKS, name, importance);
        mChannel.setDescription(description);
        mChannel.enableLights(true);
        mChannel.setLightColor(Color.WHITE);
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
            File image = new StickerProvider().setRootDir(context).uriToFile(Uri.parse(newStickerList.get(0)));
            n.setLargeIcon(BitmapFactory.decodeFile(image.toString()));
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
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify((int) System.currentTimeMillis(), n);
    }
    
    public static void showUpdateNotif(Context context, StickerPack pack) {
        Notification n = buildNewStickerNotification(context, pack);
        showNotification(context, n);
    }
}
