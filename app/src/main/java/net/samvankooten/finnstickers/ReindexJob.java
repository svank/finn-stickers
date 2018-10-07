package net.samvankooten.finnstickers;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.util.Log;

import com.google.firebase.FirebaseApp;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

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
        FirebaseApp.initializeApp(context);
        // Scan the data dir for directories (containing sticker packs)
        for (File directory : context.getFilesDir().listFiles()) {
            if (!directory.isDirectory())
                continue;
            // If we have a directory, it should have a data.json inside
            // (that's a copy of the sticker-listing json file from the server)
            File jsonFile = new File(directory, "data.json");
            if (!jsonFile.exists())
                continue;
        
            // Read the sticker-list file and the pack data file
            String contents;
            String packJSON;
            try {
                contents = Util.readTextFile(jsonFile);
                // Infer the pack data JSON filename
                // i.e. Finn.json, alongside Finn/data.json
                packJSON = Util.readTextFile(new File(directory.toString() + ".json"));
            } catch (IOException e) {
                Log.e(TAG, "Error reading json file", e);
                continue;
            }
        
            StickerPack pack;
            StickerProcessor.ParsedStickerList stickers;
            StickerProcessor processor;
            try {
                // Parse the files, get a StickerPack and a List of Stickers
                pack = new StickerPack(new JSONObject(packJSON));
                processor = new StickerProcessor(pack, context);
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
