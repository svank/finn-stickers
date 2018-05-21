package net.samvankooten.finnstickers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.google.firebase.appindexing.FirebaseAppIndex;

/** Receives broadcast for App Indexing Update. */
public class AppIndexingUpdateReceiver extends BroadcastReceiver {
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null
                && FirebaseAppIndex.ACTION_UPDATE_INDEX.equals(intent.getAction())) {
            // Schedule the job to be run in the background.
            //AppIndexingUpdateService.enqueueWork(context);
            // TODO: I think we're supposed to just re-insert every sticker into the index here
            // For now, just schedule an update check?
            if (!Util.checkIfEverOpened(context))
                return;
            UpdateManager.scheduleUpdates(context);
        }
    }
}