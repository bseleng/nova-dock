package local.nova.dock;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.function.IntConsumer;

/** Saved profile controls. Decorative artwork stays behind the touch targets. */
final class ProfileCard extends FrameLayout {
    private final int accent;
    private final TextView state;
    private final Tile[] tiles=new Tile[5];

    ProfileCard(Context context,boolean docked,IntConsumer change) {
        super(context);
        accent=docked?0xff74e3cc:0xffbeb1f7;
        setBackground(shape(0xff1a2427,22));
        setClipToOutline(true); setClipChildren(true);
        ImageView art=new ImageView(context);
        art.setImageResource(docked?R.drawable.profile_dock:R.drawable.profile_handheld);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP); art.setAlpha(.105f);
        art.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams illustration=new FrameLayout.LayoutParams(dp(242),dp(220),Gravity.TOP|Gravity.END);
        illustration.topMargin=dp(-30); illustration.rightMargin=dp(-54);
        addView(art,illustration);

        LinearLayout content=column(); content.setPadding(dp(14),dp(16),dp(14),dp(14));
        LinearLayout header=column();
        LinearLayout titleRow=new LinearLayout(context); titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text(docked?"В доке":"В руках",24,0xffedf6f3); title.setTypeface(null,Typeface.BOLD);
        titleRow.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        state=text("Сейчас активен",12,0xfff7fffd); state.setTypeface(null,Typeface.BOLD); state.setPadding(dp(10),dp(5),dp(10),dp(5));
        state.setBackground(shape(docked?0xff2d7c69:0xff705aa8,10)); titleRow.addView(state);
        header.addView(titleRow);
        content.addView(header,new LinearLayout.LayoutParams(-1,dp(54)));

        int[] icons={R.drawable.ic_rate,R.drawable.ic_performance,R.drawable.ic_fan,R.drawable.ic_bypass,R.drawable.ic_controller};
        for (int id=0;id<tiles.length;id++) {
            final int setting=id;
            tiles[id]=new Tile(context,id,icons[id],!(docked && id==Profile.CONTROLLER),()->change.accept(setting));
        }
        for (int row=0;row<2;row++) {
            LinearLayout pair=new LinearLayout(context); pair.setOrientation(LinearLayout.HORIZONTAL); pair.setBaselineAligned(false);
            LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-1,1); pair.addView(tiles[row*2],left);
            LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,-1,1); right.leftMargin=dp(8); pair.addView(tiles[row*2+1],right);
            LinearLayout.LayoutParams line=new LinearLayout.LayoutParams(-1,-2); line.bottomMargin=dp(8); content.addView(pair,line);
        }
        content.addView(tiles[Profile.CONTROLLER],new LinearLayout.LayoutParams(-1,-2));
        addView(content,new FrameLayout.LayoutParams(-1,-2));
    }

    void update(Profile profile,boolean active,RuntimeSettings live) {
        state.setVisibility(active?VISIBLE:GONE);
        for (int id=0;id<tiles.length;id++) {
            int value=active?live.value(profile,id):profile.intValue(id);
            boolean on=id==Profile.RATE || id==Profile.FAN?value!=0:id!=Profile.BYPASS || value==1;
            tiles[id].value(active?live.label(profile,id):profile.value(id),on,active?live.hint(profile,id):"");
        }
    }

    private final class Tile extends LinearLayout {
        private final TextView value;
        private final TextView hint;
        private final ImageView icon;
        private final String label;
        private final boolean editable;
        Tile(Context c,int setting,int resource,boolean editable,Runnable action) {
            super(c); this.editable=editable; label=Profile.settingLabel(setting);
            setMinimumHeight(dp(setting==Profile.CONTROLLER?84:94));
            setOrientation(VERTICAL); setGravity(Gravity.CENTER); setPadding(dp(12),dp(10),dp(12),dp(10));
            GradientDrawable background=shape(editable?0xf228383b:0xc5233033,15);
            GradientDrawable focused=shape(0xff35494d,15); focused.setStroke(dp(2),accent);
            StateListDrawable states=new StateListDrawable(); states.addState(new int[]{android.R.attr.state_focused},focused); states.addState(new int[]{},background);
            setBackground(editable?new RippleDrawable(ColorStateList.valueOf(0x3374e3cc),states,shape(Color.WHITE,15)):background);
            icon=new ImageView(c); icon.setImageResource(resource); addView(icon,new LinearLayout.LayoutParams(dp(23),dp(23)));
            value=text("",14,0xffedf6f3); value.setGravity(Gravity.CENTER); value.setTypeface(null,Typeface.BOLD);
            LinearLayout.LayoutParams valueLayout=new LinearLayout.LayoutParams(-1,-2); valueLayout.topMargin=dp(8); addView(value,valueLayout);
            hint=text("",9,0xffa5bab5); hint.setGravity(Gravity.CENTER); hint.setPadding(0,dp(3),0,0); hint.setVisibility(GONE);
            addView(hint,new LinearLayout.LayoutParams(-1,-2));
            for (int i=0;i<getChildCount();i++) getChildAt(i).setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            if (editable) { setOnClickListener(v->action.run()); setFocusable(true); }
            setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override public void onInitializeAccessibilityNodeInfo(View host,AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host,info);
                    info.setClassName(editable?Button.class.getName():TextView.class.getName());
                }
            });
        }
        void value(String text,boolean on,String detail) {
            value.setText(text);
            hint.setText(detail); hint.setVisibility(detail.isEmpty()?GONE:VISIBLE);
            icon.setImageTintList(ColorStateList.valueOf(editable && on?accent:0xff91a7a2));
            setContentDescription(label+": "+text+(detail.isEmpty()?"":". "+detail)+(editable?". Нажмите, чтобы переключить":". В доке контроллер отключается"));
        }
    }
    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout l=new LinearLayout(getContext()); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private TextView text(String s,int size,int color) { TextView t=new TextView(getContext()); t.setText(s); t.setTextSize(size); t.setTextColor(color); return t; }
    private GradientDrawable shape(int color,int radius) { GradientDrawable s=new GradientDrawable(); s.setColor(color); s.setCornerRadius(dp(radius)); return s; }
}
