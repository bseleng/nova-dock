package local.nova.dock;

import java.util.HashMap;
import java.util.Map;

/** A press belongs to one window, physical device, key and original DOWN timestamp. */
final class PressGate {
    private final Map<Long,Long> down=new HashMap<>();

    boolean key(int key,int action,int device,long downTime,int repeat,boolean canceled,boolean focused) {
        if (!focused) { reset(); return false; }
        long identity=((long)key<<32)|(device&0xffffffffL);
        Long previous=down.get(identity);
        if (action==0) {
            if (canceled) {
                down.remove(identity);
            } else if (repeat==0) down.put(identity,downTime);
            return false;
        }
        if (previous==null || previous.longValue()!=downTime) return false;
        down.remove(identity); // Clear before the action opens or closes another window.
        return action==1 && !canceled;
    }
    void reset() { down.clear(); }
}
