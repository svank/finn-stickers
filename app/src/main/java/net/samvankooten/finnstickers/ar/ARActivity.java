package net.samvankooten.finnstickers.ar;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.ScaleController;
import com.google.ar.sceneform.ux.TransformableNode;

import net.samvankooten.finnstickers.R;
import net.samvankooten.finnstickers.StickerPack;
import net.samvankooten.finnstickers.StickerProvider;
import net.samvankooten.finnstickers.misc_classes.GlideApp;
import net.samvankooten.finnstickers.utils.StickerPackRepository;
import net.samvankooten.finnstickers.utils.Util;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import static android.hardware.SensorManager.SENSOR_DELAY_NORMAL;

public class ARActivity extends AppCompatActivity {
    private static final String TAG = "ARActivity";
    public static final String AR_PREFS = "ar";
    private static final int EXT_STORAGE_REQ_CODE = 1;
    private static final double MIN_OPENGL_VERSION = 3.0;
    private static final float STICKER_HEIGHT = 0.5f;
    private static final String[] models = new String[]{"finn_low_poly.sfb", "cowwy_low_poly.sfb"};
    private static final int[] model_icons = new int[]{R.drawable.ar_finn, R.drawable.ar_cowwy};
    
    private ArFragment arFragment;
    private List<Renderable[]> renderables;
    private List<AnchorNode> addedNodes;
    private StickerPackGallery gallery;
    private Node selectedNode;
    
    private OrientationEventListener orientationListener;
    private int orientation = 0;
    private int orientationOffset = 0;
    
    private PhotoVideoHelper pvHelper;
    private StickerProvider provider;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Util.performNeededMigrations(this);
        
        if (!checkIsSupportedDeviceOrFinish())
            return;
        
        setContentView(R.layout.activity_ar);
        
        addedNodes = new LinkedList<>();
        provider = new StickerProvider();
        provider.setRootDir(this);
        
        setOrientationListener();
        
