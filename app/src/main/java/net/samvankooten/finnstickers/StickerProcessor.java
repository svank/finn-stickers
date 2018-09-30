package net.samvankooten.finnstickers;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.appindexing.FirebaseAppIndex;
import com.google.firebase.appindexing.FirebaseAppIndexingInvalidArgumentException;
import com.google.firebase.appindexing.Indexable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by sam on 9/23/17.
 *
 * Basically the code at https://developer.android.com/training/basics/network-ops/xml.html
 */

class StickerProcessor {
    private static final String TAG = "StickerProcessor";

    private StickerPack pack;
    private Context context;
    private static final FirebaseAppIndex index = FirebaseAppIndex.getInstance();


    StickerProcessor(StickerPack pack, Context context){
        this.pack = pack;
        this.context = context;
    }
    
    /**
     * Deletes and unregisters an installed StickerPack
     */
    static void clearStickers(Context context, StickerPack pack) {
        // Remove stickers from Firebase index.
        List<String> urls = pack.getStickerURLs();
        
        // These calls return Task objects, so we probably could respond to their result
        // if we wanted
        index.remove(urls.toArray(new String[0]));
        index.remove(pack.getURL());
        
        try {
            // Put the pack icon back into the cache directory, so it's still available
            // after deletion
            File dest = pack.generateCachedIconPath(context.getCacheDir());
            Util.copy(pack.getIconfile(), dest);
            pack.setIconfile(dest);
            Util.delete(pack.buildFile(context.getFilesDir(), ""));
            Util.delete(new File(pack.getJsonSavePath()));
        } catch (IOException e) {
            Log.e(TAG, e.toString());
            Toast.makeText(context, "Error deleting files", Toast.LENGTH_LONG).show();
        }
        
        pack.uninstalledPackSetup();
    }
    
    /**
     * Given a sticker pack data file, downloads stickers and registers them with Firebase.
     * @param packData Downloaded contents of pack data file
     * @return A List of the installed Stickers
     */
    List<Sticker> process(Util.DownloadResult packData) throws IOException {
        File rootPath = pack.buildFile(context.getFilesDir(), "");
        if (rootPath.exists()) {
            Log.e(TAG, "Attempting to download a sticker pack that appears to exists already");
            Log.e(TAG, "Attempting to remove traces of existing pack");
            Util.delete(rootPath);
            Log.e(TAG, "Continuing");
        }
        
        ParsedStickerList result;
        try {
            result = parseStickerList(packData);
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing sticker list JSON", e);
            return null;
        }
        downloadAndRegisterStickers(result);
        return result.list;
    }
    
    class ParsedStickerList {
        List<Sticker> list;
        String packIconFilename;
        ParsedStickerList(List<Sticker> list, String packIconFilename) {
            this.list = list;
            this.packIconFilename = packIconFilename;
        }
    }
    
    List<Sticker> getStickerList(Util.DownloadResult in) throws JSONException {
        return parseStickerList(in).list;
    }
    
    private ParsedStickerList parseStickerList(Util.DownloadResult in) throws JSONException {
        JSONObject data = new JSONObject(in.readString());
    
        List<String> defaultKWs = new LinkedList<>();
        JSONArray defaultKWsData = data.getJSONArray("default_keywords");
        for (int i=0; i<defaultKWsData.length(); i++) {
            defaultKWs.add(defaultKWsData.getString(i));
        }
        
        JSONArray stickers = data.getJSONArray("stickers");
        List<Sticker> list = new LinkedList<>();
        for (int i=0; i<stickers.length(); i++) {
            Sticker sticker = new Sticker(stickers.getJSONObject(i));
            sticker.addKeywords(defaultKWs);
            list.add(sticker);
        }
        
        return new ParsedStickerList(list, data.getString("pack_icon"));
    }
        
    void downloadAndRegisterStickers(ParsedStickerList input) throws IOException {
        final List<Sticker> stickers = input.list;
        String packIconFilename = input.packIconFilename;
        
        URL url = new URL(pack.buildURLString(packIconFilename));
        File destination = pack.buildFile(context.getFilesDir(), packIconFilename);
        // If we were just viewing the pack list, the pack's icon has been downloaded to cache.
        // So try just copying it. Otherwise, download the icon.
        // (I'm getting an error if I try to move/rename the file, so I'm copying (reading
        // and then re-writing) instead)
        if (pack.getIconfile() != null && pack.getIconfile().exists()) {
            Util.copy(pack.getIconfile(), destination);
            Util.delete(pack.getIconfile());
        } else
            Util.downloadFile(url, destination);
        
        pack.setIconfile(destination);
        
        Indexable[] indexables = new Indexable[stickers.size() + 1];
        for(int i = 0; i < stickers.size(); i++) {
            Sticker sticker = stickers.get(i);
            sticker.setPackName(pack.getPackname());
            sticker.downloadToFile(pack, context.getFilesDir());
            indexables[i] = sticker.getIndexable();
        }
        
        pack.writeToFile(pack.buildJSONPath(context.getFilesDir()));

        try {
            Indexable stickerPack = new Indexable.Builder("StickerPack")
                    .setName(pack.getPackname())
                    .setImage(pack.buildURI(packIconFilename).toString())
                    .setDescription(pack.getDescription())
                    .setUrl(pack.getURL())
                    .put("hasSticker", indexables)
                    .build();

            indexables[indexables.length - 1] = stickerPack;
            
            Task<Void> task = index.update(indexables);
            
            task.addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
//                    Log.v(TAG, "Successfully added Pack to index");
                    pack.absorbFirebaseURLs(stickers);
                    pack.updateJSONFile();
                    pack.showUpdateNotif();
                }
            });

            task.addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Log.e(TAG, "Failed to add Pack to index", e);
                    pack.clearNotifData();
                }
            });
        } catch (FirebaseAppIndexingInvalidArgumentException e){
            Log.e(TAG, e.toString());
        }
    }
}
