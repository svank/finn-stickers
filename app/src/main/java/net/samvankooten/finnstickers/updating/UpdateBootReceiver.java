package net.samvankooten.finnstickers.updating;

/*
  Created by sam on 12/17/17.
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import net.samvankooten.finnstickers.utils.Util;

/**
 * Reschedules background updates if the app is updated or the devices reboots
 */
public class UpdateBootReceiver extends BroadcastReceiver {
    public static final String TAG = "UpdateBootReceiver";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() == null
                || (!intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)
                    && !intent.getAction().equals(Intent.ACTION_MY_PACKAGE_REPLACED)))
            return;
        if (!Util.appHasBeenOpenedBefore(context))
            return;
        UpdateUtils.scheduleUpdates(context);
    }
}