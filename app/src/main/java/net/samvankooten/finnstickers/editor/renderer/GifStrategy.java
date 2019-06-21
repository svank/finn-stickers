package net.samvankooten.finnstickers.editor.renderer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.util.Log;

import com.waynejo.androidndkgif.GifDecoder;
import com.waynejo.androidndkgif.GifEncoder;
import com.waynejo.androidndkgif.GifImage;
import com.waynejo.androidndkgif.GifImageIterator;

import net.samvankooten.finnstickers.StickerProvider;

import java.io.File;
import java.io.FileNotFoundException;

public class GifStrategy extends RenderStrategy {
    private static final String TAG = "GifStrategy";
    private String location;
    private Context context;
    private int targetWidth;
    private int targetHeight;
    
    public GifStrategy(Context context) {
        this.context = context;
    }
    @Override
    public boolean loadImage(String location) {
        if (!location.startsWith("/"))
            location = new StickerProvider(context).uriToFile(location).toString();
        this.location = location;
        final GifImageIterator iterator = new GifDecoder()
                                            .loadUsingIterator(location);
        if (!iterator.hasNext()) {
            Log.e(TAG, "No first frame");
            iterator.close();
            return false;
        }
        GifImage frame = iterator.next();
        if (frame == null) {
            Log.e(TAG, "Got null first frame");
            iterator.close();
            return false;
        }
        Bitmap bitmap = frame.bitmap;
        targetWidth = bitmap.getWidth();
        targetHeight = bitmap.getHeight();
        iterator.close();
        return true;
    }
    
    @Override
    public int getTargetWidth() {
        return targetWidth;
    }
    
    @Override
    public int getTargetHeight() {
        return targetHeight;
    }
    
    @Override
    public boolean renderImage(Bitmap textData, File dest) {
        final GifImageIterator iterator = new GifDecoder()
                .loadUsingIterator(location);
        GifEncoder encoder = new GifEncoder();
        try {
            encoder.init(targetWidth, targetHeight, dest.toString(), GifEncoder.EncodingType.ENCODING_TYPE_STABLE_HIGH_MEMORY
            );
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Error saving gif", e);
            return false;
        }
        while (iterator.hasNext()) {
            GifImage frame = iterator.next();
            if (frame == null) {
                Log.e(TAG, "Got null frame");
                break;
            }
            Bitmap background = frame.bitmap;
    
            Bitmap result = Bitmap.createBitmap(getTargetWidth(), getTargetHeight(), Bitmap.Config.ARGB_8888);
            Canvas resultCanvas = new Canvas(result);
            Matrix matrix = new Matrix();
            matrix.setScale(
                    (float) getTargetWidth() / background.getWidth(),
                    (float) getTargetHeight() / background.getHeight());
            resultCanvas.drawBitmap(background, matrix, null);
            resultCanvas.drawBitmap(textData, 0, 0, null);
            
            encoder.encodeFrame(result, frame.delayMs);
        }
        iterator.close();
        encoder.close();
        return true;
    }
}
