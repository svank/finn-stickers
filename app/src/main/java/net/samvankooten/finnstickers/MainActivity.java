package net.samvankooten.finnstickers;

import android.annotation.SuppressLint;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    static final String URL_BASE = "https://samvankooten.net/finn_stickers/v3/";
    static final String PACK_LIST_URL = URL_BASE + "sticker_pack_list.json";

    private ListView mListView;
    private StickerPackListViewModel model;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setSupportActionBar(findViewById(R.id.main_toolbar));
        
        UpdateManager.scheduleUpdates(this);
    
        Button refresh = findViewById(R.id.refresh_button);
        refresh.setOnClickListener(v -> {
            displayLoading();
            model.downloadData();
        });
    
        displayLoading();
    
        model = ViewModelProviders.of(this).get(StickerPackListViewModel.class);
        if (!model.infoHasBeenSet()) {
            try {
                model.setInfo(new URL(PACK_LIST_URL), getCacheDir(), getFilesDir());
                model.downloadData();
            } catch (MalformedURLException e) {
                Log.e(TAG, "Bad pack list url " + e.getMessage());
            }
        }
        
        model.getPacks().observe(this, this::updateFromDownload);
    }
    
    private void displayLoading() {
        findViewById(R.id.refresh_button).setVisibility(View.GONE);
        findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
        
        mListView = findViewById(R.id.pack_list_view);
        
        // If there wasn't a network connection, and we loaded just the installed packs,
        // and the user hit "Reload", clear the populated list of packs.
        StickerPackAdapter adapter = new StickerPackAdapter(this, new LinkedList<>());
        mListView.setAdapter(adapter);
    }

    private void updateFromDownload(StickerPackListDownloadTask.Result result){
        findViewById(R.id.progressBar).setVisibility(View.GONE);
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
        StickerPackAdapter adapter = new StickerPackAdapter(this, packs);
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
        return true;
    }
    
    @SuppressLint("InflateParams")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        WebView view;
        switch (item.getItemId()) {
            case R.id.action_view_licenses:
                view = (WebView) LayoutInflater.from(this).inflate(R.layout.dialog_licenses, null);
                view.loadUrl("file:///android_asset/open_source_licenses.html");
                new AlertDialog.Builder(this, R.style.Theme_AppCompat_Light_Dialog_Alert)
                        .setTitle(getString(R.string.view_licenses_title))
                        .setView(view)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
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
    
            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                // This is how Google's example does it, but I'm not really sure how this helps.
                return super.onOptionsItemSelected(item);
        }
    }

}
