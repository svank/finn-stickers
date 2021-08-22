package net.samvankooten.finnstickers.misc_classes;

import android.app.backup.BackupAgent;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.app.backup.FullBackupDataOutput;
import android.content.Context;
import android.os.ParcelFileDescriptor;

import net.samvankooten.finnstickers.utils.Util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FinnBackupAgent extends BackupAgent {
    private static final String TAG = "FinnBackupAgent";
    
    // These must be implemented, but we don't need anything
    @Override
    public void onBackup(ParcelFileDescriptor oldState,
                         BackupDataOutput data,
                         ParcelFileDescriptor newState) {
    }
    
    @Override
    public void onRestore (BackupDataInput data,
                                    int appVersionCode,
                                    ParcelFileDescriptor newState) {
    }
    
    @Override
    public void onFullBackup(FullBackupDataOutput data) {
        for (File file : getFilesToBackup(getApplicationContext())) {
            fullBackupFile(file, data);
        }
    }
    
    @Override
    public void onRestoreFinished() {
        Context context = getApplicationContext();
        
        Util.markPendingRestore(context, true);
        
        PostRestoreJob.schedule(context);
    }
    
    public static List<File> getFilesToBackup(Context context) {
        File prefsdir = new File(context.getApplicationInfo().dataDir,"shared_prefs");
        File[] files = prefsdir.listFiles();
        ArrayList<File> filesToBackup = new ArrayList<>();
        
        if (files != null) {
            for (File file : files) {
                if (file.getName().contains("net.samvankooten.finnstickers"))
                    filesToBackup.add(file);
            }
        }
        
        return filesToBackup;
    }
}
