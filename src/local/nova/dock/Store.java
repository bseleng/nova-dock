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
            throw new Failure("control_save_failed");
    }
    static synchronized void automatic(Context c,boolean enabled) { save(c,state(c).automatic(enabled)); }
    static synchronized void manual(Context c,boolean docked) { save(c,state(c).manual(docked)); }
    static synchronized void applied(Context c,boolean docked) { save(c,state(c).applied(docked)); }
    static synchronized void syncManual(Context c,Boolean settled) {
        ControlState state=state(c);
        if (!state.automatic) save(c,state.freezeManual(settled));
    }
    static synchronized void stop(Context c) { save(c,state(c).stopped()); }
    static boolean startOnBoot(Context c) { return prefs(c).getBoolean("start_on_boot",true); }
    static void startOnBoot(Context c,boolean enabled) { prefs(c).edit().putBoolean("start_on_boot",enabled).commit(); }
    static synchronized LogBuffer history(Context c) {
        String before=prefs(c).getString("history",""); LogBuffer buffer=new LogBuffer(before);
        if (!before.equals(buffer.data())) prefs(c).edit().putString("history",buffer.data()).apply();
        return buffer;
    }
    static synchronized void clearHistory(Context c) { prefs(c).edit().putString("history","").commit(); }
    static synchronized void event(Context c,String code,String...args) {
        String time=new SimpleDateFormat("HH:mm:ss",Locale.ROOT).format(new Date());
        String record=LogEvent.encode(time,code,args);
        Log.i("NovaDock",code);
        prefs(c).edit().putString("history",history(c).append(record).data()).apply();
    }
    static synchronized void log(Context c,String legacyMessage) {
        String time=new SimpleDateFormat("HH:mm:ss",Locale.ROOT).format(new Date());
        prefs(c).edit().putString("history",history(c).append(time+"  "+legacyMessage).data()).apply();
    }
    static String statusCode(Context c) { return prefs(c).getString("status_code","idle"); }
    static void status(Context c,String code,String argument) {
        prefs(c).edit().putString("status_code",code).putString("status_argument",argument==null?"":argument).remove("status").apply();
    }
    static void failure(Context c,Throwable error) {
        String code=error instanceof Failure?((Failure)error).code:error.getMessage();
        String detail=error instanceof Failure?((Failure)error).detail:"";
        prefs(c).edit().putString("status_code","error").putString("status_argument",code==null?"unknown":code).putString("status_detail",detail).remove("status").apply();
        event(c,"error",code,detail);
    }
}
