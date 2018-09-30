package net.samvankooten.finnstickers;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.FileChannel;

import javax.net.ssl.HttpsURLConnection;

/**
 * Created by sam on 10/22/17.
 */

public class Util {
    
    static final String CONTENT_URI_ROOT =
            String.format("content://%s/", StickerProvider.class.getName());
    
    private static final String TAG = "Util";
    
    /**
     * Recursively deletes a file or directory.
     * @param file Path to be deleted
     */
    static void delete(File file) throws IOException{
        if (file.isDirectory())
            for (File child : file.listFiles())
                delete(child);

        if (file.delete())
            return;
        throw new IOException("Error deleting " + file.toString());
    }
    
    static void copy(File src, File dest) throws IOException {
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
    
    public static class DownloadResult{
        public final HttpsURLConnection connection;
        public final InputStream stream;
        public DownloadResult(HttpsURLConnection c, InputStream s) {
            connection = c;
            stream = s;
        }
    
        /**
         * Releases resources related to the HTTP connection
         */
        public void close() {
            if (connection != null)
                connection.disconnect();
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error in closing input stream", e);
                }
            }
        }
    
        /**
         * Returns the downloaded data as a String
         */
        @NonNull
        public String readString() {
            Reader reader;
            StringBuilder buffer = new StringBuilder();
            try {
                reader = new InputStreamReader(stream, "UTF-8");
                char[] rawBuffer = new char[1024];
                for (;;) {
                    int readSize = reader.read(rawBuffer, 0, rawBuffer.length);
                    if (readSize < 0)
                        break;
                    buffer.append(rawBuffer, 0, readSize);
                }
            } catch (IOException e) {
                Log.e(TAG, "Error reading input stream to string", e);
            }
            return buffer.toString();
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
        InputStream stream;
        HttpsURLConnection connection;
        connection = (HttpsURLConnection) url.openConnection();
        // Timeouts (in ms) arbitrarily set.
        connection.setReadTimeout(15000);
        connection.setConnectTimeout(15000);
        connection.setRequestMethod("GET");
        // Already true by default but setting just in case; needs to be true since this
        // request is providing input to the app from the server.
        connection.setDoInput(true);
        
        connection.connect();
        
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpsURLConnection.HTTP_OK) {
            throw new IOException("HTTP error code: " + responseCode);
        }
        // Retrieve the response body as an InputStream.
        stream = connection.getInputStream();

        return new DownloadResult(connection, stream);
    }
    
    /**
     * Downloads a URL and saves its contents to a file
     * @param url URL to download
     * @param destination Path at which to save the data
     */
    public static void downloadFile(@NonNull URL url, @NonNull File destination) throws IOException {
        OutputStream output = null;
        DownloadResult result = null;
        try{
            result = downloadFromUrl(url);

            // Ensure the directory path exists
            File dirPath = destination.getParentFile();
            if (dirPath != null) {
                dirPath.mkdirs();
            }
            // coming from https://stackoverflow.com/questions/3028306/download-a-file-with-android-and-showing-the-progress-in-a-progressdialog
            output = new FileOutputStream(destination);

            byte data[] = new byte[4096];
            int count;
            while ((count = result.stream.read(data)) != -1) {
                // do something to publish progress
                output.write(data, 0, count);
            }
        } finally {
            // Close Stream and disconnect HTTPS connection.
            if (result != null)
                result.close();
            if (output != null)
                output.close();
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
    static boolean checkIfEverOpened(@NonNull Context context) {
        File dir = context.getFilesDir();
        File f1 = new File(dir, "tongue"); // App opened as V1
        File f2 = new File(dir, StickerPack.KNOWN_PACKS_FILE); // App opened as V2
        if (f1.exists() || f2.exists())
            return true;
        return false;
    }
}
