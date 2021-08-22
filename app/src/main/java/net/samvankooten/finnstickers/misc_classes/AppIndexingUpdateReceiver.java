package net.samvankooten.finnstickers.misc_classes;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.google.firebase.appindexing.FirebaseAppIndex;

import net.samvankooten.finnstickers.utils.Util;

/** Receives broadcast for App Indexing Update. */
public class AppIndexingUpdateReceiver extends BroadcastReceiver {
    
    private static final String TAG = "AppIndexingUpdateRceivr";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null
                && FirebaseAppIndex.ACTION_UPDATE_INDEX.equals(intent.getAction())) {
            // We're supposed to re-insert the Indexables in the Firebase index,
            // in case the index got corrupted or something.
            // (It appears to de-duplicate, so there's no concern about checking before
            // we re-insert.)
            if (!Util.appHasBeenOpenedBefore(context))
                return;
    
            ReindexWorker.schedule(context);
        }
    }
}