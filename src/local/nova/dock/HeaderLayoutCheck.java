package local.nova.dock;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import java.util.Locale;

/** Explicit ADB check of the real header layout; isolated locales/fonts never change device settings. */
final class HeaderLayoutCheck {
    static String run(Context base) {
        if (Looper.myLooper()!=Looper.getMainLooper()) throw new IllegalStateException("Header check needs the main thread");
        int cases=0;
        for (float scale:new float[]{1f,1.15f}) for (String language:new String[]{"en","ru"}) {
            Configuration config=new Configuration(base.getResources().getConfiguration());
            config.fontScale=scale; config.setLocale(Locale.forLanguageTag(language));
            Context c=base.createConfigurationContext(config);
            HeaderBar main=new HeaderBar(c,"nova/dock",true);
            main.setLeftAction(R.string.action_log,R.id.action_log,()->{});
            main.setRightAction(R.string.action_settings,R.id.action_settings,()->{});
            verify(main,Math.max(dp(c,480),c.getResources().getDisplayMetrics().widthPixels)-dp(c,36),true,false); cases++;
            int[] titles={R.string.dialog_settings_title,R.string.history_title,R.string.output_mode_title,
                R.string.output_aspect_title,R.string.output_timing_title,R.string.dialog_language_title,R.string.dialog_access_title};
            for (int title:titles) for (int width:new int[]{520,576}) {
                boolean compact=title==R.string.history_title || title==R.string.dialog_settings_title;
                HeaderBar header=new HeaderBar(c,c.getString(title),false,compact);
                if (compact) header.setRightAction(R.string.action_close,R.id.action_close_dialog,()->{});
                if (title==R.string.history_title) header.setLeftHoldAction(R.string.action_clear,R.id.action_clear_log,()->{});
                if (title==R.string.dialog_settings_title) header.setLeftHoldAction(R.string.action_restore,R.id.action_restore_settings,()->{});
                if (title==R.string.output_timing_title) header.setLeftAction(R.string.value_unchanged,R.id.action_keep_output,()->{});
                verify(header,dp(c,width),false,compact); cases++;
            }
            for (int mode:new int[]{R.string.mode_docked,R.string.mode_handheld}) {
                HeaderBar header=new HeaderBar(c,c.getString(R.string.output_mode_title)+" · "+c.getString(mode),false);
                verify(header,dp(c,520),false,false); cases++;
            }
        }
        return "HeaderLayout=PASS; cases="+cases+"; locales=EN,RU; fonts=100,115; mainCornersPhysical=true; settingsHistoryHeaderClose=true; otherModalFooters=true; titlesCentered=true; completeText=true; dangerSingleLineAndExternalHint=true; actionRowCentered=true; compactIcons14dpGap6=true; regularIcons16dpGap8=true";
    }
    private static void verify(HeaderBar header,int width,boolean main,boolean compact) {
        header.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(main?dp(header.getContext(),52):dp(header.getContext(),300),main?View.MeasureSpec.EXACTLY:View.MeasureSpec.AT_MOST));
        header.layout(0,0,width,header.getMeasuredHeight());
        if (header.getLayoutDirection()!=View.LAYOUT_DIRECTION_LTR) throw new AssertionError("Physical header sides mirrored");
        TextView title=header.titleView;
        if (Math.abs(title.getLeft()+title.getRight()-width)>1) throw new AssertionError("Title is not centered: "+title.getText());
        text(title);
        View rowAction=header.right.getVisibility()==View.VISIBLE?header.right:header.left.getVisibility()==View.VISIBLE?header.left:title;
        if (Math.abs(title.getTop()+title.getBottom()-rowAction.getTop()-rowAction.getBottom())>1) throw new AssertionError("Title is not centered in the action row: "+title.getText());
        if (main || compact) {
            if (header.right.getVisibility()!=View.VISIBLE || header.right.getRight()!=width) throw new AssertionError("Right action is not at the physical right edge");
            text(header.right); icon(header.right,compact);
            if (title.getRight()>header.right.getLeft()) throw new AssertionError("Title overlaps right action: "+title.getText());
        } else if (header.right.getVisibility()!=View.GONE) throw new AssertionError("Noncompact modal Close must stay in the footer");
        if (header.left.getVisibility()==View.VISIBLE) {
            if (header.left.getLeft()!=0 || header.left.getRight()>title.getLeft()) throw new AssertionError("Left action or title overlap");
            text(header.left);
            if (header.left instanceof HoldButton && ((HoldButton)header.left).isHoldRequired()) {
                icon(header.left,compact);
                if (header.left.getText().toString().indexOf('\n')>=0) throw new AssertionError("Danger button must be single-line");
                TextView hint=header.holdHint;
                if (hint.getId()!=R.id.hold_hint || hint.getVisibility()!=View.INVISIBLE || hint.getHeight()<=0) throw new AssertionError("External hold hint did not reserve its unfocused row");
                if (hint.getTop()<header.left.getBottom() || hint.getBottom()>header.getHeight()) throw new AssertionError("External hold hint overlaps the action row or escapes header");
                if (!hint.getText().toString().equals(header.getContext().getString(R.string.hint_hold))) throw new AssertionError("External hold hint is not localized");
                text(hint); hintIcon(hint);
            } else icon(header.left,compact);
        }
    }
    private static void text(TextView text) {
        android.text.Layout layout=text.getLayout();
        if (layout==null || layout.getHeight()>text.getHeight()-text.getCompoundPaddingTop()-text.getCompoundPaddingBottom()) throw new AssertionError("Clipped header text: "+text.getText());
        int last=layout.getLineCount()-1;
        if (last>=0 && (layout.getLineEnd(last)<text.length() || layout.getEllipsisCount(last)!=0)) throw new AssertionError("Truncated header text: "+text.getText());
    }
    private static void icon(TextView button,boolean compact) {
        Drawable image=button.getCompoundDrawables()[0]; int size=dp(button.getContext(),compact?14:16);
        if (image==null || image.getBounds().width()!=size || image.getBounds().height()!=size || button.getCompoundDrawablePadding()!=dp(button.getContext(),compact?6:8)) throw new AssertionError("Header key icon dimensions changed; compact="+compact);
    }
    private static void hintIcon(TextView hint) {
        Drawable image=hint.getCompoundDrawables()[2]; int size=dp(hint.getContext(),14);
        if (image==null || image.getBounds().width()!=size || image.getBounds().height()!=size || hint.getCompoundDrawablePadding()!=dp(hint.getContext(),4)) throw new AssertionError("Hold hint A icon dimensions changed");
    }
    private static int dp(Context c,int value) { return NativeDialog.dp(c,value); }
}
