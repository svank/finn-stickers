package net.samvankooten.finnstickers.misc_classes;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.util.Log;

import com.google.firebase.FirebaseApp;

import net.samvankooten.finnstickers.StickerPack;
import net.samvankooten.finnstickers.utils.StickerPackProcessor;
import net.samvankooten.finnstickers.utils.Util;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
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
        
        List<StickerPack> packs;
        try {
            packs = Util.getInstalledPacks(context);
        } catch (JSONException e) {
            return false;
        }
        for (StickerPack pack : packs) {
            File directory = new File(context.getFilesDir(), pack.getPackname());
            
            // Double-check that everything exists
            if (!directory.isDirectory())
                continue;
            File jsonFile = new File(directory, pack.getDatafile());
            if (!jsonFile.exists())
                continue;
        
            // Read the sticker-list file and the pack data file
            String contents;
            String packJSON;
            try {
                contents = Util.readTextFile(jsonFile);
            } catch (IOException e) {
                Log.e(TAG, "Error reading json file", e);
                continue;
            }
        
            StickerPackProcessor.ParsedStickerList stickers;
            StickerPackProcessor processor;
            try {
                // Parse the files, get a StickerPack and a List of Stickers
                processor = new StickerPackProcessor(pack, context);
                stickers = processor.parseStickerList(contents);
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing json file", e);
                continue;
            }
        
            // Re-insert those stickers into the Firebase index, as requested
            processor.registerStickers(stickers.list);
        }
        return false;
    }
    
    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}