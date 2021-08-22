package net.samvankooten.finnstickers.updating;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import net.samvankooten.finnstickers.R;
import net.samvankooten.finnstickers.utils.DownloadCallback;
import net.samvankooten.finnstickers.utils.Util;

import androidx.work.Configuration;

/**
 * Created by sam on 11/19/17.
 */

public class UpdateJob extends JobService implements DownloadCallback<StickerPackBackgroundUpdateTask.Result> {
    private static final String TAG = "UpdateJob";
    private StickerPackBackgroundUpdateTask task;
    private JobParameters callingJobParams = null;
    
    public UpdateJob() {
        Configuration.Builder builder = new Configuration.Builder();
        builder.setJobSchedulerJobIdRange(98000, 99000);
    }
    
    @Override
    public boolean onStartJob(JobParameters params) {
        SharedPreferences prefs = Util.getUserPrefs(this);
        if (prefs.getBoolean(getString(R.string.settings_check_in_background_key), true)) {
            callingJobParams = params;
            task = new StickerPackBackgroundUpdateTask(this, getApplicationContext());
            task.execute();
        }
        return true;
    }
    
    @Override
    public boolean onStopJob(JobParameters params) {
        if (task != null)
            task.cancel(true);
        return true;
    }
    
    @Override
    public void updateFromDownload(StickerPackBackgroundUpdateTask.Result result, Context context) {
        if (result == null || !result.success) {
            Log.e(TAG, "Background update unsuccessful");
            jobFinished(callingJobParams, true);
            callingJobParams = null;
            return;
        }
        
        if (result.exception != null) {
            Log.e(TAG, "Exception raised in pack list download; halting", result.exception);
            jobFinished(callingJobParams, true);
            callingJobParams = null;
            return;
        }
    }
    
    @Override
    public void finishDownloading() {
        if (callingJobParams != null)
            jobFinished(callingJobParams, false);
    }
}