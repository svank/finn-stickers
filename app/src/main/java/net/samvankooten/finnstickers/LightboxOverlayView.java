package net.samvankooten.finnstickers;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.stfalcon.frescoimageviewer.ImageViewer;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

class LightboxOverlayView extends RelativeLayout {
    private List<Uri> uris;
    private List<File> paths;
    private int pos;
    private ImageViewer viewer;
    private Context context;
    private OnDeleteCallback callback;
    
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
        this.context = context;
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
        try {
            Util.delete(paths.get(pos));
        } catch (IOException e) {
            Log.e(TAG, "Error deleting file: "+e);
            return;
        }
        paths.remove(pos);
        uris.remove(pos);
    
        if (callback != null)
            callback.onDelete();
        
        if (paths.size() == 0) {
            viewer.onDismiss();
            return;
        }
        
        if (pos == paths.size())
            pos -= 1;
        
        /* HACK ALERT!
        ImageViewer doesn't let us remove items from its Uri list. So what we have to do is
        create a fresh new ImageViewer with the updated Uri list and then remove the old one.
        It's not beautiful, but it works.
         */
        
        // Remove this overlay from the old viewer so we can put it on the new viewer.
        ((ViewGroup) this.getParent()).removeView(this);
        
        ImageViewer viewer = new ImageViewer.Builder(context, uris)
                .setStartPosition(pos)
                .setOverlayView(this)
                .setImageChangeListener(this::setPos)
                .show();
        
        // Wait a fraction of a second for the new viewer to set up before we remove the old
        // viewer. Otherwise we get flickering.
        final ImageViewer oldViewer = this.viewer;
        final Handler handler = new Handler();
        handler.postDelayed(oldViewer::onDismiss, 50);
        this.viewer = viewer;
    }
    
    void setPos(int pos) {
        this.pos = pos;
    }
    
    void setViewer(ImageViewer viewer) {
        this.viewer = viewer;
    }
    
    void setOnDeleteCallback(OnDeleteCallback callback) {
        this.callback = callback;
    }
    
    interface OnDeleteCallback {
        void onDelete();
    }
}
