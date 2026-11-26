package local.nova.dock;

/** Immutable per-profile choice. A null preset is the app-level KEEP option, not stock Adaptive. */
final class OutputSpec {
    final OutputMode.Aspect aspect;
    final OutputMode.Preset preset;
    OutputSpec(OutputMode.Aspect aspect,OutputMode.Preset preset) {
        if (aspect==null) throw new IllegalArgumentException("invalid_value");
        this.aspect=aspect; this.preset=preset;
    }
    static OutputSpec defaults(boolean docked) { return new OutputSpec(OutputMode.Aspect.FOUR_THREE,null); }
    static OutputSpec stored(boolean docked,String aspect,String preset) {
        OutputSpec fallback=defaults(docked);
        try { return new OutputSpec(aspect==null?fallback.aspect:OutputMode.Aspect.valueOf(aspect),preset==null || "KEEP".equals(preset)?null:OutputMode.Preset.valueOf(preset)); }
        catch (IllegalArgumentException error) { return fallback; }
    }
    String presetCode() { return preset==null?"KEEP":preset.name(); }
    boolean matches(OutputMode.Aspect liveAspect,OutputMode.Preset livePreset) { return aspect==liveAspect && (preset==null || preset==livePreset); }
    OutputMode.Choice plan(OutputMode.Aspect liveAspect,OutputMode.Preset livePreset) {
        boolean changeAspect=aspect!=liveAspect;
        if (preset!=null) return new OutputMode.Choice(preset,aspect,true,changeAspect);
        if (!changeAspect) return null;
        // Like RPSettings: changing ratio re-applies a recognized current family for that ratio.
        return new OutputMode.Choice(livePreset,aspect,livePreset!=null,true);
    }
    @Override public boolean equals(Object other) { return other instanceof OutputSpec && aspect==((OutputSpec)other).aspect && preset==((OutputSpec)other).preset; }
    @Override public int hashCode() { return 31*aspect.hashCode()+(preset==null?0:preset.hashCode()); }
}
