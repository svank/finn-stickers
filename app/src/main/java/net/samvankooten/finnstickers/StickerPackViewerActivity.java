package net.samvankooten.finnstickers;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.File;
import java.util.List;

public class StickerPackViewerActivity extends AppCompatActivity {
    
    public static final String TAG = "StckrPackViewerActivity";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sticker_pack_viewer);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    
        String packName = (String) this.getIntent().getExtras().get("packName");
        
        StickerPack pack;
        try {
            String path = StickerPack.buildJSONPath(getFilesDir(), packName);
            JSONObject obj = new JSONObject(Util.readTextFile(new File(path)));
            pack = new StickerPack(obj);
        } catch (Exception e) {
            Log.e(TAG, "Error loading JSON", e);
            return;
        }
        
        setTitle(pack.getPackname());
        
        boolean showUpdates = false;
        if ((System.currentTimeMillis() / 1000L - pack.getUpdatedTimestamp()) < 7*24*60*60
                && pack.getUpdatedURIs().size() > 0)
            showUpdates = true;
        
        // These GridViews will make themselves tall rather than scrolling, so we can have
        // multiple grids within one ScrollView that scroll as one.
        ExpandableHeightGridView gridview = (ExpandableHeightGridView) findViewById(R.id.gridview);
        ExpandableHeightGridView updatedGridview = (ExpandableHeightGridView) findViewById(R.id.gridview_updated);
        gridview.setExpanded(true);
        updatedGridview.setExpanded(true);
        
        // Ensures ScrollView will start at top, rather than scrolling a bit to put the
        // tippy-top of the GridView at the top.
        gridview.setFocusable(false);
        updatedGridview.setFocusable(false);
        
        TextView updatedLabel = (TextView) findViewById(R.id.updatedLabel);
        TextView existingLabel = (TextView) findViewById(R.id.existingLabel);
        
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
        
        gridview.setAdapter(new StickerPackViewerAdapter(this, uris));
    }
    
}
