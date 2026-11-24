package local.nova.dock;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.hardware.display.DisplayManager;
import android.view.Display;
import java.util.LinkedHashMap;
import java.util.Map;

final class Retroid {
    static final String[] KEYS = {"min_refresh_rate", "peak_refresh_rate", "performance_mode", "fan_mode", "fan_speed", "temp_abxy_layout_mode", "no_create_gamepad_button_layout", "flip_button_layout"};
    final Context context;
    final VendorBridge bridge = new VendorBridge();
    Retroid(Context c) { context = c.getApplicationContext(); }
    boolean supported() { return "Retroid Pocket Nova".equals(Build.MODEL) && Build.VERSION.SDK_INT == 33; }
    boolean permitted() { return bridge.available(); }
    String get(String key) { return Settings.System.getString(context.getContentResolver(), key); }
    void put(String key, String value) {
        bridge.write(key, value);
    }
    Map<String, String> snapshot() {
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : KEYS) values.put(key, get(key));
        return values;
    }
    void saveOriginal() {
        SharedPreferences p = Store.prefs(context);
        if (p.getBoolean("saved_original", false)) return;
        SharedPreferences.Editor edit = p.edit();
        for (Map.Entry<String, String> e : snapshot().entrySet()) {
            edit.putString("original_" + e.getKey(), e.getValue());
            edit.putBoolean("present_" + e.getKey(), e.getValue() != null);
        }
        edit.putBoolean("saved_original", true).commit();
    }
    void restore(Map<String, String> values) throws InterruptedException {
        // Firmware SystemUI reacts asynchronously to performance changes and may rewrite fan_mode.
        put("performance_mode", values.get("performance_mode"));
        Thread.sleep(700);
        for (Map.Entry<String, String> e : values.entrySet()) if (!"performance_mode".equals(e.getKey())) put(e.getKey(), e.getValue());
    }
    void restoreOriginal() throws InterruptedException {
        SharedPreferences p = Store.prefs(context);
        if (!p.getBoolean("saved_original", false)) return;
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : KEYS) values.put(key, p.getBoolean("present_" + key, false) ? p.getString("original_" + key, null) : null);
        restore(values);
    }
    float highRate() {
        float rate = 60f;
        Display d = context.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
        if (d != null) for (Display.Mode m : d.getSupportedModes()) rate = Math.max(rate, m.getRefreshRate());
        return Math.min(rate, 120.00001f);
    }
    void controller(boolean docked) {
        // Exact write order of Retroid's own quick settings tile.
        put("temp_abxy_layout_mode", docked ? "2" : "0");
        put("no_create_gamepad_button_layout", docked ? "1" : "0");
        put("flip_button_layout", docked ? "0" : "1");
    }
    boolean apply(boolean docked, boolean requireConnection) throws InterruptedException {
        if (!supported() || !permitted()) throw new SecurityException("Требуется Nova с Android 13 и доступный сервис Retroid");
        saveOriginal();
        if (!docked) controller(false);
        float rate = docked ? 60f : highRate();
        // Lower min first for 120 -> 60; raise peak first for 60 -> 120.
        if (docked) { put("min_refresh_rate", Float.toString(rate)); put("peak_refresh_rate", Float.toString(rate)); }
        else { put("peak_refresh_rate", Float.toString(rate)); put("min_refresh_rate", Float.toString(rate)); }
        if (docked) { put("fan_speed", "35000"); put("fan_mode", "6"); }
        put("performance_mode", docked ? "2" : "0");
        Thread.sleep(700);
        if (docked && requireConnection && !Boolean.TRUE.equals(Detector.sample(context).connected)) return false;
        // Reassert fan after the firmware's automatic performance/fan coupling has settled.
        if (docked) put("fan_speed", "35000");
        put("fan_mode", docked ? "6" : "1");
        if (docked) controller(true);
        return true;
    }
    boolean matches(boolean docked) {
        try {
            return Math.round(Float.parseFloat(get("min_refresh_rate"))) == (docked ? 60 : 120)
                && Math.round(Float.parseFloat(get("peak_refresh_rate"))) == (docked ? 60 : 120)
                && (docked ? "2" : "0").equals(get("performance_mode"))
                && (docked ? "6" : "1").equals(get("fan_mode"))
                && (!docked || "35000".equals(get("fan_speed")))
                && (docked ? "2" : "0").equals(get("temp_abxy_layout_mode"))
                && (docked ? "1" : "0").equals(get("no_create_gamepad_button_layout"))
                && (docked ? "0" : "1").equals(get("flip_button_layout"));
        } catch (Exception e) { return false; }
    }
    String actual() {
        String perf = get("performance_mode"), fan = get("fan_mode");
        String p = "0".equals(perf) ? "Standard" : "2".equals(perf) ? "High Performance" : "Performance";
        String f = "1".equals(fan) ? "Quiet" : "6".equals(fan) && "35000".equals(get("fan_speed")) ? "Максимум" : "5".equals(fan) ? "Sport" : "4".equals(fan) ? "Smart" : "Режим " + fan;
        String pad = "1".equals(get("no_create_gamepad_button_layout")) ? "Контроллер выключен" : "1".equals(get("flip_button_layout")) ? "Контроллер Xbox" : "Контроллер Retro";
        String hz = get("peak_refresh_rate");
        try { hz = Integer.toString(Math.round(Float.parseFloat(hz))); } catch (Exception ignored) { }
        return hz + " Гц  ·  " + p + "\n" + f + "  ·  " + pad;
    }
}
