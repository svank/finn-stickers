package net.samvankooten.finnstickers.editor.renderer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import net.samvankooten.finnstickers.editor.EditorActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class JpegStrategy extends RenderStrategy {
    private Bitmap background;
    private Context context;
    private Bitmap textData;
    
    private static final float SCALE_FACTOR = 1.6f;
    
    public JpegStrategy(Context context) {
        this.context = context;
    }
    
    @Override
    public boolean loadImage(String location) {
        Uri uri;
        if (location.startsWith("/"))
            uri = Uri.fromFile(new File(location));
        else
            uri = Uri.parse(location);
        try {
            background = MediaStore.Images.Media.getBitmap(
                    context.getContentResolver(),
                    uri);
        } catch (IOException e) {
            Log.e(EditorActivity.TAG, "Error loading bitmap", e);
            return false;
        }
        return true;
    }
    
    @Override
    public boolean loadText(Bitmap textData) {
        this.textData = textData;
        return true;
    }
    
    @Override
    public int getTargetWidth() {
        // We don't want to restrict ourselves to the size of the sticker,
        // since the text will look better at high resolution. But we don't
        // want to make a really big sticker if the backing sticker doesn't
        // have the resolution to support it. So we'll do a multiple of the
        // backing sticker size. But if by chance we get a really big sticker,
        // don't scale up past the size of the text we're rendering.
        int scaledWidth = (int) (SCALE_FACTOR*background.getWidth());
        return scaledWidth > textData.getWidth() ? textData.getWidth() : scaledWidth;
    }
    
    @Override
    public int getTargetHeight() {
        int scaledHeight = (int) (SCALE_FACTOR*background.getHeight());
        return scaledHeight > textData.getHeight() ? textData.getHeight() : scaledHeight;
    }
    
    @Override
    public boolean renderImage(File dest) {
        Bitmap result = Bitmap.createBitmap(getTargetWidth(), getTargetHeight(), Bitmap.Config.ARGB_8888);
        Canvas resultCanvas = new Canvas(result);
        Matrix matrix = new Matrix();
        matrix.setScale(
                (float) getTargetWidth() / background.getWidth(),
                (float) getTargetHeight() / background.getHeight());
        resultCanvas.drawBitmap(background, matrix, null);
        
        matrix.setScale(
                (float) getTargetWidth() / textData.getWidth(),
                (float) getTargetHeight() / textData.getHeight());
        resultCanvas.drawBitmap(textData, matrix, null);
        
        try {
            FileOutputStream stream = new FileOutputStream(dest);
            result.compress(Bitmap.CompressFormat.JPEG, 50, stream);
            stream.close();
        } catch (IOException e) {
            Log.e(EditorActivity.TAG, "Error saving sticker", e);
            return false;
        }
        return true;
    }
    
}
