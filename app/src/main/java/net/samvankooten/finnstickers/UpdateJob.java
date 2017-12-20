package net.samvankooten.finnstickers;

import android.app.job.JobParameters;
import android.app.job.JobService;

/**
 * Created by sam on 11/19/17.
 */

public class UpdateJob extends JobService {
    private static final String TAG = "UpdateJob";
    
    @Override
    public boolean onStartJob(JobParameters params) {
        UpdateManager manager = new UpdateManager();
        manager.backgroundUpdate(getApplicationContext(), this, params);
        return false;
    }
    
    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
