package net.samvankooten.finnstickers.misc_classes;

import android.app.backup.BackupAgent;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.content.Context;
import android.os.ParcelFileDescriptor;

import net.samvankooten.finnstickers.utils.NotificationUtils;
import net.samvankooten.finnstickers.utils.Util;

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
    
    public void onRestoreFinished() {
        Context context = getApplicationContext();
        
        Util.performNeededMigrations(context);
        NotificationUtils.createChannels(context);
        Util.markPendingRestore(context, true);
        
        RestoreJobIntentService.start(this);
    }
}
