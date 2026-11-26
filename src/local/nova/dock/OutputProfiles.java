package local.nova.dock;

import android.content.Context;
import java.util.Map;

/** App-owned profile storage, independent of live native output and private RPSettings prefs. */
final class OutputProfiles {
    static OutputSpec get(Context context,boolean docked) {
        Map<String,?> values=Store.prefs(context).getAll(); String prefix=docked?"profile_dock_output_":"profile_handheld_output_";
        try { return OutputSpec.stored(docked,(String)values.get(prefix+"aspect"),(String)values.get(prefix+"preset")); }
        catch (ClassCastException error) { return OutputSpec.defaults(docked); }
    }
    static void save(Context context,boolean docked,OutputSpec spec) {
        String prefix=docked?"profile_dock_output_":"profile_handheld_output_";
        if (!Store.prefs(context).edit().putString(prefix+"aspect",spec.aspect.name()).putString(prefix+"preset",spec.presetCode()).commit()) throw new Failure("profile_save_failed");
    }
}
