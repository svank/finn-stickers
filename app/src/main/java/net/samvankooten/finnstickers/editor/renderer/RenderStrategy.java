package net.samvankooten.finnstickers.editor.renderer;

import android.graphics.Bitmap;

import java.io.File;

abstract class RenderStrategy {
    public abstract boolean loadImage(String location);
    
    public abstract int getTargetWidth();
    
    public abstract int getTargetHeight();
    
    public abstract boolean renderImage(Bitmap textData, File dest);
}
