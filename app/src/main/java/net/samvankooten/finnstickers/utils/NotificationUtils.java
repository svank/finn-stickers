package net.samvankooten.finnstickers.utils;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;

import net.samvankooten.finnstickers.MainActivity;
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
        // Ensure channels are configured.
        // If they're in place already, this is a no-op
        createChannels(context);
        
        List<String> newStickerList = pack.getUpdatedURIs();
        
        NotificationCompat.Builder n = new NotificationCompat.Builder(context, CHANNEL_ID_STICKERS)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(String.format("New %s sticker%s installed!", pack.getPackname(),
                        newStickerList.size() > 1 ? "s" : ""))
                .setContentText(String.format("%d new sticker%s. Tap to view.", newStickerList.size(),
                        newStickerList.size() > 1 ? "s" : ""));
        
        if (newStickerList.size() > 0) {
            File image = new StickerProvider().setRootDir(context).uriToFile(Uri.parse(newStickerList.get(0)));
            n.setLargeIcon(BitmapFactory.decodeFile(image.toString()));
        }
        
        Intent resultIntent = new Intent(context, StickerPackViewerActivity.class);
        resultIntent.putExtra("pack", pack);
        resultIntent.putExtra("picker", false);
        PendingIntent resultPendingIntent =
                PendingIntent.getActivity(context, (int) System.currentTimeMillis(), resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        n.setContentIntent(resultPendingIntent);
        
        Notification notif = n.build();
        notif.flags |= Notification.FLAG_AUTO_CANCEL;
        return notif;
    }
    
    public static Notification buildNewPackNotification(Context context, StickerPack pack, File icon) {
        // Ensure channels are configured.
        // If they're in place already, this is a no-op
        createChannels(context);
        
        NotificationCompat.Builder n = new NotificationCompat.Builder(context, CHANNEL_ID_PACKS)
                .setSmallIcon(R.drawable.ic_notif)
                .setLargeIcon(BitmapFactory.decodeFile(icon.toString()))
                .setContentTitle(String.format("New %s sticker pack available!", pack.getPackname()))
                .setContentText("Tap to view.");
        
        Intent resultIntent = new Intent(context, MainActivity.class);
//        resultIntent.putExtra("packName", pack.getPackname());
        PendingIntent resultPendingIntent =
                PendingIntent.getActivity(context, (int) System.currentTimeMillis(), resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        n.setContentIntent(resultPendingIntent);
        
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
