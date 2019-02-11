package net.samvankooten.finnstickers.updating;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import net.samvankooten.finnstickers.MainActivity;
import net.samvankooten.finnstickers.StickerPack;
import net.samvankooten.finnstickers.StickerPackListDownloadTask;
import net.samvankooten.finnstickers.utils.DownloadCallback;

import java.net.URL;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by sam on 10/29/17.
 */

public class UpdateManager implements DownloadCallback<StickerPackListDownloadTask.Result> {
    private static final String TAG = "UpdateManager";
    private UpdateJob callingJob = null;
    private JobParameters callingJobParams = null;
    
    public static void scheduleUpdates(Context context) {
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
        List<String> uris = new LinkedList<>(newUris);
        
        for (String oldUri : oldUris) {
            oldUri = trimFilename(oldUri);
            for (int i=0; i<uris.size(); i++) {
                String newUri = trimFilename(uris.get(i));
                if (oldUri.equals(newUri)) {
                    uris.remove(i);
                    break;
                }
            }
        }
        
        return uris;
    }
    
    private static String trimFilename(String filename) {
        if (filename == null)
            return filename;
        if (filename.contains("."))
            filename = filename.substring(0, filename.lastIndexOf("."));
        if (filename.contains("-sticker"))
            filename = filename.substring(0, filename.lastIndexOf("-sticker"));
        return filename;
    }
    
    public void backgroundUpdate(Context context, UpdateJob callingJob, JobParameters params) {
        this.callingJob = callingJob;
        this.callingJobParams = params;
        try {
            AsyncTask packListTask = new StickerPackListDownloadTask(this, context,
                    new URL(MainActivity.PACK_LIST_URL), context.getCacheDir(),
                    context.getFilesDir());
            packListTask.execute();
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
        
        if (result.exception != null) {
            Log.e(TAG, "Exception raised in pack list download; halting", result.exception);
            return;
        }
        
        List<StickerPack> packs = result.packs;
        for (StickerPack pack : packs) {
            if (pack.getStatus() == StickerPack.Status.UPDATEABLE) {
                pack.update(context, null, true);
            }
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
