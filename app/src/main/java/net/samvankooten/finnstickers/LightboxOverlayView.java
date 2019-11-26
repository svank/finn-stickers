package net.samvankooten.finnstickers;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.stfalcon.imageviewer.StfalconImageViewer;

import net.samvankooten.finnstickers.utils.Util;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.TooltipCompat;

public class LightboxOverlayView extends RelativeLayout {
    private List<Uri> uris;
    private int pos;
    private StfalconImageViewer<Uri> viewer;
    private OnDeleteCallback deleteCallback;
    private List<Boolean> areDeletable;
    private OnEditCallback editCallback;
    private List<Boolean> areEditable;
    private final ReentrantLock deleteLock = new ReentrantLock();
    private GetTransitionImageCallback getTransitionImageCallback;
    private FrameLayout shareFrame;
    private FrameLayout deleteFrame;
    private FrameLayout editFrame;
    private FrameLayout openFrame;
    
    private static final String TAG = "LightboxOverlayView";
    
    public LightboxOverlayView(Context context, List<Uri> uris, int pos, boolean showOpenExternally) {
        super(context);
        updateUris(uris);
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
        backButton.setOnClickListener(v -> viewer.close());
        TooltipCompat.setTooltipText(backButton, getResources().getString(R.string.back_button));
    }
    
    private void sendShareIntent() {
        Uri uri = uris.get(pos);
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.setType(getContext().getContentResolver().getType(uri));
        sendIntent.putExtra(Intent.EXTRA_STREAM, uri);
        sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        sendIntent.setClipData(ClipData.newRawUri(null, uri));
        getContext().startActivity(
                Intent.createChooser(sendIntent,getResources().getString(R.string.share_text)));
    }
    
    private void deleteFile() {
        if (uris.size() == 0 || !deleteLock.tryLock())
            return;
    
        LightboxOverlayConfirmDeleteFragment confirmDialog =
                LightboxOverlayConfirmDeleteFragment.newInstance(
                        () -> {if (deleteLock.isLocked()) deleteLock.unlock();},
                        (v) -> reallyDeleteFile()
                );
        
        confirmDialog.show(((AppCompatActivity) getContext()).getSupportFragmentManager(),
                "lightbox_confirm_delete");
    }
    
    private void reallyDeleteFile(){
        boolean success = deleteCallback.onDelete(pos);
        
        if (success) {
            uris.remove(pos);
            if (areDeletable != null && areDeletable.size() > pos)
                areDeletable.remove(pos);
            if (areEditable != null && areEditable.size() > pos)
                areEditable.remove(pos);
            
            if (uris.size() == 0)
                viewer.close();
            else {
                viewer.updateImages(uris);
                setPos(viewer.currentPosition());
            }
        }
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
            viewer.updateTransitionImage(getTransitionImageCallback.getTransitionImage(uris.get(pos)));
    }
    
    private void showShareIfAppropriate() {
        if (Util.stringIsURL(uris.get(pos).toString()))
            shareFrame.setVisibility(View.GONE);
        else
            shareFrame.setVisibility(View.VISIBLE);
    }
    
    private void showDeleteIfAppropriate() {
        if (deleteCallback != null
            // Use areDeletable hints only if given
            && (areDeletable == null
                || (areDeletable.size() > pos && areDeletable.get(pos))))
            deleteFrame.setVisibility(VISIBLE);
        else
            deleteFrame.setVisibility(GONE);
    }
    
    private void showEditIfAppropriate() {
        if (editCallback != null
            && !Util.stringIsURL(uris.get(pos).toString())
            && (areEditable == null
                || (areEditable.size() > pos && areEditable.get(pos))))
            editFrame.setVisibility(VISIBLE);
        else
            editFrame.setVisibility(GONE);
    }
    
    public void setGetTransitionImageCallback(GetTransitionImageCallback callback) {
        getTransitionImageCallback = callback;
    }
    
    public interface GetTransitionImageCallback {
        ImageView getTransitionImage(Uri uri);
    }
    
    public void setViewer(StfalconImageViewer<Uri> viewer) {
        this.viewer = viewer;
    }
    
    public void setOnDeleteCallback(OnDeleteCallback callback) {
        deleteCallback = callback;
        showDeleteIfAppropriate();
    }
    
    public interface OnDeleteCallback {
        boolean onDelete(int pos);
    }
    
    public void setAreDeletable(List<Boolean> deletable) {
        areDeletable = deletable;
    }
    
    public void setOnEditCallback(OnEditCallback callback) {
        editCallback = callback;
        showEditIfAppropriate();
    }
    
    public interface OnEditCallback {
        void onEdit(int position);
    }
    
    public void setAreEditable(List<Boolean> editable) {
        areEditable = editable;
    }
    
    public void updateUris(List<Uri> uris) {
        this.uris = uris;
    }
}
