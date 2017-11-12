package net.samvankooten.finnstickers;

/**
 * Created by sam on 9/24/17.
 * From https://github.com/firebase/quickstart-android/blob/master/app-indexing/app/src/main/java/com/google/samples/quickstart/app_indexing/AppIndexingService.java
 */

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

public class AppIndexingService extends IntentService {
    public static final String TAG = "AppIndexingService";

    public AppIndexingService() {
        super("AppIndexingService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(TAG, "onHandleIntent");
        UpdateManager manager = new UpdateManager();
        manager.backgroundUpdate(this);
    }
}