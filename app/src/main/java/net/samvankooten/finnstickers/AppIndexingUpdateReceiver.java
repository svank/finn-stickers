package net.samvankooten.finnstickers;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.google.firebase.appindexing.FirebaseAppIndex;

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
            
            ComponentName serviceComponent = new ComponentName(context, ReindexJob.class);
            JobInfo.Builder builder = new JobInfo.Builder(1, serviceComponent);
            builder.setPersisted(true);
            builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE);
            if (Build.VERSION.SDK_INT >= 26) {
                builder.setRequiresBatteryNotLow(true);
            }
            builder.setRequiresDeviceIdle(true);
            JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            jobScheduler.schedule(builder.build());
        }
    }
}