package local.nova.dock;

public final class HoldStateTest {
    private static void check(boolean condition,String message) { if (!condition) throw new AssertionError(message); }
    private static void near(float actual,float expected,String message) { check(Math.abs(actual-expected)<.001f,message+": "+actual); }
    public static void main(String[] arguments) {
        HoldState state=new HoldState();
        check(!state.active() && !state.tick(9999),"Idle holds must do nothing");
        check(state.begin(HoldState.Input.CONTROLLER,7,100,1000),"A fresh A press starts a hold");
        near(state.progress(1750),.5f,"Progress follows real elapsed time");
        check(!state.tick(2499),"A short hold cannot fire");
        check(state.tick(2500),"The continuous hold fires at 1500 ms");
        check(!state.tick(9000),"A completed hold never fires again"); near(state.progress(9000),1,"Completion remains visible until release");
        check(!state.begin(HoldState.Input.CONTROLLER,7,100,9000),"Repeat DOWN cannot rearm while held");
        check(!state.end(HoldState.Input.CONTROLLER,8,100),"A foreign-device release cannot cancel the owner");
        check(state.active(),"Foreign release preserves the owner's state");
        check(state.end(HoldState.Input.CONTROLLER,7,100),"The owner's release ends the hold");
        near(state.progress(9000),0,"Release clears progress");
        check(!state.begin(HoldState.Input.CONTROLLER,7,100,10000),"The same completed press cannot rearm after release");
        check(state.begin(HoldState.Input.CONTROLLER,7,101,10000),"A new DOWN may start the next hold");
        state.cancel(); check(!state.tick(20000),"Focus/window/cancel prevents a pending action");
        check(!state.begin(HoldState.Input.CONTROLLER,7,101,20000),"An interrupted press needs a fresh DOWN");

        check(state.begin(HoldState.Input.TOUCH,0,102,20000),"Touch has its own fresh press identity");
        check(!state.tick(20500),"A partial touch does not fire");
        check(!state.begin(HoldState.Input.CONTROLLER,7,103,20500),"Another input cannot inherit progress from a running hold");
        check(!state.end(HoldState.Input.TOUCH,1,102),"A different pointer cannot release the owner");
        near(state.progress(20500),1f/3f,"Foreign input did not change progress");
        state.cancel();
        check(state.begin(HoldState.Input.CONTROLLER,7,103,20600),"Input transfer after cancellation starts from zero");
        near(state.progress(20600),0,"No progress transfers from touch to A");
        check(!state.tick(22099) && state.tick(22100),"Transferred input still needs a complete duration");

        state.cancel(); check(state.begin(HoldState.Input.TOUCH,0,104,30000),"A later touch starts normally");
        check(!state.tick(29999),"Backward clock values cannot fire"); near(state.progress(29999),0,"Backward time cannot produce negative progress");
        state.end(HoldState.Input.TOUCH,0,104); check(!state.tick(40000),"Releasing a short touch cancels it");
        check(state.begin(HoldState.Input.TOUCH,0,105,40000),"A new touch after cancellation starts normally");
        check(state.tick(50000) && !state.tick(50001),"A delayed frame uses actual elapsed time and fires exactly once");
        boolean rejected=false; try { new HoldState(0); } catch (IllegalArgumentException expected) { rejected=true; }
        check(rejected,"A zero-duration hold is invalid");
        System.out.println("HoldState tests passed");
    }
}
