package net.samvankooten.finnstickers;

import android.app.FragmentManager;
import android.app.Notification;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by sam on 10/22/17.
 */

public class StickerPack implements DownloadCallback<StickerPackDownloadTask.Result>, Serializable {
    public static final String TAG = "StickerPack";
    private String packname;
    private String iconurl;
    private String packBaseDir;
    private String urlBase;
    private String datafile;
    private File iconfile;
    private String jsonSavePath;
    private String extraText;
    private String description;
    private Status status;
    private List<String> stickerURLs = null;
    private List<String> stickerURIs = null;
    private int version;
    private List<String> updatedURIs = null;
    private long updatedTimestamp = 0;
    
    private transient List<String> oldURIs = null;
    
    private transient StickerPack replaces = null;
    
    private transient StickerPackAdapter adapter = null;
    private transient Context context = null;
    private transient NetworkFragment mNetworkFragment = null;
    
    public enum Status {UNINSTALLED, INSTALLING, INSTALLED, UPDATEABLE}

    public static StickerPack[] getStickerPacks(URL url, File iconDir, List<StickerPack> list) throws JSONException, IOException{
        Util.DownloadResult result;

        // Get the data at the URL\
        result = Util.downloadFromUrl(url);

        // Parse the list of packs out of the JSON data
        JSONObject json = new JSONObject(result.readString(20000));
        JSONArray packs = json.getJSONArray("packs");

        // Parse each StickerPack JSON object and download icons
        for (int i = 0; i < packs.length(); i++) {
            JSONObject packData = packs.getJSONObject(i);
            StickerPack availablePack = new StickerPack(packData, Util.getURLPath(url));
            
            // Is this pack already in the list? i.e. is this an installed pack?
            boolean add = true;
            for (StickerPack installedPack : list) {
                if (installedPack.equals(availablePack)) {
                    if (availablePack.getVersion() <= installedPack.getVersion()) {
                        Log.d(TAG, "Skipping already-installed pack " + installedPack.getPackname());
                        add = false;
                        break;
                    } else {
                        availablePack.setStatus(Status.UPDATEABLE);
                        availablePack.setReplaces(installedPack);
                        list.remove(installedPack);
                        break;
                    }
                }
            }
            if (add)
                list.add(availablePack);

            File destination = new File(iconDir, availablePack.iconurl);
            try {
                URL iconURL = new URL(Util.getURLPath(url) + availablePack.iconurl);
                Util.downloadFile(iconURL, destination);
                availablePack.setIconfile(destination);
            } catch (Exception e) {
                Log.e(TAG, "Difficulty downloading pack icon", e);
            }
        }
        
        return list.toArray(new StickerPack[list.size()]);
    }

    public StickerPack(JSONObject data, String urlBase) throws JSONException {
        // Called for a non-installed pack
        this.packname = data.getString("packName");
        this.iconurl = data.getString("iconUrl");
        this.packBaseDir = data.getString("packBaseDir");
        this.datafile = data.getString("dataFile");
        this.extraText = data.getString("extraText");
        this.description = data.getString("description");
        this.urlBase = urlBase;
        this.version = data.getInt("version");
        this.iconfile = null;
        
        clearStickerData();
    }
    
    public void clearStickerData() {
        // Called after the pack's stickers' data has been deleted.
        // Restores this StickerPack to the state it would have if freshly-downloaded.
        this.jsonSavePath = "";
        this.status = Status.UNINSTALLED;
        this.stickerURLs = new LinkedList<>();
        this.stickerURIs = new LinkedList<>();
        this.updatedURIs = new LinkedList<>();
        this.updatedTimestamp = 0;
    }
    
