package local.nova.dock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class OutputTransactionTest {
    private static void check(boolean value,String message) { if (!value) throw new AssertionError(message); }
    private static final class Port implements OutputTransaction.Port {
        Integer width=1080,height=1920;
        final String[] properties={"1","1920","1080","120"};
        final List<String> writes=new ArrayList<>();
        String failOnce; boolean failVerify;
        public void window(Integer width,Integer height) throws Exception { fail("wm"); this.width=width; this.height=height; writes.add("wm"); }
        public void property(OutputTransaction.Property property,String value) throws Exception { fail(property.name()); properties[property.ordinal()]=value; writes.add(property.name()+"="+value); }
        public void verify() throws Exception { if (failVerify) throw new Exception("verification failed"); }
        void fail(String operation) throws Exception { if (operation.equals(failOnce)) { failOnce=null; throw new Exception("write failed"); } }
        OutputTransaction.Before before() { return new OutputTransaction.Before(width!=null,width==null?960:width,height==null?1280:height,properties); }
    }
    public static void main(String[] args) throws Exception {
        for (String failure:new String[]{"wm","SELECT","WIDTH","HEIGHT","FPS","verify"}) {
            Port port=new Port(); OutputTransaction.Before before=port.before();
            port.failOnce=failure; port.failVerify=failure.equals("verify");
            boolean failed=false;
            try { OutputTransaction.apply(port,before,OutputMode.Preset.UHD60,OutputMode.Aspect.FOUR_THREE,true,true); } catch (Exception expected) { failed=true; }
            check(failed,"A partial or verification failure must never report success");
            check(port.width==1080 && port.height==1920 && Arrays.equals(port.properties,before.properties),"Rollback must restore the fresh live before-state, including a pre-existing override");
        }
        Port noOverride=new Port(); noOverride.width=null; noOverride.height=null; OutputTransaction.Before original=noOverride.before(); noOverride.failOnce="HEIGHT";
        try { OutputTransaction.apply(noOverride,original,OutputMode.Preset.P60,OutputMode.Aspect.SIXTEEN_NINE,true,true); } catch (Exception expected) { }
        check(noOverride.width==null && noOverride.height==null,"Rollback must restore absence of an override, not force a native-size override");
        Port success=new Port(); OutputTransaction.apply(success,success.before(),OutputMode.Preset.QHD144,OutputMode.Aspect.SIXTEEN_NINE,true,false);
        check(success.writes.equals(Arrays.asList("SELECT=1","WIDTH=2560","HEIGHT=1440","FPS=144")),"A forced preset must use stock select/width/height/fps order");
        Port adaptive=new Port(); OutputTransaction.apply(adaptive,adaptive.before(),OutputMode.Preset.ADAPTIVE,OutputMode.Aspect.FOUR_THREE,true,false);
        check(adaptive.writes.equals(Arrays.asList("SELECT=0")),"Adaptive must not overwrite unrelated saved dimensions or force an aspect");
        for (OutputMode.Aspect aspect:OutputMode.Aspect.values()) for (OutputMode.Preset preset:OutputMode.Preset.values()) {
            if (preset==OutputMode.Preset.ADAPTIVE) continue;
            check(OutputMode.Preset.infer("1",preset.width(aspect),preset.height(aspect),preset.fps,aspect)==preset,"Every stock mode must round-trip actual properties");
        }
        check(OutputMode.Preset.P60.width(OutputMode.Aspect.FOUR_THREE)==1280 && OutputMode.Preset.P60.height(OutputMode.Aspect.FOUR_THREE)==960,"4:3 P modes must use 1280x960");
        check(OutputMode.Preset.P240.width(OutputMode.Aspect.SIXTEEN_NINE)==1920 && OutputMode.Preset.P240.height(OutputMode.Aspect.SIXTEEN_NINE)==1080,"16:9 P modes must use 1920x1080");
        System.out.println("OutputTransaction tests passed");
    }
}
