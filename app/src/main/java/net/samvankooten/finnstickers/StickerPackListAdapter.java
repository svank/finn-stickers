package net.samvankooten.finnstickers;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import net.samvankooten.finnstickers.misc_classes.GlideApp;

import java.util.List;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Created by sam on 10/22/17.
 */

class StickerPackListAdapter extends BaseAdapter{
    public static final String TAG = "StickerPackListAdapter";
    
    private AppCompatActivity mContext;
    private List<StickerPack> mDataSource;
    private LayoutInflater mInflater;
    private boolean show_buttons;

    public StickerPackListAdapter(MainActivity context, List<StickerPack> items) {
        mContext = context;
        mDataSource = items;
        mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        show_buttons = true;
    }
    
    public StickerPackListAdapter(ContentPickerPackPickerActivity context, List<StickerPack> items) {
        mContext = context;
        mDataSource = items;
        mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        show_buttons = false;
    }

    @Override
    public int getCount() {
        return mDataSource.size();
    }

    @Override
    public StickerPack getItem(int position) {
        return mDataSource.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        StickerPack pack = getItem(position);
        View rowView = null;
        
        Button button;
        
        switch (pack.getStatus()) {
            case UNINSTALLED:
                rowView = mInflater.inflate(R.layout.list_item_sticker_pack, parent, false);
                button = rowView.findViewById(R.id.installButton);
                
                if (!show_buttons) {
                    button.setVisibility(View.GONE);
                    break;
                }
                button.setTag(R.id.button_callback_sticker_pack, pack);
                button.setTag(R.id.button_callback_adapter, this);
                button.setTag(R.id.button_callback_context, mContext);
    
                button.setOnClickListener(v -> {
                    // Main-thread code here
                    StickerPack packToInstall = (StickerPack) v.getTag(R.id.button_callback_sticker_pack);
                    
                    MainActivity context = (MainActivity) v.getTag(R.id.button_callback_context);
                    packToInstall.install(context.model, context);
                    context.model.triggerPackStatusChange();
                });
                break;
    
            case INSTALLED:
                rowView = mInflater.inflate(R.layout.list_item_sticker_pack_installed, parent, false);
                button = rowView.findViewById(R.id.removeButton);
    
                if (!show_buttons) {
                    button.setVisibility(View.GONE);
                    break;
                }
                button.setTag(R.id.button_callback_sticker_pack, pack);
                button.setTag(R.id.button_callback_adapter, this);
                button.setTag(R.id.button_callback_context, mContext);
    
                button.setOnClickListener(v -> {
                    // Main-thread code here
                    StickerPack packToRemove = (StickerPack) v.getTag(R.id.button_callback_sticker_pack);
        
                    MainActivity context = (MainActivity) v.getTag(R.id.button_callback_context);
                    packToRemove.remove(context);
                    context.model.triggerPackStatusChange();
                });
                break;
            
            case UPDATEABLE:
                rowView = mInflater.inflate(R.layout.list_item_sticker_pack_updateable, parent, false);
                button = rowView.findViewById(R.id.updateButton);
    
                if (!show_buttons) {
                    button.setVisibility(View.GONE);
                    break;
                }
                button.setTag(R.id.button_callback_sticker_pack, pack);
                button.setTag(R.id.button_callback_adapter, this);
                button.setTag(R.id.button_callback_context, mContext);
        
                button.setOnClickListener(v -> {
                    // Main-thread code here
                    StickerPack packToUpdate = (StickerPack) v.getTag(R.id.button_callback_sticker_pack);
            
                    MainActivity context = (MainActivity) v.getTag(R.id.button_callback_context);
                    packToUpdate.update(context.model, context);
                    context.model.triggerPackStatusChange();
                });
                break;
    
            case INSTALLING:
                rowView = mInflater.inflate(R.layout.list_item_sticker_pack_downloading, parent, false);
    
                if (!show_buttons) {
                    View spinner = rowView.findViewById(R.id.progressBar);
                    spinner.setVisibility(View.GONE);
                    break;
                }
                break;
        }
        
        TextView titleTextView = rowView.findViewById(R.id.sticker_pack_list_title);
        TextView subtitleTextView = rowView.findViewById(R.id.sticker_pack_list_subtitle);
        ImageView thumbnailImageView = rowView.findViewById(R.id.sticker_pack_list_thumbnail);
        
        titleTextView.setText(pack.getPackname());
        subtitleTextView.setText(pack.getExtraText());
        
        // If the pack's icon is a gif, we need Glide. If it's not a gif, BitmapFactory is faster
        // (i.e. there's a visible latency with Glide)
        if (pack.getIconfile() != null) {
            String file = pack.getIconfile().toString();
            if (file.endsWith(".gif"))
                GlideApp.with(mContext).load(pack.getIconfile()).into(thumbnailImageView);
            else
                thumbnailImageView.setImageBitmap(BitmapFactory.decodeFile(file));
        }
        
        return rowView;
    }
}
