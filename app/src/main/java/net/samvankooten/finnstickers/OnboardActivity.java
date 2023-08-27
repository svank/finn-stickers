package net.samvankooten.finnstickers;

import android.Manifest;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;
import androidx.fragment.app.Fragment;

import com.github.paolorotolo.appintro.AppIntro;

import net.samvankooten.finnstickers.utils.Util;

public class OnboardActivity extends AppIntro {
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> wrapUp());
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        OnboardSlide slide;
        
        slide = OnboardSlide.newInstance(R.layout.onboard_slide);
        slide.setTitle(R.string.onboard_title_1);
        slide.setText(R.string.onboard_text_1);
        slide.setImageDrawable(R.drawable.onboard_welcome);
        addSlide(slide);
        
        slide = OnboardSlide.newInstance(R.layout.onboard_slide);
        slide.setTitle(R.string.onboard_title_2);
        slide.setText(R.string.onboard_text_2);
        slide.setVideoUri(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.onboard_how_to_send));
        slide.setFallbackImageDrawable(R.drawable.onboard_how_to_send_fallback);
        addSlide(slide);
    
        slide = OnboardSlide.newInstance(R.layout.onboard_slide);
        slide.setTitle(R.string.onboard_title_3);
        slide.setText(R.string.onboard_text_3);
        slide.setImageDrawable(R.drawable.onboard_text_demo);
        addSlide(slide);
    
        slide = OnboardSlide.newInstance(R.layout.onboard_slide);
        slide.setTitle(R.string.onboard_title_4);
        slide.setText(R.string.onboard_text_4);
        slide.setImageDrawable(R.drawable.onboard_how_to_install);
        addSlide(slide);
    }
    
    @Override
    public void onSkipPressed(Fragment currentFragment) {
        super.onSkipPressed(currentFragment);
        doPermsIfNeeded();
    }
    
    @Override
    public void onDonePressed(Fragment currentFragment) {
        super.onDonePressed(currentFragment);
        doPermsIfNeeded();
    }
    
    @Override
    public void onBackPressed() {
        doPermsIfNeeded();
    }
    
    private void doPermsIfNeeded() {
        if (NotificationManagerCompat.from(this).areNotificationsEnabled() || Build.VERSION.SDK_INT < 33)
            wrapUp();
        else
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    private void wrapUp() {
        SharedPreferences.Editor editor = Util.getPrefs(this).edit();
        editor.putBoolean(Util.HAS_RUN, true);
        editor.apply();
        finish();
    }
    
    @Override
    public void onSlideChanged(@Nullable Fragment oldFragment, @Nullable Fragment newFragment) {
        super.onSlideChanged(oldFragment, newFragment);
        if (newFragment != null)
            ((OnboardSlide) newFragment).seekToStartOfVideo();
    }
}
