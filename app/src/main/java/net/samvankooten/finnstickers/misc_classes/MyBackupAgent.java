package net.samvankooten.finnstickers.misc_classes;

import android.app.backup.BackupAgent;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.ParcelFileDescriptor;

import net.samvankooten.finnstickers.updating.UpdateManager;
import net.samvankooten.finnstickers.utils.NotificationUtils;

public class MyBackupAgent extends BackupAgent {
    private static final String TAG = "MyBackupAgent";
    
    // These must be implemented, but we don't need anything
    public void onBackup(ParcelFileDescriptor oldState,
                         BackupDataOutput data,
                         ParcelFileDescriptor newState) {
    }
    
    public void onRestore (BackupDataInput data,
                                    int appVersionCode,
                                    ParcelFileDescriptor newState) {
    }
    
    /**
     * Runs after a restore of the app data (e.g. after new device setup). The Android
     * backup/restore framework gets the sticker data in place, but we have to re-register
     * that data with Firebase for stickers to appear in Gboard. Here we schedule a Job to
     * do just that. We also enable the background-updates Job and setup notification channels.
     */
    public void onRestoreFinished() {
        ComponentName serviceComponent = new ComponentName(getApplicationContext(), ReindexJob.class);
        JobInfo.Builder builder = new JobInfo.Builder(1, serviceComponent);
        builder.setPersisted(true);
        builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE);
        // It seems there's some sort of race condition, that the re-indexing doesn't always
        // work after we've handed things to Firebase. So let's run the job 2-5 seconds
        // after restore.
        builder.setMinimumLatency(2000);
        builder.setOverrideDeadline(5000);
        JobScheduler jobScheduler = (JobScheduler) getApplicationContext().getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobScheduler.schedule(builder.build());
        
        UpdateManager.scheduleUpdates(getApplicationContext());
        
        NotificationUtils.createChannels(getApplicationContext());
    }
}
