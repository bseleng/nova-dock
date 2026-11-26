package local.nova.dock;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared header actions, sizing and cancellation for application-owned modal windows. */
final class NativeDialog {
    static final int BG=0xff101719,CARD=0xff182427,TEXT=0xffedf6f3,MUTED=0xffa5bab5,ACCENT=0xff74e3cc;
    final Activity activity;
    final Dialog dialog;
    final LinearLayout root;
    final HeaderBar header;
    final Button close;
    private final LinearLayout footer;
    Runnable dismissed;
    NativeDialog(Activity activity,int title,View returnFocus) {
        this(activity,title,returnFocus,false);
    }
    NativeDialog(Activity activity,int title,View returnFocus,boolean headerClose) {
        this.activity=activity; dialog=new Dialog(activity); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        root=column(activity); root.setPadding(dp(activity,18),dp(activity,12),dp(activity,18),dp(activity,16));
        root.setBackground(shape(activity,CARD,22)); root.setClipToOutline(true);
        header=new HeaderBar(activity,activity.getString(title),false,headerClose);
        root.addView(header,new LinearLayout.LayoutParams(-1,-2));
        if (headerClose) {
            header.setRightAction(R.string.action_close,R.id.action_close_dialog,dialog::cancel);
            close=header.right; footer=null;
        } else {
            close=closeButton(activity,dialog::cancel); footer=new LinearLayout(activity); footer.setGravity(Gravity.CENTER);
            footer.addView(close,new LinearLayout.LayoutParams(-2,-2));
        }
        dialog.setOnDismissListener(d->{
            GamepadControls.clear(dialog);
            if (dismissed!=null) dismissed.run();
            if (returnFocus!=null) returnFocus.post(()->{
                if (!activity.isFinishing() && !activity.isDestroyed() && !activity.isChangingConfigurations()
                    && returnFocus.isAttachedToWindow() && returnFocus.isShown() && returnFocus.hasWindowFocus() && returnFocus.isEnabled()) {
                    returnFocus.setFocusableInTouchMode(true); returnFocus.requestFocus();
                }
            });
        });
    }
    void setTitle(CharSequence title) { header.setTitle(title); }
    void setTitle(int resource) { setTitle(activity.getString(resource)); }
    void setLeftAction(int label,int id,Runnable click) { header.setLeftAction(label,id,click); }
    void setLeftHoldAction(int label,int id,Runnable held) { header.setLeftHoldAction(label,id,held); }
    void setHeaderLeft(int label,int id,Runnable click) { setLeftAction(label,id,click); }
    void clearLeftAction() { header.clearLeftAction(); }
    void show(boolean tall,View focus) {
        if (footer!=null && footer.getParent()==null) { LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2); params.topMargin=dp(activity,8); root.addView(footer,params); }
        dialog.setContentView(root); dialog.show();
        Window window=dialog.getWindow(); window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); window.setDimAmount(.65f);
        sizeWindow(dialog,tall); GamepadControls.attach(dialog,header.left,close,focus==null?close:focus);
    }
    static void sizeWindow(Dialog dialog,boolean tall) {
        Window window=dialog.getWindow(); if (window==null) return;
        android.util.DisplayMetrics screen=dialog.getContext().getResources().getDisplayMetrics();
        window.setLayout(Math.min(screen.widthPixels-dp(dialog.getContext(),28),dp(dialog.getContext(),900)),tall?Math.min(screen.heightPixels-dp(dialog.getContext(),56),dp(dialog.getContext(),740)):-2);
    }
    static int dp(android.content.Context c,int value) { return Math.round(value*c.getResources().getDisplayMetrics().density); }
    static LinearLayout column(android.content.Context c) { LinearLayout layout=new LinearLayout(c); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    static TextView text(android.content.Context c,CharSequence value,int size,int color) { TextView text=new TextView(c); text.setText(value); text.setTextSize(size); text.setTextColor(color); return text; }
    static GradientDrawable shape(android.content.Context c,int color,int radius) { GradientDrawable shape=new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(c,radius)); return shape; }
    static Button button(android.content.Context c,int label,int id,Runnable click) {
        Button button=new Button(c); button.setText(label); button.setTextColor(TEXT); button.setTextSize(14); button.setAllCaps(false); if (id!=0) button.setId(id);
        GamepadControls.style(button,0xff283a3d,ACCENT,14); button.setOnClickListener(v->click.run()); return button;
    }
    static Button closeButton(android.content.Context context,Runnable click) {
        Button button=new Button(context); styleCloseButton(button,click); return button;
    }
    static void compactButton(Button button) {
        button.setTextSize(12); button.setMinHeight(dp(button.getContext(),44)); button.setMinimumHeight(dp(button.getContext(),44));
        button.setMinWidth(dp(button.getContext(),64)); button.setMinimumWidth(dp(button.getContext(),64));
        button.setPadding(dp(button.getContext(),10),dp(button.getContext(),5),dp(button.getContext(),10),dp(button.getContext(),5));
    }
    static void styleCloseButton(Button button,Runnable click) {
        button.setText(R.string.action_close); button.setId(R.id.action_close_dialog); button.setAllCaps(false); button.setTextSize(14); button.setTextColor(TEXT);
        button.setGravity(Gravity.CENTER); button.setMinHeight(dp(button.getContext(),48));
        GamepadControls.style(button,0xff283a3d,ACCENT,14); HeaderBar.setKeyIcon(button,R.drawable.ic_key_start); button.setOnClickListener(view->click.run());
    }
}
