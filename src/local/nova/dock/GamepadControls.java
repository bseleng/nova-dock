package local.nova.dock;

import android.app.Dialog;
import android.app.AlertDialog;
import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

/** One window-owned key router plus native directional focus and analog scrolling. */
final class GamepadControls {
    private static final View.OnTouchListener TOUCH_FOCUS=(view,event)->{
        // View.onTouchEvent otherwise spends the first UP taking touch-mode focus and skips its click.
        if (event.getActionMasked()==MotionEvent.ACTION_DOWN && view.isEnabled() && view.isClickable()
            && view.isFocusable() && view.isFocusableInTouchMode() && !view.isFocused()) view.requestFocus();
        return false; // Keep native tap, long-press, drag, scrolling interception and cancellation.
    };
    static void focusOnTouch(View view) { view.setOnTouchListener(TOUCH_FOCUS); }
    static void attach(Activity activity,View left,View start,Runnable back) { AnalogScrollWindow.bind(activity.getWindow(),left,start,back); }
    static void attach(Activity activity,HeaderBar header,Runnable back) { attach(activity,header==null?null:header.left,header==null?null:header.right,back); }
    static void attach(Activity activity) { attach(activity,null,null,activity::onBackPressed); }
    static void stop(Activity activity) { AnalogScrollWindow.stop(activity.getWindow()); }
    static void stop(Dialog dialog) { AnalogScrollWindow.stop(dialog.getWindow()); }
    static void clear(Dialog dialog) { AnalogScrollWindow.clear(dialog.getWindow()); }
    static void attach(Dialog dialog) {
        View focus=null;
        if (dialog instanceof AlertDialog) focus=((AlertDialog)dialog).getButton(AlertDialog.BUTTON_NEGATIVE);
        attach(dialog,null,focus);
    }
    static void attach(Dialog dialog,HeaderBar header,View initialFocus) {
        attach(dialog,header==null?null:header.left,header==null?null:header.right,initialFocus);
    }
    static void attach(Dialog dialog,View left,View start,View initialFocus) {
        dialog.setOnKeyListener(null); // Routing lives before native fallback in this window's callback.
        AnalogScrollWindow.bind(dialog.getWindow(),left,start,dialog::cancel);
        if (dialog instanceof AlertDialog) {
            AlertDialog alert=(AlertDialog)dialog;
            for (int id:new int[]{AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL,AlertDialog.BUTTON_POSITIVE}) {
                Button button=alert.getButton(id);
                if (button==null || button.getVisibility()!=View.VISIBLE) continue;
                style(button,0xff283a3d,0xff74e3cc,14);
            }
        }
        View focus=initialFocus==null?start:initialFocus;
        if (focus!=null && focus.isShown() && focus.isEnabled()) { focus.setFocusableInTouchMode(true); focus.requestFocus(); }
    }
    static void cancelHolds(View root) {
        if (root instanceof HoldButton) ((HoldButton)root).cancelHold();
        if (root instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)root;
            for (int i=0;i<group.getChildCount();i++) cancelHolds(group.getChildAt(i));
        }
    }
    static void style(View view,int normal,int accent,int radius) {
        float density=view.getResources().getDisplayMetrics().density;
        GradientDrawable background=shape(normal,radius*density);
        GradientDrawable focused=shape(0xff35494d,radius*density);
        focused.setStroke(Math.round(2*density),accent);
        GradientDrawable selected=shape(0xff305a52,radius*density);
        GradientDrawable selectedFocus=shape(0xff3c7063,radius*density); selectedFocus.setStroke(Math.round(2*density),accent);
        StateListDrawable states=new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_selected,android.R.attr.state_focused},selectedFocus);
        states.addState(new int[]{android.R.attr.state_focused},focused);
        states.addState(new int[]{android.R.attr.state_selected},selected);
        states.addState(new int[]{},background);
        int left=view.getPaddingLeft(), top=view.getPaddingTop(), right=view.getPaddingRight(), bottom=view.getPaddingBottom();
        view.setBackground(new RippleDrawable(ColorStateList.valueOf((accent&0x00ffffff)|0x33000000),states,shape(0xffffffff,radius*density)));
        int horizontal=Math.round(12*density), vertical=Math.round(8*density);
        view.setPadding(Math.max(left,horizontal),Math.max(top,vertical),Math.max(right,horizontal),Math.max(bottom,vertical));
        view.setMinimumHeight(Math.max(view.getMinimumHeight(),Math.round(44*density)));
        if (view instanceof TextView) {
            TextView text=(TextView)view;
            text.setGravity((text.getGravity()&~Gravity.VERTICAL_GRAVITY_MASK)|Gravity.CENTER_VERTICAL);
        }
        view.setFocusable(true);
        focusOnTouch(view);
    }
    private static GradientDrawable shape(int color,float radius) {
        GradientDrawable shape=new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(radius); return shape;
    }
}
