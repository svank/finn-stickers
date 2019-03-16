package net.samvankooten.finnstickers.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import net.samvankooten.finnstickers.Sticker;
import net.samvankooten.finnstickers.StickerPack;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Implements singleton-style access to StickerPacks, allowing each pack to contain a LiveData for
 * the pack's installation status.
 * Installed packs are treated as singletons. Available (i.e. not installed) packs are not---each
 * call to getInstalledAndAvailablePacks returns a new, up-to-date instance of each uninstalled pack
 */
public class StickerPackRepository {
    private static final String TAG = "StickerPackRepository";
    public static final String INSTALLED_PACKS = "installed_packs";
    
    private static List<StickerPack> installedPacks = null;
    private static List<StickerPack> availablePacks = null;
    private static List<StickerPack> updatablePacks = null;
    
    /**
     * Generates a list of installed stickers packs
     * @param context App context
     */
    public static List<StickerPack> getInstalledPacks(Context context) throws JSONException {
        if (installedPacks != null)
            return new ArrayList<>(installedPacks);
        
        LinkedList<StickerPack> result = new LinkedList<>();
        
        Set<String> installedPackNames = Util.getPrefs(context).getStringSet(INSTALLED_PACKS, null);
        if (installedPackNames == null)
            return result;
        
        SharedPreferences prefs = Util.getPrefs(context);
        for (String name : installedPackNames) {
            String jsonData = prefs.getString(Util.STICKER_PACK_DATA_PREFIX + name, "");
            try {
                JSONObject obj = new JSONObject(jsonData);
                StickerPack pack = new StickerPack(obj, context);
                result.add(pack);
            } catch (JSONException e) {
                Log.e(TAG, "JSON Error on pack " + name, e);
                throw e;
            }
        }
        
        Collections.sort(result);
        installedPacks = result;
        
        return new ArrayList<>(result);
    }
    
    public static StickerPack getInstalledStickersAsOnePack(Context context) throws JSONException {
        List<StickerPack> packs = getInstalledPacks(context);
        List<Sticker> stickers = new LinkedList<>();
        
        for (StickerPack pack : packs)
            stickers.addAll(pack.getStickers());
        
        StickerPack pack = new StickerPack();
        pack.absorbStickerData(stickers);
        return pack;
    }
    
    public static StickerPack getInstalledOrCachedPackByName(String name, Context context) throws JSONException {
        List<StickerPack> packs = getInstalledPacks(context);
        
        // Include remote packs only if previously loaded---don't trigger network requests here
        if (availablePacks != null)
            packs.addAll(availablePacks);
        if (updatablePacks != null)
            packs.addAll(0, updatablePacks);
        
        for (StickerPack pack : packs) {
            if (pack.getPackname().equals(name))
                return pack;
        }
        return null;
    }
    
    /**
     * Generates a complete list of installed & available sticker packs
     * TODO: The available packs don't come as singletons, so that updated server data is always
     * reflected. Should that change?
     * @return Array of available & installed StickerPacks
     */
    public static AllPacksResult getInstalledAndAvailablePacks(Context context) {
        URL url;
        try {
            url = new URL(Util.PACK_LIST_URL);
        } catch (MalformedURLException e) {
            Log.e(TAG, "Bad pack url", e);
            return new AllPacksResult(null, false, e);
        }
        
        // Find installed packs
        List<StickerPack> list;
        try {
            list = getInstalledPacks(context);
        } catch (Exception e) {
            return new AllPacksResult(null, false, e);
        }
        
        if (!Util.connectedToInternet(context))
            return new AllPacksResult(list, false, new Exception("No internet connection"));
        
        Util.DownloadResult result;
        try {
            // Download the list of available packs
            result = Util.downloadFromUrl(url);
        } catch (IOException e) {
            return new AllPacksResult(list, false, e);
        }
        
        JSONArray packs;
        try {
            // Parse the list of packs out of the JSON data
            JSONObject json = new JSONObject(result.readString());
            packs = json.getJSONArray("packs");
        } catch (Exception e) {
            return new AllPacksResult(list, false, e);
        } finally {
            result.close();
        }
        
        // Parse each StickerPack JSON object and download icons
        availablePacks = new LinkedList<>();
        updatablePacks = new LinkedList<>();
        try {
            for (int i = 0; i < packs.length(); i++) {
                JSONObject packData = packs.getJSONObject(i);
                StickerPack availablePack = new StickerPack(packData, Util.getURLPath(url));
                
                // Is this pack already in the list? i.e. is this an installed pack?
                boolean add = true;
                for (StickerPack installedPack : list) {
                    if (installedPack.equals(availablePack)) {
                        if (availablePack.getVersion() <= installedPack.getVersion()) {
                            add = false;
                            
                            // In case the user uninstalls and then immediately re-installs
                            // the pack, ensure we have current server data in the Pack.
                            installedPack.setRemoteVersion(availablePack);
                            break;
                        } else {
                            availablePack.setStatus(StickerPack.Status.UPDATEABLE);
                            availablePack.setReplaces(installedPack);
                            list.remove(installedPack);
                            updatablePacks.add(availablePack);
                            break;
                        }
                    }
                }
                if (add)
                    availablePacks.add(availablePack);
            }
        } catch (Exception e) {
            availablePacks = null;
            return new AllPacksResult(list, false, e);
        }
        
        Collections.sort(availablePacks);
        list.addAll(availablePacks);
        Collections.sort(list);
        return new AllPacksResult(new ArrayList<>(list), true, null);
    }
    
    public static void registerInstalledPack(StickerPack pack, Context context) {
        Set<String> installedPacksSet = Util.getMutableStringSetFromPrefs(context, INSTALLED_PACKS);
        installedPacksSet.add(pack.getPackname());
        SharedPreferences.Editor editor = Util.getPrefs(context).edit();
        editor.putStringSet(INSTALLED_PACKS, installedPacksSet);
        editor.apply();
        
        if (installedPacks != null) {
            installedPacks.add(pack);
            Collections.sort(installedPacks);
        }
        if (availablePacks != null)
            availablePacks.remove(pack);
    }
    
    public static void unregisterInstalledPack(StickerPack pack, Context context) {
        Set<String> installedPacksSet = Util.getMutableStringSetFromPrefs(context, INSTALLED_PACKS);
        installedPacksSet.remove(pack.getPackname());
        SharedPreferences.Editor editor = Util.getPrefs(context).edit();
        editor.putStringSet(INSTALLED_PACKS, installedPacksSet);
        editor.apply();
        
        if (installedPacks != null)
            installedPacks.remove(pack);
        if (availablePacks != null) {
            availablePacks.add(pack);
            Collections.sort(availablePacks);
        }
    }
    
    public static class AllPacksResult {
        public final boolean networkSucceeded;
        public final List<StickerPack> list;
        public final Exception exception;
        public AllPacksResult(List<StickerPack> list, boolean networkSucceeded, Exception e) {
            this.list = list;
            this.networkSucceeded = networkSucceeded;
            exception = e;
        }
    }
}
