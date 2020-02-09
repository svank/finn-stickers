package net.samvankooten.finnstickers;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import net.samvankooten.finnstickers.utils.ViewUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.ContextThemeWrapper;

public class LightboxOverlayConfirmDeleteFragment extends BottomSheetDialogFragment {
    
    private OnCloseListener closeListener;
    private View.OnClickListener confirmListener;
    private int theme = R.style.DayNightDeleteConfirmationTheme;
    
    public static LightboxOverlayConfirmDeleteFragment newInstance(
            OnCloseListener closeListener,
            View.OnClickListener confirmListener, boolean forceDark) {
        final LightboxOverlayConfirmDeleteFragment fragment = new LightboxOverlayConfirmDeleteFragment();
        fragment.setArgs(closeListener, confirmListener, forceDark);
        return fragment;
    }
    
    private void setArgs(OnCloseListener closeListener, View.OnClickListener confirmListener, boolean forceDark) {
        this.closeListener = closeListener;
        this.confirmListener = confirmListener;
        if (forceDark)
            theme = R.style.DeleteConfirmationTheme;
    }
    
    @Override
    public int getTheme() {
        return theme;
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
    public void onStart() {
        super.onStart();
        
        // This is all some hackery to get the dialog fragment to draw under the transparent
        // nav bar properly
        if (getDialog() == null)
            return;
        Window window = getDialog().getWindow();
        if (window == null)
            return;
        View view = window.findViewById(com.google.android.material.R.id.container);
        if (view == null)
            return;
        
        if (getView() == null)
            return;
        View mainView = getView().findViewById(R.id.main_view);
        if (mainView == null)
            return;
        
        view.setFitsSystemWindows(false);
        final ViewUtils.LayoutData mainViewPadding = ViewUtils.recordLayoutData(mainView);
        window.findViewById(com.google.android.material.R.id.container).setOnApplyWindowInsetsListener((v, windowInsets) -> {
            ViewUtils.updatePaddingBottom(mainView,
                    windowInsets.getSystemWindowInsetBottom(),
                    mainViewPadding);
            ViewUtils.updateMarginSides(mainView,
                    windowInsets.getSystemWindowInsetLeft(),
                    windowInsets.getSystemWindowInsetRight(),
                    mainViewPadding);
            return windowInsets;
        });
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
