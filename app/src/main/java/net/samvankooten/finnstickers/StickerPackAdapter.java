package net.samvankooten.finnstickers;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

/**
 * Created by sam on 10/22/17.
 */

public class StickerPackAdapter extends BaseAdapter{
    public static final String TAG = "StickerPackAdapter";
    
    private AppCompatActivity mContext;
    private List<StickerPack> mDataSource;
    private LayoutInflater mInflater;
    private boolean show_buttons;

    public StickerPackAdapter(MainActivity context, List<StickerPack> items) {
        mContext = context;
        mDataSource = items;
        mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        show_buttons = true;
    }
    
    public StickerPackAdapter(ContentPickerPackPickerActivity context, List<StickerPack> items) {
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
    
                button.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        // Main-thread code here
                        StickerPack pack = (StickerPack) v.getTag(R.id.button_callback_sticker_pack);
                        
                        StickerPackAdapter adapter = (StickerPackAdapter) v.getTag(R.id.button_callback_adapter);
    
                        MainActivity context = (MainActivity) v.getTag(R.id.button_callback_context);
                        pack.install(adapter, context);
                        adapter.notifyDataSetChanged();
                    }
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
    
                button.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        // Main-thread code here
                        StickerPack pack = (StickerPack) v.getTag(R.id.button_callback_sticker_pack);
            
                        StickerPackAdapter adapter = (StickerPackAdapter) v.getTag(R.id.button_callback_adapter);
            
                        MainActivity context = (MainActivity) v.getTag(R.id.button_callback_context);
                        pack.remove(context);
                        adapter.notifyDataSetChanged();
                    }
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
        
                button.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        // Main-thread code here
                        StickerPack pack = (StickerPack) v.getTag(R.id.button_callback_sticker_pack);
                
                        StickerPackAdapter adapter = (StickerPackAdapter) v.getTag(R.id.button_callback_adapter);
                
                        MainActivity context = (MainActivity) v.getTag(R.id.button_callback_context);
                        pack.update(adapter, context);
                        adapter.notifyDataSetChanged();
                    }
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
        thumbnailImageView.setImageBitmap(BitmapFactory.decodeFile(pack.getIconfile().toString()));
        
        
        return rowView;
    }
}
