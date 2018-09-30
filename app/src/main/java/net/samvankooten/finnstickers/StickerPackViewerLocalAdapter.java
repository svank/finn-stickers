package net.samvankooten.finnstickers;

import android.content.Context;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.List;

/**
 * Created by sam on 10/31/17.
 */

class StickerPackViewerLocalAdapter extends BaseAdapter {
    private Context mContext;
    private List<String> mUris;
    private StickerProvider mProvider;
    
    StickerPackViewerLocalAdapter(Context c, List<String> uris) {
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
            int size = (int) (120 * mContext.getResources().getDisplayMetrics().density);
            imageView.setLayoutParams(new GridView.LayoutParams(size, size));
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageView.setPadding(8, 8, 8, 8);
        } else {
            imageView = (ImageView) convertView;
        }
        
        File path = mProvider.uriToFile(Uri.parse(getItem(position)));
        Glide.with(mContext).load(path).into(imageView);
    
        imageView.setTag(R.id.sticker_uri, getItem(position));
        return imageView;
    }
}
