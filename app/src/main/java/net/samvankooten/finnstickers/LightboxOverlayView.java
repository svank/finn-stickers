package net.samvankooten.finnstickers;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.stfalcon.imageviewer.StfalconImageViewer;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class LightboxOverlayView extends RelativeLayout {
    private List<Uri> uris;
    private List<File> paths;
    private int pos;
    private StfalconImageViewer viewer;
    private OnDeleteCallback callback;
    private Lock deleteLock = new ReentrantLock();
    private GridView gridView;
    
    private static final String TAG = "LightboxOverlayView";
    
    public LightboxOverlayView(Context context, List uris, List<File> paths, int pos) {
        super(context);
        if (uris.size() > 0) {
            if (uris.get(0) instanceof String &&
                    !((String) uris.get(0)).substring(0, 5).equals("http:")) {
                this.uris = new LinkedList<>();
                for (int i = 0; i < uris.size(); i++) {
                    this.uris.add(Uri.parse((String) uris.get(i)));
                }
            } else if (uris.get(0) instanceof Uri)
                this.uris = uris;
        }
        this.paths = paths;
        this.pos = pos;
        init();
    }
    
    private void init() {
        View view = inflate(getContext(), R.layout.lightbox_overlay, this);
        ImageView shareButton = view.findViewById(R.id.share_button);
        if (uris == null)
            shareButton.setVisibility(View.GONE);
        else
            shareButton.setOnClickListener(v -> sendShareIntent());
        ImageView deleteButton = view.findViewById(R.id.delete_button);
        if (paths == null)
            deleteButton.setVisibility(View.GONE);
        else
            deleteButton.setOnClickListener(v -> deleteFile());
    }
    
    private void sendShareIntent() {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.setType("image/jpg");
        sendIntent.putExtra(Intent.EXTRA_STREAM, uris.get(pos));
        getContext().startActivity(Intent.createChooser(sendIntent, "Share image via..."));
    }
    
    private void deleteFile() {
        if (!deleteLock.tryLock())
            return;
        
        if (paths.size() == 0) {
            deleteLock.unlock();
            return;
        }
        
        try {
            Util.delete(paths.get(pos));
        } catch (IOException e) {
            Log.e(TAG, "Error deleting file: "+e);
            deleteLock.unlock();
            return;
        }
        paths.remove(pos);
        uris.remove(pos);
        
        if (paths.size() == 0) {
            if (callback != null)
                callback.onDelete();
            viewer.dismiss();
            deleteLock.unlock();
            return;
        }
        
        viewer.updateImages(uris);
        pos = viewer.currentPosition();
    
        if (callback != null)
            callback.onDelete();
        
        deleteLock.unlock();
    }
    
    void setPos(int pos) {
        this.pos = pos;
        if (gridView != null)
            viewer.updateTransitionImage((ImageView) gridView.getChildAt(pos));
    }
    
    void setViewer(StfalconImageViewer viewer) {
        this.viewer = viewer;
    }
    
    void setOnDeleteCallback(OnDeleteCallback callback) {
        this.callback = callback;
    }
    
    void setGridView(GridView gridView) { this.gridView = gridView; }
    
    interface OnDeleteCallback {
        void onDelete();
    }
}
