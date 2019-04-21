package net.samvankooten.finnstickers.editor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.InputType;
import android.text.Layout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import net.samvankooten.finnstickers.R;

import androidx.appcompat.widget.AppCompatEditText;

class TextObject extends AppCompatEditText {
    private static final String TAG = "TextObject";
    
    private Context context;
    
    private Bitmap topBitmap = null;
    private Canvas topCanvas = null;
    private Bitmap bottomBitmap = null;
    private Canvas bottomCanvas = null;
    private float rotation = 0;
    private int maxWidth;
    private float scale;
    private int baseSize;
    private int basePadding;
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
        basePadding = context.getResources().getDimensionPixelSize(R.dimen.editor_text_padding);
        scale = 1;
        setTextSize(baseSize);
        setupBitmaps(1, 1);
        setTypeface(null, Typeface.BOLD);
        setBackgroundColor(Color.TRANSPARENT);
        setBackgroundColor(Color.argb(100, 255, 0, 0));
        setInputType(buildInputType());
        
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
        bottomBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bottomCanvas = new UnClippableCanvas(topBitmap);
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setPaintToOutline();
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        // TextView seems to clear the canvas it's given, thus the need to have two canvases
        // to overlay manually. An optimization might be to draw the stroke directly onto the
        // canvas we're given, so we're only keeping one bitmap ourselves. However, the bottom
        // layer needs to be in an UnClippableCanvas, since otherwise the stroke extends beyond
        // the nominal bounds of the text and the edge gets clipped off. So two bitmaps of our
        // own are needed.
        topBitmap.eraseColor(Color.TRANSPARENT);
        bottomBitmap.eraseColor(Color.TRANSPARENT);
        setPaintToOutline();
        super.onDraw(bottomCanvas);
        
        setPaintToRegular();
        super.onDraw(topCanvas);
    
        canvas.drawBitmap(bottomBitmap, 0, 0, null);
        canvas.drawBitmap(topBitmap, 0, 0, null);
    }
    
    private void onStartEditing() {
        setInputType(buildInputType());
        if (originalText != null) {
            updateWidth();
            setText(originalText);
        }
    }
    
    private void onStopEditing() {
        if (getText() != null) {
            originalText = getText().toString();
            setText(makeLineBreaksHard());
            updateWidth();
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
        int padding = (int) (basePadding * scale);
        
        setPadding(padding, padding, padding, padding);
        
        if (getText() == null)
            return;
        final String text = getText().toString();
        if (textIsMultiLine()) {
            setPaintToOutline();
            totalWidth = (int) Math.ceil(Layout.getDesiredWidth(text, new TextPaint(getPaint())));
            totalWidth += 2 * padding;
        }
        setFixedWidth(totalWidth);
    }
    
    public void setFixedWidth(int w) {
        ViewGroup.LayoutParams params = getLayoutParams();
        params.width = w;
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        setLayoutParams(params);
    }
    
    private String makeLineBreaksHard() {
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
    
    private boolean textIsMultiLine() {
        if (getText() == null)
            return false;
        return getText().toString().indexOf('\n') >= 0;
    }
    
    /**
     * TextObject has to stay a more-or-less fixed width. Otherwise, if the view is rotated,
     * changing width (e.g. adding text) can make the whole view jump around. But if the text
     * we have doesn't extend to the edge of that fixed width, we don't want to respond to
     * touches in the area that doesn't contain text. This function checks for just that.
     * Given a MotionEvent, this transforms the touch coords into local coords within the rotated
     * frame, and then compares those local coords to location of text.
     *
     */
    boolean touchIsOnText(MotionEvent ev) {
        int location[] = new int[2];
        getLocationOnScreen(location);
        
        float[] vec = new float[2];
        vec[0] = ev.getRawX() - location[0];
        vec[1] = ev.getRawY() - location[1];
        
        Matrix matrix = new Matrix();
        matrix.setRotate(-getRotation());
        matrix.mapVectors(vec);
        
        float localX = vec[0];
        float localY = vec[1];
        
        setPaintToOutline();
        Layout layout = getLayout();
        int line = layout.getLineForVertical((int) localY);
        return localX < layout.getLineMax(line) + 2 * (basePadding * scale)
                && localX > 0
                && localY < layout.getLineBottom(layout.getLineCount()-1)
                && localY > 0;
    }
    
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (touchIsOnText(ev))
            return super.dispatchTouchEvent(ev);
        else
            return false;
    }
}
