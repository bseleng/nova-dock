package local.nova.dock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.widget.Button;

/** Ordinary Button until configured; dangerous actions require 1500 ms of continuous A or touch. */
final class HoldButton extends Button implements Choreographer.FrameCallback {
    private final HoldState hold=new HoldState();
    private final Paint progressPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip=new Path();
    private final Rect visible=new Rect();
    private Runnable holdAction;
    private boolean scheduled,touchGesture;
    private int pointer=-1;
    private int controllerDevice=-1;
    private long touchPress;
    private final float progressInset;

    HoldButton(Context context) { super(context); progressPaint.setColor(0x2a74e3cc); progressInset=2*getResources().getDisplayMetrics().density; }

    void setHoldAction(Runnable action) { cancelHold(); holdAction=action; if (action!=null) { setClickable(true); setLongClickable(false); } }
    void clearHoldAction() { cancelHold(); holdAction=null; }
    boolean isHoldRequired() { return holdAction!=null; }
    float holdProgress() { return hold.progress(SystemClock.uptimeMillis()); }
    boolean isHolding() { return hold.active(); }

    boolean beginControllerHold(int device,long downTime) {
        if (!isHoldRequired() || !isFocused() || !eligible() || !availableDevice(device)) return false;
        if (hold.owns(HoldState.Input.CONTROLLER,device,downTime)) return true;
        if (hold.input()==HoldState.Input.TOUCH) cancelHold();
        if (!hold.begin(HoldState.Input.CONTROLLER,device,downTime,SystemClock.uptimeMillis())) return false;
        controllerDevice=device;
        setPressed(true); invalidate(); post(); return true;
    }
    void endControllerHold(int device,long downTime) {
        if (hold.end(HoldState.Input.CONTROLLER,device,downTime)) { controllerDevice=-1; finishVisuals(); }
    }
    void cancelHold() {
        hold.cancel(); touchGesture=false; pointer=-1; controllerDevice=-1;
        finishVisuals();
    }
    private void finishVisuals() {
        if (scheduled) Choreographer.getInstance().removeFrameCallback(this);
        scheduled=false; setPressed(false); invalidate();
    }
    private void post() { if (!scheduled && hold.pending()) { scheduled=true; Choreographer.getInstance().postFrameCallback(this); } }

    @Override public void doFrame(long frameTimeNanos) {
        scheduled=false;
        if (!hold.pending() || !eligible() || hold.input()==HoldState.Input.CONTROLLER && (!isFocused() || !availableDevice(controllerDevice))) { cancelHold(); return; }
        boolean fire=hold.tick(SystemClock.uptimeMillis()); invalidate();
        if (fire) {
            Runnable action=holdAction;
            if (action!=null) action.run();
        }
        post(); // pending becomes false on completion; no idle timer or repeated action.
    }
    private boolean eligible() {
        return isAttachedToWindow() && isShown() && hasWindowFocus() && isEnabled() && isClickable()
            && getGlobalVisibleRect(visible) && !visible.isEmpty();
    }
    private static boolean availableDevice(int device) {
        if (device<0) return true; // Android's virtual/injected gamepad events have no physical device.
        InputDevice actual=InputDevice.getDevice(device);
        return actual!=null && (Build.VERSION.SDK_INT<27 || actual.isEnabled());
    }

    @Override public boolean performClick() { return !isHoldRequired() && !touchGesture && super.performClick(); }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!isHoldRequired() && !touchGesture) return super.onTouchEvent(event);
        if (!isEnabled() || !isClickable()) { cancelHold(); return isClickable(); }
        int action=event.getActionMasked();
        if (action==MotionEvent.ACTION_DOWN) {
            if (!eligible() || event.getPointerCount()!=1) { cancelHold(); return true; }
            if (hold.input()==HoldState.Input.CONTROLLER) cancelHold();
            touchGesture=true; pointer=event.getPointerId(0); touchPress=event.getDownTime();
            if (inside(event.getX(),event.getY()) && hold.begin(HoldState.Input.TOUCH,pointer,touchPress,SystemClock.uptimeMillis())) {
                setPressed(true); invalidate(); post();
            }
        } else if (action==MotionEvent.ACTION_POINTER_DOWN || action==MotionEvent.ACTION_CANCEL || action==MotionEvent.ACTION_OUTSIDE) {
            cancelTouch();
        } else if (action==MotionEvent.ACTION_MOVE) {
            int index=event.findPointerIndex(pointer);
            if (!touchGesture || event.getPointerCount()!=1 || index<0 || !inside(event.getX(index),event.getY(index)) || !eligible()) cancelTouch();
        } else if (action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_POINTER_UP) {
            cancelTouch();
        }
        return true; // The parent may still intercept a drag and send ACTION_CANCEL.
    }
    private boolean inside(float x,float y) { return x>=0 && y>=0 && x<getWidth() && y<getHeight(); }
    private void cancelTouch() {
        if (hold.end(HoldState.Input.TOUCH,pointer,touchPress)) finishVisuals();
        touchGesture=false; pointer=-1;
    }
    @Override protected void onFocusChanged(boolean focused,int direction,Rect previous) {
        super.onFocusChanged(focused,direction,previous);
        if (!focused && hold!=null && hold.input()==HoldState.Input.CONTROLLER) cancelHold();
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused); if (!focused && hold!=null) cancelHold();
    }
    @Override protected void onDetachedFromWindow() { cancelHold(); super.onDetachedFromWindow(); }
    @Override protected void onSizeChanged(int width,int height,int oldWidth,int oldHeight) {
        super.onSizeChanged(width,height,oldWidth,oldHeight);
        float radius=12*getResources().getDisplayMetrics().density;
        clip.reset();
        if (width>2*progressInset && height>2*progressInset)
            clip.addRoundRect(progressInset,progressInset,width-progressInset,height-progressInset,radius,radius,Path.Direction.CW);
    }
    @Override protected void onDraw(Canvas canvas) {
        float progress=holdProgress();
        if (progress>0) {
            int saved=canvas.save(); canvas.clipPath(clip);
            canvas.drawRect(progressInset,progressInset,progressInset+(getWidth()-2*progressInset)*progress,getHeight()-progressInset,progressPaint); canvas.restoreToCount(saved);
        }
        super.onDraw(canvas);
    }
}
