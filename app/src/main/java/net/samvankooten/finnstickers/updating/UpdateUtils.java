package net.samvankooten.finnstickers.updating;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by sam on 10/29/17.
 */

public class UpdateUtils {
    private static final String TAG = "UpdateUtils";
    
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
}
