package net.samvankooten.finnstickers.utils;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.tasks.Task;
import com.google.firebase.appindexing.FirebaseAppIndex;
import com.google.firebase.appindexing.FirebaseAppIndexingInvalidArgumentException;
import com.google.firebase.appindexing.Indexable;

import net.samvankooten.finnstickers.Sticker;
import net.samvankooten.finnstickers.StickerPack;
import net.samvankooten.finnstickers.StickerProvider;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
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

public class StickerPackProcessor {
    private static final String TAG = "StickerPackProcessor";

    private final StickerPack pack;
    private final Context context;
    private static final FirebaseAppIndex index = FirebaseAppIndex.getInstance();
    private volatile Exception downloadException;
    StickerProvider provider;
    
    public StickerPackProcessor(StickerPack pack, Context context){
        this.pack = pack;
        this.context = context;
        provider = new StickerProvider();
        provider.setRootDir(context);
    }
    
    /**
     * Deletes and unregisters an installed StickerPack
     */
    public void uninstallPack() {
        if (pack.getStatus() != StickerPack.Status.INSTALLED
            && pack.getStatus() != StickerPack.Status.UPDATEABLE)
            return;
        
        // Remove stickers from Firebase index.
        List<String> urls = pack.getStickerFirebaseURLs();
        index.remove(urls.toArray(new String[0]));
        index.remove(pack.getFirebaseURL());
        
        // Delete the pack's files
        try {
            Util.delete(pack.buildFile(context.getFilesDir(), ""));
        } catch (IOException e) {
            Log.e(TAG, e.toString());
            Toast.makeText(context, "Error deleting files", Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * Given a sticker pack data file, downloads stickers and registers them with Firebase.
     * @param jsonData Downloaded contents of pack data file
     */
    public void process(String jsonData, boolean showNotif) throws Exception {
        File rootPath = pack.buildFile(context.getFilesDir(), "");
        if (rootPath.exists()) {
            Log.e(TAG, "Attempting to download a sticker pack that appears to exists already");
            Log.e(TAG, "Attempting to remove traces of existing pack");
            Util.delete(rootPath);
            Log.e(TAG, "Continuing");
        }
        
        ParsedStickerList result;
        try {
            result = parseStickerList(jsonData);
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing sticker list JSON", e);
            return;
        }
        downloadAndRegisterStickers(result, showNotif);
    }
    
    public class ParsedStickerList {
        public final List<Sticker> list;
        public final String packIconFilename;
        public ParsedStickerList(List<Sticker> list, String packIconFilename) {
            this.list = list;
            this.packIconFilename = packIconFilename;
        }
    }
    
    public List<Sticker> getStickerList(Util.DownloadResult in) throws JSONException, IOException {
        return parseStickerList(in.readString()).list;
    }
    
    public ParsedStickerList parseStickerList(String downloadedData) throws JSONException {
        JSONObject data = new JSONObject(downloadedData);
    
        List<String> defaultKWs = new LinkedList<>();
        JSONArray defaultKWsData = data.getJSONArray("default_keywords");
        for (int i=0; i<defaultKWsData.length(); i++)
            defaultKWs.add(defaultKWsData.getString(i));
        
        JSONArray stickers = data.getJSONArray("stickers");
        List<Sticker> list = new LinkedList<>();
        for (int i=0; i<stickers.length(); i++) {
            Sticker sticker = new Sticker(stickers.getJSONObject(i), pack.buildURLString(""));
            sticker.addKeywords(defaultKWs);
            sticker.setPackName(pack.getPackname());
            list.add(sticker);
        }
        
        return new ParsedStickerList(list, data.getString("pack_icon"));
    }
    
    public void downloadAndRegisterStickers(ParsedStickerList input, boolean showNotif) throws Exception {
        final List<Sticker> stickers = input.list;
        String packIconFilename = input.packIconFilename;
        
        // Download the pack icon
        URL url = new URL(pack.buildURLString(packIconFilename));
        File destination = pack.buildFile(context.getFilesDir(), packIconFilename);
        Util.downloadFile(url, destination);
        
        pack.setIconLocation(provider.fileToUri(destination).toString());
        
        // Queue up downloads to run asynchronously
        final CountDownLatch countdown = new CountDownLatch(stickers.size());
        
        for(int i = 0; i < stickers.size(); i++) {
            final Sticker sticker = stickers.get(i);
            final File sDestination = pack.buildFile(context.getFilesDir(), sticker.getRelativePath());
            final URL source = new URL(pack.buildURLString(sticker.getRelativePath()));
            
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
                    if (!response.isSuccessful()) {
                        Log.e(TAG, "Unsuccessful download " + response.toString());
                        downloadException = new Exception("Unsuccessful download");
                        countdown.countDown();
                        return;
                    }
                    
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
        
        Task<Void> task = registerStickers(stickers);
        if (task != null) {
            task.addOnFailureListener(e -> Log.e(TAG, "Failed to add Pack to index", e));
        }
    
        pack.absorbStickerData(stickers, context);
        if (showNotif && pack.getUpdatedURIs().size() > 0)
            NotificationUtils.showUpdateNotif(context, pack);
    }
    
    public Task<Void> registerStickers(List<Sticker> stickers) {
        Indexable[] indexables = new Indexable[stickers.size() + 1];
        for(int i = 0; i < stickers.size(); i++)
            indexables[i] = stickers.get(i).getIndexable();
    
        try {
            Indexable stickerPack = new Indexable.Builder("StickerPack")
                    .setName(pack.getPackname())
                    .setImage(pack.getIconLocation())
                    .setDescription(pack.getDescription())
                    .setUrl(pack.getFirebaseURL())
                    .put("hasSticker", indexables)
                    .build();

            indexables[indexables.length - 1] = stickerPack;
    
            return index.update(indexables);
        } catch (FirebaseAppIndexingInvalidArgumentException e){
            Log.e(TAG, e.toString());
            return null;
        }
    }
}
