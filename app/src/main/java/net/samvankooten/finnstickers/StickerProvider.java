package net.samvankooten.finnstickers;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import net.samvankooten.finnstickers.utils.Util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Provider that makes the stickers queryable by other applications.
 * Yanked directly from the Firebase app_indexing demo app.
 */
public class StickerProvider extends ContentProvider {
    private static final String TAG = "StickerProvider";
    private final static String[] OPENABLE_PROJECTION= {
            OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE };
    
    @Nullable private File rootDir;
    
    public StickerProvider() {
        super();
    }
    
    public StickerProvider(Context c) {
        super();
        setRootDir(c);
    }
    
    @Override
    public boolean onCreate() {
        final Context context = getContext();
        Util.performNeededMigrations(context);
        if (context != null) {
            setRootDir(context);
        }
        return rootDir != null;
    }
    
    public StickerProvider setRootDir(Context c) {
        rootDir = chooseRootDir(c);
        return this;
    }
    
    public static File chooseRootDir(Context c) {
        File rootDir = new File(c.getFilesDir(), "");
        try {
            rootDir = rootDir.getCanonicalFile();
        } catch (IOException e) {
            rootDir = null;
        }
        
        return rootDir;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        final File file = uriToFile(uri);
        if (!isFileInRoot(file)) {
            throw new SecurityException("File is not in root: " + file);
        }
        return getMimeType(file);
    }

    @Nullable
    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode)
            throws FileNotFoundException {
        final File file = uriToFile(uri);
        if (!isFileInRoot(file)) {
            throw new SecurityException("File is not in root: " + file);
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }
    
    public File uriToFile(@NonNull Uri uri) {
        if (rootDir == null) {
            throw new IllegalStateException("Root directory is null");
        }
        File file = new File(rootDir, uri.getEncodedPath());
        try {
            file = file.getCanonicalFile();
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to get canonical file: " + file);
        }
        return file;
    }
    
    public File uriToFile(@NonNull String uri) {
        return uriToFile(Uri.parse(uri));
    }
    
    public Uri fileToUri(@NonNull String file) {
        return fileToUri(new File(file));
    }
    
    public Uri fileToUri(@NonNull File file) {
        if (rootDir == null) {
            throw new IllegalStateException("Root directory is null");
        }
        try {
            file = file.getCanonicalFile();
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to get canonical file: " + file);
        }
        String rel = rootDir.toURI().relativize(file.toURI()).getPath();
        return Uri.parse(Constants.CONTENT_URI_ROOT + rel);
    }

    private boolean isFileInRoot(@NonNull File file) {
        return rootDir != null && file.getPath().startsWith(rootDir.getPath());
    }

    private String getMimeType(@NonNull File file) {
        String mimeType = null;
        final String extension = getFileExtension(file);
        if (extension != null) {
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }
        if (mimeType == null) {
            mimeType = "application/octet-stream";
        }
        return mimeType;
    }

    @Nullable
    private String getFileExtension(@NonNull File file) {
        String extension = null;
        final String filename = file.getName();
        final int index = filename.lastIndexOf('.');
        if (index >= 0) {
            extension = filename.substring(index + 1);
        }
        return extension;
    }
    
    private long getUriFileSize(Uri uri) {
        File file = uriToFile(uri);
        return file.length();
    }
    
    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, Bundle queryArgs, CancellationSignal cancellationSignal) {
        // If we used the file-picker interface, the application receiving the sticker might
        // ask for a filename to associate with the data.
    
        // Here's a stub from
        // https://github.com/commonsguy/cw-omnibus/blob/master/ContentProvider/Pipe/app/src/main/java/com/commonsware/android/cp/pipe/AbstractFileProvider.java
        if (projection == null) {
            projection = OPENABLE_PROJECTION;
        }
    
        final MatrixCursor cursor = new MatrixCursor(projection, 1);
    
        MatrixCursor.RowBuilder b = cursor.newRow();
    
        for (String col : projection) {
            if (OpenableColumns.DISPLAY_NAME.equals(col)) {
                if (uri.toString().endsWith(".gif"))
                    b.add("sticker.gif");
                else
                    b.add("sticker.jpeg");
            }
            else if (OpenableColumns.SIZE.equals(col)) {
                b.add(getUriFileSize(uri));
            }
            else { // unknown, so just add null
                b.add(null);
            }
        }
    
        return cursor;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return query(uri, projection, null, null);
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        throw new UnsupportedOperationException("no inserts");
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException("no deletes");
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException("no updates");
    }
}