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
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class RestoreWorker extends Worker {
    private static final String TAG = "RestoreWorker";
    public static final String WORK_ID = TAG;
    
    private static final Lock lock = new ReentrantLock();
    public static final String PROGRESS = "PROGRESS";
    
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
            
            setProgressAsync(
                    new Data.Builder().putFloat(PROGRESS, 0).build());
            
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
            
            final float progressPerPack = 1f / packs.list.size();
            
            for (int i=0; i<packs.list.size(); i++) {
                var pack = packs.list.get(i);
                if (isStopped()) {
                    Log.e(TAG, "Stopping on request before " + pack.getPackname());
                    onRestoreFail(getApplicationContext());
                    return Result.retry();
                }
                switch (pack.getStatus()) {
                    case INSTALLED:
                        // Force an "update" to re-download stickers
                        pack.setVersion(pack.getVersion() - 1);
                        pack.setStatus(StickerPack.Status.UPDATABLE);
                    case UPDATABLE:
                        pack.update(getApplicationContext(), null, false);
                        setProgressAsync(
                                new Data.Builder().putFloat(PROGRESS,
                                        progressPerPack * i
                                        + progressPerPack/2).build());
                        final int j = i;
                        pack.renderCustomImages(getApplicationContext(), (nComplete, nTotal) ->
                            setProgressAsync(
                                    new Data.Builder().putFloat(PROGRESS,
                                            progressPerPack * j
                                            + progressPerPack/2
                                            + progressPerPack/2/nTotal*nComplete).build())
                        );
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
    
    public static void start(Context context) {
        var constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED).build();
        var request = new OneTimeWorkRequest.Builder(RestoreWorker.class)
                .setConstraints(constraints).build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(
                WORK_ID, ExistingWorkPolicy.KEEP, request);
    }
}
