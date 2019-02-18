package net.samvankooten.finnstickers.misc_classes;

import android.app.backup.BackupAgent;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.google.firebase.FirebaseApp;

import net.samvankooten.finnstickers.MainActivity;
import net.samvankooten.finnstickers.StickerPack;
import net.samvankooten.finnstickers.updating.UpdateUtils;
import net.samvankooten.finnstickers.utils.NotificationUtils;
import net.samvankooten.finnstickers.utils.Util;

import java.net.MalformedURLException;
import java.net.URL;

public class FinnBackupAgent extends BackupAgent {
    private static final String TAG = "FinnBackupAgent";
    
    // These must be implemented, but we don't need anything
    public void onBackup(ParcelFileDescriptor oldState,
                         BackupDataOutput data,
                         ParcelFileDescriptor newState) {
    }
    
    public void onRestore (BackupDataInput data,
                                    int appVersionCode,
                                    ParcelFileDescriptor newState) {
    }
    
    /**
     * Runs after a restore of the app data (e.g. after new device setup). We know which sticker
     * packs were installed during backup, and we presumably have internet access now if the app
     * was just installed, so download & install those backs. Also set up update checks, etc.
     * If there's an error, we'll just forget there's anything we want to install and let the
     * user open the app to re-install packs.
     */
    public void onRestoreFinished() {
        Context context = getApplicationContext();
        Util.performNeededMigrations(context);
        FirebaseApp.initializeApp(context);
        
        URL url;
        try {
            url = new URL(MainActivity.PACK_LIST_URL);
        } catch (MalformedURLException e) {
            Log.e(TAG, "Back pack list url", e);
            return;
        }
        
        Util.AllPacksResult packs = Util.getInstalledAndAvailablePacks(
                url, context.getCacheDir(), context);
    
        if (!packs.networkSucceeded) {
            Log.e(TAG, "Error downloading pack info");
            onRestoreFail(context);
            return;
        }
        
        if (packs.exception != null) {
            Log.e(TAG, "Error in packlist downlad", packs.exception);
            onRestoreFail(context);
            return;
        }
        
        for (StickerPack pack : packs.list) {
            switch (pack.getStatus()) {
                case UPDATEABLE:
                    pack.update(context, null, false);
                    break;
                case INSTALLED:
                    pack.uninstall(context);
                    pack.install(context, null, false);
                    break;
            }
        }
        
        NotificationUtils.createChannels(getApplicationContext());
        UpdateUtils.scheduleUpdates(context);
        // It seems like Firebase updates don't happen from within this BackupAgent context,
        // so schedule a re-index from a more normal context.
        AppIndexingUpdateReceiver.scheduleReindex(context, true);
    }
    
    private void onRestoreFail(Context context) {
        Util.getPrefs(context).edit().clear().apply();
    }
}
