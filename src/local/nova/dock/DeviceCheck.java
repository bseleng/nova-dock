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
        Bundle result=new Bundle(); StringBuilder report=new StringBuilder();
        Context c=getTargetContext(); Retroid r=new Retroid(c);
        Map<String,String> original=r.snapshot(); boolean wasEnabled=Store.enabled(c), wasManaged=Store.managed(c);
        Map<String,?> originalPreferences=Store.prefs(c).getAll();
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
            if ("true".equals(arguments.getString("profiles"))) {
                if (!r.permitted()) throw new IllegalStateException("Retroid service unavailable");
                if (Boolean.TRUE.equals(sample.connected)) throw new IllegalStateException("Unplug external display before profile self-test");
                changed=true;
                Store.stop(c); c.stopService(new Intent(c,DockService.class)); Thread.sleep(1500);
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