    public StickerPack(JSONObject data) throws JSONException {
        // Called for an installed pack
        this.packname = data.getString("packName");
        this.iconurl = data.getString("iconUrl");
        this.packBaseDir = data.getString("packBaseDir");
        this.datafile = data.getString("dataFile");
        this.extraText = data.getString("extraText");
        this.description = data.getString("description");
        this.urlBase = data.getString("urlBase");
        this.iconfile = new File(data.getString("iconfile"));
        this.jsonSavePath = data.getString("jsonSavePath");
        this.status = Status.UNINSTALLED;
        this.version = data.getInt("version");
        this.stickerURLs = new LinkedList<>();
        this.stickerURIs = new LinkedList<>();
        JSONArray stickers = data.getJSONArray("stickers");
        for (int i=0; i<stickers.length(); i++) {
            JSONObject sticker = stickers.getJSONObject(i);
            stickerURLs.add(sticker.getString("url"));
            stickerURIs.add(sticker.getString("uri"));
        }
        
        JSONArray updatedURIs = data.getJSONArray("updatedURIs");
        this.updatedURIs = new LinkedList<>();
        for (int i=0; i<updatedURIs.length(); i++) {
            this.updatedURIs.add(updatedURIs.getString(i));
        }
        this.updatedTimestamp = data.getLong("updatedTimestamp");
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
            obj.put("jsonSavePath", jsonSavePath);
            obj.put("version", version);
            
            JSONArray stickers = new JSONArray();
            for (int i=0; i<stickerURLs.size(); i++) {
                JSONObject sticker = new JSONObject();
                sticker.put("url", stickerURLs.get(i));
                sticker.put("uri", stickerURIs.get(i));
                stickers.put(sticker);
            }
            obj.put("stickers", stickers);
            
            JSONArray updatedURIs = new JSONArray();
            for (int i=0; i<this.updatedURIs.size(); i++) {
                updatedURIs.put(this.updatedURIs.get(i));
            }
            obj.put("updatedURIs", updatedURIs);
            obj.put("updatedTimestamp", this.updatedTimestamp);
            
            
        } catch (JSONException e) {
            Log.e(TAG, "Error on JSON out", e);
        }
        return obj;
    }
    
    public void writeToFile(String filename) {
        jsonSavePath = filename;
        try {
            FileWriter file = new FileWriter(filename);
            file.write(createJSON().toString());
            file.close();
        } catch (IOException e) {
            Log.e(TAG, "Error writing to file", e);
        }
    }
    
    public void updateJSONFile() {
        if (jsonSavePath == null || jsonSavePath.equals(""))
            return;
        writeToFile(jsonSavePath);
    }
    
    public String buildURLString(String filename) {
        return urlBase + '/' + packBaseDir + '/' + filename;
    }
    
    public File buildFile(File base, String filename) {
        return new File(new File(base, packname), filename);
    }
    
    public Uri buildURI(String filename) {
        String path = Sticker.CONTENT_URI_ROOT + '/' + packname + '/' + filename;
        return Uri.parse(path);
    }
    
    public String buildJSONPath(File path) {
        return buildJSONPath(path, getPackname());
    }
    
    public static String buildJSONPath(File path, String packname) {
        return String.format("%s/%s.json", path, packname);
    }
    
    public void absorbFirebaseURLs(List<Sticker> stickers) {
        stickerURLs = new LinkedList<>();
        stickerURIs = new LinkedList<>();
        for (Sticker sticker : stickers) {
            stickerURLs.add(sticker.getURL());
            stickerURIs.add(sticker.getURI().toString());
        }
    }
    
    public void install(StickerPackAdapter adapter, Context context) {
        if (status != Status.UNINSTALLED)
            return;
        status = Status.INSTALLING;
        
        this.context = context;
        this.adapter = adapter;
    
        StickerPackDownloadTask task = new StickerPackDownloadTask(this, this, context);
        
        try {
            // Hook up to a NetworkFragment if we're launched by the MainActivity (i.e. GUI)
            final MainActivity activity = (MainActivity) context;
            FragmentManager fragmentManager = activity.getFragmentManager();
            mNetworkFragment = NetworkFragment.getInstance(fragmentManager, buildURLString(datafile));
            mNetworkFragment.startDownload(task);
        } catch (ClassCastException e) {
            // Just go ahead and run the background task if we're running in the background
            // (i.e. auto-update)
            task.execute(new Object());
        }
        
        Log.d(TAG, "launched task");
        if (oldURIs != null)
            Log.d(TAG, oldURIs.toString());
    }
    
    public void remove(Context context) {
        if (status != Status.INSTALLED) {
            return;
        }
        
        StickerProcessor.clearStickers(context, this);
        
        status = Status.UNINSTALLED;
    }
    
    public void update(StickerPackAdapter adapter, Context context) {
        if (status != Status.UPDATEABLE) {
            return;
        }
        
        oldURIs = replaces.getStickerURIs();
        Log.d(TAG, oldURIs.toString());
        
        replaces.remove(context);
        
        status = Status.UNINSTALLED;
        
        install(adapter, context);
    }
    
    public void updateFromDownload(StickerPackDownloadTask.Result result, Context context) {
        Log.d(TAG, "updateFromDownload");
        if (result.mException != null) {
            Log.e(TAG, "Exception in sticker install", result.mException);
            Toast.makeText(context, "Error: " + result.mException.toString(),
                    Toast.LENGTH_LONG).show();
            status = Status.UNINSTALLED;
        } else {
            status = Status.INSTALLED;
        }
    }
    
    public void showUpdateNotif() {
        if (oldURIs != null) {
            List<String> uris = UpdateManager.findNewStickers(oldURIs, getStickerURIs());
            if (uris.size() == 0)
                return;
            Notification n = NotificationUtils.buildNewStickerNotification(context, uris, this);
            this.updatedURIs = uris;
            this.updatedTimestamp = System.currentTimeMillis() / 1000L;
            updateJSONFile();
            NotificationUtils.showNotification(context, n);
            clearUpdateNotif();
        }
    }
    
    public void clearUpdateNotif() {
        oldURIs = null;
        context = null;
    }
    
    @Override
    public NetworkInfo getActiveNetworkInfo(Context context) {
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
        if (adapter != null) {
            adapter.notifyDataSetChanged();
            adapter = null;
        }
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
    
    public List<String> getStickerURLs() { return stickerURLs; }
    
    public String getJsonSavePath() { return jsonSavePath; }
    
    public String getURL() {
        return "finnstickers://sticker/pack/" + getPackname();
    }
    
    public int getVersion() { return version; }
    
    public void setReplaces(StickerPack replaces) { this.replaces = replaces; }
    
    public StickerPack getReplaces() { return this.replaces; }
    
    public List<String> getStickerURIs() { return stickerURIs; }
    
    public long getUpdatedTimestamp() { return updatedTimestamp; }
    
    public List<String> getUpdatedURIs() { return updatedURIs; }
}
