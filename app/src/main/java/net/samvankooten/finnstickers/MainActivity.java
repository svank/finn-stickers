package net.samvankooten.finnstickers;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.gms.oss.licenses.OssLicensesMenuActivity;
import com.google.ar.core.ArCoreApk;

import net.samvankooten.finnstickers.ar.ARActivity;
import net.samvankooten.finnstickers.ar.AROnboardActivity;
import net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerActivity;
import net.samvankooten.finnstickers.updating.UpdateUtils;
import net.samvankooten.finnstickers.utils.NotificationUtils;
import net.samvankooten.finnstickers.utils.Util;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProviders;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import static net.samvankooten.finnstickers.ar.ARActivity.AR_PREFS;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    
    public static final String URL_BASE = "https://samvankooten.net/finn_stickers/v3/";
    public static final String PACK_LIST_URL = URL_BASE + "sticker_pack_list.json";
    
    private ListView mListView;
    StickerPackListViewModel model;
    private MenuItem arButton;
    private ArCoreApk.Availability arAvailability;
    private SwipeRefreshLayout swipeLayout;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Util.performNeededMigrations(this);
        setContentView(R.layout.activity_main);
        
        if (!Util.checkIfEverOpened(this))
            start_onboarding();
        
        UpdateUtils.scheduleUpdates(this);
        NotificationUtils.createChannels(this);
        
        Button refresh = findViewById(R.id.refresh_button);
        refresh.setOnClickListener(v -> refresh());
        
        swipeLayout = findViewById(R.id.swipeRefresh);
        swipeLayout.setOnRefreshListener(this::refresh);
        swipeLayout.setColorSchemeResources(R.color.colorAccent);
        
        displayLoading();
        
        model = ViewModelProviders.of(this).get(StickerPackListViewModel.class);
        if (!model.infoHasBeenSet()) {
            // Give the ViewModel information about the environment if it hasn't yet been set
            // (i.e. we're starting the application fresh, rather than rotating the screen)
            try {
                model.setInfo(new URL(PACK_LIST_URL), getCacheDir(), getFilesDir());
                model.downloadData();
            } catch (MalformedURLException e) {
                Log.e(TAG, "Bad pack list url " + e.getMessage());
            }
        }
        
        // Respond when the list of packs becomes available
        model.getPacks().observe(this, this::updateFromDownload);
        
        // When a pack finishes installing/deleting, receive that notification and
        // update the UI
        model.getPackStatusChange().observe(this, i -> {
            ListView view = findViewById(R.id.pack_list_view);
            StickerPackListAdapter adapter = (StickerPackListAdapter) view.getAdapter();
            adapter.notifyDataSetChanged();
        });
    }
    
    private void start_onboarding() {
        Intent intent = new Intent(this, OnboardActivity.class);
        startActivity(intent);
    }
    
    private void refresh() {
        displayLoading();
        model.downloadData();
    }
    
    private void displayLoading() {
        findViewById(R.id.refresh_button).setVisibility(View.GONE);
        swipeLayout.setRefreshing(true);
        
        mListView = findViewById(R.id.pack_list_view);
        
        // If there wasn't a network connection, and we loaded just the installed packs,
        // and the user hit "Reload", clear the populated list of packs.
        StickerPackListAdapter adapter = new StickerPackListAdapter(this, new LinkedList<>());
        mListView.setAdapter(adapter);
    }
    
    private void updateFromDownload(StickerPackListDownloadTask.Result result){
        swipeLayout.setRefreshing(false);
        
        if (result == null || !result.networkSucceeded) {
            Toast.makeText(this, "No network connectivity",
                    Toast.LENGTH_SHORT).show();
            findViewById(R.id.refresh_button).setVisibility(View.VISIBLE);
            if (result == null)
                return;
        }
        
        if (result.exception != null) {
            Log.e(TAG, "Error downloading sticker pack list", result.exception);
            Toast.makeText(this, "Error: " + result.exception.toString(),
                    Toast.LENGTH_LONG).show();
            findViewById(R.id.refresh_button).setVisibility(View.VISIBLE);
            return;
        }
        List<StickerPack> packs = result.packs;
        StickerPackListAdapter adapter = new StickerPackListAdapter(this, packs);
        mListView.setAdapter(adapter);
        
        // To allow clicking on list items directly, as seen in
        // https://www.raywenderlich.com/124438/android-listview-tutorial
        mListView.setClickable(true);
        mListView.setOnItemClickListener((parent, view, position, id) -> {
            StickerPack selectedPack = (StickerPack) parent.getItemAtPosition(position);
            if (selectedPack.getStatus() == StickerPack.Status.INSTALLING)
                return;
            
            Intent intent = new Intent(MainActivity.this, StickerPackViewerActivity.class);
            
            intent.putExtra("pack", selectedPack);
            intent.putExtra("picker", false);
            
            startActivity(intent);
        });
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu_items, menu);
        
        // Disable this button until we know AR is supported on this device.
        arButton = menu.findItem(R.id.action_start_AR);
        arButton.setVisible(false);
        arButton.setEnabled(false);
        maybeEnableArButton();
        return true;
    }
    
    @SuppressLint("InflateParams")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        WebView view;
        switch (item.getItemId()) {
            case R.id.action_onboard:
                start_onboarding();
                return true;
            
            case R.id.action_view_licenses:
                OssLicensesMenuActivity.setActivityTitle(getString(R.string.view_licenses_title));
                startActivity(new Intent(this, OssLicensesMenuActivity.class));
                return true;
            
            case R.id.action_view_privacy_policy:
                view = (WebView) LayoutInflater.from(this).inflate(R.layout.dialog_licenses, null);
                view.loadUrl("https://samvankooten.net/finn_stickers/privacy_policy.html");
                new AlertDialog.Builder(this, R.style.Theme_AppCompat_Light_Dialog_Alert)
                        .setTitle(getString(R.string.view_privacy_policy_title))
                        .setView(view)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                return true;
            
            case R.id.action_start_AR:
                SharedPreferences sharedPreferences = getSharedPreferences(AR_PREFS, MODE_PRIVATE);
                if (sharedPreferences.getBoolean("hasRunAR", false))
                    startActivity(new Intent(this, ARActivity.class));
                else {
                    Intent intent = new Intent(this, AROnboardActivity.class);
                    intent.putExtra("promptARCoreInstall", (arAvailability == null
                            || arAvailability == ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD
                            || arAvailability == ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED));
                    startActivity(intent);
                }
                return true;
                
            case R.id.action_send_feedback:
                Intent Email = new Intent(Intent.ACTION_SEND);
                Email.setType("text/email");
                Email.putExtra(Intent.EXTRA_EMAIL, new String[] { "appfeedback@samvankooten.net" });
                Email.putExtra(Intent.EXTRA_SUBJECT, "Finn Stickers");
                startActivity(Intent.createChooser(Email, getResources().getString(R.string.send_feedback_share_label)));
                return true;
                
            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                // This is how Google's example does it, but I'm not really sure how this helps.
                return super.onOptionsItemSelected(item);
        }
    }
    
    /**
     * From Google's AR docs, check if AR is supported an enable the AR button if so.
     */
    void maybeEnableArButton() {
        arAvailability = ArCoreApk.getInstance().checkAvailability(this);
        if (arAvailability.isTransient()) {
            // Re-query at 5Hz while compatibility is checked in the background.
            new Handler().postDelayed(this::maybeEnableArButton, 200);
        }
        if (arAvailability.isSupported()) {
            arButton.setVisible(true);
            arButton.setEnabled(true);
        }
    }
}
