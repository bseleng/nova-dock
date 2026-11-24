package local.nova.dock;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.view.Display;
import android.view.InputDevice;
import java.util.Map;

/** Explicit ADB-only instrumentation; no exported activity or broadcast can force a dock profile. */
public final class DeviceCheck extends Instrumentation {
    private Bundle arguments;
    @Override public void onCreate(Bundle b) { arguments=b; start(); }
    @Override public void onStart() {
        Bundle result=new Bundle(); StringBuilder report=new StringBuilder();
        Context c=getTargetContext(); Retroid r=new Retroid(c);
        Map<String,String> original=r.snapshot(); boolean wasEnabled=Store.enabled(c);
        Map<String,Integer> originalProfiles=Profiles.snapshot(c);
        boolean changed=false;
        int code=-1;
        try {
            report.append("model=").append(android.os.Build.MODEL).append("\nvendorServiceAvailable=").append(r.permitted());
            report.append("\nvendorBridge=").append(new VendorBridge().probe());
            Detector.Sample sample=Detector.sample(c); report.append("\ndisplayConnected=").append(sample.connected).append("; source=").append(sample.source);
            if (!r.supported() || sample.connected==null) throw new IllegalStateException("Diagnostic prerequisites failed");
            if ("true".equals(arguments.getString("profiles"))) {
                if (!r.permitted()) throw new IllegalStateException("Retroid service unavailable");
                if (Boolean.TRUE.equals(sample.connected)) throw new IllegalStateException("Unplug external display before profile self-test");
                Store.enabled(c,false); c.stopService(new Intent(c,DockService.class)); Thread.sleep(1500);
                changed=true;
                float beforeHz=c.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY).getRefreshRate();
                r.apply(true,false); Thread.sleep(1800);
                if (!r.matches(true)) throw new IllegalStateException("Dock settings were overridden: "+r.snapshot());
                float hz=c.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY).getRefreshRate();
                int expected=Profiles.get(c,true).rate;
                if (Math.round(hz)!=(expected==0?Math.round(beforeHz):expected)) throw new IllegalStateException("Actual dock refresh rate="+hz);
                report.append("\ndock=PASS; actualHz=").append(hz).append("; fanDuty=").append(Detector.read("/sys/class/gpio5_pwm2/duty")).append("; pads=").append(pads());
                Store.log(c,"Самопроверка: "+Profiles.get(c,true).status());
                Thread.sleep(2200);
                beforeHz=hz; r.apply(false,false); Thread.sleep(1800);
                if (!r.matches(false)) throw new IllegalStateException("Handheld settings were overridden: "+r.snapshot());
                hz=c.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY).getRefreshRate();
                expected=Profiles.get(c,false).rate;
                if (Math.round(hz)!=(expected==0?Math.round(beforeHz):expected)) throw new IllegalStateException("Actual handheld refresh rate="+hz);
                report.append("\nhandheld=PASS; actualHz=").append(hz).append("; fanDuty=").append(Detector.read("/sys/class/gpio5_pwm2/duty")).append("; pads=").append(pads());
                Store.log(c,"Самопроверка: "+Profiles.get(c,false).status());
                if ("true".equals(arguments.getString("customProfiles"))) {
                    String min=r.get("min_refresh_rate"), peak=r.get("peak_refresh_rate");
                    Profiles.save(c,new Profile(true,0,4,1,2));
                    r.apply(true,false); Thread.sleep(1800);
                    if (!r.matches(true) || !min.equals(r.get("min_refresh_rate")) || !peak.equals(r.get("peak_refresh_rate"))) throw new IllegalStateException("Custom dock or keep-rate check failed");
                    report.append("\ncustomDock=PASS; rate=unchanged; fan=Smart; performance=Performance; pads=").append(pads());
                    Profiles.save(c,new Profile(false,60,5,1,1));
                    r.apply(false,false); Thread.sleep(1800);
                    if (!r.matches(false) || Math.round(c.getSystemService(DisplayManager.class).getDisplay(0).getRefreshRate())!=60) throw new IllegalStateException("Custom handheld check failed");
                    report.append("\ncustomHandheld=PASS; rate=60; fan=Sport; performance=Performance; layout=Retro; pads=").append(pads());
                }
            }
            report.append("\nPASS");
        } catch (Exception e) { report.append("\nFAIL: ").append(e); code=0; }
        finally {
            if (changed) {
                Profiles.restore(c,originalProfiles);
                try { r.restore(original); report.append("\noriginalSettingsRestored=true"); }
                catch (Exception e) { report.append("\nrestoreFailed=").append(e); code=0; }
                Store.enabled(c,wasEnabled);
                if (wasEnabled) report.append("\nreopenNovaDockToResumeAutomation=true");
            }
            result.putString("stream", report.toString()+"\n"); finish(code,result);
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
