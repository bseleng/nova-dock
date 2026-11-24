package local.nova.dock;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.hardware.display.DisplayManager;
import android.view.Display;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

final class Retroid {
    static final String[] KEYS = {"min_refresh_rate", "peak_refresh_rate", "performance_mode", "fan_mode", "fan_speed", "temp_abxy_layout_mode", "no_create_gamepad_button_layout", "flip_button_layout", "is_charging_separation"};
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
        SharedPreferences.Editor edit = p.edit();
        boolean changed=!p.getBoolean("saved_original",false);
        // Capture only newly managed keys on upgrade; never replace an earlier original value.
        for (String key : KEYS) {
            if (p.contains("present_"+key)) continue;
            String value=get(key);
            edit.putString("original_"+key,value).putBoolean("present_"+key,value!=null);
            changed=true;
        }
        if (changed && !edit.putBoolean("saved_original",true).commit()) throw new IllegalStateException("Не удалось сохранить исходные настройки");
    }
    void restore(Map<String, String> values) throws InterruptedException {
        // Both charging and performance changes can asynchronously rewrite the fan preset.
        if (values.containsKey("is_charging_separation")) put("is_charging_separation",values.get("is_charging_separation"));
        if (values.containsKey("performance_mode")) put("performance_mode",values.get("performance_mode"));
        Thread.sleep(700);
        for (Map.Entry<String, String> e : values.entrySet()) if (!"performance_mode".equals(e.getKey()) && !"is_charging_separation".equals(e.getKey())) put(e.getKey(), e.getValue());
    }
    void restoreOriginal() throws InterruptedException {
        SharedPreferences p = Store.prefs(context);
        if (!p.getBoolean("saved_original", false)) return;
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : KEYS) if (p.contains("present_"+key)) values.put(key, p.getBoolean("present_" + key, false) ? p.getString("original_" + key, null) : null);
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
        return apply(docked,()->requireConnection);
    }
    boolean apply(boolean docked,BooleanSupplier requireConnection) throws InterruptedException {
        if (!supported() || !permitted()) throw new SecurityException("Требуется Nova с Android 13 и доступный сервис Retroid");
        saveOriginal();
        Profile profile=Profiles.get(context,docked);
        if (requireConnection.getAsBoolean() && !Boolean.valueOf(docked).equals(Detector.sample(context).connected)) return false;
        if (!docked && !controllerMatches(profile)) controller(profile.layout);
        rate(profile);
        if (docked) fan(profile);
        // Settle this explicit application; subsequent system-tile edits remain temporary overrides.
        bypass(profile);
        put("performance_mode", Integer.toString(profile.performance));
        Thread.sleep(700);
        if (requireConnection.getAsBoolean() && !Boolean.valueOf(docked).equals(Detector.sample(context).connected)) return false;
        fan(profile);
        if (docked && !controllerMatches(profile)) controller(profile.layout);
        return true;
    }
    private void rate(Profile profile) {
        float rate = profile.rate==120 ? highRate() : 60f;
        // Lower min first for 120 -> 60; raise peak first for 60 -> 120.
        if (profile.rate!=0) {
            float current;
            try { current=Float.parseFloat(get("peak_refresh_rate")); } catch (Exception e) { current=rate; }
            if (rate<current) { put("min_refresh_rate",Float.toString(rate)); put("peak_refresh_rate",Float.toString(rate)); }
            else { put("peak_refresh_rate",Float.toString(rate)); put("min_refresh_rate",Float.toString(rate)); }
        }
    }
    boolean applySetting(Profile profile,int setting,BooleanSupplier active,BooleanSupplier requireConnection) throws InterruptedException {
        if (!supported() || !permitted()) throw new SecurityException("Сервис Retroid недоступен");
        if (!active.getAsBoolean()) return false;
        if (requireConnection.getAsBoolean() && !Boolean.valueOf(profile.docked).equals(Detector.sample(context).connected)) return false;
        saveOriginal();
        RuntimeSettings live=new RuntimeSettings(snapshot());
        int performance=live.value(profile,Profile.PERFORMANCE), fan=live.value(profile,Profile.FAN);
        String speed=get("fan_speed");
        switch (setting) {
            case Profile.RATE: rate(profile); break;
            case Profile.FAN:
                if (!Profile.fanAllowed(performance,profile.fan)) throw new IllegalStateException("Вентилятор недоступен при текущей производительности");
                fan(profile); break;
            case Profile.CONTROLLER:
                if (profile.docked) return false;
                controller(profile.layout); break;
            case Profile.PERFORMANCE:
            case Profile.BYPASS:
                if (setting==Profile.PERFORMANCE) put("performance_mode",Integer.toString(profile.performance)); else bypass(profile);
                Thread.sleep(700L);
                if (!active.getAsBoolean()) return false;
                if (requireConnection.getAsBoolean() && !Boolean.valueOf(profile.docked).equals(Detector.sample(context).connected)) return false;
                int after=new RuntimeSettings(snapshot()).value(profile,Profile.PERFORMANCE);
                // Retroid couples these writes to Smart. Preserve a compatible live fan choice.
                if (after==(setting==Profile.PERFORMANCE?profile.performance:performance)) {
                    int desired=Profile.fanAllowed(after,fan)?fan:4;
                    fan(desired,desired==fan?speed:"35000");
                }
                break;
            default:throw new IllegalArgumentException("Неизвестная настройка");
        }
        return true;
    }
    private void fan(Profile profile) {
        fan(profile.fan,"35000");
    }
    private void fan(int mode,String speed) {
        if (mode==6) put("fan_speed",speed!=null && speed.matches("[0-9]+")?speed:"35000");
        put("fan_mode",Integer.toString(mode));
    }
    boolean fanMatches(Profile profile) {
        return Integer.toString(profile.fan).equals(get("fan_mode")) && (profile.fan!=6 || "35000".equals(get("fan_speed")));
    }
    private void bypass(Profile profile) { put("is_charging_separation",profile.bypassCharging?"1":"0"); }
    boolean bypassMatches(Profile profile) { return (profile.bypassCharging?"1":"0").equals(get("is_charging_separation")); }
    private boolean controllerMatches(Profile profile) {
        return Integer.toString(profile.layout).equals(get("temp_abxy_layout_mode"))
            && (profile.layout==2?"1":"0").equals(get("no_create_gamepad_button_layout"))
            && (profile.layout==0?"1":"0").equals(get("flip_button_layout"));
    }
    boolean matches(boolean docked) {
        try {
            Profile profile=Profiles.get(context,docked);
            return (profile.rate==0 || (Math.round(Float.parseFloat(get("min_refresh_rate")))==profile.rate && Math.round(Float.parseFloat(get("peak_refresh_rate")))==profile.rate))
                && Integer.toString(profile.performance).equals(get("performance_mode")) && fanMatches(profile) && controllerMatches(profile) && bypassMatches(profile);
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
        String bypass=get("is_charging_separation");
        return hz + " Гц  ·  " + p + "\n" + f + "  ·  " + pad+"\nОбходная зарядка: "+("1".equals(bypass)?"вкл.":"0".equals(bypass)?"выкл.":"неизвестно");
    }
}
