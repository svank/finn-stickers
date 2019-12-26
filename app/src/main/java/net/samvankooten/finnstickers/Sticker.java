package net.samvankooten.finnstickers;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.google.firebase.appindexing.FirebaseAppIndexingInvalidArgumentException;
import com.google.firebase.appindexing.Indexable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
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
    private List<String> customKeywords;
    private List<String> autoKeywords;
    private String serverBaseURL;
    private JSONObject customData;
    
    /**
     * Creates a Sticker instance from a JSONObject
     */
    public Sticker(JSONObject obj, Context context) throws JSONException {
        setPath(obj.getString("filename"));
        
        JSONArray keys = obj.getJSONArray("keywords");
        keywords = new ArrayList<>(keys.length());
        for (int i=0; i<keys.length(); i++)
            keywords.add(keys.getString(i));
    
        if (obj.has("customKeywords")) {
            keys = obj.getJSONArray("customKeywords");
            customKeywords = new ArrayList<>(keys.length());
            for (int i=0; i<keys.length(); i++)
                customKeywords.add(keys.getString(i));
        } else
            customKeywords = new ArrayList<>();
        
        if (obj.has("packname"))
            packname = obj.getString("packname");
        
        if (obj.has("customData")) {
            customData = obj.getJSONObject("customData");
            autoKeywords = new ArrayList<>();
            Collections.addAll(autoKeywords,
                    context.getResources().getStringArray(R.array.custom_sticker_keywords));
        }
    }
    
    public Sticker(JSONObject obj, String baseDir, Context context) throws JSONException {
        // Call main constructor
        this(obj, context);
        setServerBaseDir(baseDir);
    }
    
    public Sticker(String path, String packname, List<String> keywords, List<String> customKeywords,
                   JSONObject customData, Context context) {
        setPath(path);
        this.keywords = keywords;
        this.customKeywords = customKeywords;
        this.packname = packname;
        this.customData = customData;
        
        if (customData != null) {
            autoKeywords = new ArrayList<>();
            Collections.addAll(autoKeywords,
                    context.getResources().getStringArray(R.array.custom_sticker_keywords));
        }
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
    
            JSONArray customKeywords = new JSONArray();
            for (String keyword : this.customKeywords)
                customKeywords.put(keyword);
            obj.put("customKeywords", customKeywords);
    
            if (customData != null)
                obj.put("customData", customData);
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
        for (String keyword: getKeywords()){
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
                    .put("keywords", getKeywords().toArray(new String[0]))
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
    
    public JSONObject getCustomData() {
        return customData;
    }
    
    public String getCustomBasePath() {
        String parent = "";
        try {
            parent = getCustomData().getString("basePath");
        } catch (JSONException e) {
            Log.e(TAG, "Error pulling sticker basePath", e);
        }
        return parent;
    }
    
    public boolean isCustomized() {
        return customData != null;
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
        return Uri.parse(Constants.CONTENT_URI_ROOT + packname + path);
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
        List<String> out = new ArrayList<>(keywords);
        if (customKeywords != null)
            out.addAll(customKeywords);
        if (autoKeywords != null)
            out.addAll(autoKeywords);
        return out;
    }
    
    public List<String> getBaseKeywords() {
        return keywords;
    }
    
    public String getPackname() {
        return packname;
    }
}