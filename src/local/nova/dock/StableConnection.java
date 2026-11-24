package local.nova.dock;

/** Monotonic-time debounce. Unknown samples cannot become an unplug event. */
final class StableConnection {
    private Boolean candidate;
    private long since;
    Boolean update(Boolean observed, long now) {
        if (observed == null) { candidate = null; return null; }
        if (!observed.equals(candidate)) { candidate = observed; since = now; return null; }
        return now - since >= (observed ? 1200L : 2200L) ? candidate : null;
    }
}
