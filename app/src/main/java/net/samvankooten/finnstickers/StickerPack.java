package net.samvankooten.finnstickers;

import android.app.FragmentManager;
import android.app.Notification;
import android.content.Context;
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
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by sam on 10/22/17.
 */

public class StickerPack implements DownloadCallback<StickerPackDownloadTask.Result>, Serializable {
    private static final String TAG = "StickerPack";
    static final String KNOWN_PACKS_FILE = "known_packs.txt";
    
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
    
    enum Status {UNINSTALLED, INSTALLING, INSTALLED, UPDATEABLE}
    
    /**
     * Generates a list of installed stickers packs
     * @param dataDir Directory into which packs have been installed
     */
    static List<StickerPack> getInstalledPacks(File dataDir) throws IOException, JSONException {
        LinkedList<StickerPack> list = new LinkedList<>();
        
        // Scan the data dir for the .json files of installed packs
        for (File file : dataDir.listFiles()) {
            if (!file.isFile())
                continue;
            
            String name = file.getName();
            if (name.length() < 5 || !name.substring(name.length()-5).equals(".json"))
                continue;
            
            if (name.equals(KNOWN_PACKS_FILE))
                continue;
            
            // Load the found JSON file
            JSONObject obj = new JSONObject(Util.readTextFile(file));
            StickerPack pack = new StickerPack(obj);
            pack.setStatus(StickerPack.Status.INSTALLED);
            list.add(pack);
        }
        return list;
    }
    
