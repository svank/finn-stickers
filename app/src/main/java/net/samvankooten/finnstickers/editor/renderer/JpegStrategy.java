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
    public int getTargetWidth() {
        return background.getWidth();
    }
    
    @Override
    public int getTargetHeight() {
        return background.getHeight();
    }
    
    @Override
    public boolean renderImage(Bitmap textData, File dest) {
        Bitmap result = Bitmap.createBitmap(getTargetWidth(), getTargetHeight(), Bitmap.Config.ARGB_8888);
        Canvas resultCanvas = new Canvas(result);
        Matrix matrix = new Matrix();
        matrix.setScale(
                (float) getTargetWidth() / background.getWidth(),
                (float) getTargetHeight() / background.getHeight());
        resultCanvas.drawBitmap(background, matrix, null);
        resultCanvas.drawBitmap(textData, 0, 0, null);
        
        try {
            FileOutputStream stream = new FileOutputStream(dest);
            result.compress(Bitmap.CompressFormat.JPEG, 90, stream);
            stream.close();
        } catch (IOException e) {
            Log.e(EditorActivity.TAG, "Error saving sticker", e);
            return false;
        }
        return true;
    }
    
}
