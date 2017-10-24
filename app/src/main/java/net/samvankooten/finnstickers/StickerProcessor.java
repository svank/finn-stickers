package net.samvankooten.finnstickers;

import android.content.Context;
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

    public List process(InputStream in) throws XmlPullParserException, IOException {
        // Given a sticker pack xml file, downloads stickers and registers them with Firebase.
    
        File rootPath = pack.buildFile(context.getFilesDir(), "");
        if (rootPath.exists()) {
            // TODO: Test this check
            Log.e(TAG, "Attempting to download a sticker pack that appears to exists already");
            return null;
        }
        
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
    
        Log.d(TAG, "FINNished parsing xml");
    
        return new ParsedStickerList(stickers, packIconFilename);
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
