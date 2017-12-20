package net.samvankooten.finnstickers;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by sam on 10/22/17.
 */

public class Util {
    public static final String TAG = "Util";
    
    static void delete(File file) {
        if (file.isDirectory())
            for (File child : file.listFiles())
                delete(child);

        file.delete();
    }
    
    public static class DownloadResult{
        public HttpURLConnection connection;
        public InputStream stream;
        public DownloadResult(HttpURLConnection c, InputStream s) {
            connection = c;
            stream = s;
        }

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

        public String readString(int maxReadSize) {
            Reader reader;
            StringBuilder buffer = new StringBuilder();
            try {
                reader = new InputStreamReader(stream, "UTF-8");
                char[] rawBuffer = new char[maxReadSize];
                int readSize;
                while (((readSize = reader.read(rawBuffer)) != -1) && maxReadSize > 0) {
                    if (readSize > maxReadSize) {
                        readSize = maxReadSize;
                    }
                    buffer.append(rawBuffer, 0, readSize);
                    maxReadSize -= readSize;
                }
            } catch (IOException e) {
                Log.e(TAG, "Error reading input stream to string", e);
            }
            return buffer.toString();
        }
    }

    public static DownloadResult downloadFromUrl(URL url) throws IOException {
        InputStream stream;
        HttpURLConnection connection;
        connection = (HttpURLConnection) url.openConnection();
        // Timeouts arbitrarily set to 5000ms.
        connection.setReadTimeout(15000);
        connection.setConnectTimeout(15000);
        connection.setRequestMethod("GET");
        // Already true by default but setting just in case; needs to be true since this
        // request is providing input to the app from the server.
        connection.setDoInput(true);
        // Open communications link (network traffic occurs here).
        connection.connect();
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP error code: " + responseCode);
        }
        // Retrieve the response body as an InputStream.
        stream = connection.getInputStream();

        return new DownloadResult(connection, stream);
    }

    public static void downloadFile(URL url, File destination) throws IOException {
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

    public static String getURLPath(URL url) {
        String host = url.getHost();
        String path = url.getPath();
        String protocol = url.getProtocol();
        String dirs = path.substring(0, path.lastIndexOf("/"));
        try {
            return new URL(protocol, host, dirs).toString() + '/';
        } catch (MalformedURLException e){
            Log.e(TAG, "Unexpected error parsing URL base", e);
            return null;
        }
    }
    
    @NonNull
    public static String readTextFile(File file) throws IOException {
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
    
    public static boolean checkIfEverOpened(Context context) {
        // Checks whether the app has ever been opened
        File dir = context.getFilesDir();
        File f1 = new File(dir, "tongue"); // App opened as V1
        File f2 = new File(dir, StickerPack.KNOWN_PACKS_FILE); // App opened as V2
        if (f1.exists() || f2.exists())
            return true;
        return false;
    }
}
