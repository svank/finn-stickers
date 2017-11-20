package net.samvankooten.finnstickers;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.util.Log;

/**
 * Created by sam on 11/19/17.
 */

public class UpdateJob extends JobService {
    private static final String TAG = "UpdateJob";
    
    @Override
    public boolean onStartJob(JobParameters params) {
        Log.d(TAG, "Running job");
        UpdateManager manager = new UpdateManager();
        manager.backgroundUpdate(getApplicationContext());
        return false;
    }
    
    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
