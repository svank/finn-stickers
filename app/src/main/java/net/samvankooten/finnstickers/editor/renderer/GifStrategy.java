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
    private Bitmap textData;
    
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
    public boolean loadText(Bitmap rawTextBitmap) {
        // Scale the text to our output size, so we have pre-scaled text
        // for each Gif frame
        textData = Bitmap.createBitmap(getTargetWidth(), getTargetHeight(),
                Bitmap.Config.ARGB_8888);
        final Canvas textCanvas = new Canvas(textData);
        final Matrix matrix = new Matrix();
        matrix.setScale(
                (float) getTargetWidth() / rawTextBitmap.getWidth(),
                (float) getTargetHeight() / rawTextBitmap.getHeight());
        textCanvas.drawBitmap(rawTextBitmap, matrix, null);
        return true;
    }
    
    @Override
    public int getTargetWidth() {
        // In JpegStrategy we scale up the output images, but since Gifs are
        // already larger files, maybe we shouldn't do that here.
        return targetWidth;
    }
    
    @Override
    public int getTargetHeight() {
        return targetHeight;
    }
    
    @Override
    public boolean renderImage(File dest) {
        final Bitmap renderedFrame = Bitmap.createBitmap(getTargetWidth(), getTargetHeight(),
                Bitmap.Config.ARGB_8888);
        final Canvas frameCanvas = new Canvas(renderedFrame);
        
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
        
        Matrix matrix = new Matrix();
        while (iterator.hasNext()) {
            GifImage frame = iterator.next();
            if (frame == null) {
                Log.e(TAG, "Got null frame");
                break;
            }
            Bitmap background = frame.bitmap;
            matrix.setScale(
                    (float) getTargetWidth() / background.getWidth(),
                    (float) getTargetHeight() / background.getHeight());
            frameCanvas.drawBitmap(background, matrix, null);
            frameCanvas.drawBitmap(textData, 0, 0, null);
            
            encoder.encodeFrame(renderedFrame, frame.delayMs);
        }
        iterator.close();
        encoder.close();
        return true;
    }
}
