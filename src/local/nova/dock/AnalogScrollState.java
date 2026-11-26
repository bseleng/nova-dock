package local.nova.dock;

/** Frame-rate-independent right-stick velocity, expressed in density-independent pixels. */
final class AnalogScrollState {
    static final float DEAD_ZONE=.18f;
    static final double MAX_SPEED_DP=900d;
    static final long MAX_FRAME_NANOS=50_000_000L;
    private double velocity,fraction;
    private long lastFrame;
    private boolean timed;

    boolean axis(float value,float flat) {
        double next=speed(value,flat);
        if (next==0) { stop(); return false; }
        if (velocity==0 || Math.signum(next)!=Math.signum(velocity)) {
            fraction=0; timed=false;
        }
        velocity=next; return true;
    }

    int frame(long timeNanos,float density) {
        if (!active()) return 0;
        if (!timed) { lastFrame=timeNanos; timed=true; return 0; }
        long elapsed=timeNanos-lastFrame;
        if (elapsed<=0) return 0;
        lastFrame=timeNanos;
        double scale=Float.isNaN(density)||Float.isInfinite(density)||density<=0?1d:density;
        fraction+=velocity*scale*Math.min(elapsed,MAX_FRAME_NANOS)/1_000_000_000d;
        int distance=(int)fraction;
        fraction-=distance;
        return distance;
    }

    boolean active() { return velocity!=0; }
    int direction() { return velocity>0?1:velocity<0?-1:0; }
    void stop() { velocity=0; fraction=0; timed=false; }

    static double speed(float value,float flat) {
        if (Float.isNaN(value)||Float.isInfinite(value)) return 0;
        float dead=Float.isNaN(flat)||Float.isInfinite(flat)?DEAD_ZONE:Math.max(DEAD_ZONE,Math.min(1f,flat));
        double amplitude=Math.min(1d,Math.abs((double)value));
        if (amplitude<=dead) return 0;
        double normalized=(amplitude-dead)/(1d-dead);
        return Math.copySign(MAX_SPEED_DP*normalized*normalized,value);
    }
}
