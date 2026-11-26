package local.nova.dock;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.Display;
import android.view.InputDevice;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;

/** Explicit ADB-only instrumentation; no exported activity or broadcast can force a dock profile. */
public final class DeviceCheck extends Instrumentation {
    private Bundle arguments;
    @Override public void onCreate(Bundle b) { arguments=b; start(); }
    @Override public void onStart() {
        if ("true".equals(arguments.getString("headerLayout"))) {
            Bundle result=new Bundle(); final String[] report=new String[1]; final Throwable[] failure=new Throwable[1]; int code=-1;
            try {
                runOnMainSync(()->{ try { report[0]=HeaderLayoutCheck.run(getTargetContext()); } catch (Throwable error) { failure[0]=error; } });
                if (failure[0]!=null) throw failure[0];
            }
            catch (Throwable error) { code=0; report[0]="HeaderLayout=FAIL: "+error; }
            result.putString("stream",report[0]+"\n"); finish(code,result); return;
        }
        if ("true".equals(arguments.getString("analogUi"))) {
            AnalogUiCheck.run(this,"true".equals(arguments.getString("holdOnly"))); return;
        }
        if ("true".equals(arguments.getString("tileSizing"))) {
            Bundle result=new Bundle(); final String[] report=new String[1]; final Throwable[] failure=new Throwable[1]; int code=-1;
            try {
                runOnMainSync(()->{ try { report[0]=TileSizingCheck.run(getTargetContext()); } catch (Throwable error) { failure[0]=error; } });
                if (failure[0]!=null) throw failure[0];
            }
            catch (Throwable error) { code=0; report[0]="TileSizing=FAIL: "+error; }
            result.putString("stream",report[0]+"\n"); finish(code,result); return;
        }
        synchronized (DockService.hardware) { runChecks(); }
    }
    private void runChecks() {
        Bundle result=new Bundle(); StringBuilder report=new StringBuilder();
        Context c=getTargetContext(); Retroid r=new Retroid(c);
        Map<String,String> original=r.snapshot(); boolean wasEnabled=Store.enabled(c), wasManaged=Store.managed(c);
        Map<String,?> originalPreferences=Store.prefs(c).getAll();
        GlobalSettings globalSettings=new GlobalSettings(c,r.bridge);
        GlobalSettings.Snapshot originalGlobals=null;
        boolean changed=false;
        int code=-1;
        try {
            report.append("model=").append(android.os.Build.MODEL).append("\nvendorServiceAvailable=").append(r.permitted());
            report.append("\ndefaultDockBypass=").append(Profile.defaults(true).bypassCharging?1:0)
                .append("; defaultHandheldBypass=").append(Profile.defaults(false).bypassCharging?1:0)
                .append("; startOnBoot=").append(Store.startOnBoot(c))
                .append("; managed=").append(wasManaged).append("; automatic=").append(wasEnabled)
                .append("; manualDocked=").append(Store.manualDocked(c));
            report.append("\nvendorBridge=").append(new VendorBridge().probe());
            Detector.Sample sample=Detector.sample(c); report.append("\ndisplayConnected=").append(sample.connected).append("; source=").append(sample.source);
            if (!r.supported() || sample.connected==null) throw new IllegalStateException("Diagnostic prerequisites failed");
            boolean profiles="true".equals(arguments.getString("profiles")),globals="true".equals(arguments.getString("globals")),profileOutputs="true".equals(arguments.getString("profileOutputs")),profileControllers="true".equals(arguments.getString("profileControllers"));
            if (profiles || globals || profileOutputs || profileControllers) {
                if (!r.permitted()) throw new IllegalStateException("Retroid service unavailable");
                if (Boolean.TRUE.equals(sample.connected)) throw new IllegalStateException("Unplug external display before profile self-test");
                changed=true;
                Store.stop(c); c.stopService(new Intent(c,DockService.class)); Thread.sleep(1500);
            }
            if (globals || profileOutputs) originalGlobals=globalSettings.read();
            if (globals) {
                globalSettings.screenOff(false); if (globalSettings.read().screenOff) throw new IllegalStateException("Global screen OFF mismatch");
                globalSettings.screenOff(true); if (!globalSettings.read().screenOff) throw new IllegalStateException("Global screen ON mismatch");
                report.append("\nglobalScreen=PASS");
                globalSettings.showRefresh(false); if (globalSettings.read().showRefresh) throw new IllegalStateException("Refresh overlay OFF mismatch");
                globalSettings.showRefresh(true); if (!globalSettings.read().showRefresh) throw new IllegalStateException("Refresh overlay ON mismatch");
                report.append("\nglobalRefreshOverlay=PASS");
                globalSettings.output(new OutputMode.Choice(OutputMode.Preset.P60,OutputMode.Aspect.SIXTEEN_NINE,true,true));
                GlobalSettings.Snapshot selected=globalSettings.read();
                if (selected.aspect!=OutputMode.Aspect.SIXTEEN_NINE || selected.preset!=OutputMode.Preset.P60) throw new IllegalStateException("Stock 1080P60/aspect mismatch");
                globalSettings.output(new OutputMode.Choice(OutputMode.Preset.UHD60,OutputMode.Aspect.SIXTEEN_NINE,true,false));
                if (globalSettings.read().preset!=OutputMode.Preset.UHD60) throw new IllegalStateException("Stock 4K60 mismatch");
                report.append("\nglobalOutput=PASS; tested=1080P60,4K60; aspect=16:9");
                globalSettings.output(new OutputMode.Choice(OutputMode.Preset.ADAPTIVE,OutputMode.Aspect.SIXTEEN_NINE,true,false));
                if (globalSettings.read().preset!=OutputMode.Preset.ADAPTIVE) throw new IllegalStateException("Adaptive output mismatch");
                report.append("\nglobalAdaptive=PASS");
                int nativeChoices=0;
                for (OutputMode.Aspect aspect:OutputMode.Aspect.values()) for (OutputMode.Preset preset:OutputMode.Preset.values()) {
                    globalSettings.output(new OutputMode.Choice(preset,aspect,true,true));
                    GlobalSettings.Snapshot actual=globalSettings.read();
                    if (actual.aspect!=aspect || actual.preset!=preset) throw new IllegalStateException("Native output choice mismatch: "+aspect+"/"+preset);
                    if (preset==OutputMode.Preset.ADAPTIVE) {
                        if (!"0".equals(actual.properties.get(VendorBridge.DpProperty.SELECT))) throw new IllegalStateException("Adaptive did not select stock automatic output");
                    } else if (actual.outputWidth!=preset.width(aspect) || actual.outputHeight!=preset.height(aspect) || actual.outputFps!=preset.fps) {
                        throw new IllegalStateException("Native timing values mismatch: "+aspect+"/"+preset);
                    }
                    nativeChoices++;
                }
                report.append("\nnativePresets=PASS; choices=").append(nativeChoices);
            }
            if (profileOutputs) {
                globalSettings.output(new OutputMode.Choice(OutputMode.Preset.P60,OutputMode.Aspect.FOUR_THREE,true,true));
                GlobalSettings.Snapshot beforeSave=globalSettings.read();
                OutputProfiles.save(c,true,new OutputSpec(OutputMode.Aspect.SIXTEEN_NINE,null));
                OutputProfiles.save(c,false,new OutputSpec(OutputMode.Aspect.FOUR_THREE,null));
                GlobalSettings.Snapshot afterSave=globalSettings.read();
                if (!sameGlobals(beforeSave,afterSave)) throw new IllegalStateException("Inactive profile output save changed native settings");
                report.append("\ninactiveOutputSave=PASS; nativeWrites=none");
                r.apply(true,false); globalSettings.profile(OutputProfiles.get(c,true)); Thread.sleep(900L);
                GlobalSettings.Snapshot dock=globalSettings.read();
                if (!r.matches(true) || dock.aspect!=OutputMode.Aspect.SIXTEEN_NINE || dock.preset!=OutputMode.Preset.P60
                    || dock.outputWidth!=1920 || dock.outputHeight!=1080) throw new IllegalStateException("Dock profile 16:9/KEEP transition mismatch");
                report.append("\nprofileDockOutput=PASS; aspect=16:9; timing=KEEP; resolved=1920x1080@60");
                r.apply(false,false); globalSettings.profile(OutputProfiles.get(c,false)); Thread.sleep(900L);
                GlobalSettings.Snapshot hand=globalSettings.read();
                if (!r.matches(false) || hand.aspect!=OutputMode.Aspect.FOUR_THREE || hand.preset!=OutputMode.Preset.P60
                    || hand.outputWidth!=1280 || hand.outputHeight!=960) throw new IllegalStateException("Handheld profile 4:3/KEEP transition mismatch");
                report.append("\nprofileHandheldOutput=PASS; aspect=4:3; timing=KEEP; resolved=1280x960@60");
                Map<String,String> beforePartial=r.snapshot();
                globalSettings.profile(new OutputSpec(OutputMode.Aspect.SIXTEEN_NINE,null)); Thread.sleep(600L);
                if (!beforePartial.equals(r.snapshot()) || globalSettings.read().aspect!=OutputMode.Aspect.SIXTEEN_NINE) throw new IllegalStateException("Partial output apply changed unrelated profile settings");
                report.append("\npartialOutputOnly=PASS; nineProfileKeys=unchanged");
            }
            if (profileControllers) verifyControllerProfiles(c,r,report);
            if (profiles) {
                verifyOriginalMigration(c,r);
                report.append("\noriginalSnapshotMigration=PASS");
                float beforeHz=c.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY).getRefreshRate();
                r.apply(true,false); Thread.sleep(1800);
                if (!r.matches(true)) throw new IllegalStateException("Dock settings were overridden: "+r.snapshot());
                float hz=c.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY).getRefreshRate();
                int expected=Profiles.get(c,true).rate;
                if (Math.round(hz)!=(expected==0?Math.round(beforeHz):expected)) throw new IllegalStateException("Actual dock refresh rate="+hz);
                report.append("\ndock=PASS; actualHz=").append(hz).append("; fanDuty=").append(Detector.read("/sys/class/gpio5_pwm2/duty")).append("; pads=").append(pads());
                report.append("; bypass=").append(checkBypass(c,r,true));
                Store.log(c,"Самопроверка: "+Profiles.get(c,true).status());
                Thread.sleep(2200);
                beforeHz=hz; r.apply(false,false); Thread.sleep(1800);
                if (!r.matches(false)) throw new IllegalStateException("Handheld settings were overridden: "+r.snapshot());
                hz=c.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY).getRefreshRate();
                expected=Profiles.get(c,false).rate;
                if (Math.round(hz)!=(expected==0?Math.round(beforeHz):expected)) throw new IllegalStateException("Actual handheld refresh rate="+hz);
                report.append("\nhandheld=PASS; actualHz=").append(hz).append("; fanDuty=").append(Detector.read("/sys/class/gpio5_pwm2/duty")).append("; pads=").append(pads());
                report.append("; bypass=").append(checkBypass(c,r,false));
                Store.log(c,"Самопроверка: "+Profiles.get(c,false).status());
                if ("true".equals(arguments.getString("customProfiles"))) {
                    String min=r.get("min_refresh_rate"), peak=r.get("peak_refresh_rate");
                    Profiles.save(c,new Profile(true,0,4,1,2,false));
                    r.apply(true,false); Thread.sleep(1800);
                    if (!r.matches(true) || !min.equals(r.get("min_refresh_rate")) || !peak.equals(r.get("peak_refresh_rate"))) throw new IllegalStateException("Custom dock or keep-rate check failed");
                    report.append("\ncustomDock=PASS; rate=unchanged; fan=Smart; performance=Performance; pads=").append(pads());
                    report.append("; bypass=").append(checkBypass(c,r,true));
                    Profiles.save(c,new Profile(false,60,5,1,1,true));
                    r.apply(false,false); Thread.sleep(1800);
                    if (!r.matches(false) || Math.round(c.getSystemService(DisplayManager.class).getDisplay(0).getRefreshRate())!=60) throw new IllegalStateException("Custom handheld check failed");
                    report.append("\ncustomHandheld=PASS; rate=60; fan=Sport; performance=Performance; layout=Retro; pads=").append(pads());
                    report.append("; bypass=").append(checkBypass(c,r,false));
                }
            }
            report.append("\nPASS");
        } catch (Exception e) { report.append("\nFAIL: ").append(e); code=0; }
        finally {
            if (changed) {
                if (originalGlobals!=null) try {
                    globalSettings.restoreSnapshot(originalGlobals); Thread.sleep(300L);
                    GlobalSettings.Snapshot restored=globalSettings.read();
                    if (!sameGlobals(originalGlobals,restored))
                        throw new IllegalStateException("External globals were not restored exactly");
                    report.append("\nexternalGlobalsRestored=true");
                } catch (Exception e) { report.append("\nglobalRestoreFailed=").append(e); code=0; }
                try {
                    r.restore(original); Thread.sleep(1200L);
                    for (Map.Entry<String,String> entry:original.entrySet()) if (!Objects.equals(entry.getValue(),r.get(entry.getKey()))) throw new IllegalStateException("Original value not restored: "+entry.getKey());
                    report.append("\noriginalSettingsRestored=true");
                }
                catch (Exception e) { report.append("\nrestoreFailed=").append(e); code=0; }
                try {
                    restorePreferences(c,originalPreferences);
                    if (!originalPreferences.equals(Store.prefs(c).getAll())) throw new IllegalStateException("Preference snapshot mismatch");
                    report.append("\npreferencesRestored=true");
                } catch (Exception e) { report.append("\npreferencesRestoreFailed=").append(e); code=0; }
                if (wasManaged) report.append("\nreopenNovaDockToResumeSession=true; automatic=").append(wasEnabled);
            }
            result.putString("stream", report.toString()+"\n"); finish(code,result);
        }
    }
    private static boolean sameGlobals(GlobalSettings.Snapshot a,GlobalSettings.Snapshot b) {
        return Objects.equals(a.screenRaw,b.screenRaw) && a.showRefresh==b.showRefresh && a.window.overridden==b.window.overridden
            && a.window.width==b.window.width && a.window.height==b.window.height && a.properties.equals(b.properties);
    }
    private static void verifyControllerProfiles(Context c,Retroid retroid,StringBuilder report) throws InterruptedException {
        SharedPreferences preferences=Store.prefs(c); Map<String,?> before=preferences.getAll();
        Profile dock=new Profile(true,0,4,1,2,false),handheld=new Profile(false,60,5,1,1,true);
        try {
            Profiles.save(c,dock); Profiles.save(c,handheld);
            if (!preferences.edit().remove("profile_dock_layout").commit()) throw new IllegalStateException("Could not prepare legacy Dock profile");
            Map<String,Integer> legacy=Profiles.snapshot(c);
            if (!legacy.containsKey("profile_dock_layout") || legacy.get("profile_dock_layout")!=null) throw new IllegalStateException("Controller snapshot must preserve an absent Dock layout");
            Map<String,?> absent=preferences.getAll();
            if (!sameProfile(dock,Profiles.get(c,true)) || !absent.equals(preferences.getAll())) throw new IllegalStateException("Reading legacy Dock layout changed its other fields or wrote preferences");
            for (int invalid:new int[]{-1,3,99}) {
                if (!preferences.edit().putInt("profile_dock_layout",invalid).commit()) throw new IllegalStateException("Could not prepare invalid Dock controller");
                verifyLayoutFallback(c,dock);
            }
            if (!preferences.edit().putString("profile_dock_layout","0").commit()) throw new IllegalStateException("Could not prepare non-integer Dock controller");
            verifyLayoutFallback(c,dock);
            Profiles.restore(c,legacy);
            if (preferences.contains("profile_dock_layout") || !sameProfile(dock,Profiles.get(c,true))) throw new IllegalStateException("Profile restore did not retain legacy Dock layout absence");
            Profile handFallback=new Profile(false,handheld.rate,handheld.fan,handheld.performance,0,handheld.bypassCharging);
            if (!preferences.edit().putInt("profile_handheld_layout",2).commit()) throw new IllegalStateException("Could not prepare invalid Handheld controller");
            verifyLayoutFallback(c,handFallback);
            if (!preferences.edit().putString("profile_handheld_layout","bad").commit()) throw new IllegalStateException("Could not prepare non-integer Handheld controller");
            verifyLayoutFallback(c,handFallback); Profiles.save(c,handheld);
            report.append("\ncontrollerStorageMigration=PASS; missingDock=Off; invalidLayoutOnlyFallback=true; readWrites=none");
            for (int layout:new int[]{0,1,2}) {
                Profile selected=new Profile(true,dock.rate,dock.fan,dock.performance,layout,dock.bypassCharging); Profiles.save(c,selected);
                if (!sameProfile(selected,Profiles.get(c,true)) || !sameProfile(handheld,Profiles.get(c,false))) throw new IllegalStateException("Dock controller did not round-trip independently: "+layout);
                if (!retroid.apply(true,false) || !retroid.matches(true)) throw new IllegalStateException("Full Dock controller application failed: "+layout);
                verifyController(retroid,layout);
            }
            report.append("\ndockControllerFull=PASS; choices=Xbox,Retro,Off");
            for (boolean docked:new boolean[]{true,false}) {
                Profile fixture=docked?dock:handheld;
                for (int layout:docked?new int[]{0,1,2}:new int[]{0,1}) {
                    Profile selected=new Profile(docked,fixture.rate,fixture.fan,fixture.performance,layout,fixture.bypassCharging); Profiles.save(c,selected);
                    if (!sameProfile(selected,Profiles.get(c,docked))) throw new IllegalStateException("Controller did not round-trip: "+docked+"/"+layout);
                    Map<String,String> previous=retroid.snapshot();
                    if (!retroid.applySetting(selected,Profile.CONTROLLER,()->true,()->false)) throw new IllegalStateException("Partial controller application was ignored: "+docked+"/"+layout);
                    verifyController(retroid,layout);
                    Map<String,String> actual=retroid.snapshot();
                    for (String key:Retroid.KEYS) if (!"temp_abxy_layout_mode".equals(key) && !"no_create_gamepad_button_layout".equals(key) && !"flip_button_layout".equals(key)
                        && !Objects.equals(previous.get(key),actual.get(key))) throw new IllegalStateException("Controller changed unrelated native setting: "+key);
                }
            }
            report.append("\ncontrollerPartial=PASS; Dock=Xbox,Retro,Off; Handheld=Xbox,Retro; otherSixKeys=unchanged");
        } finally { restorePreferences(c,before); }
    }
    private static void verifyLayoutFallback(Context context,Profile expected) {
        Map<String,?> before=Store.prefs(context).getAll();
        if (!sameProfile(expected,Profiles.get(context,expected.docked)) || !before.equals(Store.prefs(context).getAll())) throw new IllegalStateException("Invalid layout reset other profile fields or wrote preferences");
    }
    private static boolean sameProfile(Profile a,Profile b) {
        return a.docked==b.docked && a.rate==b.rate && a.fan==b.fan && a.performance==b.performance && a.layout==b.layout && a.bypassCharging==b.bypassCharging;
    }
    private static void verifyController(Retroid retroid,int layout) {
        if (!Integer.toString(layout).equals(retroid.get("temp_abxy_layout_mode"))
            || !(layout==2?"1":"0").equals(retroid.get("no_create_gamepad_button_layout"))
            || !(layout==0?"1":"0").equals(retroid.get("flip_button_layout"))) throw new IllegalStateException("Native controller triplet mismatch: "+layout);
    }
    @SuppressWarnings("unchecked")
    private static void restorePreferences(Context c,Map<String,?> values) {
        SharedPreferences.Editor edit=Store.prefs(c).edit().clear();
        for (Map.Entry<String,?> entry:values.entrySet()) {
            String key=entry.getKey(); Object value=entry.getValue();
            if (value instanceof Boolean) edit.putBoolean(key,(Boolean)value);
            else if (value instanceof Integer) edit.putInt(key,(Integer)value);
            else if (value instanceof Long) edit.putLong(key,(Long)value);
            else if (value instanceof Float) edit.putFloat(key,(Float)value);
            else if (value instanceof String) edit.putString(key,(String)value);
            else if (value instanceof Set) edit.putStringSet(key,new HashSet<>((Set<String>)value));
        }
        if (!edit.commit()) throw new IllegalStateException("Preferences were not restored");
    }
    private static String checkBypass(Context c,Retroid r,boolean docked) {
        Profile profile=Profiles.get(c,docked);
        if (!r.bypassMatches(profile)) throw new IllegalStateException("Bypass setting mismatch");
        String driver=Detector.read("/sys/class/qcom-battery/usb_charge_now");
        Intent battery=c.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        boolean powered=battery!=null && battery.getIntExtra("plugged",0)!=0;
        boolean autoLimit="true".equals(Detector.property("persist.is.auto.charging.separate"));
        if (powered && c.getSystemService(PowerManager.class).isInteractive() && !autoLimit && !driver.isEmpty()
            && !(profile.bypassCharging?"0":"1").equals(driver)) throw new IllegalStateException("Charging driver mismatch: "+driver);
        return r.get("is_charging_separation")+", usb_charge_now="+driver;
    }
    private static void verifyOriginalMigration(Context c,Retroid r) {
        SharedPreferences prefs=Store.prefs(c); Map<String,?> before=prefs.getAll();
        String key="is_charging_separation", present="present_"+key, original="original_"+key;
        boolean hadMarker=prefs.contains(present), wasPresent=prefs.getBoolean(present,false);
        String oldOriginal=prefs.getString(original,null), current=r.get(key);
        // Emulate a snapshot made by 0.2.x, which did not manage bypass charging.
        prefs.edit().remove(present).remove(original).commit();
        try {
            r.saveOriginal();
            Map<String,?> after=prefs.getAll();
            for (String managed:Retroid.KEYS) {
                if (managed.equals(key)) continue;
                for (String prefix:new String[]{"present_","original_"}) {
                    String name=prefix+managed;
                    if (before.containsKey(name) && !Objects.equals(before.get(name),after.get(name))) throw new IllegalStateException("Original snapshot overwritten: "+name);
                }
            }
            if (!prefs.contains(present) || prefs.getBoolean(present,false)!=(current!=null)
                || !Objects.equals(current,prefs.getString(original,null))) throw new IllegalStateException("New original value not captured");
        } finally {
            if (hadMarker) prefs.edit().putBoolean(present,wasPresent).putString(original,oldOriginal).commit();
        }
    }
    private static String pads() {
        StringBuilder names=new StringBuilder();
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice d=InputDevice.getDevice(id);
            if (d!=null && d.supportsSource(InputDevice.SOURCE_GAMEPAD)) {
                if (names.length()>0) names.append(", ");
                names.append(d.getName());
            }
        }
        return names.length()==0 ? "none" : names.toString();
    }
}
