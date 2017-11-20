package net.samvankooten.finnstickers;

/**
 * Created by sam on 9/24/17.
 * From https://github.com/firebase/quickstart-android/blob/master/app-indexing/app/src/main/java/com/google/samples/quickstart/app_indexing/AppIndexingService.java
 */

import android.app.IntentService;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class AppIndexingService extends IntentService {
    public static final String TAG = "AppIndexingService";

    public AppIndexingService() {
        super("AppIndexingService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(TAG, "onHandleIntent");
        ComponentName serviceComponent = new ComponentName(this, UpdateJob.class);
        JobInfo.Builder builder = new JobInfo.Builder(0, serviceComponent);
        builder.setMinimumLatency(0);
        builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED);
        builder.setRequiresDeviceIdle(true);
        if (Build.VERSION.SDK_INT >= 26) {
            builder.setRequiresStorageNotLow(true);
            builder.setRequiresBatteryNotLow(true);
        }
        JobScheduler jobScheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobScheduler.schedule(builder.build());
    }
}