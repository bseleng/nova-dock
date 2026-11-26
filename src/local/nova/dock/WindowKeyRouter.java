package local.nova.dock;

import android.view.KeyEvent;
import android.graphics.Rect;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;

/** Header shortcuts target only the focused window, independently of the currently focused control. */
final class WindowKeyRouter {
    private final Window window;
    private final View left,start;
    private final Runnable back;
    private final PressGate presses=new PressGate();
    private final Rect visible=new Rect();
    private APress aPress;
    private boolean seenA;
    private int lastADevice;
    private long lastADown;

    WindowKeyRouter(Window window,View left,View start,Runnable back) { this.window=window; this.left=left; this.start=start; this.back=back; }

    private static final class APress {
        final View view;
        final int device;
        final long down;
        final boolean hold;
        APress(View view,int device,long down) { this.view=view; this.device=device; this.down=down; hold=view instanceof HoldButton && ((HoldButton)view).isHoldRequired(); }
        boolean matches(KeyEvent event) { return device==event.getDeviceId() && down==event.getDownTime(); }
    }

    static boolean owns(int key) {
        return key==KeyEvent.KEYCODE_BUTTON_START || key==KeyEvent.KEYCODE_BUTTON_SELECT
            || key==KeyEvent.KEYCODE_BUTTON_A || key==KeyEvent.KEYCODE_BUTTON_B;
    }
    boolean dispatch(KeyEvent event) {
        int key=event.getKeyCode();
        if (aPress!=null && (window.getCurrentFocus()!=aPress.view || !usable(aPress.view))) cancelA();
        if (event.getAction()==KeyEvent.ACTION_DOWN && (key==KeyEvent.KEYCODE_BUTTON_START || key==KeyEvent.KEYCODE_BUTTON_B || navigation(key))) cancelActiveHolds();
        if (!owns(key)) return false;
        View decor=window.getDecorView();
        boolean focused=decor.isAttachedToWindow() && decor.isShown() && decor.hasWindowFocus();
        boolean fire=presses.key(key,event.getAction(),event.getDeviceId(),event.getDownTime(),event.getRepeatCount(),event.isCanceled(),focused);
        if (key==KeyEvent.KEYCODE_BUTTON_A) { a(event,fire,focused); return true; }
        if (fire) {
            if (key==KeyEvent.KEYCODE_BUTTON_START) { if (start==null) back.run(); else click(start); }
            else if (key==KeyEvent.KEYCODE_BUTTON_SELECT) {
                if (usable(left) && left instanceof HoldButton && ((HoldButton)left).isHoldRequired()) { left.setFocusableInTouchMode(true); left.requestFocus(); }
                else click(left);
            }
            else if (key==KeyEvent.KEYCODE_BUTTON_B) back.run();
        }
        return true; // Includes repeats, cancellations, orphan UPs and absent/disabled actions.
    }
    void reset() { presses.reset(); cancelA(); }
    void cancelActiveHolds() { cancelA(); GamepadControls.cancelHolds(window.getDecorView()); }
    private void a(KeyEvent event,boolean fire,boolean focused) {
        if (event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0 && !event.isCanceled() && focused) {
            if (aPress!=null || seenA && lastADevice==event.getDeviceId() && lastADown==event.getDownTime()) return;
            seenA=true; lastADevice=event.getDeviceId(); lastADown=event.getDownTime();
            View target=window.getCurrentFocus();
            if (!usable(target)) return;
            aPress=new APress(target,event.getDeviceId(),event.getDownTime());
            if (aPress.hold && !AnalogScrollWindow.isScrolling(window)) ((HoldButton)target).beginControllerHold(aPress.device,aPress.down);
            return;
        }
        if (aPress==null || !aPress.matches(event)) return;
        if (event.getAction()==KeyEvent.ACTION_DOWN && !event.isCanceled()) return; // Repeats never restart a hold.
        APress owner=aPress; aPress=null; // Release ownership before any action changes windows/focus.
        if (owner.hold) ((HoldButton)owner.view).endControllerHold(owner.device,owner.down);
        else if (fire && focused && owner.view==window.getCurrentFocus() && usable(owner.view)) activate(owner.view);
    }
    private void cancelA() {
        APress owner=aPress; aPress=null;
        if (owner!=null && owner.hold) ((HoldButton)owner.view).endControllerHold(owner.device,owner.down);
    }
    private boolean usable(View view) {
        return view!=null && view.isAttachedToWindow() && view.isShown() && view.isEnabled()
            && view.getGlobalVisibleRect(visible) && !visible.isEmpty();
    }
    private static boolean navigation(int key) {
        return key==KeyEvent.KEYCODE_DPAD_UP || key==KeyEvent.KEYCODE_DPAD_DOWN || key==KeyEvent.KEYCODE_DPAD_LEFT || key==KeyEvent.KEYCODE_DPAD_RIGHT
            || key==KeyEvent.KEYCODE_TAB || key==KeyEvent.KEYCODE_PAGE_UP || key==KeyEvent.KEYCODE_PAGE_DOWN || key==KeyEvent.KEYCODE_MOVE_HOME || key==KeyEvent.KEYCODE_MOVE_END;
    }
    private static void click(View view) {
        if (view!=null && view.isAttachedToWindow() && view.isShown() && view.isEnabled() && view.isClickable()) view.performClick();
    }
    private static void activate(View focused) {
        if (focused instanceof AdapterView && focused.isShown() && focused.isEnabled()) {
            AdapterView<?> list=(AdapterView<?>)focused; int position=list.getSelectedItemPosition();
            if (position!=AdapterView.INVALID_POSITION) list.performItemClick(list.getSelectedView(),position,list.getSelectedItemId());
        } else click(focused);
    }
}
