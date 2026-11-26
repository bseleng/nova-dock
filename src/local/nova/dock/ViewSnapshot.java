package local.nova.dock;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable worker read; rendering never calls PServer or Settings.System on the UI thread. */
final class ViewSnapshot {
    final Map<String,String> runtime;
    final GlobalSettings.Snapshot globals;
    final boolean supported,permitted;
    final String globalError;
    ViewSnapshot(Map<String,String> runtime,GlobalSettings.Snapshot globals,boolean supported,boolean permitted,String globalError) {
        this.runtime=Collections.unmodifiableMap(new LinkedHashMap<>(runtime)); this.globals=globals;
        this.supported=supported; this.permitted=permitted; this.globalError=globalError;
    }
}
