package local.nova.dock;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.util.SparseIntArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** One content envelope for every tile, independent of the currently displayed value or language. */
final class TileSizing {
    private final Context context;
    private final List<Catalog> catalogs=new ArrayList<>();
    private final SparseIntArray heights=new SparseIntArray();

    TileSizing(Context context) {
        this.context=context;
        for (String language:new String[]{"en","ru"}) {
            Configuration configuration=new Configuration(context.getResources().getConfiguration());
            configuration.setLocale(Locale.forLanguageTag(language));
            catalogs.add(new Catalog(context.createConfigurationContext(configuration)));
        }
    }

    int height(int tileWidth) {
        int known=heights.get(tileWidth); if (known!=0) return known;
        int width=Math.max(1,tileWidth-dp(24)),result=dp(94);
        for (Catalog catalog:catalogs) for (Presentation presentation:catalog.presentations) result=Math.max(result,catalog.height(presentation,width));
        heights.put(tileWidth,result); return result;
    }

    private int dp(int value) { return NativeDialog.dp(context,value); }

    /** Main's two weighted cards share the smaller width before either card is measured. */
    static final class Cards extends LinearLayout {
        private final TileSizing sizing;
        Cards(Context context) { super(context); setOrientation(HORIZONTAL); sizing=new TileSizing(context); }
        @Override protected void onMeasure(int widthSpec,int heightSpec) {
            int available=MeasureSpec.getSize(widthSpec)-getPaddingLeft()-getPaddingRight(),count=0;
            for (int i=0;i<getChildCount();i++) {
                View child=getChildAt(i); if (child.getVisibility()==GONE) continue;
                count++;
                ViewGroup.LayoutParams params=child.getLayoutParams();
                if (params instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams margin=(ViewGroup.MarginLayoutParams)params; available-=margin.leftMargin+margin.rightMargin;
                }
            }
            if (count>0) {
                int tileWidth=(available/count-NativeDialog.dp(getContext(),28+8))/2;
                int height=sizing.height(tileWidth);
                for (int i=0;i<getChildCount();i++) if (getChildAt(i) instanceof ProfileCard) ((ProfileCard)getChildAt(i)).tileHeight(height);
            }
            super.onMeasure(widthSpec,heightSpec);
        }
    }

    static final class Presentation {
        final int setting,owner;
        final String primary,secondary,hint;
        Presentation(int setting,int owner,String primary,String secondary,String hint) {
            this.setting=setting; this.owner=owner; this.primary=primary; this.secondary=secondary; this.hint=hint;
        }
    }

    private static final class ActualOutput {
        final GlobalSettings.Snapshot snapshot;
        final String primary,secondary;
        ActualOutput(Context context,GlobalSettings.Snapshot snapshot) {
            this.snapshot=snapshot; primary=GlobalText.compactPrimary(context,snapshot); secondary=GlobalText.compactSecondary(snapshot);
        }
    }

