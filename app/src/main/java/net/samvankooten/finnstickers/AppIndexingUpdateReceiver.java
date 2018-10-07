package net.samvankooten.finnstickers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.google.firebase.appindexing.FirebaseAppIndex;

import androidx.work.Constraints;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

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
            if (!Util.checkIfEverOpened(context))
                return;
            UpdateManager.scheduleUpdates(context);
            Constraints.Builder constraints = new Constraints.Builder()
                    .setRequiresBatteryNotLow(true);
            
            if (Build.VERSION.SDK_INT >= 23) {
                constraints.setRequiresDeviceIdle(true);
            }
            
            OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(ReindexWorker.class)
                    .setConstraints(constraints.build())
                    .build();
            WorkManager.getInstance().enqueue(work);
        }
    }
}