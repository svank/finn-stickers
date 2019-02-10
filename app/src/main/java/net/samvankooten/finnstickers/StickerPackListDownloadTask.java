package net.samvankooten.finnstickers;

import android.app.Notification;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import net.samvankooten.finnstickers.utils.DownloadCallback;
import net.samvankooten.finnstickers.utils.NotificationUtils;
import net.samvankooten.finnstickers.utils.Util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by sam on 10/22/17.
 */

public class StickerPackListDownloadTask extends AsyncTask<Object, Void, StickerPackListDownloadTask.Result> {
    private static final String TAG = "StckrPckLstDownloadTask";
    
    private DownloadCallback<Result> callback;
    private final URL packListURL;
    private final File iconsDir;
    private final File dataDir;
    private Context context;
    
    public StickerPackListDownloadTask(DownloadCallback<Result> callback, Context context,
                                URL packListURL, File iconsDir, File dataDir) {
        this.packListURL = packListURL;
        this.iconsDir = iconsDir;
        this.callback = callback;
        this.dataDir = dataDir;
        this.context = context;
    }
    
    /**
     * Wrapper class that serves as a union of a result value and an exception. When the download
     * task has completed, either the result value or exception can be a non-null value.
     * This allows you to pass exceptions to the UI thread that were thrown during doInBackground().
     */
    public class Result {
        public List<StickerPack> packs;
        public boolean networkSucceeded = false;
        public Exception exception;
        public Result(Util.AllPacksResult result) {
            packs = result.list;
            networkSucceeded = result.networkSucceeded;
        }
        public Result(Exception exception) {
            this.exception = exception;
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
            Util.AllPacksResult result = Util.getInstalledAndAvailablePacks(packListURL, iconsDir, dataDir);
            
            if (result.networkSucceeded)
                checkForNewPacks(result.list);
            
            return new Result(result);
        } catch (Exception e) {
            Log.e(TAG, "Error downloading sticker pack list", e);
            return new Result(e);
        }
    }
    
    /**
     * Checks a list of StickerPacks to see if any are new (never seen before by this app),
     * notifies the user if any are found, and updates the saved list of seen-before packs.
     */
    private void checkForNewPacks(List<StickerPack> packList) throws IOException {
        File file = new File(dataDir, Util.KNOWN_PACKS_FILE);
        if (file.exists() && file.isFile()) {
            // Check if there are any new packs---available packs not listed in
            // the known packs file. We'll copy the pack list and then remove
            // every pack that is known.
            LinkedList<StickerPack> newPacks = new LinkedList<>(packList);
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
            
            // Notify for each new pack
            for (StickerPack pack : newPacks) {
                Notification n = NotificationUtils.buildNewPackNotification(context, pack);
                NotificationUtils.showNotification(context, n);
            }
        }
        
        // Write out a new list of known packs
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
    }
    
    /**
     * Updates the DownloadCallback with the result.
     */
    @Override
    protected void onPostExecute(Result result) {
        if (callback != null && context != null) {
            callback.updateFromDownload(result, context);
            callback.finishDownloading();
        }
        callback = null;
        context = null;
    }
    
    protected void onCancelled(Result result) {
        onPostExecute(result);
    }
}