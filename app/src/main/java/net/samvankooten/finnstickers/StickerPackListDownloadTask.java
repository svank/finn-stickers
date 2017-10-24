package net.samvankooten.finnstickers;

import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.URL;
import java.util.LinkedList;
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

    StickerPackListDownloadTask(DownloadCallback<Result> callback,
                                URL packListURL, File iconsDir, File dataDir) {
        setPackListURL(packListURL);
        setIconsDir(iconsDir);
        setCallback(callback);
        setDataDir(dataDir);
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
    }

    /**
     * Defines work to perform on the background thread.
     */
    @Override
    protected Result doInBackground(Object... params) {
        try {
            List list = new LinkedList<StickerPack>();
            for (File file : dataDir.listFiles()) {
                if (!file.isFile())
                    continue;
                
                String name = file.getName();
                if (name.length() < 5 || !name.substring(name.length()-5).equals(".json"))
                    continue;
                
                Log.d(TAG, "Loading json file " + file.toString());
                
                File src = new File(dataDir, name);
                
                // This is the easiest way I could find to read a text file in Android/Java.
                // There really ought to be a better way!
                StringBuilder data = new StringBuilder();
                BufferedReader br = new BufferedReader(new FileReader(file));
                String line;
                while ((line = br.readLine()) != null) {
                    data.append(line);
                    data.append('\n');
                }
                br.close();
                
                JSONObject obj = new JSONObject(data.toString());
                StickerPack pack = new StickerPack(obj);
                pack.setStatus(StickerPack.Status.INSTALLED);
                list.add(pack);
            }
            
            StickerPack[] packList = StickerPack.getStickerPacks(packListURL, iconsDir, list);
            Log.d(TAG, String.format("Downloaded %d sticker packs", packList.length));
            return new Result(packList);
        } catch (Exception e) {
            return new Result(e);
        }
    }

    /**
     * Updates the DownloadCallback with the result.
     */
    @Override
    protected void onPostExecute(Result result) {
        if (result != null && mCallback != null) {
            mCallback.updateFromDownload(result);
//                mCallback.finishDownloading();
        }
    }

    /**
     * Override to add special behavior for cancelled AsyncTask.
     */
    @Override
    protected void onCancelled(Result result) {
    }
}
