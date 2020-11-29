package net.samvankooten.finnstickers.ar;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;

import net.samvankooten.finnstickers.utils.Util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import androidx.exifinterface.media.ExifInterface;

public class IOHelperSAF extends IOHelper {
    private static final String TAG = "IOHelperSAF";
    private static final String SAVE_PATH = "DCIM/Finn Stickers";
    
    IOHelperSAF(Context context) {
        super(context);
    }
    
    @TargetApi(30)
    @Override
    void loadPastImages(List<Uri> imageUris) {
        Uri collection;
        collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        
        String[] projection = new String[] { MediaStore.Files.FileColumns._ID };
        String selection = MediaStore.Files.FileColumns.OWNER_PACKAGE_NAME + " = ?";
        String[] selectionArgs = new String[] {
                context.getApplicationContext().getPackageName()
        };
        String sortOrder = MediaStore.Files.FileColumns.DATE_ADDED + " DESC";
        
        try (Cursor cursor = context.getApplicationContext().getContentResolver().query(
                collection,
                projection,
                selection,
                selectionArgs,
                sortOrder
        )) {
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID);
            while (cursor.moveToNext()) {
                long id = cursor.getLong(idColumn);
                Uri contentUri = MediaStore.Files.getContentUri(
                        MediaStore.VOLUME_EXTERNAL_PRIMARY, id);
                imageUris.add(contentUri);
            }
        }
    }
    
    @Override
    Uri saveImage(Bitmap pendingBitmap, int orientation) {
        String filename = generateFilename("jpg");
        return saveBitmapToDisk(filename, pendingBitmap, orientation);
    }
    
    @TargetApi(30)
    private Uri saveBitmapToDisk(String filename, Bitmap pendingBitmap, int orientation) {
        ContentResolver resolver = context.getApplicationContext()
                .getContentResolver();
        Uri collection;
        collection = MediaStore.Images.Media
                .getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        ContentValues details = new ContentValues();
        details.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
        details.put(MediaStore.Images.Media.RELATIVE_PATH, SAVE_PATH);
        details.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        details.put(MediaStore.Images.Media.IS_PENDING, 1);
        
        Uri outputURI = resolver.insert(collection, details);
        try {
            OutputStream stream = resolver.openOutputStream(outputURI, "w");
            pendingBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
            stream.flush();
            stream.close();
            
            ParcelFileDescriptor pfd =
                    resolver.openFileDescriptor(outputURI, "rw", null);
            ExifInterface exifInterface = new ExifInterface(pfd.getFileDescriptor());
            applyImageOrientation(exifInterface, orientation);
            pfd.close();
        } catch (IOException e) {
            e.printStackTrace();
            resolver.delete(outputURI, null, null);
            return null;
        }
        
        details.clear();
        details.put(MediaStore.Images.Media.IS_PENDING, 0);
        resolver.update(outputURI, details, null, null);
        return outputURI;
    }
    
    @TargetApi(30)
    @Override
    void prepareVideoRecorder(VideoRecorder vr) {
        ContentResolver resolver = context.getApplicationContext()
                .getContentResolver();
        Uri collection;
        collection = MediaStore.Video.Media
                .getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        ContentValues details = new ContentValues();
        details.put(MediaStore.Video.Media.DISPLAY_NAME, generateFilename("mp4"));
        details.put(MediaStore.Video.Media.RELATIVE_PATH, SAVE_PATH);
        details.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        details.put(MediaStore.Video.Media.IS_PENDING, 1);
        
        Uri outputURI = resolver.insert(collection, details);
        try {
            ParcelFileDescriptor pfd = resolver.openFileDescriptor(
                    outputURI, "w", null);
            vr.setVideoFileDescriptor(pfd.getFileDescriptor(), outputURI);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            resolver.delete(outputURI, null, null);
        }
    }
    
    @TargetApi(30)
    @Override
    Uri finishVideoRecording(VideoRecorder vr) {
        ContentResolver resolver = context.getApplicationContext()
                .getContentResolver();
        
        ContentValues details = new ContentValues();
        details.put(MediaStore.Video.Media.IS_PENDING, 0);
        
        resolver.update(vr.getVideoUri(), details, null, null);
        return vr.getVideoUri();
    }
    
    @Override
    boolean haveExtPermission() {
        return true;
    }
    
    String generateFilename(String suffix) {
        if (!suffix.startsWith("."))
            suffix = "." + suffix;
        
        return Util.generateUniqueFileName(null, suffix);
    }
}
