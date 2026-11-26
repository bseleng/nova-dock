package local.nova.dock;

import android.content.Context;

/** Localized presentation kept outside the pure profile model and its JVM tests. */
final class ProfileText {
    private ProfileText() { }

    static String setting(Context c,int setting) {
        int resource;
        switch (setting) {
            case Profile.RATE:resource=R.string.setting_rate;break;
            case Profile.PERFORMANCE:resource=R.string.setting_performance;break;
            case Profile.FAN:resource=R.string.setting_fan;break;
            case Profile.BYPASS:resource=R.string.setting_bypass;break;
            case Profile.CONTROLLER:resource=R.string.setting_controller;break;
            case SettingChanges.OUTPUT:resource=R.string.setting_output_mode;break;
            default:resource=R.string.value_unknown;break;
        }
        return AppText.get(c,resource);
    }

    static String value(Context c,int setting,int value) {
        switch (setting) {
            case Profile.RATE:return rate(c,value);
            case Profile.PERFORMANCE:return performance(c,value);
            case Profile.FAN:return fan(c,value);
            case Profile.BYPASS:return AppText.get(c,value==1?R.string.value_on:R.string.value_off);
            case Profile.CONTROLLER:
                return AppText.get(c,value==2?R.string.value_disabled:value==0?R.string.controller_xbox:value==1?R.string.controller_retro:R.string.value_unknown);
            default:return AppText.get(c,R.string.value_unknown);
        }
    }

    static String value(Context c,Profile profile,int setting) { return value(c,setting,profile.intValue(setting)); }

    static String fan(Context c,int value) {
        int resource;
        switch (value) {
            case 0:resource=R.string.value_disabled;break;
            case 1:resource=R.string.fan_quiet;break;
            case 4:resource=R.string.fan_smart;break;
            case 5:resource=R.string.fan_sport;break;
            case 6:resource=R.string.fan_maximum;break;
            default:resource=R.string.value_unknown;break;
        }
        return AppText.get(c,resource);
    }

    static String performance(Context c,int value) {
        return AppText.get(c,value==0?R.string.performance_standard:value==1?R.string.performance_performance:value==2?R.string.performance_high:R.string.value_unknown);
    }

    static String rate(Context c,int value) {
        return value==0?AppText.get(c,R.string.value_unchanged):AppText.get(c,R.string.format_rate_hz,value);
    }

    static String status(Context c,Profile profile) {
        return AppText.get(c,R.string.format_profile_status,AppText.get(c,profile.docked?R.string.mode_docked:R.string.mode_handheld),rate(c,profile.rate),fan(c,profile.fan));
    }

    static String body(Context c,Profile profile) {
        return AppText.get(c,R.string.format_profile_body,
            AppText.get(c,R.string.format_controller,value(c,profile,Profile.CONTROLLER)),fan(c,profile.fan),performance(c,profile.performance),value(c,profile,Profile.BYPASS));
    }
}
