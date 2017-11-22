package net.samvankooten.finnstickers;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import java.util.List;

public class ContentPickerPackPickerActivity extends AppCompatActivity {
    public static final String TAG = "PickerActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    
        findViewById(R.id.refresh_button).setVisibility(View.GONE);
        findViewById(R.id.editText).setVisibility(View.GONE);
    
        ListView listView = findViewById(R.id.pack_list_view);
        
        List<StickerPack> pack_list;
        try {
            pack_list = StickerPack.getInstalledPacks(getFilesDir());
        } catch (Exception e) {
            Log.e(TAG, "Error getting installed packs", e);
            return;
        }
        
        StickerPackAdapter adapter = new StickerPackAdapter(this, pack_list.toArray(new StickerPack[pack_list.size()]));
        listView.setAdapter(adapter);
        
        // To allow clicking on list items directly, as seen in
        // https://www.raywenderlich.com/124438/android-listview-tutorial
        listView.setClickable(true);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
        
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                StickerPack selectedPack = (StickerPack) parent.getItemAtPosition(position);
                
                Intent intent = new Intent(ContentPickerPackPickerActivity.this, StickerPackViewerActivity.class);
            
                intent.putExtra("pack", selectedPack);
                intent.putExtra("picker", true);
            
                startActivityForResult(intent, 314);
            }
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
