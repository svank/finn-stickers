package net.samvankooten.finnstickers.editor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.Layout;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import net.samvankooten.finnstickers.R;

import org.json.JSONException;
import org.json.JSONObject;

import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.AppCompatTextView;

class TextObject extends AppCompatEditText {
    private static final String TAG = "TextObject";
    
    private Context context;
    
    private Bitmap backingBitmap = null;
    private Canvas backingCanvas = null;
    private Matrix bitmapScaleMatrix = new Matrix();
    private AppCompatTextView outlineTextView = null;
    private AppCompatTextView centerTextView = null;
    private int imageWidth;
    
    private boolean isEditing = false;
    private float scale = 1;
    private int baseSize;
    private int basePadding;
    private String originalText;
    
    private String brokenText = "";
    private int nLines = 1;
    
    private onEditCallback onStartEditCallback;
    private onEditCallback onStopEditCallback;
    
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
        initTextView(this);
        setTextSize(baseSize);
        setPadding(basePadding, basePadding/2, basePadding, basePadding/2);
        super.setTextColor(Color.TRANSPARENT);
        setupDrawBackingResources();
        
        setImeOptions(getImeOptions() | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        
        setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus)
                onStartEditing();
            else
                onStopEditing();
        });
        
        addTextChangedListener(new TextWatcher() {
            private int nLinesBeforeEdit;
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                nLinesBeforeEdit = nLines;
            }
            
            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) { }
            
            @Override
            public void afterTextChanged(Editable editable) {
                if (getLayout() != null) {
                    brokenText = getTextWithHardLineBreaks();
                    nLines = getLayout().getLineCount();
                    if (nLines != nLinesBeforeEdit)
                        updateWidth();
                }
                setupDrawBackingResources();
            }
        });
    }
    
    private void initTextView(TextView view) {
        view.setTypeface(null, Typeface.BOLD);
        view.setBackgroundColor(Color.TRANSPARENT);
        view.setInputType(buildInputType());
    }
    
    public JSONObject toJSON(int imageLeft, int imageRight, int imageTop, int imageBottom) {
        if (hasFocus())
            clearFocus();
        
        JSONObject data = new JSONObject();
        final int imageWidth = imageRight - imageLeft;
        final int imageHeight = imageBottom - imageTop;
        try {
            data.put("text", originalText);
            data.put("brokenText", getText());
            data.put("scale", scale);
            data.put("pivotX", getPivotX() / imageWidth);
            data.put("pivotY", getPivotY() / imageHeight);
            data.put("rotation", getRotation());
            data.put("x", makeFractional(getX(), imageLeft, imageRight));
            data.put("y", makeFractional(getY(), imageTop, imageBottom));
            data.put("baseSize", (float) baseSize / imageWidth);
            data.put("basePadding", (float) basePadding / imageWidth);
            data.put("textColor", centerTextView.getCurrentTextColor());
            data.put("outlineColor", outlineTextView.getCurrentTextColor());
            return data;
        } catch (JSONException e) {
            Log.e(TAG, "Error generating JSON", e);
            return new JSONObject();
        }
    }
    
    public void loadJSON(JSONObject data, int imageLeft, int imageRight, int imageTop, int imageBottom) {
        final int imageWidth = imageRight - imageLeft;
        final int imageHeight = imageBottom - imageTop;
        try {
            this.imageWidth = imageWidth;
            baseSize = (int) (imageWidth * data.getDouble("baseSize"));
            basePadding = (int) (imageWidth * data.getDouble("basePadding"));
            originalText = data.getString("text");
            brokenText = data.getString("brokenText");
            nLines = 1;
            for (int i=0; i<brokenText.length(); i++) {
                if (brokenText.charAt(i) == '\n')
                    nLines += 1;
            }
            setText(brokenText);
            scale((float) data.getDouble("scale"), true);
            setPivotX(imageWidth * (float) data.getDouble("pivotX"));
            setPivotY(imageHeight * (float) data.getDouble("pivotY"));
            setRotation((float) data.getDouble("rotation"));
            setX(imageLeft + imageWidth * (float) data.getDouble("x"));
            setY(imageTop + imageHeight * (float) data.getDouble("y"));
    
            if (data.has("textColor"))
                setTextColor(data.getInt("textColor"));
            if (data.has("outlineColor"))
                setOutlineColor(data.getInt("outlineColor"));
            
        } catch (JSONException e) {
            Log.e(TAG, "Error loading JSON", e);
        }
    }
    
    private int buildInputType() {
        return InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
    }
    
    public void setImageWidth(int imageWidth) {
        this.imageWidth = imageWidth;
        updateWidth();
    }
    
    private void setupDrawBackingResources() {
        // Emojis don't render at sizes above 256px, which seems to be a
        // long-standing bug. So when we're rendering larger than that, we render 255px text
        // and then scale up that bitmap in onDraw(). So here we're capping these resources
        // at 255px font size. We'll then have backing TextViews at that size, into which we
        // insert our text, and have them render onto a Bitmap that we scale up.
        float ratio = 1;
        float textSize = getTextSize();
        if (baseSize * scale >= 255) {
            ratio = 255f / baseSize / scale;
            textSize = 255;
        }
        
        if (outlineTextView == null) {
            outlineTextView = new AppCompatTextView(context);
            FrameLayout layout = new FrameLayout(context);
            layout.addView(outlineTextView);
            initTextView(outlineTextView);
            outlineTextView.setTextColor(Color.BLACK);
            // If this text view is set to have a transparent color, make sure we have a uniform
            // appearance rather than having increased opacity when two letters' outlines overlap
            // by overwriting rather than blending as we draw
            outlineTextView.getPaint().setXfermode(
                    new PorterDuffXfermode(PorterDuff.Mode.SRC));
        
            centerTextView = new AppCompatTextView(context);
            FrameLayout layout2 = new FrameLayout(context);
            layout2.addView(centerTextView);
            initTextView(centerTextView);
            centerTextView.setTextColor(Color.WHITE);
            // If this text view is set to have a transparent color, make sure we see through
            // to the underlying image rather than just to the outline TextView by overwriting
            // rather than blending as we draw
            centerTextView.getPaint().setXfermode(
                    new PorterDuffXfermode(PorterDuff.Mode.SRC));
        }
        
        // We need to do a two-pass text render to get the white text and the black outline.
        // I was getting some funny behavior having just one backing TextView and changing its
        // style for the two renders, so instead we're using two backing TextViews, one for
        // each style.
        
        outlineTextView.setText(brokenText);
        outlineTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
        outlineTextView.setPadding(
                (int) (ratio * getPaddingLeft()),
                (int) (ratio * getPaddingTop()),
                (int) (ratio * getPaddingRight()),
                (int) (ratio * getPaddingBottom()));
        
        int renderedTextWidth = (int) Math.ceil(Layout.getDesiredWidth(brokenText,
                new TextPaint(outlineTextView.getPaint())))
                + outlineTextView.getPaddingLeft() + outlineTextView.getPaddingRight();
        outlineTextView.setWidth(renderedTextWidth);
        outlineTextView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
    
        Paint paint = outlineTextView.getPaint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.2f * outlineTextView.getTextSize());
        
        centerTextView.setText(brokenText);
        centerTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
        centerTextView.setPadding(
                (int) (ratio * getPaddingLeft()),
                (int) (ratio * getPaddingTop()),
                (int) (ratio * getPaddingRight()),
                (int) (ratio * getPaddingBottom()));
        centerTextView.setWidth(renderedTextWidth);
        centerTextView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
    
        int width = outlineTextView.getMeasuredWidth();
        int height = outlineTextView.getMeasuredHeight();
        
        // This is where we'll render text
        if (backingBitmap == null
                || backingBitmap.getWidth() != width
                || backingBitmap.getHeight() != height) {
            backingBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            backingCanvas = new UnClippableCanvas(backingBitmap);
        }
        
        int nativeTextWidth = (int) Math.ceil(Layout.getDesiredWidth(brokenText,
                new TextPaint(getPaint())))
                + getPaddingLeft() + getPaddingRight();
    
        bitmapScaleMatrix.setScale(
                (float) nativeTextWidth / renderedTextWidth,
                (float) nativeTextWidth / renderedTextWidth);
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        backingBitmap.eraseColor(Color.TRANSPARENT);
        
        outlineTextView.draw(backingCanvas);
        centerTextView.draw(backingCanvas);
        
        canvas.drawBitmap(backingBitmap, bitmapScaleMatrix, null);
        
        // This renders the text cursor, etc, during text editing.
        super.onDraw(canvas);
    }
    
    private void onStartEditing() {
        isEditing = true;
        setBackgroundColor(context.getResources().getColor(R.color.editorTextBackgroundDuringEdit));
        setInputType(buildInputType());
        updateWidth();
        if (originalText != null) {
            setText(originalText);
        }
        
        if (onStartEditCallback != null)
            onStartEditCallback.onCall();
    }
    
    private void onStopEditing() {
        isEditing = false;
        setBackgroundColor(Color.TRANSPARENT);
        if (getText() != null) {
            originalText = getText().toString();
            brokenText = getTextWithHardLineBreaks();
            nLines = getLayout().getLineCount();
            setText(brokenText);
            updateWidth();
        }
        setInputType(buildInputType() | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        
        if (onStopEditCallback != null)
            onStopEditCallback.onCall();
    }
    
    @Override
    public void setTextSize(float size) {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
    }
    
    public boolean scale(float factor) {
        return scale(factor, false);
    }
    
    private boolean scale(float factor, boolean force) {
        if (isEditing && !force)
            return false;
        
        // Set a lower limit so objects don't get too small to touch/manipulate.
        // A width of 0 indicates we haven't been laid out or whatever, and we're probably
        // being used in a non-interactive rendering mode.
        if (getWidth() != 0
            && baseSize * scale * factor < context.getResources().getDimension(R.dimen.editor_text_min_size)
            && factor < 1
            && !force)
            return false;
        
        if (getHeight()*factor >= 3000
                && factor > 1
                && !force)
            return false;
        
        scale *= factor;
        setTextSize(baseSize * scale);
        
        int padding = (int) (basePadding * scale);
        setPadding(padding, padding/2, padding, padding/2);
        updateWidth();
        setupDrawBackingResources();
        
        return true;
    }
    
    public boolean rotate(float angle) {
        if (isEditing)
            return false;
        setPivotX(getUserVisibleWidth() / 2f);
        setPivotY(getHeight() / 2f);
        setRotation(getRotation() + angle);
        return true;
    }
    
    public void addDx(float dx) {
        setX(getX() + dx);
    }
    
    public void addDy(float dy) {
        setY(getY() + dy);
    }
    
    private void updateWidth() {
        if (getText() == null)
            return;
        
        int nominalWidth = (int) (imageWidth * scale);
        if (isEditing) {
            int currentWidth = getWidth();
            setFixedWidth(currentWidth > nominalWidth ?
                    currentWidth : nominalWidth);
        } else {
            int textDesiredWidth = getUserVisibleWidth();
            setFixedWidth(textDesiredWidth > nominalWidth ?
                    textDesiredWidth : nominalWidth);
        }
    }
    
    private void setFixedWidth(int w) {
        ViewGroup.LayoutParams params = getLayoutParams();
        params.width = w;
        Paint.FontMetrics fm = getPaint().getFontMetrics();
        params.height = (int) Math.ceil((fm.bottom - fm.top + fm.leading) * nLines)
                        + getPaddingTop() + getPaddingBottom();
        
        setLayoutParams(params);
    }
    
    private String getTextWithHardLineBreaks() {
        Layout layout = getLayout();
        if (layout == null)
            return "";
        String text = layout.getText().toString();
        StringBuilder newText = new StringBuilder();
        int lastLineNumber = 0;
        for (int i=0; i<text.length(); i++) {
            int lineNumber = layout.getLineForOffset(i);
            if (lineNumber != lastLineNumber && text.charAt(i - 1) != '\n')
                newText.append('\n');
            lastLineNumber = lineNumber;
            newText.append(text.charAt(i));
        }
        
        return newText.toString();
    }
    
    private int getUserVisibleWidth() {
        return (int) Math.ceil(Layout.getDesiredWidth(brokenText, new TextPaint(getPaint())))
                + getPaddingLeft() + getPaddingRight();
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
        int[] location = new int[2];
        getLocationOnScreen(location);
        
        float[] vec = new float[2];
        vec[0] = ev.getRawX() - location[0];
        vec[1] = ev.getRawY() - location[1];
        
        Matrix matrix = new Matrix();
        matrix.setRotate(-getRotation());
        matrix.mapVectors(vec);
        
        float localX = vec[0];
        float localY = vec[1];
        
        Layout layout = getLayout();
        int line = layout.getLineForVertical((int) localY);
        
        float leftBound = 0;
        float rightBound = layout.getLineMax(line) + getPaddingLeft() + getPaddingRight();
        float bottomBound = (layout.getLineBottom(layout.getLineCount()-1)
                + layout.getLineDescent(layout.getLineCount()-1));
        float topBound = getPaddingTop();
        
        return localX < rightBound
                && localX > leftBound
                && localY < bottomBound
                && localY > topBound;
    }
    
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (touchIsOnText(ev))
            return super.dispatchTouchEvent(ev);
        else
            return false;
    }
    
    public int getTextColor() {
        if (centerTextView != null)
            return centerTextView.getCurrentTextColor();
        return 0;
    }
    
    @Override
    public void setTextColor(int color) {
        if (centerTextView == null)
            return;
        centerTextView.setTextColor(color);
        invalidate();
    }
    
    public int getOutlineColor() {
        if (outlineTextView != null)
            return outlineTextView.getCurrentTextColor();
        return 0;
    }
    
    public void setOutlineColor(int color) {
        if (outlineTextView == null)
            return;
        outlineTextView.setTextColor(color);
        invalidate();
    }
    
    public void setOnStartEditCallback(onEditCallback callback) {
        onStartEditCallback = callback;
    }
    
    public void setOnStopEditCallback(onEditCallback callback) {
        onStopEditCallback = callback;
    }
    
    public interface onEditCallback {
        void onCall();
    }
    
    private static float makeFractional(float value, int bound1, int bound2) {
        final float span = bound2 - bound1;
        final float x = value - bound1;
        return x / span;
    }
}
