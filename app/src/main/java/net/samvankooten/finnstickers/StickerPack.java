package net.samvankooten.finnstickers;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import net.samvankooten.finnstickers.updating.UpdateUtils;
import net.samvankooten.finnstickers.utils.DownloadCallback;
import net.samvankooten.finnstickers.utils.StickerPackProcessor;
import net.samvankooten.finnstickers.utils.StickerPackRepository;
import net.samvankooten.finnstickers.utils.Util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/**
 * Created by sam on 10/22/17.
 */

public class StickerPack implements DownloadCallback<StickerPackDownloadTask.Result>, Comparable<StickerPack> {
    private static final String TAG = "StickerPack";
    
    private String packname;
    private String iconLocation;
    private String packBaseDir;
    private String urlBase;
    private String datafile;
    private String extraText;
    private String description;
    private List<Sticker> stickers = null;
    private int version;
    private List<String> updatedURIs = null;
    private long updatedTimestamp = 0;
    private int displayOrder = 0;
    private int stickerCount;
    private long totalSize;
    
    private StickerPack replaces = null;
    private StickerPack remoteVersion = null;
    
    /*
    liveStatus is for UI to observe. Since the pack's status is sometimes updated in a background
    thread when liveStatus can't be updated directly (i.e. only by posting an update task to run
    on the main thread), keeping status strictly in a LiveData might mean the status is at times
    out of sync with the actual status of the StickerPack, and so we keep status and liveStatus as
    separate things. liveStatus should be used for UI matters, and status itself for checking the
    state of a Pack before performing actions.
     */
    private Status status;
    private MutableLiveData<Status> liveStatus = new MutableLiveData<>();
    
    private InstallCompleteCallback installCallback = null;
    private List<String> replacedUris = null;
    
    public enum Status {UNINSTALLED, INSTALLING, INSTALLED, UPDATEABLE}
    
    /**
     * Creates an empty StickerPack
     */
    public StickerPack() {
        uninstalledPackSetup();
        packname = "";
        iconLocation = "";
        packBaseDir = "";
        datafile = "";
        extraText = "";
        description = "";
        urlBase = "";
        version = 1;
        displayOrder = 0;
        stickerCount = 0;
        totalSize = 0;
        setStatus(Status.INSTALLED);
    }
    
    /**
     * Builds a StickerPack from a JSONObject for a non-installed pack.
     * @param data JSON data
     * @param urlBase The URL of the server directory containing this pack
     */
    public StickerPack(JSONObject data, String urlBase) throws JSONException {
        commonSetup(data);
        
        iconLocation = urlBase + '/' + data.getString("iconUrl");
        datafile = data.getString("dataFile");
        this.urlBase = urlBase;
        
        stickerCount = data.has("stickerCount") ? data.getInt("stickerCount") : 0;
        totalSize = data.has("totalSize") ? data.getInt("totalSize") : 0;
        
        uninstalledPackSetup();
    }
    
    /**
     * Initialization for uninstalled packs. Also converts an installed pack
     * to an uninstalled state after its files have been deleted.
     */
    public void uninstalledPackSetup() {
        if (remoteVersion != null) {
            stickers = remoteVersion.getStickers();
            stickerCount = remoteVersion.getStickerCount();
            totalSize = remoteVersion.getTotalSize();
            iconLocation = remoteVersion.getIconLocation();
            datafile = remoteVersion.getDatafile();
            urlBase = remoteVersion.getUrlBase();
            remoteVersion = null;
        } else {
            stickers = new LinkedList<>();
        }
        setStatus(Status.UNINSTALLED);
        updatedURIs = new LinkedList<>();
        updatedTimestamp = 0;
        replaces = null;
    }
    
