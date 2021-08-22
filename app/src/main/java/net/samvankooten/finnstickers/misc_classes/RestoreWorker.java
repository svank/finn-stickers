package net.samvankooten.finnstickers.misc_classes;

import android.content.Context;
import android.util.Log;

import net.samvankooten.finnstickers.StickerPack;
import net.samvankooten.finnstickers.ar.AROnboardActivity;
import net.samvankooten.finnstickers.utils.StickerPackRepository;
import net.samvankooten.finnstickers.utils.Util;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class RestoreWorker extends Worker {
    private static final String TAG = "RestoreWorker";
    
    private static final Lock lock = new ReentrantLock();
    
    public RestoreWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }
    
    @NonNull
    @Override
    public Result doWork() {
        if (!lock.tryLock()) {
            Log.e(TAG, "Task already running, stopping");
            return Result.retry();
        }
        
        try {
            if (!Util.restoreIsPending(getApplicationContext())) {
                Log.e(TAG, "No restore needed");
                return Result.success();
            }
            
            if (!Util.connectedToInternet(getApplicationContext())) {
                Log.e(TAG, "no network access");
                return Result.retry();
            }
            
            Util.performNeededMigrations(getApplicationContext());
            
            StickerPackRepository.AllPacksResult packs =
                    StickerPackRepository.getInstalledAndAvailablePacks(getApplicationContext());
            
            if (!packs.networkSucceeded) {
                Log.e(TAG, "Error downloading pack info");
                onRestoreFail(getApplicationContext());
                return Result.retry();
            }
            
            if (packs.exception != null) {
                Log.e(TAG, "Error in pack list download", packs.exception);
                onRestoreFail(getApplicationContext());
                return Result.retry();
            }
            
            for (StickerPack pack : packs.list) {
                if (isStopped()) {
                    Log.e(TAG, "Stopping on request before " + pack.getPackname());
                    onRestoreFail(getApplicationContext());
                    return Result.retry();
                }
                switch (pack.getStatus()) {
                    case UPDATABLE:
                        pack.update(getApplicationContext(), null, false);
                        pack.renderCustomImages(getApplicationContext());
                        pack.updateStats(getApplicationContext());
                        break;
                    case INSTALLED:
                        // Force an "update" to re-download stickers
                        pack.setVersion(pack.getVersion() - 1);
                        pack.setStatus(StickerPack.Status.UPDATABLE);
                        pack.update(getApplicationContext(), null, false);
                        pack.renderCustomImages(getApplicationContext());
                        pack.updateStats(getApplicationContext());
                        break;
                }
            }
            
            if (AROnboardActivity.arHasRun(getApplicationContext()))
                AROnboardActivity.setShouldPromptForNeededPermissions(true, getApplicationContext());
            
            Util.markPendingRestore(getApplicationContext(), false);
            
            // It seems this is needed to ensure custom stickers are picked up when
            // rendered in this background restore context.
            ReindexWorker.schedule(getApplicationContext());
        } finally {
            lock.unlock();
        }
        return Result.success();
    }
    
    private void onRestoreFail(Context context) {
        Util.getPrefs(context).edit().clear().apply();
    }
    
    public static void start(Context context, boolean foreground) {
        Constraints constraints;
        ExistingWorkPolicy policy;
        if (foreground) {
            constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED).build();
            policy = ExistingWorkPolicy.REPLACE;
        } else {
            constraints = new Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .setRequiresStorageNotLow(true)
                    .setRequiredNetworkType(NetworkType.UNMETERED).build();
            policy = ExistingWorkPolicy.KEEP;
        }
        var request = new OneTimeWorkRequest.Builder(RestoreWorker.class)
                .setConstraints(constraints).build();
        
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(TAG, policy, request);
    }
}
