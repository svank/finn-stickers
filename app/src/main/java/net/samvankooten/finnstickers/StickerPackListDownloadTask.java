package net.samvankooten.finnstickers;

import android.app.Notification;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.util.Log;

import com.google.firebase.appindexing.FirebaseAppIndex;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by sam on 10/22/17.
 */

public class StickerPackListDownloadTask extends AsyncTask<Object, Integer, StickerPackListDownloadTask.Result> {
    public static final String TAG = "StckrPckLstDownloadTask";
    
    private DownloadCallback<Result> mCallback;
    private URL packListURL;
    private File iconsDir;
    private File dataDir;
    private Context mContext;

    StickerPackListDownloadTask(DownloadCallback<Result> callback, Context context,
                                URL packListURL, File iconsDir, File dataDir) {
        setPackListURL(packListURL);
        setIconsDir(iconsDir);
        setCallback(callback);
        setDataDir(dataDir);
        setContext(context);
    }

    void setCallback(DownloadCallback<Result> callback) {
        mCallback = callback;
    }

    public void setPackListURL(URL packListURL) {
        this.packListURL = packListURL;
    }

    public void setIconsDir(File iconsDir) {
        this.iconsDir = iconsDir;
    }
    
    public void setDataDir(File dataDir) {
        this.dataDir = dataDir;
    }
    
    public void setContext(Context context) { mContext = context; }

    /**
     * Wrapper class that serves as a union of a result value and an exception. When the download
     * task has completed, either the result value or exception can be a non-null value.
     * This allows you to pass exceptions to the UI thread that were thrown during doInBackground().
     */
    class Result {
        public StickerPack[] mResultValue;
        public Exception mException;
        public Result(StickerPack[] resultValue) {
            mResultValue = resultValue;
        }
        public Result(Exception exception) {
            mException = exception;
        }
    }

    @Override
    protected void onPreExecute() {
        if (mCallback != null) {
            NetworkInfo networkInfo = mCallback.getActiveNetworkInfo(mContext);
            if (networkInfo == null || !networkInfo.isConnected() ||
                    (networkInfo.getType() != ConnectivityManager.TYPE_WIFI
                            && networkInfo.getType() != ConnectivityManager.TYPE_MOBILE)) {
                // If no connectivity, cancel task and update Callback with null data.
                mCallback.updateFromDownload(null, mContext);
                cancel(true);
            }
        }
    }

    /**
     * Defines work to perform on the background thread.
     */
    @Override
    protected Result doInBackground(Object... params) {
        if (isCancelled()) {
            return null;
        }
        try {
            List<StickerPack> list = StickerPack.getInstalledPacks(dataDir);
    
            StickerPack[] packList = StickerPack.getStickerPacks(packListURL, iconsDir, list);
            
            checkMigration(packList);
            
            File file = new File(dataDir, StickerPack.KNOWN_PACKS_FILE);
            if (file.exists() && file.isFile()) {
                ArrayList<StickerPack> newPacks = new ArrayList<>(Arrays.asList(packList));
                
                BufferedReader br = new BufferedReader(new FileReader(file));
                String line;
                while ((line = br.readLine()) != null) {
                    for (int i = 0; i < newPacks.size(); i++) {
                        if (newPacks.get(i).getPackname().equals(line)) {
                            newPacks.remove(i);
                            break;
                        }
                    }
                }
    
                for (StickerPack pack : newPacks) {
                    Notification n = NotificationUtils.buildNewPackNotification(mContext, pack);
                    NotificationUtils.showNotification(mContext, n);
                }
            }
            
            try {
                FileWriter writer = new FileWriter(file);
                for (StickerPack pack : packList) {
                    writer.write(pack.getPackname());
                    writer.write('\n');
                }
                writer.close();
            } catch (IOException e) {
                Log.e(TAG, "Error writing list of seen packs", e);
            }
            
            return new Result(packList);
        } catch (Exception e) {
            Log.e(TAG, "Error downloading sticker pack list", e);
            return new Result(e);
        }
    }
    
    /**
     * Updates the DownloadCallback with the result.
     */
    @Override
    protected void onPostExecute(Result result) {
        if (result != null && mCallback != null) {
            mCallback.updateFromDownload(result, mContext);
            mCallback.finishDownloading();
        }
    }

    /**
     * Override to add special behavior for cancelled AsyncTask.
     */
    @Override
    protected void onCancelled(Result result) {
    }
    
    private void checkMigration(StickerPack[] packList) {
        File testDir = new File(dataDir, "tongue");
        if (testDir.exists() && testDir.isDirectory()) {
            FirebaseAppIndex.getInstance().removeAll();
            for (File target : dataDir.listFiles()) {
                Util.delete(target);
            }
            
            for (StickerPack pack : packList) {
                if (pack.getPackname().equals("Finn")) {
                    pack.install(null, mContext);
                }
            }
            
            File file = new File(dataDir, StickerPack.KNOWN_PACKS_FILE);
            try {
                FileWriter writer = new FileWriter(file);
                writer.write("Finn\n");
                writer.close();
            } catch (IOException e) {
                Log.e(TAG, "Error migrating list of seen packs", e);
            }
        }
    }
}
