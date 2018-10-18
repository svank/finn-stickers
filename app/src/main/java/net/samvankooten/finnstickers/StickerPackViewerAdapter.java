package net.samvankooten.finnstickers;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import com.facebook.drawee.view.SimpleDraweeView;

import java.util.List;

/**
 * Created by sam on 10/31/17.
 */

class StickerPackViewerAdapter extends BaseAdapter {
    private final Context context;
    private final List<String> identifiers;
    private final StickerProvider provider;
    
    StickerPackViewerAdapter(Context c, List<String> identifiers) {
        context = c;
        this.identifiers = identifiers;
        provider = new StickerProvider();
        provider.setRootDir(context);
    }
    
    public int getCount() {
        return identifiers.size();
    }
    
    public String getItem(int position) {
        return identifiers.get(position);
    }
    
    public long getItemId(int position) {
        return position;
    }
    
    // create a new ImageView for each item referenced by the Adapter
    public View getView(int position, View convertView, ViewGroup parent) {
        SimpleDraweeView imageView;
        if (convertView == null) {
            // if it's not recycled, initialize some attributes
            imageView = new SimpleDraweeView(context);
            int size = (int) (120 * context.getResources().getDisplayMetrics().density);
            imageView.setLayoutParams(new GridView.LayoutParams(size, size));
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageView.setPadding(8, 8, 8, 8);
        } else {
            imageView = (SimpleDraweeView) convertView;
        }
        
        String item = getItem(position);
        imageView.setImageURI(item);
        imageView.setTag(R.id.sticker_uri, item);
        
        return imageView;
    }
}
