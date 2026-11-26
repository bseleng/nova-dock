package local.nova.dock;

import android.content.Context;

final class GlobalText {
    static String aspect(Context c,OutputMode.Aspect aspect) { return c.getString(aspect==OutputMode.Aspect.FOUR_THREE?R.string.output_aspect_4_3:R.string.output_aspect_16_9); }
    static String preset(Context c,OutputMode.Preset preset,OutputMode.Aspect aspect) {
        if (preset==null) return c.getString(R.string.value_unknown);
        if (preset==OutputMode.Preset.ADAPTIVE) return c.getString(R.string.output_adaptive);
        return c.getString(R.string.format_output_resolution_rate,preset.width(aspect),preset.height(aspect),Integer.toString(preset.fps));
    }
    static String summary(Context c,GlobalSettings.Snapshot snapshot) {
        String mode=snapshot.preset==OutputMode.Preset.ADAPTIVE?c.getString(R.string.output_adaptive):snapshot.outputWidth>0 && snapshot.outputHeight>0 && snapshot.outputFps>0?
            c.getString(R.string.format_output_resolution_rate,snapshot.outputWidth,snapshot.outputHeight,Integer.toString(snapshot.outputFps)):c.getString(R.string.value_unknown);
        return c.getString(R.string.format_output_summary,mode,aspect(c,snapshot.aspect));
    }
    static String compactPrimary(Context c,GlobalSettings.Snapshot snapshot) {
        String rate=snapshot.preset==OutputMode.Preset.ADAPTIVE?c.getString(R.string.output_adaptive):snapshot.outputFps>0?c.getString(R.string.format_rate_hz,snapshot.outputFps):c.getString(R.string.value_unknown);
        return c.getString(R.string.format_output_summary,rate,aspect(c,snapshot.aspect));
    }
    static String compactSecondary(GlobalSettings.Snapshot snapshot) {
        if (snapshot.preset==OutputMode.Preset.ADAPTIVE || snapshot.outputWidth<=0 || snapshot.outputHeight<=0) return "";
        return snapshot.outputWidth+" × "+snapshot.outputHeight;
    }
    static String savedPrimary(Context c,OutputSpec spec) {
        String timing=spec.preset==null?c.getString(R.string.value_unchanged):spec.preset==OutputMode.Preset.ADAPTIVE?c.getString(R.string.output_adaptive):c.getString(R.string.format_rate_hz,spec.preset.fps);
        return c.getString(R.string.format_output_summary,timing,aspect(c,spec.aspect));
    }
    static String savedSecondary(OutputSpec spec) {
        return spec.preset==null || spec.preset==OutputMode.Preset.ADAPTIVE?"":spec.preset.width(spec.aspect)+" × "+spec.preset.height(spec.aspect);
    }
    static String savedSummary(Context c,OutputSpec spec) { return c.getString(R.string.format_output_summary,spec.preset==null?c.getString(R.string.value_unchanged):preset(c,spec.preset,spec.aspect),aspect(c,spec.aspect)); }
    static String key(Context c,GlobalChanges.Key key) {
        return c.getString(key==GlobalChanges.Key.SCREEN_OFF?R.string.label_screen_off:key==GlobalChanges.Key.SHOW_REFRESH?R.string.label_show_refresh_rate:R.string.setting_output_mode);
    }
}
