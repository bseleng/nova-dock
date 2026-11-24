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
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;

public final class DockService extends Service {
    static final String STOP = "local.nova.dock.STOP";
    static final String RESTORE = "local.nova.dock.RESTORE";
    static final String REAPPLY = "local.nova.dock.REAPPLY";
    static final String RECONFIGURE = "local.nova.dock.RECONFIGURE";
    static final String MANUAL = "local.nova.dock.MANUAL";
    static final String CHANGE = "local.nova.dock.CHANGE";
    private static final SettingChanges changes = new SettingChanges();
    private static final ControlState.Revisions controls=new ControlState.Revisions();
    private HandlerThread thread;
    private Handler worker;
    private Retroid retroid;
    private volatile boolean running;
    private volatile boolean observing;
    private volatile Boolean applied;
    private Boolean attempted;
    private int failures;
    private final StableConnection stable = new StableConnection();
    private final Runnable inspect = () -> inspect();
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
    static SettingChanges.Request change(Context c,Profile profile,int setting) {
        SettingChanges.Request request=changes.offer(profile,setting);
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
    @Override public void onCreate() {
        super.onCreate();
        retroid = new Retroid(this);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("dock", "Режим дока", NotificationManager.IMPORTANCE_LOW));
        startForeground(1, notification(Store.enabled(this)?"Проверка подключения…":"Ручной режим"));
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
        String action = intent == null ? null : intent.getAction();
        boolean stopping=STOP.equals(action) || RESTORE.equals(action);
        Long dispatched=intent!=null && intent.hasExtra("revision")?intent.getLongExtra("revision",0):null;
        long stopRevision=stopping?controls.receiveStop(dispatched,Store.managed(this)):0;
        if (stopRevision!=0) {
            Store.stop(this);
            running = false;
            stopObserving();
            changes.cancelAll();
            worker.removeCallbacksAndMessages(null);
            worker.post(() -> {
                if (!controls.current(stopRevision)) return;
                try {
                    if (RESTORE.equals(action)) retroid.restoreOriginal();
                    controls.ifCurrent(stopRevision,()->{
                        if (!Store.managed(this)) {
                            Store.status(this, RESTORE.equals(action) ? "Исходные настройки восстановлены" : "Управление остановлено");
                            Store.log(this, Store.prefs(this).getString("status", "Остановлено"));
                        }
                    });
                } catch (Exception e) { if (controls.current(stopRevision)) error(e); }
                applied=null;
                controls.ifCurrent(stopRevision,()->{
                    if (!Store.managed(this)) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(startId); }
                });
            });
            return START_NOT_STICKY;
        }
        if (!Store.managed(this)) { changes.cancelAll(); stopSelf(startId); return START_NOT_STICKY; }
        if (!retroid.supported() || !retroid.permitted()) {
            Store.stop(this);
            changes.cancelAll();
            Store.status(this, "Ошибка: сервис Retroid недоступен или прошивка не поддерживается");
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        boolean cold=!running;
        if (cold) {
            running = true;
            Store.log(this, Store.enabled(this)?"Автоматика запущена":"Ручное управление запущено");
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
                if (applied==null) applied=Store.lastDocked(this);
                controls.ifCurrent(revision,()->{ Store.syncManual(this,applied); normalStatus(applied); });
            }
        }
    }
    private void retry() {
        if (!running || !Store.managed(this)) return;
        if (Store.enabled(this)) {
            applied=null; attempted=null; failures=0; stable.update(null,SystemClock.elapsedRealtime());
            if (!observing) configure(false,controls.latest()); else signal();
        } else applyManual(Store.manualDocked(this),true);
    }
    private void applyManual(boolean mode,boolean force) {
        if (!running || !Store.state(this).needsManualApply(applied,force)) return;
        try {
            if (!retroid.apply(mode,false)) return;
            if (!running || !Store.managed(this)) return;
            applied=mode; attempted=null; failures=0; Store.applied(this,mode);
            normalStatus(mode);
            Store.log(this,Profiles.get(this,mode).status()+" · Ручной выбор");
        } catch (Exception e) { applied=null; if (running && Store.managed(this)) error(e); }
    }
    private void normalStatus(boolean mode) {
        String text=Profiles.get(this,mode).status(); Store.status(this,text); notifyStatus(text);
    }
    private void inspect() {
        if (!automaticActive()) return;
        boolean transitioning=false;
        try {
            Detector.Sample sample = Detector.sample(this);
            if (sample.connected == null) {
                stable.update(null, SystemClock.elapsedRealtime());
                Store.status(this, "Ожидание достоверного состояния видеовыхода");
                notifyStatus(Store.prefs(this).getString("status", ""));
                return;
            }
            Boolean mode = stable.update(sample.connected, SystemClock.elapsedRealtime());
            if (mode == null) return;
            if (mode.equals(applied)) {
                if (Store.prefs(this).getString("status","").startsWith("Ожидание")) normalStatus(mode);
                return;
            }
            if (!mode.equals(attempted)) { failures = 0; attempted = mode; }
            if (failures >= 3) return;
            transitioning=true;
            boolean ok = retroid.apply(mode, ()->Store.enabled(this));
            if (!ok) { applied=null; Store.log(this, "Подключение изменилось во время перехода"); return; }
            if (!running || !Store.managed(this)) return;
            // A cable may be removed while firmware settings are being applied.
            Detector.Sample after = Detector.sample(this);
            if (Store.enabled(this) && !mode.equals(after.connected)) { applied=null; return; }
            applied = mode;
            failures = 0;
            String text = Profiles.get(this,mode).status();
            Store.status(this, text);
            Store.log(this, text + " · " + sample.source);
            Store.applied(this,mode);
            notifyStatus(text);
            // Do not continuously reconcile settings: quick-panel edits last until the next mode.
        } catch (Exception e) { if (transitioning) applied=null; failures++; if (running && Store.managed(this)) error(e); }
    }
    private void changeSetting(SettingChanges.Request request) {
        try {
            Profile profile=request.profile;
            boolean mode=profile.docked;
            int setting=request.setting;
            if (!running || !Store.managed(this) || !changes.current(request) || !Boolean.valueOf(mode).equals(applied)) return;
            // Finish an in-flight firmware transition so its compatible fan/custom speed survives
            // into the next coalesced request. Stopping still cancels dependent writes.
            if (!retroid.applySetting(profile,setting,()->running && Store.managed(this) && changes.unfinished(request),()->Store.enabled(this))) return;
            if (!changes.current(request)) return;
            String text=profile.status(); Store.status(this,text); notifyStatus(text);
            Store.log(this,"Настройка применена · "+(mode?"В доке":"В руках")+" · "+Profile.settingLabel(setting)+": "+profile.value(setting));
        } catch (Exception e) { error(e); }
        finally { changes.complete(request); }
    }
    private void error(Exception e) {
        String message = "Ошибка: " + e.getMessage();
        Store.status(this, message); Store.log(this, message); notifyStatus(message);
    }
    private Notification notification(String text) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, DockService.class).setAction(STOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, "dock").setSmallIcon(R.drawable.ic_dock).setContentTitle("Nova Dock")
            .setContentText(text).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(new Notification.Action.Builder(null, "Остановить", stop).build()).build();
    }
    private void notifyStatus(String text) { getSystemService(NotificationManager.class).notify(1, notification(text)); }
    @Override public void onDestroy() {
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
