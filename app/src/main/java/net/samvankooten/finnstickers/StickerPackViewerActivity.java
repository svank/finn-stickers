package net.samvankooten.finnstickers;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.widget.GridView;

import org.json.JSONObject;

import java.io.File;

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
        
        GridView gridview = (GridView) findViewById(R.id.gridview);
        gridview.setAdapter(new StickerPackViewerAdapter(this, pack));
    }
    
}
