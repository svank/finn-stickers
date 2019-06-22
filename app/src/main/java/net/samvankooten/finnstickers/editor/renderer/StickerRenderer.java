package net.samvankooten.finnstickers.editor.renderer;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;

import net.samvankooten.finnstickers.StickerProvider;
import net.samvankooten.finnstickers.editor.DraggableTextManager;
import net.samvankooten.finnstickers.editor.EditorActivity;
import net.samvankooten.finnstickers.utils.Util;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.URL;

public class StickerRenderer {
    public static final String TAG = "StickerRenderer";
    
    public static boolean renderToFile(String baseImage, String packname, JSONObject textData,
                                       File dest, Context context) {
        RenderStrategy strategy = chooseStrategy(dest.toString(), context);
        if (strategy == null) {
            return false;
        }
        baseImage = makeUrlIfNeeded(baseImage, packname, context);
        if (Util.stringIsURL(baseImage)) {
            try {
                String suffix = baseImage.substring(baseImage.lastIndexOf('.'));
                File destination = new File(context.getCacheDir(), "rendering_base" + suffix);
                Util.downloadFile(new URL(baseImage), destination);
                baseImage = dest.toString();
            } catch (IOException e) {
                Log.e(EditorActivity.TAG, "Error downloading from URL " + baseImage, e);
                return false;
            }
        }
        
        boolean success = strategy.loadImage(baseImage);
        if (!success)
            return false;
        
        final int targetWidth = strategy.getTargetWidth();
        final int targetHeight = strategy.getTargetHeight();
        
        Bitmap text = DraggableTextManager.render(context, textData, targetWidth, targetHeight);
        return strategy.renderImage(text, dest);
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
                Log.e(TAG, "Unable to choose strategy");
                return null;
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
        return String.format("%s%s/%s", Util.URL_BASE, Util.URL_REMOVED_STICKER_DIR,
                filename.substring(filename.indexOf(packname)));
    }
}