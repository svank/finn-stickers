package net.samvankooten.finnstickers;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.tasks.Task;
import com.google.firebase.appindexing.FirebaseAppIndex;
import com.google.firebase.appindexing.FirebaseAppIndexingInvalidArgumentException;
import com.google.firebase.appindexing.Indexable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

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
    private volatile Exception downloadException;


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
    List<Sticker> process(Util.DownloadResult packData) throws Exception {
        File rootPath = pack.buildFile(context.getFilesDir(), "");
        if (rootPath.exists()) {
            Log.e(TAG, "Attempting to download a sticker pack that appears to exists already");
            Log.e(TAG, "Attempting to remove traces of existing pack");
            Util.delete(rootPath);
            Log.e(TAG, "Continuing");
        }
        
        ParsedStickerList result;
        String jsonData = packData.readString();
        try {
            result = parseStickerList(jsonData);
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing sticker list JSON", e);
            return null;
        }
        downloadAndRegisterStickers(result);
        
        // Save the original JSON file
        File file = pack.buildFile(context.getFilesDir(), pack.getDatafile());
        file.createNewFile();
        FileOutputStream out = new FileOutputStream(file);
        out.write(jsonData.getBytes());
        out.close();
        
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
    
    List<Sticker> getStickerList(Util.DownloadResult in) throws JSONException, IOException {
        return parseStickerList(in.readString()).list;
    }
    
    ParsedStickerList parseStickerList(String downloadedData) throws JSONException {
        JSONObject data = new JSONObject(downloadedData);
    
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
            sticker.setPackName(pack.getPackname());
            list.add(sticker);
        }
        
        return new ParsedStickerList(list, data.getString("pack_icon"));
    }
        
    void downloadAndRegisterStickers(ParsedStickerList input) throws Exception {
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
    
        // Queue up downloads to run asynchronously
        final CountDownLatch countdown = new CountDownLatch(stickers.size());
        
        for(int i = 0; i < stickers.size(); i++) {
            final Sticker sticker = stickers.get(i);
            final File sDestination = pack.buildFile(context.getFilesDir(), sticker.getPath());
            final URL source = new URL(pack.buildURLString(sticker.getPath()));
            
            Request request = new Request.Builder()
                    .url(source)
                    .build();
            Util.httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, e.toString());
                    downloadException = e;
                    countdown.countDown();
                }
    
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    InputStream input = response.body().byteStream();
                    Util.saveStreamToFile(input, sDestination);
                    countdown.countDown();
                }
            });
        }
        
        countdown.await();
        
        // Slightly clumsy way to pass exceptions from the download back to the main thread
        if (downloadException != null) {
            Exception e = downloadException;
            downloadException = null;
            throw e;
        }
        
        pack.writeToFile(pack.buildJSONPath(context.getFilesDir()));
        
        Task<Void> task = registerStickers(stickers);
        
        if (task != null) {
            task.addOnSuccessListener(aVoid -> {
                pack.absorbFirebaseURLs(stickers);
                pack.updateJSONFile();
                pack.showUpdateNotif();
            });
    
            task.addOnFailureListener(e -> {
                Log.e(TAG, "Failed to add Pack to index", e);
                pack.clearNotifData();
            });
        }
    }
    
    Task<Void> registerStickers(List<Sticker> stickers) {
        Indexable[] indexables = new Indexable[stickers.size() + 1];
        for(int i = 0; i < stickers.size(); i++) {
            indexables[i] = stickers.get(i).getIndexable();
        }
    
        StickerProvider provider = new StickerProvider();
        provider.setRootDir(context);
        try {
            Indexable stickerPack = new Indexable.Builder("StickerPack")
                    .setName(pack.getPackname())
                    .setImage(provider.fileToUri(pack.getIconfile()).toString())
                    .setDescription(pack.getDescription())
                    .setUrl(pack.getURL())
                    .put("hasSticker", indexables)
                    .build();

            indexables[indexables.length - 1] = stickerPack;
    
            Task<Void> task = index.update(indexables);
            
            return task;
        } catch (FirebaseAppIndexingInvalidArgumentException e){
            Log.e(TAG, e.toString());
            return null;
        }
    }
}
