package net.samvankooten.finnstickers.ar;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.bumptech.glide.request.RequestOptions;

import net.samvankooten.finnstickers.R;
import net.samvankooten.finnstickers.misc_classes.GlideApp;

import java.util.ArrayList;
import java.util.List;

public class GalleryRow extends HorizontalScrollView {
    
    private LinearLayout layout;
    private int selectedItem = -1;
    private List<String> uris;
    
    public GalleryRow(Context context) {
        super(context);
        init(context);
    }
    
    public GalleryRow(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }
    
    public GalleryRow(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }
    
    public GalleryRow(Context context, AttributeSet attrs, int defStyleAttr, int style) {
        super(context, attrs, defStyleAttr, style);
        init(context);
    }
    
    private void init(Context context) {
        layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        addView(layout);
    }
    
    public interface OnClickListener{
        void onClick(int position, View view);
    }
    
    public void setup(List<String> uris, final OnClickListener listener) {
        this.uris = uris;
        
        for (int i=0; i<uris.size(); i++) {
            ImageView v = new ImageView(getContext());
            final int size = (int) getResources().getDimension(R.dimen.ar_item_size);
            v.setLayoutParams(new LayoutParams(size, size));
            final int padding = (int) getResources().getDimension(R.dimen.ar_item_padding);
            v.setPadding(padding, padding, padding, padding);
            layout.addView(v);
            
            GlideApp.with(getContext()).load(uris.get(i)).apply(new RequestOptions().centerCrop()).into(v);
            
            v.setClickable(true);
            v.setFocusable(true);
            final int position = i;
            v.setOnClickListener(view -> listener.onClick(position, view));
        }
        
        setSelectedItem(0);
    }
    
    public void setSelectedItem(int position) {
        if (selectedItem >= 0)
            layout.getChildAt(selectedItem).setBackground(null);
        selectedItem = position;
        if (selectedItem >= 0)
            layout.getChildAt(selectedItem).setBackground(getContext().getDrawable(R.drawable.selection_square));
    }
    
    public int getSelectedItem() {
        return selectedItem;
    }
    
    public List<ImageView> getViews() {
        ArrayList<ImageView> list = new ArrayList<>(uris.size());
        
        for (int i=0; i<uris.size(); i++) {
            list.add((ImageView) layout.getChildAt(i));
        }
        return list;
    }
}