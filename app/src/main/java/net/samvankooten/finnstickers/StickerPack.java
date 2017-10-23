package net.samvankooten.finnstickers;

import android.app.FragmentManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 * Created by sam on 10/22/17.
 */

public class StickerPack implements DownloadCallback<StickerPackDownloadTask.Result>{
    public static final String TAG = "StickerPack";
    private String packname;
    private String iconurl;
    private String datafile;
    private File iconfile;
    private String extraText;
    private Status status;
    
    private StickerPackAdapter adapter = null;
    private Context context = null;
    private NetworkFragment mNetworkFragment = null;
    
    public enum Status {UNINSTALLED, INSTALLING, INSTALLED}

    public static StickerPack[] getStickerPacks(URL url, File iconDir) throws JSONException{
        Util.DownloadResult result;
        StickerPack[] list = null;

        // Get the data at the URL
        try {
            result = Util.downloadFromUrl(url);
        } catch (IOException e) {
            Log.e(TAG, "Error downloading sticker pack list", e);
            return list;
        }

        // Parse the list of packs out of the JSON data
        JSONObject json = new JSONObject(result.readString(20000));
        JSONArray packs = json.getJSONArray("packs");

        // Parse each StickerPack JSON object and download icons
        list = new StickerPack[packs.length()];
        for (int i = 0; i < packs.length(); i++) {
            JSONObject packData = packs.getJSONObject(i);
            StickerPack pack = new StickerPack(packData, Util.getURLPath(url));
            list[i] = pack;

            File destination = new File(iconDir, pack.iconurl);
            try {
                URL iconURL = new URL(Util.getURLPath(url) + pack.iconurl);
                Util.downloadSticker(iconURL, destination);
                pack.setIconfile(destination);
            } catch (Exception e) {
                Log.e(TAG, "Difficulty downloading pack icon", e);
            }
        }

        return list;
    }

    public StickerPack(JSONObject data, String urlBase) throws JSONException {
        this.packname = data.getString("packName");
        this.iconurl = data.getString("iconUrl");
        this.datafile = urlBase + data.getString("dataFile");
        this.extraText = data.getString("extraText");
        this.iconfile = null;
        this.status = Status.UNINSTALLED;
    }
    
    public void install(StickerPackAdapter adapter, MainActivity context) {
        if (status != Status.UNINSTALLED)
            return;
        status = Status.INSTALLING;
        
        this.context = context;
        this.adapter = adapter;
    
        FragmentManager fragmentManager = context.getFragmentManager();
        mNetworkFragment = NetworkFragment.getInstance(fragmentManager, datafile);
    
        StickerPackDownloadTask task = new StickerPackDownloadTask(this, datafile, context);
        mNetworkFragment.startDownload(task);
        Log.d(TAG, "launched task");
    }
    
    public void updateFromDownload(StickerPackDownloadTask.Result result) {
        Log.d(TAG, "updateFromDownload");
        if (result.mException != null) {
            Log.e(TAG, "Exception in sticker install", result.mException);
        }
    }
    
    @Override
    public NetworkInfo getActiveNetworkInfo() {
        if (context == null)
            return null;
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return connectivityManager.getActiveNetworkInfo();
    }
    
    @Override
    public void onProgressUpdate(int progressCode, int percentComplete) {
        switch(progressCode) {
            // TODO: add UI behavior for progress updates here.
            case Progress.ERROR:
                
                break;
            case Progress.CONNECT_SUCCESS:
                
                break;
            case Progress.GET_INPUT_STREAM_SUCCESS:
                
                break;
            case Progress.PROCESS_INPUT_STREAM_IN_PROGRESS:
                
                break;
            case Progress.PROCESS_INPUT_STREAM_SUCCESS:
                
                break;
        }
    }
    
    @Override
    public void finishDownloading() {
        Log.d(TAG, "finishDownloading");
        status = Status.INSTALLED;
        if (adapter != null) {
            adapter.notifyDataSetChanged();
            adapter = null;
        }
        context = null;
        if (mNetworkFragment != null) {
            mNetworkFragment.cancelDownload();
            mNetworkFragment = null;
        }
    }

    public String getPackname() {
        return packname;
    }

    public void setPackname(String packname) {
        this.packname = packname;
    }

    public String getIconurl() {
        return iconurl;
    }

    public void setIconurl(String iconurl) {
        this.iconurl = iconurl;
    }

    public String getDatafile() {
        return datafile;
    }

    public void setDatafile(String datafile) {
        this.datafile = datafile;
    }

    public File getIconfile() {
        return iconfile;
    }

    public void setIconfile(File iconfile) {
        this.iconfile = iconfile;
    }

    public String getExtraText() {
        return extraText;
    }

    public void setExtraText(String extraText) {
        this.extraText = extraText;
    }
    
    public Status getStatus() {
        return status;
    }
    
    public void setStatus(Status status) {
        this.status = status;
    }
}
