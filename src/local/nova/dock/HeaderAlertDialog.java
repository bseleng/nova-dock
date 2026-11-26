package local.nova.dock;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;

/** Native choice lists and positive actions share the same window-scoped header controls. */
final class HeaderAlertDialog {
    final HeaderBar header;
    final AlertDialog.Builder builder;
    AlertDialog dialog;
    Runnable dismissed;
    private final View returnFocus;
    private final Activity owner;

    HeaderAlertDialog(Activity owner,int title,View returnFocus) {
        this.returnFocus=returnFocus; this.owner=owner;
        header=new HeaderBar(owner,owner.getString(title),false);
        builder=new AlertDialog.Builder(owner).setCustomTitle(header);
    }

    AlertDialog show(boolean focusList,int position) {
        builder.setNeutralButton(R.string.action_close,null); dialog=builder.create();
        dialog.setOnDismissListener(closed->{
            GamepadControls.clear(dialog);
            if (dismissed!=null) dismissed.run();
            if (returnFocus!=null) returnFocus.post(()->{
                if (!owner.isFinishing() && !owner.isDestroyed() && !owner.isChangingConfigurations()
                    && returnFocus.isAttachedToWindow() && returnFocus.isShown() && returnFocus.hasWindowFocus() && returnFocus.isEnabled()) {
                    returnFocus.setFocusableInTouchMode(true); returnFocus.requestFocus();
                }
            });
        });
        dialog.show(); NativeDialog.sizeWindow(dialog,false);
        Button close=dialog.getButton(AlertDialog.BUTTON_NEUTRAL); NativeDialog.styleCloseButton(close,dialog::cancel); centerClose(close);
        ListView list=dialog.getListView();
        if (list!=null && position>=0) list.setSelection(position);
        View initial=focusList && list!=null?list:header.left.getVisibility()==View.VISIBLE && header.left.isSelected()?header.left:close;
        GamepadControls.attach(dialog,header.left,close,initial); return dialog;
    }

    private static void centerClose(Button close) {
        if (!(close.getParent() instanceof LinearLayout)) return;
        LinearLayout panel=(LinearLayout)close.getParent(); panel.setGravity(Gravity.CENTER); panel.setOrientation(LinearLayout.HORIZONTAL);
        for (int i=0;i<panel.getChildCount();i++) {
            View sibling=panel.getChildAt(i);
            if (sibling!=close && (!(sibling instanceof Button) || sibling.getVisibility()!=View.VISIBLE)) sibling.setVisibility(View.GONE);
        }
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity=Gravity.CENTER; params.weight=0; close.setLayoutParams(params);
    }

    void dismiss() { if (dialog!=null) dialog.dismiss(); }
}
