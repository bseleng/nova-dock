package local.nova.dock;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.view.Display;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

final class Detector {
    static final class Sample {
        final Boolean connected;
        final String source;
        Sample(Boolean connected, String source) { this.connected = connected; this.source = source; }
    }
    static String read(String file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String value = reader.readLine();
            return value == null ? "" : value.trim();
        } catch (Exception e) { return ""; }
    }
    static String property(String name) {
        Process process = null;
        try {
            process = new ProcessBuilder("/system/bin/getprop", name).redirectErrorStream(true).start();
            if (!process.waitFor(600, TimeUnit.MILLISECONDS)) return "";
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String value = reader.readLine();
                return value == null ? "" : value.trim();
            }
        } catch (Exception e) { return ""; }
        finally { if (process != null) process.destroy(); }
    }
    static Sample sample(Context c) {
        // Physical DP state also works when Retroid mirrors the primary logical display.
        String physical = read("/sys/class/drm/card0-DP-1/status");
        if ("connected".equals(physical)) return new Sample(true, "DisplayPort");
        if ("disconnected".equals(physical)) return new Sample(false, "DisplayPort");
        String prop = property("sys.dp.isconnect");
        if ("0".equals(prop) || "1".equals(prop)) return new Sample("1".equals(prop), "Retroid DP");
        prop = property("persist.sys.hdmiisconnect");
        if ("0".equals(prop) || "1".equals(prop)) return new Sample("1".equals(prop), "Retroid HDMI");
        // A presentation display is positive evidence; absence is not proof of unplugging.
        DisplayManager dm = c.getSystemService(DisplayManager.class);
        for (Display d : dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)) {
            if (d.getDisplayId() != Display.DEFAULT_DISPLAY && (d.getFlags() & Display.FLAG_PRIVATE) == 0)
                return new Sample(true, "Android DisplayManager");
        }
        return new Sample(null, "Нет доступа к состоянию видеовыхода");
    }
}
