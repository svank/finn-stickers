package net.samvankooten.finnstickers.ar;


import android.annotation.TargetApi;
import android.app.Activity;
import android.util.Log;

import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.BaseTransformableNode;
import com.google.ar.sceneform.ux.FootprintSelectionVisualizer;

import net.samvankooten.finnstickers.R;

/**
 * A class that allows us to block the appearance of the ring under selected Renderables,
 * for use while video is being recorded.
 */
@TargetApi(24)
class CustomSelectionVisualizer extends FootprintSelectionVisualizer {
    private static final String TAG = "ControllableSelectionVi";
    
    public static final String RING = "ring";
    public static final String NO_RING = "noRing";
    
    private static boolean shouldShowVisualizer = true;
    private NodeSelectedCallback nodeSelectedCallback;
    
    public CustomSelectionVisualizer(Activity activity) {
        ModelRenderable.builder()
                .setSource(activity, R.raw.sceneform_footprint)
                .build()
                .thenAccept(this::setFootprintRenderable)
                .exceptionally(
                        throwable -> {
                            Log.e(TAG, "Unable to load footprint", throwable);
                            return null;
                    });
    }
    
    @Override
    public void applySelectionVisual(BaseTransformableNode node) {
        if (node == null)
            return;
        
        String name = node.getName();
        if (name != null && name.equals(RING) && shouldShowVisualizer)
            super.applySelectionVisual(node);
        else
            super.applySelectionVisual(null);
        
        nodeSelectedCallback.onNodeSelected(node);
    }
    
    @Override
    public void removeSelectionVisual(BaseTransformableNode node) {
        super.removeSelectionVisual(node);
        nodeSelectedCallback.onNodeSelected(null);
    }
    
    public void setNodeSelectedCallback(NodeSelectedCallback nodeSelectedCallback) {
        this.nodeSelectedCallback = nodeSelectedCallback;
    }
    
    public static void setShouldShowVisualizer(boolean shouldShow) {
        shouldShowVisualizer = shouldShow;
    }
    
    public interface NodeSelectedCallback {
        void onNodeSelected(Node node);
    }
}