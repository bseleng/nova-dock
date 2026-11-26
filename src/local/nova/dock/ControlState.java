package local.nova.dock;

/** Session ownership is independent of automatic display detection. */
final class ControlState {
    static final class Revisions {
        private long revision;
        synchronized long next() { return ++revision; }
        synchronized long latest() { return revision; }
        synchronized boolean current(long observed) { return revision==observed; }
        synchronized long receiveStop(Long dispatched,boolean managed) {
            if (dispatched==null) return ++revision; // A notification STOP is issued on receipt.
            if (dispatched<=0) return 0;
            if (revision==0) {
                if (managed) return 0; // A newer persisted session survived process death.
                revision=dispatched;
            }
            return revision==dispatched?revision:0;
        }
        synchronized boolean ifCurrent(long observed,Runnable action) {
            if (revision!=observed) return false;
            action.run(); return true;
        }
    }
    final boolean managed, automatic, manualDocked, lastDocked;
    ControlState(Boolean managed,boolean automatic,Boolean manualDocked,boolean lastDocked) {
        this.managed=managed==null?automatic:managed;
        this.automatic=automatic;
        this.manualDocked=manualDocked==null?lastDocked:manualDocked;
        this.lastDocked=lastDocked;
    }
    ControlState automatic(boolean enabled) {
        return new ControlState(true,enabled,enabled?manualDocked:lastDocked,lastDocked);
    }
    ControlState manual(boolean docked) {
        return new ControlState(true,false,docked,lastDocked);
    }
    ControlState applied(boolean docked) {
        return new ControlState(managed,automatic,manualDocked,docked);
    }
    ControlState stopped() { return new ControlState(false,false,manualDocked,lastDocked); }
    ControlState freezeManual(Boolean settled) { return new ControlState(managed,automatic,settled==null?lastDocked:settled,lastDocked); }
    boolean needsManualApply(Boolean applied,boolean force) {
        return managed && !automatic && (force || applied==null || applied!=manualDocked);
    }
    boolean shouldResume(boolean boot,boolean startOnBoot) { return managed && (!boot || startOnBoot); }
    boolean activeProfile(boolean docked) { return managed && lastDocked==docked; }
}
