package net.samvankooten.finnstickers;

import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.stfalcon.frescoimageviewer.ImageViewer;

import java.util.List;

public class StickerPackViewerActivity extends AppCompatActivity {
    
    private static final String TAG = "StckrPackViewerActivity";
    
    private StickerPack pack;
    private boolean picker;
    private StickerPackViewerViewModel model;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Fresco.initialize(this);
        setContentView(R.layout.activity_sticker_pack_viewer);
    
        pack = (StickerPack) this.getIntent().getSerializableExtra("pack");
        picker = this.getIntent().getBooleanExtra("picker", false);
    
        if (!picker)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        
        setTitle(pack.getPackname() + " Sticker Pack");
    
        model = ViewModelProviders.of(this).get(StickerPackViewerViewModel.class);
        
        boolean showUpdates = false;
        if ((System.currentTimeMillis() / 1000L - pack.getUpdatedTimestamp()) < 7*24*60*60
                && pack.getUpdatedURIs().size() > 0 && !picker)
            showUpdates = true;
        
        // These GridViews will make themselves tall rather than scrolling, so we can have
        // multiple grids within one ScrollView that scroll as one.
        ExpandableHeightGridView gridview = findViewById(R.id.gridview);
        ExpandableHeightGridView updatedGridview = findViewById(R.id.gridview_updated);
        gridview.setExpanded(true);
        updatedGridview.setExpanded(true);
        
        // Ensures ScrollView will start at top, rather than scrolling a bit to put the
        // tippy-top of the GridView at the top.
        gridview.setFocusable(false);
        updatedGridview.setFocusable(false);
        
        TextView updatedLabel = findViewById(R.id.updatedLabel);
        TextView existingLabel = findViewById(R.id.existingLabel);
        
        if (!showUpdates) {
            updatedGridview.setVisibility(View.GONE);
            updatedLabel.setVisibility(View.GONE);
            existingLabel.setVisibility(View.GONE);
        }
        
        List<String> uris = pack.getStickerURIs();
        
        if (showUpdates) {
            List<String> updatedUris = pack.getUpdatedURIs();
            updatedGridview.setAdapter(new StickerPackViewerAdapter(this, updatedUris));
            
            for (int i=0; i<updatedUris.size(); i++) {
                for (int j=0; j<uris.size(); j++) {
                    if (uris.get(j).equals(updatedUris.get(i))) {
                        uris.remove(j);
                        break;
                    }
                }
            }
        }
        if (pack.getStatus() == StickerPack.Status.INSTALLED) {
            gridview.setAdapter(new StickerPackViewerAdapter(this, uris));
        } else {
            displayLoading();
            model.setPack(pack);
            model.getResult().observe(this, this::updateFromDownload);
        }
    
        Button refresh = findViewById(R.id.refresh_button);
        refresh.setOnClickListener(v -> {
            displayLoading();
            model.downloadData();
        });
    
        gridview.setClickable(true);
        if (picker) {
            gridview.setOnItemClickListener((adapterView, view, position, id) -> {
                Intent data = new Intent();
                data.putExtra("uri", (String) view.getTag(R.id.sticker_uri));
                setResult(RESULT_OK, data);
                finish();
            });
        } else {
            gridview.setOnItemClickListener((adapterView, view, position, id) -> {
                LightboxOverlayView overlay = new LightboxOverlayView(
                        this, uris, null, position);
                ImageViewer.Builder builder;
                if (uris.size() == 0)
                    builder = new ImageViewer.Builder(this, model.getResult().getValue().urls);
                else
                    builder = new ImageViewer.Builder(this, uris);
                ImageViewer viewer = builder
                        .setStartPosition(position)
                        .setOverlayView(overlay)
                        .setImageChangeListener(overlay::setPos)
                        .hideStatusBar(false)
                        .show();
            });
        }
    }
    
    private void displayLoading() {
        findViewById(R.id.refresh_button).setVisibility(View.GONE);
        findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
    }
    
    public void updateFromDownload(StickerPackViewerDownloadTask.Result result) {
        if (result == null) {
            // No network connectivity
            Toast.makeText(this, "No network connectivity",
                    Toast.LENGTH_SHORT).show();
            findViewById(R.id.refresh_button).setVisibility(View.VISIBLE);
            findViewById(R.id.progressBar).setVisibility(View.GONE);
            return;
        }
        if (result.exception != null) {
            Log.e(TAG, result.exception.toString());
            Toast.makeText(this, "Unexpected Error",
                    Toast.LENGTH_SHORT).show();
            findViewById(R.id.refresh_button).setVisibility(View.VISIBLE);
            findViewById(R.id.progressBar).setVisibility(View.GONE);
            return;
        }
        findViewById(R.id.progressBar).setVisibility(View.GONE);
        ExpandableHeightGridView gridview = findViewById(R.id.gridview);
        gridview.setAdapter(new StickerPackViewerAdapter(this, result.urls));
    }
    
}
