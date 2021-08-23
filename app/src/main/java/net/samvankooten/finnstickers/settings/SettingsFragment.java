package net.samvankooten.finnstickers.settings;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.webkit.WebView;
import android.widget.Toast;

import com.google.android.gms.oss.licenses.OssLicensesMenuActivity;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.appindexing.FirebaseAppIndex;

import net.samvankooten.finnstickers.BuildConfig;
import net.samvankooten.finnstickers.Constants;
import net.samvankooten.finnstickers.MainActivity;
import net.samvankooten.finnstickers.R;
import net.samvankooten.finnstickers.StickerPack;
import net.samvankooten.finnstickers.misc_classes.FinnBackupAgent;
import net.samvankooten.finnstickers.misc_classes.ReindexWorker;
import net.samvankooten.finnstickers.updating.FirebaseMessageReceiver;
import net.samvankooten.finnstickers.updating.UpdateUtils;
import net.samvankooten.finnstickers.utils.StickerPackRepository;
import net.samvankooten.finnstickers.utils.Util;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Calendar;
import java.util.List;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.core.text.HtmlCompat;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import static androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY;

public class SettingsFragment extends PreferenceFragmentCompat {
    private static final String TAG = "SettingsFragment";
    
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings_screen, rootKey);
        
        findPreference(getString(R.string.settings_export_key)).setOnPreferenceClickListener(preference -> onExport());
        findPreference(getString(R.string.settings_import_key)).setOnPreferenceClickListener(preference -> onImport());
        
        findPreference(getString(R.string.settings_privacy_policy_key)).setOnPreferenceClickListener(preference -> {
            WebView view = (WebView) LayoutInflater.from(getContext()).inflate(R.layout.dialog_privacy_policy, null);
            view.loadUrl("https://samvankooten.net/finn_stickers/privacy_policy.html");
            new AlertDialog.Builder(getContext())
                    .setTitle(getString(R.string.view_privacy_policy_title))
                    .setView(view)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return true;
        });
        
        findPreference(getString(R.string.settings_oss_licenses_key)).setOnPreferenceClickListener(preference -> {
            OssLicensesMenuActivity.setActivityTitle(getString(R.string.view_licenses_title));
            startActivity(new Intent(getContext(), OssLicensesMenuActivity.class));
            return true;
        });
        
        findPreference(getString(R.string.settings_github_key)).setOnPreferenceClickListener(preference -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/svank/finn-stickers/"));
            startActivity(browserIntent);
            return true;
        });
        
        findPreference(getString(R.string.settings_about_key)).setOnPreferenceClickListener(preference -> {
            String version = BuildConfig.VERSION_NAME;
            int versionCode = BuildConfig.VERSION_CODE;
    
            Calendar buildDate = Calendar.getInstance();
            buildDate.setTimeInMillis(Long.parseLong(BuildConfig.BUILD_TIME));
            String message = String.format(getString(R.string.settings_about_text),
                    version, versionCode, buildDate.get(Calendar.YEAR));
            new AlertDialog.Builder(getContext())
                    .setMessage(message)
                    .setTitle(getString(R.string.settings_about))
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return true;
        });
        
        findPreference(getString(R.string.settings_refresh_firebase_key)).setOnPreferenceClickListener(preference -> {
            FirebaseAppIndex.getInstance(getContext()).removeAll();
            ReindexWorker.doReindex(getContext());
            Snackbar.make(getView(), getString(R.string.settings_refresh_firebase_complete), Snackbar.LENGTH_SHORT).show();
            return true;
        });
        
        findPreference(getString(R.string.settings_check_in_background_key))
                .setOnPreferenceChangeListener((preference, newValue) -> {
            if ((boolean) newValue) {
                FirebaseMessageReceiver.registerFCMTopics(getContext());
                UpdateUtils.scheduleUpdates(getContext());
            } else {
                FirebaseMessageReceiver.unregisterFCMTopics();
                UpdateUtils.unscheduleUpdates(getContext());
            }
            return true;
        });
        
        findPreference(getString(R.string.settings_theme_key))
            .setOnPreferenceChangeListener(((preference, newValue) -> {
                Util.applyTheme((String) newValue, getContext());
                return true;
            }));
        
        var force_updatable = findPreference(getString(R.string.settings_force_updatable_key));
        if (BuildConfig.VERSION_NAME.contains("dev")) {
            force_updatable.setOnPreferenceClickListener( preference ->{
                forcePacksUpdatable();
                return true;});
        } else
            force_updatable.setVisible(false);
    
        var force_new = findPreference(getString(R.string.settings_force_new_key));
        if (BuildConfig.VERSION_NAME.contains("dev")) {
            force_new.setOnPreferenceClickListener( preference ->{
                forcePackNew();
                return true;});
        } else
            force_new.setVisible(false);
    }
    
    private void forcePacksUpdatable() {
        var packs = StickerPackRepository.getInstalledPacks(getContext());
        if (packs == null) {
            Log.e(TAG, "Error loading packs");
            return;
        }
        for (StickerPack pack : packs)
            pack.forceUpdatable(getContext());
    }
    
    private void forcePackNew() {
        var knownPacks = Util.getKnownPacks(getContext());
        if (knownPacks.size() == 0) {
            Toast.makeText(getContext(), "All packs already forgotten", Toast.LENGTH_SHORT).show();
            return;
        }
        String target = knownPacks.iterator().next();
        for (StickerPack pack : StickerPackRepository.getInstalledPacks(getContext())) {
            if (pack.getPackname().equals(target)) {
                pack.uninstall(getContext());
                break;
            }
        }
        Util.forgetKnownPack(getContext(), target);
        Toast.makeText(getContext(), "Forgot " + target, Toast.LENGTH_SHORT).show();
    }
    
    @SuppressLint("ApplySharedPref")
    private boolean onExport() {
        // Ensure a restore is performed when this data is imported.
        Util.markPendingRestore(getContext(), true);
        // Ensure all shared prefs are committed to disk
        Util.getPrefs(getContext()).edit().commit();
        PreferenceManager.getDefaultSharedPreferences(getContext()).edit().commit();
        
        List<File> filesToBackup = FinnBackupAgent.getFilesToBackup(getContext());
        File output = new File(getContext().getCacheDir(), Constants.DIR_FOR_SHARED_FILES);
        output = Util.generateUniqueFile(output.toString(),
                "_finn_stickers_settings_export.zip");
        boolean success = Util.createZipFile(filesToBackup.toArray(new File[0]), output);
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
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.setData(contentUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, getString(R.string.config_share_text)));
        }
        return true;
    }
    
    final ActivityResultLauncher<String> mGetContent = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            this::startImport);
    
    private boolean onImport() {
        mGetContent.launch("application/zip");
        return true;
    }
    
    private void startImport(Uri inputUri) {
        String filename = "";
        String scheme = inputUri.getScheme();
        
        if (scheme == null)
            Log.e(TAG, "null scheme");
        else if (scheme.equals("file")) {
            filename = inputUri.getLastPathSegment();
        } else if (scheme.equals("content")) {
            Cursor cursor = getContext().getContentResolver().query(inputUri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                filename = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                cursor.close();
            }
        }
        new AlertDialog.Builder(getContext())
                .setTitle(getString(R.string.config_import_confirm_title))
                .setMessage(HtmlCompat.fromHtml(String.format(
                        getString(R.string.config_import_confirm_body),
                        filename), FROM_HTML_MODE_LEGACY))
                .setPositiveButton(android.R.string.yes, (dialog, which) -> doConfigImport(inputUri))
                .setNegativeButton(android.R.string.no, (dialog, which) -> dialog.dismiss())
                .show();
    }
    
    private void doConfigImport(Uri inputUri) {
        ParcelFileDescriptor inputPFD;
        FileDescriptor fd;
        try {
            inputPFD = getContext().getContentResolver().openFileDescriptor(inputUri, "r");
            fd = inputPFD.getFileDescriptor();
        } catch (FileNotFoundException|NullPointerException e) {
            Log.e(TAG, "Error opening uri", e);
            onGenericError();
            return;
        }
        
        // Verify extraction before deleting our current configuration
        File extractDest = new File(getContext().getCacheDir(), "import_test");
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
        FirebaseAppIndex.getInstance(getContext()).removeAll();
        File prefsDir = new File(getContext().getApplicationInfo().dataDir,"shared_prefs");
        File[] files = prefsDir.listFiles();
        int i = 0;
        try {
            for (i = 0; i < files.length; i++)
                Util.delete(files[i]);
    
            files = getContext().getFilesDir().listFiles();
            for (i = 0; i < files.length; i++)
                Util.delete(files[i]);
        } catch (NullPointerException e) {
            Log.e(TAG, "Got null filelist", e);
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
