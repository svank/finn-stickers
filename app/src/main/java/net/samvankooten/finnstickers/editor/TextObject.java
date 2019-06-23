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
    
    private Bitmap topBitmap = null;
    private Canvas topCanvas = null;
    private Bitmap bottomBitmap = null;
    private Canvas bottomCanvas = null;
    private AppCompatTextView bottomTextView = null;
    private int maxWidth;
    
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
        setPadding(basePadding, basePadding, basePadding, basePadding);
        setTextColor(Color.WHITE);
        setupDrawBackingResources(1, 1);
        
        setImeOptions(getImeOptions() | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        
        setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus)
                onStartEditing();
            else
                onStopEditing();
        });
        
        addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) { }
    
            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) { }
    
            @Override
            public void afterTextChanged(Editable editable) {
                setupDrawBackingResources(topBitmap.getWidth(), topBitmap.getHeight());
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
            baseSize = (int) (imageHeight * data.getDouble("baseSize"));
            basePadding = (int) (imageHeight * data.getDouble("basePadding"));
            originalText = data.getString("text");
            setText(data.getString("brokenText"));
            scale((float) data.getDouble("scale"), true);
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
        if (topBitmap == null
                || topBitmap.getWidth() != width
                || topBitmap.getHeight() != height) {
            topBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            topCanvas = new Canvas(topBitmap);
        }
    
        if (bottomBitmap == null
                || bottomBitmap.getWidth() != width
                || bottomBitmap.getHeight() != height) {
            bottomBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bottomCanvas = new UnClippableCanvas(topBitmap);
        }
        
        if (bottomTextView == null) {
            bottomTextView = new AppCompatTextView(context);
            FrameLayout layout = new FrameLayout(context);
            layout.addView(bottomTextView);
            initTextView(bottomTextView);
            bottomTextView.setTextColor(Color.BLACK);
        }
    
        Paint paint = bottomTextView.getPaint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.2f * getTextSize());
        bottomTextView.setText(getTextWithHardLineBreaks());
        bottomTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, getTextSize());
        bottomTextView.setPadding(getPaddingLeft(), getPaddingTop(), getPaddingRight(), getPaddingBottom());
        bottomTextView.setWidth(getWidth());
        bottomTextView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
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
        
        bottomTextView.draw(bottomCanvas);
        
        super.onDraw(topCanvas);
    
        canvas.drawBitmap(bottomBitmap, 0, 0, null);
        canvas.drawBitmap(topBitmap, 0, 0, null);
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
    
    @Override
    public void setTextSize(float size) {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
    }
    
    public void scale(float factor) {
        scale(factor, false);
    }
    
    private void scale(float factor, boolean force) {
        // Set a lower limit so objects don't get too small to touch/manipulate.
        // A width of 0 indicates we haven't been laid out or whatever, and we're probably
        // being used in a non-interactive rendering mode.
        if (getWidth() != 0
            && baseSize * scale * factor < context.getResources().getDimension(R.dimen.editor_text_min_size)
            && factor < 1
            && !force)
            return;
        
        // When the text is too large, draw performance drops and eventually the app crashes.
        // Besides that, emojis don't render at sizes above 256px, which seems to be a
        // long-standing bug.
        // So cap sizes at 256, even though emoji-less text can reasonably go up to 600ish.
        if (baseSize * scale * factor >= 256 && !force)
            return;
        
        if (baseSize * scale * factor < 36 && !force)
            return;
        
        scale *= factor;
        setTextSize(baseSize * scale);
        
        int width = getUserVisibleWidth();
        addDx((width - factor * width) / 2);
        addDy((getHeight() - factor * getHeight()) / 2);
        
        int padding = (int) (basePadding * scale);
        setPadding(padding, padding, padding, padding);
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
//        final String text = getText().toString();
//        if (textIsMultiLine()) {
//            setPaintToOutline();
//            totalWidth = (int) Math.ceil(Layout.getDesiredWidth(text, new TextPaint(getPaint())));
//            totalWidth += getPaddingRight() + getPaddingLeft();
//        }
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
        float rightBound = layout.getLineMax(line) + 2*basePadding*scale;
        float bottomBound = (layout.getLineBottom(layout.getLineCount()-1)
                + layout.getLineDescent(layout.getLineCount()-1));
        float topBound = basePadding*scale;
        
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
