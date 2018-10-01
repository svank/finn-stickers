package net.samvankooten.finnstickers;

/*
  Created by sam on 12/17/17.
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Reschedules background updates if the app is updated or the devices reboots
 */
public class UpdateBootReceiver extends BroadcastReceiver {
    public static final String TAG = "UpdateBootReceiver";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Util.checkIfEverOpened(context))
            return;
        UpdateManager.scheduleUpdates(context);
    }
}