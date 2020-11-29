package net.samvankooten.finnstickers.ar;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import net.samvankooten.finnstickers.utils.Util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;

public class IOHelperDirect extends IOHelper {
    private static final String TAG = "IOHelperDirect";
    
    IOHelperDirect(Context context) {
        super(context);
    }
    
    @Override
    void loadPastImages(List<Uri> imageUris) {
        if (!haveExtPermission())
            return;
        
        if (imageUris.size() > 0)
            // Past images must have been populated already.
            return;
        
        List<String> roots = generateOldPhotoRootPaths();
        roots.add(generatePhotoRootPath());
        
        for (String pathName : roots) {
            File path = new File(pathName);
            if (!path.exists())
                continue;
            
            File[] files = path.listFiles();
            if (files == null)
                continue;
            
            if (files.length > 1)
                Arrays.sort(files,
                        (object1, object2) ->
                        Long.compare(object1.lastModified(), object2.lastModified()));
            
            for (File file : files) {
                String strFile = file.toString();
                if (strFile.endsWith(".jpg") || strFile.endsWith(".mp4")) {
                    imageUris.add(0, generateSharableUri(file));
                }
            }
        }
    }
    
    @Override
    Uri saveImage(Bitmap pendingBitmap, int orientation) {
        String filename = generateFilename("jpg");
        boolean saveSuccess = saveBitmapToDisk(filename, pendingBitmap, orientation);
        if (saveSuccess) {
            notifySystemOfNewMedia(new File(filename));
            return generateSharableUri(new File(filename));
        }
        return null;
    }
    
    private boolean saveBitmapToDisk(String filename, Bitmap pendingBitmap, int orientation) {
        // Coming from https://codelabs.developers.google.com/codelabs/sceneform-intro/index.html?index=..%2F..%2Fio2018#14
        try (FileOutputStream outputStream = new FileOutputStream(filename);
             ByteArrayOutputStream outputData = new ByteArrayOutputStream()) {
            pendingBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputData);
            outputData.writeTo(outputStream);
            outputStream.flush();
            outputStream.close();
            
            ExifInterface exifInterface = new ExifInterface(filename);
            applyImageOrientation(exifInterface, orientation);
            return true;
        } catch (IOException ex) {
            Log.e(TAG, "Failed to save image " + ex.toString());
            Toast.makeText(context,
                    "Failed to save image", Toast.LENGTH_LONG).show();
            return false;
        }
    }
    
    @Override
    void prepareVideoRecorder(VideoRecorder vr) {
        vr.setVideoPath(new File(generateFilename("mp4")));
    }
    
    @Override
    Uri finishVideoRecording(VideoRecorder vr) {
        File path = vr.getVideoPath();
        notifySystemOfNewMedia(path);
        return generateSharableUri(path);
    }
    
    @Override
    boolean haveExtPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }
    
    String generateFilename(String suffix) {
        if (!suffix.startsWith("."))
            suffix = "." + suffix;
        
        String rootPath = generatePhotoRootPath();
        String fileName = Util.generateUniqueFileName(rootPath, suffix);
        
        File dir = new File(rootPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(rootPath, fileName).toString();
    }
    
    void notifySystemOfNewMedia(File path) {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        mediaScanIntent.setData(Uri.fromFile(path));
        context.sendBroadcast(mediaScanIntent);
    }
    
    private Uri generateSharableUri(File path) {
        return FileProvider.getUriForFile(context,
                "net.samvankooten.finnstickers.fileprovider", path);
    }
    
    private static String generatePhotoRootPath() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "Finn Stickers").toString();
    }
    
    private static ArrayList<String> generateOldPhotoRootPaths() {
        // We used to store pictures in this directory, so make sure we scan it
        ArrayList<String> out = new ArrayList<>();
        out.add(Environment.getExternalStorageDirectory() + File.separator + "DCIM"
                + File.separator + "Finn Stickers/");
        return out;
    }
}
