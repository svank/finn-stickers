package net.samvankooten.finnstickers.settings;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.LayoutInflater;
import android.webkit.WebView;

import com.google.android.gms.oss.licenses.OssLicensesMenuActivity;
import com.google.android.material.snackbar.Snackbar;

import net.samvankooten.finnstickers.Constants;
import net.samvankooten.finnstickers.MainActivity;
import net.samvankooten.finnstickers.R;
import net.samvankooten.finnstickers.misc_classes.ReindexJob;
import net.samvankooten.finnstickers.utils.Util;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import static android.app.Activity.RESULT_OK;

public class SettingsFragment extends PreferenceFragmentCompat {
    private static final String TAG = "SettingsFragment";
    
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings_screen, rootKey);
    
        findPreference("export").setOnPreferenceClickListener(preference -> onExport());
        findPreference("import").setOnPreferenceClickListener(preference -> onImport());
        
        findPreference("privacy").setOnPreferenceClickListener(preference -> {
            WebView view = (WebView) LayoutInflater.from(getContext()).inflate(R.layout.dialog_privacy_policy, null);
            view.loadUrl("https://samvankooten.net/finn_stickers/privacy_policy.html");
            new AlertDialog.Builder(getContext())
                    .setTitle(getString(R.string.view_privacy_policy_title))
                    .setView(view)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return true;
        });
        
        findPreference("oss").setOnPreferenceClickListener(preference -> {
            OssLicensesMenuActivity.setActivityTitle(getString(R.string.view_licenses_title));
            startActivity(new Intent(getContext(), OssLicensesMenuActivity.class));
            return true;
        });
        
        findPreference("refresh_firebase").setOnPreferenceClickListener(preference -> {
            ReindexJob.doReindex(getContext());
            Snackbar.make(getView(), getString(R.string.settings_refresh_firebase_complete), Snackbar.LENGTH_SHORT).show();
            return true;
        });
    }
    
    @SuppressLint("ApplySharedPref")
    private boolean onExport() {
        // Ensure a restore is performed when this data is imported.
        Util.markPendingRestore(getContext(), true);
        // Ensure all shared prefs are committed to disk
        Util.getPrefs(getContext()).edit().commit();
        PreferenceManager.getDefaultSharedPreferences(getContext()).edit().commit();
        File prefsdir = new File(getContext().getApplicationInfo().dataDir,"shared_prefs");
        File[] files = prefsdir.listFiles();
        for (int i=0; i<files.length; i++) {
            if (files[i].toString().contains("com.google.android.gms"))
                files[i] = null;
        }
        File output = new File(getContext().getCacheDir(), Constants.DIR_FOR_SHARED_FILES);
        output = Util.generateUniqueFile(output.toString(),
                "_finn_stickers_settings_export.zip");
        
        boolean success = Util.createZipFile(files, output);
        Util.markPendingRestore(getContext(), false);
        
        if (!success) {
            onGenericError();
            return true;
        }
    
        Uri contentUri = FileProvider.getUriForFile(
                getContext(), "net.samvankooten.finnstickers.fileprovider", output);
        if (contentUri != null) {
            Intent shareIntent = new Intent();
            shareIntent.setAction(Intent.ACTION_SEND);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            shareIntent.setType(getContext().getContentResolver().getType(contentUri));
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            startActivity(
                    Intent.createChooser(shareIntent, getResources().getString(R.string.config_share_text)));
        }
        
        return true;
    }
    
    private boolean onImport() {
        Intent requestFileIntent = new Intent(Intent.ACTION_GET_CONTENT);
        requestFileIntent.setType("*/*");
        startActivityForResult(requestFileIntent, 1122);
    
        return true;
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent returnIntent) {
        if (resultCode != RESULT_OK || requestCode != 1122 || returnIntent.getData() == null)
            return;
        
        Uri inputUri = returnIntent.getData();
        ParcelFileDescriptor inputPDF;
        FileDescriptor fd;
        try {
            inputPDF = getContext().getContentResolver().openFileDescriptor(inputUri, "r");
            fd = inputPDF.getFileDescriptor();
        } catch (FileNotFoundException|NullPointerException e) {
            Log.e(TAG, "Error opening uri", e);
            onGenericError();
            return;
        }
        
        // Verify extraction before deleting our current configuration
        File extractDest = new File(getContext().getCacheDir(), "import_text");
        boolean success = Util.extractZipFile(new FileInputStream(fd), extractDest);
        
        if (!success) {
            onGenericError();
            try {
                Util.delete(extractDest);
            } catch (IOException e) {
                Log.e(TAG, "Error deleting", e);
            }
            return;
        }
        
        // Delete all the old configuration
        File prefsDir = new File(getContext().getApplicationInfo().dataDir,"shared_prefs");
        File[] files = prefsDir.listFiles();
        int i = 0;
        try {
            for (i=0; i<files.length; i++)
                Util.delete(files[i]);
            
            files = getContext().getFilesDir().listFiles();
            for (i=0; i<files.length; i++)
                Util.delete(files[i]);
        } catch (IOException e) {
            Log.e(TAG, "Error deleting " + files[i].toString(), e);
            onGenericError();
            return;
        }
        
        // Copy the new config into place
        File[] filesToCopy = extractDest.listFiles();
        for (i=0; i<filesToCopy.length; i++) {
            try {
                Util.copy(filesToCopy[i], new File(prefsDir, filesToCopy[i].getName()));
            } catch (IOException e) {
                Log.e(TAG, "Error copying " + filesToCopy[i], e);
            }
        }
        
        try {
            Util.delete(extractDest);
        } catch (IOException e) {
            Log.e(TAG, "Error deleting extracted files", e);
        }
        
        // Restart the app
        Intent intent = new Intent(getContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        getContext().startActivity(intent);
        Runtime.getRuntime().exit(0);
    }
    
    private void onGenericError() {
        Snackbar.make(getView(), getContext().getString(R.string.unexpected_error), Snackbar.LENGTH_LONG).show();
    }
}
