package net.samvankooten.finnstickers;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.ContextThemeWrapper;

public class LightboxOverlayConfirmDeleteFragment extends BottomSheetDialogFragment {
    
    private OnCloseListener closeListener;
    private View.OnClickListener confirmListener;
    
    public static LightboxOverlayConfirmDeleteFragment newInstance(
            OnCloseListener closeListener,
            View.OnClickListener confirmListener) {
        final LightboxOverlayConfirmDeleteFragment fragment = new LightboxOverlayConfirmDeleteFragment();
        fragment.setArgs(closeListener, confirmListener);
        return fragment;
    }
    
    private void setArgs(OnCloseListener closeListener, View.OnClickListener confirmListener) {
        this.closeListener = closeListener;
        this.confirmListener = confirmListener;
    }
    
    @Override
    public int getTheme() {
        return R.style.DeleteConfirmationTheme;
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context themeWrapper = new ContextThemeWrapper(getActivity(), getTheme());
        LayoutInflater localInflater = inflater.cloneInContext(themeWrapper);
        
        View view = localInflater.inflate(R.layout.dialog_confirm_deletion, container,
                false);
        
        view.findViewById(R.id.confirm_delete_button).setOnClickListener(
                (v) -> {
                    if (confirmListener != null)
                        confirmListener.onClick(v);
                    this.dismiss();
                });
        
        return view;
    }
    
    @Override
    public void onPause() {
        super.onPause();
        dismiss();
    }
    
    @Override
    public void onCancel(@NonNull DialogInterface di) {
        super.onCancel(di);
        if (closeListener != null)
            closeListener.onClose();
    }
    
    @Override
    public void onDismiss(@NonNull DialogInterface di) {
        super.onDismiss(di);
        if (closeListener != null)
            closeListener.onClose();
    }
    
    public interface OnCloseListener {
        void onClose();
    }
}
