package net.samvankooten.finnstickers;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.FootprintSelectionVisualizer;
import com.google.ar.sceneform.ux.ScaleController;
import com.google.ar.sceneform.ux.TransformableNode;
import com.google.ar.sceneform.ux.TransformationSystem;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class ARActivity extends AppCompatActivity {
    private static final String TAG = "ARActivity";
    private static final double MIN_OPENGL_VERSION = 3.0;
    private ArFragment arFragment;
    private List<RecyclerView> stickerGalleries;
    private int selectedPack = -1;
    private int selectedSticker = -1;
    private List<Renderable[]> renderables;
    private List<AnchorNode> addedNodes;
    private RecyclerView packGallery;
    private StickerProvider provider;
    
    @Override
    @SuppressWarnings({"FutureReturnValueIgnored"})
    @TargetApi(24)
    // CompletableFuture requires api level 24
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if (!checkIsSupportedDeviceOrFinish()) {
            return;
        }
        
        addedNodes = new LinkedList<>();
        provider = new StickerProvider();
        provider.setRootDir(this);
        
        setContentView(R.layout.activity_ar);
        
        packGallery = findViewById(R.id.gallery_pack_picker);
        // When we change the ImageView background color on selection, an animation is triggered
        // which causes the image itself to blink a bit. So disable the whole animation in lieu of
        // learning how to change it/make my own animation.
        packGallery.getItemAnimator().setChangeDuration(0);
        
        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.sceneform_fragment);
        
        initializeGallery();
        
        ImageView deleteButton = findViewById(R.id.delete_icon);
        deleteButton.setClickable(true);
        deleteButton.setOnClickListener(view -> {
            if (addedNodes == null)
                return;
            for (AnchorNode node : addedNodes) {
                node.setParent(null);
            }
        });
        
        ImageView backButton = findViewById(R.id.back_icon);
        backButton.setClickable(true);
        backButton.setOnClickListener(view -> finish());
        
        // Create a new TransformationSystem that doesn't place rings under selected objects,
        // for use with flush-with-the-surface objects
        final TransformationSystem noRingTransformationSystem = new TransformationSystem(
                getResources().getDisplayMetrics(), new FootprintSelectionVisualizer());
        arFragment.getArSceneView().getScene().addOnPeekTouchListener(noRingTransformationSystem::onTouch);
        
        // Place objects when the user taps the screen
        arFragment.setOnTapArPlaneListener(
            (HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {
                if (selectedSticker < 0 || selectedPack < 0)
                    return;
                
                if (renderables.size() <= selectedPack || renderables.get(selectedPack) == null)
                    return;
                
                Renderable[] pack = renderables.get(selectedPack);
                
                if (pack[selectedSticker] == null)
                    return;
                
                Renderable renderable = pack[selectedSticker];
                
                // Create the Anchor
                AnchorNode anchorNode = new AnchorNode(hitResult.createAnchor());
                anchorNode.setParent(arFragment.getArSceneView().getScene());
    
                TransformableNode tnode;
                
                if (plane != null && plane.getType() == Plane.Type.VERTICAL) {
                    // If the user tapped a vertical surface, make the sticker appear
                    // flush with the wall, like a painting.
                    
                    tnode = new TransformableNode(noRingTransformationSystem);
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
                    tnode = new TransformableNode(arFragment.getTransformationSystem());
                    tnode.setRenderable(renderable);
                    setNodeScale(tnode);
                    tnode.setParent(anchorNode);
                }
                tnode.select();
                addedNodes.add(anchorNode);
        });
    }
    
    private static void setNodeScale(TransformableNode tnode) {
        ScaleController scaleController = tnode.getScaleController();
        scaleController.setMinScale(.75f);
        scaleController.setMaxScale(12f);
        scaleController.setSensitivity(.2f);
        scaleController.setElasticity(.4f);
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
    
    private void initializeGallery() {
        LinearLayout galleryLayout = findViewById(R.id.gallery_layout);
        
        // Load the list of StickerPacks and their icon Uris
        List<StickerPack> packs;
        try {
            packs = StickerPack.getInstalledPacks(getFilesDir());
        } catch (Exception e) {
            Log.e(TAG, "Error loading packs", e);
            return;
        }
        stickerGalleries = new ArrayList<>(packs.size());
        renderables = new ArrayList<>(packs.size());
        List<String> packIcons = new ArrayList<>(packs.size());
        for (StickerPack pack : packs)
            packIcons.add(provider.fileToUri(pack.getIconfile()).toString());
        
        // Set up the upper gallery, showing each installed pack
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
        packGallery.setLayoutManager(layoutManager);
        packGallery.setAdapter(new StickerPackViewerRecyclerAdapter(this, packIcons, 80, 10));
        
        // Have clicking a pack thumbnail activate the pack's gallery
        packGallery.addOnItemTouchListener(new RecyclerItemClickListener(this, (view, position) -> {
            view.playSoundEffect(android.view.SoundEffectConstants.CLICK);
            hideStickerGalleries();
            if (selectedPack == position) {
                // If the user taps the already-selected pack, close it.
                setSelectedSticker(RecyclerView.NO_POSITION);
                setSelectedPack(RecyclerView.NO_POSITION);
                return;
            }
            setSelectedPack(position);
            StickerPackViewerRecyclerAdapter adapter =
                    (StickerPackViewerRecyclerAdapter) stickerGalleries.get(position).getAdapter();
            selectedSticker = adapter.getSelectedPos();
            if (selectedSticker == RecyclerView.NO_POSITION)
                setSelectedSticker(0);
            stickerGalleries.get(position).setVisibility(View.VISIBLE);
        }));
        
        // Set up a gallery for each individual pack
        for (StickerPack pack : packs) {
            RecyclerView stickerGallery = new RecyclerView(this, null, R.attr.ARStickerPicker);
            // When we change the ImageView background color on selection, an animation is triggered
            // which causes the image itself to blink a bit. So disable the whole animation in lieu of
            // learning how to change it/make my own animation.
            stickerGallery.getItemAnimator().setChangeDuration(0);
            layoutManager = new LinearLayoutManager(this);
            layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
            stickerGallery.setLayoutManager(layoutManager);
            
            List<String> uris = pack.getStickerURIs();
            stickerGallery.setAdapter(new StickerPackViewerRecyclerAdapter(this, uris, 80, 10));
            galleryLayout.addView(stickerGallery);
            
            stickerGallery.setVisibility(View.GONE);
            stickerGalleries.add(stickerGallery);
            
            renderables.add(new Renderable[uris.size()]);
            for (int i = 0; i < uris.size(); i++) {
                loadStickerRenderable(renderables.size()-1, i, provider.uriToFile(uris.get(i)).toString());
            }
            
            // Select a sticker for placement when it is clicked
            stickerGallery.addOnItemTouchListener(new RecyclerItemClickListener(this, ((view, position) -> {
                view.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                setSelectedSticker(position);
            })));
        }
    }
    
    private void setSelectedSticker(int position) {
        StickerPackViewerRecyclerAdapter adapter =
                (StickerPackViewerRecyclerAdapter) stickerGalleries.get(selectedPack).getAdapter();
        adapter.setSelectedPos(position);
        selectedSticker = position;
    }
    
    private void setSelectedPack(int position) {
        StickerPackViewerRecyclerAdapter adapter =
                (StickerPackViewerRecyclerAdapter) packGallery.getAdapter();
        adapter.setSelectedPos(position);
        selectedPack = position;
    }
    
    private void hideStickerGalleries() {
        if (stickerGalleries != null) {
            for (RecyclerView gallery : stickerGalleries) {
                gallery.setVisibility(View.GONE);
            }
        }
    }
    
    @TargetApi(24)
    // CompletableFuture requires api level 24
    private void loadStickerRenderable(int pack, int pos, String path) {
        ViewRenderable.builder()
                .setView(this, R.layout.ar_sticker)
                .build()
                .thenAccept(renderable -> {
                    ImageView view = renderable.getView().findViewById(R.id.ar_sticker_image);
                    view.setImageBitmap(
                            BitmapFactory.decodeFile(path));
                    renderables.get(pack)[pos] = renderable;
                    renderable.setShadowCaster(false);
                }).exceptionally(
                throwable -> {
                    Toast toast =
                            Toast.makeText(this, "Unable to load sticker", Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.CENTER, 0, 0);
                    toast.show();
                    Log.e(TAG, "Error loading sticker", throwable);
                    return null;
                });
    }
}
