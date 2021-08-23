package net.samvankooten.finnstickers.misc_classes;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import net.samvankooten.finnstickers.Constants;

import androidx.work.Configuration;

public class PostRestoreJob extends JobService {
    /**
     * It seems you can't schedule work with WorkManager from BackupAgent#onRestoreFinished, as
     * that's not running in a fully-initialized process for the application. I couldn't find any
     * discussion of this online, so my workaround is to use a JobScheduler job to schedule
     * the WorkManager job. :-/
     */
    private static final String TAG = "PostRestoreJob";
    
    public PostRestoreJob() {
        Configuration.Builder builder = new Configuration.Builder();
        builder.setJobSchedulerJobIdRange(98000, 99000);
    }
    
    @Override
    public boolean onStartJob(JobParameters params) {
        RestoreWorker.start(this);
        return false;
    }
    
    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
    
    public static void schedule(Context context) {
        var jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        var builder = new JobInfo.Builder(Constants.RESTORE_JOB_ID,
                new ComponentName(context, PostRestoreJob.class))
                .setOverrideDeadline(0);
        jobScheduler.schedule(builder.build());
    }
}
