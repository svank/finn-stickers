package net.samvankooten.finnstickers;

import static net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerActivity.ALL_PACKS;
import static net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerActivity.FADE_PACK_BACK_IN;
import static net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerActivity.PACK;
import static net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerActivity.PICKER;
import static net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerActivity.PICKER_ALLOW_MULTIPLE;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.work.WorkManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.ar.core.ArCoreApk;

import net.samvankooten.finnstickers.ar.AROnboardActivity;
import net.samvankooten.finnstickers.misc_classes.RestoreWorker;
import net.samvankooten.finnstickers.settings.SettingsActivity;
import net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerActivity;
import net.samvankooten.finnstickers.utils.StickerPackRepository;
import net.samvankooten.finnstickers.utils.Util;
import net.samvankooten.finnstickers.utils.ViewUtils;

import java.util.LinkedList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "MainActivity";
    
    private RecyclerView mainView;
    private StickerPackListAdapter adapter;
    private StickerPackListViewModel model;
    private MenuItem arButton;
    private LinearProgressIndicator progressBar;
    private ArCoreApk.Availability arAvailability;
    private View clickedView;
    private Snackbar restoreInProgressSnackBar;
    
    private boolean picker = false;
    private boolean pickerAllowMultiple = false;
    
    private SwipeRefreshManager swipeRefreshManager;
    
    final ActivityResultLauncher<Intent> launchViewer = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (picker && result.getResultCode() == RESULT_OK) {
                    setResult(Activity.RESULT_OK, result.getData());
                    finish();
                }
            }
    );

    private final ActivityResultLauncher<String> requestNotifPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> Util.markShouldAsKNotifications(this, false));
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        setContentView(R.layout.activity_main);
        
        if (!Util.appHasBeenOpenedBefore(this))
            startOnboarding();
        
        if (getIntent().getAction() != null) {
            picker = getIntent().getAction().equals(Intent.ACTION_GET_CONTENT);
            if (picker)
                pickerAllowMultiple = getIntent().getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
        }
        
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (picker && getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeAsUpIndicator(R.drawable.icon_back);
        }
        final ViewUtils.LayoutData toolbarPadding = ViewUtils.recordLayoutData(toolbar);
        toolbar.setOnApplyWindowInsetsListener((v, windowInsets) -> {
            // The toolbar needs top padding to handle the status bar properly
            ViewUtils.updatePaddingTop(toolbar, windowInsets.getSystemWindowInsetTop(),
                    toolbarPadding);
            return windowInsets;
        });
        
        View appBarLayout = findViewById(R.id.app_bar_layout);
        final ViewUtils.LayoutData appBarLayoutPadding = ViewUtils.recordLayoutData(appBarLayout);
        appBarLayout.setOnApplyWindowInsetsListener((v, windowInsets) -> {
            // The appBarLayout needs side margin so it doesn't draw under the
            // transparent nav bar in landscape mode
            ViewUtils.updateMarginSides(appBarLayout,
                    windowInsets.getSystemWindowInsetLeft(),
                    windowInsets.getSystemWindowInsetRight(),
                    appBarLayoutPadding);
            return windowInsets;
        });

        model = new ViewModelProvider(this).get(StickerPackListViewModel.class);

        SwipeRefreshLayout swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setOnRefreshListener(this::refresh);
        swipeRefresh.setColorSchemeResources(R.color.colorAccent);
        swipeRefreshManager = new SwipeRefreshManager(swipeRefresh);
        mainView = findViewById(R.id.pack_list_view);
        mainView.setHasFixedSize(true);
        
        progressBar = findViewById(R.id.upperProgressBar);
        progressBar.setVisibility(View.GONE);
        
        adapter = new StickerPackListAdapter(new LinkedList<>(), this, model);
        adapter.setOnClickListener(listItemClickListener);
        adapter.setOnRefreshListener(this::refresh);
        adapter.setShowHeader(false);
        adapter.setShowCarousel(StickerPackRepository.getInstalledPacks(this).size() > 0);
        StickerPackRepository.getLiveInstalledPacks(this).observe(this,
                packList -> adapter.setShowCarousel(packList.size() > 0));
        mainView.setAdapter(adapter);
        
        mainView.setLayoutManager(new LinearLayoutManager(this, RecyclerView.VERTICAL, false));
        
        final ViewUtils.LayoutData mainViewPadding = ViewUtils.recordLayoutData(mainView);
        mainView.setOnApplyWindowInsetsListener((v, windowInsets) -> {
            // mainView needs bottom padding for the transparent nav bar
            ViewUtils.updatePaddingBottom(mainView,
                    windowInsets.getSystemWindowInsetBottom(),
                    mainViewPadding);
            // mainView needs side margin for the transparent nav bar in landscape
            ViewUtils.updatePaddingSides(mainView,
                    windowInsets.getSystemWindowInsetLeft(),
                    windowInsets.getSystemWindowInsetRight(),
                    mainViewPadding);
            return windowInsets;
        });
        
        displayLoading();
        
        if (Util.restoreIsPending(this)) {
            List<StickerPack> packs = StickerPackRepository.getInstalledPacks(this);
            if (packs != null && packs.size() > 0) {
                RestoreWorker.start(this);
                restoreInProgressSnackBar = Snackbar.make(mainView, getString(R.string.restoring_while_you_wait),
                        Snackbar.LENGTH_INDEFINITE);
                restoreInProgressSnackBar.show();
                if (!Util.connectedToInternet(this))
                    Toast.makeText(this, R.string.internet_required, Toast.LENGTH_LONG).show();
                swipeRefresh.setRefreshing(true);
                Util.getPrefs(this).registerOnSharedPreferenceChangeListener(this);
                progressBar.setVisibility(View.VISIBLE);
                WorkManager.getInstance(getApplicationContext())
                        .getWorkInfosForUniqueWorkLiveData(RestoreWorker.WORK_ID)
                        .observe(this, (workInfos) -> {
                            if (workInfos != null && workInfos.size() > 0) {
                                var workInfo = workInfos.get(0);
                                int progress = (int) Math.ceil(
                                        100 * workInfo.getProgress().getFloat(RestoreWorker.PROGRESS, 0));
                                progressBar.setProgress(progress);
                            }
                        });
            } else {
                Util.markPendingRestore(this, false);
                loadPacks();
            }
        } else {
            if (savedInstanceState == null)
                swipeRefreshManager.setRefreshInhibited(1000);
            loadPacks();
        }
        
        // This handles the case of running the return shared-element transition if the phone is
        // rotated while in StickerPackViewer---wait until our list is repopulated before letting
        // the animation run.
        if (savedInstanceState != null) {
            postponeEnterTransition();
            mainView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    if (adapter.getItemCount() == 0)
                        return;
                    startPostponedEnterTransition();
                    mainView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
            });
        } else {
            mainView.setAlpha(0f);
            mainView.animate()
                    .alpha(1f)
                    .setDuration(getResources().getInteger(R.integer.main_activity_animate_in_duration));
        }

        if (Util.shouldAskNotifications(this) && Build.VERSION.SDK_INT >= 33)
            requestNotifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }
    
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        if (!Util.restoreIsPending(MainActivity.this)) {
            if (restoreInProgressSnackBar != null) {
                restoreInProgressSnackBar.dismiss();
                restoreInProgressSnackBar = null;
                progressBar.setVisibility(View.GONE);
            }
            
            loadPacks();
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        }
    }
    
    private void loadPacks() {
        if (!model.isInitialized()) {
            // Give the ViewModel information about the environment if it hasn't yet been set
            // (i.e. we're starting the application fresh, rather than rotating the screen)
            model.setInfo(getFilesDir());
            model.loadInstalledPacks();
            model.downloadData();
        }
    
        // Respond when the list of packs becomes available
        model.getPacks().observe(this, this::showPacks);
        model.getDownloadException().observe(this, this::showDownloadException);
        model.getDownloadSuccess().observe(this, this::showDownloadSuccess);
        model.getDownloadRunning().observe(this, this::showProgress);
        
    }
    
    private void startOnboarding() {
        Intent intent = new Intent(this, OnboardActivity.class);
        startActivity(intent);
    }
    
    private void refresh() {
        displayLoading();
        model.downloadData();
    }
    
    private void displayLoading() {
        adapter.setShowFooter(false);
    }
    
    private void showDownloadSuccess(Boolean downloadSuccess) {
        adapter.setShowFooter(!downloadSuccess);
    }
    
    private void showDownloadException(Exception e) {
        if (e != null) {
            String message;
            if (!Util.connectedToInternet(this)) {
                Log.w(TAG, "Not connected to internet");
                message = getString(R.string.no_network);
            } else {
                Log.e(TAG, "Download exception", e);
                message = getString(R.string.network_error);
            }
            Snackbar.make(mainView, message, Snackbar.LENGTH_LONG).show();
            
            model.clearException();
        }
    }
    
    private void showProgress(Boolean loadInProgress) {
        swipeRefreshManager.setRefreshing(loadInProgress);
    }
    
    private void showPacks(List<StickerPack> packs){
        if (packs.size() == 0)
            return;
        
        adapter.setPacks(packs);
        adapter.setShowHeader(true);
        adapter.notifyDataSetChanged();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu_items, menu);
        
        // Disable this button until we know AR is supported on this device.
        arButton = menu.findItem(R.id.action_start_AR);
        arButton.setVisible(false);
        arButton.setEnabled(false);
        maybeEnableArButton();
        return true;
    }
    
    @SuppressLint("InflateParams")
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (restoreInProgressSnackBar != null)
            return true;
        
        int id = item.getItemId();
        if (id == R.id.action_onboard) {
            startOnboarding();
            return true;

        } else if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;

        } else if (id == R.id.action_start_AR) {
            startActivity(AROnboardActivity.getARLaunchIntent(this,
                    arAvailability == null
                    || arAvailability == ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD
                    || arAvailability == ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED));
            return true;

        } else if (id == R.id.search) {
            Intent intent = new Intent(this, StickerPackViewerActivity.class);
            intent.putExtra(ALL_PACKS, true);
            startPackViewer(intent, null);
            return true;

        } else if (id == R.id.action_send_feedback) {
            Intent Email = new Intent(Intent.ACTION_SEND);
            Email.setType("message/rfc822");
            Email.putExtra(Intent.EXTRA_EMAIL, new String[] { "appfeedback@samvankooten.net" });
            Email.putExtra(Intent.EXTRA_SUBJECT, "Finn Stickers");
            startActivity(Intent.createChooser(Email, getString(R.string.send_feedback_share_label)));
            return true;

        } else if (id == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    /**
     * From Google's AR docs, check if AR is supported an enable the AR button if so.
     */
    private void maybeEnableArButton() {
        if (picker)
            return;
        
        arAvailability = ArCoreApk.getInstance().checkAvailability(this);
        if (arAvailability.isTransient()) {
            // Re-query at 5Hz while compatibility is checked in the background.
            new Handler().postDelayed(this::maybeEnableArButton, 200);
        }
        if (arAvailability.isSupported()) {
            arButton.setVisible(true);
            arButton.setEnabled(true);
        }
    }
    
    private void startPackViewer(Intent intent, Bundle bundle) {
        intent.putExtra(PICKER, picker);
        intent.putExtra(PICKER_ALLOW_MULTIPLE, pickerAllowMultiple);
        
        launchViewer.launch(intent);
    }
    
    @Override
    public void onActivityReenter(int resultCode, Intent data) {
        if (clickedView != null
                && data.getBooleanExtra(FADE_PACK_BACK_IN, false)) {
            clickedView.setAlpha(0f);
            clickedView.animate().alpha(1f)
                    .setStartDelay(getResources().getInteger(R.integer.pack_view_animate_out_duration)*3/4)
                    .setDuration(getResources().getInteger(R.integer.pack_view_animate_out_duration)).start();
        }
        clickedView = null;
    }
    
    private final StickerPackListAdapter.OnClickListener listItemClickListener =
            new StickerPackListAdapter.OnClickListener() {
        private long lastClickTime;
        
        @Override
        public void onClick(StickerPack pack) {
            synchronized (this) {
                long currentTime = SystemClock.elapsedRealtime();
                if (currentTime - lastClickTime < 500)
                    return;
            
                lastClickTime = currentTime;
            }
            
            Intent intent = new Intent(MainActivity.this, StickerPackViewerActivity.class);
            
            intent.putExtra(PACK, pack.getPackname());
            intent.putExtra(PICKER, picker);
            
            if (mainView.getLayoutManager() == null)
                return;
            View view = mainView.getLayoutManager().findViewByPosition(adapter.getAdapterPositionOfPack(pack));
            if (view == null)
                return;
            
            StickerPackViewHolder holder = (StickerPackViewHolder) mainView.getChildViewHolder(view);
            clickedView = holder.getTransitionView();
            
            ActivityOptions options;
            options = ActivityOptions.makeSceneTransitionAnimation(MainActivity.this,
                    holder.getTransitionView(), holder.getTransitionName());
            startPackViewer(intent, options.toBundle());
        }
    };
    
    private static class SwipeRefreshManager {
        // We don't want the spinner to flash really quickly when we're first opening the app
        // and the Internet connection isn't very slow, so we run everything through this class,
        // which inhibits display of the spinner for a time
        private final SwipeRefreshLayout swipeRefreshLayout;
        private boolean isRefreshing = false;
        private boolean refreshInhibited = false;
        
        SwipeRefreshManager(SwipeRefreshLayout swipeRefreshLayout) {
            this.swipeRefreshLayout = swipeRefreshLayout;
        }
        
        void setRefreshing(boolean isRefreshing) {
            this.isRefreshing = isRefreshing;
            // The swipeRefreshLayout status should be updated if either no
            // inhibiton is in place, or if the refresh status is being disabled.
            if (!refreshInhibited || !isRefreshing)
                swipeRefreshLayout.setRefreshing(isRefreshing);
        }
        
        void setRefreshInhibited(int time) {
            // If our Runnable is in the queue, remove it
            swipeRefreshLayout.removeCallbacks(this::onInhibitionEnded);
            
            if (time == 0) {
                // If inhibition has been ended, act accordingly
                onInhibitionEnded();
            } else {
                // Schedule an end to inhibition at the appropriate time
                refreshInhibited = true;
                swipeRefreshLayout.postDelayed(this::onInhibitionEnded, time);
            }
        }
        
        private void onInhibitionEnded() {
            // At the end of inhibition, mark it so and set the
            // swipeRefreshLayout status accordingly
            refreshInhibited = false;
            swipeRefreshLayout.setRefreshing(isRefreshing);
        }
    }
}