    /**
     * Builds a StickerPack from a JSONObject for an installed pack.
     * @param data JSON data
     */
    public StickerPack(JSONObject data, Context context) throws JSONException {
        if (status == Status.INSTALLED || status == Status.INSTALLING)
            return;
        commonSetup(data);
        
        iconLocation = data.getString("iconLocation");
        setStatus(Status.INSTALLED);
        updatedTimestamp = data.getLong("updatedTimestamp");
        
        JSONArray stickers = data.getJSONArray("stickers");
        this.stickers = new ArrayList<>(stickers.length());
        for (int i=0; i<stickers.length(); i++)
            this.stickers.add(new Sticker(stickers.getJSONObject(i)));
        
        stickerCount = stickers.length();
        totalSize = Util.dirSize(buildFile(context.getFilesDir(), ""));
        
        JSONArray updatedURIs = data.getJSONArray("updatedURIs");
        this.updatedURIs = new ArrayList<>(updatedURIs.length());
        for (int i=0; i<updatedURIs.length(); i++)
            this.updatedURIs.add(updatedURIs.getString(i));
    }
    
    private void commonSetup(JSONObject data) throws JSONException {
        packBaseDir = data.getString("packBaseDir");
        packname = data.getString("packName");
        extraText = data.getString("extraText");
        description = data.getString("description");
        version = data.getInt("version");
        if (data.has("displayOrder"))
            displayOrder = data.getInt("displayOrder");
    }
    
    /**
     * For uninstalled packs, if we refresh the data from the server, we can keep the StickerPack
     * as a singleton instance by having the existing instance copy the updateable data from the
     * new instance, so the old instance gets the freshest data.
     * @param data JSONObject to copy data from
     */
    public void copyFreshDataFrom(JSONObject data) throws JSONException {
        commonSetup(data);
        iconLocation = urlBase + '/' + data.getString("iconUrl");
        datafile = data.getString("dataFile");
    
        stickerCount = data.has("stickerCount") ? data.getInt("stickerCount") : stickerCount;
        totalSize = data.has("totalSize") ? data.getInt("totalSize") : totalSize;
    }
    
    public JSONObject toJSON() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("packName", packname);
            obj.put("packBaseDir", packBaseDir);
            obj.put("extraText", extraText);
            obj.put("description", description);
            obj.put("iconLocation", iconLocation);
            obj.put("version", version);
            obj.put("updatedTimestamp", this.updatedTimestamp);
            obj.put("displayOrder", displayOrder);
            
            JSONArray stickers = new JSONArray();
            for (Sticker sticker : this.stickers)
                stickers.put(sticker.toJSON());
            obj.put("stickers", stickers);
            
            JSONArray updatedURIs = new JSONArray();
            for (String uri : this.updatedURIs)
                updatedURIs.put(uri);
            
            obj.put("updatedURIs", updatedURIs);
            
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
        if (filename.length() > 0 && filename.charAt(0) == '/')
            filename = filename.substring(1);
        return urlBase + packBaseDir + '/' + filename;
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
        this.stickers = stickers;
        
        // Ensure the stickers don't think they're remotely-located, and that their getLocation()
        // returns a local Uri
        for (Sticker sticker : this.stickers)
            sticker.setServerBaseDir(null);
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
        if (getStatus() != Status.UNINSTALLED)
            return;
        setStatus(Status.INSTALLING);
        
