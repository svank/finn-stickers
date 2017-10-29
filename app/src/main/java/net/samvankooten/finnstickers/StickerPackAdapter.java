package net.samvankooten.finnstickers;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Created by sam on 10/22/17.
 */

public class StickerPackAdapter extends BaseAdapter{
    public static final String TAG = "StickerPackAdapter";
    
    private MainActivity mContext;
    private StickerPack[] mDataSource;
    private LayoutInflater mInflater;

    public StickerPackAdapter(MainActivity context, StickerPack[] items) {
        mContext = context;
        mDataSource = items;
        mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public int getCount() {
        return mDataSource.length;
    }

    @Override
    public StickerPack getItem(int position) {
        return mDataSource[position];
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
                button.setTag(R.id.button_callback_sticker_pack, pack);
                button.setTag(R.id.button_callback_adapter, this);
                button.setTag(R.id.button_callback_context, mContext);
    
                button.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        // Main-thread code here
                        StickerPack pack = (StickerPack) v.getTag(R.id.button_callback_sticker_pack);
                        
                        StickerPackAdapter adapter = (StickerPackAdapter) v.getTag(R.id.button_callback_adapter);
    
                        MainActivity context = (MainActivity) v.getTag(R.id.button_callback_context);
                        Log.d(TAG, "Launching install()");
                        pack.install(adapter, context);
                        adapter.notifyDataSetChanged();
                    }
                });
                break;
    
            case INSTALLED:
                rowView = mInflater.inflate(R.layout.list_item_sticker_pack_installed, parent, false);
                button = rowView.findViewById(R.id.removeButton);
                button.setTag(R.id.button_callback_sticker_pack, pack);
                button.setTag(R.id.button_callback_adapter, this);
                button.setTag(R.id.button_callback_context, mContext);
    
                button.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        // Main-thread code here
                        StickerPack pack = (StickerPack) v.getTag(R.id.button_callback_sticker_pack);
            
                        StickerPackAdapter adapter = (StickerPackAdapter) v.getTag(R.id.button_callback_adapter);
            
                        MainActivity context = (MainActivity) v.getTag(R.id.button_callback_context);
                        Log.d(TAG, "Launching pack removal");
                        pack.remove(context);
                        adapter.notifyDataSetChanged();
                    }
                });
                break;
            
            case UPDATEABLE:
                rowView = mInflater.inflate(R.layout.list_item_sticker_pack_updateable, parent, false);
                button = rowView.findViewById(R.id.updateButton);
                button.setTag(R.id.button_callback_sticker_pack, pack);
                button.setTag(R.id.button_callback_adapter, this);
                button.setTag(R.id.button_callback_context, mContext);
        
                button.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        // Main-thread code here
                        StickerPack pack = (StickerPack) v.getTag(R.id.button_callback_sticker_pack);
                
                        StickerPackAdapter adapter = (StickerPackAdapter) v.getTag(R.id.button_callback_adapter);
                
                        MainActivity context = (MainActivity) v.getTag(R.id.button_callback_context);
                        Log.d(TAG, "Launching update()");
                        pack.update(adapter, context);
                        adapter.notifyDataSetChanged();
                    }
                });
                break;
    
            case INSTALLING:
                rowView = mInflater.inflate(R.layout.list_item_sticker_pack_downloading, parent, false);
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
