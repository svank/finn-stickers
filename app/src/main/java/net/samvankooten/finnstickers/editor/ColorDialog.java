package net.samvankooten.finnstickers.editor;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.Window;
import android.widget.TabHost;

import com.rarepebble.colorpicker.ColorPickerView;

import net.samvankooten.finnstickers.R;

class ColorDialog extends Dialog {
    
    private final Context context;
    private final int textColor;
    private final int outlineColor;
    
    private ColorPickerView textPicker;
    private ColorPickerView outlinePicker;
    
    public ColorDialog(Context context, int textColor, int outlineColor) {
        super(context);
        this.context = context;
        this.textColor = textColor;
        this.outlineColor = outlineColor;
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_color_picker);
    
        TabHost tabs = findViewById(R.id.tabhost);
        tabs.setup();
        TabHost.TabSpec spec = tabs.newTabSpec("text");
        spec.setContent(R.id.tab_text);
        spec.setIndicator(context.getString(R.string.color_picker_text));
        tabs.addTab(spec);
        
        spec = tabs.newTabSpec("outline");
        spec.setContent(R.id.tab_outline);
        spec.setIndicator(context.getString(R.string.color_picker_outline));
        tabs.addTab(spec);
    
        textPicker = findViewById(R.id.colorPicker_text);
        textPicker.setColor(textColor);
        outlinePicker = findViewById(R.id.colorPicker_outline);
        outlinePicker.setColor(outlineColor);
        
        findViewById(R.id.ok_button).setOnClickListener(v -> dismiss());
        findViewById(R.id.cancel_button).setOnClickListener(v -> {
            textPicker.setColor(textColor);
            outlinePicker.setColor(outlineColor);
            dismiss();
        });
    }
    
    public int getTextColor() {
        return textPicker.getColor();
    }
    
    public int getOutlineColor() {
        return outlinePicker.getColor();
    }
}
