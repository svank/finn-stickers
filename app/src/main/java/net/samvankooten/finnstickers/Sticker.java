package net.samvankooten.finnstickers;

import android.net.Uri;
import android.util.Log;

import com.google.firebase.appindexing.FirebaseAppIndexingInvalidArgumentException;
import com.google.firebase.appindexing.Indexable;

import java.util.ArrayList;

/**
 * Created by sam on 9/23/17.
 */

public class Sticker {
    private static final String TAG = "Sticker";

    public static final String STICKER_PACK_NAME = "Finn!";
    public static final String STICKER_URL_PATTERN = "finnstickers://sticker/%s";
    public static final String CONTENT_URI_ROOT =
            String.format("content://%s/", StickerProvider.class.getName());

    private String filename;
    private ArrayList<String> keywords;

    public Sticker() {
        keywords = new ArrayList();
        keywords.add("Finn");
        keywords.add("Dog");
        keywords.add("Amazing");
        keywords.add("Beautiful");
        keywords.add("Perfect");
        keywords.add("Finjamin");
        keywords.add("Finnjamin");
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }
    
    public void addKeyword(String keyword){
        keywords.add(keyword);
    }

    public String toString(){
        StringBuilder result = new StringBuilder();
        result.append("[Sticker, ");
        result.append(" Filename: ");
        result.append(filename);
        for (String keyword: keywords){
            result.append(" Keyword: ");
            result.append(keyword);
        }
        result.append("]");
        return result.toString();
    }

    public Indexable getIndexable() {
        Uri contentUri = Uri.parse(CONTENT_URI_ROOT + filename);
        String url = String.format(STICKER_URL_PATTERN, filename);
        String[] keywordArray = new String[keywords.size()];
        keywordArray = keywords.toArray(keywordArray);
        Indexable indexable = null;

        try {
            indexable = new Indexable.Builder("Sticker")
                    .setName(filename)
                    .setImage(contentUri.toString())
                    .setUrl(url)
                    .put("keywords", keywordArray)
                    .put("isPartOf",
                            new Indexable.Builder("StickerPack")
                                    .setName(STICKER_PACK_NAME)
                                    .build())
                    .build();

        } catch (FirebaseAppIndexingInvalidArgumentException e) {
            Log.e(TAG, e.toString());
        }
        return indexable;
    }
}
