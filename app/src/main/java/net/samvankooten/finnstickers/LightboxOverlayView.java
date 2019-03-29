package net.samvankooten.finnstickers;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.stfalcon.imageviewer.StfalconImageViewer;

import net.samvankooten.finnstickers.utils.Util;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import androidx.appcompat.widget.TooltipCompat;

public class LightboxOverlayView extends RelativeLayout {
    private List<Uri> uris;
    private List<File> paths;
    private int pos;
    private StfalconImageViewer<Uri> viewer;
    private OnDeleteCallback deleteCallback;
    private OnEditCallback editCallback;
    private final Lock deleteLock = new ReentrantLock();
    private GetTransitionImageCallback getTransitionImageCallback;
    private FrameLayout shareFrame;
    private FrameLayout deleteFrame;
    private FrameLayout editFrame;
    private FrameLayout openFrame;
    
    private static final String TAG = "LightboxOverlayView";
    
    public LightboxOverlayView(Context context, List<Uri> uris, List<File> paths, int pos, boolean showOpenExternally) {
        super(context);
        this.uris = uris;
        this.paths = paths;
        this.pos = pos;
        
        View view = inflate(getContext(), R.layout.lightbox_overlay, this);
    
        shareFrame = view.findViewById(R.id.share_frame);
        deleteFrame = view.findViewById(R.id.delete_frame);
        editFrame = view.findViewById(R.id.edit_frame);
        openFrame = view.findViewById(R.id.open_externally_frame);
        
        ImageView shareButton = view.findViewById(R.id.share_button);
        TooltipCompat.setTooltipText(shareButton, getResources().getString(R.string.share_button));
        shareButton.setOnClickListener(v -> sendShareIntent());
        showShareIfAppropriate();
    
        ImageView editButton = view.findViewById(R.id.edit_button);
        TooltipCompat.setTooltipText(editButton, getResources().getString(R.string.edit_button));
        editButton.setOnClickListener(v -> edit());
        showEditIfAppropriate();
        
        ImageView deleteButton = view.findViewById(R.id.delete_button);
        TooltipCompat.setTooltipText(deleteButton, getResources().getString(R.string.delete_button));
        deleteButton.setOnClickListener(v -> deleteFile());
        showDeleteIfAppropriate();
        
        ImageView openButton = view.findViewById(R.id.open_externally_button);
        TooltipCompat.setTooltipText(openButton, getResources().getString(R.string.open_externally_button));
        if (showOpenExternally)
            openButton.setOnClickListener(v -> openFile());
        else
            openFrame.setVisibility(View.GONE);
        
        ImageView backButton = view.findViewById(R.id.back_icon);
        backButton.setOnClickListener(v -> viewer.dismiss());
        TooltipCompat.setTooltipText(backButton, getResources().getString(R.string.back_button));
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
        
        File path = paths.get(pos);
        paths.remove(pos);
        uris.remove(pos);
        
        if (paths.size() != 0) {
            viewer.updateImages(uris);
            pos = viewer.currentPosition();
        }
    
        if (deleteCallback != null)
            deleteCallback.onDelete(path);
        
        if (paths.size() == 0)
            viewer.dismiss();
        
        deleteLock.unlock();
    }
    
    private void openFile() {
        Uri uri = uris.get(pos);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setDataAndType(uri, "image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        getContext().startActivity(intent);
    }
    
    private void edit() {
        if (editCallback != null)
            editCallback.onEdit(pos);
    }
    
    public void setPos(int pos) {
        this.pos = pos;
        
        showShareIfAppropriate();
        showEditIfAppropriate();
        showDeleteIfAppropriate();
        
        if (getTransitionImageCallback != null)
            viewer.updateTransitionImage(getTransitionImageCallback.getTransitionImage(pos));
    }
    
    private void showShareIfAppropriate() {
        if (Util.stringIsURL(uris.get(pos).toString()))
            shareFrame.setVisibility(View.GONE);
        else
            shareFrame.setVisibility(View.VISIBLE);
    }
    
    private void showDeleteIfAppropriate() {
        if (deleteCallback != null && paths.get(pos) != null)
            deleteFrame.setVisibility(VISIBLE);
        else
            deleteFrame.setVisibility(GONE);
    }
    
    private void showEditIfAppropriate() {
        if (editCallback != null && !Util.stringIsURL(uris.get(pos).toString()))
            editFrame.setVisibility(VISIBLE);
        else
            editFrame.setVisibility(GONE);
    }
    
    public void setGetTransitionImageCallback(GetTransitionImageCallback callback) {
        getTransitionImageCallback = callback;
    }
    
    public interface GetTransitionImageCallback {
        ImageView getTransitionImage(int pos);
    }
    
    public void setViewer(StfalconImageViewer<Uri> viewer) {
        this.viewer = viewer;
    }
    
    public void setOnDeleteCallback(OnDeleteCallback callback) {
        deleteCallback = callback;
        showDeleteIfAppropriate();
    }
    
    public interface OnDeleteCallback {
        void onDelete(File path);
    }
    
    public void setOnEditCallback(OnEditCallback callback) {
        editCallback = callback;
        showEditIfAppropriate();
    }
    
    public interface OnEditCallback {
        void onEdit(int position);
    }
}
