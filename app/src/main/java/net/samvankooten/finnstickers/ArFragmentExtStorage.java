package net.samvankooten.finnstickers;

import android.Manifest;

import com.google.ar.sceneform.ux.ArFragment;

public class ArFragmentExtStorage extends ArFragment {
    
    @Override
    public String[] getAdditionalPermissions() {
        return new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};
    }
}
