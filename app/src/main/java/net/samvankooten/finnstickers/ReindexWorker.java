package net.samvankooten.finnstickers;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

import androidx.work.Worker;

public class ReindexWorker extends Worker {
    private static final String TAG = "ReindexWorker";
    
    /**
     * Per https://developers.google.com/android/reference/com/google/firebase/appindexing/FirebaseAppIndex,
     * we're supposed to re-insert all our indexables into the Firebase index whenever it asks.
     * It looks like it won't allow duplicates, so we can just re-insert everything without checking.
     * This worker will, in the background, find all installed sticker packs, get their stickers,
     * and insert then into the index.
     */
    @NonNull
    @Override
    public Worker.Result doWork() {
        Context context = getApplicationContext();
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
                // i.e. Finn.json, alongisde Finn/data.json
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
        return Result.SUCCESS;
    }
}
