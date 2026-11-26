package local.nova.dock;

public final class PressGateTest {
    private static void check(boolean condition,String message) { if (!condition) throw new AssertionError(message); }
    private static boolean key(PressGate gate,int key,int action,int device,long time,int repeat,boolean canceled) {
        return gate.key(key,action,device,time,repeat,canceled,true);
    }
    public static void main(String[] arguments) {
        PressGate gate=new PressGate();
        check(!key(gate,1,1,10,100,0,false),"An orphan UP must not activate a new window");
        check(!key(gate,1,0,10,100,0,false),"DOWN only arms a press");
        check(key(gate,1,1,10,100,0,false),"Matching UP activates exactly once");
        check(!key(gate,1,1,10,100,0,false),"A duplicate UP cannot activate twice");

        key(gate,1,0,10,200,0,false);
        check(!key(gate,1,0,10,200,1,false) && !key(gate,1,0,10,200,8,false),"Held-button repeats never activate");
        check(key(gate,1,1,10,200,8,false),"A held press activates once on release");
        check(!key(gate,1,0,10,300,2,false) && !key(gate,1,1,10,300,0,false),"A repeat without a fresh DOWN cannot arm");

        key(gate,1,0,10,400,0,false);
        check(!key(gate,1,1,10,400,0,true),"Canceled release does not activate");
        check(!key(gate,1,1,10,400,0,false),"Canceled press cannot revive");
        key(gate,1,0,10,500,0,true);
        check(!key(gate,1,1,10,500,0,false),"Canceled DOWN cannot arm");

        key(gate,1,0,10,600,0,false);
        check(!gate.key(1,1,10,600,0,false,false),"An unfocused owner never acts");
        check(!key(gate,1,1,10,600,0,false),"Focus loss clears the old press");
        key(gate,1,0,10,700,0,false); gate.reset();
        check(!key(gate,1,1,10,700,0,false),"Pause/detach reset prevents cross-window releases");

        key(gate,1,0,10,800,0,false);
        check(!key(gate,1,1,11,800,0,false),"Another device cannot release this press");
        check(key(gate,1,1,10,800,0,false),"A foreign-device release does not disarm the owner");
        key(gate,1,0,10,900,0,false); key(gate,1,0,10,901,0,false);
        check(!key(gate,1,1,10,900,0,false),"An old release cannot activate a newer press");
        check(key(gate,1,1,10,901,0,false),"An old release cannot disarm a newer press");

        key(gate,1,0,10,1000,0,false); key(gate,2,0,10,1000,0,false);
        check(key(gate,2,1,10,1000,0,false) && key(gate,1,1,10,1000,0,false),"Different keys keep independent press ownership");
        key(gate,1,0,10,1100,0,false); key(gate,1,0,11,1100,0,false);
        check(key(gate,1,1,10,1100,0,false) && key(gate,1,1,11,1100,0,false),"Each device retains its matching press");
        key(gate,1,0,10,1200,0,false);
        check(!key(gate,1,2,10,1200,0,false) && !key(gate,1,1,10,1200,0,false),"Other key phases clear a matching press without firing");
        key(gate,1,0,10,1300,0,false); key(gate,1,0,10,1301,0,true);
        check(!key(gate,1,1,10,1300,0,false),"A canceled DOWN invalidates an older held press of that key");
        System.out.println("PressGate tests passed");
    }
}
