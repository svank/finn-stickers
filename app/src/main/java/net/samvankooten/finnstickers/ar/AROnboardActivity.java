package net.samvankooten.finnstickers.ar;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
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

public class AROnboardActivity extends AppIntro {
    
    public static final String LAUNCH_AR = "launchAR";
    public static final String ONLY_PERMISSIONS = "onlyPermissions";
    public static final String PROMPT_ARCORE_INSTALL = "promptARCoreInstall";
    
    private static final String HAS_RUN_AR = "hasRunAR";
    private static final String SHOULD_PROMPT_PERMISSIONS = "shouldPromptPermissions";
    
    private static final String[] neededPerms = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            };
    private static final String[] neededPermsAPI30 = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
    };
    private static final int PERM_REQ_CODE = 143;
    private boolean promptARCoreInstall;
    private boolean launchAR;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    
        promptARCoreInstall = getIntent().getBooleanExtra(PROMPT_ARCORE_INSTALL, false);
        launchAR = getIntent().getBooleanExtra(LAUNCH_AR, false);
        boolean onlyPerms = getIntent().getBooleanExtra(ONLY_PERMISSIONS, false);
        
        OnboardSlide slide;
        
        if (!onlyPerms) {
            slide = OnboardSlide.newInstance(R.layout.onboard_slide);
            slide.setTitle(R.string.ar_onboard_title_1);
            slide.setText(R.string.ar_onboard_text_1);
            slide.setDisclaimer(R.string.ar_onboard_disclaimer_1);
            slide.setImageDrawable(R.drawable.ar_welcome);
            addSlide(slide);
    
            slide = OnboardSlide.newInstance(R.layout.onboard_slide);
            slide.setTitle(R.string.ar_onboard_title_2);
            slide.setText(R.string.ar_onboard_text_2);
            slide.setVideoUri(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.ar_onboard_find_surfaces));
            slide.setFallbackImageDrawable(R.drawable.ar_onboard_find_surfaces_fallback);
            addSlide(slide);
    
            slide = OnboardSlide.newInstance(R.layout.onboard_slide);
            slide.setTitle(R.string.ar_onboard_title_3);
            slide.setText(R.string.ar_onboard_text_3);
            slide.setVideoUri(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.ar_onboard_how_to_place));
            slide.setFallbackImageDrawable(R.drawable.ar_onboard_how_to_place_fallback);
            addSlide(slide);
    
            slide = OnboardSlide.newInstance(R.layout.onboard_slide);
            slide.setTitle(R.string.ar_onboard_title_4);
            slide.setText(R.string.ar_onboard_text_4);
            slide.setVideoUri(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.ar_onboard_moving));
            slide.setFallbackImageDrawable(R.drawable.ar_onboard_moving_fallback);
            addSlide(slide);
        }
    
        slide = OnboardSlide.newInstance(R.layout.onboard_slide);
        slide.setTitle(R.string.ar_onboard_title_5);
        if (Build.VERSION.SDK_INT >= 30)
            slide.setText(R.string.ar_onboard_text_5_sdk_30);
        else
            slide.setText(R.string.ar_onboard_text_5);
        slide.setImageDrawable(R.drawable.ar_welcome);
        slide.setFallbackImageDrawable(R.drawable.ar_onboard_moving_fallback);
        addSlide(slide);
    }
    
    private static String[] getNeededPerms(Context context) {
        List<String> permsToAskFor = new LinkedList<>();
        String[] perms = Build.VERSION.SDK_INT >= 30 ? neededPermsAPI30 : neededPerms;
        for (String perm : perms) {
            if (ContextCompat.checkSelfPermission(context, perm)
                    != PackageManager.PERMISSION_GRANTED)
                permsToAskFor.add(perm);
        }
        return permsToAskFor.toArray(new String[0]);
    }
    
    @Override
    public void onSkipPressed(Fragment currentFragment) {
        super.onSkipPressed(currentFragment);
        
        onDonePressed(currentFragment);
    }
    
    @Override
    public void onDonePressed(Fragment currentFragment) {
        super.onDonePressed(currentFragment);
        
        if (promptARCoreInstall) {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.may_need_arcore_title))
                    .setMessage(getString(R.string.may_need_arcore))
                    .setPositiveButton(android.R.string.ok, (d, i) -> doPermissionRequest())
                    .setNegativeButton(android.R.string.cancel, (d, i) -> finish())
                    .create().show();
        } else {
            doPermissionRequest();
        }
    }
    
    private void doPermissionRequest() {
        String[] perms = getNeededPerms(this);
        
        if (perms.length > 0) {
            ActivityCompat.requestPermissions(this, perms, PERM_REQ_CODE);
        } else
            onboardingComplete();
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        onboardingComplete();
    }
    
    private void onboardingComplete() {
        if (launchAR) {
            SharedPreferences sharedPreferences = ARActivity.getARSharedPrefs(this);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(HAS_RUN_AR, true);
            editor.putBoolean(SHOULD_PROMPT_PERMISSIONS, false);
            editor.apply();
        
            Intent intent = new Intent(this, ARActivity.class);
            finish();
            startActivity(intent);
        } else
            finish();
    }
    
    @Override
    public void onSlideChanged(@Nullable Fragment oldFragment, @Nullable Fragment newFragment) {
        super.onSlideChanged(oldFragment, newFragment);
        if (newFragment != null)
            ((OnboardSlide) newFragment).seekToStartOfVideo();
    }
    
    public static Intent getARLaunchIntent(Context context, boolean promptARCore) {
        SharedPreferences sharedPreferences = ARActivity.getARSharedPrefs(context);
        if (sharedPreferences.getBoolean(HAS_RUN_AR, false)) {
            if (sharedPreferences.getBoolean(SHOULD_PROMPT_PERMISSIONS, false)) {
                Intent intent = new Intent(context, AROnboardActivity.class);
                intent.putExtra(LAUNCH_AR, true);
                intent.putExtra(ONLY_PERMISSIONS, true);
                intent.putExtra(PROMPT_ARCORE_INSTALL, promptARCore);
                return intent;
            } else
                return new Intent(context, ARActivity.class);
        }
        Intent intent = new Intent(context, AROnboardActivity.class);
        intent.putExtra(LAUNCH_AR, true);
        intent.putExtra(ONLY_PERMISSIONS, false);
        intent.putExtra(PROMPT_ARCORE_INSTALL, promptARCore);
        return intent;
    }
    
    public static void setShouldPromptForNeededPermissions(boolean shouldPrompt, Context context) {
        if (getNeededPerms(context).length == 0)
            shouldPrompt = false;
        SharedPreferences sharedPreferences = ARActivity.getARSharedPrefs(context);
        sharedPreferences.edit().putBoolean(SHOULD_PROMPT_PERMISSIONS, shouldPrompt).apply();
    }
    
    public static boolean arHasRun(Context context) {
        return ARActivity.getARSharedPrefs(context).getBoolean(HAS_RUN_AR, false);
    }
}