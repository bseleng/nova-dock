package local.nova.dock;

public final class ProfileControllerTest {
    private static void check(boolean condition,String message) { if (!condition) throw new AssertionError(message); }
    private static void unchanged(Profile before,Profile after) {
        check(before.docked==after.docked && before.rate==after.rate && before.fan==after.fan
            && before.performance==after.performance && before.bypassCharging==after.bypassCharging,"Controller changes must preserve every other profile field");
    }
    private static void invalid(boolean docked,int layout) {
        try { new Profile(docked,60,4,1,layout,false); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid saved controller accepted: "+docked+"/"+layout);
    }
    public static void main(String[] args) {
        Profile dockDefault=Profile.defaults(true),handDefault=Profile.defaults(false);
        check(dockDefault.layout==2 && handDefault.layout==0,"Existing defaults must remain Dock Off / Handheld Xbox");
        check(dockDefault.rate==60 && dockDefault.fan==5 && dockDefault.performance==2 && dockDefault.bypassCharging,"Dock defaults must match the owner's release preset: 60 Hz / Sport / High Performance / bypass on");
        check(handDefault.rate==120 && handDefault.fan==1 && handDefault.performance==0 && !handDefault.bypassCharging,"All other Handheld defaults must remain unchanged");
        for (int layout=0;layout<=2;layout++) check(new Profile(true,60,4,1,layout,false).layout==layout,"Dock constructor must retain the explicit saved choice");
        for (int layout=0;layout<=1;layout++) check(new Profile(false,60,4,1,layout,false).layout==layout,"Handheld constructor must retain its saved choice");
        invalid(true,-1); invalid(true,3); invalid(false,-1); invalid(false,2);

        Profile off=new Profile(true,120,4,1,2,false),xbox=off.next(Profile.CONTROLLER),retro=xbox.next(Profile.CONTROLLER),again=retro.next(Profile.CONTROLLER);
        check(xbox.layout==0 && retro.layout==1 && again.layout==2,"Dock cycle must be Off -> Xbox -> Retro -> Off");
        unchanged(off,xbox); unchanged(off,retro); unchanged(off,again);
        check(off.layout==2 && xbox!=off && retro!=xbox,"A controller edit must leave previous immutable snapshots intact");
        check(off.next(Profile.CONTROLLER,0,off.performance).layout==1,"Active Dock editing must cycle from live Xbox to Retro");
        check(xbox.next(Profile.CONTROLLER,1,xbox.performance).layout==2,"Active Dock editing must cycle from live Retro to Off");
        check(retro.next(Profile.CONTROLLER,2,retro.performance).layout==0,"Active Dock editing must cycle from a temporary Off override to Xbox");
        for (int setting:new int[]{Profile.RATE,Profile.FAN,Profile.PERFORMANCE,Profile.BYPASS}) check(xbox.next(setting).layout==0,"Editing another setting must preserve a saved Dock Xbox choice");
        check(xbox.body().contains("Xbox") && retro.body().contains("Retro") && off.body().contains("выключен"),"Legacy profile descriptions must reflect the saved Dock controller");
        Profile hand=handDefault;
        for (int expected:new int[]{1,0,1,0}) {
            Profile next=hand.next(Profile.CONTROLLER); check(next.layout==expected,"Handheld must keep its Xbox / Retro cycle"); unchanged(hand,next); hand=next;
        }
        check(handDefault.next(Profile.CONTROLLER,2,handDefault.performance).layout==0,"A live Handheld Off override must return to Xbox without saving an unsupported Off preset");

        SettingChanges queue=new SettingChanges();
        SettingChanges.Request first=queue.offer(xbox,Profile.CONTROLLER); check(queue.take()==first,"Dock controller changes must enter the normal setting queue");
        SettingChanges.Request obsolete=queue.offer(retro,Profile.CONTROLLER),last=queue.offer(again,Profile.CONTROLLER);
        SettingChanges.Request handheld=queue.offer(handDefault.next(Profile.CONTROLLER),Profile.CONTROLLER);
        check(!queue.current(first) && !queue.current(obsolete) && queue.current(last),"Rapid Dock controller changes must keep the final explicit target");
        check(queue.target(true,Profile.CONTROLLER)==last && last.profile.layout==2,"A recreated UI must retain the final Dock Off target until ACK");
        queue.complete(first);
        check(!queue.acknowledged(true,Profile.CONTROLLER,last.revision),"An older Dock completion must not acknowledge a newer controller choice");
        check(queue.take()==last && last.profile.layout==2,"The final Dock Off request must follow the already-started Xbox write");
        queue.complete(last);
        check(queue.target(true,Profile.CONTROLLER)==null && queue.take()==handheld,"Dock ACK must leave Handheld controller work independent");
        queue.complete(handheld); check(queue.take()==null,"No superseded controller choice may replay after settling");
        System.out.println("ProfileController tests passed");
    }
}
