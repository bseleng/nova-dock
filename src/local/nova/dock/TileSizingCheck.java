package local.nova.dock;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Collections;
import java.util.Locale;

/** Explicit instrumentation helper: native text/layout only; no settings, service, or preference writes. */
final class TileSizingCheck {
    private static final int[][] IDS={
        {R.id.dock_rate,R.id.dock_output,R.id.dock_fan,R.id.dock_performance,R.id.dock_controller,R.id.dock_bypass},
        {R.id.handheld_rate,R.id.handheld_output,R.id.handheld_fan,R.id.handheld_performance,R.id.handheld_controller,R.id.handheld_bypass}
    };

    /** Run through Instrumentation.runOnMainSync. Tests both locales with isolated resource configurations. */
    static String run(Context base) {
        if (Looper.myLooper()!=Looper.getMainLooper()) throw new IllegalStateException("TileSizingCheck needs the main thread");
        StringBuilder report=new StringBuilder(); int states=0;
        for (float scale:new float[]{1f,1.15f}) {
            int commonHeight=0;
            for (String language:new String[]{"en","ru"}) {
                Configuration configuration=new Configuration(base.getResources().getConfiguration());
                configuration.fontScale=scale; configuration.setLocale(Locale.forLanguageTag(language));
                Context context=base.createConfigurationContext(configuration);
                TileSizing.Catalog catalog=new TileSizing.Catalog(context);
                TileSizing.Cards cards=new TileSizing.Cards(context);
                cards.addView(new ProfileCard(context,true,setting->{},output(context,R.id.dock_output)),new LinearLayout.LayoutParams(0,-2,1));
                LinearLayout.LayoutParams second=new LinearLayout.LayoutParams(0,-2,1); second.leftMargin=NativeDialog.dp(context,12);
                cards.addView(new ProfileCard(context,false,setting->{},output(context,R.id.handheld_output)),second);
                int width=context.getResources().getDisplayMetrics().widthPixels-NativeDialog.dp(context,36);
                baseline(cards,context); int height=verify(cards,width,0); states++;
                if (commonHeight!=0 && commonHeight!=height) throw new AssertionError("Language changed the shared tile height");
                commonHeight=height;
                for (TileSizing.Presentation presentation:catalog.presentations) { fill(cards,presentation); verify(cards,width,height); states++; }
                baseline(cards,context); verify(cards,width,height); states++;
                report.append(language).append(" font=").append(scale).append(" heightPx=").append(height).append('\n');
            }
        }
        return report.append("TileSizing=PASS; states=").append(states).append("; realPresentations=true; all12Equal=true; completeText=true; shortLongStable=true").toString();
    }

    private static View output(Context context,int id) {
        LinearLayout tile=new LinearLayout(context); tile.setId(id); tile.setOrientation(LinearLayout.VERTICAL); tile.setGravity(Gravity.CENTER);
        tile.setPadding(NativeDialog.dp(context,12),NativeDialog.dp(context,10),NativeDialog.dp(context,12),NativeDialog.dp(context,10));
        ImageView icon=new ImageView(context); tile.addView(icon,new LinearLayout.LayoutParams(NativeDialog.dp(context,23),NativeDialog.dp(context,23)));
        TextView primary=NativeDialog.text(context,"",14,NativeDialog.TEXT); primary.setGravity(Gravity.CENTER); primary.setTypeface(null,Typeface.BOLD);
        LinearLayout.LayoutParams value=new LinearLayout.LayoutParams(-1,-2); value.topMargin=NativeDialog.dp(context,8); tile.addView(primary,value);
        for (int i=0;i<2;i++) {
            TextView detail=NativeDialog.text(context,"",9,NativeDialog.MUTED); detail.setGravity(Gravity.CENTER); detail.setPadding(0,NativeDialog.dp(context,3),0,0); tile.addView(detail,new LinearLayout.LayoutParams(-1,-2));
        }
        return tile;
    }

    private static void baseline(ViewGroup cards,Context context) {
        ((ProfileCard)cards.getChildAt(0)).update(Profile.defaults(true),false,new RuntimeSettings(Collections.emptyMap()));
        ((ProfileCard)cards.getChildAt(1)).update(Profile.defaults(false),false,new RuntimeSettings(Collections.emptyMap()));
        OutputSpec spec=OutputSpec.defaults(false);
        fill(cards,new TileSizing.Presentation(SettingChanges.OUTPUT,-1,GlobalText.savedPrimary(context,spec),GlobalText.savedSecondary(spec),""));
    }

    private static void fill(ViewGroup cards,TileSizing.Presentation presentation) {
        int index=presentation.setting==Profile.RATE?0:presentation.setting==SettingChanges.OUTPUT?1:presentation.setting==Profile.FAN?2:presentation.setting==Profile.PERFORMANCE?3:presentation.setting==Profile.CONTROLLER?4:5;
        for (int mode=0;mode<IDS.length;mode++) {
            if (presentation.owner!=-1 && presentation.owner!=(mode==0?1:0)) continue;
            ViewGroup tile=cards.findViewById(IDS[mode][index]);
            ((TextView)tile.getChildAt(1)).setText(presentation.primary);
            int detailIndex=2;
            if (index==1) { text((TextView)tile.getChildAt(2),presentation.secondary); detailIndex=3; }
            text((TextView)tile.getChildAt(detailIndex),presentation.hint);
        }
    }

    private static void text(TextView view,String text) { view.setText(text); view.setVisibility(text.isEmpty()?View.GONE:View.VISIBLE); }

    private static int verify(ViewGroup cards,int width,int expected) {
        cards.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        cards.layout(0,0,width,cards.getMeasuredHeight()); int height=expected;
        for (int[] ids:IDS) for (int id:ids) {
            ViewGroup tile=cards.findViewById(id);
            if (height==0) height=tile.getHeight();
            if (tile.getHeight()!=height) throw new AssertionError("Unequal/jumping tile height: "+id);
            for (int i=0;i<tile.getChildCount();i++) {
                View child=tile.getChildAt(i); if (child.getVisibility()!=View.VISIBLE) continue;
                if (child.getTop()<tile.getPaddingTop() || child.getBottom()>height-tile.getPaddingBottom()) throw new AssertionError("Tile content exceeds padding: "+id);
                if (child instanceof TextView) {
                    TextView text=(TextView)child; android.text.Layout layout=text.getLayout();
                    if (layout==null || layout.getHeight()>text.getHeight()-text.getCompoundPaddingTop()-text.getCompoundPaddingBottom()) throw new AssertionError("Clipped text: "+text.getText());
                    if (layout.getLineCount()>0 && (layout.getLineEnd(layout.getLineCount()-1)<text.length() || layout.getEllipsisCount(layout.getLineCount()-1)!=0)) throw new AssertionError("Incomplete text: "+text.getText());
                }
            }
        }
        return height;
    }
}
