package net.samvankooten.finnstickers.editor;

import android.graphics.Bitmap;
import android.graphics.Canvas;

import androidx.annotation.NonNull;

public class UnClippableCanvas extends Canvas {
    
    public UnClippableCanvas(@NonNull Bitmap bitmap) {
        super(bitmap);
    }
    
    @Override
    public boolean clipRect(float left, float top, float right, float bottom) {
        return true;
    }
}