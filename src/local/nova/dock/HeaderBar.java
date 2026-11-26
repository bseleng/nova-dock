package local.nova.dock;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

/** Main keeps symmetric edge commands; modal titles expand when their action slots are empty. */
final class HeaderBar extends ViewGroup {
    final Button left,right;
    final TextView titleView,holdHint;
    private final boolean pinnedMain,compact;
    private int sideWidth,titleWidth,rowHeight;

    HeaderBar(Context context,CharSequence title,boolean pinnedMain) {
        this(context,title,pinnedMain,false);
    }
    HeaderBar(Context context,CharSequence title,boolean pinnedMain,boolean compact) {
        super(context); this.pinnedMain=pinnedMain; this.compact=compact;
        setLayoutDirection(LAYOUT_DIRECTION_LTR); setClipChildren(true);
        left=button(context,R.drawable.ic_key_select); right=button(context,R.drawable.ic_key_start);
        titleView=NativeDialog.text(context,title,pinnedMain?28:compact?20:22,NativeDialog.TEXT);
        titleView.setTypeface(null,Typeface.BOLD); titleView.setGravity(Gravity.CENTER);
        titleView.setFocusable(false); titleView.setClickable(false);
        holdHint=NativeDialog.text(context,context.getString(R.string.hint_hold),10,0xffffffff);
        holdHint.setId(R.id.hold_hint); holdHint.setGravity(Gravity.CENTER_VERTICAL); holdHint.setMinHeight(dp(16));
        Drawable confirm=context.getDrawable(R.drawable.ic_key_a).mutate(); confirm.setBounds(0,0,dp(14),dp(14));
        holdHint.setCompoundDrawables(null,null,confirm,null); holdHint.setCompoundDrawablePadding(dp(4));
        holdHint.setFocusable(false); holdHint.setClickable(false); holdHint.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (pinnedMain) { titleView.setMaxLines(1); titleView.setEllipsize(TextUtils.TruncateAt.END); }
        addView(left,new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT));
        addView(titleView,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));
        addView(right,new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT));
        addView(holdHint,new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT));
        left.setOnFocusChangeListener((view,focused)->updateHoldHint());
        clearLeftAction(); right.setVisibility(GONE);
        setMinimumHeight(dp(compact?44:52));
    }

    void setTitle(CharSequence title) { titleView.setText(title); }
    void setLeftAction(int label,int id,Runnable click) { action(left,label,id,click); keyIcon(left,R.drawable.ic_key_select); }
    void setLeftHoldAction(int label,int id,Runnable held) {
        action(left,label,id,()->{}); keyIcon(left,R.drawable.ic_key_select);
        left.setContentDescription(getContext().getString(label)+". "+getContext().getString(R.string.hint_hold)+" A");
        ((HoldButton)left).setHoldAction(held); left.setFocusableInTouchMode(true);
        holdHint.setVisibility(INVISIBLE); updateHoldHint(); requestLayout();
    }
    void setRightAction(int label,int id,Runnable click) { action(right,label,id,click); keyIcon(right,R.drawable.ic_key_start); }
    void clearLeftAction() {
        ((HoldButton)left).clearHoldAction();
        holdHint.setVisibility(GONE);
        left.setOnClickListener(null); left.setClickable(false); left.setEnabled(false); left.setVisibility(GONE);
    }

    private void action(Button button,int label,int id,Runnable click) {
        ((HoldButton)button).clearHoldAction();
        if (button==left) holdHint.setVisibility(GONE);
        button.setText(label);
        if (id!=0) button.setId(id); else if (button.getId()==NO_ID) button.setId(View.generateViewId());
        button.setEnabled(true); button.setVisibility(VISIBLE); button.setOnClickListener(view->click.run());
        button.setContentDescription(getContext().getString(label)); requestLayout();
    }
    private void updateHoldHint() {
        boolean danger=((HoldButton)left).isHoldRequired();
        holdHint.setVisibility(!danger?GONE:left.isFocused() && hasWindowFocus()?VISIBLE:INVISIBLE);
    }
    @Override public void onWindowFocusChanged(boolean focused) { super.onWindowFocusChanged(focused); updateHoldHint(); }
    private Button button(Context context,int iconResource) {
        Button button=new HoldButton(context); button.setAllCaps(false); button.setTextSize(14); button.setTextColor(NativeDialog.TEXT);
        button.setLayoutDirection(LAYOUT_DIRECTION_LTR); button.setGravity(Gravity.CENTER);
        button.setMaxLines(pinnedMain?1:Integer.MAX_VALUE); button.setMinHeight(dp(48));
        GamepadControls.style(button,0xff283a3d,NativeDialog.ACCENT,14);
        if (compact) NativeDialog.compactButton(button);
        keyIcon(button,iconResource);
        return button;
    }
    private void keyIcon(Button button,int resource) {
        setKeyIcon(button,resource);
        if (compact) {
            Drawable icon=button.getCompoundDrawables()[0]; icon.setBounds(0,0,dp(14),dp(14));
            button.setCompoundDrawablePadding(dp(6));
        }
    }
    static void setKeyIcon(Button button,int resource) {
        Drawable icon=button.getContext().getDrawable(resource).mutate(); icon.setTint(NativeDialog.TEXT);
        int size=NativeDialog.dp(button.getContext(),16); icon.setBounds(0,0,size,size);
        button.setCompoundDrawables(icon,null,null,null); button.setCompoundDrawablePadding(NativeDialog.dp(button.getContext(),8));
    }

    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        int width=MeasureSpec.getMode(widthSpec)==MeasureSpec.UNSPECIFIED?dp(400):MeasureSpec.getSize(widthSpec);
        int content=Math.max(0,width-getPaddingLeft()-getPaddingRight());
        int maxHeight=MeasureSpec.getMode(heightSpec)==MeasureSpec.UNSPECIFIED?dp(300):Math.max(0,MeasureSpec.getSize(heightSpec)-getPaddingTop()-getPaddingBottom());
        if (pinnedMain) maxHeight=Math.min(maxHeight,dp(52));
        int childHeight=MeasureSpec.makeMeasureSpec(maxHeight,MeasureSpec.AT_MOST);
        int naturalWidth=MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED),wantedSlot=pinnedMain?dp(140):0;
        if (left.getVisibility()!=GONE) { left.measure(naturalWidth,childHeight); wantedSlot=Math.max(wantedSlot,left.getMeasuredWidth()); }
        if (right.getVisibility()!=GONE) { right.measure(naturalWidth,childHeight); wantedSlot=Math.max(wantedSlot,right.getMeasuredWidth()); }
        sideWidth=Math.min(wantedSlot,Math.max(0,(content-dp(pinnedMain?96:112))/2));
        int gap=sideWidth==0?0:Math.min(dp(8),Math.max(0,(content-2*sideWidth)/2));
        titleWidth=Math.max(0,content-2*sideWidth-2*gap);
        // Slots are symmetric; buttons wrap their icon/text group inside those slots.
        int sideSpec=MeasureSpec.makeMeasureSpec(sideWidth,MeasureSpec.AT_MOST);
        if (left.getVisibility()!=GONE) left.measure(sideSpec,childHeight);
        if (right.getVisibility()!=GONE) right.measure(sideSpec,childHeight);
        titleView.measure(MeasureSpec.makeMeasureSpec(titleWidth,MeasureSpec.EXACTLY),childHeight);
        rowHeight=pinnedMain?dp(52):Math.max(dp(compact?44:52),Math.max(titleView.getMeasuredHeight(),Math.max(left.getVisibility()==GONE?0:left.getMeasuredHeight(),right.getVisibility()==GONE?0:right.getMeasuredHeight())));
        int hintHeight=0;
        if (holdHint.getVisibility()!=GONE) {
            holdHint.measure(MeasureSpec.makeMeasureSpec(content,MeasureSpec.AT_MOST),MeasureSpec.makeMeasureSpec(dp(24),MeasureSpec.AT_MOST));
            hintHeight=dp(8)+holdHint.getMeasuredHeight();
        }
        int desired=rowHeight+hintHeight;
        setMeasuredDimension(resolveSize(width,widthSpec),resolveSize(desired+getPaddingTop()+getPaddingBottom(),heightSpec));
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b) {
        int contentHeight=rowHeight;
        if (left.getVisibility()!=GONE) {
            int y=getPaddingTop()+(contentHeight-left.getMeasuredHeight())/2;
            left.layout(getPaddingLeft(),y,getPaddingLeft()+left.getMeasuredWidth(),y+left.getMeasuredHeight());
        }
        if (right.getVisibility()!=GONE) {
            int x=getWidth()-getPaddingRight()-right.getMeasuredWidth(),y=getPaddingTop()+(contentHeight-right.getMeasuredHeight())/2;
            right.layout(x,y,x+right.getMeasuredWidth(),y+right.getMeasuredHeight());
        }
        int x=getPaddingLeft()+(getWidth()-getPaddingLeft()-getPaddingRight()-titleWidth)/2;
        int y=getPaddingTop()+(contentHeight-titleView.getMeasuredHeight())/2;
        titleView.layout(x,y,x+titleWidth,y+titleView.getMeasuredHeight());
        if (holdHint.getVisibility()!=GONE) {
            int hintX=getPaddingLeft()+Math.max(0,(left.getMeasuredWidth()-holdHint.getMeasuredWidth())/2);
            int hintY=getPaddingTop()+rowHeight+dp(3);
            holdHint.layout(hintX,hintY,hintX+holdHint.getMeasuredWidth(),hintY+holdHint.getMeasuredHeight());
        }
    }
    private int dp(int value) { return NativeDialog.dp(getContext(),value); }
}
