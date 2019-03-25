package net.samvankooten.finnstickers.ar;

import android.Manifest;

import com.google.ar.sceneform.ux.ArFragment;

public class ArFragmentExtStorage extends ArFragment {
    
    @Override
    public String[] getAdditionalPermissions() {
        return new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.RECORD_AUDIO};
    }
}
