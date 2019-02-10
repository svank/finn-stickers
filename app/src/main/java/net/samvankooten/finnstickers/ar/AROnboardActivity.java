package net.samvankooten.finnstickers.ar;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

import com.github.paolorotolo.appintro.AppIntro;

import net.samvankooten.finnstickers.OnboardSlide;
import net.samvankooten.finnstickers.R;

import java.util.LinkedList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import static net.samvankooten.finnstickers.ar.ARActivity.AR_PREFS;

public class AROnboardActivity extends AppIntro {
    
    private static final String[] neededPerms = new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};
    private static final int EXT_STORAGE_REQ_CODE = 142;
    private boolean promptARCoreInstall;
    private Fragment pendingFragment;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        getWindow().setStatusBarColor(getResources().getColor(R.color.colorAccentDark));
    
        promptARCoreInstall = getIntent().getBooleanExtra("promptARCoreInstall", false);
        
        String[] permsToAskFor = getNeededPerms();
        if (permsToAskFor.length > 0)
            askForPermissions(permsToAskFor, 1);
        
        OnboardSlide slide;
        
        slide = OnboardSlide.newInstance(R.layout.onboard_slide);
        slide.setTitle(R.string.ar_onboard_title_1);
        slide.setText(R.string.ar_onboard_text_1);
        slide.setImageDrawable(R.drawable.ar_welcome);
        addSlide(slide);
        
        slide = OnboardSlide.newInstance(R.layout.onboard_slide);
        slide.setTitle(R.string.ar_onboard_title_2);
        slide.setText(R.string.ar_onboard_text_2);
        slide.setVideoUri(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.ar_onboard_find_surfaces));
        addSlide(slide);
        
        slide = OnboardSlide.newInstance(R.layout.onboard_slide);
        slide.setTitle(R.string.ar_onboard_title_3);
        slide.setText(R.string.ar_onboard_text_3);
        slide.setVideoUri(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.ar_onboard_how_to_place));
        addSlide(slide);
    
        slide = OnboardSlide.newInstance(R.layout.onboard_slide);
        slide.setTitle(R.string.ar_onboard_title_4);
        slide.setText(R.string.ar_onboard_text_4);
        slide.setVideoUri(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.ar_onboard_moving));
        addSlide(slide);
    }
    
    private String[] getNeededPerms() {
        List<String> permsToAskFor = new LinkedList<>();
        for (String perm : neededPerms) {
            if (ContextCompat.checkSelfPermission(this, perm)
                    != PackageManager.PERMISSION_GRANTED)
                permsToAskFor.add(perm);
        }
        return permsToAskFor.toArray(new String[0]);
    }
    
    @Override
    public void onSkipPressed(Fragment currentFragment) {
        super.onSkipPressed(currentFragment);
        
        String[] neededPerms = getNeededPerms();
        if (neededPerms.length > 0) {
            // The library doesn't do the permissions prompt if Skip is pressed, so we
            // have to do it ourselves. After we'll want to call onDonePressed to wrap
            // things up, and it expects currentFragment as input, so make sure we have
            // that ready.
            pendingFragment = currentFragment;
            ActivityCompat.requestPermissions(this, neededPerms, EXT_STORAGE_REQ_CODE);
        } else
            onDonePressed(currentFragment);
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] results) {
        switch (requestCode) {
            case EXT_STORAGE_REQ_CODE:
                // We initiated the request after the user pressed the Skip button,
                // so we'll handle this
                onDonePressed(pendingFragment);
                pendingFragment = null;
            
            default:
                // super() initiated the request after the user clicked "Next"
                // Let super() handle this.
                super.onRequestPermissionsResult(requestCode, permissions, results);
        }
    }
    
    @Override
    public void onDonePressed(Fragment currentFragment) {
        super.onDonePressed(currentFragment);
        
        if (promptARCoreInstall) {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.may_need_arcore_title))
                    .setMessage(getString(R.string.may_need_arcore))
                    .setPositiveButton(android.R.string.ok, (d, i) -> startArActivity())
                    .setNegativeButton(android.R.string.cancel, (d, i) -> finish())
                    .create().show();
        } else {
            startArActivity();
        }
    }
    
    private void startArActivity() {
        SharedPreferences sharedPreferences = getSharedPreferences(AR_PREFS, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("hasRunAR", true);
        editor.apply();
        
        Intent intent = new Intent(this, ARActivity.class);
        finish();
        startActivity(intent);
    }
    
    @Override
    public void onSlideChanged(@Nullable Fragment oldFragment, @Nullable Fragment newFragment) {
        super.onSlideChanged(oldFragment, newFragment);
        if (newFragment != null)
            ((OnboardSlide) newFragment).seekToStartIfVideo();
    }
}