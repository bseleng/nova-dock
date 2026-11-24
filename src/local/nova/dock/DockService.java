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
    private HandlerThread thread;
    private Handler worker;
    private Retroid retroid;
    private volatile boolean running;
    private Boolean applied;
    private Boolean attempted;
    private int failures;
    private int lastPowerState=Integer.MIN_VALUE;
    private final Runnable fanCheck=() -> checkFan();
    private final StableConnection stable = new StableConnection();
    private final Runnable inspect = () -> inspect();
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!running) return;
            inspect();
            if (running) worker.postDelayed(this, 1200L);
        }
    };
    private final DisplayManager.DisplayListener listener = new DisplayManager.DisplayListener() {
        public void onDisplayAdded(int id) { signal(); }
        public void onDisplayRemoved(int id) { signal(); }
        public void onDisplayChanged(int id) { signal(); }
    };
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_BATTERY_CHANGED.equals(i.getAction())) {
                int state=(i.getIntExtra("status",0)<<8)|i.getIntExtra("plugged",0);
                if (state==lastPowerState) return;
                lastPowerState=state;
            }
            signal();
            if (worker!=null && running) worker.post(() -> {
                worker.removeCallbacks(fanCheck);
                worker.postDelayed(fanCheck,1600L);
            });
        }
    };
    static void start(Context c, String action) {
        Intent i = new Intent(c, DockService.class);
        if (action != null) i.setAction(action);
        c.startForegroundService(i);
    }
    @Override public void onCreate() {
        super.onCreate();
        retroid = new Retroid(this);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("dock", "Режим дока", NotificationManager.IMPORTANCE_LOW));
        startForeground(1, notification("Проверка подключения…"));
        thread = new HandlerThread("NovaDock"); thread.start();
        worker = new Handler(thread.getLooper());
        getSystemService(DisplayManager.class).registerDisplayListener(listener, worker);
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.ro.action.HDMI_OR_DP_STATUS_CHANGED");
        filter.addAction("android.intent.action.HDMI_PLUGGED");
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        // Treat vendor broadcasts only as wake-ups; always re-read physical connection state.
        registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (STOP.equals(action) || RESTORE.equals(action)) {
            Store.enabled(this, false);
            running = false;
            worker.removeCallbacksAndMessages(null);
            worker.post(() -> {
                try {
                    if (RESTORE.equals(action)) retroid.restoreOriginal(); else retroid.apply(false, false);
                    Store.status(this, RESTORE.equals(action) ? "Исходные настройки восстановлены" : "Автоматика выключена · "+Profiles.get(this,false).status());
                    Store.log(this, Store.prefs(this).getString("status", "Остановлено"));
                } catch (Exception e) { error(e); }
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf(startId);
            });
            return START_NOT_STICKY;
        }
        if (!Store.enabled(this)) { stopSelf(startId); return START_NOT_STICKY; }
        if (!retroid.supported() || !retroid.permitted()) {
            Store.enabled(this, false);
            Store.status(this, "Сервис Retroid недоступен или прошивка не поддерживается");
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (REAPPLY.equals(action)) { worker.post(() -> { applied = null; failures = 0; }); }
        if (!running) {
            running = true;
            worker.post(poll);
            Store.log(this, "Автоматика запущена");
        }
        return START_STICKY;
    }
    private void signal() {
        if (worker == null || !running) return;
        worker.removeCallbacks(inspect);
        worker.post(inspect);
    }
    private void inspect() {
        if (!running || !Store.enabled(this)) return;
        try {
            Detector.Sample sample = Detector.sample(this);
            if (sample.connected == null) {
                stable.update(null, SystemClock.elapsedRealtime());
                Store.status(this, "Ожидание достоверного состояния видеовыхода");
                notifyStatus(Store.prefs(this).getString("status", ""));
                return;
            }
            Boolean mode = stable.update(sample.connected, SystemClock.elapsedRealtime());
            if (mode == null || mode.equals(applied)) return;
            if (!mode.equals(attempted)) { failures = 0; attempted = mode; }
            if (failures >= 3) return;
            boolean ok = retroid.apply(mode, true);
            if (!ok) { Store.log(this, "Подключение изменилось во время перехода"); return; }
            // A cable may be removed while firmware settings are being applied.
            Detector.Sample after = Detector.sample(this);
            if (!mode.equals(after.connected)) return;
            applied = mode;
            failures = 0;
            String text = Profiles.get(this,mode).status();
            Store.status(this, text);
            Store.log(this, text + " · " + sample.source);
            Store.prefs(this).edit().putBoolean("last_docked", mode).apply();
            notifyStatus(text);
            worker.postDelayed(() -> verify(mode, 0), 1200L);
        } catch (Exception e) { failures++; error(e); }
    }
    private void checkFan() {
        if (!running || !Store.enabled(this) || applied==null) return;
        if (!applied.equals(Detector.sample(this).connected)) return;
        try {
            Profile profile=Profiles.get(this,applied);
            if (retroid.restoreFan(profile)) {
                Store.log(this,"Вентилятор восстановлен: "+Profile.fanLabel(profile.fan)+" · событие питания/экрана");
                worker.postDelayed(() -> {
                    if (running && Store.enabled(this) && Boolean.valueOf(profile.docked).equals(applied)
                        && Boolean.valueOf(profile.docked).equals(Detector.sample(this).connected)
                        && profile.fan==Profiles.get(this,profile.docked).fan && !retroid.fanMatches(profile))
                        error(new IllegalStateException("Прошивка повторно изменила режим вентилятора"));
                },1600L);
            }
        } catch (Exception e) { error(e); }
    }
    private void verify(boolean mode, int attempt) {
        if (!running || !Store.enabled(this) || !Boolean.valueOf(mode).equals(applied)) return;
        if (!Boolean.valueOf(mode).equals(Detector.sample(this).connected)) return;
        if (retroid.matches(mode)) return;
        if (attempt >= 2) {
            applied = null; failures = 3;
            error(new IllegalStateException("Прошивка переопределяет профиль. Нажмите «Повторить» после проверки настроек."));
            return;
        }
        try {
            Store.log(this, "Повторная проверка профиля: " + (attempt + 1));
            retroid.apply(mode, true);
            worker.postDelayed(() -> verify(mode, attempt + 1), 1200L);
        } catch (Exception e) { error(e); }
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
        getSystemService(DisplayManager.class).unregisterDisplayListener(listener);
        unregisterReceiver(receiver);
        if (worker != null) worker.removeCallbacksAndMessages(null);
        if (thread != null) thread.quitSafely();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
