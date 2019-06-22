package net.samvankooten.finnstickers;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

public class CompositeStickerPack extends StickerPack {
    private static final String TAG = "CompositePack";
    
    private HashMap<String, StickerPack> packs = new HashMap<>();
    private int stickerCount;
    private int totalSize;
    private MediatorLiveData<Status> liveStatus = new MediatorLiveData<>();
    
    public CompositeStickerPack() {
        super();
        liveStatus.setValue(Status.INSTALLED);
    }
    
    public void addPack(StickerPack pack) {
        packs.put(pack.getPackname(), pack);
        updateStats();
        liveStatus.addSource(pack.getLiveStatus(), status -> liveStatus.setValue(Status.INSTALLED));
    }
    
    public void updateSavedJSON(Context context) {}
    
    public void deleteSavedJSON(Context context) {}
    
    public void addSticker(Sticker newSticker, Sticker parentSticker, Context context) {
        packs.get(newSticker.getPackname()).addSticker(
                newSticker, parentSticker, context);
    }
    
    public boolean deleteSticker(int pos, Context context) {
        StickerPack pack = packs.get(getStickers().get(pos).getPackname());
        String uri = getStickerURIs().get(pos);
        int i = pack.getStickerURIs().indexOf(uri);
        return pack.deleteSticker(i, context);
    }
    
    public void install(Context context, InstallCompleteCallback callback, boolean async) {}
    
    public void uninstall(Context context) {}
    
    public void update(Context context, InstallCompleteCallback callback, boolean async) {}
    
    private void updateStats() {
        stickerCount = 0;
        totalSize = 0;
        for (StickerPack pack : packs.values()) {
            stickerCount += pack.getStickerCount();
            totalSize += pack.getTotalSize();
        }
    }
    
    public boolean wasUpdatedRecently() {
        return false;
    }
    
    public Status getStatus() { return liveStatus.getValue(); }
    
    public LiveData<Status> getLiveStatus() { return liveStatus; }
    
    public int getStickerCount() { return stickerCount; }
    
    public float getTotalSizeInMB() { return totalSize / 1024f / 1024f; }
    
    public long getTotalSize() { return totalSize; }
    
    public List<String> getStickerFirebaseURLs() {
        List<String> output = new ArrayList<>(stickerCount);
        for (StickerPack pack : packs.values())
            output.addAll(pack.getStickerFirebaseURLs());
        return output;
    }
    
    public List<String> getStickerURIs() {
        List<String> output = new ArrayList<>(stickerCount);
        for (StickerPack pack : packs.values())
            output.addAll(pack.getStickerURIs());
        return output;
    }
    
    public List<String> getStickerRelativePaths() {
        return getRelativePathsOfStickers(getStickers());
    }
    
    public List<Sticker> getStickers() {
        List<Sticker> output = new ArrayList<>(stickerCount);
        for (StickerPack pack : packs.values())
            output.addAll(pack.getStickers());
        return output;
    }
    
    public Sticker getStickerByUri(String uri) {
        for (Sticker sticker: getStickers()) {
            if (sticker.getURI().toString().equals(uri))
                return sticker;
        }
        return null;
    }
}
