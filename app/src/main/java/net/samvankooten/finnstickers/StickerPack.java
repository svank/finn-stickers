package net.samvankooten.finnstickers;

import android.app.FragmentManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;

/**
 * Created by sam on 10/22/17.
 */

public class StickerPack implements DownloadCallback<StickerPackDownloadTask.Result>{
    public static final String TAG = "StickerPack";
    private String packname;
    private String iconurl;
    private String packBaseDir;
    private String urlBase;
    private String datafile;
    private File iconfile;
    private String extraText;
    private String description;
    private Status status;
    
    private StickerPackAdapter adapter = null;
    private Context context = null;
    private NetworkFragment mNetworkFragment = null;
    
    public enum Status {UNINSTALLED, INSTALLING, INSTALLED}

    public static StickerPack[] getStickerPacks(URL url, File iconDir, List<StickerPack> list) throws JSONException{
        Util.DownloadResult result;

        // Get the data at the URL
        try {
            result = Util.downloadFromUrl(url);
        } catch (IOException e) {
            Log.e(TAG, "Error downloading sticker pack list", e);
            return null;
        }

        // Parse the list of packs out of the JSON data
        JSONObject json = new JSONObject(result.readString(20000));
        JSONArray packs = json.getJSONArray("packs");

        // Parse each StickerPack JSON object and download icons
        for (int i = 0; i < packs.length(); i++) {
            JSONObject packData = packs.getJSONObject(i);
            StickerPack pack = new StickerPack(packData, Util.getURLPath(url));
            
            // Is this pack already in the list? i.e. is this an installed pack?
            boolean add = true;
            for (StickerPack p2 : list) {
                if (p2.equals(pack)) {
                    Log.d(TAG, "Skipping already-installed pack " + p2.getPackname());
                    add = false;
                    break;
                }
            }
            if (add)
                list.add(pack);

            File destination = new File(iconDir, pack.iconurl);
            try {
                URL iconURL = new URL(Util.getURLPath(url) + pack.iconurl);
                Util.downloadFile(iconURL, destination);
                pack.setIconfile(destination);
            } catch (Exception e) {
                Log.e(TAG, "Difficulty downloading pack icon", e);
            }
        }
        
        return list.toArray(new StickerPack[list.size()]);
    }

    public StickerPack(JSONObject data, String urlBase) throws JSONException {
        this.packname = data.getString("packName");
        this.iconurl = data.getString("iconUrl");
        this.packBaseDir = data.getString("packBaseDir");
        this.datafile = data.getString("dataFile");
        this.extraText = data.getString("extraText");
        this.description = data.getString("description");
        this.urlBase = urlBase;
        this.iconfile = null;
        this.status = Status.UNINSTALLED;
    }
    
    public StickerPack(JSONObject data) throws JSONException {
        this.packname = data.getString("packName");
        this.iconurl = data.getString("iconUrl");
        this.packBaseDir = data.getString("packBaseDir");
        this.datafile = data.getString("dataFile");
        this.extraText = data.getString("extraText");
        this.description = data.getString("description");
        this.urlBase = data.getString("urlBase");
        this.iconfile = new File(data.getString("iconfile"));
        this.status = Status.UNINSTALLED;
    }
    
    public JSONObject createJSON() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("packName", packname);
            obj.put("iconUrl", iconurl);
            obj.put("packBaseDir", packBaseDir);
            obj.put("dataFile", datafile);
            obj.put("extraText", extraText);
            obj.put("description", description);
            obj.put("urlBase", urlBase);
            obj.put("iconfile", iconfile.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error on JSON out", e);
        }
        return obj;
    }
    
    public String buildURLString(String filename) {
        StringBuilder builder = new StringBuilder();
        builder.append(urlBase);
        builder.append('/');
        builder.append(packBaseDir);
        builder.append('/');
        builder.append(filename);
        return builder.toString();
    }
    
    public File buildFile(File base, String filename) {
        return new File(new File(base, packname), filename);
    }
    
    public Uri buildURI(String filename) {
        StringBuilder path = new StringBuilder();
        path.append(Sticker.CONTENT_URI_ROOT);
        path.append('/');
        path.append(packname);
        path.append('/');
        path.append(filename);
        return Uri.parse(path.toString());
    }
    
    public void install(StickerPackAdapter adapter, MainActivity context) {
        if (status != Status.UNINSTALLED)
            return;
        status = Status.INSTALLING;
        
        this.context = context;
        this.adapter = adapter;
    
        FragmentManager fragmentManager = context.getFragmentManager();
        mNetworkFragment = NetworkFragment.getInstance(fragmentManager, datafile);
    
        StickerPackDownloadTask task = new StickerPackDownloadTask(this, this, context);
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
    
    public boolean equals(StickerPack other) {
        return getPackname().equals(other.getPackname());
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
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public Status getStatus() {
        return status;
    }
    
    public void setStatus(Status status) {
        this.status = status;
    }
}
