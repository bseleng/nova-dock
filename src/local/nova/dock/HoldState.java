package local.nova.dock;

/** A continuous hold belongs to one physical press; finishing never rearms that press. */
final class HoldState {
    enum Input { CONTROLLER, TOUCH }
    static final long DURATION_MS=1500L;
    private final long duration;
    private Input input,lastInput;
    private int owner,lastOwner;
    private long press,lastPress,started;
    private boolean active,fired,seen;

    HoldState() { this(DURATION_MS); }
    HoldState(long duration) { if (duration<=0) throw new IllegalArgumentException("invalid_value"); this.duration=duration; }

    boolean begin(Input input,int owner,long press,long now) {
        if (input==null) throw new IllegalArgumentException("invalid_value");
        if (active || seen && input==lastInput && owner==lastOwner && press==lastPress) return false;
        this.input=input; this.owner=owner; this.press=press; started=now;
        lastInput=input; lastOwner=owner; lastPress=press; seen=true; active=true; fired=false; return true;
    }
    boolean owns(Input input,int owner,long press) { return active && this.input==input && this.owner==owner && this.press==press; }
    boolean end(Input input,int owner,long press) { if (!owns(input,owner,press)) return false; cancel(); return true; }
    boolean tick(long now) {
        if (!pending() || elapsed(now)<duration) return false;
        fired=true; return true;
    }
    float progress(long now) { return !active?0:fired?1:(float)Math.min(1d,elapsed(now)/(double)duration); }
    boolean active() { return active; }
    boolean pending() { return active && !fired; }
    Input input() { return active?input:null; }
    void cancel() { active=false; fired=false; input=null; }
    private long elapsed(long now) { return now<=started?0:now-started; }
}
