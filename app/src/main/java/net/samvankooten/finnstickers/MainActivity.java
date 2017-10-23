package net.samvankooten.finnstickers;

import android.app.FragmentManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ListView;

import java.net.URL;

public class MainActivity extends AppCompatActivity implements DownloadCallback<StickerPackListDownloadTask.Result> {
    public static final String TAG = "MainActivity";

    public static final String URL_BASE = "http://samvankooten.net/finn_stickers/v2/";
    public static final String PACK_LIST_URL = URL_BASE + "sticker_pack_list.json";

    // Keep a reference to the NetworkFragment, which owns the AsyncTask object
    // that is used to execute network ops.
    private NetworkFragment mNetworkFragment;

    // Boolean telling us whether a download is in progress, so we don't trigger overlapping
    // downloads with consecutive button clicks.
    private boolean mDownloading = false;

    private ListView mListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        FragmentManager fragmentManager = getFragmentManager();

        mNetworkFragment = NetworkFragment.getInstance(fragmentManager, PACK_LIST_URL);
        mListView = (ListView) findViewById(R.id.pack_list_view);

        try {
            // TODO: Check network connectivity first
            AsyncTask packListTask = new StickerPackListDownloadTask(this,
                    new URL(PACK_LIST_URL), getCacheDir());
            mNetworkFragment.startDownload(packListTask);
        } catch (Exception e) {
            Log.e(TAG, "Bad pack list download effort", e);
        }
    }

    public void updateFromDownload(StickerPackListDownloadTask.Result result){
        if (result.mException != null) {
            // Todo
            return;
        }
        final StickerPack[] packs = result.mResultValue;
        StickerPackAdapter adapter = new StickerPackAdapter(this, packs);
        mListView.setAdapter(adapter);
        
        // To allow clicking on list items directly, as seen in
        // https://www.raywenderlich.com/124438/android-listview-tutorial
//        final Context context = this;
//        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
//
//            @Override
//            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
//                StickerPack selectedPack = packs[position];
//
//                Intent detailIntent = new Intent(context, StickerPackDetail.class);
//
//                detailIntent.putExtra("title", selectedPack.getPackname());
//                detailIntent.putExtra("dataurl", selectedPack.getDatafile());
//                detailIntent.putExtra("iconFile", selectedPack.getIconfile());
//            }
//
//        });
    }

    public void launchStickerRemove(View view) {
//        findViewById(R.id.manualStartButton).setVisibility(View.INVISIBLE);
//        findViewById(R.id.manuallRemoveButton).setVisibility(View.INVISIBLE);
//        findViewById(R.id.completionTextView).setVisibility(View.INVISIBLE);
//        findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
        StickerProcessor.clearStickers(this);
//        updateFromDownload(null);
    }

    @Override
    public NetworkInfo getActiveNetworkInfo() {
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
        mDownloading = false;
        if (mNetworkFragment != null) {
            mNetworkFragment.cancelDownload();
            mNetworkFragment = null;
        }
    }

}
