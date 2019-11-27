package net.samvankooten.finnstickers.utils;

import android.view.View;
import android.view.ViewGroup;

public class ViewUtils {
    /*
    Utils for handling window insets (nav bar & status bar location/size when those bars are
    transparent). The insets can change during the lifetime of the activity, so
    recordLayoutData will record the View's original padding and margins. Then the other functions
    can be used to set the View's padding/margin to the original value plus some current
    inset-related value, and these functions can be called again later to update that
    margin/padding if the insets change.
     */
    
    public static void updatePaddingTop(View view, int paddingTop, LayoutData orig) {
        view.setPadding(view.getPaddingLeft(), paddingTop + orig.paddingTop,
                view.getPaddingRight(), view.getPaddingBottom());
    }
    
    public static void updatePaddingBottom(View view, int paddingBottom, LayoutData orig) {
        view.setPadding(view.getPaddingLeft(), view.getPaddingTop(),
                view.getPaddingRight(), paddingBottom + orig.paddingBottom);
    }
    
    public static void updatePaddingLeft(View view, int paddingLeft, LayoutData orig) {
        view.setPadding(paddingLeft + orig.paddingLeft, view.getPaddingTop(),
                view.getPaddingRight(), view.getPaddingBottom());
    }
    
    public static void updatePaddingRight(View view, int paddingRight, LayoutData orig) {
        view.setPadding(view.getPaddingLeft(), view.getPaddingTop(),
                paddingRight + orig.paddingRight, view.getPaddingBottom());
    }
    
    public static void updateMarginLeft(View view, int marginLeft, LayoutData orig) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            marginParams.leftMargin = marginLeft + orig.leftMargin;
            view.setLayoutParams(marginParams);
        }
    }
    
    public static void updateMarginRight(View view, int marginRight, LayoutData orig) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            marginParams.rightMargin = marginRight + orig.rightMargin;
            view.setLayoutParams(marginParams);
        }
    }
    
    public static void updatePaddingSides(View view,
                                          int paddingLeft,
                                          int paddingRight,
                                          LayoutData orig) {
        updatePaddingLeft(view, paddingLeft, orig);
        updatePaddingRight(view, paddingRight, orig);
    }
    
    public static void updateMarginSides(View view,
                                         int marginLeft,
                                         int marginRight,
                                         LayoutData orig) {
        updateMarginLeft(view, marginLeft, orig);
        updateMarginRight(view, marginRight, orig);
    }
    
    public static LayoutData recordLayoutData(View view) {
        return new LayoutData(view);
    }
    
    public static class LayoutData {
        public int paddingLeft;
        public int paddingRight;
        public int paddingTop;
        public int paddingBottom;
        
        public int leftMargin = -1;
        public int rightMargin = -1;
        public int topMargin = -1;
        public int bottomMargin = -1;
        
        public LayoutData(View view) {
            this.paddingLeft = view.getPaddingLeft();
            this.paddingRight = view.getPaddingRight();
            this.paddingTop = view.getPaddingTop();
            this.paddingBottom = view.getPaddingBottom();
    
            ViewGroup.LayoutParams params = view.getLayoutParams();
            if (params instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) params;
                this.leftMargin = marginParams.leftMargin;
                this.rightMargin = marginParams.rightMargin;
                this.topMargin = marginParams.topMargin;
                this.bottomMargin = marginParams.bottomMargin;
            }
        }
    }
}