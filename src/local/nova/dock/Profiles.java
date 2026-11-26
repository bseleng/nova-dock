package local.nova.dock;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.LinkedHashMap;
import java.util.Map;

final class Profiles {
    static final String[] KEYS={"profile_dock_rate","profile_dock_fan","profile_dock_performance","profile_dock_layout","profile_dock_bypass","profile_handheld_rate","profile_handheld_fan","profile_handheld_performance","profile_handheld_layout","profile_handheld_bypass"};
    static Profile get(Context c, boolean docked) {
        SharedPreferences p=Store.prefs(c); Profile d=Profile.defaults(docked);
        String prefix=docked?"profile_dock_":"profile_handheld_";
        try {
            int bypass=p.getInt(prefix+"bypass",d.bypassCharging?1:0);
            if (bypass!=0 && bypass!=1) throw new IllegalArgumentException("unknown_charge_mode");
            return new Profile(docked,p.getInt(prefix+"rate",d.rate),p.getInt(prefix+"fan",d.fan),p.getInt(prefix+"performance",d.performance),layout(p,prefix,d),bypass==1);
        }
        catch (IllegalArgumentException | ClassCastException e) { return d; }
    }
    private static int layout(SharedPreferences preferences,String prefix,Profile fallback) {
        try {
            int value=preferences.getInt(prefix+"layout",fallback.layout);
            return value>=0 && value<=(fallback.docked?2:1)?value:fallback.layout;
        } catch (ClassCastException e) { return fallback.layout; }
    }
    static void save(Context c, Profile profile) {
        String prefix=profile.docked?"profile_dock_":"profile_handheld_";
        SharedPreferences.Editor edit=Store.prefs(c).edit().putInt(prefix+"rate",profile.rate).putInt(prefix+"fan",profile.fan).putInt(prefix+"performance",profile.performance).putInt(prefix+"layout",profile.layout).putInt(prefix+"bypass",profile.bypassCharging?1:0);
        if (!edit.commit()) throw new Failure("profile_save_failed");
    }
    static Map<String,Integer> snapshot(Context c) {
        SharedPreferences p=Store.prefs(c); Map<String,Integer> values=new LinkedHashMap<>();
        for (String key:KEYS) values.put(key,p.contains(key)?p.getInt(key,0):null);
        return values;
    }
    static void restore(Context c, Map<String,Integer> values) {
        SharedPreferences.Editor edit=Store.prefs(c).edit();
        for (Map.Entry<String,Integer> entry:values.entrySet()) {
            if (entry.getValue()==null) edit.remove(entry.getKey()); else edit.putInt(entry.getKey(),entry.getValue());
        }
        edit.commit();
    }
}
