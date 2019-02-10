package net.samvankooten.finnstickers;

import android.net.Uri;
import android.util.Log;

import com.google.firebase.appindexing.FirebaseAppIndexingInvalidArgumentException;
import com.google.firebase.appindexing.Indexable;

import net.samvankooten.finnstickers.utils.Util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by sam on 9/23/17.
 */

public class Sticker {
    private static final String TAG = "Sticker";
    
    /**
     * Firebase requires unique URLs to ID stickers
     */
    private static final String STICKER_URL_PATTERN = "finnstickers://sticker/";

    private String path;
    private String packname;
    private List<String> keywords;
    
    /**
     * Creates a Sticker instance from a JSONObject
     */
    public Sticker(JSONObject obj) throws JSONException {
        setPath(obj.getString("filename"));
        
        keywords = new ArrayList<>();
        JSONArray keys = obj.getJSONArray("keywords");
        
        for (int i=0; i<keys.length(); i++)
            keywords.add(keys.getString(i));
    }
    
    public void addKeyword(String keyword){
        keywords.add(keyword);
    }
    
    public void addKeywords(List<String> keywords) {
        for(int i=0; i<keywords.size(); i++)
            addKeyword(keywords.get(i));
    }
    
    public void setPath(String path) {
        if (path.charAt(0) != '/')
            this.path = "/" + path;
        else
            this.path = path;
    }
    
    public void setPackName(String packname) {
        this.packname = packname;
    }

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
    
    public String getURL() {
        return STICKER_URL_PATTERN + packname + path;
    }
    
    public Uri getURI() {
        return Uri.parse(Util.CONTENT_URI_ROOT + packname + path);
    }
    
    public String getPath() {
        return path;
    }
}