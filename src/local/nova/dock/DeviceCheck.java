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
                r.apply(true,false); Thread.sleep(1800);
                if (!r.matches(true)) throw new IllegalStateException("Dock settings were overridden: "+r.snapshot());
                float hz=c.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY).getRefreshRate();
                if (Math.round(hz)!=60) throw new IllegalStateException("Actual dock refresh rate="+hz);
                report.append("\ndock=PASS; actualHz=").append(hz).append("; fanDuty=").append(Detector.read("/sys/class/gpio5_pwm2/duty")).append("; pads=").append(pads());
                Store.log(c,"Самопроверка: док 60 Гц, максимум, контроллер выключен");
                Thread.sleep(2200);
                r.apply(false,false); Thread.sleep(1800);
                if (!r.matches(false)) throw new IllegalStateException("Handheld settings were overridden: "+r.snapshot());
                hz=c.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY).getRefreshRate();
                if (Math.round(hz)!=120) throw new IllegalStateException("Actual handheld refresh rate="+hz);
                report.append("\nhandheld=PASS; actualHz=").append(hz).append("; fanDuty=").append(Detector.read("/sys/class/gpio5_pwm2/duty")).append("; pads=").append(pads());
                Store.log(c,"Самопроверка: портатив 120 Гц, Quiet, Xbox");
            }
            report.append("\nPASS");
        } catch (Exception e) { report.append("\nFAIL: ").append(e); code=0; }
        finally {
            if (changed) {
                try { r.restore(original); report.append("\noriginalSettingsRestored=true"); }
                catch (Exception e) { report.append("\nrestoreFailed=").append(e); code=0; }
                Store.enabled(c,wasEnabled);
                if (wasEnabled) DockService.start(c,null);
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
