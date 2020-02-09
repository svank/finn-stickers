package net.samvankooten.finnstickers.updating;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import net.samvankooten.finnstickers.Constants;
import net.samvankooten.finnstickers.R;
import net.samvankooten.finnstickers.Sticker;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import androidx.preference.PreferenceManager;

/**
 * Created by sam on 10/29/17.
 */

public class UpdateUtils {
    private static final String TAG = "UpdateUtils";
    
    public static void scheduleUpdates(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (!prefs.getBoolean(context.getString(R.string.settings_check_in_background_key), true))
            return;
        
        ComponentName serviceComponent = new ComponentName(context, UpdateJob.class);
        JobInfo.Builder builder = new JobInfo.Builder(Constants.PERIODIC_UPDATE_CHECK_ID, serviceComponent);
        builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED);
        builder.setRequiresDeviceIdle(true);
        builder.setPersisted(true);
        int period = 7*24*60*60*1000; // Once per week
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
        if (jobScheduler != null)
            jobScheduler.schedule(builder.build());
    }
    
    public static void unscheduleUpdates(Context context) {
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (jobScheduler != null)
            jobScheduler.cancel(Constants.PERIODIC_UPDATE_CHECK_ID);
    }
    
    public static void scheduleUpdateSoon(Context context) {
        ComponentName serviceComponent = new ComponentName(context, UpdateJob.class);
        JobInfo.Builder builder = new JobInfo.Builder(Constants.PROMPTED_UPDATE_CHECK_ID, serviceComponent);
        builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED);
        builder.setRequiresDeviceIdle(true);
        builder.setPersisted(true);
        if (Build.VERSION.SDK_INT >= 26) {
            builder.setRequiresStorageNotLow(true);
            builder.setRequiresBatteryNotLow(true);
        }
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (jobScheduler != null)
            jobScheduler.schedule(builder.build());
    }
    
    public static List<String> findNewUris(List<String> oldUris, List<String> newUris) {
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
    
    public static List<String> findNewUrisFromStickers(List<Sticker> oldStickers, List<Sticker> newStickers) {
        List<String> oldUris = new ArrayList<>(oldStickers.size());
        List<String> newUris = new ArrayList<>(newStickers.size());
    
        for (Sticker oldSticker: oldStickers)
            oldUris.add(oldSticker.getURI().toString());
        for (Sticker newSticker: newStickers)
            newUris.add(newSticker.getURI().toString());
        
        return findNewUris(oldUris, newUris);
    }
    
    private static String trimFilename(String filename) {
        if (filename == null)
            return null;
        if (filename.contains("."))
            filename = filename.substring(0, filename.lastIndexOf("."));
        if (filename.contains("-sticker"))
            filename = filename.substring(0, filename.lastIndexOf("-sticker"));
        return filename;
    }
}
