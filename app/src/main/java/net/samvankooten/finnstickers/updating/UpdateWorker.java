package net.samvankooten.finnstickers.updating;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import net.samvankooten.finnstickers.R;
import net.samvankooten.finnstickers.StickerPack;
import net.samvankooten.finnstickers.utils.NotificationUtils;
import net.samvankooten.finnstickers.utils.StickerPackRepository;
import net.samvankooten.finnstickers.utils.Util;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class UpdateWorker extends Worker {
    private static final String TAG = "UpdateWorker";
    public UpdateWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }
    
    @NonNull
    @Override
    public Result doWork() {
        SharedPreferences prefs = Util.getUserPrefs(getApplicationContext());
        if (!prefs.getBoolean(getApplicationContext().getString(R.string.settings_check_in_background_key), true))
            return Result.success();
        
        if (!Util.connectedToInternet(getApplicationContext()))
            return Result.retry();
        
        StickerPackRepository.AllPacksResult packs =
                StickerPackRepository.getInstalledAndAvailablePacks(getApplicationContext());
        if (packs.exception != null) {
            Log.e(TAG, "Error in packlist download", packs.exception);
            return Result.retry();
        }
        
        if (!packs.networkSucceeded) {
            Log.e(TAG, "Error downloading pack info");
            return Result.retry();
        }
        
        Util.checkAndNotifyForNewPacks(getApplicationContext(), packs.list);
        
        for (StickerPack pack : packs.list) {
            if (isStopped())
                return Result.retry();
            if (pack.getStatus() == StickerPack.Status.UPDATABLE) {
                if (prefs.getBoolean(getApplicationContext().getString(R.string.settings_update_in_background_key), true))
                    pack.update(getApplicationContext(), null, false);
                else
                    NotificationUtils.showUpdateAvailNotif(getApplicationContext(), pack);
            }
        }
        
        return Result.success();
    }
}
