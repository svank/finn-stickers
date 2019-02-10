package net.samvankooten.finnstickers;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ListView;

import net.samvankooten.finnstickers.sticker_pack_viewer.StickerPackViewerActivity;
import net.samvankooten.finnstickers.utils.Util;

import java.util.List;

import androidx.appcompat.app.AppCompatActivity;

public class ContentPickerPackPickerActivity extends AppCompatActivity {
    private static final String TAG = "PickerActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    
        findViewById(R.id.refresh_button).setVisibility(View.GONE);
    
        ListView listView = findViewById(R.id.pack_list_view);
        
        List<StickerPack> pack_list;
        try {
            pack_list = Util.getInstalledPacks(getFilesDir());
        } catch (Exception e) {
            Log.e(TAG, "Error getting installed packs", e);
            return;
        }
        
        StickerPackListAdapter adapter = new StickerPackListAdapter(this, pack_list);
        listView.setAdapter(adapter);
        
        // To allow clicking on list items directly, as seen in
        // https://www.raywenderlich.com/124438/android-listview-tutorial
        listView.setClickable(true);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            StickerPack selectedPack = (StickerPack) parent.getItemAtPosition(position);
            
            Intent intent = new Intent(ContentPickerPackPickerActivity.this, StickerPackViewerActivity.class);
        
            intent.putExtra("pack", selectedPack);
            intent.putExtra("picker", true);
        
            startActivityForResult(intent, 314);
        });
    }
    
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 314 && resultCode == RESULT_OK) {
                Uri resultUri = Uri.parse(data.getStringExtra("uri"));
                Intent result = new Intent();
                result.setData(resultUri);
                setResult(Activity.RESULT_OK, result);
                finish();
        }
    }

}
