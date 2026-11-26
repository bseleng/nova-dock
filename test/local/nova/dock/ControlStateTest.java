package local.nova.dock;

public final class ControlStateTest {
    private static void check(boolean condition,String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void delayedRestore(boolean automatic) {
        ControlState.Revisions revisions=new ControlState.Revisions();
        ControlState state=new ControlState(true,false,false,false);
        long restore=revisions.next(); state=state.stopped(); // Dispatch RESTORE before its intent is delivered.
        long newer=revisions.next(); state=automatic?state.automatic(true):state.manual(true);
        long received=revisions.receiveStop(restore,state.managed);
        check(received==0,"RESTORE delivered after a newer manual/auto dispatch must be ignored");
        check(revisions.latest()==newer,"Delivering a stale RESTORE must not create a new control revision");
        final boolean[] wroteOrStopped={false};
        if (received!=0) revisions.ifCurrent(received,()->wroteOrStopped[0]=true);
        check(!wroteOrStopped[0] && state.managed,"A stale RESTORE must not stop the session, restore hardware, or clean up foreground state");
        check(state.automatic==automatic && (automatic || state.manualDocked),"The newer requested control state must survive delayed RESTORE");
        check(revisions.current(newer),"The newer manual/auto command must remain executable after delayed RESTORE");
    }
    public static void main(String[] args) {
        ControlState legacy=new ControlState(null,true,null,true);
        check(legacy.managed && legacy.manualDocked,"Legacy enabled sessions must retain their last applied mode");
        ControlState manual=legacy.automatic(false);
        check(manual.managed && !manual.automatic && manual.manualDocked && manual.lastDocked,"Auto OFF must atomically retain ownership and dock mode");
        check(!manual.needsManualApply(true,false),"Auto OFF and repeated manual Dock must preserve runtime overrides");
        check(manual.needsManualApply(null,false),"A cold manual session must apply its saved mode once");
        check(manual.needsManualApply(true,true),"Explicit retry must reapply the active manual mode");
        manual=manual.manual(false);
        check(manual.lastDocked && !manual.manualDocked,"A requested manual mode must not replace the actual mode before application");
        check(manual.needsManualApply(true,false),"Choosing another manual mode must apply it");
        manual=manual.applied(true);
        check(!manual.manualDocked,"An old in-flight transition must not overwrite a later explicit manual choice");
        manual=manual.applied(false);
        check(!manual.needsManualApply(false,false),"Reopening a live manual session must not reapply its profile");
        ControlState dirty=new ControlState(true,true,false,false).automatic(false).freezeManual(null);
        check(dirty.managed && !dirty.automatic && !dirty.manualDocked && !dirty.lastDocked,"Auto OFF after a dirty failed transition must retain only the last successful manual target");
        check(dirty.needsManualApply(null,false),"Unknown/dirty actual state must remain eligible for a complete explicit Handheld apply, not a same-mode NO-OP");
        check(!dirty.applied(false).needsManualApply(false,false),"Only a genuinely completed Handheld transition may become a same-mode NO-OP");
        ControlState settledDock=dirty.applied(true).freezeManual(true);
        check(settledDock.manualDocked && settledDock.lastDocked && !settledDock.needsManualApply(true,false),"Auto OFF after a started successful Dock transition must freeze its settled runtime without reapplying");
        ControlState automatic=manual.automatic(true);
        check(automatic.managed && automatic.automatic && !automatic.lastDocked,"Auto ON must retain the current applied mode");
        check(!automatic.needsManualApply(false,true),"Automatic sessions must use display debounce rather than manual apply");
        check(manual.shouldResume(true,true) && !manual.shouldResume(true,false),"Boot startup must honor its flag for manual sessions");
        check(manual.shouldResume(false,false),"APK replacement must resume manual sessions independently of the boot flag");
        check(!manual.stopped().shouldResume(false,true),"Stopped/restored sessions must remain stopped on reopen or update");

        ControlState.Revisions revisions=new ControlState.Revisions();
        final ControlState[] state={legacy};
        long off=revisions.next(); state[0]=state[0].automatic(false);
        long dock=revisions.next(); state[0]=state[0].manual(true);
        long handheld=revisions.next(); state[0]=state[0].manual(false);
        check(!revisions.ifCurrent(off,()->state[0]=state[0].manual(true)),"Deferred Auto OFF must not overwrite the last manual selection");
        check(!revisions.ifCurrent(dock,()->state[0]=state[0].applied(true)),"A superseded manual Dock command must not execute");
        check(revisions.ifCurrent(handheld,()->state[0]=state[0].applied(false)),"The latest manual Handheld command must execute");
        check(!state[0].manualDocked && !state[0].lastDocked,"The final manual selection and applied mode must agree");

        // The worker has begun Dock writes but still reports the previous Handheld mode.
        state[0]=state[0].manual(true);
        check(state[0].needsManualApply(false,false),"Dock must begin from an active Handheld session");
        state[0]=state[0].manual(false);
        state[0]=state[0].applied(true); // Complete the already-started Dock transition in order.
        check(!state[0].manualDocked && state[0].lastDocked,"Finishing dirty Dock must preserve the later Handheld request");
        check(state[0].needsManualApply(true,false),"Handheld must fully apply after dirty Dock, despite initially being the old active mode");
        state[0]=state[0].applied(false);
        check(!state[0].needsManualApply(false,false),"Only the settled final Handheld may become a same-mode NO-OP");
        state[0]=state[0].manual(true).manual(false).manual(true).applied(true);
        check(state[0].manualDocked && !state[0].needsManualApply(true,false),"Dock/Handheld/Dock must finish at the last Dock selection without redundant replay");

        long stop=revisions.next(); state[0]=state[0].stopped();
        long restarted=revisions.next(); state[0]=state[0].automatic(true);
        final boolean[] stopped={false};
        check(!revisions.ifCurrent(stop,()->stopped[0]=true) && !stopped[0],"An old STOP/RESTORE cleanup must not stop a newer session");
        check(revisions.current(restarted) && state[0].managed,"Restarted ownership must survive old cleanup");
        delayedRestore(false);
        delayedRestore(true);
        long notificationStop=revisions.receiveStop(null,true);
        check(notificationStop>restarted && revisions.current(notificationStop),"A notification STOP without a revision must be assigned one on receipt");
        ControlState.Revisions recovered=new ControlState.Revisions();
        check(recovered.receiveStop(7L,true)==0,"A cold stale RESTORE must not replace a persisted managed session");
        check(recovered.receiveStop(7L,false)==7 && recovered.current(7),"A current dispatched RESTORE must survive process death while unmanaged");
        System.out.println("ControlState tests passed");
    }
}
