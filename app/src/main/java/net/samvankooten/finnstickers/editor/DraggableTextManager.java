package net.samvankooten.finnstickers.editor;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedList;
import java.util.List;

public class DraggableTextManager extends FrameLayout{
    private static final String TAG = "DraggableTextManager";
    private TextObject activeText;
    private final List<TextObject> textObjects = new LinkedList<>();
    private Context context;
    private float lastTouchX;
    private float lastTouchY;
    private float firstTouchX;
    private float firstTouchY;
    private int touchSlop;
    private boolean isDragging = false;
    private boolean gestureStartedOnText = false;
    private ScaleGestureDetector scaleDetector;
    private RotationGestureDetector rotationDetector;
    
    private int imageTop = -1;
    private int imageBottom = -1;
    private int imageLeft = -1;
    private int imageRight = -1;
    
    private OnEditCallback onStartEditCallback;
    private OnEditCallback onStopEditCallback;
    
    private boolean keyboardShowing = false;
    private int visibleHeight = 0;
    private float standardY;
    private static final float NO_OFFSET = (float) 9999999.9;
    
    public DraggableTextManager(Context context) {
        super(context);
        init(context, false);
    }
    
    public DraggableTextManager(Context context, boolean isHeadless) {
        super(context);
        init(context, isHeadless);
    }
    
