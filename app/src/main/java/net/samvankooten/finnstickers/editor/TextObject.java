package net.samvankooten.finnstickers.editor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
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
    
    private Bitmap largeBitmap = null;
    private Canvas largeCanvas = null;
    private Bitmap smallBitmap = null;
    private Canvas smallCanvas = null;
    private Matrix bitmapScaleMatrix = new Matrix();
    private AppCompatTextView outlineTextView = null;
    private AppCompatTextView centerTextView = null;
    private int maxWidth;
    private int maxHeight;
    
    private float scale = 1;
    private int baseSize;
    private int basePadding;
    private String originalText;
    
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
        setTextColor(Color.TRANSPARENT);
        setupDrawBackingResources(1, 1);
        
        setImeOptions(getImeOptions() | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        
        setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus)
                onStartEditing();
            else
                onStopEditing();
        });
        
        addTextChangedListener(new TextWatcher() {
            private int nLines;
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (getLayout() == null) {
                    nLines = -1;
                    return;
                }
                nLines = getLayout().getLineCount();
            }
    
            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) { }
    
            @Override
            public void afterTextChanged(Editable editable) {
                // If a line of text was added and that's pushing us over our max size,
                // reduce our text size.
                if (nLines >= 0 && getLayout() != null) {
                    int newNLines = getLayout().getLineCount();
                    if (newNLines != nLines && getLayout().getHeight() > getMaximumHeight()) {
                        scaleWithFixedPos((float) getMaximumHeight() / (getLayout().getHeight()+2*getPaddingTop()));
                    }
                }
                
                setupDrawBackingResources(largeBitmap.getWidth(), largeBitmap.getHeight());
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
        
        int imageHeight = imageBottom - imageTop;
        JSONObject data = new JSONObject();
        try {
            data.put("text", originalText);
            data.put("brokenText", getText());
            data.put("scale", scale);
            data.put("pivotX", getPivotX() / imageHeight);
            data.put("pivotY", getPivotY() / imageHeight);
            data.put("rotation", getRotation());
            data.put("x", makeFractional(getX(), imageLeft, imageRight));
            data.put("y", makeFractional(getY(), imageTop, imageBottom));
            data.put("baseSize", (float) baseSize / imageHeight);
            data.put("basePadding", (float) basePadding / imageHeight);
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
            maxWidth = imageWidth;
            maxHeight = imageHeight;
            baseSize = (int) (imageHeight * data.getDouble("baseSize"));
            basePadding = (int) (imageHeight * data.getDouble("basePadding"));
            originalText = data.getString("text");
            setText(data.getString("brokenText"));
            scale((float) data.getDouble("scale"), true, false);
            setPivotX(imageWidth * (float) data.getDouble("pivotX"));
            setPivotY(imageHeight * (float) data.getDouble("pivotY"));
            setRotation((float) data.getDouble("rotation"));
            setX(imageLeft + imageWidth * (float) data.getDouble("x"));
            setY(imageTop + imageHeight * (float) data.getDouble("y"));
        } catch (JSONException e) {
            Log.e(TAG, "Error loading JSON", e);
        }
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
    
    public void setMaxHeight(int maxHeight) {
        this.maxHeight= maxHeight;
        updateWidth();
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0)
            return;
        setupDrawBackingResources(w, h);
    }
    
    private void setupDrawBackingResources(int width, int height) {
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
        
            centerTextView = new AppCompatTextView(context);
            FrameLayout layout2 = new FrameLayout(context);
            layout2.addView(centerTextView);
            initTextView(centerTextView);
            centerTextView.setTextColor(Color.WHITE);
        }
        
        // We need to do a two-pass text render to get the white text and the black outline.
        // I was getting some funny behavior having just one backing TextView and changing its
        // style for the two renders, so instead we're using two backing TextViews, one for
        // each style.
        outlineTextView.setText(getTextWithHardLineBreaks());
        outlineTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
        outlineTextView.setPadding(
                (int) (ratio * getPaddingLeft()),
                (int) (ratio * getPaddingTop()),
                (int) (ratio * getPaddingRight()),
                (int) (ratio * getPaddingBottom()));
        outlineTextView.setWidth((int)(ratio * width));
        outlineTextView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
    
        Paint paint = outlineTextView.getPaint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.2f * outlineTextView.getTextSize());
        
        
        centerTextView.setText(getTextWithHardLineBreaks());
        centerTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
        centerTextView.setPadding(
                (int) (ratio * getPaddingLeft()),
                (int) (ratio * getPaddingTop()),
                (int) (ratio * getPaddingRight()),
                (int) (ratio * getPaddingBottom()));
        centerTextView.setWidth((int)(ratio * width));
        centerTextView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        
        // This is what we'll upscale the rendered text onto
        if (largeBitmap == null
                || largeBitmap.getWidth() != width
                || largeBitmap.getHeight() != height) {
            largeBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            largeCanvas = new Canvas(largeBitmap);
        }
    
        width = outlineTextView.getMeasuredWidth();
        height = outlineTextView.getMeasuredHeight();
        
        // This is where we'll render text at its native size
        if (smallBitmap == null
                || smallBitmap.getWidth() != width
                || smallBitmap.getHeight() != height) {
            smallBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            smallCanvas = new UnClippableCanvas(smallBitmap);
        }
        
        bitmapScaleMatrix.setScale(
                (float) largeBitmap.getWidth() / smallBitmap.getWidth(),
                (float) largeBitmap.getWidth() / smallBitmap.getWidth());
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        largeBitmap.eraseColor(Color.TRANSPARENT);
        smallBitmap.eraseColor(Color.TRANSPARENT);
        
        outlineTextView.draw(smallCanvas);
        centerTextView.draw(smallCanvas);
        
        largeCanvas.drawBitmap(smallBitmap, bitmapScaleMatrix, null);
        
        canvas.drawBitmap(largeBitmap, 0, 0, null);
        
        // This renders the text cursor, etc, during text editing.
        super.onDraw(canvas);
    }
    
    private void onStartEditing() {
        setBackgroundColor(context.getResources().getColor(R.color.editorTextBackgroundDuringEdit));
        setInputType(buildInputType());
        if (originalText != null) {
            updateWidth();
            setText(originalText);
        }
        
        if (onStartEditCallback != null)
            onStartEditCallback.onCall();
    }
    
    private void onStopEditing() {
        setBackgroundColor(Color.TRANSPARENT);
        if (getText() != null) {
            originalText = getText().toString();
            setText(getTextWithHardLineBreaks());
            updateWidth();
        }
        setInputType(buildInputType() | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    
        if (onStopEditCallback != null)
            onStopEditCallback.onCall();
    }
    
    private int getMaximumHeight() {
        return maxHeight;
    }
    
    @Override
    public void setTextSize(float size) {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
    }
    
    public void scale(float factor) {
        scale(factor, false, false);
    }
    
    private void scaleWithFixedPos(float factor) {
        scale(factor, true, true);
    }
    
    private void scale(float factor, boolean force, boolean fixedPos) {
        // Set a lower limit so objects don't get too small to touch/manipulate.
        // A width of 0 indicates we haven't been laid out or whatever, and we're probably
        // being used in a non-interactive rendering mode.
        if (getWidth() != 0
            && baseSize * scale * factor < context.getResources().getDimension(R.dimen.editor_text_min_size)
            && factor < 1
            && !force)
            return;
        
        // If our height exceeds that of our parent's height, our bottom gets cut off (or rather,
        // we're capped at our parent's height). This doesn't seem to happen if our width
        // becomes too much, so go figure. I think some sort of size cap is good anyway,
        // so I'm using this instead of finding a workaround.
        if (getParent() instanceof View
                && getHeight()*factor >= getMaximumHeight()
                && factor > 1
                && !force)
            return;
        
        scale *= factor;
        setTextSize(baseSize * scale);
        
        int width = getUserVisibleWidth() + getPaddingRight() + getPaddingLeft();
        if (!fixedPos) {
            addDx((width - factor * width) / 2);
            addDy((getHeight() - factor * getHeight()) / 2);
        }
        
        int padding = (int) (basePadding * scale);
        setPadding(padding, padding/2, padding, padding/2);
        updateWidth();
    }
    
    public void rotate(float angle) {
        setPivotX(getUserVisibleWidth() / 2f);
        setPivotY(getHeight() / 2f);
        setRotation(getRotation() + angle);
    }
    
    public void addDx(float dx) {
        setX(getX() + dx);
    }
    
    public void addDy(float dy) {
        setY(getY() + dy);
    }
    
    private void updateWidth() {
        int totalWidth = (int) (maxWidth * scale);
        
        if (getText() == null)
            return;
        
        setFixedWidth(totalWidth);
    }
    
    private void setFixedWidth(int w) {
        ViewGroup.LayoutParams params = getLayoutParams();
        params.width = w;
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        
        setLayoutParams(params);
    }
    
    public String getTextWithHardLineBreaks() {
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
        return (int) Math.ceil(Layout.getDesiredWidth(getTextWithHardLineBreaks(), new TextPaint(getPaint())));
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
