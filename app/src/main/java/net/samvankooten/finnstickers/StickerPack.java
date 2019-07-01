package net.samvankooten.finnstickers;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

import net.samvankooten.finnstickers.editor.renderer.StickerRenderer;
import net.samvankooten.finnstickers.updating.UpdateUtils;
import net.samvankooten.finnstickers.utils.DownloadCallback;
import net.samvankooten.finnstickers.utils.StickerPackProcessor;
import net.samvankooten.finnstickers.utils.StickerPackRepository;
import net.samvankooten.finnstickers.utils.Util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
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
    private List<Sticker> stickersPreUpdate = null;
    
    public enum Status {UNINSTALLED, INSTALLING, INSTALLED, UPDATABLE}
    
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
    private void uninstalledPackSetup() {
        if (remoteVersion != null) {
            stickers = remoteVersion.getStickers();
            stickerCount = remoteVersion.getStickerCount();
            totalSize = remoteVersion.getTotalSize();
            iconLocation = remoteVersion.getIconLocation();
            datafile = remoteVersion.getDatafile();
            urlBase = remoteVersion.getUrlBase();
            packBaseDir = remoteVersion.getPackBaseDir();
            extraText = remoteVersion.getExtraText();
            description = remoteVersion.getDescription();
            version = remoteVersion.getVersion();
        } else {
            stickers = new LinkedList<>();
        }
        setStatus(Status.UNINSTALLED);
        updatedURIs = new LinkedList<>();
        updatedTimestamp = 0;
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
            this.stickers.add(new Sticker(stickers.getJSONObject(i), context));
        
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
    
    /** Creates a copy of the given StickerPack */
    public StickerPack(StickerPack src) {
        packname = src.getPackname();
        iconLocation = src.getIconLocation();
        packBaseDir = src.getPackBaseDir();
        urlBase = src.getUrlBase();
        datafile = src.getDatafile();
        extraText = src.getExtraText();
        description = src.getDescription();
        stickers = new ArrayList<>(src.getStickers());
        version = src.getVersion();
        updatedURIs = new ArrayList<>(src.getUpdatedURIs());
        updatedTimestamp = src.getUpdatedTimestamp();
        displayOrder = src.getDisplayOrder();
        stickerCount = src.getStickerCount();
        totalSize = src.getTotalSize();
        remoteVersion = src.getRemoteVersion();
    }
    
    /**
     * For uninstalled packs, if we refresh the data from the server, we can keep the StickerPack
     * as a singleton instance by having the existing instance copy the updatable data from the
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
    
    public void addSticker(Sticker newSticker, Sticker parentSticker, Context context) {
        if (status != Status.INSTALLED && status != Status.UPDATABLE) {
            Log.e(TAG, "Trying to add a sticker to a pack that's not installed");
            return;
        }
        int pos = stickerCount - 1;
        if (parentSticker != null) {
            pos = stickers.indexOf(parentSticker) + 1;
        }
        stickers.add(pos, newSticker);
        
        if (parentSticker != null) {
            int updatedPos = updatedURIs.indexOf(parentSticker.getURI().toString());
            if (updatedPos >= 0) {
                updatedURIs.add(updatedPos + 1, newSticker.getURI().toString());
            }
        }
        
        updateStats(context);
        updateSavedJSON(context);
        setStatus(status);
        
        StickerPackProcessor processor = new StickerPackProcessor(this, context);
        processor.registerStickers(stickers);
    }
    
    public boolean deleteSticker(int pos, Context context) {
        return deleteSticker(pos, context, false);
    }
    
    private boolean deleteSticker(int pos, Context context, boolean uninstalledOK) {
        if (!uninstalledOK && status != Status.INSTALLED && status != Status.UPDATABLE) {
            Log.e(TAG, "Trying to remove a sticker from a pack that's not installed");
            return false;
        }
        
        File relativePath = new File(getPackBaseDir(), stickers.get(pos).getRelativePath());
        File absPath = new File(context.getFilesDir(), relativePath.toString());
        try {
            if (absPath.exists())
                Util.delete(absPath);
        } catch (IOException e) {
            Log.e(TAG, "Error deleting file: ", e);
            return false;
        }
        updatedURIs.remove(stickers.get(pos).getURI().toString());
        StickerPackProcessor.unregisterSticker(stickers.get(pos));
        stickers.remove(pos);
        
        updateStats(context);
        updateSavedJSON(context);
        setStatus(status);
        
        StickerPackProcessor processor = new StickerPackProcessor(this, context);
        processor.registerStickers(stickers);
        return true;
    }
    
    /**
     * Given a list of Stickers, adds their URLs and URIs to this Pack's internal list.
     */
    public void absorbStickerData(List<Sticker> stickers, Context context) {
        this.stickers = stickers;
        
        // Ensure the stickers don't think they're remotely-located, and that their getLocation()
        // returns a local Uri
        for (Sticker sticker : this.stickers)
            sticker.setServerBaseDir(null);
        
        if (stickersPreUpdate != null) {
            updatedURIs = UpdateUtils.findNewUrisFromStickers(stickersPreUpdate, stickers);
            if (updatedURIs.size() != 0)
                this.updatedTimestamp = System.currentTimeMillis() / 1000L;
            
            // Migrate customized stickers
            List<String> potentialParents = new ArrayList<>(stickers.size());
            for (Sticker sticker : stickers)
                potentialParents.add(sticker.getRelativePath());
            List<String> potentialAdoptiveParents = new ArrayList<>(stickersPreUpdate.size());
            for (Sticker sticker : stickersPreUpdate)
                potentialAdoptiveParents.add(sticker.getRelativePath());
            
            // By looping backwards, we can avoid needing to update potentialParents as stickers
            // are added
            outerLoop:
            for (int i=stickersPreUpdate.size()-1; i>=0; i--) {
                Sticker oldSticker = stickersPreUpdate.get(i);
                if (oldSticker.getCustomTextData() == null)
                    continue;
                // This is a customized sticker to be migrated
                // Its parent sticker might have been removed in an update and so might not be
                // in the current sticker pack. So when deciding where to place it in the current
                // Sticker list, we'll start by trying to place it after its parent, and if not
                // found, we'll work backward up the old sticker list, using those Stickers as
                // potential adoptive parents, until we find a adopter that's in the current
                // Sticker list. That way we at least get the customized sticker in approximately
                // the right place.
                String parent = oldSticker.getCustomTextBaseImage();
                int idx = potentialParents.indexOf(parent);
                if (idx >= 0) {
                    stickers.add(idx+1, oldSticker);
                    continue;
                }
                
                for (int j=i-1; j>=0; j--) {
                    String potentialAdopter = potentialAdoptiveParents.get(j);
                    idx = potentialParents.indexOf(potentialAdopter);
                    if (idx >= 0) {
                        stickers.add(idx+1, oldSticker);
                        continue outerLoop;
                    }
                }
                
                // No adoptive parent was found
                stickers.add(0, oldSticker);
            }
            
            stickersPreUpdate = null;
        }
        updateSavedJSON(context);
        renderCustomImages(context);
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
        remoteVersion = this.copy();
        setStatus(Status.INSTALLING);
        
        installCallback = callback;
        StickerPackDownloadTask task = new StickerPackDownloadTask(
                this, this, !async, context);
        if (async)
            task.execute();
        else
            task.doInForeground();
    }
    
    public void uninstall(Context context) {
        if (getStatus() != Status.INSTALLED
                && getStatus() != Status.UPDATABLE)
            return;
        
        new StickerPackProcessor(this, context).uninstallPack();
        StickerPackRepository.unregisterInstalledPack(this, context);
        
        deleteSavedJSON(context);
        uninstalledPackSetup();
    }
    
    public void update(Context context, InstallCompleteCallback callback, boolean async) {
        if (getStatus() != Status.UPDATABLE)
            return;
        
        stickersPreUpdate = new ArrayList<>(stickers);
        
        uninstall(context);
        
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
            updateStats(context);
            StickerPackRepository.registerInstalledPack(this, context);
            
            setStatus(Status.INSTALLED);
        }
    }
    
    private void renderCustomImages(Context context) {
        for (int i=0; i< stickers.size(); i++) {
            Sticker sticker = stickers.get(i);
            if (sticker.getCustomTextData() != null) {
                File relativePath = new File(getPackBaseDir(), sticker.getRelativePath());
                File absPath = new File(context.getFilesDir(), relativePath.toString());
                if (absPath.exists())
                    continue;
                absPath.getParentFile().mkdirs();
                File finalLocation = null;
                try {
                    finalLocation = StickerRenderer.renderToFile(
                            Sticker.generateUri(packname, sticker.getCustomTextBaseImage()).toString(),
                            packname,
                            new JSONObject(sticker.getCustomTextData()),
                            absPath,
                            context);
                } catch (JSONException e) {
                    Log.e(TAG, "Error loading JSON", e);
                } catch (Exception e) {
                    Log.e(TAG, "Error in sticker render", e);
                }
                if (finalLocation == null) {
                    Log.e(TAG, "Deleting sticker after unsuccessful render");
                    deleteSticker(stickers.indexOf(sticker), context, true);
                    i--;
                }
            }
        }
    }
    
    @Override
    public void finishDownloading() {
        if (installCallback != null)
            installCallback.onInstallComplete();
        installCallback = null;
    }
    
    public StickerPack copy() {
        return new StickerPack(this);
    }
    
    public boolean equals(StickerPack other) {
        return getPackname().equals(other.getPackname());
    }
    
    private void updateStats(Context context) {
        stickerCount = stickers.size();
        totalSize = Util.dirSize(buildFile(context.getFilesDir(), ""));
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
    
    public void setVersion(int version) { this.version = version; }
    
    public StickerPack getRemoteVersion() { return remoteVersion; }
    
    public void setRemoteVersion(StickerPack remoteVersion) { this.remoteVersion = remoteVersion; }
    
    public List<Sticker> getStickers() { return stickers; }
    
    public Sticker getStickerByUri(String uri) {
        for (Sticker sticker: stickers) {
            if (sticker.getURI().toString().equals(uri))
                return sticker;
        }
        return null;
    }
    
    public long getUpdatedTimestamp() { return updatedTimestamp; }
    
    public List<String> getUpdatedURIs() { return updatedURIs; }
    
    public String getUrlBase() { return urlBase; }
    
    public String getPackBaseDir() { return packBaseDir; }
}
