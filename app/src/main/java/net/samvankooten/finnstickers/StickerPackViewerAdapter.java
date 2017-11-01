package net.samvankooten.finnstickers;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import java.io.File;
import java.util.List;

/**
 * Created by sam on 10/31/17.
 */

public class StickerPackViewerAdapter extends BaseAdapter {
    private Context mContext;
    private List<String> mUris;
    private StickerProvider mProvider;
    
    public StickerPackViewerAdapter(Context c, List<String> uris) {
        mContext = c;
        mUris = uris;
        mProvider = new StickerProvider();
        mProvider.setRootDir(mContext);
    }
    
    public int getCount() {
        return mUris.size();
    }
    
    public String getItem(int position) {
        return mUris.get(position);
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
        
        File path = mProvider.uriToFile(Uri.parse(getItem(position)));
        imageView.setImageDrawable(Drawable.createFromPath(path.toString()));
        return imageView;
    }
}
