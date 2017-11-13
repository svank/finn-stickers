package net.samvankooten.finnstickers;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import java.util.List;

/**
 * Created by sam on 10/31/17.
 */

public class StickerPackViewerRemoteAdapter extends BaseAdapter {
    private Context mContext;
    private List<Bitmap> mImages;
    private StickerProvider mProvider;
    
    public StickerPackViewerRemoteAdapter(Context c, List<Bitmap> images) {
        mContext = c;
        mImages = images;
        mProvider = new StickerProvider();
        mProvider.setRootDir(mContext);
    }
    
    public int getCount() {
        return mImages.size();
    }
    
    public Bitmap getItem(int position) {
        return mImages.get(position);
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
            // TODO: Don't hardcode sticker size
            // These numbers are sticker dimensions in pixels
            imageView.setLayoutParams(new GridView.LayoutParams(320, 320));
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageView.setPadding(8, 8, 8, 8);
        } else {
            imageView = (ImageView) convertView;
        }
        
        imageView.setImageBitmap(getItem(position));
        return imageView;
    }
}
