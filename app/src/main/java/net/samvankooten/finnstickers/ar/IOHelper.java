package net.samvankooten.finnstickers.ar;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;

import java.io.IOException;
import java.util.List;

import androidx.exifinterface.media.ExifInterface;

public abstract class IOHelper {
    private static final String TAG = "IOHelper";
    
    protected final Context context;
    
    IOHelper(Context context) {
        this.context = context;
    }
    
    abstract void loadPastImages(List<Uri> imageUris);
    
    abstract Uri saveImage(Bitmap pendingBitmap, int orientation);
    
    abstract void prepareVideoRecorder(VideoRecorder vr);
    
    abstract Uri finishVideoRecording(VideoRecorder vr);
    
    abstract boolean haveExtPermission();
    
    void applyImageOrientation(ExifInterface exifInterface, int orientation) throws IOException {
        // Save image orientation based on device orientation
        switch (orientation) {
            case 0:
                exifInterface.setAttribute(ExifInterface.TAG_ORIENTATION,
                        String.valueOf(ExifInterface.ORIENTATION_NORMAL));
                break;
            case 90:
                exifInterface.setAttribute(ExifInterface.TAG_ORIENTATION,
                        String.valueOf(ExifInterface.ORIENTATION_ROTATE_90));
                break;
            case 180:
                exifInterface.setAttribute(ExifInterface.TAG_ORIENTATION,
                        String.valueOf(ExifInterface.ORIENTATION_ROTATE_180));
                break;
            case 270:
                exifInterface.setAttribute(ExifInterface.TAG_ORIENTATION,
                        String.valueOf(ExifInterface.ORIENTATION_ROTATE_270));
                break;
        }
        exifInterface.saveAttributes();
    }
}
