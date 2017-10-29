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
import java.net.MalformedURLException;
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

    // We don't use namespaces
    private static final String ns = null;

    private String urlBase;
    private StickerPack pack;
    private Context context = null;
    public static final FirebaseAppIndex index = FirebaseAppIndex.getInstance();


    public StickerProcessor(StickerPack pack, Context context){
        URL url;
        this.pack = pack;
        this.context = context;
        try {
            url = new URL(pack.buildURLString(pack.getDatafile()));
        } catch (MalformedURLException e) {
            // This shouldn't happen, since we've already downloaded from this URL
            Log.e(TAG, "Malformed URL", e);
            return;
        }
        String host = url.getHost();
        String path = url.getPath();
        String protocol = url.getProtocol();
        String dirs = path.substring(0, path.lastIndexOf("/"));
        try {
            urlBase = new URL(protocol, host, dirs).toString() + '/';
        } catch (MalformedURLException e){
            Log.e(TAG, "Unexpected error parsing URL base", e);
        }
    }

    public static void clearStickers(Context context, StickerPack pack) {
        // Remove stickers from Firebase index.
        List<String> urls = pack.getStickerURLs();
        Task<Void> task = index.remove(urls.toArray(new String[urls.size()]));
        task = index.remove(pack.getURL());
        
        delete(pack.buildFile(context.getFilesDir(), ""));
        delete(new File(pack.getJsonSavePath()));
        
        pack.clearStickerData();
    }

    private static void delete(File file) {
        if (file.isDirectory())
            for (File child : file.listFiles())
                delete(child);

        file.delete();
    }

    public List process(Util.DownloadResult in) throws IOException {
        // Given a sticker pack data file, downloads stickers and registers them with Firebase.
    
        File rootPath = pack.buildFile(context.getFilesDir(), "");
        if (rootPath.exists()) {
            // TODO: Test this check
            Log.e(TAG, "Attempting to download a sticker pack that appears to exists already");
            return null;
        }
        
        ParsedStickerList result = null;
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
        public List list;
        public String packIconFilename;
        public ParsedStickerList(List list, String packIconFilename) {
            this.list = list;
            this.packIconFilename = packIconFilename;
        }
    }
    
    private ParsedStickerList parseStickerList(Util.DownloadResult in) throws JSONException {
        JSONObject data = new JSONObject(in.readString(20000));
    
        List<String> defaultKWs = new LinkedList<String>();
        JSONArray defaultKWsData = data.getJSONArray("default_keywords");
        for (int i=0; i<defaultKWsData.length(); i++) {
            defaultKWs.add(defaultKWsData.getString(i));
        }
        
        JSONArray stickers = data.getJSONArray("stickers");
        Log.d(TAG, "There are " + stickers.length() + "stickers");
        List list = new LinkedList();
        for (int i=0; i<stickers.length(); i++) {
            Sticker sticker = new Sticker(stickers.getJSONObject(i));
            sticker.addKeywords(defaultKWs);
            list.add(sticker);
        }
        
        Log.d(TAG, "FINNished parsing sticker JSON");
        return new ParsedStickerList(list, data.getString("pack_icon"));
    }
        
    public void registerStickers(ParsedStickerList input) throws IOException {
        final List stickers = input.list;
        String packIconFilename = input.packIconFilename;
    
        URL url = new URL(pack.buildURLString(packIconFilename));
        File destination = pack.buildFile(context.getFilesDir(), packIconFilename);
        Util.downloadFile(url, destination);
        
        Indexable[] indexables = new Indexable[stickers.size() + 1];
        for(int i = 0; i < stickers.size(); i++) {
            Sticker sticker = (Sticker) stickers.get(i);
            sticker.setPackName(pack.getPackname());
            sticker.download(pack, context.getFilesDir());
            indexables[i] = sticker.getIndexable();
            Log.d(TAG, "Handled sticker " + sticker.getPath());
        }
        
        pack.writeToFile(String.format("%s/%s.json",
                context.getFilesDir(), pack.getPackname()));

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
                }
            });

            task.addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Log.e(TAG, "Failed to add Pack to index", e);
                }
            });
        } catch (FirebaseAppIndexingInvalidArgumentException e){
            Log.e(TAG, e.toString());
        }
    }
}
