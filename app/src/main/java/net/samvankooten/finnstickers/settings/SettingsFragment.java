package net.samvankooten.finnstickers.settings;

import android.os.Bundle;

import net.samvankooten.finnstickers.R;

import androidx.preference.PreferenceFragmentCompat;

public class SettingsFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings_screen, rootKey);
    }
}
