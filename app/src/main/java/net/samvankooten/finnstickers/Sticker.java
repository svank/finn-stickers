package net.samvankooten.finnstickers;

import android.net.Uri;
import android.util.Log;

import com.google.firebase.appindexing.FirebaseAppIndexingInvalidArgumentException;
import com.google.firebase.appindexing.Indexable;

import net.samvankooten.finnstickers.utils.Util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by sam on 9/23/17.
 */

public class Sticker implements Serializable {
    private static final String TAG = "Sticker";
    
    /**
     * Firebase requires unique URLs to ID stickers
     */
    public static final String STICKER_FIREBASE_URL_PATTERN = "finnstickers://sticker/";

    private String path;
    private String packname;
    private List<String> keywords;
    private String serverBaseURL;
    private String customTextData;
    private String customTextBaseImage;
    
    /**
     * Creates a Sticker instance from a JSONObject
     */
    public Sticker(JSONObject obj) throws JSONException {
        setPath(obj.getString("filename"));
        
        keywords = new ArrayList<>();
        JSONArray keys = obj.getJSONArray("keywords");
        for (int i=0; i<keys.length(); i++)
            keywords.add(keys.getString(i));
        
        if (obj.has("packname"))
            packname = obj.getString("packname");
        
        if (obj.has("customTextData") && obj.has("customTextBaseImage")) {
            customTextData = obj.getString("customTextData");
            customTextBaseImage = obj.getString("customTextBaseImage");
        }
    }
    
    public Sticker(JSONObject obj, String baseDir) throws JSONException {
        // Call main constructor
        this(obj);
        setServerBaseDir(baseDir);
    }
    
    public Sticker(String path, String packname, List<String> keywords) {
        setPath(path);
        this.keywords = keywords;
        this.packname = packname;
    }
    
    public JSONObject toJSON() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("filename", getRelativePath());
            obj.put("packname", packname);
            
            JSONArray keywords = new JSONArray();
            for (String keyword : this.keywords)
                keywords.put(keyword);
            obj.put("keywords", keywords);
            
            if (customTextData != null && customTextBaseImage != null) {
                obj.put("customTextData", customTextData);
                obj.put("customTextBaseImage", customTextBaseImage);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error on JSON out", e);
        }
        return obj;
    }
    
    public void addKeyword(String keyword){
        keywords.add(keyword);
    }
    
    public void addKeywords(List<String> keywords) {
        for(int i=0; i<keywords.size(); i++)
            addKeyword(keywords.get(i));
    }
    
    private void setPath(String path) {
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
        Indexable indexable = null;
        
        try {
            indexable = new Indexable.Builder("Sticker")
                    .setName(packname + path)
                    .setImage(getURI().toString())
                    // URL is a unique identifier for the sticker in Firebase
                    .setUrl(getFirebaseURL())
                    .put("keywords", keywords.toArray(new String[0]))
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
    
    public void setServerBaseDir(String baseDir) {
        if (baseDir != null && baseDir.charAt(baseDir.length()-1) == '/')
            baseDir = baseDir.substring(0, baseDir.length()-1);
        serverBaseURL = baseDir;
    }
    
    public void setCustomTextData(String customTextData) {
        this.customTextData = customTextData;
    }
    
    public String getCustomTextData() {
        return customTextData;
    }
    
    public void setCustomTextBaseImage(String customTextBaseImage) {
        this.customTextBaseImage = customTextBaseImage;
    }
    
    public String getCustomTextBaseImage() {
        return customTextBaseImage;
    }
    
    public String getFirebaseURL() {
        return STICKER_FIREBASE_URL_PATTERN + packname + path;
    }
    
    public String getURL() {
        return serverBaseURL + path;
    }
    
    public Uri getURI() {
        return generateUri(packname, path);
    }
    
    public static Uri generateUri(String packname, String path) {
        return Uri.parse(Util.CONTENT_URI_ROOT + packname + path);
    }
    
    public String getCurrentLocation() {
        if (serverBaseURL != null)
            return getURL();
        return getURI().toString();
    }
    
    public String getRelativePath() {
        return path;
    }
    
    public List<String> getKeywords() {
        return keywords;
    }
    
    public String getPackname() {
        return packname;
    }
}