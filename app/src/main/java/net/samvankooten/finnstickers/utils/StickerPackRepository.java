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
    
    private static List<StickerPack> installedPacks = new ArrayList<>(5);
    private static List<StickerPack> availablePacks = new ArrayList<>(5);
    
    /**
     * Generates a list of installed stickers packs
     * @param context App context
     */
    public static List<StickerPack> getInstalledPacks(Context context) {
        if (installedPacks.size() > 0)
            return new ArrayList<>(installedPacks);
        
        try {
            loadInstalledPacks(context);
        } catch (JSONException e) {
            return null;
        }

        return new ArrayList<>(installedPacks);
    }
    
    private static void loadInstalledPacks(Context context) throws JSONException {
        if (installedPacks.size() > 0)
            return;
        
        Set<String> installedPackNames = Util.getPrefs(context).getStringSet(INSTALLED_PACKS, null);
        if (installedPackNames == null)
            return;
    
        SharedPreferences prefs = Util.getPrefs(context);
        for (String name : installedPackNames) {
            String jsonData = prefs.getString(Util.STICKER_PACK_DATA_PREFIX + name, "");
            try {
                JSONObject obj = new JSONObject(jsonData);
                StickerPack pack = new StickerPack(obj, context);
                installedPacks.add(pack);
            } catch (JSONException e) {
                Log.e(TAG, "JSON Error on pack " + name, e);
                throw e;
            }
        }
    
        Collections.sort(installedPacks);
    }
    
    public static StickerPack getInstalledStickersAsOnePack(Context context) {
        try {
            loadInstalledPacks(context);
        } catch (JSONException e) {
            return null;
        }
        
        List<Sticker> stickers = new ArrayList<>(40);
        
        for (StickerPack pack : installedPacks)
            stickers.addAll(pack.getStickers());
        
        StickerPack pack = new StickerPack();
        pack.absorbStickerData(stickers, context);
        return pack;
    }
    
    public static StickerPack getInstalledOrCachedPackByName(String name, Context context) {
        try {
            loadInstalledPacks(context);
        } catch (JSONException e) {
            return null;
        }
    
        for (StickerPack pack : installedPacks) {
            if (pack.getPackname().equals(name))
                return pack;
        }
    
        for (StickerPack pack : availablePacks) {
            if (pack.getPackname().equals(name))
                return pack;
        }
        
        return null;
    }
    
    public static StickerPack getInstalledOrRemotePackByName(String name, Context context)
            throws Exception{
        StickerPack pack = getInstalledOrCachedPackByName(name, context);
        if (pack != null)
            return pack;
        
        AllPacksResult result = getInstalledAndAvailablePacks(context);
        if (result.exception != null)
            throw result.exception;
        return getInstalledOrCachedPackByName(name, context);
    }
    
    private static StickerPack getKnownEquivalent(StickerPack target) {
        for (StickerPack pack : installedPacks) {
            if (pack.equals(target))
                return pack;
        }
    
        for (StickerPack pack : availablePacks) {
            if (pack.equals(target))
                return pack;
        }
        
        return null;
    }
    
    public static List<StickerPack> getInstalledAndCachedAvailablePacks() {
        List<StickerPack> packs = new LinkedList<>(installedPacks);
        
        packs.addAll(availablePacks);
        
        return packs;
    }
    
    /**
     * Generates a complete list of installed & available sticker packs
     * @return Array of available & installed StickerPacks
     */
    public static AllPacksResult getInstalledAndAvailablePacks(Context context) {
        // Make sure the installed packs list is loaded
        List<StickerPack> list;
        list = getInstalledPacks(context);
        if (list == null) {
            Log.e(TAG, "Error getting installed packs");
            return new AllPacksResult(null, false, null);
        }
        
        if (!Util.connectedToInternet(context))
            return new AllPacksResult(list, false, new Exception("No internet connection"));
        
        Util.DownloadResult result;
        URL url;
        try {
            // Download the list of available packs
            url = new URL(Util.PACK_LIST_URL);
            result = Util.downloadFromUrl(url);
        } catch (IOException e) {
            Log.e(TAG, "Error in download", e);
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
        
        List<StickerPack> freshAvailablePacks = new ArrayList<>(5);
        // Parse each StickerPack JSON object and download icons
        try {
            for (int i = 0; i < packs.length(); i++) {
                JSONObject packData = packs.getJSONObject(i);
                StickerPack freshPack = new StickerPack(packData, Util.getURLPath(url));
                
                StickerPack equivalent = getKnownEquivalent(freshPack);
                
                if (equivalent == null) {
                    freshAvailablePacks.add(freshPack);
                    continue;
                }
                
                switch (equivalent.getStatus()) {
                    case UNINSTALLED:
                        equivalent.copyFreshDataFrom(packData);
                        freshAvailablePacks.add(equivalent);
                        break;
                    case UPDATABLE:
                        equivalent.getRemoteVersion().copyFreshDataFrom(packData);
                        break;
                    case INSTALLING:
                        equivalent.setRemoteVersion(freshPack);
                        installedPacks.add(equivalent);
                        break;
                    case INSTALLED:
                        equivalent.setRemoteVersion(freshPack);
                        if (freshPack.getVersion() > equivalent.getVersion()) {
                            // This is an update we didn't know about before
                            equivalent.setStatus(StickerPack.Status.UPDATABLE);
                        }
                }
            }
        } catch (Exception e) {
            availablePacks.clear();
            return new AllPacksResult(list, false, e);
        }
        
        Collections.sort(freshAvailablePacks);
        
        availablePacks = freshAvailablePacks;
        return new AllPacksResult(getInstalledAndCachedAvailablePacks(),
                true, null);
    }
    
    public static void registerInstalledPack(StickerPack pack, Context context) {
        Set<String> installedPacksSet = Util.getMutableStringSetFromPrefs(context, INSTALLED_PACKS);
        installedPacksSet.add(pack.getPackname());
        SharedPreferences.Editor editor = Util.getPrefs(context).edit();
        editor.putStringSet(INSTALLED_PACKS, installedPacksSet);
        editor.apply();
        
        if (!installedPacks.contains(pack)) {
            installedPacks.add(pack);
            Collections.sort(installedPacks);
        }
        availablePacks.remove(pack);
        
        Util.addAppShortcut(pack, context);
    }
    
    public static void unregisterInstalledPack(StickerPack pack, Context context) {
        Set<String> installedPacksSet = Util.getMutableStringSetFromPrefs(context, INSTALLED_PACKS);
        installedPacksSet.remove(pack.getPackname());
        SharedPreferences.Editor editor = Util.getPrefs(context).edit();
        editor.putStringSet(INSTALLED_PACKS, installedPacksSet);
        editor.apply();
        
        installedPacks.remove(pack);
        availablePacks.add(pack);
        Collections.sort(availablePacks);
        
        Util.removeAppShortcut(pack.getPackname(), context);
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