        installCallback = callback;
        StickerPackDownloadTask task = new StickerPackDownloadTask(
                this, this, async, context);
        if (async)
            task.execute();
        else
            task.doInForeground();
    }
    
    public void uninstall(Context context) {
        if (getStatus() != Status.INSTALLED
            && getStatus() != Status.UPDATEABLE)
            return;
        
        new StickerPackProcessor(this, context).uninstallPack();
        StickerPackRepository.unregisterInstalledPack(this, context);
        
        replacedUris = getStickerURIs();
        deleteSavedJSON(context);
        uninstalledPackSetup();
    }
    
    public void update(Context context, InstallCompleteCallback callback, boolean async) {
        if (getStatus() != Status.UPDATEABLE)
            return;
        
        replaces.uninstall(context);
        
        setStatus(Status.UNINSTALLED);
        
        install(context, callback, async);
    }
    
    public void updateFromDownload(StickerPackDownloadTask.Result result, Context context) {
        if (result == null) {
            // No network access
            Toast.makeText(context, "No network connectivity",
                    Toast.LENGTH_SHORT).show();
            setStatus(Status.UNINSTALLED);
            return;
        }
        
        if (result.exception != null) {
            Log.e(TAG, "Exception in sticker install", result.exception);
            Toast.makeText(context, "Error while installing sticker pack",
                    Toast.LENGTH_LONG).show();
            setStatus(Status.UNINSTALLED);
        } else {
            setStatus(Status.INSTALLED);
            stickerCount = stickers.size();
            totalSize = Util.dirSize(buildFile(context.getFilesDir(), ""));
            StickerPackRepository.registerInstalledPack(this, context);
        }
    }
    
    public void checkForUpdatedStickers() {
        if (replaces == null || replaces.getStickerURIs() == null)
            return;
    
        updatedURIs = UpdateUtils.findNewStickers(replaces.getReplacedUris(), getStickerURIs());
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
    
    @Override
    public int compareTo(StickerPack other) {
        if (other == this)
            return 0;
        
        int oDisplayOrder = other.getDisplayOrder();
        if (oDisplayOrder != displayOrder)
            return displayOrder - oDisplayOrder;
        return getPackname().compareTo(other.getPackname());
    }
    
    @Override
    public int hashCode() {
        return (getPackname() + getVersion()).hashCode();
    }
    
    public boolean wasUpdatedRecently() {
        return (System.currentTimeMillis() / 1000L - getUpdatedTimestamp()) < 7*24*60*60
                && getUpdatedURIs().size() > 0;
    }
    
    public String getPackname() { return packname; }
    
    public int getDisplayOrder() { return displayOrder; }
    
    public String getDatafile() { return datafile; }
    
    public void setDatafile(String datafile) { this.datafile = datafile; }
    
    public String getIconLocation() { return iconLocation; }
    
    public void setIconLocation(String location) { this.iconLocation = location; }
    
    public String getExtraText() { return extraText; }
    
    public String getDescription() { return description; }
    
    public Status getStatus() { return status; }
    
    public LiveData<Status> getLiveStatus() { return liveStatus; }
    
    public void setStatus(Status status) {
        this.status = status;
        liveStatus.postValue(status);
    }
    
    public int getStickerCount() { return stickerCount; }
    
    public float getTotalSizeInMB() { return totalSize / 1024f / 1024f; }
    
    public long getTotalSize() { return totalSize; }
    
    public List<String> getStickerFirebaseURLs() {
        List<String> output = new ArrayList<>(stickers.size());
        for (Sticker sticker : stickers)
            output.add(sticker.getFirebaseURL());
        return output;
    }
    
    public List<String> getStickerURIs() {
        List<String> output = new ArrayList<>(stickers.size());
        for (Sticker sticker : stickers)
            output.add(sticker.getURI().toString());
        return output;
    }
    
    public static List<String> getRelativePathsOfStickers(List<Sticker> stickers) {
        List<String> output = new ArrayList<>(stickers.size());
        for (Sticker sticker : stickers)
            output.add(sticker.getRelativePath());
        return output;
    }
    
    public List<String> getStickerRelativePaths() {
        return getRelativePathsOfStickers(stickers);
    }
    
    public String getFirebaseURL() { return "finnstickers://sticker/pack/" + getPackname(); }
    
    public int getVersion() { return version; }
    
    public void setReplaces(StickerPack replaces) { this.replaces = replaces; }
    
    public void setRemoteVersion(StickerPack remoteVersion) { this.remoteVersion = remoteVersion; }
    
    public StickerPack getReplaces() { return this.replaces; }
    
    public List<Sticker> getStickers() { return stickers; }
    
    private List<String> getReplacedUris() { return replacedUris; }
    
    public long getUpdatedTimestamp() { return updatedTimestamp; }
    
    public List<String> getUpdatedURIs() { return updatedURIs; }
    
    public String getUrlBase() { return urlBase; }
    
    public String getPackBaseDir() { return packBaseDir; }
}
