package local.nova.dock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.os.Looper;
import android.view.Display;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;

public final class DockService extends Service {
    static final String STOP = "local.nova.dock.STOP";
    static final String RESTORE = "local.nova.dock.RESTORE";
    static final String REAPPLY = "local.nova.dock.REAPPLY";
    static final String RECONFIGURE = "local.nova.dock.RECONFIGURE";
    static final String MANUAL = "local.nova.dock.MANUAL";
    static final String CHANGE = "local.nova.dock.CHANGE";
    static final String GLOBAL = "local.nova.dock.GLOBAL";
    private static final SettingChanges changes = new SettingChanges();
    private static final GlobalChanges globals=new GlobalChanges();
    private static final Set<Runnable> observers=new HashSet<>();
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static volatile ViewSnapshot snapshot;
    private static final ControlState.Revisions controls=new ControlState.Revisions();
    static final Object hardware=new Object();
    private HandlerThread thread;
    private Handler worker;
    private Retroid retroid;
    private GlobalSettings globalSettings;
    private volatile long stoppingRevision;
    private volatile boolean globalWorking;
    private volatile boolean destroyed;
    private volatile int lastStartId;
    private volatile boolean running;
    private volatile boolean observing;
    private volatile Boolean applied;
    private OutputSpec appliedOutput;
    private Runnable observedOutput;
    private long observationRevision;
    private Boolean attempted;
    private int failures;
    private final StableConnection stable = new StableConnection();
    private final Runnable inspect = () -> inspect();
    private final Runnable globalPending=()->drainGlobals();
    private final Runnable changePending = () -> {
        SettingChanges.Request request;
        while (running && (request=changes.take())!=null) changeSetting(request);
    };
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!automaticActive()) return;
            inspect();
            if (automaticActive()) worker.postDelayed(this, 1200L);
        }
    };
    private final DisplayManager.DisplayListener listener = new DisplayManager.DisplayListener() {
        public void onDisplayAdded(int id) { signal(); }
        public void onDisplayRemoved(int id) { signal(); }
        public void onDisplayChanged(int id) { signal(); }
    };
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            signal();
        }
    };
    static void start(Context c, String action) {
        Intent i = new Intent(c, DockService.class);
        if (action != null) i.setAction(action);
        if (STOP.equals(action) || RESTORE.equals(action)) {
            i.putExtra("revision",controls.next());
            Store.stop(c);
            long cutoff=globals.revision(); globals.cancelThrough(cutoff); i.putExtra("global_cutoff",cutoff);
        }
        c.startForegroundService(i);
    }
    static void automatic(Context c,boolean enabled) {
        long revision=controls.next();
        Store.automatic(c,enabled);
        c.startForegroundService(new Intent(c,DockService.class).setAction(RECONFIGURE).putExtra("revision",revision));
    }
    static void manual(Context c,boolean docked) {
        long revision=controls.next();
        Store.manual(c,docked);
        c.startForegroundService(new Intent(c,DockService.class).setAction(MANUAL).putExtra("docked",docked).putExtra("revision",revision));
    }
    static void restore(Context c) {
        start(c,RESTORE);
    }
    static GlobalChanges.Request global(Context c,GlobalChanges.Key key,boolean value,OutputMode.Choice output) {
        GlobalChanges.Request request=globals.offer(key,value,output);
        try { c.startForegroundService(new Intent(c,DockService.class).setAction(GLOBAL)); }
        catch (RuntimeException e) { globals.complete(request,"global_write_failed",""); throw e; }
        return request;
    }
    static void refresh(Context c) { global(c,GlobalChanges.Key.READ,false,null); }
    static void refreshRuntime(Context c) { global(c,GlobalChanges.Key.RUNTIME,false,null); }
    static ViewSnapshot snapshot() { return snapshot; }
    static GlobalChanges.Request globalTarget(GlobalChanges.Key key) { return globals.target(key); }
    static GlobalChanges.Result globalResult(GlobalChanges.Key key) { return globals.result(key); }
    static GlobalChanges.Result takeGlobalError(GlobalChanges.Key key) { return globals.takeError(key); }
    static boolean globalAcknowledged(GlobalChanges.Key key,long revision) { return globals.acknowledged(key,revision); }
    static synchronized void observe(Runnable observer) { observers.add(observer); }
    static synchronized void unobserve(Runnable observer) { observers.remove(observer); }
    private static void changed() {
        main.post(()->{ ArrayList<Runnable> listeners; synchronized (DockService.class) { listeners=new ArrayList<>(observers); } for (Runnable observer:listeners) observer.run(); });
    }
    static SettingChanges.Request change(Context c,Profile profile,int setting) {
        return dispatchChange(c,changes.offer(profile,setting));
    }
    static SettingChanges.Request changeOutput(Context c,boolean docked,OutputSpec spec) {
        return dispatchChange(c,changes.offerOutput(Profiles.get(c,docked),spec));
    }
    private static SettingChanges.Request dispatchChange(Context c,SettingChanges.Request request) {
        try {
            c.startForegroundService(new Intent(c,DockService.class).setAction(CHANGE));
        } catch (RuntimeException e) {
            changes.complete(request);
            if (request.rebasedFan!=null) changes.complete(request.rebasedFan);
            throw e;
        }
        return request;
    }
    static boolean acknowledged(boolean docked,int setting,long revision) {
        return changes.acknowledged(docked,setting,revision);
    }
    static SettingChanges.Request profileTarget(boolean docked,int setting) { return changes.target(docked,setting); }
    @Override public void onCreate() {
        super.onCreate();
        retroid = new Retroid(this);
        globalSettings=new GlobalSettings(this,retroid.bridge);
        updateChannel();
        startForeground(1, notification(getString(Store.enabled(this)?R.string.status_checking:R.string.status_manual)));
        thread = new HandlerThread("NovaDock"); thread.start();
        worker = new Handler(thread.getLooper());
        getSystemService(DisplayManager.class).registerDisplayListener(listener, worker);
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.ro.action.HDMI_OR_DP_STATUS_CHANGED");
        filter.addAction("android.intent.action.HDMI_PLUGGED");
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        // Treat vendor broadcasts only as wake-ups; always re-read physical connection state.
        registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        lastStartId=startId;
        String action = intent == null ? null : intent.getAction();
        // Globals remain available even when no profile session is managed.
        if (GLOBAL.equals(action)) {
            worker.removeCallbacks(globalPending); worker.post(globalPending);
            return Store.managed(this)?START_STICKY:START_NOT_STICKY;
        }
        boolean stopping=STOP.equals(action) || RESTORE.equals(action);
        Long dispatched=intent!=null && intent.hasExtra("revision")?intent.getLongExtra("revision",0):null;
        long stopRevision=stopping?controls.receiveStop(dispatched,Store.managed(this)):0;
        if (stopRevision!=0) {
            stoppingRevision=stopRevision;
            Store.stop(this);
            running = false;
            stopObserving();
            changes.cancelAll();
            globals.cancelThrough(intent.getLongExtra("global_cutoff",globals.revision()));
            worker.removeCallbacksAndMessages(null);
            worker.post(() -> {
                try {
                    if (!controls.current(stopRevision)) return;
                    if (RESTORE.equals(action)) restoreOriginals();
                    controls.ifCurrent(stopRevision,()->{
                        if (!Store.managed(this)) {
                            String code=RESTORE.equals(action)?"restored":"stopped";
                            Store.status(this,code,""); Store.event(this,code);
                        }
                    });
                } catch (Exception e) { if (controls.current(stopRevision)) error(e); }
                finally {
                    applied=null;
                    if (stoppingRevision==stopRevision) stoppingRevision=0;
                    publish(true); changed(); cleanup();
                }
            });
            return START_NOT_STICKY;
        }
        if (!Store.managed(this)) { changes.cancelAll(); stopSelf(startId); return START_NOT_STICKY; }
        if (!retroid.supported() || !retroid.permitted()) {
            Store.stop(this);
            changes.cancelAll();
            Store.failure(this,new Failure("vendor_unavailable"));
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        boolean cold=!running;
        if (cold) {
            running = true;
            Store.event(this,"session_started",Store.enabled(this)?"1":"0");
        }
        if (!Store.enabled(this)) stopObserving();
        long revision=stopping || intent==null?controls.latest():intent.getLongExtra("revision",controls.latest());
        if (cold && controls.latest()==0) {
            // After process death, persisted state is authoritative; old in-process revisions died too.
            worker.post(()->configure(true,controls.latest()));
        } else if (MANUAL.equals(action) && intent.hasExtra("docked")) {
            boolean mode=intent.getBooleanExtra("docked",false);
            if (controls.current(revision) && !Boolean.valueOf(mode).equals(applied)) changes.cancelAll();
            worker.post(()->{
                if (!controls.current(revision) || !running || !Store.managed(this) || Store.enabled(this)) return;
                applyManual(mode,false);
            });
        } else if (REAPPLY.equals(action)) {
            changes.cancelAll();
            worker.post(()->retry());
        } else if (cold || RECONFIGURE.equals(action) || Store.enabled(this)!=observing) {
            worker.post(()->configure(cold,revision));
        }
        if (CHANGE.equals(action)) {
            worker.removeCallbacks(changePending);
            worker.post(changePending);
        }
        return START_STICKY;
    }
    private void signal() {
        if (worker == null || !automaticActive()) return;
        worker.removeCallbacks(inspect);
        worker.post(inspect);
    }
    private boolean automaticActive() { return running && observing && Store.managed(this) && Store.enabled(this); }
    private void stopObserving() {
        observing=false;
        worker.removeCallbacks(poll);
        worker.removeCallbacks(inspect);
    }
    private void configure(boolean cold,long revision) {
        if (!running || !Store.managed(this) || !controls.current(revision)) return;
        try { synchronized (hardware) { globalSettings.adoptScreenOnce(); } }
        catch (Exception e) { error(e); }
        if (Store.enabled(this)) {
            if (!observing) {
                stable.update(null,SystemClock.elapsedRealtime()); attempted=null; failures=0;
                observing=true;
                worker.removeCallbacks(poll); worker.post(poll);
            }
        } else {
            stopObserving(); stable.update(null,SystemClock.elapsedRealtime());
            if (cold) applyManual(Store.manualDocked(this),false);
            else {
                // A transition already in flight is allowed to settle before freezing its mode.
                Boolean settled=applied;
                controls.ifCurrent(revision,()->{ Store.syncManual(this,settled); if (settled!=null) normalStatus(settled); });
            }
        }
        publish(true); changed();
    }
    private void retry() {
        if (!running || !Store.managed(this)) return;
        if (Store.enabled(this)) {
            applied=null; attempted=null; failures=0; stable.update(null,SystemClock.elapsedRealtime());
            if (!observing) configure(false,controls.latest()); else signal();
        } else applyManual(Store.manualDocked(this),true);
    }
    private void applyManual(boolean mode,boolean force) {
        synchronized (hardware) { applyManualLocked(mode,force); }
    }
    private void applyManualLocked(boolean mode,boolean force) {
        if (!running || !Store.state(this).needsManualApply(applied,force)) return;
        try {
            if (!applyCompleteMode(mode,()->false)) { applied=null; return; }
            if (!running || !Store.managed(this)) return;
            applied=mode; attempted=null; failures=0; Store.applied(this,mode);
            normalStatus(mode);
            modeEvent(mode,"manual"); scheduleObservedOutput(mode,appliedOutput); publish(true); changed();
        } catch (Exception e) { applied=null; publish(true); if (running && Store.managed(this)) error(e); }
    }
    private void normalStatus(boolean mode) {
        Store.status(this,"applied",mode?"1":"0"); notifyStatus(AppStatus.text(this));
    }
    private void modeEvent(boolean mode,String source) {
        Profile p=Profiles.get(this,mode);
        OutputSpec output=appliedOutput==null?OutputProfiles.get(this,mode):appliedOutput;
        Store.event(this,"mode_applied",mode?"1":"0",Integer.toString(p.rate),Integer.toString(p.fan),Integer.toString(p.performance),Integer.toString(p.layout),p.bypassCharging?"1":"0",source,output.aspect.name(),output.presetCode());
    }
    private boolean applyCompleteMode(boolean mode,BooleanSupplier requireConnection) throws Exception {
        if (!retroid.apply(mode,requireConnection)) return false;
        if (!running || !Store.managed(this)) return false;
        if (requireConnection.getAsBoolean() && !Boolean.valueOf(mode).equals(Detector.sample(this).connected)) return false;
        OutputSpec spec=OutputProfiles.get(this,mode);
        globalSettings.profile(spec);
        if (requireConnection.getAsBoolean() && !Boolean.valueOf(mode).equals(Detector.sample(this).connected)) return false;
        appliedOutput=spec; return true;
    }
    private void inspect() {
        synchronized (hardware) { inspectLocked(); }
    }
    private void inspectLocked() {
        if (!automaticActive()) return;
        boolean transitioning=false;
        try {
            Detector.Sample sample = Detector.sample(this);
            if (sample.connected == null) {
                stable.update(null, SystemClock.elapsedRealtime());
                Store.status(this,"waiting",""); notifyStatus(AppStatus.text(this)); changed();
                return;
            }
            Boolean mode = stable.update(sample.connected, SystemClock.elapsedRealtime());
            if (mode == null) return;
            if (mode.equals(applied)) {
                if ("waiting".equals(Store.statusCode(this))) { normalStatus(mode); changed(); }
                return;
            }
            if (!mode.equals(attempted)) { failures = 0; attempted = mode; }
            if (failures >= 3) return;
            changes.cancelMode(!mode);
            transitioning=true;
            boolean ok = applyCompleteMode(mode, ()->Store.enabled(this));
            if (!ok) { applied=null; Store.event(this,"connection_changed"); publish(true); changed(); return; }
            if (!running || !Store.managed(this)) return;
            // A cable may be removed while firmware settings are being applied.
            Detector.Sample after = Detector.sample(this);
            if (Store.enabled(this) && !mode.equals(after.connected)) { applied=null; return; }
            applied = mode;
            failures = 0;
            Store.applied(this,mode);
            normalStatus(mode); modeEvent(mode,"automatic"); scheduleObservedOutput(mode,appliedOutput); publish(true); changed();
            // Do not continuously reconcile settings: quick-panel edits last until the next mode.
        } catch (Exception e) { if (transitioning) { applied=null; publish(true); } failures++; if (running && Store.managed(this)) error(e); }
    }
    private void changeSetting(SettingChanges.Request request) {
        synchronized (hardware) { changeSettingLocked(request); }
    }
    private void changeSettingLocked(SettingChanges.Request request) {
        try {
            Profile profile=request.profile;
            boolean mode=profile.docked;
            int setting=request.setting;
            if (!running || !Store.managed(this) || !changes.current(request) || !Boolean.valueOf(mode).equals(applied)) return;
            if (setting==SettingChanges.OUTPUT) {
                if (Store.enabled(this) && !Boolean.valueOf(mode).equals(Detector.sample(this).connected)) return;
                globalSettings.profile(request.output);
                if (Store.enabled(this) && !Boolean.valueOf(mode).equals(Detector.sample(this).connected)) { applied=null; return; }
                if (!changes.current(request)) return;
                normalStatus(mode);
                Store.event(this,"output_applied",mode?"1":"0",request.output.aspect.name(),request.output.presetCode());
                scheduleObservedOutput(mode,request.output);
                return;
            }
            // Finish an in-flight firmware transition so its compatible fan/custom speed survives
            // into the next coalesced request. Stopping still cancels dependent writes.
            if (!retroid.applySetting(profile,setting,()->running && Store.managed(this) && changes.unfinished(request),()->Store.enabled(this))) return;
            if (!changes.current(request)) return;
            normalStatus(mode);
            Store.event(this,"setting_applied",mode?"1":"0",Integer.toString(setting),Integer.toString(profile.intValue(setting)));
        } catch (Exception e) { if (request.setting==SettingChanges.OUTPUT && e.getSuppressed().length>0) applied=null; error(e); }
        finally { publish(request.setting==SettingChanges.OUTPUT); changes.complete(request); changed(); }
    }
    private void error(Exception e) {
        Store.failure(this,e); notifyStatus(AppStatus.text(this)); changed();
    }
    private void scheduleObservedOutput(boolean mode,OutputSpec spec) {
        if (observedOutput!=null) worker.removeCallbacks(observedOutput);
        long observation=++observationRevision,control=controls.latest();
        observedOutput=()->{
            if (destroyed || !running || !Store.managed(this) || observation!=observationRevision || !controls.current(control)
                || !Boolean.valueOf(mode).equals(applied) || changes.target(mode,SettingChanges.OUTPUT)!=null || !spec.equals(OutputProfiles.get(this,mode))) return;
            if (Store.enabled(this) && !Boolean.valueOf(mode).equals(Detector.sample(this).connected)) return;
            for (Display display:getSystemService(DisplayManager.class).getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)) {
                if (display.getDisplayId()==Display.DEFAULT_DISPLAY || !display.isValid()) continue;
                try {
                    Display.Mode actual=display.getMode();
                    if (actual.getPhysicalWidth()<=0 || actual.getPhysicalHeight()<=0 || actual.getRefreshRate()<=0) continue;
                    if (!display.isValid() || !controls.current(control) || !Boolean.valueOf(mode).equals(applied)) return;
                    Store.event(this,"output_observed",mode?"1":"0",Integer.toString(display.getDisplayId()),display.getName(),Integer.toString(actual.getPhysicalWidth()),Integer.toString(actual.getPhysicalHeight()),Float.toString(actual.getRefreshRate()));
                } catch (RuntimeException ignored) { /* The display may disappear during this one-shot read. */ }
            }
            changed();
        };
        worker.postDelayed(observedOutput,1000L);
    }
    private void drainGlobals() {
        globalWorking=true;
        try {
            while (true) {
                synchronized (hardware) {
                    if (destroyed || stoppingRevision!=0) break;
                    GlobalChanges.Request request=globals.take(); if (request==null) break;
                    processGlobal(request);
                }
            }
        } finally { globalWorking=false; cleanup(); }
    }
    private void processGlobal(GlobalChanges.Request request) {
        String failure=null,detail="";
        try {
            if (!globals.current(request)) return;
            if (request.key!=GlobalChanges.Key.READ && request.key!=GlobalChanges.Key.RUNTIME) {
                if (!retroid.supported()) throw new Failure("unsupported_device");
                if (!retroid.permitted()) throw new Failure("vendor_unavailable");
                switch (request.key) {
                    case SCREEN_OFF:globalSettings.screenOff(request.value);break;
                    case SHOW_REFRESH:globalSettings.showRefresh(request.value);break;
                    case OUTPUT:globalSettings.output(request.output);break;
                    default:break;
                }
                if (globals.current(request)) {
                    if (request.key==GlobalChanges.Key.OUTPUT) Store.event(this,"global_applied",request.key.name(),request.output.preset==null?"":request.output.preset.name(),request.output.aspect.name());
                    else Store.event(this,"global_applied",request.key.name(),request.value?"1":"0");
                }
            }
        } catch (Exception e) {
            failure=e instanceof Failure?((Failure)e).code:"global_write_failed"; detail=e instanceof Failure?((Failure)e).detail:"";
            if (globals.current(request)) Store.event(this,"error",failure,detail);
        } finally {
            String readFailure=publish(request.key!=GlobalChanges.Key.RUNTIME);
            if (failure==null && readFailure!=null) failure=readFailure;
            globals.complete(request,failure,detail); changed();
        }
    }
    private String publish(boolean readGlobals) {
        synchronized (hardware) { return publishLocked(readGlobals); }
    }
    private String publishLocked(boolean readGlobals) {
        ViewSnapshot before=snapshot;
        java.util.Map<String,String> runtime=before==null?new java.util.LinkedHashMap<>():before.runtime;
        try { runtime=retroid.snapshot(); } catch (Exception ignored) { }
        boolean supported=retroid.supported(),permitted=retroid.permitted();
        GlobalSettings.Snapshot globalSnapshot=before==null?null:before.globals;
        String globalError=before==null?null:before.globalError;
        if (readGlobals) {
            try {
                if (!supported) throw new Failure("unsupported_device");
                if (!permitted) throw new Failure("vendor_unavailable");
                globalSnapshot=globalSettings.read(); globalError=null;
            } catch (Exception e) { globalError=e instanceof Failure?((Failure)e).code:"global_read_failed"; }
        }
        snapshot=new ViewSnapshot(runtime,globalSnapshot,supported,permitted,globalError);
        return readGlobals?globalError:null;
    }
    private void cleanup() {
        if (destroyed || Store.managed(this) || running || globalWorking || stoppingRevision!=0) return;
        long globalRevision=globals.revision(),controlRevision=controls.latest();
        int startId=lastStartId;
        globals.ifIdle(globalRevision,()->controls.ifCurrent(controlRevision,()->{
            if (!destroyed && !Store.managed(this) && !running && !globalWorking && stoppingRevision==0) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(startId); }
        }));
    }
    private void restoreOriginals() throws Exception {
        synchronized (hardware) {
            Exception failure=null;
            try { globalSettings.restoreOriginal(); } catch (Exception e) { failure=e; }
            try { retroid.restoreOriginal(); } catch (Exception e) { if (failure==null) failure=e; }
            if (failure!=null) throw failure;
        }
    }
    private void updateChannel() {
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("dock",getString(R.string.notification_channel_name),NotificationManager.IMPORTANCE_LOW));
    }
    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration); updateChannel();
        if (running && Store.managed(this)) worker.post(()->notifyStatus(AppStatus.text(this)));
    }
    private Notification notification(String text) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, DockService.class).setAction(STOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, "dock").setSmallIcon(R.drawable.ic_output).setContentTitle(getString(R.string.app_name))
            .setContentText(text).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(new Notification.Action.Builder(null,getString(R.string.action_stop),stop).build()).build();
    }
    private void notifyStatus(String text) { getSystemService(NotificationManager.class).notify(1, notification(text)); }
    @Override public void onDestroy() {
        destroyed=true;
        running = false;
        observing=false;
        changes.cancelAll();
        getSystemService(DisplayManager.class).unregisterDisplayListener(listener);
        unregisterReceiver(receiver);
        if (worker != null) worker.removeCallbacksAndMessages(null);
        if (thread != null) thread.quitSafely();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
