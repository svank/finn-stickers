package net.samvankooten.finnstickers;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import java.net.URL;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by sam on 10/29/17.
 */

public class UpdateManager implements DownloadCallback<StickerPackListDownloadTask.Result> {
    public static final String TAG = "UpdateManager";
    private UpdateJob callingJob = null;
    private JobParameters callingJobParams = null;
    
    public static void scheduleUpdates(Context context) {
        // TODO: Consider replacing this scheduling logic with Jetpack's WorkManager
        ComponentName serviceComponent = new ComponentName(context, UpdateJob.class);
        JobInfo.Builder builder = new JobInfo.Builder(0, serviceComponent);
        builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED);
        builder.setRequiresDeviceIdle(true);
        builder.setPersisted(true);
        int period = 24*60*60*1000; // Once per day
        if (Build.VERSION.SDK_INT >= 24) {
            builder.setPeriodic(period, period); // Offer a large flex value
        } else {
            builder.setPeriodic(period);
        }
        if (Build.VERSION.SDK_INT >= 26) {
            builder.setRequiresStorageNotLow(true);
            builder.setRequiresBatteryNotLow(true);
        }
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobScheduler.schedule(builder.build());
    }
    
    public static List<String> findNewStickers(List<String> oldUris, List<String> newUris) {
        List<String> uris = new LinkedList<>();
        uris.addAll(newUris);
        
        for (String oldUri : oldUris) {
            for (int i=0; i<uris.size(); i++) {
                if (oldUri.equals(uris.get(i))) {
                    uris.remove(i);
                    break;
                }
            }
        }
        
        return uris;
    }
    
    public void backgroundUpdate(Context context, UpdateJob callingJob, JobParameters params) {
        this.callingJob = callingJob;
        this.callingJobParams = params;
        try {
            // TODO: Should we check network connectivity first?
            // TODO: Postpone this to device idle/charging/wifi?
                AsyncTask packListTask = new StickerPackListDownloadTask(this, context,
                        new URL(MainActivity.PACK_LIST_URL), context.getCacheDir(),
                        context.getFilesDir());
                packListTask.execute(new Object());
        } catch (Exception e) {
            Log.e(TAG, "Bad pack list download effort", e);
            if (callingJob != null) {
                callingJob.jobFinished(params, false);
                callingJob = null;
                callingJobParams = null;
            }
        }
    }
    
    @Override
    public void updateFromDownload(StickerPackListDownloadTask.Result result, Context context) {
        if (result == null)
            // No network connectivity
            return;
        
        if (result.mException != null) {
            Log.e(TAG, "Exception raised in pack list download; halting", result.mException);
            return;
        }
        
        List<StickerPack> packs = result.mResultValue;
        for (StickerPack pack : packs) {
            if (pack.getStatus() == StickerPack.Status.UPDATEABLE) {
                pack.update(null, context);
            }
        }
    }
    
    @Override
    public NetworkInfo getActiveNetworkInfo(Context context) {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return connectivityManager.getActiveNetworkInfo();
    }
    
    @Override
    public void onProgressUpdate(int progressCode, int percentComplete) {
        switch(progressCode) {
            // TODO: add UI behavior for progress updates here.
            case Progress.ERROR:
                
                break;
            case Progress.CONNECT_SUCCESS:
                
                break;
            case Progress.GET_INPUT_STREAM_SUCCESS:
                
                break;
            case Progress.PROCESS_INPUT_STREAM_IN_PROGRESS:
                
                break;
            case Progress.PROCESS_INPUT_STREAM_SUCCESS:
                
                break;
        }
    }
    
    @Override
    public void finishDownloading() {
        if (callingJob != null) {
            callingJob.jobFinished(callingJobParams, false);
            callingJob = null;
            callingJobParams = null;
        }
    }
}
