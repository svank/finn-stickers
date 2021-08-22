package net.samvankooten.finnstickers.misc_classes;

import android.content.Context;
import android.util.Log;

import com.google.firebase.FirebaseApp;

import net.samvankooten.finnstickers.StickerPack;
import net.samvankooten.finnstickers.utils.StickerPackProcessor;
import net.samvankooten.finnstickers.utils.StickerPackRepository;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * A WorkManager Worker that ensures downloaded stickers are in the Firebase Index.
 * This isn't involved with sticker installation---that registers stickers itself. But after a
 * restore of app data, the already-downloaded stickers need to be registered. And sometimes
 * Firebase asks us to update the index, which the docs say can happen if the index is corrupted.
 * So here we scan for downloaded stickers and register them with Firebase. Firebase appears to
 * de-duplicate index entries, so we don't have to worry about that.
 */
public class ReindexWorker extends Worker {
    private static final String TAG = "ReindexWorker";
    public ReindexWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }
    
    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        FirebaseApp.initializeApp(context);
        
        doReindex(context);
        return Result.success();
    }
    
    public static void doReindex(Context context) {
        List<StickerPack> packs;
        packs = StickerPackRepository.getInstalledPacks(context);
        if (packs == null) {
            Log.e(TAG, "Error loading packs");
            return;
        }
        
        for (StickerPack pack : packs) {
            StickerPackProcessor processor = new StickerPackProcessor(pack, context);
            
            // Re-insert those stickers into the Firebase index, as requested
            processor.registerStickers(pack.getStickers());
        }
    }
    public static void schedule(Context context) {
        var constraints = new Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiredNetworkType(NetworkType.UNMETERED);
        var request = new OneTimeWorkRequest.Builder(ReindexWorker.class)
                .setConstraints(constraints.build()).build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(
                TAG, ExistingWorkPolicy.KEEP, request);
    }
}
