package net.samvankooten.finnstickers.updating;

import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import net.samvankooten.finnstickers.BuildConfig;
import net.samvankooten.finnstickers.R;
import net.samvankooten.finnstickers.StickerPack;
import net.samvankooten.finnstickers.utils.NotificationUtils;
import net.samvankooten.finnstickers.utils.StickerPackRepository;
import net.samvankooten.finnstickers.utils.Util;

import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

public class FirebaseMessageReceiver extends FirebaseMessagingService {
    private static final String TAG = "FirebaseMessageReceiver";
    
    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        
        if (remoteMessage.getNotification() != null) {
            // Just in case the "send a notification" system is ever used, that notif is sent
            // directly to the notification tray when the app is in the background, but it
            // goes here if the app is in the foreground. So here we just send it likewise
            // to the notification tray.
            Notification notification = new NotificationCompat.Builder(this, getApplicationContext().getString(R.string.notif_channel_misc))
                    .setContentTitle(remoteMessage.getNotification().getTitle())
                    .setContentText(remoteMessage.getNotification().getBody())
                    .setSmallIcon(R.drawable.icon_notif)
                    .build();
            NotificationUtils.showNotification(this, notification);
        } else if (remoteMessage.getData() != null) {
            Map<String, String> data = remoteMessage.getData();
            if (data.containsKey("check_for_update") && data.get("check_for_update").equals("true"))
                UpdateUtils.scheduleUpdateSoon(this);
            
        }
    }
    
    @Override
    public void onNewToken(@NonNull String token) {
        registerFCMTopics(this);
    }
    
    public static void registerFCMTopics(Context context) {
        SharedPreferences prefs = Util.getUserPrefs(context);
        if (!prefs.getBoolean(context.getString(R.string.settings_check_in_background_key), true))
            return;
        FirebaseMessaging.getInstance().subscribeToTopic("update_notif")
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful())
                        Log.e(TAG, "Sub error");
                });
        
        if (BuildConfig.VERSION_NAME.endsWith("-dev"))
            FirebaseMessaging.getInstance().subscribeToTopic("update_notif_dev")
                    .addOnCompleteListener(task -> {
                        if (!task.isSuccessful())
                            Log.e(TAG, "Sub error");
                    });
    }
    
    public static void unregisterFCMTopics() {
        FirebaseMessaging.getInstance().unsubscribeFromTopic("update_notif");
        FirebaseMessaging.getInstance().unsubscribeFromTopic("update_notif_dev");
    }
    
    public static void unregisterFCMTopicsIfNoPacksInstalled(Context context) {
        List<StickerPack> packs = StickerPackRepository.getInstalledPacks(context);
        if (packs != null && packs.size() == 0)
            unregisterFCMTopics();
    }
}
