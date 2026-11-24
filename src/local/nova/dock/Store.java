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
    static void enabled(Context c, boolean enabled) { prefs(c).edit().putBoolean("enabled", enabled).commit(); }
    static synchronized void log(Context c, String message) {
        Log.i("NovaDock", message);
        String line = new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date()) + "  " + message;
        String history = line + "\n" + prefs(c).getString("history", "");
        if (history.length() > 12000) history = history.substring(0, 12000);
        prefs(c).edit().putString("history", history).apply();
    }
    static void status(Context c, String text) { prefs(c).edit().putString("status", text).apply(); }
}
