package local.nova.dock;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final int BG=0xff101719, CARD=0xff1a2427, TEXT=0xffedf6f3, MUTED=0xffa5bab5, ACCENT=0xff74e3cc;
    private final Handler main=new Handler(Looper.getMainLooper());
    private TextView status, permission;
    private ProfileCard dockCard, handheldCard;
    private Button retry, manualDock, manualHandheld;
    private LinearLayout manualModes;
    private Switch automatic, startOnBoot;
    private Retroid retroid;
    private boolean refreshing;
    private final long[][] pendingRevision=new long[2][5];
    private final int[][] pendingValue=new int[2][5];
    private final Runnable refreshLoop=new Runnable() { public void run() { refresh(); main.postDelayed(this,1500L); } };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b); retroid=new Retroid(this);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        LinearLayout root=column(); root.setPadding(dp(18),dp(12),dp(18),dp(18)); scroll.addView(root);
        LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text("Nova Dock",28,TEXT); title.setTypeface(null,Typeface.BOLD); header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        header.addView(button("Журнал",()->HistoryDialog.show(this)),new LinearLayout.LayoutParams(dp(96),dp(44))); root.addView(header);

        LinearLayout state=card(); root.addView(state,spaced(12));
        automatic=switchControl("Переключать режимы автоматически");
        state.addView(automatic,new LinearLayout.LayoutParams(-1,-2));
        View divider=new View(this); divider.setBackgroundColor(0xff304044);
        LinearLayout.LayoutParams separator=new LinearLayout.LayoutParams(-1,dp(1)); separator.topMargin=dp(4); separator.bottomMargin=dp(1); state.addView(divider,separator);
        startOnBoot=switchControl("Запускать после загрузки");
        state.addView(startOnBoot,new LinearLayout.LayoutParams(-1,-2));
        startOnBoot.setOnCheckedChangeListener((button,checked)->{
            if (refreshing) return;
            Store.startOnBoot(this,checked); Store.log(this,"Автозапуск "+(checked?"включён":"выключен"));
        });
        manualModes=new LinearLayout(this); manualModes.setOrientation(LinearLayout.HORIZONTAL);
        manualDock=button("В доке",()->selectManual(true)); manualDock.setTextSize(14);
        manualHandheld=button("В руках",()->selectManual(false)); manualHandheld.setTextSize(14);
        manualModes.addView(manualDock,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams handheldMode=new LinearLayout.LayoutParams(0,-2,1); handheldMode.leftMargin=dp(8);
        manualModes.addView(manualHandheld,handheldMode); state.addView(manualModes,spaced(8));
        status=text("",13,ACCENT); status.setPadding(dp(12),dp(6),dp(12),0); status.setVisibility(View.GONE); state.addView(status);
        retry=button("Применить снова",()->{ if (Store.managed(this)) DockService.start(this,DockService.REAPPLY); });
        retry.setVisibility(View.GONE); state.addView(retry,spaced(8));
        automatic.setOnCheckedChangeListener((button,checked)->{
            if (refreshing) return;
            if (checked && (!retroid.supported() || !retroid.permitted())) { refresh(); showAccess(); return; }
            DockService.automatic(this,checked);
            if (checked) requestNotificationPermission();
            refresh();
        });
        LinearLayout profiles=new LinearLayout(this); profiles.setOrientation(LinearLayout.HORIZONTAL);
        dockCard=new ProfileCard(this,true,setting->changeProfile(true,setting));
        handheldCard=new ProfileCard(this,false,setting->changeProfile(false,setting));
        profiles.addView(dockCard,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams hand=new LinearLayout.LayoutParams(0,-2,1); hand.leftMargin=dp(12); profiles.addView(handheldCard,hand);
        root.addView(profiles,spaced(10));
        permission=text("",12,0xfff4aaa9); permission.setOnClickListener(v->showAccess()); GamepadControls.style(permission,0,ACCENT,14); root.addView(permission,spaced(10));
        TextView reset=text("Восстановить исходные настройки",12,MUTED); reset.setGravity(Gravity.CENTER); reset.setMinHeight(dp(48));
        GamepadControls.style(reset,0,ACCENT,14);
        reset.setOnClickListener(v->{
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Восстановить исходные настройки?")
                .setMessage("Управление выключится. Вернутся настройки, сохранённые перед первым применением профиля.")
                .setNegativeButton("Отмена",null).setPositiveButton("Восстановить",(d,w)->{
                    DockService.restore(this); refresh();
                }).show();
            GamepadControls.attach(dialog);
        }); root.addView(reset,spaced(6));
        String version=""; try { version=getPackageManager().getPackageInfo(getPackageName(),0).versionName; } catch (Exception ignored) { }
        TextView footer=text("Nova Dock "+version+" · Retroid Pocket Nova",10,MUTED); footer.setGravity(Gravity.CENTER); root.addView(footer);
        setContentView(scroll);
    }
    private void changeProfile(boolean docked,int setting) {
        try {
            Profile before=Profiles.get(this,docked);
            boolean active=Store.managed(this) && Store.lastDocked(this)==docked;
            RuntimeSettings live=runtimeSettings(docked,active);
            Profile selected=active?before.next(setting,live.value(before,setting),live.value(before,Profile.PERFORMANCE)):before.next(setting);
            if (selected==before) return;
            Profiles.save(this,selected);
            String detail=Profile.settingLabel(setting)+": "+(active?live.label(before,setting):before.value(setting))+" → "+selected.value(setting);
            if (setting==Profile.PERFORMANCE && selected.fan!=before.fan) detail+=" · Вентилятор профиля: "+Profile.fanLabel(before.fan)+" → "+Profile.fanLabel(selected.fan);
            Store.log(this,(docked?"Профиль дока":"Портативный профиль")+" сохранён · "+detail);
            if (active) {
                int mode=docked?1:0;
                SettingChanges.Request request=DockService.change(this,selected,setting);
                pendingRevision[mode][setting]=request.revision;
                pendingValue[mode][setting]=selected.intValue(setting);
                if (request.rebasedFan!=null) {
                    pendingRevision[mode][Profile.FAN]=request.rebasedFan.revision;
                    pendingValue[mode][Profile.FAN]=request.rebasedFan.profile.fan;
                }
            }
            refresh();
        } catch (Exception e) { Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show(); }
    }
    private void refresh() {
        boolean auto=Store.enabled(this), managed=Store.managed(this);
        refreshing=true; automatic.setChecked(auto); startOnBoot.setChecked(Store.startOnBoot(this)); refreshing=false;
        manualModes.setVisibility(auto?View.GONE:View.VISIBLE);
        boolean selected=Store.manualDocked(this); manualDock.setSelected(selected); manualHandheld.setSelected(!selected);
        String message=Store.prefs(this).getString("status",""); status.setText(message);
        boolean error=message.startsWith("Ошибка:"); status.setTextColor(error?0xfff4aaa9:ACCENT);
        boolean waiting=managed && (message.startsWith("Ожидание") || message.startsWith("Проверка"));
        status.setVisibility(error || waiting?View.VISIBLE:View.GONE);
        retry.setVisibility(error && managed?View.VISIBLE:View.GONE);
        boolean docked=Store.lastDocked(this);
        RuntimeSettings live=runtimeSettings(docked,managed);
        dockCard.update(Profiles.get(this,true),managed && docked,live); handheldCard.update(Profiles.get(this,false),managed && !docked,live);
        boolean access=retroid.permitted(); permission.setVisibility(access?View.GONE:View.VISIBLE);
        permission.setText("Сервис Retroid недоступен · сведения");
    }
    private RuntimeSettings runtimeSettings(boolean docked,boolean active) {
        int mode=docked?1:0;
        // Observe ACKs before the hardware snapshot so an ACK cannot expose an older read.
        if (active) for (int setting=0;setting<5;setting++) if (pendingRevision[mode][setting]!=0) {
            if (DockService.acknowledged(docked,setting,pendingRevision[mode][setting])) pendingRevision[mode][setting]=0;
        }
        RuntimeSettings live=new RuntimeSettings(retroid.snapshot());
        if (active) for (int setting=0;setting<5;setting++)
            if (pendingRevision[mode][setting]!=0) live.pending(setting,pendingValue[mode][setting]);
        return live;
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (GamepadControls.handle(event,getCurrentFocus(),this::onBackPressed)) return true;
        return super.dispatchKeyEvent(event);
    }
    private void showAccess() {
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Доступ к настройкам Retroid").setMessage(retroid.supported()
            ?"Сервис прошивки Retroid недоступен. Сохраните номер прошивки и журнал для диагностики."
            :"Эта версия предназначена для Retroid Pocket Nova с Android 13.").setPositiveButton("Понятно",null).show();
        GamepadControls.attach(dialog);
    }
    private void selectManual(boolean docked) {
        if (!retroid.supported() || !retroid.permitted()) { showAccess(); return; }
        DockService.manual(this,docked); requestNotificationPermission(); refresh();
    }
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},1);
    }
    @Override public void onResume() { super.onResume(); if (Store.managed(this)) DockService.start(this,null); main.post(refreshLoop); }
    @Override public void onPause() { main.removeCallbacks(refreshLoop); super.onPause(); }
    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout card() { LinearLayout l=column(); l.setPadding(dp(14),dp(8),dp(14),dp(12)); GradientDrawable bg=new GradientDrawable(); bg.setColor(CARD); bg.setCornerRadius(dp(20)); l.setBackground(bg); return l; }
    private TextView text(String s,int size,int color) { TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color); t.setLineSpacing(dp(2),1f); return t; }
    private Switch switchControl(String label) {
        Switch control=new Switch(this); control.setText(label); control.setTextSize(16); control.setTextColor(TEXT); control.setMinHeight(dp(48));
        GamepadControls.style(control,0,ACCENT,14); return control;
    }
    private Button button(String label,Runnable action) {
        Button b=new Button(this); b.setText(label); b.setTextSize(13); b.setAllCaps(false); b.setTextColor(TEXT);
        GamepadControls.style(b,0xff283a3d,ACCENT,14); b.setOnClickListener(v->action.run()); return b;
    }
    private LinearLayout.LayoutParams spaced(int gap) { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.topMargin=dp(gap); return p; }
}
