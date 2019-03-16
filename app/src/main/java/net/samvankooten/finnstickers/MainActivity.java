package net.samvankooten.finnstickers;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.webkit.WebView;

import com.google.android.gms.oss.licenses.OssLicensesMenuActivity;
import com.google.android.material.snackbar.Snackbar;
import com.google.ar.core.ArCoreApk;

import net.samvankooten.finnstickers.ar.ARActivity;
import net.samvankooten.finnstickers.ar.AROnboardActivity;
import net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerActivity;
import net.samvankooten.finnstickers.updating.UpdateUtils;
import net.samvankooten.finnstickers.utils.NotificationUtils;
import net.samvankooten.finnstickers.utils.Util;

import java.util.LinkedList;
import java.util.List;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import static net.samvankooten.finnstickers.ar.ARActivity.AR_PREFS;
import static net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerActivity.ALL_PACKS;
import static net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerActivity.PACK;
import static net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerActivity.PICKER;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    
    private RecyclerView mainView;
    private StickerPackListAdapter adapter;
    private StickerPackListViewModel model;
    private MenuItem arButton;
    private ArCoreApk.Availability arAvailability;
    private SwipeRefreshLayout swipeRefresh;
    
    private boolean picker;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Util.performNeededMigrations(this);
        setContentView(R.layout.activity_main);
        
        if (!Util.checkIfEverOpened(this))
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
        adapter.setOnClickListener(this::onListItemClick);
        adapter.setOnRefreshListener(this::refresh);
        adapter.setShowHeader(false);
        mainView.setAdapter(adapter);
        
        mainView.setLayoutManager(new LinearLayoutManager(this, RecyclerView.VERTICAL, false));
        
        displayLoading();
        
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
    
    private void onListItemClick(StickerPack pack) {
        Intent intent = new Intent(MainActivity.this, StickerPackViewerActivity.class);
    
        intent.putExtra(PACK, pack.getPackname());
        intent.putExtra(PICKER, picker);
    
        View view = mainView.getLayoutManager().findViewByPosition(adapter.getAdapterPositionOfPack(pack));
        StickerPackViewHolder holder = (StickerPackViewHolder) mainView.getChildViewHolder(view);
    
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
            
            case R.id.action_view_licenses:
                OssLicensesMenuActivity.setActivityTitle(getString(R.string.view_licenses_title));
                startActivity(new Intent(this, OssLicensesMenuActivity.class));
                return true;
            
            case R.id.action_view_privacy_policy:
                view = (WebView) LayoutInflater.from(this).inflate(R.layout.dialog_privacy_policy, null);
                view.loadUrl("https://samvankooten.net/finn_stickers/privacy_policy.html");
                new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.view_privacy_policy_title))
                        .setView(view)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                return true;
            
            case R.id.action_start_AR:
                SharedPreferences sharedPreferences = getSharedPreferences(AR_PREFS, MODE_PRIVATE);
                if (sharedPreferences.getBoolean("hasRunAR", false))
                    startActivity(new Intent(this, ARActivity.class));
                else {
                    Intent intent = new Intent(this, AROnboardActivity.class);
                    intent.putExtra(AROnboardActivity.PROMPT_ARCORE_INSTALL, (arAvailability == null
                            || arAvailability == ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD
                            || arAvailability == ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED));
                    intent.putExtra(AROnboardActivity.LAUNCH_AR, true);
                    startActivity(intent);
                }
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
            startActivity(intent, bundle);
    }
    
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 314 && resultCode == RESULT_OK) {
            Uri resultUri = Uri.parse(data.getStringExtra("uri"));
            Intent result = new Intent();
            result.setData(resultUri);
            setResult(Activity.RESULT_OK, result);
            finish();
        }
    }
}