    public DraggableTextManager(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, false);
    }
    
    public DraggableTextManager(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, false);
    }
    
    private void init(Context context, boolean isHeadless) {
        this.context = context;
        
        if (!isHeadless) {
            touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
            scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
            rotationDetector = new RotationGestureDetector(context, new RotationListener());
        }
    }
    
    public JSONObject toJSON() {
        JSONObject data = new JSONObject();
        JSONArray texts = new JSONArray();
        for (TextObject text : textObjects) {
            texts.put(text.toJSON(imageLeft, imageRight, imageTop, imageBottom));
        }
        try {
            data.put("origWidth", getImageWidth());
            data.put("origHeight", getImageHeight());
            data.put("texts", texts);
        } catch (JSONException e) {
            Log.e(TAG, "Error converting DraggableTextManager to JSON list", e);
            return new JSONObject();
        }
        return data;
    }
    
    public void loadJSON(JSONObject data) {
        JSONArray texts;
        try {
            if (imageTop == -1) {
                if (data.has("origWidth")
                        && data.has("origHeight"))
                    setImageBounds(data.getInt("origWidth"),
                            data.getInt("origHeight"));
                else
                    // A sensible guess for stickers saved before we started
                    // recording the image size
                    setImageBounds(1080, 1080);
            }
            
            texts = data.getJSONArray("texts");
            for (int i=texts.length()-1; i>=0; i--) {
                TextObject text = new TextObject(context);
                addView(text);
                setupNewText(text);
                text.loadJSON(texts.getJSONObject(i), imageLeft, imageRight, imageTop, imageBottom);
                
                textObjects.add(0, text);
                text.bringToFront();
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error loading text list from JSON", e);
        }
    }
    
    @SuppressLint("ClickableViewAccessibility")
    void addText() {
        TextObject text = new TextObject(context);
        textObjects.add(text);
        addView(text);
        setupNewText(text);
        text.setImageWidth(imageRight - imageLeft);
        text.setX(imageLeft);
        text.setY(imageTop + 0.25f * (imageBottom - imageTop));
        
        selectText(text);
        text.requestFocus();
        showKeyboard(text);
    }
    
    private void setupNewText(TextObject text) {
        text.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        
        text.setOnStartEditCallback(this::onStartEditing);
        text.setOnStopEditCallback(this::onStopEditing);
    
        text.addTextChangedListener(new TextWatcher() {
            private int size = -1;
            private boolean haveTrimmedSpace = false;
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}
        
            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                // If another line has been added, scroll the screen if the new line is under the
                // keyboard. Wait to do this until the View's new height has been computed.
                text.postDelayed(() -> {
                    int newLineCount = text.getHeight();
                    if (size >= 0 && size != newLineCount)
                        offsetFromKeyboard();
                    size = newLineCount;
                }, 20);
            }
        
            @Override
            public void afterTextChanged(Editable editable) {
                // The cursor won't blink if we start the EditText with "" as the text, so we start
                // with " " instead. Remove that space once real text has been entered.
                if (!haveTrimmedSpace && editable.length() > 1 && editable.charAt(0) == ' ') {
                    editable.delete(0, 1);
                    haveTrimmedSpace = true;
                }
            }
        });
    }
    
    @Override
    public void clearFocus() {
        unselectText(true);
    }
    
    private void selectText(TextObject object) {
        if (object == activeText)
            return;
        unselectText(object == null);
        activeText = object;
        
        if (object != null) {
            textObjects.remove(object);
            textObjects.add(0, object);
            object.bringToFront();
        }
        
        if (keyboardShowing)
            offsetFromKeyboard();
    }
    
    private void unselectText(boolean shouldHideKeyboard) {
        if (activeText != null) {
            activeText.clearFocus();
            if (shouldHideKeyboard) {
                hideKeyboard();
            }
        }
    }
    
    public boolean requestStopEdit() {
        if (activeText != null && activeText.hasFocus()) {
            clearFocus();
            return true;
        }
        return false;
    }
    
    private void onStartEditing() {
        if (onStartEditCallback != null)
            onStartEditCallback.onCall();
    }
    
    private void onStopEditing() {
        if (onStopEditCallback != null)
            onStopEditCallback.onCall();
        
        if (activeText.getText() == null
            || activeText.getText().length() == 0) {
            deleteText(activeText);
            activeText = null;
        }
    }
    
    public void deleteSelectedText() {
        if (activeText != null)
            deleteText(activeText);
        activeText = null;
    }
    
    private void deleteText(TextObject text) {
        clearFocus();
        textObjects.remove(text);
        removeView(text);
    }
    
    public int getSelectedTextColor() {
        if (activeText != null)
            return activeText.getTextColor();
        return 0;
    }
    
    public void setSelectedTextColor(int color) {
        if (activeText != null)
            activeText.setTextColor(color);
    }
    
    public int getSelectedTextOutlineColor() {
        if (activeText != null)
            return activeText.getOutlineColor();
        return 0;
    }
    
    public void setSelectedTextOutlineColor(int color) {
        if (activeText != null)
            activeText.setOutlineColor(color);
    }
    
    private int pixelsOfTextBelow(int visibleHeight) {
        if (activeText == null)
            return 0;
        Rect r = new Rect();
        activeText.getGlobalVisibleRect(r);
        float bottom = activeText.getY() + activeText.getHeight();
        if (r.bottom > bottom)
            bottom = r.bottom;
        return (int) bottom + (int) getY() - visibleHeight;
    }
    
    void notifyKeyboardShowing(int visibleHeight) {
        if (!keyboardShowing || visibleHeight != this.visibleHeight) {
            keyboardShowing = true;
            this.visibleHeight = visibleHeight;
            offsetFromKeyboard();
        }
    }
    
    void notifyKeyboardGone() {
        keyboardShowing = false;
        if (standardY != NO_OFFSET) {
            animate().y(standardY).setDuration(200).start();
            standardY = NO_OFFSET;
        }
    }
    
    private void offsetFromKeyboard() {
        if (keyboardShowing) {
            int coveredText = pixelsOfTextBelow(visibleHeight);
            if (coveredText > 0) {
                // text is covered
                int duration = 200;
                if (standardY == NO_OFFSET) {
                    standardY = getY();
                    duration = 200;
                }
                animate().yBy(-coveredText).setDuration(duration).start();
            }
        }
    }
    
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        final int action = ev.getActionMasked();
        
        switch (action) {
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                gestureStartedOnText = false;
                break;
            
            case MotionEvent.ACTION_DOWN: {
                gestureStartedOnText = false;
                
                TextObject text = null;
                boolean textFound = false;
                for (int i=0; i<textObjects.size(); i++) {
                    text = textObjects.get(i);
                    if (text.touchIsOnText(ev)) {
                        textFound = true;
                        break;
                    }
                }
                
                if (!textFound)
                    return true;
                
                selectText(text);
                
                if (keyboardShowing)
                    break;
                
                gestureStartedOnText = true;
                
                final int pointerIndex = ev.getActionIndex();
                firstTouchX = ev.getX(pointerIndex);
                firstTouchY = ev.getY(pointerIndex);
                lastTouchX = firstTouchX;
                lastTouchY = firstTouchY;
                break;
            }
            
            case MotionEvent.ACTION_POINTER_DOWN: {
                float[] coord = calcAverageLocation(ev);
                lastTouchX = coord[0];
                lastTouchY = coord[1];
                break;
            }
            
            case MotionEvent.ACTION_MOVE: {
                if (!gestureStartedOnText)
                    break;
                
                if (isDragging)
                    return true;
                
                float[] coord = calcAverageLocation(ev);
                final float dx = firstTouchX - coord[0];
                final float dy = firstTouchY - coord[1];
                final double dr = Math.sqrt( Math.pow(dx, 2) + Math.pow(dy, 2));
                
                if (dr > touchSlop) {
                    isDragging = true;
                    unselectText(true);
                    return true;
                }
                break;
            }
        }
        
        return false;
    }
    
    @Override
    public boolean performClick() {
        super.performClick();
        clearFocus();
        return true;
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        final int action = ev.getActionMasked();
        
        if (!isDragging) {
            switch (action) {
                case MotionEvent.ACTION_DOWN: {
                    gestureStartedOnText = false;
                    return true;
                }
                case MotionEvent.ACTION_UP: {
                    performClick();
                }
            }
            return true;
        }
        
        if (activeText == null || !gestureStartedOnText)
            return true;
        
        scaleDetector.onTouchEvent(ev);
        rotationDetector.onTouchEvent(ev);
        
        switch (action) {
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                gestureStartedOnText = false;
                break;
                
            case MotionEvent.ACTION_POINTER_DOWN: {
                // The first pointer is handled in onInterceptTouchEvent
                float[] coord = calcAverageLocation(ev);
                lastTouchX = coord[0];
                lastTouchY = coord[1];
            }
            case MotionEvent.ACTION_MOVE: {
                // Find the average of all pointer locations
                float[] coord = calcAverageLocation(ev);
                
                // Calculate the distance moved
                final float dx = coord[0] - lastTouchX;
                final float dy = coord[1] - lastTouchY;
                
                activeText.addDx(dx);
                activeText.addDy(dy);
                
                // Remember this touch position for the next move event
                lastTouchX = coord[0];
                lastTouchY = coord[1];
                
                break;
            }
            
            case MotionEvent.ACTION_POINTER_UP: {
                float[] coord = calcAverageLocation(ev, ev.getActionIndex());
                lastTouchX = coord[0];
                lastTouchY = coord[1];
                break;
            }
        }
        return true;
    }
    
    private float[] calcAverageLocation(MotionEvent ev) {
        return calcAverageLocation(ev, -1);
    }
    
    private float[] calcAverageLocation(MotionEvent ev, int excludedIndex) {
        final float[] out = new float[2];
        for (int i=0; i<ev.getPointerCount(); i++) {
            if (i != excludedIndex) {
                out[0] += ev.getX(i);
                out[1] += ev.getY(i);
            }
        }
        int pointerCount = ev.getPointerCount();
        if (excludedIndex >= 0)
            pointerCount -= 1;
        out[0] /= pointerCount;
        out[1] /= pointerCount;
        return out;
    }
    
    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            if (activeText == null)
                return true;
            
            // We'll be offsetting the View's location so that, post-scaling,
            // the point initially under the gesture focus is still under
            // the focus
            float xInit = activeText.getX();
            float yInit = activeText.getY();
            
            if (!activeText.scale(detector.getScaleFactor()))
                return true;
            
            activeText.setX(xInit - (detector.getFocusX() - xInit)
                                   * (detector.getScaleFactor()-1));
            activeText.setY(yInit - (detector.getFocusY() - yInit)
                                   * (detector.getScaleFactor()-1));
            return true;
        }
    }
    
    private class RotationListener implements RotationGestureDetector.OnRotationGestureListener {
        private final Matrix matrix = new Matrix();
        @Override
        public boolean onRotate(RotationGestureDetector detector) {
            if (activeText == null)
                return true;
            
            // We want to rotate the TextObject around the midpoint between
            // the user's fingers. Setting the View's pivot point to that
            // position should accomplish that goal, but I'm getting weird
            // jumps in the View's position sometimes when I update that pivot.
            // So instead, we'll record the position of the (0, 0) point of
            // the View before rotation, calculate where that point goes after
            // rotation about both the actual pivot point and what we'd prefer
            // the pivot point to be, and offset the View's location according
            // to the difference between those two locations.
            
            // Record the (0, 0) point
            float x0 = activeText.getX();
            float y0 = activeText.getY();
            
            if (!activeText.rotate(detector.getRotationDelta()))
                return true;
            
            // Calculate where the (0, 0) point is actually going
            matrix.setRotate(detector.getRotationDelta());
            float[] point = new float[2];
            float dx = activeText.getPivotX() + activeText.getX();
            float dy = activeText.getPivotY() + activeText.getY();
            point[0] = x0 - dx;
            point[1] = y0 - dy;
            matrix.mapVectors(point);
            float x2 = point[0] + dx;
            float y2 = point[1] + dy;
            
            // Calculate where we'd prefer the (0, 0) point go
            matrix.setRotate(detector.getRotationDelta());
            dx = detector.getFocusX();
            dy = detector.getFocusY();
            point[0] = x0 - dx;
            point[1] = y0 - dy;
            matrix.mapPoints(point);
            float x1 = point[0] + dx;
            float y1 = point[1] + dy;
            
            activeText.addDx(x1 - x2);
            activeText.addDy(y1 - y2);
            return true;
        }
        
        @Override
        public boolean onRotationBegin(RotationGestureDetector detector) {
            return activeText != null;
        }
        
        @Override
        public void onRotationEnd(RotationGestureDetector detector) {}
    }
    
    private void showKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
    }
    
    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(getWindowToken(), 0);
    }
    
    public void setImageBounds(int top, int bottom, int left, int right) {
        imageTop = top;
        imageBottom = bottom;
        imageLeft = left;
        imageRight = right;
    }
    
    private void setImageBounds(int width, int height) {
        setImageBounds(0, height, 0, width);
    }
    
    public int getImageWidth() {
        return imageRight - imageLeft;
    }
    
    public int getImageHeight() {
        return imageBottom - imageTop;
    }
    
    public static Bitmap render(Context context, JSONObject data) {
        DraggableTextManager manager = new DraggableTextManager(context, true);
    
        manager.loadJSON(data);
        manager.measure(
                MeasureSpec.makeMeasureSpec(manager.getImageWidth(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(manager.getImageHeight(), MeasureSpec.EXACTLY));
        manager.layout(0, 0, manager.getImageWidth(), manager.getImageHeight());
    
        Bitmap bitmap = Bitmap.createBitmap(manager.getImageWidth(), manager.getImageHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        manager.draw(canvas);
        
        return bitmap;
    }
    
    public void setOnStartEditCallback(OnEditCallback callback) {
        onStartEditCallback = callback;
    }
    
    public void setOnStopEditCallback(OnEditCallback callback) {
        onStopEditCallback = callback;
    }
    
    public interface OnEditCallback {
        void onCall();
    }
    
    public List<TextObject> getTextObjects() {
        return textObjects;
    }
    
    public boolean hasEdits() {
        return textObjects.size() > 0;
    }
}
