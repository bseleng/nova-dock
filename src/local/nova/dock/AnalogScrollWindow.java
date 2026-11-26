package local.nova.dock;

import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.KeyboardShortcutGroup;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SearchEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import java.util.List;

/** Decor-level motion interception works even when a dialog button or native list has focus. */
final class AnalogScrollWindow implements Window.Callback {
    private final Window.Callback original;
    private final AnalogScroll scroll;
    private final Window window;
    private WindowKeyRouter keys;

    private AnalogScrollWindow(Window window,Window.Callback original) {
        this.window=window; this.original=original; scroll=new AnalogScroll(window);
    }
    static void attach(Window window) {
        if (window==null || window.getCallback() instanceof AnalogScrollWindow) return;
        Window.Callback original=window.getCallback();
        if (original!=null) window.setCallback(new AnalogScrollWindow(window,original));
    }
    static void stop(Window window) {
        if (window!=null && window.getCallback() instanceof AnalogScrollWindow) {
            AnalogScrollWindow callback=(AnalogScrollWindow)window.getCallback();
            callback.scroll.cancel(); if (callback.keys!=null) callback.keys.reset();
            GamepadControls.cancelHolds(window.getDecorView());
        }
    }
    static void bind(Window window,HeaderBar header,Runnable back) {
        bind(window,header==null?null:header.left,header==null?null:header.right,back);
    }
    static void bind(Window window,View left,View start,Runnable back) {
        attach(window);
        if (window!=null && window.getCallback() instanceof AnalogScrollWindow) {
            AnalogScrollWindow callback=(AnalogScrollWindow)window.getCallback();
            if (callback.keys!=null) callback.keys.reset();
            GamepadControls.cancelHolds(window.getDecorView());
            callback.keys=new WindowKeyRouter(window,left,start,back);
        }
    }
    static void clear(Window window) {
        stop(window);
        if (window!=null && window.getCallback() instanceof AnalogScrollWindow) ((AnalogScrollWindow)window.getCallback()).keys=null;
    }
    static boolean isScrolling(Window window) {
        if (window==null || !(window.getCallback() instanceof AnalogScrollWindow)) return false;
        AnalogScroll scroll=((AnalogScrollWindow)window.getCallback()).scroll;
        return scroll.isActive() || scroll.hasScrollIntent();
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        boolean handled=scroll.motion(event);
        if (scroll.hasScrollIntent()) { if (keys!=null) keys.cancelActiveHolds(); else GamepadControls.cancelHolds(window.getDecorView()); }
        boolean delegated=original.dispatchGenericMotionEvent(event);
        return delegated || handled && !AnalogScroll.nativeAxes(event);
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int action=event.getActionMasked(); boolean multiple=event.getPointerCount()>1;
        if (action==MotionEvent.ACTION_DOWN || action==MotionEvent.ACTION_CANCEL || multiple) {
            scroll.cancel(); if (keys!=null) keys.cancelActiveHolds(); else GamepadControls.cancelHolds(window.getDecorView());
        }
        boolean handled=original.dispatchTouchEvent(event);
        // ViewGroup may split a second pointer into a fresh single-pointer DOWN for another child.
        if (multiple) GamepadControls.cancelHolds(window.getDecorView());
        return handled;
    }
    @Override public void onWindowFocusChanged(boolean focused) { if (!focused) stop(window); original.onWindowFocusChanged(focused); }
    @Override public void onDetachedFromWindow() { stop(window); original.onDetachedFromWindow(); }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        // A release delivered while a dismissed window is detaching must not reach native fallback.
        if (keys!=null?keys.dispatch(event):WindowKeyRouter.owns(event.getKeyCode())) return true;
        return original.dispatchKeyEvent(event);
    }
    @Override public boolean dispatchKeyShortcutEvent(KeyEvent event) { return original.dispatchKeyShortcutEvent(event); }
    @Override public boolean dispatchTrackballEvent(MotionEvent event) { return original.dispatchTrackballEvent(event); }
    @Override public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) { return original.dispatchPopulateAccessibilityEvent(event); }
    @Override public View onCreatePanelView(int feature) { return original.onCreatePanelView(feature); }
    @Override public boolean onCreatePanelMenu(int feature,Menu menu) { return original.onCreatePanelMenu(feature,menu); }
    @Override public boolean onPreparePanel(int feature,View view,Menu menu) { return original.onPreparePanel(feature,view,menu); }
    @Override public boolean onMenuOpened(int feature,Menu menu) { return original.onMenuOpened(feature,menu); }
    @Override public boolean onMenuItemSelected(int feature,MenuItem item) { return original.onMenuItemSelected(feature,item); }
    @Override public void onWindowAttributesChanged(WindowManager.LayoutParams parameters) { original.onWindowAttributesChanged(parameters); }
    @Override public void onContentChanged() { original.onContentChanged(); }
    @Override public void onAttachedToWindow() { original.onAttachedToWindow(); }
    @Override public void onPanelClosed(int feature,Menu menu) { original.onPanelClosed(feature,menu); }
    @Override public boolean onSearchRequested() { return original.onSearchRequested(); }
    @Override public boolean onSearchRequested(SearchEvent event) { return original.onSearchRequested(event); }
    @Override public ActionMode onWindowStartingActionMode(ActionMode.Callback callback) { return original.onWindowStartingActionMode(callback); }
    @Override public ActionMode onWindowStartingActionMode(ActionMode.Callback callback,int type) { return original.onWindowStartingActionMode(callback,type); }
    @Override public void onActionModeStarted(ActionMode mode) { original.onActionModeStarted(mode); }
    @Override public void onActionModeFinished(ActionMode mode) { original.onActionModeFinished(mode); }
    @Override public void onProvideKeyboardShortcuts(List<KeyboardShortcutGroup> groups,Menu menu,int device) { original.onProvideKeyboardShortcuts(groups,menu,device); }
    @Override public void onPointerCaptureChanged(boolean captured) { original.onPointerCaptureChanged(captured); }
}
