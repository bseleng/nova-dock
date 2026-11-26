package local.nova.dock;

/** One in-process mailbox per mode/setting, shared by the activity and service. */
final class SettingChanges {
    static final int OUTPUT=5, COUNT=6;
    static final class Request {
        final Profile profile;
        final int setting;
        final long revision;
        final Request rebasedFan;
        final OutputSpec output;
        Request(Profile profile,int setting,long revision) {
            this(profile,setting,revision,null);
        }
        Request(Profile profile,int setting,long revision,Request rebasedFan) {
            this(profile,setting,revision,rebasedFan,null);
        }
        Request(Profile profile,int setting,long revision,Request rebasedFan,OutputSpec output) {
            this.profile=profile; this.setting=setting; this.revision=revision;
            this.rebasedFan=rebasedFan; this.output=output;
        }
    }
    private final Request[][] pending=new Request[2][COUNT];
    private final Request[][] targets=new Request[2][COUNT];
    private final long[][] latest=new long[2][COUNT], completed=new long[2][COUNT];
    private long revision;

    synchronized Request offer(Profile profile,int setting) {
        if (setting<Profile.RATE || setting>Profile.CONTROLLER) throw new IllegalArgumentException("Неизвестная настройка");
        return offer(profile,setting,null);
    }
    synchronized Request offerOutput(Profile profile,OutputSpec output) {
        if (output==null) throw new IllegalArgumentException("invalid_value");
        return offer(profile,OUTPUT,output);
    }
    private Request offer(Profile profile,int setting,OutputSpec output) {
        int mode=profile.docked?1:0;
        long nextRevision=++revision;
        Request fan=null;
        // A pending fan was chosen for the preceding performance target. Rebase it on the
        // final compatible profile and run it after that performance, never on the old live mode.
        if (setting==Profile.PERFORMANCE && pending[mode][Profile.FAN]!=null) {
            fan=new Request(profile,Profile.FAN,++revision);
            latest[mode][Profile.FAN]=fan.revision;
            pending[mode][Profile.FAN]=fan;
            targets[mode][Profile.FAN]=fan;
        }
        Request request=new Request(profile,setting,nextRevision,fan,output);
        latest[mode][setting]=request.revision;
        pending[mode][setting]=request;
        targets[mode][setting]=request;
        return request;
    }
    synchronized Request take() {
        Request next=null;
        for (Request[] mode:pending) for (Request request:mode)
            if (request!=null && (next==null || request.revision<next.revision)) next=request;
        if (next!=null) pending[next.profile.docked?1:0][next.setting]=null;
        return next;
    }
    synchronized boolean current(Request request) {
        int mode=request.profile.docked?1:0;
        return latest[mode][request.setting]==request.revision && completed[mode][request.setting]<request.revision;
    }
    synchronized boolean unfinished(Request request) {
        return completed[request.profile.docked?1:0][request.setting]<request.revision;
    }
    synchronized void complete(Request request) {
        int mode=request.profile.docked?1:0;
        completed[mode][request.setting]=Math.max(completed[mode][request.setting],request.revision);
        if (pending[mode][request.setting]==request) pending[mode][request.setting]=null;
    }
    synchronized boolean acknowledged(boolean docked,int setting,long revision) {
        return completed[docked?1:0][setting]>=revision;
    }
    synchronized Request target(boolean docked,int setting) {
        Request request=targets[docked?1:0][setting];
        return request!=null && !acknowledged(docked,setting,request.revision)?request:null;
    }
    synchronized void cancelAll() {
        cancelMode(false); cancelMode(true);
    }
    synchronized void cancelMode(boolean docked) {
        int mode=docked?1:0;
        for (int setting=0;setting<COUNT;setting++) { pending[mode][setting]=null; completed[mode][setting]=latest[mode][setting]; }
    }
}
