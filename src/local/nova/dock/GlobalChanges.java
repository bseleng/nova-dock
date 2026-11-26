package local.nova.dock;

/** Global settings have independent revisions; switching profiles never cancels this mailbox. */
final class GlobalChanges {
    enum Key { READ, RUNTIME, SCREEN_OFF, SHOW_REFRESH, OUTPUT }
    static final class Request {
        final Key key;
        final long revision;
        final boolean value;
        final OutputMode.Choice output;
        Request(Key key,long revision,boolean value,OutputMode.Choice output) {
            this.key=key; this.revision=revision; this.value=value; this.output=output;
        }
    }
    static final class Result {
        final long revision;
        final String error,detail;
        private boolean delivered;
        Result(long revision,String error,String detail) { this.revision=revision; this.error=error; this.detail=detail; }
    }
    private final Request[] pending=new Request[Key.values().length];
    private final Request[] targets=new Request[pending.length];
    private final Result[] results=new Result[pending.length];
    private final long[] latest=new long[pending.length], completed=new long[pending.length];
    private long revision;
    synchronized Request offer(Key key,boolean value,OutputMode.Choice output) {
        if (key==Key.OUTPUT && output==null) throw new IllegalArgumentException("invalid_value");
        if (key==Key.OUTPUT && pending[key.ordinal()]!=null) output=OutputMode.Choice.merge(pending[key.ordinal()].output,output);
        Request request=new Request(key,++revision,value,output);
        latest[key.ordinal()]=request.revision; pending[key.ordinal()]=request; targets[key.ordinal()]=request; return request;
    }
    synchronized Request take() {
        Request next=null;
        for (Request request:pending) if (request!=null && (next==null || request.revision<next.revision)) next=request;
        if (next!=null) pending[next.key.ordinal()]=null;
        return next;
    }
    synchronized boolean current(Request request) { return latest[request.key.ordinal()]==request.revision && completed[request.key.ordinal()]<request.revision; }
    synchronized boolean acknowledged(Key key,long revision) { return completed[key.ordinal()]>=revision; }
    synchronized void complete(Request request) { complete(request,null,""); }
    synchronized void complete(Request request,String error,String detail) {
        int index=request.key.ordinal();
        if (request.revision>completed[index]) results[index]=new Result(request.revision,error,detail);
        completed[index]=Math.max(completed[index],request.revision);
        if (pending[index]==request) pending[index]=null;
    }
    synchronized boolean empty() { for (Request request:pending) if (request!=null) return false; return true; }
    synchronized Request target(Key key) { return targets[key.ordinal()]!=null && !acknowledged(key,targets[key.ordinal()].revision)?targets[key.ordinal()]:null; }
    synchronized Result result(Key key) { return results[key.ordinal()]; }
    synchronized Result takeError(Key key) {
        Result result=results[key.ordinal()];
        if (result==null || result.error==null || result.delivered) return null;
        result.delivered=true; return result;
    }
    synchronized long revision() { return revision; }
    synchronized boolean ifIdle(long expected,Runnable action) {
        if (revision!=expected || !empty()) return false;
        action.run(); return true;
    }
    synchronized void cancelAll() {
        cancelThrough(revision);
    }
    synchronized void cancelThrough(long cutoff) {
        for (int i=0;i<pending.length;i++) {
            if (pending[i]!=null && pending[i].revision<=cutoff) pending[i]=null;
            completed[i]=Math.max(completed[i],Math.min(latest[i],cutoff));
            if (results[i]==null || results[i].revision<=cutoff) results[i]=new Result(completed[i],null,"");
        }
    }
}
