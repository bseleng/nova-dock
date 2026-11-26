package local.nova.dock;

import android.content.Context;
import android.graphics.Rect;
import android.hardware.input.InputManager;
import android.view.Choreographer;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.widget.AbsListView;
import android.widget.ScrollView;

/** Scrolls the current window without focus traversal, item selection, or synthetic key presses. */
final class AnalogScroll implements Choreographer.FrameCallback,InputManager.InputDeviceListener,View.OnAttachStateChangeListener {
    private static final int[] NATIVE_AXES={MotionEvent.AXIS_X,MotionEvent.AXIS_Y,MotionEvent.AXIS_HAT_X,MotionEvent.AXIS_HAT_Y};
    private final Window window;
    private final View decor;
    private final Choreographer frames=Choreographer.getInstance();
    private final AnalogScrollState state=new AnalogScrollState();
    private final InputManager input;
    private final float density;
    private final Rect visible=new Rect();
    private View target,previousFocus;
    private boolean scheduled,listening,scrollIntent;
    private int deviceId=Integer.MIN_VALUE;

    AnalogScroll(Window window) {
        this.window=window; decor=window.getDecorView();
        Context context=decor.getContext(); density=context.getResources().getDisplayMetrics().density;
        input=context.getSystemService(InputManager.class);
        decor.addOnAttachStateChangeListener(this);
    }

    boolean motion(MotionEvent event) {
        if ((event.getSource()&InputDevice.SOURCE_CLASS_JOYSTICK)==0) return false;
        if (event.getActionMasked()==MotionEvent.ACTION_CANCEL) {
            boolean handled=state.active() || scrollIntent; cancel(); return handled;
        }
        if (event.getActionMasked()!=MotionEvent.ACTION_MOVE || !decor.hasWindowFocus() || !decor.isAttachedToWindow()) return false;
        InputDevice device=event.getDevice();
        InputDevice.MotionRange range=range(device,MotionEvent.AXIS_RZ,event.getSource());
        int axis=MotionEvent.AXIS_RZ;
        if (range==null) {
            range=range(device,MotionEvent.AXIS_RY,event.getSource());
            if (range!=null) axis=MotionEvent.AXIS_RY;
        }
        // Virtual/injected joystick events need no physical-device MotionRange.
        boolean virtual=device==null || device.isVirtual() || event.getDeviceId()<0;
        if (range==null && !virtual) return false;
        if (range==null && event.getAxisValue(axis)==0 && event.getAxisValue(MotionEvent.AXIS_RY)!=0) axis=MotionEvent.AXIS_RY;
        float value=event.getAxisValue(axis),flat=range==null?0:range.getFlat();
        if (range!=null) {
            float extent=value<0?-range.getMin():range.getMax();
            if (extent>0) { value/=extent; flat/=extent; }
        }
        boolean intent=AnalogScrollState.speed(value,flat)!=0;
        boolean wasActive=state.active();
        if (deviceId!=Integer.MIN_VALUE && event.getDeviceId()!=deviceId) {
            if (!intent) return false;
            stop();
        }
        scrollIntent=intent;
        if (!state.axis(value,flat)) { stop(); return wasActive; }
        deviceId=event.getDeviceId();
        if (!listening && input!=null) { input.registerInputDeviceListener(this,null); listening=true; }
        target=findTarget(state.direction()); previousFocus=window.getCurrentFocus();
        if (target==null) { stop(); return false; }
        post(); return true;
    }
    /** The accepted axis stays held at an edge until neutral or an explicit lifecycle/touch stop. */
    boolean hasScrollIntent() { return scrollIntent; }
    boolean isActive() { return state.active(); }

    /** Forward simultaneous left-stick/HAT motion to Android's normal joystick-to-D-pad path. */
    static boolean nativeAxes(MotionEvent event) {
        for (int axis:NATIVE_AXES) {
            if (event.getAxisValue(axis)!=0) return true;
            for (int h=0;h<event.getHistorySize();h++) if (event.getHistoricalAxisValue(axis,h)!=0) return true;
        }
        return false;
    }

    @Override public void doFrame(long timeNanos) {
        scheduled=false;
        if (!decor.isAttachedToWindow() || !decor.hasWindowFocus()) { cancel(); return; }
        if (!state.active()) { stop(); return; }
        View focus=window.getCurrentFocus();
        if (focus!=previousFocus || !available(target,state.direction())) {
            target=findTarget(state.direction()); previousFocus=focus;
        }
        if (target==null) { stop(); return; }
        int distance=state.frame(timeNanos,density);
        if (distance!=0) {
            if (target instanceof AbsListView) ((AbsListView)target).scrollListBy(distance);
            else target.scrollBy(0,distance);
        }
        if (!available(target,state.direction())) { stop(); return; }
        post();
    }

    void stop() {
        if (scheduled) frames.removeFrameCallback(this);
        scheduled=false; state.stop(); target=null; previousFocus=null;
        if (!scrollIntent) {
            deviceId=Integer.MIN_VALUE;
            if (listening && input!=null) input.unregisterInputDeviceListener(this);
            listening=false;
        }
    }
    void cancel() { scrollIntent=false; stop(); }

    private void post() { if (!scheduled) { scheduled=true; frames.postFrameCallback(this); } }
    private View findTarget(int direction) {
        View focus=window.getCurrentFocus();
        for (View current=focus;current!=null;) {
            if (available(current,direction)) return current;
            ViewParent parent=current.getParent(); current=parent instanceof View?(View)parent:null;
        }
        return largest(decor,direction,null);
    }
    private View largest(View root,int direction,View best) {
        if (!root.isShown()) return best;
        if (available(root,direction) && (best==null || area(root)>area(best))) best=root;
        if (root instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)root;
            for (int i=0;i<group.getChildCount();i++) best=largest(group.getChildAt(i),direction,best);
        }
        return best;
    }
    private boolean available(View view,int direction) {
        return view!=null && (view instanceof ScrollView || view instanceof AbsListView) && view.isAttachedToWindow() && view.isShown()
            && view.getWidth()>0 && view.getHeight()>0 && view.getGlobalVisibleRect(visible)
            && (view instanceof AbsListView?((AbsListView)view).canScrollList(direction):view.canScrollVertically(direction));
    }
    private long area(View view) { return view.getGlobalVisibleRect(visible)?(long)visible.width()*visible.height():0; }
    private static InputDevice.MotionRange range(InputDevice device,int axis,int source) {
        if (device==null) return null;
        InputDevice.MotionRange range=device.getMotionRange(axis,source);
        if (range==null) range=device.getMotionRange(axis);
        return range!=null && range.getMin()<0 && range.getMax()>0?range:null;
    }

    @Override public void onInputDeviceAdded(int id) { }
    @Override public void onInputDeviceChanged(int id) { if (id==deviceId) cancel(); }
    @Override public void onInputDeviceRemoved(int id) { if (id==deviceId) cancel(); }
    @Override public void onViewAttachedToWindow(View view) { }
    @Override public void onViewDetachedFromWindow(View view) { cancel(); }
}
