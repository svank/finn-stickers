package net.samvankooten.finnstickers;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import net.samvankooten.finnstickers.updating.UpdateManager;
import net.samvankooten.finnstickers.utils.DownloadCallback;
import net.samvankooten.finnstickers.utils.StickerPackProcessor;
import net.samvankooten.finnstickers.utils.Util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by sam on 10/22/17.
 */

public class StickerPack implements DownloadCallback<StickerPackDownloadTask.Result>, Serializable {
    private static final String TAG = "StickerPack";
    
    private String packname;
    private String iconurl;
    private File iconfile;
    private String packBaseDir;
    private String urlBase;
    private String datafile;
    private String extraText;
    private String description;
    private Status status;
    private List<String> stickerURLs = null;
    private List<String> stickerURIs = null;
    private int version;
    private List<String> updatedURIs = null;
    private long updatedTimestamp = 0;
    
    private StickerPack replaces = null;
    
    private transient InstallCompleteCallback installCallback = null;
    private List<String> removedURIs = null;
    
    public enum Status {UNINSTALLED, INSTALLING, INSTALLED, UPDATEABLE}
    
    /**
     * Builds a StickerPack from a JSONObject for a non-installed pack.
     * @param data JSON data
     * @param urlBase The URL of the server directory containing this pack
     */
    public StickerPack(JSONObject data, String urlBase) throws JSONException {
        this.packname = data.getString("packName");
        this.iconurl = data.getString("iconUrl");
        this.packBaseDir = data.getString("packBaseDir");
        this.datafile = "data.json";
        this.extraText = data.getString("extraText");
        this.description = data.getString("description");
        this.urlBase = urlBase;
        this.iconfile = null;
        this.version = data.getInt("version");
        
        uninstalledPackSetup();
    }
    
    /**
     * Initialization for uninstalled packs. Also converts an installed pack
     * to an uninstalled state after its files have been deleted.
     */
    public void uninstalledPackSetup() {
        this.status = Status.UNINSTALLED;
        this.stickerURLs = new LinkedList<>();
        this.stickerURIs = new LinkedList<>();
        this.updatedURIs = new LinkedList<>();
        this.updatedTimestamp = 0;
    }
    
    /**
     * Builds a StickerPack from a JSONObject for an installed pack.
     * @param data JSON data
     */
    public StickerPack(JSONObject data) throws JSONException {
        this.packname = data.getString("packName");
        this.iconurl = data.getString("iconUrl");
        this.packBaseDir = data.getString("packBaseDir");
        this.datafile = data.getString("dataFile");
        this.extraText = data.getString("extraText");
        this.description = data.getString("description");
        this.urlBase = data.getString("urlBase");
        this.iconfile = new File(data.getString("iconfile"));
        this.status = Status.INSTALLED;
        this.version = data.getInt("version");
        this.updatedTimestamp = data.getLong("updatedTimestamp");
        
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
    }
    
