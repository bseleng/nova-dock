package local.nova.dock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class OutputSpecTest {
    private static void check(boolean value,String message) { if (!value) throw new AssertionError(message); }
    private static final class NativeState implements OutputTransaction.Port {
        Integer width=null,height=null;
        final String[] properties={"1","1280","960","60"};
        final List<String> writes=new ArrayList<>();
        public void window(Integer width,Integer height) { this.width=width; this.height=height; writes.add("wm"); }
        public void property(OutputTransaction.Property key,String value) { properties[key.ordinal()]=value; writes.add(key.name()); }
        public void verify() { }
        OutputMode.Aspect aspect() { return OutputMode.Aspect.of(width==null?960:width,height==null?1280:height); }
        OutputMode.Preset preset() { return OutputMode.Preset.infer(properties[0],Integer.parseInt(properties[1]),Integer.parseInt(properties[2]),Integer.parseInt(properties[3]),aspect()); }
        void apply(OutputSpec spec) throws Exception {
            OutputMode.Choice plan=spec.plan(aspect(),preset());
            if (plan==null) return;
            OutputTransaction.Before before=new OutputTransaction.Before(width!=null,width==null?960:width,height==null?1280:height,properties);
            OutputTransaction.apply(this,before,plan.preset,plan.aspect,plan.changeMode,plan.changeAspect);
        }
    }
    public static void main(String[] args) throws Exception {
        OutputSpec dock=OutputSpec.defaults(true),hand=OutputSpec.defaults(false);
        check(dock.aspect==OutputMode.Aspect.FOUR_THREE && hand.aspect==OutputMode.Aspect.FOUR_THREE && dock.preset==null && hand.preset==null,"Both release output defaults must be 4:3 + KEEP");
        check(OutputSpec.stored(true,null,null).equals(dock) && OutputSpec.stored(false,null,null).equals(hand),"Existing installations without output keys must migrate by reading the same defaults, without adopting live globals");
        check(OutputSpec.stored(true,"future","future").equals(dock),"Invalid profile storage must fall back safely");
        OutputSpec wide=new OutputSpec(OutputMode.Aspect.SIXTEEN_NINE,null);
        check(OutputSpec.stored(true,wide.aspect.name(),wide.presetCode()).equals(wide),"Saved KEEP must round-trip independently for each owner mode");
        ControlState manualHand=new ControlState(true,false,false,false);
        check(!manualHand.activeProfile(true) && manualHand.activeProfile(false),"Saving the inactive Dock output must not dispatch hardware; active manual Hand output must dispatch immediately");
        check(!manualHand.stopped().activeProfile(false),"An unmanaged card saves output without applying it");

        NativeState live=new NativeState(); live.apply(hand); check(live.writes.isEmpty(),"KEEP + an already-correct ratio must not rewrite native output");
        live.apply(wide);
        check(live.aspect()==OutputMode.Aspect.SIXTEEN_NINE && live.preset()==OutputMode.Preset.P60 && live.properties[1].equals("1920") && live.properties[2].equals("1080"),"User-selected Dock 16:9 must rebase recognized KEEP family like factory settings");
        live.apply(hand);
        check(live.aspect()==OutputMode.Aspect.FOUR_THREE && live.properties[1].equals("1280") && live.properties[2].equals("960") && live.properties[3].equals("60"),"The next Handheld 4:3 mode must restore its ratio and dependent P60 dimensions");
        NativeState unknown=new NativeState(); unknown.properties[1]="1600"; unknown.properties[2]="900"; unknown.properties[3]="75"; String[] previous=unknown.properties.clone(); unknown.apply(wide);
        check(unknown.writes.equals(Arrays.asList("wm")) && Arrays.equals(previous,unknown.properties),"KEEP with unrecognized live timing must change ratio without inventing a native preset");
        OutputSpec explicit=new OutputSpec(OutputMode.Aspect.SIXTEEN_NINE,OutputMode.Preset.QHD144); live.apply(explicit);
        check(live.preset()==OutputMode.Preset.QHD144 && live.properties[3].equals("144"),"An explicit per-profile preset must apply the selected native timing");

        SettingChanges queue=new SettingChanges();
        SettingChanges.Request old=queue.offerOutput(Profile.defaults(false),wide);
        SettingChanges.Request latest=queue.offerOutput(Profile.defaults(false),hand);
        check(queue.target(false,SettingChanges.OUTPUT)==latest && latest.output.equals(hand) && latest.profile.docked==false,"Rapid active output edits must retain one full immutable owner/spec target");
        check(queue.take()==latest && queue.target(false,SettingChanges.OUTPUT)==latest,"Activity recreation must retain the in-flight spec until its ACK");
        queue.complete(old); check(!queue.acknowledged(false,SettingChanges.OUTPUT,latest.revision),"An obsolete output completion must not ACK the newer spec");
        queue.complete(latest); check(queue.target(false,SettingChanges.OUTPUT)==null,"Output ACK must release the profile pending state");
        SettingChanges.Request pendingHand=queue.offerOutput(Profile.defaults(false),wide);
        SettingChanges.Request pendingDock=queue.offerOutput(Profile.defaults(true),explicit);
        queue.cancelMode(false);
        check(queue.acknowledged(false,SettingChanges.OUTPUT,pendingHand.revision) && !queue.current(pendingHand),"A mode change must cancel/ACK old-owner queued output");
        check(queue.current(pendingDock) && queue.take()==pendingDock,"Canceling Handheld must not consume the next Dock owner/spec request");
        queue.cancelAll(); check(queue.acknowledged(true,SettingChanges.OUTPUT,pendingDock.revision),"STOP/RESTORE cancellation must include the sixth output slot");
        System.out.println("OutputSpec tests passed");
    }
}
