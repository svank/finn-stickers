package net.samvankooten.finnstickers;

import android.net.Uri;
import android.util.Log;

import com.google.firebase.appindexing.FirebaseAppIndexingInvalidArgumentException;
import com.google.firebase.appindexing.Indexable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by sam on 9/23/17.
 */

public class Sticker {
    private static final String TAG = "Sticker";

    public static final String STICKER_URL_PATTERN = "finnstickers://sticker/%s";
    public static final String CONTENT_URI_ROOT =
            String.format("content://%s/", StickerProvider.class.getName());

    private String path;
    private String packname;
    private List<String> keywords;

    public Sticker() {
        keywords = new ArrayList<String>();
    }
    
    public Sticker(JSONObject obj) throws JSONException {
        setPath(obj.getString("filename"));
        keywords = new ArrayList<String>();
        JSONArray keys = obj.getJSONArray("keywords");
        for (int i=0; i<keys.length(); i++) {
            keywords.add(keys.getString(i));
        }
    }
    
    public void addKeyword(String keyword){
        keywords.add(keyword);
    }
    
    public void addKeywords(List<String> keywords) {
        for(int i=0; i<keywords.size(); i++) {
            addKeyword(keywords.get(i));
        }
    }
    
    public void setPath(String path) {
        if (path.charAt(0) != '/')
            path = "/" + path;
        this.path = path;
    }
    
    public void setPackName(String packname) {this.packname = packname; }

    public String toString(){
        StringBuilder result = new StringBuilder();
        result.append("[Sticker, ");
        result.append(" Filename: ");
        result.append(path);
        for (String keyword: keywords){
            result.append(" Keyword: ");
            result.append(keyword);
        }
        result.append("]");
        return result.toString();
    }

    public Indexable getIndexable() {
        Uri contentUri = getURI();
        String[] keywordArray = new String[keywords.size()];
        keywordArray = keywords.toArray(keywordArray);
        Indexable indexable = null;

        try {
            indexable = new Indexable.Builder("Sticker")
                    .setName(packname + path)
                    .setImage(contentUri.toString())
                    .setUrl(getURL())
                    .put("keywords", keywordArray)
                    .put("isPartOf",
                            new Indexable.Builder("StickerPack")
                                    .setName(packname)
                                    .build())
                    .build();

        } catch (FirebaseAppIndexingInvalidArgumentException e) {
            Log.e(TAG, e.toString());
        }
        return indexable;
    }
    
    /**
     * Given a URL, sets up a connection and gets the HTTP response body from the server.
     * If the network request is successful, it returns the response body in String form. Otherwise,
     * it will throw an IOException.
     */
    public void downloadToFile(StickerPack pack, File destinationBase) throws IOException {
        File destination = pack.buildFile(destinationBase, path);
        URL source = new URL(pack.buildURLString(path));
        Util.downloadFile(source, destination);
    }
    
    public Util.DownloadResult download(StickerPack pack) throws IOException {
        URL source = new URL(pack.buildURLString(path));
        return Util.downloadFromUrl(source);
    }
    
    public String getURL() {
        return String.format(STICKER_URL_PATTERN, packname + path);
    }
    
    public Uri getURI() { return Uri.parse(CONTENT_URI_ROOT + packname + path); }
    
    public String getPath() {
        return path;
    }
}
