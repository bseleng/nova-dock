package local.nova.dock;

import java.util.Map;

/** Reads live settings without turning temporary system-tile edits into saved profile edits. */
final class RuntimeSettings {
    private final Map<String,String> values;
    private final int[] pending={-1,-1,-1,-1,-1};
    RuntimeSettings(Map<String,String> values) { this.values=values; }
    void pending(int setting,int value) { pending[setting]=value; }
    int value(Profile profile,int setting) {
        if (pending[setting]>=0) return pending[setting];
        int fallback=profile.intValue(setting);
        switch (setting) {
            case Profile.RATE:
                if (profile.rate==0) return 0;
                int rate=Math.round(number("peak_refresh_rate",profile.rate));
                return rate==60 || rate==120?rate:profile.rate;
            case Profile.PERFORMANCE:
                int performance=integer("performance_mode",fallback);
                return performance>=0 && performance<=2?performance:fallback;
            case Profile.FAN:
                int fan=integer("fan_mode",fallback);
                return fan==0 || fan==1 || fan==4 || fan==5 || fan==6?fan:fallback;
            case Profile.BYPASS:
                int bypass=integer("is_charging_separation",fallback);
                return bypass==0 || bypass==1?bypass:fallback;
            case Profile.CONTROLLER:
                if ("1".equals(values.get("no_create_gamepad_button_layout"))) return 2;
                return "1".equals(values.get("flip_button_layout"))?0:1;
            default:return fallback;
        }
    }
    String label(Profile profile,int setting) {
        if (setting==Profile.FAN && pending[setting]<0 && value(profile,setting)==6 && !"35000".equals(values.get("fan_speed"))) return "Custom";
        return Profile.valueLabel(setting,value(profile,setting));
    }
    boolean changed(Profile profile,int setting) {
        if (pending[setting]>=0 || setting==Profile.RATE && profile.rate==0) return false;
        return value(profile,setting)!=profile.intValue(setting)
            || setting==Profile.FAN && profile.fan==6 && !"35000".equals(values.get("fan_speed"));
    }
    boolean changed(Profile profile) {
        for (int setting=0;setting<5;setting++) if (changed(profile,setting)) return true;
        return false;
    }
    String hint(Profile profile,int setting) {
        if (pending[setting]>=0) return "Применяется…";
        if (setting==Profile.RATE && profile.rate==0) return "Сейчас: "+Math.round(number("peak_refresh_rate",60))+" Гц";
        return changed(profile,setting)?"Сохранено: "+profile.value(setting):"";
    }
    private int integer(String key,int fallback) { try { return Integer.parseInt(values.get(key)); } catch (Exception e) { return fallback; } }
    private float number(String key,float fallback) { try { return Float.parseFloat(values.get(key)); } catch (Exception e) { return fallback; } }
}
