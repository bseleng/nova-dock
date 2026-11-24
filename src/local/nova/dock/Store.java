package local.nova.dock;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class Store {
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("dock", Context.MODE_PRIVATE); }
    static boolean enabled(Context c) { return prefs(c).getBoolean("enabled", false); }
    static boolean managed(Context c) { return state(c).managed; }
    static boolean manualDocked(Context c) { return state(c).manualDocked; }
    static boolean lastDocked(Context c) { return prefs(c).getBoolean("last_docked",false); }
    static ControlState state(Context c) {
        SharedPreferences p=prefs(c);
        return new ControlState(p.contains("managed")?p.getBoolean("managed",false):null,p.getBoolean("enabled",false),
            p.contains("manual_docked")?p.getBoolean("manual_docked",false):null,p.getBoolean("last_docked",false));
    }
    private static void save(Context c,ControlState state) {
        if (!prefs(c).edit().putBoolean("managed",state.managed).putBoolean("enabled",state.automatic)
            .putBoolean("manual_docked",state.manualDocked).putBoolean("last_docked",state.lastDocked).commit())
            throw new IllegalStateException("Не удалось сохранить режим управления");
    }
    static synchronized void automatic(Context c,boolean enabled) { save(c,state(c).automatic(enabled)); }
    static synchronized void manual(Context c,boolean docked) { save(c,state(c).manual(docked)); }
    static synchronized void applied(Context c,boolean docked) { save(c,state(c).applied(docked)); }
    static synchronized void syncManual(Context c,boolean docked) {
        ControlState state=state(c);
        if (!state.automatic) save(c,new ControlState(state.managed,false,docked,state.lastDocked));
    }
    static synchronized void stop(Context c) { save(c,state(c).stopped()); }
    static boolean startOnBoot(Context c) { return prefs(c).getBoolean("start_on_boot",true); }
    static void startOnBoot(Context c,boolean enabled) { prefs(c).edit().putBoolean("start_on_boot",enabled).commit(); }
    static synchronized void log(Context c, String message) {
        Log.i("NovaDock", message);
        String line = new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date()) + "  " + message;
        String history = line + "\n" + prefs(c).getString("history", "");
        if (history.length() > 12000) history = history.substring(0, 12000);
        prefs(c).edit().putString("history", history).apply();
    }
    static void status(Context c, String text) { prefs(c).edit().putString("status", text).apply(); }
}
