package net.samvankooten.finnstickers;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

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

public class StickerProcessor {
    private static final String TAG = "StickerProcessor";

    private StickerPack pack;
    private Context context = null;
    public static final FirebaseAppIndex index = FirebaseAppIndex.getInstance();


    public StickerProcessor(StickerPack pack, Context context){
        this.pack = pack;
        this.context = context;
    }

    public static void clearStickers(Context context, StickerPack pack) {
        // Remove stickers from Firebase index.
        List<String> urls = pack.getStickerURLs();
        
        // These calls returnt ask objects, so we probably could respond to their result
        // if we wanted
        index.remove(urls.toArray(new String[urls.size()]));
        index.remove(pack.getURL());
        
        Util.delete(pack.buildFile(context.getFilesDir(), ""));
        Util.delete(new File(pack.getJsonSavePath()));
        
        pack.clearStickerData();
    }
    
    public List process(Util.DownloadResult in) throws IOException {
        // Given a sticker pack data file, downloads stickers and registers them with Firebase.
    
        File rootPath = pack.buildFile(context.getFilesDir(), "");
        if (rootPath.exists()) {
            Log.e(TAG, "Attempting to download a sticker pack that appears to exists already");
            Log.e(TAG, "Attempting to remove traces of existing pack");
            Util.delete(rootPath);
            Log.e(TAG, "Continuing");
        }
        
        ParsedStickerList result;
        try {
            result = parseStickerList(in);
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing sticker list JSON", e);
            return null;
        }
        registerStickers(result);
        return result.list;
    }
    
    public class ParsedStickerList {
        public List<Sticker> list;
        public String packIconFilename;
        public ParsedStickerList(List<Sticker> list, String packIconFilename) {
            this.list = list;
            this.packIconFilename = packIconFilename;
        }
    }
    
    protected List<Sticker> getStickerList(Util.DownloadResult in) throws JSONException {
        return parseStickerList(in).list;
    }
    
    private ParsedStickerList parseStickerList(Util.DownloadResult in) throws JSONException {
        JSONObject data = new JSONObject(in.readString(20000));
    
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
        
    public void registerStickers(ParsedStickerList input) throws IOException {
        final List<Sticker> stickers = input.list;
        String packIconFilename = input.packIconFilename;
    
        URL url = new URL(pack.buildURLString(packIconFilename));
        File destination = pack.buildFile(context.getFilesDir(), packIconFilename);
        Util.downloadFile(url, destination);
        
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
                    pack.clearUpdateNotif();
                }
            });
        } catch (FirebaseAppIndexingInvalidArgumentException e){
            Log.e(TAG, e.toString());
        }
    }
}
