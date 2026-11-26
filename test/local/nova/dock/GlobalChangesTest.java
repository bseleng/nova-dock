package local.nova.dock;

public final class GlobalChangesTest {
    private static void check(boolean value,String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        GlobalChanges queue=new GlobalChanges();
        GlobalChanges.Request first=queue.offer(GlobalChanges.Key.SCREEN_OFF,false,null);
        GlobalChanges.Request second=queue.offer(GlobalChanges.Key.SCREEN_OFF,true,null);
        check(queue.take()==second && queue.take()==null && !queue.current(first),"Global taps must coalesce independently per key");
        queue.complete(first);
        check(!queue.acknowledged(second.key,second.revision),"An old completion must not acknowledge a newer target");
        queue.complete(second,"global_write_failed","");
        check(queue.target(second.key)==null && queue.result(second.key).error.equals("global_write_failed"),"A failed write must release optimistic UI and retain its own error");
        GlobalChanges.Request read=queue.offer(GlobalChanges.Key.READ,false,null); queue.take(); queue.complete(read);
        check(queue.result(second.key).error.equals("global_write_failed"),"A later read must not erase an unconsumed mutation result");
        check(queue.takeError(second.key)!=null && queue.takeError(second.key)==null,"A recreated activity must be able to consume a failed mutation exactly once");

        queue=new GlobalChanges();
        queue.offer(GlobalChanges.Key.OUTPUT,false,new OutputMode.Choice(null,OutputMode.Aspect.SIXTEEN_NINE,false,true));
        GlobalChanges.Request merged=queue.offer(GlobalChanges.Key.OUTPUT,false,new OutputMode.Choice(OutputMode.Preset.QHD120,OutputMode.Aspect.FOUR_THREE,true,false));
        check(merged.output.changeMode && merged.output.changeAspect && merged.output.aspect==OutputMode.Aspect.SIXTEEN_NINE && merged.output.preset==OutputMode.Preset.QHD120,"Mode-only coalescing must retain a preceding explicit aspect change");
        check(queue.take()==merged && queue.take()==null,"The merged choice must execute only once");
        queue=new GlobalChanges();
        queue.offer(GlobalChanges.Key.OUTPUT,false,new OutputMode.Choice(OutputMode.Preset.UHD60,OutputMode.Aspect.FOUR_THREE,true,false));
        merged=queue.offer(GlobalChanges.Key.OUTPUT,false,new OutputMode.Choice(null,OutputMode.Aspect.SIXTEEN_NINE,false,true));
        check(merged.output.preset==OutputMode.Preset.UHD60 && merged.output.changeMode && merged.output.changeAspect,"Aspect-only coalescing must retain a preceding explicit mode change");

        queue=new GlobalChanges(); first=queue.offer(GlobalChanges.Key.SCREEN_OFF,false,null); long cutoff=queue.revision(); queue.cancelThrough(cutoff);
        second=queue.offer(GlobalChanges.Key.SCREEN_OFF,true,null); queue.cancelThrough(cutoff);
        check(queue.current(second) && !queue.acknowledged(second.key,second.revision) && queue.take()==second,"Late STOP/RESTORE delivery must not cancel globals dispatched after its cutoff");
        queue.complete(first); check(!queue.acknowledged(second.key,second.revision),"Old canceled completion must not swallow the new global request");
        queue.complete(second);
        long oldCleanupRevision=queue.revision(); final boolean[] stopped={false};
        check(queue.ifIdle(oldCleanupRevision,()->stopped[0]=true) && stopped[0],"An idle unmanaged instance may begin cleanup");
        GlobalChanges.Request afterStopSelf=queue.offer(GlobalChanges.Key.SHOW_REFRESH,true,null);
        // Old onDestroy owns no mailbox cancellation. Only an explicit STOP/RESTORE cutoff cancels requests.
        queue.complete(second);
        check(queue.current(afterStopSelf) && queue.take()==afterStopSelf,"A request offered between old stopSelf and onDestroy must survive for the next worker");
        queue.complete(afterStopSelf);
        GlobalChanges.Request newest=queue.offer(GlobalChanges.Key.SHOW_REFRESH,false,null);
        check(!queue.ifIdle(oldCleanupRevision,()->{ throw new AssertionError("Stale cleanup ran"); }) && queue.current(newest),"Old cleanup must never stop a newer global session");
        SettingChanges profiles=new SettingChanges(); profiles.cancelAll();
        check(queue.current(newest),"Profile/mode cancellation must not cancel independent global work");
        System.out.println("GlobalChanges tests passed");
    }
}
