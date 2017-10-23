package net.samvankooten.finnstickers;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Created by sam on 10/22/17.
 */

public class StickerPackAdapter extends BaseAdapter{

    private Context mContext;
    private StickerPack[] mDataSource;
    private LayoutInflater mInflater;

    public StickerPackAdapter(Context context, StickerPack[] items) {
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
    public View getView(int position, View rowView, ViewGroup parent) {
        if (rowView == null)
            rowView = mInflater.inflate(R.layout.list_item_sticker_pack, parent, false);
        
        // TODO: Use view holders if the number of sticker packs ever becomes large
        
        TextView titleTextView =
                (TextView) rowView.findViewById(R.id.sticker_pack_list_title);
        
        TextView subtitleTextView =
                (TextView) rowView.findViewById(R.id.sticker_pack_list_subtitle);
        
        ImageView thumbnailImageView =
                (ImageView) rowView.findViewById(R.id.sticker_pack_list_thumbnail);

        StickerPack pack = getItem(position);
        
        titleTextView.setText(pack.getPackname());
        subtitleTextView.setText(pack.getExtraText());
        thumbnailImageView.setImageBitmap(BitmapFactory.decodeFile(pack.getIconfile().toString()));
        
        return rowView;
    }
}
