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
    
    private final AppCompatActivity mContext;
    private final List<StickerPack> mDataSource;
    private final LayoutInflater mInflater;
    private final boolean show_buttons;
    
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
        
        View rowView;
        if (convertView != null) {
            rowView = convertView;
            rowView.findViewById(R.id.installButton).setVisibility(View.GONE);
            rowView.findViewById(R.id.removeButton).setVisibility(View.GONE);
            rowView.findViewById(R.id.updateButton).setVisibility(View.GONE);
            rowView.findViewById(R.id.progressBar).setVisibility(View.GONE);
            rowView.findViewById(R.id.sticker_pack_list_update_text).setVisibility(View.GONE);
        } else
            rowView = mInflater.inflate(R.layout.list_item_sticker_pack, parent, false);
        
        TextView titleTextView = rowView.findViewById(R.id.sticker_pack_list_title);
        TextView subtitleTextView = rowView.findViewById(R.id.sticker_pack_list_subtitle);
        ImageView thumbnailImageView = rowView.findViewById(R.id.sticker_pack_list_thumbnail);
    
        titleTextView.setText(pack.getPackname());
        subtitleTextView.setText(pack.getExtraText());
        
        if (pack.wasUpdatedRecently()) {
            TextView updatedTextView = rowView.findViewById(R.id.sticker_pack_list_update_text);
            int nNewStickers = pack.getUpdatedURIs().size();
            updatedTextView.setText(String.format(mContext.getString(R.string.pack_list_update_report),
                    nNewStickers,
                    (nNewStickers > 1) ? "s" : ""));
            updatedTextView.setVisibility(View.VISIBLE);
        }
        
        // If we don't to this, the on-click ripple effect doesn't always radiate from the touch
        // location. Not sure what's up.
        rowView.setOnTouchListener((view, motionEvent) -> {
            view.findViewById(R.id.viewWithRippleEffect)
                    .getBackground()
                    .setHotspot(motionEvent.getX(), motionEvent.getY());
            view.performClick();
            return false;
        });
        
        // If the pack's icon is a gif, we need Glide. If it's not a gif, BitmapFactory is faster
        // (i.e. there's a visible latency with Glide)
        if (pack.getIconfile() != null) {
            String file = pack.getIconfile().toString();
            if (file.endsWith(".gif"))
                GlideApp.with(mContext).load(pack.getIconfile()).into(thumbnailImageView);
            else
                thumbnailImageView.setImageBitmap(BitmapFactory.decodeFile(file));
        }
        
        if (!show_buttons)
            return rowView;
    
        Button button;
        switch (pack.getStatus()) {
            case UNINSTALLED:
                button = rowView.findViewById(R.id.installButton);
                button.setVisibility(View.VISIBLE);
                button.setTag(R.id.button_callback_sticker_pack, pack);
                button.setTag(R.id.button_callback_adapter, this);
                button.setTag(R.id.button_callback_context, mContext);

                button.setOnClickListener(v -> {
                    StickerPack packToInstall = (StickerPack) v.getTag(R.id.button_callback_sticker_pack);
    
                    MainActivity context = (MainActivity) v.getTag(R.id.button_callback_context);
                    packToInstall.install(context, () -> context.model.triggerPackStatusChange(), true);
                    context.model.triggerPackStatusChange();
                });
                break;
    
            case INSTALLED:
                button = rowView.findViewById(R.id.removeButton);
                button.setVisibility(View.VISIBLE);
                button.setTag(R.id.button_callback_sticker_pack, pack);
                button.setTag(R.id.button_callback_adapter, this);
                button.setTag(R.id.button_callback_context, mContext);

                button.setOnClickListener(v -> {
                    StickerPack packToRemove = (StickerPack) v.getTag(R.id.button_callback_sticker_pack);
    
                    MainActivity context = (MainActivity) v.getTag(R.id.button_callback_context);
                    packToRemove.uninstall(context);
                    context.model.triggerPackStatusChange();
                });
                break;
            
            case UPDATEABLE:
                button = rowView.findViewById(R.id.updateButton);
                button.setVisibility(View.VISIBLE);
                button.setTag(R.id.button_callback_sticker_pack, pack);
                button.setTag(R.id.button_callback_adapter, this);
                button.setTag(R.id.button_callback_context, mContext);

                button.setOnClickListener(v -> {
                    StickerPack packToUpdate = (StickerPack) v.getTag(R.id.button_callback_sticker_pack);
    
                    MainActivity context = (MainActivity) v.getTag(R.id.button_callback_context);
                    packToUpdate.update(context, () -> context.model.triggerPackStatusChange(), true);
                    context.model.triggerPackStatusChange();
                });
                break;
    
            case INSTALLING:
                View spinner = rowView.findViewById(R.id.progressBar);
                spinner.setVisibility(View.VISIBLE);
                break;
        }
        
        return rowView;
    }
}
