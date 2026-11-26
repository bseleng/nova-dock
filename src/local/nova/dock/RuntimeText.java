package local.nova.dock;

import android.content.Context;

final class RuntimeText {
    static String value(Context c,RuntimeSettings live,Profile profile,int setting) {
        if (setting==Profile.FAN && live.customFan(profile)) return c.getString(R.string.fan_custom);
        return ProfileText.value(c,setting,live.value(profile,setting));
    }
    static String hint(Context c,RuntimeSettings live,Profile profile,int setting) {
        if (live.isPending(setting)) return c.getString(R.string.applying);
        if (setting==Profile.RATE && profile.rate==0) return c.getString(R.string.current_value,c.getString(R.string.format_rate_hz,live.currentRate()));
        return live.changed(profile,setting)?c.getString(R.string.saved_value,ProfileText.value(c,profile,setting)):"";
    }
}
