package net.samvankooten.finnstickers.updating;

import android.app.job.JobParameters;
import android.app.job.JobService;

import net.samvankooten.finnstickers.utils.Util;

/**
 * Created by sam on 11/19/17.
 */

public class UpdateJob extends JobService {
    private static final String TAG = "UpdateJob";
    
    @Override
    public boolean onStartJob(JobParameters params) {
        Util.performNeededMigrations(getApplicationContext());
        UpdateManager manager = new UpdateManager();
        manager.backgroundUpdate(getApplicationContext(), this, params);
        return false;
    }
    
    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
