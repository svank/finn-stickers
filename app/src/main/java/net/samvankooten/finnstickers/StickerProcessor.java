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
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
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
    private StickerPack pack;
    private Context context = null;
    public static final FirebaseAppIndex index = FirebaseAppIndex.getInstance();


    public StickerProcessor(StickerPack pack, Context context){
        URL url;
        this.pack = pack;
        this.context = context;
        try {
            url = new URL(pack.getDatafile());
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

    public static void clearStickers(Context context) {
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
        ParsedStickerList result = readFeed(parser);
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

    private ParsedStickerList readFeed(XmlPullParser parser) throws XmlPullParserException, IOException {
        List stickers = new ArrayList();
    
        parser.require(XmlPullParser.START_TAG, ns, "packicon");
        String packIconFilename = readText(parser);
        parser.require(XmlPullParser.END_TAG, ns, "packicon");
    
        parser.nextTag();
        parser.require(XmlPullParser.START_TAG, ns, "finnstickers");
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            // Starts by looking for the entry tag
            if (name.equals("sticker")) {
                Sticker sticker = readSticker(parser);
                stickers.add(sticker);
            } else {
                Log.e(TAG, "Unexpected xml entry:" + name);
                skip(parser);
            }
        }
    
        Log.d(TAG, "Finished parsing xml");
    
        return new ParsedStickerList(stickers, packIconFilename);
    }
        
    public void registerStickers(ParsedStickerList input) throws IOException {
        List stickers = input.list;
        String packIconFilename = input.packIconFilename;
    
        URL url = new URL(urlBase + packIconFilename);
        File destination = new File(String.format("%s/%s/%s",
                context.getFilesDir(), pack.getPackname(), packIconFilename));
        Util.downloadFile(url, destination);
        
        Indexable[] indexables = new Indexable[stickers.size() + 1];
        for(int i = 0; i < stickers.size(); i++) {
            Sticker sticker = (Sticker) stickers.get(i);
            sticker.setPackName(pack.getPackname());
            sticker.download(urlBase, context.getFilesDir());
            indexables[i] = sticker.getIndexable();
        }
        
        FileWriter file = new FileWriter(String.format("%s/%s.json",
                context.getFilesDir(), pack.getPackname()));
        file.write(pack.createJSON().toString());

        try {
            Indexable stickerPack = new Indexable.Builder("StickerPack")
                    .setName(pack.getPackname())
                    .setImage(Uri.parse(Sticker.CONTENT_URI_ROOT + packIconFilename).toString())
                    .setDescription(pack.getDescription())
                    .setUrl("finnstickers://sticker/pack/" + pack.getPackname())
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
    }

    private Sticker readSticker(XmlPullParser parser) throws XmlPullParserException, IOException{
        Sticker sticker = new Sticker();

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            if (name.equals("filename")){
                sticker.setPath(readFilename(parser));
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

}
