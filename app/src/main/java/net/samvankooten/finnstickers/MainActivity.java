package net.samvankooten.finnstickers;

import android.app.FragmentManager;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

public class MainActivity extends FragmentActivity implements DownloadCallback<String> {
    private static final String MDTAG = "Manual Download Tag";
    public static final String URL_STRING = "http://samvankooten.net/finn_stickers/data.xml";

    // Keep a reference to the NetworkFragment, which owns the AsyncTask object
    // that is used to execute network ops.
    private NetworkFragment mNetworkFragment;

    // Boolean telling us whether a download is in progress, so we don't trigger overlapping
    // downloads with consecutive button clicks.
    private boolean mDownloading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        FragmentManager fragmentManager = getFragmentManager();
        mNetworkFragment = NetworkFragment.getInstance(fragmentManager, URL_STRING);
        StickerProcessor.context = this.getApplicationContext();
    }

    public void launchStickerUpdate(View view) {
        findViewById(R.id.manualStartButton).setVisibility(View.INVISIBLE);
        findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
        Log.v(MDTAG, "Starting Download...");
        mNetworkFragment.startDownload();
        Log.v(MDTAG, "Started Download");
    }

    public void updateFromDownload(String result) {
        // Update your UI here based on result of download.
        Log.v(MDTAG, "Starting UI update...");
        findViewById(R.id.progressBar).setVisibility(View.INVISIBLE);
        findViewById(R.id.completionTextView).setVisibility(View.VISIBLE);
        TextView resultTextView = findViewById(R.id.resultTextView);
        resultTextView.setVisibility(View.VISIBLE);
        resultTextView.setText(result);
        // Display a downloaded image for verification
        ImageView imview = findViewById(R.id.imageView);
        String filename = this.getApplicationContext().getFilesDir() + "/pack_icon.png";
        imview.setImageBitmap(BitmapFactory.decodeFile(filename));
    }

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
            // You can add UI behavior for progress updates here.
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
