package net.samvankooten.finnstickers.utils;

import android.app.Notification;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.util.Log;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.signature.ObjectKey;

import net.samvankooten.finnstickers.Sticker;
import net.samvankooten.finnstickers.StickerPack;
import net.samvankooten.finnstickers.StickerProvider;
import net.samvankooten.finnstickers.misc_classes.GlideApp;
import net.samvankooten.finnstickers.misc_classes.GlideRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import androidx.annotation.AnyRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Created by sam on 10/22/17.
 */

public class Util {
    public static final String CONTENT_URI_ROOT =
            String.format("content://%s/", StickerProvider.class.getName());
    
    public static final String URL_BASE = "https://samvankooten.net/finn_stickers/v4/";
    public static final String PACK_LIST_URL = URL_BASE + "sticker_pack_list.json";
    
    private static final String PREFS_NAME = "net.samvankooten.finnstickers.prefs";
    public static final String KNOWN_PACKS = "known_packs";
    public static final String INSTALLED_PACKS = "installed_packs";
    public static final String STICKER_PACK_DATA_PREFIX = "json_data_for_pack_";
    public static final String HAS_RUN = "has_run";
    
    private static final String TAG = "Util";
    public static final OkHttpClient httpClient = new OkHttpClient.Builder()
                                        .connectTimeout(15, TimeUnit.SECONDS)
                                        .readTimeout(15, TimeUnit.SECONDS)
                                        .writeTimeout(15, TimeUnit.SECONDS)
                                        .build();
    
    /**
     * Recursively deletes a file or directory.
     * @param file Path to be deleted
     */
    public static void delete(File file) throws IOException{
        if (!file.exists()) {
            Log.w(TAG, "delete: File doesn't exist: " + file.toString());
            return;
        }
        
        if (file.isDirectory())
            for (File child : file.listFiles())
                delete(child);

        if (file.delete())
            return;
        throw new IOException("Error deleting " + file.toString());
    }
    
    public static void copy(File src, File dest) throws IOException {
        FileChannel inChannel = new FileInputStream(src).getChannel();
        if (!dest.exists()) {
            // Ensure the directory path exists
            File dirPath = dest.getParentFile();
            if (dirPath != null) {
                dirPath.mkdirs();
            }
            dest.createNewFile();
        }
        FileChannel outChannel = new FileOutputStream(dest).getChannel();
        try
        {
            inChannel.transferTo(0, inChannel.size(), outChannel);
        }
        finally
        {
            if (inChannel != null)
                inChannel.close();
            if (outChannel != null)
                outChannel.close();
        }
    }
    
