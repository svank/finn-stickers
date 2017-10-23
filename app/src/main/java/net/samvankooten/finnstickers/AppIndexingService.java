package net.samvankooten.finnstickers;

/**
 * Created by sam on 9/24/17.
 * From https://github.com/firebase/quickstart-android/blob/master/app-indexing/app/src/main/java/com/google/samples/quickstart/app_indexing/AppIndexingService.java
 */

import android.app.IntentService;
import android.content.Intent;

import com.google.firebase.appindexing.FirebaseAppIndex;

public class AppIndexingService extends IntentService {

    private NetworkFragment.DownloadTask mDownloadTask;
    public AppIndexingService() {
        super("AppIndexingService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
//        mDownloadTask = new NetworkFragment().new PackListDownloadTask(null);
//        mDownloadTask.execute(MainActivity.URL_STRING);
    }
}