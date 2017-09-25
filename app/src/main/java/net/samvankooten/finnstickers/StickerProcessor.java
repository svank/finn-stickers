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
            Log.e("StickerProcessor", "Malformed URL");
            return;
        }
        String host = url.getHost();
        String path = url.getPath();
        String protocol = url.getProtocol();
        String dirs = path.substring(0, path.lastIndexOf("/"));
        try {
            urlBase = new URL(protocol, host, dirs).toString() + '/';
        } catch (MalformedURLException ignored){
            // TODO?
        }
    }

    public static void clearStickers() {
        Task<Void> task = index.removeAll();
//        try {
//            task.wait();
//        } catch (InterruptedException e) {
//            Log.e("StickerProcessor", e.toString());
//        }
        String[] filesList = context.fileList();
        for(String filename: filesList){
            context.deleteFile(filename);
        }
    }

    public List process(InputStream in) throws XmlPullParserException, IOException {
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(in, null);
            parser.nextTag();
            return readFeed(parser);
        } finally {
            in.close();
        }
    }

    private List readFeed(XmlPullParser parser) throws XmlPullParserException, IOException {
//        Indexable.Builder pack = new Indexable.Builder("StickerPack")
//                .setName("Finn!")
//                .setUrl("finnstickers://sticker/pack/finn");
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
                stickers.add(readSticker(parser, packIconFilename));
            } else {
                Log.e("StickerProcessor", "Unexpected xml entry:" + name);
                skip(parser);
            }
        }
//
//
//        Indexable[] idxArray = new Indexable[stickers.size()];
//        idxArray= stickers.toArray(idxArray);
//
//        pack.put("hasSticker", idxArray);

        Indexable[] indexables = new Indexable[stickers.size()];
        List<Indexable> toIndex = new ArrayList<Indexable>();
        for(int i = 0; i < stickers.size(); i++) {
            indexables[i] = ((Sticker) stickers.get(i)).indexable;
            toIndex.add(indexables[i]);
        }

        try {
            Indexable stickerPack = new Indexable.Builder("StickerPack")
                    .setName(Sticker.STICKER_PACK_NAME)
                    .setImage(Uri.parse(Sticker.CONTENT_URI_ROOT + packIconFilename).toString())
                    .setDescription("Finjamin stickers!")
                    .setUrl("finnstickers://sticker/pack/finn")
                    .put("hasSticker", indexables)
                    .build();

            toIndex.add(stickerPack);

            Task<Void> task = index.update(toIndex.toArray(new Indexable[toIndex.size()]));

            task.addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
//                    Log.v("Sticker", "Successfully added Pack to index");
                }
            });

            task.addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Log.e("Sticker", "Failed to add Pack to index", e);
                }
            });
        } catch (FirebaseAppIndexingInvalidArgumentException e){
            Log.e("StickerProcessor", e.toString());
        }
        return stickers;
    }

    private Sticker readSticker(XmlPullParser parser, String packIconFilename) throws XmlPullParserException, IOException{
        Sticker sticker = new Sticker(packIconFilename);

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
        sticker.addToIndex(context, index);
        return sticker;

//        return sticker.buildIndexible(context, index);
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
        String result = null;
//        Log.v("StickerProcessor", "Starting download: " + url.toString());
        try {
            connection = (HttpURLConnection) url.openConnection();
            // Timeout for reading InputStream arbitrarily set to 3000ms.
            connection.setReadTimeout(3000);
            // Timeout for connection.connect() arbitrarily set to 3000ms.
            connection.setConnectTimeout(3000);
            // For this use case, set HTTP method to GET.
            connection.setRequestMethod("GET");
            // Already true by default but setting just in case; needs to be true since this request
            // is carrying an input (response) body.
            connection.setDoInput(true);
            // Open communications link (network traffic occurs here).
            connection.connect();
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP error code: " + responseCode);
            }
            // Retrieve the response body as an InputStream.
            stream = connection.getInputStream();

            // coming from https://stackoverflow.com/questions/3028306/download-a-file-with-android-and-showing-the-progress-in-a-progressdialog
            output = new FileOutputStream(destination);

            byte data[] = new byte[4096];
            long total = 0;
            int count;
            while ((count = stream.read(data)) != -1) {
                total += count;
                // do something to publish progress
                output.write(data, 0, count);
            }
        } finally {
//                // Close Stream and disconnect HTTPS connection.
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