    public static String resourceToUri(@NonNull Context context,
                                    @AnyRes int resId) {
        // Coming from https://stackoverflow.com/questions/6602417/get-the-uri-of-an-image-stored-in-drawable
        Resources res = context.getResources();
        Uri resUri = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE +
                               "://" + res.getResourcePackageName(resId) +
                               "/" + res.getResourceTypeName(resId) +
                               "/" + res.getResourceEntryName(resId));
        return resUri.toString();
    }
    
    public static class DownloadResult{
        public final Response response;
        public DownloadResult(Response response) {
            this.response = response;
        }
    
        /**
         * Releases resources related to the HTTP connection
         */
        public void close() {
            if (response != null)
                response.close();
        }
    
        /**
         * Returns the downloaded data as a String
         */
        @NonNull
        public String readString() throws IOException {
            return response.body().string();
        }
    
        /**
         * Returns the downloaded data as an InputStream
         */
        @NonNull
        public InputStream readStream() {
            return response.body().byteStream();
        }
    }
    
    /**
     * Downloads data from a given URL. Returns a DownloadResult---use its readString method
     * or its stream attribute to access the data, and be sure to call
     * result.close() after use.
     * @param url The URL to download from
     * @return a DownloadResult with a readString method
     */
    @NonNull
    public static DownloadResult downloadFromUrl(@NonNull URL url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .build();
        Response response = httpClient.newCall(request).execute();
        if (!response.isSuccessful())
            throw new IOException("HTTP error code: " + response);

        return new DownloadResult(response);
    }
    
    /**
     * Downloads a URL and saves its contents to a file
     * @param url URL to download
     * @param destination Path at which to save the data
     */
    public static void downloadFile(@NonNull URL url, @NonNull File destination) throws IOException {
        DownloadResult result = null;
        try {
            result = downloadFromUrl(url);
            InputStream input = result.readStream();
            saveStreamToFile(input, destination);
        } finally {
            if (result != null)
                result.close();
        }
    }
    
    /**
     * Writes the contents of an InputStream to a file, creating the file and its parent directories
     * if necessary.
     */
    public static void saveStreamToFile(@NonNull InputStream stream, @NonNull File destination) throws IOException{
        File dirPath = destination.getParentFile();
        
        if (dirPath != null)
            dirPath.mkdirs();
        
        try (OutputStream output = new FileOutputStream(destination)) {
            // Ensure the directory path exists
            // coming from https://stackoverflow.com/questions/3028306/download-a-file-with-android-and-showing-the-progress-in-a-progressdialog
        
            byte data[] = new byte[4096];
            int count;
            while ((count = stream.read(data)) != -1) {
                output.write(data, 0, count);
            }
        }
    }
    
    /**
     * Gets the "path" component of a URL---everything up to the last slash.
     * That is,
     * samvankooten.net/finn_stickers/cool_sticker.jpg -> samvankooten.net/finn_stickers/
     * samvankooten.net/finn_stickers/a_dir -> samvankooten.net/finn_stickers/
     */
    @Nullable
    public static String getURLPath(@NonNull URL url) {
        String host = url.getHost();
        String path = url.getPath();
        String protocol = url.getProtocol();
        int lastSlash = path.lastIndexOf("/");
        String dirs = "";
        if (lastSlash > 0)
            dirs = path.substring(0, lastSlash);
        try {
            return new URL(protocol, host, dirs).toString() + '/';
        } catch (MalformedURLException e){
            Log.e(TAG, "Unexpected error parsing URL base", e);
            return null;
        }
    }
    
    /**
     * Given a File, returns its contents as a String
     */
    @NonNull
    public static String readTextFile(@NonNull File file) throws IOException {
        // This is the easiest way I could find to read a text file in Android/Java.
        // There really ought to be a better way!
        StringBuilder data = new StringBuilder();
        BufferedReader br = new BufferedReader(new FileReader(file));
        String line;
        while ((line = br.readLine()) != null) {
            data.append(line);
            data.append('\n');
        }
        br.close();
        return data.toString();
    }
    
    public static boolean stringIsURL(String string) {
        return string.substring(0, 4).toLowerCase().equals("http");
    }
    
    /**
     * Enable caching for remote Glide loads---see CustomAppGlideModule
     */
    public static void enableGlideCacheIfRemote(GlideRequest request, String url, int extraKey) {
        if (!stringIsURL(url))
            return;
    
        request.signature(new ObjectKey(extraKey)).diskCacheStrategy(DiskCacheStrategy.AUTOMATIC);
    }
    
    /**
     * Checks whether the app has ever been opened
     */
    public static boolean checkIfEverOpened(@NonNull Context context) {
        File dir = context.getFilesDir();
        File f1 = new File(dir, "tongue"); // App opened as V1
        File f2 = new File(dir, "known_packs.txt"); // App opened as V2
        boolean has_run = getPrefs(context).getBoolean(HAS_RUN, false);
        return f1.exists() || f2.exists() || has_run;
    }
    
    /**
     * Returns true if we're connected to the Internet, false otherwise
     */
    public static boolean connectedToInternet(Context context) {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected() &&
                (networkInfo.getType() == ConnectivityManager.TYPE_WIFI
                        || networkInfo.getType() == ConnectivityManager.TYPE_MOBILE);
    }
    
    /**
     * Generates a list of installed stickers packs
     * @param context App context
     */
    public static List<StickerPack> getInstalledPacks(Context context) throws JSONException {
        LinkedList<StickerPack> installedPacks = new LinkedList<>();
        
        Set<String> installedPackNames = getPrefs(context).getStringSet(INSTALLED_PACKS, null);
        if (installedPackNames == null)
            return installedPacks;
        
        SharedPreferences prefs = getPrefs(context);
        for (String name : installedPackNames) {
            String jsonData = prefs.getString(STICKER_PACK_DATA_PREFIX + name, "");
            try {
                JSONObject obj = new JSONObject(jsonData);
                StickerPack pack = new StickerPack(obj);
                installedPacks.add(pack);
            } catch (JSONException e) {
                Log.e(TAG, "JSON Error on pack " + name, e);
                throw e;
            }
        }
        
        Collections.sort(installedPacks);
        
        return installedPacks;
    }
    
    /**
     * Generates a complete list of installed & available sticker packs
     * @param url Location of available packs list
     * @return Array of available & installed StickerPacks
     */
    public static AllPacksResult getInstalledAndAvailablePacks(URL url, Context context) {
        // Find installed packs
        List<StickerPack> list;
        try {
            list = getInstalledPacks(context);
        } catch (Exception e) {
            return new AllPacksResult(null, false, e);
        }
        
        if (!connectedToInternet(context))
            return new AllPacksResult(list, false, new Exception("No internet connection"));
        
        DownloadResult result;
        try {
            // Download the list of available packs
            result = downloadFromUrl(url);
        } catch (IOException e) {
            return new AllPacksResult(list, false, e);
        }
        
        JSONArray packs;
        try {
            // Parse the list of packs out of the JSON data
            JSONObject json = new JSONObject(result.readString());
            packs = json.getJSONArray("packs");
        } catch (Exception e) {
            return new AllPacksResult(list, false, e);
        } finally {
            result.close();
        }
        
        // Parse each StickerPack JSON object and download icons
        try {
            for (int i = 0; i < packs.length(); i++) {
                JSONObject packData = packs.getJSONObject(i);
                StickerPack availablePack = new StickerPack(packData, getURLPath(url));
        
                // Is this pack already in the list? i.e. is this an installed pack?
                boolean add = true;
                for (StickerPack installedPack : list) {
                    if (installedPack.equals(availablePack)) {
                        if (availablePack.getVersion() <= installedPack.getVersion()) {
                            add = false;
                            
                            // In case the user uninstalls and then immediately re-installs
                            // the pack, ensure we have current server data in the Pack.
                            installedPack.setUrlBase(availablePack.getUrlBase());
                            installedPack.setDatafile(availablePack.getDatafile());
                            break;
                        } else {
                            availablePack.setStatus(StickerPack.Status.UPDATEABLE);
                            availablePack.setReplaces(installedPack);
                            list.remove(installedPack);
                            break;
                        }
                    }
                }
                if (add)
                    list.add(availablePack);
                else
                    continue;
            }
        } catch (Exception e) {
            return new AllPacksResult(list, false, e);
        }
        
        Collections.sort(list);
        return new AllPacksResult(new ArrayList<>(list), true, null);
    }
    
    /**
     * Checks a list of StickerPacks to see if any are new (never seen before by this app),
     * notifies the user if any are found, and updates the saved list of seen-before packs.
     */
    public static void checkForNewPacks(Context context, List<StickerPack> packList) {
        SharedPreferences prefs = getPrefs(context);
        Set<String> knownPacks = getMutableStringSetFromPrefs(prefs, KNOWN_PACKS);
        final int origKnownPacksCount = knownPacks.size();
        
        List<StickerPack> newPacks = new LinkedList<>();
        
        if (knownPacks.size() == 0) {
            newPacks = packList;
        }
        else {
            for (StickerPack pack : packList) {
                if (!knownPacks.contains(pack.getPackname()))
                    newPacks.add(pack);
            }
        }
        
        for (StickerPack pack : newPacks)
            knownPacks.add(pack.getPackname());
        
        // Don't notify for new packs if the app's never been formally opened
        // (don't think we should ever hit this condition) or if there
        // were no known packs (i.e. this is the first time we're downloading
        // a pack list.)
        if (origKnownPacksCount > 0 && checkIfEverOpened(context)) {
            // Notify for each new pack
            for (StickerPack pack : newPacks) {
                // First download the icon file so we can display it in a notification
                String suffix = pack.getIconLocation().substring(pack.getIconLocation().lastIndexOf("."));
                File destination = new File(context.getCacheDir(), pack.getPackname() + "-icon" + suffix);
                try {
                    URL iconURL = new URL(pack.getIconLocation());
                    downloadFile(iconURL, destination);
                    // And get it in the Glide cache so it's right there if the user
                    // clicks the notification. (That leaves us with two copies of the image,
                    // but it looks like getting Glide to load into a notification is more
                    // complex than I care about.)
                    GlideRequest request = GlideApp.with(context).load(iconURL);
                    enableGlideCacheIfRemote(request, iconURL.toString(), pack.getVersion());
                    request.downloadOnly(1, 1).get();
                } catch (Exception e) {
                    Log.e(TAG, "Difficulty downloading pack icon", e);
                }
                
                Notification n = NotificationUtils.buildNewPackNotification(context, pack, destination);
                NotificationUtils.showNotification(context, n);
            }
        }
        
        // Save the new list of known packs
        SharedPreferences.Editor editor = prefs.edit();
        editor.putStringSet(KNOWN_PACKS, knownPacks);
        editor.apply();
    }
    
    public static class AllPacksResult {
        public final boolean networkSucceeded;
        public final List<StickerPack> list;
        public final Exception exception;
        public AllPacksResult(List<StickerPack> list, boolean networkSucceeded, Exception e) {
            this.list = list;
            this.networkSucceeded = networkSucceeded;
            exception = e;
        }
    }
    
    public static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    public static Set<String> getMutableStringSetFromPrefs(SharedPreferences prefs, String key) {
        Set<String> output = new HashSet<>();
        Set<String> saved = prefs.getStringSet(key, null);
        if (saved != null)
            output.addAll(saved);
        return output;
    }
    
    public static Set<String> getMutableStringSetFromPrefs(Context context, String key) {
        return getMutableStringSetFromPrefs(getPrefs(context), key);
    }
    
    public static void registerInstalledPack(StickerPack pack, Context context) {
        Set<String> installedPacks = getMutableStringSetFromPrefs(context, INSTALLED_PACKS);
        installedPacks.add(pack.getPackname());
        SharedPreferences.Editor editor = getPrefs(context).edit();
        editor.putStringSet(INSTALLED_PACKS, installedPacks);
        editor.apply();
    }
    
    public static void unregisterInstalledPack(StickerPack pack, Context context) {
        Set<String> installedPacks = getMutableStringSetFromPrefs(context, INSTALLED_PACKS);
        installedPacks.remove(pack.getPackname());
        SharedPreferences.Editor editor = getPrefs(context).edit();
        editor.putStringSet(INSTALLED_PACKS, installedPacks);
        editor.apply();
    }
    
    public static void performNeededMigrations(Context context) {
        if (context == null)
            return;
        
        // Migrate known pack storage from 2.1.1 and below
        File file = new File(context.getFilesDir(), "known_packs.txt");
        if (file.exists() && file.isFile()) {
            Set<String> packs = new HashSet<>();
            try {
                BufferedReader br = new BufferedReader(new FileReader(file));
                String line;
                while ((line = br.readLine()) != null)
                    packs.add(line);
                br.close();
                
                SharedPreferences.Editor editor = getPrefs(context).edit();
                editor.putStringSet(KNOWN_PACKS, packs);
                editor.putBoolean(HAS_RUN, true);
                editor.apply();
                
                delete(file);
            } catch (IOException e) {
                Log.e(TAG, "Error in known packs migration", e);
            }
        }
        
        // Migrate installed pack data from 2.0 - 2.1.1
        SharedPreferences prefs = getPrefs(context);
        Set<String> installedPacks = prefs.getStringSet(INSTALLED_PACKS, null);
        if (installedPacks == null) {
            SharedPreferences.Editor editor = prefs.edit();
            installedPacks = new HashSet<>();
            StickerProvider provider = new StickerProvider();
            provider.setRootDir(context);
            // Scan the data dir for info on installed packs
            for (File name : context.getFilesDir().listFiles()) {
                if (name.isFile() && name.getName().endsWith(".json")) {
                    try {
                        // Read in the JSON file we'll move to SharedPrefs
                        String data = readTextFile(name);
                        JSONObject pack = new JSONObject(data);
                        
                        // Migrate keywords from data.json to a list of Stickers
                        String packName = name.getName();
                        packName = packName.substring(0, packName.length()-5);
                        File packDir = new File(name.getParent(), packName);
                        File dataFile = new File(packDir, "data.json");
                        String dataFileContents = readTextFile(dataFile);
                        
                        StickerPack dummyPack = new StickerPack(pack, "");
                        StickerPackProcessor processor = new StickerPackProcessor(dummyPack, context);
                        List<Sticker> stickers = processor.parseStickerList(dataFileContents).list;
                        
                        JSONArray stickerArray = new JSONArray();
                        for (Sticker sticker : stickers)
                            stickerArray.put(sticker.toJSON());
                        pack.put("stickers", stickerArray);
                        
                        // Convert icon location from file path to Uri
                        String iconFile = pack.getString("iconfile");
                        pack.put("iconLocation", provider.fileToUri(iconFile).toString());
                        
                        // Remove old data
                        pack.remove("iconfile");
                        pack.remove("iconUrl");
                        pack.remove("dataFile");
                        pack.remove("urlBase");
                        pack.remove("jsonSavePath");
                        
                        editor.putString(STICKER_PACK_DATA_PREFIX + packName, pack.toString());
                        delete(name);
                        delete(dataFile);
                    } catch (Exception e) {
                        Log.e(TAG, "Error migrating file " + name.toString(), e);
                    }
                } else
                    // We have a directory, which must be an installed sticker pack
                    installedPacks.add(name.getName());
            }
    
            editor.putStringSet(INSTALLED_PACKS, installedPacks);
            editor.apply();
        }
    }
}
