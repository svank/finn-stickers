package net.samvankooten.finnstickers.misc_classes;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import net.samvankooten.finnstickers.Constants;
import net.samvankooten.finnstickers.StickerPack;
import net.samvankooten.finnstickers.updating.UpdateUtils;
import net.samvankooten.finnstickers.utils.StickerPackRepository;
import net.samvankooten.finnstickers.utils.Util;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import androidx.annotation.NonNull;
import androidx.core.app.JobIntentService;

public class RestoreJobIntentService extends JobIntentService {
    private static final String TAG = "RestoreJobIntentService";
    public static final String RESTORE = ".RESTORE";
    
    private static final Lock lock = new ReentrantLock();
    
    static void enqueueWork(Context context, Intent work) {
        enqueueWork(context, RestoreJobIntentService.class, Constants.RESTORE_JOB_ID, work);
    }
    
    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        if (intent.getAction() == null || !intent.getAction().equals(RESTORE)) {
            Log.e(TAG, "Invalid intent");
            return;
        }
        if (!lock.tryLock()) {
            Log.e(TAG, "Task already running, stopping");
            return;
        }
        
        try {
            if (!Util.restoreIsPending(this)) {
                Log.e(TAG, "No restore needed");
                return;
            }
            
            if (!Util.connectedToInternet(this)) {
                Log.e(TAG, "no network access");
                return;
            }
            
            Util.performNeededMigrations(getApplicationContext());
            
            StickerPackRepository.AllPacksResult packs =
                    StickerPackRepository.getInstalledAndAvailablePacks(this);
            
            if (!packs.networkSucceeded) {
                Log.e(TAG, "Error downloading pack info");
                onRestoreFail(this);
                return;
            }
            
            if (packs.exception != null) {
                Log.e(TAG, "Error in pack list download", packs.exception);
                onRestoreFail(this);
                return;
            }
            
            for (StickerPack pack : packs.list) {
                if (isStopped()) {
                    Log.e(TAG, "Stopping on request before " + pack.getPackname());
                    return;
                }
                switch (pack.getStatus()) {
                    case UPDATABLE:
                        pack.update(this, null, false);
                        pack.renderCustomImages(this);
                        pack.updateStats(this);
                        break;
                    case INSTALLED:
                        // Force an "update" to re-download stickers
                        pack.setVersion(pack.getVersion() - 1);
                        pack.setStatus(StickerPack.Status.UPDATABLE);
                        pack.update(this, null, false);
                        pack.renderCustomImages(this);
                        pack.updateStats(this);
                        break;
                }
            }
            
            Util.markPendingRestore(this, false);
            
            UpdateUtils.scheduleUpdates(this);
            // It seems this is needed to ensure custom stickers are picked up when
            // rendered in this background restore context.
            AppIndexingUpdateReceiver.scheduleReindex(this, true);
        } finally {
            lock.unlock();
        }
    }
    
    private void onRestoreFail(Context context) {
        Util.getPrefs(context).edit().clear().apply();
    }
    
    @Override
    public boolean onStopCurrentWork() {
        return true;
    }
    
    public static void start(Context context) {
        Intent mIntent = new Intent(context, RestoreJobIntentService.class);
        mIntent.setAction(RestoreJobIntentService.RESTORE);
        RestoreJobIntentService.enqueueWork(context, mIntent);
    }
}
