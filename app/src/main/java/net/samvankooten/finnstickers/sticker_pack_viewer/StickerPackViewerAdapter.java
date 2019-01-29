package net.samvankooten.finnstickers.sticker_pack_viewer;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.signature.ObjectKey;

import net.samvankooten.finnstickers.R;
import net.samvankooten.finnstickers.StickerProvider;
import net.samvankooten.finnstickers.misc_classes.GlideApp;
import net.samvankooten.finnstickers.misc_classes.GlideRequest;

import java.util.List;

import static com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade;

/**
 * Created by sam on 10/31/17.
 */

class StickerPackViewerAdapter extends BaseAdapter {
    private final Context context;
    private final List<String> identifiers;
    private final StickerProvider provider;
    private boolean remote;
    private int pack_version;
    
    StickerPackViewerAdapter(Context c, List<String> identifiers, boolean remote, int pack_version) {
        context = c;
        this.identifiers = identifiers;
        provider = new StickerProvider();
        provider.setRootDir(context);
        this.remote = remote;
        this.pack_version = pack_version;
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
        ImageView imageView;
        if (convertView == null) {
            // if it's not recycled, initialize some attributes
            imageView = new ImageView(context);
            int size = (int) (120 * context.getResources().getDisplayMetrics().density);
            imageView.setLayoutParams(new GridView.LayoutParams(size, size));
            imageView.setPadding(8, 8, 8, 8);
        } else {
            imageView = (ImageView) convertView;
        }
        
        String item = getItem(position);
        
        GlideRequest builder = GlideApp.with(context).load(item).centerCrop();
        if (remote) {
            builder.transition(withCrossFade());
            // Enable disk caching for remote loads---see CustomAppGlideModule
            builder.diskCacheStrategy(DiskCacheStrategy.AUTOMATIC);
            // Ensure cached data is invalidated if pack version number changes
            builder.signature(new ObjectKey(pack_version));
        }
        builder.into(imageView);
        
        imageView.setTag(R.id.sticker_uri, item);
        
        return imageView;
    }
}
