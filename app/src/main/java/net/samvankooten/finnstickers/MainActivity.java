package net.samvankooten.finnstickers;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.webkit.WebView;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;
import com.google.ar.core.ArCoreApk;

import net.samvankooten.finnstickers.ar.AROnboardActivity;
import net.samvankooten.finnstickers.misc_classes.RestoreJobIntentService;
import net.samvankooten.finnstickers.settings.SettingsActivity;
import net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerActivity;
import net.samvankooten.finnstickers.updating.UpdateUtils;
import net.samvankooten.finnstickers.utils.NotificationUtils;
import net.samvankooten.finnstickers.utils.StickerPackRepository;
import net.samvankooten.finnstickers.utils.Util;

import java.util.LinkedList;
import java.util.List;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import static net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerActivity.ALL_PACKS;
import static net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerActivity.FADE_PACK_BACK_IN;
import static net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerActivity.PACK;
import static net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerActivity.PICKER;
import static net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerActivity.SELECTED_STICKER;

public class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "MainActivity";
    
    private RecyclerView mainView;
    private StickerPackListAdapter adapter;
    private StickerPackListViewModel model;
    private MenuItem arButton;
    private ArCoreApk.Availability arAvailability;
    private SwipeRefreshLayout swipeRefresh;
    private View clickedView;
    private Snackbar bar;
    
    private boolean picker;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Util.performNeededMigrations(this);
        setContentView(R.layout.activity_main);
        
        if (!Util.appHasBeenOpenedBefore(this))
            startOnboarding();
        
        UpdateUtils.scheduleUpdates(this);
        NotificationUtils.createChannels(this);
        
        if (getIntent().getAction() != null)
            picker = getIntent().getAction().equals(Intent.ACTION_GET_CONTENT);
        else
            picker = false;
        
        setSupportActionBar(findViewById(R.id.toolbar));
        if (picker)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        
        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setOnRefreshListener(this::refresh);
        swipeRefresh.setColorSchemeResources(R.color.colorAccent);
        mainView = findViewById(R.id.pack_list_view);
        mainView.setHasFixedSize(true);
        
        adapter = new StickerPackListAdapter(new LinkedList<>(), this);
        adapter.setOnClickListener(listItemClickListener);
        adapter.setOnRefreshListener(this::refresh);
        adapter.setShowHeader(false);
        mainView.setAdapter(adapter);
        
        mainView.setLayoutManager(new LinearLayoutManager(this, RecyclerView.VERTICAL, false));
        
        displayLoading();
        
        if (Util.restoreIsPending(this)) {
            List<StickerPack> packs = StickerPackRepository.getInstalledPacks(this);
            if (packs != null && packs.size() > 0) {
                RestoreJobIntentService.start(this);
                bar = Snackbar.make(mainView, getString(R.string.restoring_while_you_wait),
                        Snackbar.LENGTH_INDEFINITE);
                bar.show();
                if (!Util.connectedToInternet(this))
                    Toast.makeText(this, R.string.internet_required, Toast.LENGTH_LONG).show();
                swipeRefresh.setRefreshing(true);
                Util.getPrefs(this).registerOnSharedPreferenceChangeListener(this);
            } else {
                Util.markPendingRestore(this, false);
                loadPacks();
            }
        } else
            loadPacks();
        
        // This handles the case of running the return shared-element transition if the phone is
        // rotated while in StickerPackViewer---wait until our list is repopulated before letting
        // the animation run.
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
    }
    
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        if (!Util.restoreIsPending(MainActivity.this)) {
            if (bar != null) {
                bar.dismiss();
                bar = null;
            }
            
            loadPacks();
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        }
    }
    
    private void loadPacks() {
        model = ViewModelProviders.of(this).get(StickerPackListViewModel.class);
        if (!model.isInitialized()) {
            // Give the ViewModel information about the environment if it hasn't yet been set
            // (i.e. we're starting the application fresh, rather than rotating the screen)
            model.setInfo(getFilesDir());
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
        if (downloadSuccess) {
            adapter.setShowFooter(false);
        } else
            adapter.setShowFooter(true);
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
    
    private void showProgress(Boolean inProgress) {
        swipeRefresh.setRefreshing(inProgress);
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
    public boolean onOptionsItemSelected(MenuItem item) {
        WebView view;
        switch (item.getItemId()) {
            case R.id.action_onboard:
                startOnboarding();
                return true;
            
            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            
            case R.id.action_start_AR:
                startActivity(AROnboardActivity.getARLaunchIntent(this,
                        arAvailability == null
                        || arAvailability == ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD
                        || arAvailability == ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED));
                return true;
            
            case R.id.search:
                Intent intent = new Intent(this, StickerPackViewerActivity.class);
                intent.putExtra(ALL_PACKS, true);
                startPackViewer(intent, null);
                return true;
                
            case R.id.action_send_feedback:
                Intent Email = new Intent(Intent.ACTION_SEND);
                Email.setType("text/email");
                Email.putExtra(Intent.EXTRA_EMAIL, new String[] { "appfeedback@samvankooten.net" });
                Email.putExtra(Intent.EXTRA_SUBJECT, "Finn Stickers");
                startActivity(Intent.createChooser(Email, getResources().getString(R.string.send_feedback_share_label)));
                return true;
                
            case android.R.id.home:
                onBackPressed();
                return true;
                
            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                // This is how Google's example does it, but I'm not really sure how this helps.
                return super.onOptionsItemSelected(item);
        }
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
        
        if (picker)
            startActivityForResult(intent, 314, bundle);
        else
            startActivityForResult(intent, 628, bundle);
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 314 && resultCode == RESULT_OK && data.hasExtra(SELECTED_STICKER)) {
            Uri resultUri = Uri.parse(data.getStringExtra(StickerPackViewerActivity.SELECTED_STICKER));
            Intent result = new Intent();
            result.setData(resultUri);
            setResult(Activity.RESULT_OK, result);
            finish();
        }
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
    
            View view = mainView.getLayoutManager().findViewByPosition(adapter.getAdapterPositionOfPack(pack));
            if (view == null)
                return;
            
            StickerPackViewHolder holder = (StickerPackViewHolder) mainView.getChildViewHolder(view);
            clickedView = holder.getTransitionView();
    
            // Views involved in shared element transitions live in a layer above everything else
            // for the duration of the transition. The views below the ToolBar exist in a space
            // slightly larger than the available screen size so that, when the ToolBar disappears
            // upon scrolling, content is already rendered to fill that new space. This means the
            // shared element in StickerPackViewer extends below the top of the nav bar. Since it's
            // in a top-most layer while transitioning, it covers up the nav bar, and then snaps
            // below it once the transition ends. To prevent that, we add the nav bar to the
            // transition so it also moves to the upper layer and appropriately covers the bottom
            // of the RecyclerView.
            ActivityOptions options;
            View navBg = findViewById(android.R.id.navigationBarBackground);
            if (navBg != null) {
                options = ActivityOptions.makeSceneTransitionAnimation(MainActivity.this,
                        Pair.create(holder.getTransitionView(), holder.getTransitionName()),
                        Pair.create(navBg, "navbar"));
            } else {
                options = ActivityOptions.makeSceneTransitionAnimation(MainActivity.this,
                        holder.getTransitionView(), holder.getTransitionName());
            }
            startPackViewer(intent, options.toBundle());
        }
    };
}
