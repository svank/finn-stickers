package net.samvankooten.finnstickers.misc_classes;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.util.Log;

import com.google.firebase.FirebaseApp;

import net.samvankooten.finnstickers.StickerPack;
import net.samvankooten.finnstickers.utils.StickerPackProcessor;
import net.samvankooten.finnstickers.utils.StickerPackRepository;
import net.samvankooten.finnstickers.utils.Util;

import java.util.List;

/**
 * A JobScheduler Job that ensures downloaded stickers are in the Firebase Index.
 * This isn't involved with sticker installation---that registers stickers itself. But after a
 * restore of app data, the already-downloaded stickers need to be registered. And sometimes
 * Firebase asks us to update the index, which the docs say can happen if the index is corrupted.
 * So here we scan for downloaded stickers and register them with Firebase. Firebase appears to
 * de-duplicate index entries, so we don't have to worry about that.
 */
public class ReindexJob extends JobService {
    private static final String TAG = "ReindexJob";
    
    @Override public boolean onStartJob(JobParameters params) {
        Context context = getApplicationContext();
        Util.performNeededMigrations(context);
        FirebaseApp.initializeApp(context);
        
        doReindex(context);
        return false;
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
    
    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}