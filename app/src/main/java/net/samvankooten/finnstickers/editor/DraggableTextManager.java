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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;

import net.samvankooten.finnstickers.R;

import java.util.LinkedList;
import java.util.List;

class DraggableTextManager extends FrameLayout{
    private static final String TAG = "DraggableTextManager";
    private TextObject activeText;
    private List<TextObject> textObjects = new LinkedList<>();
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
    
    private int imageTop;
    private int imageBottom;
    private int imageLeft;
    private int imageRight;
    
    private boolean keyboardShowing = false;
    private int visibleHeight = 0;
    private float standardY;
    private static final float NO_OFFSET = (float) 9999999.9;
    
    public DraggableTextManager(Context context) {
        super(context);
        init(context);
    }
    
    public DraggableTextManager(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }
    
    public DraggableTextManager(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }
    
    private void init(Context context) {
        this.context = context;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        rotationDetector = new RotationGestureDetector(context, new RotationListener());
    }
    
    @SuppressLint("ClickableViewAccessibility")
    void addText() {
        TextObject text = new TextObject(context);
        textObjects.add(text);
        addView(text);
        final int offset = (int) getResources().getDimension(R.dimen.editor_text_offset);
        text.setMaxWidth(imageRight - imageLeft - 2*offset);
        text.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                text.getViewTreeObserver().removeOnPreDrawListener(this);
                text.setX(getLeft() + offset);
                text.setY(imageTop + 0.25f * (imageBottom - imageTop));
                return true;
            }
        });
        
        selectText(text);
        text.requestFocus();
        showKeyboard(text);
        
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
    
    void unselectText(boolean shouldHideKeyboard) {
        if (activeText != null) {
            activeText.clearFocus();
            if (shouldHideKeyboard) {
                hideKeyboard(activeText);
            }
        }
    }
    
    int pixelsOfTextBelow(int visibleHeight) {
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
                
                if (keyboardShowing)
                    break;
                
                gestureStartedOnText = true;
                
                selectText(text);
                
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
    public boolean onTouchEvent(MotionEvent ev) {
        final int action = ev.getActionMasked();
        
        if (action == MotionEvent.ACTION_DOWN) {
            gestureStartedOnText = false;
            clearFocus();
            return true;
        }
        
        if (activeText == null || !isDragging || !gestureStartedOnText)
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
            if (activeText != null)
                activeText.scale(detector.getScaleFactor());
            return true;
        }
    }
    
    private class RotationListener implements RotationGestureDetector.OnRotationGestureListener {
        @Override
        public boolean onRotate(RotationGestureDetector detector) {
            if (activeText != null)
                activeText.rotate(detector.getRotationDelta());
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
    
    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
    
    public void setImageBounds(int top, int bottom, int left, int right) {
        imageTop = top;
        imageBottom = bottom;
        imageLeft = left;
        imageRight = right;
    }
    
    public Bitmap render(Bitmap background, final int targetW, final int targetH) {
        final int w = imageRight - imageLeft;
        final int h = imageBottom - imageTop;
        
        FrameLayout layout = new FrameLayout(context);
        for (TextObject text : textObjects) {
            TextObject newText = new TextObject(context);
            layout.addView(newText);
            newText.setFixedWidth(text.getWidth());
            newText.setText(text.getText());
            newText.setX(w * text.getFractionalX(imageLeft, imageRight));
            newText.setY(h * text.getFractionalY(imageTop, imageBottom));
            newText.setTextSize(h * text.getFractionalSize(imageTop, imageBottom));
            newText.setRotation(text.getCurrentRotation());
        }
        layout.measure(
                MeasureSpec.makeMeasureSpec(w, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(h, MeasureSpec.AT_MOST));
        layout.layout(0, 0, w, h);
        Bitmap textLayer = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas textCanvas = new Canvas(textLayer);
        layout.draw(textCanvas);
        
        Bitmap output = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888);
        Canvas outCanvas = new Canvas(output);
        
        Matrix matrix = new Matrix();
        matrix.setScale(
                (float) targetW / background.getWidth(),
                (float) targetH / background.getHeight());
        outCanvas.drawBitmap(background, matrix, null);
        
        matrix = new Matrix();
        matrix.setScale(
                (float) targetW / textLayer.getWidth(),
                (float) targetH / textLayer.getHeight());
        outCanvas.drawBitmap(textLayer, matrix, null);
        return output;
    }
}
