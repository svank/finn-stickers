package net.samvankooten.finnstickers;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Xml;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.appindexing.FirebaseAppIndex;
import com.google.firebase.appindexing.FirebaseAppIndexingInvalidArgumentException;
import com.google.firebase.appindexing.Indexable;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
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
    public static Context context = null;
    public static final FirebaseAppIndex index = FirebaseAppIndex.getInstance();


    public StickerProcessor(String urlString){
        URL url;
        try {
            url = new URL(urlString);
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

    public static void clearStickers() {
        // Remove stickers from Firebase index.
        Task<Void> task = index.removeAll();

        // Delete sticker files.
        String[] filesList = context.fileList();
        for(String filename: filesList){
            delete(new File(context.getFilesDir(), filename));
        }
    }

    private static void delete(File file) {
        if (file.isDirectory())
            for (File child : file.listFiles())
                delete(child);

        file.delete();
    }

    public List process(InputStream in) throws XmlPullParserException, IOException {
        // Given a sticker pack xml file, downloads stickers and registers them with Firebase.
        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        parser.setInput(in, null);
        parser.nextTag();
        return readFeed(parser);
    }

    private List readFeed(XmlPullParser parser) throws XmlPullParserException, IOException {
        List stickers = new ArrayList();

        parser.require(XmlPullParser.START_TAG, ns, "packicon");
        String packIconFilename = readText(parser);
        parser.require(XmlPullParser.END_TAG, ns, "packicon");

        URL url = new URL(urlBase + packIconFilename);
        File destination = new File(context.getFilesDir(), packIconFilename);
        downloadSticker(url, destination.toString());

        parser.nextTag();
        parser.require(XmlPullParser.START_TAG, ns, "finnstickers");
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            // Starts by looking for the entry tag
            if (name.equals("sticker")) {
                stickers.add(readSticker(parser));
            } else {
                Log.e(TAG, "Unexpected xml entry:" + name);
                skip(parser);
            }
        }

        Indexable[] indexables = new Indexable[stickers.size() + 1];
        for(int i = 0; i < stickers.size(); i++) {
            indexables[i] = ((Sticker) stickers.get(i)).getIndexable();
        }

        try {
            Indexable stickerPack = new Indexable.Builder("StickerPack")
                    .setName(Sticker.STICKER_PACK_NAME)
                    .setImage(Uri.parse(Sticker.CONTENT_URI_ROOT + packIconFilename).toString())
                    .setDescription("Finjamin stickers!")
                    .setUrl("finnstickers://sticker/pack/finn")
                    .put("hasSticker", indexables)
                    .build();

            indexables[indexables.length - 1] = stickerPack;

            Task<Void> task = index.update(indexables);

            task.addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
//                    Log.v(TAG, "Successfully added Pack to index");
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
        return stickers;
    }

    private Sticker readSticker(XmlPullParser parser) throws XmlPullParserException, IOException{
        Sticker sticker = new Sticker();

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            if (name.equals("filename")){
                sticker.setFilename(readFilename(parser));
            } else if (name.equals("keyword")){
                sticker.addKeyword(readKeyword(parser));
            } else {
                skip(parser);
            }
        }
        return sticker;
    }

    private String readFilename(XmlPullParser parser) throws IOException, XmlPullParserException{
        parser.require(XmlPullParser.START_TAG, ns, "filename");
        String filename = readText(parser);
        parser.require(XmlPullParser.END_TAG, ns, "filename");

        URL url = new URL(urlBase + filename);
        File destination = new File(context.getFilesDir(), filename);
        downloadSticker(url, destination.toString());

        return filename;
    }

    private String readKeyword(XmlPullParser parser) throws IOException, XmlPullParserException{
        parser.require(XmlPullParser.START_TAG, ns, "keyword");
        String keyword = readText(parser);
        parser.require(XmlPullParser.END_TAG, ns, "keyword");
        return keyword;
    }

    private String readText(XmlPullParser parser) throws IOException, XmlPullParserException {
        String result = "";
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.getText();
            parser.nextTag();
        }
        return result;
    }

    private void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
        if (parser.getEventType() != XmlPullParser.START_TAG) {
            throw new IllegalStateException();
        }
        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
                case XmlPullParser.END_TAG:
                    depth--;
                    break;
                case XmlPullParser.START_TAG:
                    depth++;
                    break;
            }
        }
    }

    /**
     * Given a URL, sets up a connection and gets the HTTP response body from the server.
     * If the network request is successful, it returns the response body in String form. Otherwise,
     * it will throw an IOException.
     */
    private InputStream downloadSticker(URL url, String destination) throws IOException {
        InputStream stream = null;
        HttpURLConnection connection = null;
        OutputStream output = null;
//        Log.v(TAG, "Starting download: " + url.toString());
        try {
            connection = (HttpURLConnection) url.openConnection();
            // Timeouts arbitrarily set to 5000ms.
            connection.setReadTimeout(5000);
            connection.setConnectTimeout(5000);
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

            // Ensure the directory path exists
            File dirPath = new File(destination).getParentFile();
            if (dirPath != null) {
                dirPath.mkdirs();
            }
            // coming from https://stackoverflow.com/questions/3028306/download-a-file-with-android-and-showing-the-progress-in-a-progressdialog
            output = new FileOutputStream(destination);

            byte data[] = new byte[4096];
            int count;
            while ((count = stream.read(data)) != -1) {
                // do something to publish progress
                output.write(data, 0, count);
            }
        } finally {
            // Close Stream and disconnect HTTPS connection.
            if (stream != null) {
                stream.close();
            }
            if (output != null) {
                output.close();
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
        return stream;
    }

}
