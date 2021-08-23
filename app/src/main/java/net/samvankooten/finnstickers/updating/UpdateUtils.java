package net.samvankooten.finnstickers.updating;

import android.content.Context;
import android.content.SharedPreferences;

import net.samvankooten.finnstickers.R;
import net.samvankooten.finnstickers.Sticker;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import androidx.preference.PreferenceManager;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

/**
 * Created by sam on 10/29/17.
 */

public class UpdateUtils {
    private static final String TAG = "UpdateUtils";
    private static final String REGULAR_UPDATES_KEY = "regular updates";
    private static final String PROMPTED_UPDATES_KEY = "prompted updates";
    
    public static void scheduleUpdates(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (!prefs.getBoolean(context.getString(R.string.settings_check_in_background_key), true))
            return;
        
        var constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresStorageNotLow(true)
                .setRequiresBatteryNotLow(true)
                .build();
        var request = new PeriodicWorkRequest.Builder(UpdateWorker.class,
                7, TimeUnit.DAYS)
                .setConstraints(constraints)
                .setInitialDelay(6, TimeUnit.HOURS)
                .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        10, TimeUnit.MINUTES)
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                REGULAR_UPDATES_KEY, ExistingPeriodicWorkPolicy.KEEP, request);
    }
    
    public static void unscheduleUpdates(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(REGULAR_UPDATES_KEY);
    }
    
    public static void scheduleUpdateSoon(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (!prefs.getBoolean(context.getString(R.string.settings_check_in_background_key), true))
            return;
    
        var constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresStorageNotLow(true)
                .setRequiresBatteryNotLow(true)
                .build();
        var request = new OneTimeWorkRequest.Builder(UpdateWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        10, TimeUnit.MINUTES)
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(
                PROMPTED_UPDATES_KEY, ExistingWorkPolicy.KEEP, request);
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
