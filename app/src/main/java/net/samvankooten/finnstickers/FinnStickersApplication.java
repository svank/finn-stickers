package net.samvankooten.finnstickers;

import android.app.Application;

import net.samvankooten.finnstickers.utils.Util;

public class FinnStickersApplication extends Application {
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        Util.performNeededMigrations(this);
        
        Util.applyTheme(Util.getUserPrefs(this).getString(
                getString(R.string.settings_theme_key), "system"),
                this);
    }
}