package net.samvankooten.finnstickers;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;

/**
 * Created by sam on 10/29/17.
 */

public class NotificationUtils {
    
    public static final String CHANNEL_ID = "updates";
    
    public static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return;
        NotificationManager mNotificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        CharSequence name = context.getString(R.string.notif_channel_name);
        String description = context.getString(R.string.notif_channel_description);
        int importance = NotificationManager.IMPORTANCE_LOW;
        NotificationChannel mChannel = new NotificationChannel(CHANNEL_ID, name, importance);
        mChannel.setDescription(description);
        mChannel.enableLights(true);
        mChannel.setLightColor(Color.WHITE);
        mChannel.enableVibration(false);
        mNotificationManager.createNotificationChannel(mChannel);
    }
}