    /** Complete states only: e.g. KEEP has no resolution and cannot have a live saved-value hint. */
    static final class Catalog {
        final Context context;
        final List<Presentation> presentations=new ArrayList<>();
        private final Map<String,Integer> measured=new HashMap<>();
        private final TextView primaryMeasure,detailMeasure;
        Catalog(Context context) {
            this.context=context;
            primaryMeasure=measureView(14,true); detailMeasure=measureView(9,false);
            profile(Profile.RATE,-1,new int[]{60,120},new int[]{60,120});
            add(Profile.RATE,-1,ProfileText.rate(context,0),"","");
            add(Profile.RATE,-1,ProfileText.rate(context,0),"",context.getString(R.string.applying));
            for (int rate:new int[]{60,120,144,240,999}) add(Profile.RATE,-1,ProfileText.rate(context,0),"",context.getString(R.string.current_value,context.getString(R.string.format_rate_hz,rate)));
            profile(Profile.PERFORMANCE,-1,new int[]{0,1,2},new int[]{0,1,2});
            profile(Profile.FAN,-1,new int[]{0,1,4,5,6},new int[]{0,1,4,5,6});
            for (int saved:new int[]{0,1,4,5,6}) add(Profile.FAN,-1,context.getString(R.string.fan_custom),"",context.getString(R.string.saved_value,ProfileText.fan(context,saved)));
            profile(Profile.BYPASS,-1,new int[]{0,1},new int[]{0,1});
            profile(Profile.CONTROLLER,1,new int[]{0,1,2},new int[]{0,1,2});
            profile(Profile.CONTROLLER,0,new int[]{0,1},new int[]{0,1,2});
            List<OutputSpec> savedOutputs=new ArrayList<>(); List<ActualOutput> actualOutputs=new ArrayList<>();
            for (OutputMode.Aspect aspect:OutputMode.Aspect.values()) {
                savedOutputs.add(new OutputSpec(aspect,null));
                for (OutputMode.Preset preset:OutputMode.Preset.values()) {
                    savedOutputs.add(new OutputSpec(aspect,preset)); actualOutputs.add(new ActualOutput(context,snapshot(aspect,preset)));
                }
                actualOutputs.add(new ActualOutput(context,snapshot(aspect,null)));
            }
            for (OutputSpec saved:savedOutputs) {
                String primary=GlobalText.savedPrimary(context,saved),secondary=GlobalText.savedSecondary(saved),savedHint=context.getString(R.string.saved_value,GlobalText.savedSummary(context,saved));
                add(SettingChanges.OUTPUT,-1,primary,secondary,"");
                add(SettingChanges.OUTPUT,-1,primary,secondary,context.getString(R.string.applying));
                add(SettingChanges.OUTPUT,-1,context.getString(R.string.value_unknown),"",savedHint);
                for (ActualOutput actual:actualOutputs) {
                    add(SettingChanges.OUTPUT,-1,actual.primary,actual.secondary,saved.matches(actual.snapshot.aspect,actual.snapshot.preset)?"":savedHint);
                }
            }
            add(SettingChanges.OUTPUT,-1,context.getString(R.string.status_updating),"","");
        }
        private void profile(int setting,int owner,int[] savedValues,int[] liveValues) {
            for (int saved:savedValues) {
                String savedValue=ProfileText.value(context,setting,saved);
                add(setting,owner,savedValue,"","");
                add(setting,owner,savedValue,"",context.getString(R.string.applying));
                for (int live:liveValues) add(setting,owner,ProfileText.value(context,setting,live),"",live==saved?"":context.getString(R.string.saved_value,savedValue));
                add(setting,owner,context.getString(R.string.value_unknown),"",context.getString(R.string.saved_value,savedValue));
            }
        }
        private void add(int setting,int owner,String primary,String secondary,String hint) { presentations.add(new Presentation(setting,owner,primary,secondary,hint)); }
        int height(Presentation presentation,int width) {
            int height=NativeDialog.dp(context,20+23+8)+textHeight(presentation.primary,width,14,true);
            if (!presentation.secondary.isEmpty()) height+=NativeDialog.dp(context,3)+textHeight(presentation.secondary,width,9,false);
            if (!presentation.hint.isEmpty()) height+=NativeDialog.dp(context,3)+textHeight(presentation.hint,width,9,false);
            return height;
        }
        private TextView measureView(int size,boolean bold) {
            TextView text=new TextView(context); text.setTextSize(size); if (bold) text.setTypeface(null,Typeface.BOLD);
            text.setLayoutParams(new ViewGroup.LayoutParams(1,ViewGroup.LayoutParams.WRAP_CONTENT)); return text;
        }
        private int textHeight(String value,int width,int size,boolean bold) {
            String key=width+":"+size+":"+bold+":"+value; Integer cached=measured.get(key); if (cached!=null) return cached;
            TextView text=bold?primaryMeasure:detailMeasure;
            text.getLayoutParams().width=width; text.setText(value);
            text.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
            int height=text.getMeasuredHeight(); measured.put(key,height); return height;
        }
        private static GlobalSettings.Snapshot snapshot(OutputMode.Aspect aspect,OutputMode.Preset preset) {
            Map<VendorBridge.DpProperty,String> properties=new EnumMap<>(VendorBridge.DpProperty.class);
            boolean fixed=preset!=null && preset!=OutputMode.Preset.ADAPTIVE;
            properties.put(VendorBridge.DpProperty.SELECT,preset==OutputMode.Preset.ADAPTIVE?"0":"1");
            properties.put(VendorBridge.DpProperty.WIDTH,fixed?Integer.toString(preset.width(aspect)):"0");
            properties.put(VendorBridge.DpProperty.HEIGHT,fixed?Integer.toString(preset.height(aspect)):"0");
            properties.put(VendorBridge.DpProperty.FPS,fixed?Integer.toString(preset.fps):"0");
            return new GlobalSettings.Snapshot("1",false,new GlobalSettings.WindowSize(aspect.width,aspect.height,aspect.width,aspect.height,false),properties);
        }
    }
}