        gallery = findViewById(R.id.gallery);
        galleryInit();
        
        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.sceneform_fragment);
        
        pvHelper = new PhotoVideoHelper(this);
        
        CustomSelectionVisualizer sv = new CustomSelectionVisualizer(this);
        sv.setNodeSelectedCallback(this::setSelectedNode);
        arFragment.getTransformationSystem().setSelectionVisualizer(sv);
        
        // Place objects when the user taps the screen
        arFragment.setOnTapArPlaneListener(
            (HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {
                if (getSelectedSticker() < 0 || getSelectedPack() < 0)
                    return;
                
                if (renderables.size() <= getSelectedPack() || renderables.get(getSelectedPack()) == null)
                    return;
                
                Renderable[] pack = renderables.get(getSelectedPack());
                
                if (pack[getSelectedSticker()] == null)
                    return;
                
                Renderable renderable = pack[getSelectedSticker()];
                
                // Create the Anchor
                AnchorNode anchorNode = new AnchorNode(hitResult.createAnchor());
                anchorNode.setParent(arFragment.getArSceneView().getScene());
                
                TransformableNode tnode = new TransformableNode(arFragment.getTransformationSystem());;
                
                if (plane != null && plane.getType() == Plane.Type.VERTICAL
                        && getSelectedPack() != renderables.size()-1) {
                    // If the user tapped a vertical surface, make the sticker appear
                    // flush with the wall, like a painting. But not if we're placing
                    // a 3D model (which are all in the last pack).
                    
                    tnode.setName(CustomSelectionVisualizer.NO_RING);
                    // Scale must be set before the tnode's parent is set, or the scale
                    // setting doesn't take effect (per issue tracker)
                    setNodeScale(tnode);
                    // Parent must be set before the node below is created/set up,
                    // otherwise the rotation doesn't take effect.
                    tnode.setParent(anchorNode);
                    
                    // To avoid funniness with Sceneform's rotation system, we need to
                    // add another Node between the TransformableNode and the Renderable,
                    // and apply the flush-with-surface rotation to this Node.
                    Node node = new Node();
                    node.setParent(tnode);
                    node.setRenderable(renderable);
                    
                    // Apply the flush-to-wall rotation
                    Vector3 anchorUp = anchorNode.getUp();
                    node.setLookDirection(anchorUp);
                    
                    // Shift the node so that when we drag/rotate it, it moves about its
                    // center rather than its bottom (where the anchor would be otherwise)
                    Vector3 offset = new Vector3(0f, -.2f, 0f);
                    Vector3 shift = anchorNode.worldToLocalDirection(offset);
                    node.setLocalPosition(shift);
                }
                else {
                    // For floors, use the default card-upright-in-a-holder style
                    tnode.setName(CustomSelectionVisualizer.RING);
                    tnode.setRenderable(renderable);
                    setNodeScale(tnode);
                    tnode.setParent(anchorNode);
                }
                tnode.select();
                addedNodes.add(anchorNode);
                setSelectedNode(anchorNode);
            });
    }
    
    /*
    We don't need to do anything here, but we are purposefully handling configuration
    changes on our own. Ideally we don't want any response on phone rotation (aside from
    rotating each UI elemnt in-place) because that's just distracting, so we lock to portrait mode.
    But in multi-window mode that locking is ignored. Then we do have to suffer a screen rotation
    animation, but in the manifest we're set to just call this function instead of recreating
    the activity, and that's enough for Sceneform to not lose track of anything.
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }
    
    @Override
    public void onResume() {
        super.onResume();
        if (orientationListener != null)
            orientationListener.enable();
        pvHelper.ensureUIReady();
    }
    
    @Override
    public void onPause() {
        super.onPause();
        if (orientationListener != null)
            orientationListener.disable();
        pvHelper.stopIfRecordingVideo(true);
    }
    
    private int getSelectedPack() {
        return gallery.getSelectedPack();
    }
    
    private int getSelectedSticker() {
        return gallery.getSelectedSticker();
    }
    
    private void galleryInit() {
        // Load the list of StickerPacks and their icon Uris
        List<StickerPack> packs;
        try {
            packs = StickerPackRepository.getInstalledPacks(this);
        } catch (Exception e) {
            Log.e(TAG, "Error loading packs", e);
            return;
        }
        
        renderables = new ArrayList<>(packs.size());
        
        // Load sticker Renderables
        for (StickerPack pack : packs) {
            List<String> uris = pack.getStickerURIs();
            renderables.add(new Renderable[uris.size()]);
            for (int i = 0; i < uris.size(); i++)
                loadStickerRenderable(renderables.size()-1, i, provider.uriToFile(uris.get(i)).toString());
        }
        
        // Load 3D model Renderables
        renderables.add(new Renderable[models.length]);
        for (int i=0; i<models.length; i++) {
            load3DRenderable(packs.size(), i, models[i]);
        }
        
        gallery.init(this, packs, models, model_icons);
        
        gallery.setOnDeleteListener(view -> {
            if (addedNodes == null || addedNodes.size() < 1 || selectedNode == null)
                return;
            selectedNode.setParent(null);
            addedNodes.remove(selectedNode);
            setSelectedNode(null);
        });
        
        gallery.setOnDeleteLongClicklistener(view -> {
            if (addedNodes == null)
                return true;
            for (Node node : addedNodes)
                node.setParent(null);
            addedNodes.clear();
            setSelectedNode(null);
            return true;
        });
        
        gallery.setOnBackListener(view -> finish());
        
        gallery.setOnHelpListener(view -> {
            Intent intent = new Intent(this, AROnboardActivity.class);
            intent.putExtra(AROnboardActivity.PROMPT_ARCORE_INSTALL, false);
            intent.putExtra(AROnboardActivity.LAUNCH_AR, false);
            startActivity(intent);
        });
    }
    
    private void setOrientationListener() {
        switch (((WindowManager) this.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_0:
                orientationOffset = 0; break;
            case Surface.ROTATION_90:
                orientationOffset = 90; break;
            case Surface.ROTATION_180:
                orientationOffset = 180; break;
            case Surface.ROTATION_270:
                orientationOffset = 270; break;
        }
        orientationListener = new OrientationEventListener(this, SENSOR_DELAY_NORMAL) {
            @Override
            public void onOrientationChanged(int i) {
                if (i != ORIENTATION_UNKNOWN) {
                    if (i > 315 || i <= 45)
                        i = 0;
                    else if (i <= 135)
                        i = 90;
                    else if (i <= 225)
                        i = 180;
                    else
                        i = 270;
                    
                    i += orientationOffset;
                    if (orientation != i) {
                        int oldOrientation = orientation;
                        orientation = i;
                        onNewOrientation(oldOrientation, orientation);
                    }
                }
            }
        };
        orientationListener.enable();
    }
    
    @TargetApi(24)
    private void onNewOrientation(int oldOrientation, int newOrientation) {
        /*
        Normally we lock screen orientation and just rotate the UI elements. But in multi-window
        mode we don't have a choice about screen rotation, so we shouldn't rotate UI elements.
         */
        if (isInMultiWindowMode())
            return;
        
        oldOrientation *= -1;
        newOrientation *= -1;
        
        List<View> views = gallery.getViewsToAnimate();
        views.addAll(pvHelper.getViewsToAnimate());
        views.add(gallery.getBackView());
        views.add(gallery.getDeleteView());
        views.add(gallery.getHelpView());
        
        for (View view : views)
            animateRotation(view, oldOrientation, newOrientation);
        
        for (View view : gallery.getViewsToNotAnimate())
            view.setRotation(newOrientation);
    }
    
    private void animateRotation(View view, int oldOrientation, int newOrientation) {
        // Ensure the rotation takes the short way around
        if (oldOrientation == 0 && newOrientation == -270)
            newOrientation = 90;
        if (oldOrientation == -270 && newOrientation == 0)
            oldOrientation = 90;
        
        // Ensure the current rotation is what we think it is. This should only make mod-360
        // changes to the rotation value.
        view.setRotation(oldOrientation);
        
        view.animate().rotation(newOrientation);
    }
    
    private static void setNodeScale(TransformableNode tnode) {
        ScaleController scaleController = tnode.getScaleController();
        scaleController.setMinScale(.3f);
        scaleController.setMaxScale(12f);
        scaleController.setSensitivity(.2f);
        scaleController.setElasticity(.05f);
        // This is the default scale, but adjusting the scaleController range seems to shift
        // the default to somewhere in the middle of the scale range, so put it back to the
        // nice default.
        tnode.setLocalScale(new Vector3(1f, 1f, 1f));
    }
    
    /**
     * Returns false and displays an error message if Sceneform can not run, true if Sceneform can run
     * on this device.
     *
     * Sceneform requires Android N on the device as well as OpenGL 3.0 capabilities.
     *
     * Finishes the activity if Sceneform can not run.
     */
    private boolean checkIsSupportedDeviceOrFinish() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.e(TAG, "Sceneform requires Android N or later");
            Toast.makeText(this, "AR mode requires Android N or later", Toast.LENGTH_LONG).show();
            finish();
            return false;
        }
        String openGlVersionString =
                ((ActivityManager) getSystemService(Context.ACTIVITY_SERVICE))
                        .getDeviceConfigurationInfo()
                        .getGlEsVersion();
        if (Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
            Log.e(TAG, "Sceneform requires OpenGL ES 3.0 later");
            Toast.makeText(this, "AR mode requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
                    .show();
            finish();
            return false;
        }
        return true;
    }
    
    @TargetApi(24)
    private void load3DRenderable(int pack, int pos, String item) {
        ModelRenderable.builder()
                .setSource(this, Uri.parse(item))
                .build()
                .thenAccept(renderable -> renderables.get(pack)[pos] = renderable)
                .exceptionally(
                        throwable -> {
                            Log.e(TAG, "Unable to load model.", throwable);
                            return null;
                        });
    }
    
    @TargetApi(24)
    private void loadStickerRenderable(int pack, int pos, String path) {
        ViewRenderable.builder()
                .setView(this, R.layout.ar_sticker)
                .setSizer(view -> new Vector3(STICKER_HEIGHT, STICKER_HEIGHT, 0))
                .build()
                .thenAccept(renderable -> {
                    ImageView view = renderable.getView().findViewById(R.id.ar_sticker_image);
                    if (path.endsWith(".gif"))
                        GlideApp.with(this).load(path).into(view);
                    else
                        view.setImageBitmap(BitmapFactory.decodeFile(path));
                    renderables.get(pack)[pos] = renderable;
                    renderable.setShadowCaster(false);
                }).exceptionally(
                throwable -> {
                    Toast toast =
                            Toast.makeText(ARActivity.this, "Unable to load sticker", Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.CENTER, 0, 0);
                    toast.show();
                    Log.e(TAG, "Error loading sticker", throwable);
                    return null;
                });
    }
    
    boolean haveExtPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * Begins the process of asking for storage permission by explaining the necessity
     * to the user.
     */
    void requestExtStoragePermission() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.need_ext_storage_perm)
                .setPositiveButton(android.R.string.ok, (d, i) -> finishRequestExtStoragePermission());
        builder.create().show();
    }
    
    /**
     * Once the user has dismissed the rationale dialog, ask for permission.
     */
    private void finishRequestExtStoragePermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                EXT_STORAGE_REQ_CODE);
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] results) {
        if (haveExtPermission())
            pvHelper.populatePastImages();
    }
    
    ArFragment getArFragment() {
        return arFragment;
    }
    
    List<AnchorNode> getNodes() {
        return addedNodes;
    }
    
    void setSelectedNode(Node selectedNode) {
        if (selectedNode == null)
            Log.e(TAG, "null");
        else
            Log.e(TAG, selectedNode.toString());
        this.selectedNode = selectedNode;
        if (selectedNode == null)
            gallery.setDeleteInactive();
        else
            gallery.setDeleteActive();
    }
    
    int getOrientation() {
        return orientation;
    }
}