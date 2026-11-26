package local.nova.dock;

public final class SettingChangesTest {
    private static void check(boolean condition,String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void dependentFanOrder(boolean docked) {
        SettingChanges queue=new SettingChanges();
        Profile high=new Profile(docked,120,6,2,docked?2:0,docked);
        SettingChanges.Request bypass=queue.offer(high.next(Profile.BYPASS),Profile.BYPASS);
        check(queue.take()==bypass,"The worker must already be settling bypass before the burst");

        Profile standard=high.next(Profile.PERFORMANCE);
        SettingChanges.Request obsoletePerformance=queue.offer(standard,Profile.PERFORMANCE);
        Profile off=standard.next(Profile.FAN,6,0);
        SettingChanges.Request obsoleteFan=queue.offer(off,Profile.FAN);
        check(off.fan==0,"The explicit intermediate fan choice must be Off");
        Profile performance=off.next(Profile.PERFORMANCE);
        SettingChanges.Request finalPerformance=queue.offer(performance,Profile.PERFORMANCE);
        SettingChanges.Request finalFan=finalPerformance.rebasedFan;
        check(finalFan!=null && finalFan.profile.fan==4,"Pending Off must rebase to the final compatible Smart target");
        check(finalFan.revision>finalPerformance.revision,"The rebased fan must execute after final performance");
        check(!queue.current(obsoletePerformance) && !queue.current(obsoleteFan),"Neither obsolete dependency target may execute");
        check(!queue.acknowledged(docked,Profile.FAN,finalFan.revision),"A rebased optimistic fan target must wait for its own ACK");
        queue.complete(bypass);

        int livePerformance=2, liveFan=6;
        SettingChanges.Request next=queue.take();
        check(next==finalPerformance,"Coalescing must run final Performance before any fan write");
        livePerformance=next.profile.performance;
        // Performance preserves compatible Maximum until the explicit fan choice follows it.
        check(Profile.fanAllowed(livePerformance,liveFan),"The original Maximum must survive the performance transition");
        queue.complete(next);
        check(!queue.acknowledged(docked,Profile.FAN,finalFan.revision),"Performance settling must not ACK the pending fan");
        next=queue.take();
        check(next==finalFan && Profile.fanAllowed(livePerformance,next.profile.fan),"The final fan must be valid against actual performance");
        liveFan=next.profile.fan;
        queue.complete(next);
        check(livePerformance==1 && liveFan==4,"The burst must finish at Performance/Smart without a rejected Off write");
        check(queue.acknowledged(docked,Profile.FAN,finalFan.revision),"The final Smart target must receive its ACK");
        check(queue.take()==null,"Settling must leave no old fan write to replay over a later manual edit");
    }
    public static void main(String[] args) {
        SettingChanges queue=new SettingChanges();
        Profile handheld=Profile.defaults(false), docked=Profile.defaults(true);

        SettingChanges.Request first=queue.offer(handheld.next(Profile.PERFORMANCE),Profile.PERFORMANCE);
        SettingChanges.Request second=queue.offer(first.profile.next(Profile.PERFORMANCE),Profile.PERFORMANCE);
        check(second.revision>first.revision,"Revisions must increase");
        check(queue.take()==second,"A burst must retain only its final setting value");
        check(queue.take()==null,"Duplicate service wake-ups must not replay changes");
        check(!queue.current(first),"A superseded request must not begin another application");
        check(queue.current(second),"The latest in-flight request must remain current");
        check(queue.target(false,Profile.PERFORMANCE)==second,"A recreated activity must retain the latest in-flight optimistic target");
        check(!queue.acknowledged(false,Profile.PERFORMANCE,second.revision),"Taking a request must not acknowledge it");

        SettingChanges.Request third=queue.offer(handheld,Profile.PERFORMANCE);
        check(queue.unfinished(second),"An in-flight transition must finish restoring its compatible fan before the next request");
        queue.complete(second);
        check(!queue.acknowledged(false,Profile.PERFORMANCE,third.revision),"An old completion must not acknowledge a newer tap");
        check(!queue.current(second),"An old in-flight request must stay superseded");
        check(queue.take()==third,"An in-flight completion must not remove its replacement");
        queue.complete(third);
        check(queue.acknowledged(false,Profile.PERFORMANCE,third.revision),"The final request must receive an ACK");
        check(!queue.current(third),"An acknowledged request must never be applied again");
        check(queue.target(false,Profile.PERFORMANCE)==null,"An ACK must release the shared optimistic target");
        check(queue.take()==null,"A finished burst must leave no deferred replay");

        SettingChanges.Request fan=queue.offer(handheld.next(Profile.FAN),Profile.FAN);
        SettingChanges.Request bypass=queue.offer(handheld.next(Profile.BYPASS),Profile.BYPASS);
        SettingChanges.Request dockFan=queue.offer(docked.next(Profile.FAN),Profile.FAN);
        SettingChanges.Request finalFan=queue.offer(fan.profile.next(Profile.FAN),Profile.FAN);
        check(queue.take()==bypass,"Coalescing one setting must preserve the order of other settings");
        check(queue.take()==dockFan,"Dock and handheld mailboxes must remain independent");
        check(queue.take()==finalFan,"A replacement must retain its immutable profile snapshot");
        check(finalFan.profile.fan!=fan.profile.fan,"Replacing a request must not mutate an earlier snapshot");
        check(queue.current(dockFan),"A handheld edit must not cancel an in-flight dock edit");

        SettingChanges.Request canceled=queue.offer(handheld,Profile.RATE);
        queue.cancelAll();
        check(queue.take()==null,"Stopping must discard pending work");
        check(!queue.current(dockFan),"Stopping must invalidate in-flight work");
        check(!queue.unfinished(dockFan),"Stopping must also cancel dependency writes");
        check(queue.acknowledged(false,Profile.RATE,canceled.revision),"Stopping must release a pending UI overlay");
        SettingChanges.Request resumed=queue.offer(handheld,Profile.RATE);
        check(resumed.revision>canceled.revision && queue.current(resumed),"Resume must use a fresh revision");
        check(!queue.acknowledged(false,Profile.RATE,resumed.revision),"A prior stop ACK must not acknowledge resumed work");
        queue.complete(resumed);
        check(queue.take()==null,"A failed service start acknowledged before take must leave no replay");

        dependentFanOrder(false);
        dependentFanOrder(true);
        queue=new SettingChanges();
        SettingChanges.Request unrelatedFan=queue.offer(docked,Profile.FAN);
        SettingChanges.Request performance=queue.offer(handheld.next(Profile.PERFORMANCE),Profile.PERFORMANCE);
        check(performance.rebasedFan==null && queue.take()==unrelatedFan,"Performance must not rebase another mode's fan");
        Profile quiet=new Profile(false,120,1,0,0,false);
        queue=new SettingChanges();
        queue.offer(quiet,Profile.FAN);
        performance=queue.offer(quiet.next(Profile.PERFORMANCE),Profile.PERFORMANCE);
        check(performance.rebasedFan.profile.fan==1,"Rebasing must preserve a compatible explicit Quiet choice");
        check(queue.take()==performance && queue.take()==performance.rebasedFan,"A compatible fan must still follow final performance");
        System.out.println("SettingChanges tests passed");
    }
}
