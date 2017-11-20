package net.samvankooten.finnstickers;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class StickerPackViewerActivity extends AppCompatActivity implements DownloadCallback<StickerPackViewerDownloadTask.Result > {
    
    public static final String TAG = "StckrPackViewerActivity";
    
    private StickerPack pack;
    private boolean picker;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sticker_pack_viewer);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    
        pack = (StickerPack) this.getIntent().getSerializableExtra("pack");
        picker = this.getIntent().getBooleanExtra("picker", false);
        
        if (!picker)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        
        setTitle(pack.getPackname() + " Sticker Pack");
        
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
            updatedGridview.setAdapter(new StickerPackViewerLocalAdapter(this, updatedUris));
            
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
            gridview.setAdapter(new StickerPackViewerLocalAdapter(this, uris));
        } else {
            populateRemoteItems();
        }
    
        Button refresh = findViewById(R.id.refresh_button);
        refresh.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                populateRemoteItems();
            }
        });
        
        if (picker) {
            gridview.setClickable(true);
            gridview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                    Intent data = new Intent();
                    data.putExtra("uri", (String) view.getTag(R.id.sticker_uri));
                    setResult(RESULT_OK, data);
                    finish();
                }
            });
        }
    }
    
    private void populateRemoteItems() {
        findViewById(R.id.refresh_button).setVisibility(View.GONE);
        findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
        StickerPackViewerDownloadTask task = new StickerPackViewerDownloadTask(this, pack, this);
        task.execute();
    }
    
    @Override
    public void updateFromDownload(StickerPackViewerDownloadTask.Result result, Context mContext) {
        if (result == null) {
            // No network connectivity
            Toast.makeText(this, "No network connectivity",
                    Toast.LENGTH_SHORT).show();
            findViewById(R.id.refresh_button).setVisibility(View.VISIBLE);
            findViewById(R.id.progressBar).setVisibility(View.GONE);
            return;
        }
        findViewById(R.id.progressBar).setVisibility(View.GONE);
        ExpandableHeightGridView gridview = findViewById(R.id.gridview);
        gridview.setAdapter(new StickerPackViewerRemoteAdapter(this, result.images));
    }
    
    @Override
    public NetworkInfo getActiveNetworkInfo(Context context) {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        return connectivityManager.getActiveNetworkInfo();
    }
    
    @Override
    public void onProgressUpdate(int progressCode, int percentComplete) {
        switch(progressCode) {
            // TODO: add UI behavior for progress updates here.
            case Progress.ERROR:
                
                break;
            case Progress.CONNECT_SUCCESS:
                
                break;
            case Progress.GET_INPUT_STREAM_SUCCESS:
                
                break;
            case Progress.PROCESS_INPUT_STREAM_IN_PROGRESS:
                
                break;
            case Progress.PROCESS_INPUT_STREAM_SUCCESS:
                
                break;
        }
    }
    
    @Override
    public void finishDownloading() {
//        if (mNetworkFragment != null) {
//            mNetworkFragment.cancelDownload();
//            mNetworkFragment = null;
//        }
    }
    
}
