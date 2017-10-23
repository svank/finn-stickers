package net.samvankooten.finnstickers;

import android.app.FragmentManager;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.net.MalformedURLException;
import java.net.URL;

public class MainActivity extends FragmentActivity implements DownloadCallback<StickerPackListDownloadTask.Result> {
    public static final String TAG = "MainActivity";

    public static final String URL_STRING = "http://samvankooten.net/finn_stickers/data.xml";
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
        StickerProcessor.context = this.getApplicationContext();

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
        StickerPack[] packs = result.mResultValue;
        String[] packnames = new String[packs.length];
        for (int i=0; i<packnames.length; i++) {
            packnames[i] = packs[i].getPackname();
        }

        ArrayAdapter adapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, packnames);
        mListView.setAdapter(adapter);
    }

    public void launchStickerUpdate(View view) {
        if (!mDownloading) {
            mDownloading = true;
//            findViewById(R.id.manualStartButton).setVisibility(View.INVISIBLE);
//            findViewById(R.id.manuallRemoveButton).setVisibility(View.INVISIBLE);
//            findViewById(R.id.completionTextView).setVisibility(View.INVISIBLE);
//            findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
//            mNetworkFragment.startDownload();
        }
    }

    public void launchStickerRemove(View view) {
//        findViewById(R.id.manualStartButton).setVisibility(View.INVISIBLE);
//        findViewById(R.id.manuallRemoveButton).setVisibility(View.INVISIBLE);
//        findViewById(R.id.completionTextView).setVisibility(View.INVISIBLE);
//        findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
        StickerProcessor.clearStickers();
//        updateFromDownload(null);
    }

//    public void updateFromDownload(String result) {
        // Update the UI based on result of download.
//        findViewById(R.id.progressBar).setVisibility(View.INVISIBLE);
//        findViewById(R.id.completionTextView).setVisibility(View.VISIBLE);
//        TextView resultTextView = findViewById(R.id.resultTextView);
//        if (result != null) {
//            resultTextView.setText(result);
//            resultTextView.setVisibility(View.VISIBLE);
//        }
        // Display a downloaded image for verification
//        ImageView imview = findViewById(R.id.imageView);
//        String filename = this.getApplicationContext().getFilesDir() + "/pack_icon.jpg";
//        imview.setImageBitmap(BitmapFactory.decodeFile(filename));


//        findViewById(R.id.manualStartButton).setVisibility(View.VISIBLE);
//        findViewById(R.id.manuallRemoveButton).setVisibility(View.VISIBLE);
//    }

    @Override
    public NetworkInfo getActiveNetworkInfo() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo;
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
        }
    }

}