    /**
     * Generates a complete list of installed & available sticker packs
     * @param url Location of available packs list
     * @param iconDir Directory where available pack's icons should be saved to (i.e. cache dir)
     * @param dataDir Directory containing installed packs
     * @return Array of available & installed StickerPacks
     */
    static List<StickerPack> getAllPacks(URL url, File iconDir, File dataDir) throws JSONException, IOException{
        // Find installed packs
        List<StickerPack> list = getInstalledPacks(dataDir);
        
        // Download the list of available packs
        Util.DownloadResult result;
        result = Util.downloadFromUrl(url);

        // Parse the list of packs out of the JSON data
        JSONObject json = new JSONObject(result.readString());
        JSONArray packs = json.getJSONArray("packs");
        result.close();

        // Parse each StickerPack JSON object and download icons
        for (int i = 0; i < packs.length(); i++) {
            JSONObject packData = packs.getJSONObject(i);
            StickerPack availablePack = new StickerPack(packData, Util.getURLPath(url));
            
            // Is this pack already in the list? i.e. is this an installed pack?
            boolean add = true;
            for (StickerPack installedPack : list) {
                if (installedPack.equals(availablePack)) {
                    if (availablePack.getVersion() <= installedPack.getVersion()) {
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
            else
                continue;

            File destination = availablePack.generateCachedIconPath(iconDir);
            URL iconURL = new URL(Util.getURLPath(url) + availablePack.iconurl);
            try {
                Util.downloadFile(iconURL, destination);
                availablePack.setIconfile(destination);
            } catch (Exception e) {
                Log.e(TAG, "Difficulty downloading pack icon", e);
            }
        }
        
        return new ArrayList<>(list);
    }
    
    /**
     * Builds a StickerPack from a JSONObject for a non-installed pack.
     * @param data JSON data
     * @param urlBase The URL of the server directory containing this pack
     */
    StickerPack(JSONObject data, String urlBase) throws JSONException {
        this.packname = data.getString("packName");
        this.iconurl = data.getString("iconUrl");
        this.packBaseDir = data.getString("packBaseDir");
        this.datafile = data.getString("dataFile");
        this.extraText = data.getString("extraText");
        this.description = data.getString("description");
        this.urlBase = urlBase;
        this.version = data.getInt("version");
        this.iconfile = null;
        
        uninstalledPackSetup();
    }
    
    /**
     * Initialization for uninstalled packs. Also converts an installed pack
     * to an uninstalled state after its files have been deleted.
     */
    void uninstalledPackSetup() {
        this.jsonSavePath = "";
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
    StickerPack(JSONObject data) throws JSONException {
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
    
    JSONObject toJSON() {
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
    
    /**
     * Saves this pack to a JSON file & updates the internally-stored JSON file path.
     */
    void writeToFile(String filename) {
        jsonSavePath = filename;
        try {
            FileWriter file = new FileWriter(filename);
            file.write(toJSON().toString());
            file.close();
        } catch (IOException e) {
            Log.e(TAG, "Error writing to file", e);
        }
    }
    
    /**
     * If this instance has a stored JSON save path, updates that JSON file.
     */
    void updateJSONFile() {
        if (jsonSavePath == null || jsonSavePath.equals(""))
            return;
        writeToFile(jsonSavePath);
    }
    
    /**
     * Returns the server URL of a given filename inside this pack's directory
     */
    String buildURLString(String filename) {
        return urlBase + '/' + packBaseDir + '/' + filename;
    }
    
    /**
     * Given a sticker installation root directory (i.e. the data dir), returns the path to a
     * given file inside this pack.
     */
    File buildFile(File base, String filename) {
        return new File(new File(base, packname), filename);
    }
    
    Uri buildURI(String filename) {
        String path = Util.CONTENT_URI_ROOT + '/' + packname + '/' + filename;
        return Uri.parse(path);
    }
    
    String buildJSONPath(File path) {
        return buildJSONPath(path, getPackname());
    }
    
    static String buildJSONPath(File path, String packname) {
        return String.format("%s/%s.json", path, packname);
    }
    
    /**
     * Given a list of Stickers, adds their URLs and URIs to this Pack's internal list.
     */
    void absorbFirebaseURLs(List<Sticker> stickers) {
        stickerURLs = new LinkedList<>();
        stickerURIs = new LinkedList<>();
        for (Sticker sticker : stickers) {
            stickerURLs.add(sticker.getURL());
            stickerURIs.add(sticker.getURI().toString());
        }
    }
    
    /**
     * Installs this StickerPack
     * @param adapter Adapter to notify when installation is complete
     * @param context Relevant Context
     */
    void install(StickerPackAdapter adapter, Context context) {
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
            task.execute();
        }
    }
    
    void remove(Context context) {
        if (status != Status.INSTALLED) {
            return;
        }
        
        StickerProcessor.clearStickers(context, this);
        
        status = Status.UNINSTALLED;
    }
    
    void update(StickerPackAdapter adapter, Context context) {
        if (status != Status.UPDATEABLE) {
            return;
        }
        
        oldURIs = replaces.getStickerURIs();
        
        replaces.remove(context);
        
        status = Status.UNINSTALLED;
        
        install(adapter, context);
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
        }
    }
    
    /**
     * Shows a notification that this pack was updated, if appropriate.
     */
    void showUpdateNotif() {
        if (oldURIs != null) {
            List<String> uris = UpdateManager.findNewStickers(oldURIs, getStickerURIs());
            if (uris.size() == 0)
                return;
            this.updatedURIs = uris;
            this.updatedTimestamp = System.currentTimeMillis() / 1000L;
            updateJSONFile();
            Notification n = NotificationUtils.buildNewStickerNotification(context, this);
            NotificationUtils.showNotification(context, n);
            clearNotifData();
        }
    }
    
    void clearNotifData() {
        oldURIs = null;
        context = null;
    }
    
    @Override
    public void finishDownloading() {
        if (adapter != null) {
            adapter.notifyDataSetChanged();
            adapter = null;
        }
        if (mNetworkFragment != null) {
            mNetworkFragment.cancelDownload();
            mNetworkFragment = null;
        }
        context = null;
    }
    
    boolean equals(StickerPack other) {
        return getPackname().equals(other.getPackname());
    }
    
    File generateCachedIconPath(File iconDir) {
        String suffix = iconurl.substring(iconurl.lastIndexOf("."));
        return new File(iconDir, packname + "-icon" + suffix);
    }

    String getPackname() {
        return packname;
    }

    void setPackname(String packname) {
        this.packname = packname;
    }

    String getIconurl() {
        return iconurl;
    }

    void setIconurl(String iconurl) {
        this.iconurl = iconurl;
    }

    String getDatafile() {
        return datafile;
    }

    void setDatafile(String datafile) {
        this.datafile = datafile;
    }

    File getIconfile() {
        return iconfile;
    }

    void setIconfile(File iconfile) {
        this.iconfile = iconfile;
    }
    
    String getExtraText() {
        return extraText;
    }
    
    void setExtraText(String extraText) {
        this.extraText = extraText;
    }
    
    String getDescription() {
        return description;
    }
    
    void setDescription(String description) {
        this.description = description;
    }
    
    Status getStatus() {
        return status;
    }
    
    void setStatus(Status status) {
        this.status = status;
    }
    
    List<String> getStickerURLs() { return stickerURLs; }
    
    String getJsonSavePath() { return jsonSavePath; }
    
    String getURL() {
        return "finnstickers://sticker/pack/" + getPackname();
    }
    
    int getVersion() { return version; }
    
    void setReplaces(StickerPack replaces) { this.replaces = replaces; }
    
    StickerPack getReplaces() { return this.replaces; }
    
    List<String> getStickerURIs() { return stickerURIs; }
    
    long getUpdatedTimestamp() { return updatedTimestamp; }
    
    List<String> getUpdatedURIs() { return updatedURIs; }
}
