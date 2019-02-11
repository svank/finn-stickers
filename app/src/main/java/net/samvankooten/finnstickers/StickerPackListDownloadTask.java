package net.samvankooten.finnstickers;

import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;

import net.samvankooten.finnstickers.utils.DownloadCallback;
import net.samvankooten.finnstickers.utils.NotificationUtils;
import net.samvankooten.finnstickers.utils.Util;

import java.io.File;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

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
            Util.AllPacksResult result = Util.getInstalledAndAvailablePacks(packListURL, iconsDir, dataDir, context);
            
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
    private void checkForNewPacks(List<StickerPack> packList) {
        SharedPreferences prefs = Util.getPrefs(context);
        Set<String> knownPacks = Util.getMutableStringSetFromPrefs(prefs, Util.KNOWN_PACKS);
        final int origKnownPacksCount = knownPacks.size();
        
        List<StickerPack> newPacks = new LinkedList<>();
        
        if (knownPacks.size() == 0) {
            newPacks = packList;
        }
        else {
            for (StickerPack pack : packList) {
                if (!knownPacks.contains(pack.getPackname()))
                    newPacks.add(pack);
            }
        }
        
        for (StickerPack pack : newPacks)
            knownPacks.add(pack.getPackname());
        
        // Don't notify for new packs if the app's never been formally opened
        // (don't think we should ever hit this condition) or if there
        // were no known packs (i.e. this is the first time we're downloading
        // a pack list.)
        if (origKnownPacksCount > 0 && Util.checkIfEverOpened(context)) {
            // Notify for each new pack
            for (StickerPack pack : newPacks) {
                Notification n = NotificationUtils.buildNewPackNotification(context, pack);
                NotificationUtils.showNotification(context, n);
            }
        }
        
        // Save the new list of known packs
        SharedPreferences.Editor editor = prefs.edit();
        editor.putStringSet(Util.KNOWN_PACKS, knownPacks);
        editor.apply();
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