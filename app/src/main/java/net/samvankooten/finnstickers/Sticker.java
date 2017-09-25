package net.samvankooten.finnstickers;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.appindexing.FirebaseAppIndex;
import com.google.firebase.appindexing.FirebaseAppIndexingInvalidArgumentException;
import com.google.firebase.appindexing.Indexable;

import java.io.File;
import java.util.ArrayList;

/**
 * Created by sam on 9/23/17.
 */

public class Sticker {

    public static final String STICKER_PACK_NAME = "Finn!";
    public static final String STICKER_URL_PATTERN = "finnstickers://sticker/%s";
    public static final String CONTENT_URI_ROOT =
            String.format("content://%s/", StickerProvider.class.getName());

    private String filename;
    private ArrayList<String> keywords;

    public Sticker(){
        keywords = new ArrayList();
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public ArrayList getKeywords() {
        return keywords;
    }

    public void setKeywords(ArrayList keywords) {
        this.keywords = keywords;
    }
    
    public void addKeyword(String keyword){
        keywords.add(keyword);
    }

    public String toString(){
        StringBuilder result = new StringBuilder();
        result.append("[Sticker, ");
        result.append(" Filename: " + filename);
        for (String keyword: keywords){
            result.append(" Keyword: " + keyword);
        }
        result.append("]");
        return result.toString();
    }

    public Indexable buildIndexible(Context context, FirebaseAppIndex index) {
        String[] keywordArray = new String[keywords.size()];
        keywordArray = keywords.toArray(keywordArray);

        String url = String.format(STICKER_URL_PATTERN, filename);

        Indexable idx = new Indexable.Builder("Sticker")
                .setName(filename)
                .setImage(url)
                .put("keywords", keywordArray)
                .build();

        return idx;
    }

    public void addToIndex(Context context, FirebaseAppIndex index) {
        Log.v("Sticker", "Adding " + filename + " to index");
        File stickersDir = new File(context.getFilesDir(), "");
        File stickerFile = new File(stickersDir, filename);
        Uri contentUri = Uri.parse(CONTENT_URI_ROOT + filename);
        String url = String.format(STICKER_URL_PATTERN, filename);
        String[] keywordArray = new String[keywords.size()];
        keywordArray = keywords.toArray(keywordArray);

        try {
            Indexable sticker = new Indexable.Builder("Sticker")
                    .setName(filename)
                    .setImage(contentUri.toString())
                    .setUrl(url)
                    .put("keywords", keywordArray)
                    .put("isPartOf",
                            new Indexable.Builder("StickerPack")
                                    .setName(STICKER_PACK_NAME)
                                    .setImage(contentUri.toString())
                                    .setDescription("Finjamin stickers!")
                                    .setUrl("finnstickers://sticker/pack/finn")
                                    .build())
                    .build();


            Task<Void> task = index.update(sticker);

            task.addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    Log.v("Sticker", "Successfully added to index " + filename);
                }
            });

            task.addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Log.d("Sticker", "Failed to add to index " + filename, e);
                }
            });
        } catch (FirebaseAppIndexingInvalidArgumentException e) {
            Log.e("Sticker", e.toString());
        }
    }
}
