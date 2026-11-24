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
    void controller(int layout) {
        // Exact write order of Retroid's own quick settings tile.
        put("temp_abxy_layout_mode", Integer.toString(layout));
        put("no_create_gamepad_button_layout", layout==2 ? "1" : "0");
        put("flip_button_layout", layout==0 ? "1" : "0");
    }
    boolean apply(boolean docked, boolean requireConnection) throws InterruptedException {
        if (!supported() || !permitted()) throw new SecurityException("Требуется Nova с Android 13 и доступный сервис Retroid");
        saveOriginal();
        Profile profile=Profiles.get(context,docked);
        if (!docked && !controllerMatches(profile)) controller(profile.layout);
        float rate = profile.rate==120 ? highRate() : 60f;
        // Lower min first for 120 -> 60; raise peak first for 60 -> 120.
        if (profile.rate!=0) {
            float current;
            try { current=Float.parseFloat(get("peak_refresh_rate")); } catch (Exception e) { current=rate; }
            if (rate<current) { put("min_refresh_rate",Float.toString(rate)); put("peak_refresh_rate",Float.toString(rate)); }
            else { put("peak_refresh_rate",Float.toString(rate)); put("min_refresh_rate",Float.toString(rate)); }
        }
        if (docked) fan(profile);
        put("performance_mode", Integer.toString(profile.performance));
        Thread.sleep(700);
        if (docked && requireConnection && !Boolean.TRUE.equals(Detector.sample(context).connected)) return false;
        // Reassert fan after the firmware's automatic performance/fan coupling has settled.
        fan(profile);
        if (docked && !controllerMatches(profile)) controller(profile.layout);
        return true;
    }
    private void fan(Profile profile) {
        if (profile.fan==6) put("fan_speed","35000");
        put("fan_mode",Integer.toString(profile.fan));
    }
    boolean fanMatches(Profile profile) {
        return Integer.toString(profile.fan).equals(get("fan_mode")) && (profile.fan!=6 || "35000".equals(get("fan_speed")));
    }
    boolean restoreFan(Profile profile) {
        if (fanMatches(profile)) return false;
        fan(profile); return true;
    }
    private boolean controllerMatches(Profile profile) {
        return Integer.toString(profile.layout).equals(get("temp_abxy_layout_mode"))
            && (profile.layout==2?"1":"0").equals(get("no_create_gamepad_button_layout"))
            && (profile.layout==0?"1":"0").equals(get("flip_button_layout"));
    }
    boolean matches(boolean docked) {
        try {
            Profile profile=Profiles.get(context,docked);
            return (profile.rate==0 || (Math.round(Float.parseFloat(get("min_refresh_rate")))==profile.rate && Math.round(Float.parseFloat(get("peak_refresh_rate")))==profile.rate))
                && Integer.toString(profile.performance).equals(get("performance_mode")) && fanMatches(profile) && controllerMatches(profile);
        } catch (Exception e) { return false; }
    }
    String actual() {
        String perf = get("performance_mode"), fan = get("fan_mode");
        String p = "0".equals(perf)?"Standard":"1".equals(perf)?"Performance":"2".equals(perf)?"High Performance":"Неизвестно";
        String f;
        try { f=Profile.fanLabel(Integer.parseInt(fan)); } catch (Exception e) { f="Неизвестно"; }
        if ("6".equals(fan) && !"35000".equals(get("fan_speed"))) f="Custom";
        String pad = "1".equals(get("no_create_gamepad_button_layout")) ? "Контроллер выключен" : "1".equals(get("flip_button_layout")) ? "Контроллер Xbox" : "Контроллер Retro";
        String hz = get("peak_refresh_rate");
        try { hz = Integer.toString(Math.round(Float.parseFloat(hz))); } catch (Exception ignored) { }
        return hz + " Гц  ·  " + p + "\n" + f + "  ·  " + pad;
    }
}
