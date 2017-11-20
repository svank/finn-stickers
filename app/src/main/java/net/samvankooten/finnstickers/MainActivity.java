package net.samvankooten.finnstickers;

import android.app.FragmentManager;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import java.net.URL;

public class MainActivity extends AppCompatActivity implements DownloadCallback<StickerPackListDownloadTask.Result> {
    public static final String TAG = "MainActivity";

    public static final String URL_BASE = "http://samvankooten.net/finn_stickers/v2/";
    public static final String PACK_LIST_URL = URL_BASE + "sticker_pack_list.json";

    // Keep a reference to the NetworkFragment, which owns the AsyncTask object
    // that is used to execute network ops.
    private NetworkFragment mNetworkFragment;

    private ListView mListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    
        Button refresh = findViewById(R.id.refresh_button);
        refresh.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                populatePackList();
            }
        });
        
        populatePackList();
    }
    
    public void populatePackList() {
        FragmentManager fragmentManager = getFragmentManager();
    
        findViewById(R.id.refresh_button).setVisibility(View.GONE);
        findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
    
        mNetworkFragment = NetworkFragment.getInstance(fragmentManager, PACK_LIST_URL);
        mListView = findViewById(R.id.pack_list_view);
    
        try {
            // TODO: Check network connectivity first
            AsyncTask packListTask = new StickerPackListDownloadTask(this, this,
                    new URL(PACK_LIST_URL), getCacheDir(), getFilesDir());
            mNetworkFragment.startDownload(packListTask);
        } catch (Exception e) {
            Log.e(TAG, "Bad pack list download effort", e);
        }
    }

    public void updateFromDownload(StickerPackListDownloadTask.Result result, Context mContext){
        findViewById(R.id.progressBar).setVisibility(View.GONE);
        if (result == null) {
            Toast.makeText(this, "No network connectivity",
                    Toast.LENGTH_SHORT).show();
            findViewById(R.id.refresh_button).setVisibility(View.VISIBLE);
            return;
        }
        
        if (result.mException != null) {
            Log.e(TAG, "Error downloading sticker pack list", result.mException);
            Toast.makeText(this, "Error: " + result.mException.toString(),
                    Toast.LENGTH_LONG).show();
            findViewById(R.id.refresh_button).setVisibility(View.VISIBLE);
            return;
        }
        StickerPack[] packs = result.mResultValue;
        StickerPackAdapter adapter = new StickerPackAdapter(this, packs);
        mListView.setAdapter(adapter);
        
        // To allow clicking on list items directly, as seen in
        // https://www.raywenderlich.com/124438/android-listview-tutorial
        mListView.setClickable(true);
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Log.d(TAG, "click handler");
                StickerPack selectedPack = (StickerPack) parent.getItemAtPosition(position);
                if (selectedPack.getStatus() == StickerPack.Status.INSTALLING)
                    return;

                Intent intent = new Intent(MainActivity.this, StickerPackViewerActivity.class);

                intent.putExtra("pack", selectedPack);
                intent.putExtra("picker", false);
                
                startActivity(intent);
            }

        });
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
        if (mNetworkFragment != null) {
            mNetworkFragment.cancelDownload();
            mNetworkFragment = null;
        }
    }

}
