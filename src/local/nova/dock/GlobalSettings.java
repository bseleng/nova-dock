package local.nova.dock;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import java.util.EnumMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Typed stock settings. All calls, including reads and original capture, run on NovaDock's worker. */
final class GlobalSettings {
    static final class WindowSize {
        final int physicalWidth,physicalHeight,width,height;
        final boolean overridden;
        WindowSize(int pw,int ph,int width,int height,boolean overridden) {
            physicalWidth=pw; physicalHeight=ph; this.width=width; this.height=height; this.overridden=overridden;
        }
    }
    static final class Snapshot {
        final boolean screenOff,showRefresh;
        final String screenRaw;
        final WindowSize window;
        final OutputMode.Aspect aspect;
        final OutputMode.Preset preset;
        final int outputWidth,outputHeight,outputFps;
        final Map<VendorBridge.DpProperty,String> properties;
        Snapshot(String screenRaw,boolean showRefresh,WindowSize window,Map<VendorBridge.DpProperty,String> properties) {
            this.screenRaw=screenRaw; this.screenOff=!"0".equals(screenRaw); this.showRefresh=showRefresh; this.window=window; this.properties=properties;
            aspect=OutputMode.Aspect.of(window.width,window.height);
            outputWidth=integer(properties.get(VendorBridge.DpProperty.WIDTH)); outputHeight=integer(properties.get(VendorBridge.DpProperty.HEIGHT)); outputFps=integer(properties.get(VendorBridge.DpProperty.FPS));
            preset=OutputMode.Preset.infer(properties.get(VendorBridge.DpProperty.SELECT),outputWidth,outputHeight,outputFps,aspect);
        }
    }
    private static final Pattern PHYSICAL=Pattern.compile("Physical size:\\s*(\\d+)x(\\d+)"), OVERRIDE=Pattern.compile("Override size:\\s*(\\d+)x(\\d+)");
    private final Context context;
    private final VendorBridge bridge;
    GlobalSettings(Context context,VendorBridge bridge) { this.context=context; this.bridge=bridge; }
    Snapshot read() {
        String screen=bridge.screenOff();
        if (screen!=null && !"0".equals(screen) && !"1".equals(screen)) throw new Failure("global_read_failed");
        Map<VendorBridge.DpProperty,String> properties=new EnumMap<>(VendorBridge.DpProperty.class);
        for (VendorBridge.DpProperty property:VendorBridge.DpProperty.values()) properties.put(property,bridge.property(property));
        return new Snapshot(screen,overlay(bridge.refreshOverlay()),window(),properties);
    }
    void screenOff(boolean enabled) {
        String before=bridge.screenOff();
        captureScreen(before);
        bridge.screenOff(enabled);
        if (!(enabled?"1":"0").equals(bridge.screenOff())) throw new Failure("global_write_failed");
        commit(Store.prefs(context).edit().putBoolean("screen_adopted",true));
    }
    void showRefresh(boolean enabled) {
        boolean before=overlay(bridge.refreshOverlay());
        SharedPreferences p=Store.prefs(context);
        if (!p.contains("global_original_overlay")) commit(p.edit().putBoolean("global_original_overlay",before));
        bridge.refreshOverlay(enabled);
        if (overlay(bridge.refreshOverlay())!=enabled) throw new Failure("global_write_failed");
    }
    void adoptScreenOnce() {
        SharedPreferences p=Store.prefs(context);
        if (p.getBoolean("screen_adopted",false)) return;
        String before=bridge.screenOff();
        if (!"1".equals(before)) { captureScreen(before); bridge.screenOff(true); }
        if (!"1".equals(bridge.screenOff())) throw new Failure("global_write_failed");
        commit(p.edit().putBoolean("screen_adopted",true));
    }
    void output(OutputMode.Choice choice) throws Exception {
        output(choice,read());
    }
    void profile(OutputSpec spec) throws Exception {
        Snapshot before=read(); OutputMode.Choice choice=spec.plan(before.aspect,before.preset);
        if (choice!=null) output(choice,before);
    }
    private void output(OutputMode.Choice choice,Snapshot before) throws Exception {
        SharedPreferences p=Store.prefs(context);
        // Stock's aspect preference re-applies a known current preset for the new ratio.
        OutputMode.Preset preset=choice.changeMode?choice.preset:choice.changeAspect?before.preset:null;
        boolean changeMode=choice.changeMode || choice.changeAspect && preset!=null;
        SharedPreferences.Editor originals=p.edit(); boolean changed=false;
        if (choice.changeAspect && !p.getBoolean("global_original_wm_saved",false)) {
            originals.putBoolean("global_original_wm_saved",true).putBoolean("global_original_wm_override",before.window.overridden)
                .putInt("global_original_wm_width",before.window.width).putInt("global_original_wm_height",before.window.height); changed=true;
        }
        if (changeMode && !p.getBoolean("global_original_dp_saved",false)) {
            for (VendorBridge.DpProperty property:VendorBridge.DpProperty.values()) originals.putString("global_original_dp_"+property.name(),before.properties.get(property));
            originals.putBoolean("global_original_dp_saved",true); changed=true;
        }
        if (changed) commit(originals);
        String[] previous=new String[4]; for (VendorBridge.DpProperty property:VendorBridge.DpProperty.values()) previous[property.ordinal()]=before.properties.get(property);
        OutputTransaction.apply(new OutputTransaction.Port() {
            public void window(Integer width,Integer height) { bridge.windowSize(width,height); }
            public void property(OutputTransaction.Property property,String value) { bridge.property(VendorBridge.DpProperty.values()[property.ordinal()],value); }
            public void verify() throws InterruptedException {
                if (choice.changeAspect) {
                    boolean matched=false;
                    for (int attempt=0;attempt<6;attempt++) {
                        WindowSize actual=GlobalSettings.this.window();
                        if (actual.width==choice.aspect.width && actual.height==choice.aspect.height) { matched=true; break; }
                        Thread.sleep(100L);
                    }
                    if (!matched) throw new Failure("global_write_failed");
                }
                if (changeMode && read().preset!=preset) throw new Failure("global_write_failed");
            }
        },new OutputTransaction.Before(before.window.overridden,before.window.width,before.window.height,previous),preset,choice.aspect,changeMode,choice.changeAspect);
    }
    void restoreOriginal() throws Exception {
        SharedPreferences p=Store.prefs(context);
        List<Exception> errors=new ArrayList<>();
        if (p.contains("global_original_screen_present")) attempt(errors,()->bridge.screenOff(p.getBoolean("global_original_screen_present",false)?p.getBoolean("global_original_screen",true):null));
        if (p.contains("global_original_overlay")) attempt(errors,()->bridge.refreshOverlay(p.getBoolean("global_original_overlay",false)));
        if (p.getBoolean("global_original_wm_saved",false)) {
            attempt(errors,()->{ if (p.getBoolean("global_original_wm_override",false)) bridge.windowSize(p.getInt("global_original_wm_width",960),p.getInt("global_original_wm_height",1280)); else bridge.windowSize(null,null); });
        }
        if (p.getBoolean("global_original_dp_saved",false)) for (VendorBridge.DpProperty property:VendorBridge.DpProperty.values()) attempt(errors,()->bridge.property(property,p.getString("global_original_dp_"+property.name(),"")));
        // Original snapshots and screen_adopted deliberately survive Restore.
        if (!errors.isEmpty()) throw errors.get(0);
    }
    void restoreSnapshot(Snapshot snapshot) throws Exception {
        List<Exception> errors=new ArrayList<>();
        attempt(errors,()->bridge.screenOff(snapshot.screenRaw==null?null:"1".equals(snapshot.screenRaw)));
        attempt(errors,()->bridge.refreshOverlay(snapshot.showRefresh));
        attempt(errors,()->{ if (snapshot.window.overridden) bridge.windowSize(snapshot.window.width,snapshot.window.height); else bridge.windowSize(null,null); });
        for (VendorBridge.DpProperty property:VendorBridge.DpProperty.values()) attempt(errors,()->bridge.property(property,snapshot.properties.get(property)));
        if (!errors.isEmpty()) throw errors.get(0);
    }
    private interface Task { void run() throws Exception; }
    private static void attempt(List<Exception> errors,Task task) { try { task.run(); } catch (Exception error) { errors.add(error); } }
    private void captureScreen(String value) {
        if (value!=null && !"0".equals(value) && !"1".equals(value)) throw new Failure("global_read_failed");
        SharedPreferences p=Store.prefs(context);
        if (!p.contains("global_original_screen_present")) commit(p.edit().putBoolean("global_original_screen_present",value!=null).putBoolean("global_original_screen","1".equals(value)));
    }
    private static void commit(SharedPreferences.Editor edit) { if (!edit.commit()) throw new Failure("original_save_failed"); }
    private WindowSize window() {
        return window(bridge.windowSize(),Settings.Global.getString(context.getContentResolver(),"display_size_forced"));
    }
    static WindowSize window(String value,String forced) {
        Matcher physical=PHYSICAL.matcher(value);
        if (!physical.find()) throw new Failure("global_read_failed");
        int pw=integer(physical.group(1)),ph=integer(physical.group(2));
        boolean overridden=forced!=null && !forced.trim().isEmpty();
        if (!overridden) return new WindowSize(pw,ph,pw,ph,false);
        if (!forced.matches("[0-9]+,[0-9]+")) throw new Failure("global_read_failed");
        String[] size=forced.split(",");
        return new WindowSize(pw,ph,integer(size[0]),integer(size[1]),true);
    }
    static boolean overlay(String value) {
        Matcher parcel=Pattern.compile("Parcel\\(\\s*0000000([01])(?:\\s|\\))").matcher(value);
        if (!parcel.find()) throw new Failure("global_read_failed");
        return "1".equals(parcel.group(1));
    }
    private static int integer(String value) { try { return Integer.parseInt(value); } catch (Exception e) { return -1; } }
}
