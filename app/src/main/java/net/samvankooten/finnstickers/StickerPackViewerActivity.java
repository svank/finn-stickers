package net.samvankooten.finnstickers;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.TextView;

import java.util.List;

public class StickerPackViewerActivity extends AppCompatActivity implements DownloadCallback<StickerPackViewerDownloadTask.Result > {
    
    public static final String TAG = "StckrPackViewerActivity";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sticker_pack_viewer);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    
        StickerPack pack = (StickerPack) this.getIntent().getSerializableExtra("pack");
        
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
            StickerPackViewerDownloadTask task = new StickerPackViewerDownloadTask(this, pack, this);
            task.execute();
        }
    }
    
    @Override
    public void updateFromDownload(StickerPackViewerDownloadTask.Result result, Context mContext) {
        ExpandableHeightGridView gridview = (ExpandableHeightGridView) findViewById(R.id.gridview);
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
