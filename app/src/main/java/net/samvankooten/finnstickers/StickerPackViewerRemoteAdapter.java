package net.samvankooten.finnstickers;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import com.bumptech.glide.Glide;

import java.util.List;

/**
 * Created by sam on 10/31/17.
 */

public class StickerPackViewerRemoteAdapter extends BaseAdapter {
    private Context mContext;
    private List<String> mUrls;
    
    public StickerPackViewerRemoteAdapter(Context c, List<String> urls) {
        mContext = c;
        mUrls = urls;
    }
    
    public int getCount() {
        return mUrls.size();
    }
    
    public String getItem(int position) {
        return mUrls.get(position);
    }
    
    public long getItemId(int position) {
        return position;
    }
    
    // create a new ImageView for each item referenced by the Adapter
    public View getView(int position, View convertView, ViewGroup parent) {
        ImageView imageView;
        if (convertView == null) {
            // if it's not recycled, initialize some attributes
            imageView = new ImageView(mContext);
            int size = (int) (120 * mContext.getResources().getDisplayMetrics().density);
            imageView.setLayoutParams(new GridView.LayoutParams(size, size));
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageView.setPadding(8, 8, 8, 8);
        } else {
            imageView = (ImageView) convertView;
        }
        
        Glide.with(mContext).load(getItem(position)).into(imageView);
        
        return imageView;
    }
}
