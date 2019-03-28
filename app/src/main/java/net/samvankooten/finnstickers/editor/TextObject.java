package net.samvankooten.finnstickers.editor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.InputType;
import android.text.Layout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import net.samvankooten.finnstickers.R;

import androidx.appcompat.widget.AppCompatEditText;

class TextObject extends AppCompatEditText {
    
    private Context context;
    
    private Bitmap topBitmap = null;
    private Canvas topCanvas = null;
    private float rotation = 0;
    private int maxWidth;
    private float scale;
    private int baseSize;
    private String originalText;
    
    public TextObject(Context context) {
        super(context);
        init(context);
    }
    
    public TextObject(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }
    
    public TextObject(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }
    
    private void init(Context context) {
        this.context = context;
        
        // Ensure bitmaps are allocated
        baseSize = context.getResources().getDimensionPixelSize(R.dimen.editor_default_text_size);
        scale = 1;
        setTextSize(baseSize);
        setupBitmaps(1, 1);
        setTypeface(null, Typeface.BOLD);
        setBackgroundColor(Color.TRANSPARENT);
        setBackgroundColor(Color.argb(100, 255, 0, 0));
        setInputType(buildInputType());
        setPadding(0, 0, 0, 0);
        
        setImeOptions(getImeOptions() | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        
        setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus)
                onStartEditing();
            else
                onStopEditing();
        });
    }
    
    private int buildInputType() {
        return InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
    }
    
    public void setMaxWidth(int maxWidth) {
        this.maxWidth = maxWidth;
        updateWidth();
    }
    
    private void setPaintToOutline() {
        Paint paint = getPaint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.2f * getTextSize());
        super.setTextColor(Color.BLACK);
        
    }
    
    private void setPaintToRegular() {
        Paint paint = getPaint();
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeWidth(0);
        super.setTextColor(Color.WHITE);
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0)
            return;
        setupBitmaps(w, h);
    }
    
    private void setupBitmaps(int width, int height) {
        topBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        topCanvas = new Canvas(topBitmap);
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setPaintToOutline();
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        topBitmap.eraseColor(Color.TRANSPARENT);
        setPaintToOutline();
        super.onDraw(canvas);
        
        setPaintToRegular();
        super.onDraw(topCanvas);
    
        canvas.drawBitmap(topBitmap, 0, 0, null);
    }
    
    public void onStartEditing() {
        setInputType(buildInputType());
        if (originalText != null) {
            updateWidth();
            setText(originalText);
        }
    }
    
    public void onStopEditing() {
        if (getText() != null) {
            originalText = getText().toString();
            setText(makeLineBreaksHard());
        }
        setInputType(buildInputType() | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    }
    
    @Override
    public void setTextSize(float size) {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
    }
    
    public void scale(float factor) {
        scale *= factor;
        setTextSize(baseSize * scale);
        
        addDx((getWidth() - factor * getWidth()) / 2);
        addDy((getHeight() - factor * getHeight()) / 2);
        
        updateWidth();
    }
    
    public void rotate(float angle) {
        rotation += angle;
        setRotation(rotation);
    }
    
    public void addDx(float dx) {
        setX(getX() + dx);
    }
    
    public void addDy(float dy) {
        setY(getY() + dy);
    }
    
    public float getFractionalX(int left, int right) {
        final float span = right - left;
        final float x = getX() - left;
        return x / span;
    }
    
    public float getFractionalY(int top, int bottom) {
        final float span = bottom - top;
        final float y = getY() - top;
        return y / span;
    }
    
    public float getFractionalSize(int top, int bottom) {
        final float span = bottom - top;
        return getTextSize() / span;
    }
    
    public float getCurrentRotation() {
        return rotation;
    }
    
    private void updateWidth() {
        int totalWidth = (int) (maxWidth * scale);
    
        final String text = getText().toString();
        if (text.indexOf('\n') > 0) {
            setPaintToOutline();
            totalWidth = (int) Math.ceil(Layout.getDesiredWidth(text, new TextPaint(getPaint())));
        }
        setFixedWidth(totalWidth);
    }
    
    public void setFixedWidth(int w) {
        ViewGroup.LayoutParams params = getLayoutParams();
        params.width = w;
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        setLayoutParams(params);
    }
    
    public String makeLineBreaksHard() {
        Layout layout = getLayout();
        if (layout == null)
            return "";
        String text = layout.getText().toString();
        StringBuilder newText = new StringBuilder();
        int lastLineNumber = 0;
        for (int i=0; i<text.length(); i++) {
            int lineNumber = layout.getLineForOffset(i);
            if (lineNumber != lastLineNumber)
                newText.append('\n');
            lastLineNumber = lineNumber;
            newText.append(text.charAt(i));
        }
        return newText.toString();
    }
}
