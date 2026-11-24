package local.nova.dock;

import android.app.Dialog;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.KeyEvent;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/** Native directional focus plus explicit Android gamepad confirm/cancel buttons. */
final class GamepadControls {
    static boolean handle(KeyEvent event,View focused,Runnable back) {
        int key=event.getKeyCode();
        if (key!=KeyEvent.KEYCODE_BUTTON_A && key!=KeyEvent.KEYCODE_BUTTON_B) return false;
        if (event.getAction()==KeyEvent.ACTION_UP && !event.isCanceled()) {
            if (key==KeyEvent.KEYCODE_BUTTON_B) back.run();
            else if (focused!=null && focused.isEnabled() && focused.isClickable()) focused.performClick();
        }
        return true;
    }
    static void attach(Dialog dialog) {
        dialog.setOnKeyListener((d,key,event)->handle(event,dialog.getCurrentFocus(),dialog::dismiss));
        if (dialog instanceof AlertDialog) {
            AlertDialog alert=(AlertDialog)dialog;
            Button first=null;
            for (int id:new int[]{AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL,AlertDialog.BUTTON_POSITIVE}) {
                Button button=alert.getButton(id);
                if (button==null || button.getVisibility()!=View.VISIBLE) continue;
                style(button,0xff283a3d,0xff74e3cc,14);
                if (first==null) first=button;
            }
            if (first!=null) { first.setFocusableInTouchMode(true); first.requestFocus(); }
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
    }
    private static GradientDrawable shape(int color,float radius) {
        GradientDrawable shape=new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(radius); return shape;
    }
}
