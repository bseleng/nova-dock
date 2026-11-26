package local.nova.dock;

public final class AnalogScrollStateTest {
    private static void check(boolean condition,String message) { if (!condition) throw new AssertionError(message); }
    private static void near(double actual,double expected,double tolerance,String message) { check(Math.abs(actual-expected)<=tolerance,message+": "+actual+" != "+expected); }
    private static int distance(int hz,float axis,float density) {
        AnalogScrollState state=new AnalogScrollState(); state.axis(axis,0); int distance=0;
        for (int frame=0;frame<=hz;frame++) distance+=state.frame(Math.round(frame*1_000_000_000d/hz),density);
        return distance;
    }
    public static void main(String[] arguments) {
        check(AnalogScrollState.speed(0,0)==0,"Neutral stick must not scroll");
        check(AnalogScrollState.speed(.17f,0)==0,"Drift inside the application dead zone must not scroll");
        check(AnalogScrollState.speed(AnalogScrollState.DEAD_ZONE,0)==0,"Dead-zone boundary must be neutral");
        check(AnalogScrollState.speed(.3f,.35f)==0,"Larger hardware dead zone must be honored");
        check(AnalogScrollState.speed(.7f,.75f)==0 && AnalogScrollState.speed(1,1)==0,"Hardware dead zones must not be reduced");
        check(AnalogScrollState.speed(Float.NaN,0)==0 && AnalogScrollState.speed(Float.POSITIVE_INFINITY,0)==0,"Invalid axes must stop scrolling");
        near(AnalogScrollState.speed(1,0),AnalogScrollState.MAX_SPEED_DP,.0001,"Full positive deflection");
        near(AnalogScrollState.speed(-1,0),-AnalogScrollState.MAX_SPEED_DP,.0001,"Full negative deflection");
        near(AnalogScrollState.speed(3,0),AnalogScrollState.MAX_SPEED_DP,.0001,"Out-of-range input must clamp");
        check(AnalogScrollState.speed(.5f,0)>0 && AnalogScrollState.speed(.5f,0)<AnalogScrollState.speed(.8f,0),"Speed must increase with stick amplitude");
        near(distance(60,1,1),900,1,"One second at 60 Hz");
        near(distance(120,1,1),900,1,"One second at 120 Hz");
        near(distance(144,1,2),1800,1,"Density scaling at 144 Hz");
        near(distance(60,.4f,2),distance(120,.4f,2),1,"Fractional motion must survive refresh-rate differences");
        near(distance(60,-1,1),-900,1,"Negative direction must be symmetric");

        AnalogScrollState state=new AnalogScrollState();
        state.axis(1,0); check(state.frame(0,1)==0,"Initial frame establishes the time origin");
        check(state.frame(1_000_000_000L,1)==45,"A delayed frame must clamp to 50 ms");
        check(state.frame(900_000_000L,1)==0,"Out-of-order frames must not move or rewind the clock");
        check(state.frame(1_010_000_000L,1)==9,"A valid frame after an out-of-order timestamp must use the last valid time");
        state.axis(0,0); check(!state.active() && state.direction()==0,"Neutral event stops immediately");
        check(state.frame(2_000_000_000L,1)==0,"Stopped scrolling has no inertia");
        state.axis(-1,0); check(state.direction()==-1 && state.frame(3_000_000_000L,1)==0,"Restart must reset its time origin");
        check(state.frame(3_010_000_000L,1)==-9,"Restarted motion must use only elapsed active time");
        state.stop(); check(!state.active() && state.frame(4_000_000_000L,1)==0,"Pause/dismiss stop clears pending displacement");
        state.axis(.3f,0); state.frame(5_000_000_000L,1); state.frame(5_001_000_000L,1);
        state.axis(-1,0); check(state.frame(5_002_000_000L,1)==0,"Direction change resets fractional motion and clock");
        check(state.frame(5_012_000_000L,1)==-9,"A reversal must not retain positive fractional displacement");
        state.axis(Float.NaN,0); check(!state.active(),"Invalid input clears active motion");
        System.out.println("AnalogScrollState tests passed");
    }
}
