package net.samvankooten.finnstickers.editor.renderer;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;

import net.samvankooten.finnstickers.Constants;
import net.samvankooten.finnstickers.StickerProvider;
import net.samvankooten.finnstickers.editor.DraggableTextManager;
import net.samvankooten.finnstickers.editor.EditorActivity;
import net.samvankooten.finnstickers.utils.Util;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.URL;

public class StickerRenderer {
    private static final String TAG = "StickerRenderer";
    
    public static File renderToFile(String baseImage, String packname, JSONObject customData,
                                       File dest, Context context) {
        RenderStrategy strategy = chooseStrategy(dest.toString(), context);
        
        JSONObject textData;
        try {
            textData = customData.getJSONObject("textData");
        } catch (JSONException e) {
            Log.e(TAG, "Error reading json data", e);
            return null;
        }
        
        if (packname != null)
            baseImage = makeUrlIfNeeded(baseImage, packname, context);
        boolean baseWasDownloaded = false;
        if (Util.stringIsURL(baseImage)) {
            try {
                String suffix = baseImage.substring(baseImage.lastIndexOf('.'));
                File destination = new File(context.getCacheDir(), "rendering_base" + suffix);
                Util.downloadFile(new URL(baseImage), destination);
                baseImage = destination.toString();
                baseWasDownloaded = true;
            } catch (IOException e) {
                Log.e(EditorActivity.TAG, "Error downloading from URL " + baseImage, e);
                return null;
            }
        }
        
        boolean success = strategy.loadImage(baseImage);
        if (!success)
            return null;
        
        DraggableTextManager manager = new DraggableTextManager(context, true);
        manager.loadJSON(textData);
        Bitmap text = DraggableTextManager.render(manager);
        strategy.loadText(text);
        strategy.setBackgroundIsFlipped(manager.isFlippedHorizontally());
        
        File outputFile = strategy.renderImage(dest);
        
        if (baseWasDownloaded)
            try {
                Util.delete(new File(baseImage));
            } catch (IOException e) {
                Log.e(TAG, "Error deleting base", e);
            }
        
        return outputFile;
    }
    
    private static RenderStrategy chooseStrategy(String filename, Context context) {
        String type = filename.substring(filename.lastIndexOf(".")+1);
        switch (type) {
            case "jpeg":
            case "jpg":
                return new JpegStrategy(context);
            case "gif":
                return new GifStrategy(context);
            default:
                Log.e(TAG, "Unable to infer strategy");
                return new JpegStrategy(context);
        }
    }
    
    public static String makeUrlIfNeeded(String filename, String packname, Context context) {
        if (Util.stringIsURL(filename))
            return filename;
        if (filename.startsWith("content")
                && new StickerProvider(context).uriToFile(filename).exists())
            return filename;
        if (filename.startsWith("file")
                && new File(Uri.parse(filename).getPath()).exists())
            return filename;
        return String.format("%s%s/%s", Constants.URL_BASE, Constants.URL_REMOVED_STICKER_DIR,
                filename.substring(filename.indexOf(packname)));
    }
}
