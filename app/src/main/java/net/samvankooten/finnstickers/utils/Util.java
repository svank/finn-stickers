package net.samvankooten.finnstickers.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.util.Log;

import net.samvankooten.finnstickers.StickerPack;
import net.samvankooten.finnstickers.StickerProvider;

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
    
    private static final String TAG = "Util";
    public static OkHttpClient httpClient = new OkHttpClient.Builder()
                                        .connectTimeout(15, TimeUnit.SECONDS)
                                        .readTimeout(15, TimeUnit.SECONDS)
                                        .writeTimeout(15, TimeUnit.SECONDS)
                                        .build();
    
    /**
     * Recursively deletes a file or directory.
     * @param file Path to be deleted
     */
    public static void delete(File file) throws IOException{
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
    
    /**
     * Checks whether the app has ever been opened
     */
    public static boolean checkIfEverOpened(@NonNull Context context) {
        File dir = context.getFilesDir();
        File f1 = new File(dir, "tongue"); // App opened as V1
        File f2 = new File(dir, StickerPack.KNOWN_PACKS_FILE); // App opened as V2
        return f1.exists() || f2.exists();
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
}
