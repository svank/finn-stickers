package net.samvankooten.finnstickers.sticker_pack_viewer;

import android.content.Context;
import android.util.AttributeSet;

import androidx.recyclerview.widget.RecyclerView;

/**
 * A RecyclerView that can be locked to ignore future changes in measured size.
 * Use case: The RecyclerView is part of a shared element transition and will be changing bounds.
 * We want Views in the RecyclerView to gradually be covered up as the bounds shrink. By default,
 * RecyclerView seems to instantly adopt the final bounds and hide/recycle any Views within that
 * region that's being removed. By locking the RecyclerView's size, we ensure those Views stay until
 * the transition ends.
 */
public class LockableRecyclerView extends RecyclerView {
    
    private boolean locked = false;
    private int width;
    private int height;
    
    public LockableRecyclerView(Context context) {
        super(context);
    }
    
    public LockableRecyclerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    
    public LockableRecyclerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }
    
    public void setLocked(boolean locked) {
        this.locked = locked;
    }
    
    /**
     * If we're not locked, record the final sizes after measurement.
     * If we are locked, set the View size to the last-recorded size (since onMeasure is required
     * to call setMeasuredDimension, we can't just do nothing here).
     */
    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (locked) {
            setMeasuredDimension(width, height);
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            width = getMeasuredWidth();
            height = getMeasuredHeight();
        }
    }
}
