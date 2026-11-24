package local.nova.dock;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(16,23,25), CARD = Color.rgb(26,36,39), TEXT = Color.rgb(237,246,243), MUTED = Color.rgb(165,186,181), ACCENT = Color.rgb(116,227,204);
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView status, actual, permission;
    private TextView dockBody, handheldBody, dockRate, handheldRate;
    private Switch automatic;
    private Retroid retroid;
    private boolean refreshing;
    private final Runnable refreshLoop = new Runnable() { public void run() { refresh(); main.postDelayed(this, 1500L); } };
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        retroid = new Retroid(this);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        LinearLayout root = column(); root.setPadding(dp(22), dp(16), dp(22), dp(20)); scroll.addView(root);
        TextView title = text("Nova Dock", 30, TEXT); title.setTypeface(null, Typeface.BOLD); root.addView(title);
        root.addView(text("Подключил — играешь на большом экране.\nОтключил — снова портативная консоль.", 14, MUTED));
        LinearLayout state = card(); root.addView(state, spaced());
        automatic = new Switch(this); automatic.setText("Автоматический режим"); automatic.setTextSize(17); automatic.setTextColor(TEXT); automatic.setMinHeight(dp(48));
        state.addView(automatic, new LinearLayout.LayoutParams(-1, -2));
        status = text("Проверка состояния…", 14, ACCENT); status.setPadding(0,dp(8),0,0); state.addView(status);
        actual = text("", 13, MUTED); actual.setPadding(0,dp(8),0,0); state.addView(actual);
        automatic.setOnCheckedChangeListener((button, checked) -> {
            if (refreshing) return;
            if (checked && (!retroid.supported() || !retroid.permitted())) {
                refresh(); showAccess(); return;
            }
            Store.enabled(this, checked);
            DockService.start(this, checked ? null : DockService.STOP);
            if (checked && Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            refresh();
        });
        LinearLayout profiles = new LinearLayout(this); profiles.setOrientation(LinearLayout.HORIZONTAL);
        profiles.addView(profile(true), new LinearLayout.LayoutParams(0,-2,1));
        View gap = new View(this); profiles.addView(gap, new LinearLayout.LayoutParams(dp(12),1));
        profiles.addView(profile(false), new LinearLayout.LayoutParams(0,-2,1));
        root.addView(profiles, spaced());
        permission = text("", 12, MUTED); root.addView(permission, spaced());
        LinearLayout buttons = new LinearLayout(this); buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button reapply = button("Повторить", () -> { if (Store.enabled(this)) DockService.start(this, DockService.REAPPLY); else new AlertDialog.Builder(this).setMessage("Сначала включите автоматический режим.").setPositiveButton("Понятно", null).show(); });
        buttons.addView(reapply, new LinearLayout.LayoutParams(0,dp(48),1));
        Button log = button("Журнал", () -> new AlertDialog.Builder(this).setTitle("Журнал переходов").setMessage(Store.prefs(this).getString("history", "Переходов пока не было.")).setPositiveButton("Закрыть", null).show());
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0,dp(48),1); second.leftMargin = dp(10); buttons.addView(log, second);
        root.addView(buttons, spaced());
        root.addView(button("Выключить и применить портативный профиль", () -> { Store.enabled(this, false); DockService.start(this, DockService.STOP); refresh(); }), spaced());
        TextView reset = text("Вернуть настройки, сохранённые до первого запуска", 12, MUTED); reset.setGravity(Gravity.CENTER); reset.setPadding(0,dp(16),0,dp(8));
        reset.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Восстановить исходные настройки?").setMessage("Автоматика выключится. Вернутся сохранённые исходные настройки частоты, контроллера, вентилятора, производительности и обходной зарядки.").setNegativeButton("Отмена", null).setPositiveButton("Восстановить", (d,w) -> { Store.enabled(this,false); DockService.start(this,DockService.RESTORE); refresh(); }).show()); root.addView(reset);
        String version="";
        try { version=getPackageManager().getPackageInfo(getPackageName(),0).versionName; } catch (Exception ignored) { }
        root.addView(text("Nova Dock "+version+" · Retroid Pocket Nova · Android 13", 11, MUTED));
        setContentView(scroll);
    }
    private void refresh() {
        refreshing = true; automatic.setChecked(Store.enabled(this)); refreshing = false;
        status.setText(Store.prefs(this).getString("status", "Автоматика выключена"));
        actual.setText("Сейчас: " + retroid.actual());
        Profile dock=Profiles.get(this,true), hand=Profiles.get(this,false);
        dockBody.setText(dock.body()); handheldBody.setText(hand.body());
        dockRate.setText(Profile.rateLabel(dock.rate)); handheldRate.setText(Profile.rateLabel(hand.rate));
        permission.setText(retroid.permitted() ? "Сервис Retroid доступен · root и Shizuku не нужны" : "Сервис Retroid недоступен. Нажмите здесь.");
        permission.setOnClickListener(v -> showAccess());
    }
    private void showAccess() {
        new AlertDialog.Builder(this).setTitle("Доступ к настройкам Retroid").setMessage(retroid.supported() ? "Приложение управляет профилями через встроенный сервис прошивки Retroid. Выдавать системные разрешения через ADB не требуется.\n\nЕсли сервис недоступен, сохраните номер прошивки и журнал для диагностики." : "Эта версия предназначена для Retroid Pocket Nova с Android 13.").setPositiveButton("Понятно",null).show();
    }
    @Override public void onResume() {
        super.onResume();
        if (Store.enabled(this)) DockService.start(this,null);
        main.post(refreshLoop);
    }
    @Override public void onPause() { main.removeCallbacks(refreshLoop); super.onPause(); }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout card() { LinearLayout l=column(); l.setPadding(dp(16),dp(14),dp(16),dp(14)); GradientDrawable bg=new GradientDrawable(); bg.setColor(CARD); bg.setCornerRadius(dp(18)); l.setBackground(bg); return l; }
    private LinearLayout profile(boolean docked) {
        Profile profile=Profiles.get(this,docked); LinearLayout l=card();
        l.addView(text(docked?"В ДОКЕ":"В РУКАХ",11,docked?ACCENT:Color.rgb(190,177,247)));
        TextView h=text(Profile.rateLabel(profile.rate),26,TEXT); h.setTypeface(null,Typeface.BOLD); h.setPadding(0,dp(4),0,dp(6)); l.addView(h);
        TextView body=text(profile.body(),13,MUTED); l.addView(body);
        l.addView(button("Настроить",()->editProfile(docked)),spaced());
        if (docked) { dockRate=h; dockBody=body; } else { handheldRate=h; handheldBody=body; }
        return l;
    }
    private Spinner spinner(String[] values) {
        Spinner s=new Spinner(this); ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,values);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); s.setAdapter(a); s.setMinimumHeight(dp(48)); return s;
    }
    private void bindFans(Spinner spinner,int performance,int preferred) {
        int[] ids=Profile.fans(performance); String[] labels=new String[ids.length]; int selected=0;
        for (int i=0;i<ids.length;i++) { labels[i]=Profile.fanLabel(ids[i]); if (ids[i]==preferred) selected=i; }
        ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,labels); a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(a); spinner.setTag(ids); spinner.setSelection(selected);
    }
    private int selectedFan(Spinner spinner) { int[] ids=(int[])spinner.getTag(); return ids[Math.max(0,spinner.getSelectedItemPosition())]; }
    private void editProfile(boolean docked) {
        Profile profile=Profiles.get(this,docked);
        LinearLayout content=column(); content.setPadding(dp(20),dp(10),dp(20),dp(10));
        content.addView(text("Частота экрана",13,MUTED));
        Spinner rate=spinner(new String[]{"Не менять","60 Гц","120 Гц"});
        for (int i=0;i<Profile.RATES.length;i++) if (Profile.RATES[i]==profile.rate) rate.setSelection(i);
        content.addView(rate);
        content.addView(text("Тот же параметр, что в плитке частоты Retroid. «Не менять» сохраняет текущую частоту.",11,MUTED));
        Switch bypass=new Switch(this); bypass.setText("Обходная зарядка"); bypass.setTextSize(15); bypass.setTextColor(TEXT); bypass.setMinHeight(dp(48)); bypass.setChecked(profile.bypassCharging);
        content.addView(bypass,spaced());
        content.addView(text("Штатный режим Retroid. Работает при внешнем питании с учётом настроек прошивки.",11,MUTED));
        content.addView(text("Производительность",13,MUTED),spaced());
        Spinner performance=spinner(new String[]{"Standard","Performance","High Performance"}); performance.setSelection(profile.performance); content.addView(performance);
        content.addView(text("Вентилятор",13,MUTED),spaced()); Spinner fan=new Spinner(this); fan.setMinimumHeight(dp(48)); bindFans(fan,profile.performance,profile.fan); content.addView(fan);
        content.addView(text("Доступные режимы вентилятора зависят от производительности, как в шторке Retroid.",11,MUTED));
        performance.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent,View view,int position,long id) { bindFans(fan,position,selectedFan(fan)); }
            public void onNothingSelected(AdapterView<?> parent) { }
        });
        final Spinner layout;
        if (!docked) {
            content.addView(text("Раскладка встроенного контроллера",13,MUTED),spaced());
            layout=spinner(new String[]{"Xbox","Retro"}); layout.setSelection(profile.layout); content.addView(layout);
        } else { layout=null; content.addView(text("Встроенный контроллер в доке отключается.",12,MUTED),spaced()); }
        ScrollView scroll=new ScrollView(this); scroll.addView(content);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(docked?"Профиль: в доке":"Профиль: в руках").setView(scroll).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                Profile selected=new Profile(docked,Profile.RATES[rate.getSelectedItemPosition()],selectedFan(fan),performance.getSelectedItemPosition(),docked?2:layout.getSelectedItemPosition(),bypass.isChecked());
                Profiles.save(this,selected); Store.log(this,(docked?"Профиль дока":"Портативный профиль")+" сохранён");
                if (Store.enabled(this) && Store.prefs(this).getBoolean("last_docked",false)==docked) DockService.start(this,DockService.REAPPLY);
                dialog.dismiss(); refresh(); Toast.makeText(this,"Профиль сохранён",Toast.LENGTH_SHORT).show();
            } catch (Exception e) { Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show(); }
        }));
        dialog.show();
    }
    private TextView text(String s,int size,int color) { TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color); t.setLineSpacing(dp(2),1f); return t; }
    private Button button(String label,Runnable action) { Button b=new Button(this); b.setText(label); b.setTextSize(13); b.setAllCaps(false); b.setTextColor(TEXT); b.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(40,58,61))); b.setOnClickListener(v->action.run()); return b; }
    private LinearLayout.LayoutParams spaced() { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.topMargin=dp(14); return p; }
}
