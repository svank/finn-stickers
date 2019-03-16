package net.samvankooten.finnstickers;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;

import com.google.android.material.snackbar.Snackbar;

import net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerActivity;
import net.samvankooten.finnstickers.utils.StickerPackRepository;
import net.samvankooten.finnstickers.utils.Util;

import java.util.List;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class ContentPickerPackPickerActivity extends AppCompatActivity {
    private static final String TAG = "PickerActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Util.performNeededMigrations(this);
        setContentView(R.layout.activity_main);
        setSupportActionBar(findViewById(R.id.toolbar));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    
        RecyclerView mainView = findViewById(R.id.pack_list_view);
        mainView.setLayoutManager(new LinearLayoutManager(this, RecyclerView.VERTICAL, false));
        findViewById(R.id.swipeRefresh).setEnabled(false);
        
        List<StickerPack> pack_list;
        try {
            pack_list = StickerPackRepository.getInstalledPacks(this);
        } catch (Exception e) {
            Snackbar.make(mainView, getString(R.string.unexpected_error), Snackbar.LENGTH_LONG);
            Log.e(TAG, "Error getting installed packs", e);
            return;
        }
        
        StickerPackListAdapter adapter = new StickerPackListAdapter(pack_list, this, false);
        adapter.setShowFooter(false);
        adapter.setOnClickListener(pack -> {
            Intent intent = new Intent(ContentPickerPackPickerActivity.this, StickerPackViewerActivity.class);
        
            intent.putExtra(StickerPackViewerActivity.PACK, pack.getPackname());
            intent.putExtra(StickerPackViewerActivity.PICKER, true);
    
            startActivityForResult(intent, 314);
        });
    
        if (pack_list.size() == 0) {
            Log.e(TAG, "no packs");
            adapter.overrideHeaderText(getString(R.string.no_packs_installed));
        }
        mainView.setAdapter(adapter);
    }
    
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 314 && resultCode == RESULT_OK) {
                Uri resultUri = Uri.parse(data.getStringExtra("uri"));
                Intent result = new Intent();
                result.setData(resultUri);
                setResult(Activity.RESULT_OK, result);
                finish();
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Seems to be needed to have a working "back" button in the toolbar
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}