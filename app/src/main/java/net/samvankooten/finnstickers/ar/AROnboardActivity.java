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

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import static net.samvankooten.finnstickers.ar.ARActivity.AR_PREFS;

public class AROnboardActivity extends AppIntro {
    
    public static final String LAUNCH_AR = "launchAR";
    public static final String PROMPT_ARCORE_INSTALL = "promptARCoreInstall";
    
    private static final String[] neededPerms = new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};
    private static final int PERM_REQ_CODE = 143;
    private boolean promptARCoreInstall;
    private boolean launchAR;
    private Fragment firstSlide;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        getWindow().setStatusBarColor(getResources().getColor(R.color.colorAccentDark));
    
        promptARCoreInstall = getIntent().getBooleanExtra(PROMPT_ARCORE_INSTALL, false);
        launchAR = getIntent().getBooleanExtra(AROnboardActivity.LAUNCH_AR, false);
        
        OnboardSlide slide;
        
        slide = OnboardSlide.newInstance(R.layout.onboard_slide);
        slide.setTitle(R.string.ar_onboard_title_1);
        slide.setText(R.string.ar_onboard_text_1);
        slide.setImageDrawable(R.drawable.ar_welcome);
        addSlide(slide);
        
        // Keep this so we can tell when to request permissions
        firstSlide = slide;
        
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
        
        doPermissionRequest();
        
        onDonePressed(currentFragment);
    }
    
    @Override
    public void onDonePressed(Fragment currentFragment) {
        super.onDonePressed(currentFragment);
        
        if (promptARCoreInstall) {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.may_need_arcore_title))
                    .setMessage(getString(R.string.may_need_arcore))
                    .setPositiveButton(android.R.string.ok, (d, i) -> conclude())
                    .setNegativeButton(android.R.string.cancel, (d, i) -> finish())
                    .create().show();
        } else {
            conclude();
        }
    }
    
    private void conclude() {
        if (launchAR) {
            SharedPreferences sharedPreferences = getSharedPreferences(AR_PREFS, MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean("hasRunAR", true);
            editor.apply();
    
            Intent intent = new Intent(this, ARActivity.class);
            finish();
            startActivity(intent);
        }
        else
            finish();
    }
    
    @Override
    public void onSlideChanged(@Nullable Fragment oldFragment, @Nullable Fragment newFragment) {
        super.onSlideChanged(oldFragment, newFragment);
        if (newFragment != null)
            ((OnboardSlide) newFragment).seekToStartIfVideo();
        
        // The AppIntro library supports requesting permissions, but if you turn that on,
        // it disallows swiping between panes. I don't like that, so let's request permissions
        // ourself when the user advances from the first slide.
        if (oldFragment == firstSlide) {
            doPermissionRequest();
            // If the user turns us down, don't keep re-asking if the user goes back
            // to the first slide.
            firstSlide = null;
        }
    }
    
    private void doPermissionRequest() {
        String[] perms = getNeededPerms();
    
        if (perms.length > 0) {
            ActivityCompat.requestPermissions(this,
                    perms,
                    PERM_REQ_CODE);
        }
    }
}