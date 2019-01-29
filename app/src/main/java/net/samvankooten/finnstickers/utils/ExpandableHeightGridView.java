package net.samvankooten.finnstickers.utils;

/*
  Created by sam on 10/31/17.
  From https://stackoverflow.com/questions/21264951/problems-with-gridview-inside-scrollview-in-android
  A GridView that gets tall rather than scrolling
 */
import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.GridView;

public class ExpandableHeightGridView extends GridView {
    
    boolean expanded = false;
    
    public ExpandableHeightGridView(Context context)
    {
        super(context);
    }
    
    public ExpandableHeightGridView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
    }
    
    public ExpandableHeightGridView(Context context, AttributeSet attrs,
                                    int defStyle)
    {
        super(context, attrs, defStyle);
    }
    
    public boolean isExpanded()
    {
        return expanded;
    }
    
    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
    {
        // HACK! TAKE THAT ANDROID!
        if (isExpanded())
        {
            // Calculate entire height by providing a very large height hint.
            // View.MEASURED_SIZE_MASK represents the largest height possible.
            int expandSpec = MeasureSpec.makeMeasureSpec(MEASURED_SIZE_MASK,
                    MeasureSpec.AT_MOST);
            super.onMeasure(widthMeasureSpec, expandSpec);
            
            ViewGroup.LayoutParams params = getLayoutParams();
            params.height = getMeasuredHeight();
        }
        else
        {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }
    
    public void setExpanded(boolean expanded)
    {
        this.expanded = expanded;
    } }