    public JSONObject toJSON() {
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
    
    public void updateSavedJSON(Context context) {
        SharedPreferences.Editor editor = Util.getPrefs(context).edit();
        editor.putString(Util.STICKER_PACK_DATA_PREFIX + getPackname(), toJSON().toString());
        editor.apply();
    }
    
    public void deleteSavedJSON(Context context) {
        SharedPreferences.Editor editor = Util.getPrefs(context).edit();
        editor.remove(Util.STICKER_PACK_DATA_PREFIX + getPackname());
        editor.apply();
    }
    
    /**
     * Returns the server URL of a given filename inside this pack's directory
     */
    public String buildURLString(String filename) {
        return urlBase + '/' + packBaseDir + '/' + filename;
    }
    
    /**
     * Given a sticker installation root directory (i.e. the data dir), returns the path to a
     * given file inside this pack.
     */
    public File buildFile(File base, String filename) {
        return new File(new File(base, packname), filename);
    }
    
    public Uri buildURI(String filename) {
        String path = Util.CONTENT_URI_ROOT + '/' + packname + '/' + filename;
        return Uri.parse(path);
    }
    
    /**
     * Given a list of Stickers, adds their URLs and URIs to this Pack's internal list.
     */
    public void absorbStickerData(List<Sticker> stickers) {
        stickerURLs = new LinkedList<>();
        stickerURIs = new LinkedList<>();
        for (Sticker sticker : stickers) {
            stickerURLs.add(sticker.getURL());
            stickerURIs.add(sticker.getURI().toString());
        }
    }
    
    public interface InstallCompleteCallback {
        void onInstallComplete();
    }
    
    /**
     * Installs this StickerPack
     * @param context Relevant Context
     * @param callback Callback for when installation is complete
     */
    public void install(Context context, InstallCompleteCallback callback, boolean async) {
        if (status != Status.UNINSTALLED)
            return;
        status = Status.INSTALLING;
        
        installCallback = callback;
        StickerPackDownloadTask task = new StickerPackDownloadTask(this, this, context);
        if (async)
            task.execute();
        else
            task.doInForeground();
    }
    
    public void uninstall(Context context) {
        if (status != Status.INSTALLED
            && status != Status.UPDATEABLE)
            return;
        
        new StickerPackProcessor(this, context).uninstallPack();
        Util.unregisterInstalledPack(this, context);
        
        removedURIs = stickerURIs;
        deleteSavedJSON(context);
        uninstalledPackSetup();
    }
    
    public void update(Context context, InstallCompleteCallback callback, boolean async) {
        if (status != Status.UPDATEABLE)
            return;
        
        replaces.uninstall(context);
        
        status = Status.UNINSTALLED;
        
        install(context, callback, async);
    }
    
    public void updateFromDownload(StickerPackDownloadTask.Result result, Context context) {
        if (result == null) {
            // No network access
            Toast.makeText(context, "No network connectivity",
                    Toast.LENGTH_SHORT).show();
            status = Status.UNINSTALLED;
            return;
        }
        
        if (result.exception != null) {
            Log.e(TAG, "Exception in sticker install", result.exception);
            Toast.makeText(context, "Error while installing sticker pack",
                    Toast.LENGTH_LONG).show();
            status = Status.UNINSTALLED;
        } else {
            status = Status.INSTALLED;
            Util.registerInstalledPack(this, context);
        }
    }
    
    public void checkForUpdatedStickers() {
        if (replaces == null || replaces.getStickerURIs() == null)
            return;
    
        updatedURIs = UpdateManager.findNewStickers(replaces.getRemovedURIs(), getStickerURIs());
        if (updatedURIs.size() != 0)
            this.updatedTimestamp = System.currentTimeMillis() / 1000L;
    }
    
    @Override
    public void finishDownloading() {
        if (installCallback != null)
            installCallback.onInstallComplete();
        installCallback = null;
    }
    
    public boolean equals(StickerPack other) {
        return getPackname().equals(other.getPackname());
    }
    
    public File generateCachedIconPath(File iconDir) {
        String suffix = iconurl.substring(iconurl.lastIndexOf("."));
        return new File(iconDir, packname + "-icon" + suffix);
    }
    
    public String getPackname() { return packname; }
    
    public String getIconurl() { return iconurl; }
    
    public String getDatafile() { return datafile; }
    
    public File getIconfile() { return iconfile; }
    
    public void setIconfile(File iconfile) { this.iconfile = iconfile; }
    
    public String getExtraText() { return extraText; }
    
    public String getDescription() { return description; }
    
    public Status getStatus() { return status; }
    
    public void setStatus(Status status) { this.status = status; }
    
    public List<String> getStickerURLs() { return stickerURLs; }
    
    public String getURL() { return "finnstickers://sticker/pack/" + getPackname(); }
    
    public int getVersion() { return version; }
    
    public void setReplaces(StickerPack replaces) { this.replaces = replaces; }
    
    public StickerPack getReplaces() { return this.replaces; }
    
    public List<String> getStickerURIs() { return stickerURIs; }
    
    public List<String> getRemovedURIs() { return removedURIs; }
    
    public long getUpdatedTimestamp() { return updatedTimestamp; }
    
    public List<String> getUpdatedURIs() { return updatedURIs; }
}
