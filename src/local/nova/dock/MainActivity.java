package local.nova.dock;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Collections;

public final class MainActivity extends Activity {
    private final Handler main=new Handler(Looper.getMainLooper());
    private TextView status,permission;
    private ProfileCard dockCard,handheldCard;
    private Button retry,manualDock,manualHandheld,settingsButton,logButton;
    private LinearLayout manualModes;
    private OutputTile dockOutput,handheldOutput;
    private SettingsDialog settings;
    private OutputDialog output;
    private HistoryDialog history;
    private HeaderBar header;
    private AlertDialog access;
    private final Runnable observer=this::refresh;
    private final Runnable refreshLoop=new Runnable() {
        public void run() { if (Store.managed(MainActivity.this)) DockService.refreshRuntime(MainActivity.this); refresh(); main.postDelayed(this,1500L); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page=NativeDialog.column(this); page.setBackgroundColor(NativeDialog.BG); page.setPadding(0,dp(12),0,0);
        header=new HeaderBar(this,"nova/dock",true); header.titleView.setId(R.id.title);
        header.setLeftAction(R.string.action_log,R.id.action_log,this::showLog); header.setRightAction(R.string.action_settings,R.id.action_settings,this::showSettings);
        logButton=header.left; settingsButton=header.right;
        logButton.setContentDescription(getString(R.string.a11y_log)); settingsButton.setContentDescription(getString(R.string.a11y_settings));
        LinearLayout.LayoutParams heading=new LinearLayout.LayoutParams(-1,dp(52)); heading.leftMargin=dp(18); heading.rightMargin=dp(18); page.addView(header,heading);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout root=NativeDialog.column(this); root.setPadding(dp(18),0,dp(18),dp(18)); scroll.addView(root); page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        manualModes=new LinearLayout(this); manualModes.setOrientation(LinearLayout.HORIZONTAL);
        manualDock=NativeDialog.button(this,R.string.mode_docked,R.id.manual_docked,()->selectManual(true));
        manualHandheld=NativeDialog.button(this,R.string.mode_handheld,R.id.manual_handheld,()->selectManual(false));
        manualModes.addView(manualDock,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams hand=new LinearLayout.LayoutParams(0,-2,1); hand.leftMargin=dp(8); manualModes.addView(manualHandheld,hand); root.addView(manualModes,spaced(10));
        status=NativeDialog.text(this,"",13,NativeDialog.ACCENT); status.setPadding(dp(12),dp(6),dp(12),0); status.setVisibility(View.GONE); root.addView(status);
        retry=NativeDialog.button(this,R.string.action_retry,0,this::retry); retry.setVisibility(View.GONE); root.addView(retry,spaced(6));

        dockOutput=new OutputTile(true,R.id.dock_output,NativeDialog.ACCENT);
        handheldOutput=new OutputTile(false,R.id.handheld_output,0xffbeb1f7);

        LinearLayout profiles=new TileSizing.Cards(this); profiles.setOrientation(LinearLayout.HORIZONTAL);
        dockCard=new ProfileCard(this,true,setting->changeProfile(true,setting),dockOutput); handheldCard=new ProfileCard(this,false,setting->changeProfile(false,setting),handheldOutput);
        profiles.addView(dockCard,new LinearLayout.LayoutParams(0,-2,1)); LinearLayout.LayoutParams handheld=new LinearLayout.LayoutParams(0,-2,1); handheld.leftMargin=dp(12); profiles.addView(handheldCard,handheld); root.addView(profiles,spaced(10));
        permission=NativeDialog.text(this,getString(R.string.access_unavailable),12,0xfff4aaa9); permission.setOnClickListener(v->showAccess()); GamepadControls.style(permission,0,NativeDialog.ACCENT,14); permission.setVisibility(View.GONE); root.addView(permission,spaced(8));
        String version=""; try { version=getPackageManager().getPackageInfo(getPackageName(),0).versionName; } catch (Exception ignored) { }
        TextView footer=NativeDialog.text(this,getString(R.string.format_footer,version),10,NativeDialog.MUTED); footer.setGravity(Gravity.CENTER); footer.setPadding(0,dp(10),0,0); root.addView(footer);
        setContentView(page); refresh();
    }
    private void showSettings() {
        if (settings!=null && settings.panel.dialog.isShowing()) return;
        settings=new SettingsDialog(this,getCurrentFocus()==null?settingsButton:getCurrentFocus());
    }
    private void showLog() {
        if (history!=null && history.panel.dialog.isShowing()) return;
        history=new HistoryDialog(this,getCurrentFocus()==null?logButton:getCurrentFocus());
    }
    private void showOutput(boolean docked,View returnFocus) {
        output=new OutputDialog(this,returnFocus,docked,OutputProfiles.get(this,docked));
    }
    void saveOutput(boolean docked,OutputSpec selected) {
        try {
            OutputSpec before=OutputProfiles.get(this,docked);
            if (!before.equals(selected)) {
                OutputProfiles.save(this,docked,selected);
                Store.event(this,"output_saved",docked?"1":"0",before.aspect.name(),before.presetCode(),selected.aspect.name(),selected.presetCode());
            }
            if (Store.state(this).activeProfile(docked)) DockService.changeOutput(this,docked,selected);
            refresh();
        }
        catch (Exception e) { Toast.makeText(this,AppText.error(this,e),Toast.LENGTH_LONG).show(); }
    }
    void setAutomatic(boolean enabled) {
        if (enabled && !ready()) { showAccess(); if (settings!=null) settings.refresh(); return; }
        DockService.automatic(this,enabled); Store.event(this,"auto_changed",enabled?"1":"0");
        if (enabled) requestNotificationPermission(); refresh();
    }
    private boolean ready() { ViewSnapshot state=DockService.snapshot(); return state!=null && state.supported && state.permitted; }
    private void selectManual(boolean docked) {
        if (!ready()) { showAccess(); return; }
        DockService.manual(this,docked); requestNotificationPermission(); refresh();
    }
    private void changeProfile(boolean docked,int setting) {
        try {
            Profile before=Profiles.get(this,docked); boolean active=Store.state(this).activeProfile(docked);
            RuntimeSettings live=runtimeSettings(docked,active); int oldValue=active?live.value(before,setting):before.intValue(setting);
            Profile selected=active?before.next(setting,oldValue,live.value(before,Profile.PERFORMANCE)):before.next(setting);
            if (selected==before) return; Profiles.save(this,selected);
            Store.event(this,"profile_saved",docked?"1":"0",Integer.toString(setting),Integer.toString(oldValue),Integer.toString(selected.intValue(setting)),Integer.toString(before.fan),Integer.toString(selected.fan),active && live.customFan(before)?"1":"0");
            if (active) DockService.change(this,selected,setting);
            refresh();
        } catch (Exception e) { Toast.makeText(this,AppText.error(this,e),Toast.LENGTH_LONG).show(); }
    }
    private void refresh() {
        if (manualModes==null) return;
        boolean managed=Store.managed(this),docked=Store.lastDocked(this);
        manualModes.setVisibility(Store.enabled(this)?View.GONE:View.VISIBLE); boolean selected=Store.manualDocked(this); manualDock.setSelected(selected); manualHandheld.setSelected(!selected);
        for (GlobalChanges.Key key:new GlobalChanges.Key[]{GlobalChanges.Key.SCREEN_OFF,GlobalChanges.Key.SHOW_REFRESH}) {
            GlobalChanges.Result failure=DockService.takeGlobalError(key);
            if (failure!=null) Toast.makeText(this,AppText.error(this,failure.error,failure.detail),Toast.LENGTH_LONG).show();
        }
        ViewSnapshot state=DockService.snapshot();
        boolean profileError="error".equals(Store.statusCode(this)),waiting="waiting".equals(Store.statusCode(this));
        boolean globalError=state!=null && state.globalError!=null;
        status.setText(profileError || waiting?AppStatus.text(this):globalError?AppText.error(this,state.globalError,""):"");
        status.setTextColor(profileError || globalError?0xfff4aaa9:NativeDialog.ACCENT); status.setVisibility(profileError || globalError || managed && waiting?View.VISIBLE:View.GONE);
        retry.setVisibility(profileError && managed || globalError?View.VISIBLE:View.GONE);
        dockOutput.update(state,managed && docked); handheldOutput.update(state,managed && !docked);
        RuntimeSettings live=runtimeSettings(docked,managed); dockCard.update(Profiles.get(this,true),managed && docked,live); handheldCard.update(Profiles.get(this,false),managed && !docked,live);
        permission.setVisibility(state!=null && (!state.permitted || !state.supported)?View.VISIBLE:View.GONE);
        if (settings!=null && settings.panel.dialog.isShowing()) settings.refresh();
        if (output!=null && output.panel.dialog.isShowing()) output.refreshLive();
    }
    private void retry() {
        if ("error".equals(Store.statusCode(this)) && Store.managed(this)) DockService.start(this,DockService.REAPPLY); else DockService.refresh(this);
    }
    private RuntimeSettings runtimeSettings(boolean docked,boolean active) {
        SettingChanges.Request[] targets=new SettingChanges.Request[5];
        if (active) for (int setting=0;setting<5;setting++) targets[setting]=DockService.profileTarget(docked,setting);
        ViewSnapshot state=DockService.snapshot(); RuntimeSettings live=new RuntimeSettings(state==null?Collections.emptyMap():state.runtime);
        if (active) for (int setting=0;setting<5;setting++) if (targets[setting]!=null) live.pending(setting,targets[setting].profile.intValue(setting)); return live;
    }
    private void showAccess() {
        ViewSnapshot state=DockService.snapshot();
        if (state==null) { DockService.refresh(this); return; }
        if (access!=null && access.isShowing()) return;
        View returnFocus=settings!=null && settings.panel.dialog.isShowing()?settings.panel.dialog.getCurrentFocus():getCurrentFocus();
        HeaderAlertDialog info=new HeaderAlertDialog(this,R.string.dialog_access_title,returnFocus);
        info.builder.setMessage(state.supported?R.string.dialog_access_message:R.string.dialog_unsupported_message);
        info.dismissed=()->{ if (access==info.dialog) access=null; }; access=info.show(false,-1);
    }
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},1);
    }
    @Override public void onResume() {
        super.onResume(); GamepadControls.attach(this,header,this::onBackPressed); DockService.observe(observer); if (Store.managed(this)) DockService.start(this,null); DockService.refresh(this); main.post(refreshLoop);
    }
    @Override public void onPause() {
        GamepadControls.stop(this);
        main.removeCallbacks(refreshLoop); DockService.unobserve(observer);
        if (access!=null) access.dismiss();
        if (settings!=null) settings.dismiss(); if (output!=null) output.dismiss(); if (history!=null) history.dismiss(); super.onPause();
    }
    private final class OutputTile extends LinearLayout {
        private final TextView primary,secondary,hint;
        private final boolean docked;
        OutputTile(boolean docked,int id,int accent) {
            super(MainActivity.this); setId(id); setOrientation(VERTICAL); setGravity(Gravity.CENTER); setPadding(dp(12),dp(10),dp(12),dp(10)); setMinimumHeight(dp(94));
            this.docked=docked; GamepadControls.style(this,0xf228383b,accent,15); setOnClickListener(v->showOutput(docked,this));
            ImageView icon=new ImageView(MainActivity.this); icon.setImageResource(R.drawable.ic_output); icon.setImageTintList(android.content.res.ColorStateList.valueOf(accent)); icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(icon,new LinearLayout.LayoutParams(dp(23),dp(23)));
            primary=NativeDialog.text(MainActivity.this,getString(R.string.status_updating),14,NativeDialog.TEXT); primary.setTypeface(null,Typeface.BOLD); primary.setGravity(Gravity.CENTER); primary.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams label=new LinearLayout.LayoutParams(-1,-2); label.topMargin=dp(8); addView(primary,label);
            secondary=NativeDialog.text(MainActivity.this,"",9,NativeDialog.MUTED); secondary.setGravity(Gravity.CENTER); secondary.setPadding(0,dp(3),0,0); secondary.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); addView(secondary,new LinearLayout.LayoutParams(-1,-2));
            hint=NativeDialog.text(MainActivity.this,"",9,NativeDialog.MUTED); hint.setGravity(Gravity.CENTER); hint.setPadding(0,dp(3),0,0); hint.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); addView(hint,new LinearLayout.LayoutParams(-1,-2));
        }
        void update(ViewSnapshot state,boolean active) {
            OutputSpec saved=OutputProfiles.get(MainActivity.this,docked);
            SettingChanges.Request target=active?DockService.profileTarget(docked,SettingChanges.OUTPUT):null;
            GlobalSettings.Snapshot actual=state!=null && state.globalError==null?state.globals:null;
            String value,resolution,detail="",summary;
            if (target!=null) {
                value=GlobalText.savedPrimary(MainActivity.this,target.output); resolution=GlobalText.savedSecondary(target.output); summary=GlobalText.savedSummary(MainActivity.this,target.output); detail=getString(R.string.applying);
            } else if (active && actual!=null) {
                value=GlobalText.compactPrimary(MainActivity.this,actual); resolution=GlobalText.compactSecondary(actual); summary=GlobalText.summary(MainActivity.this,actual);
                if (!saved.matches(actual.aspect,actual.preset)) detail=getString(R.string.saved_value,GlobalText.savedSummary(MainActivity.this,saved));
            } else if (active) {
                value=getString(R.string.value_unknown); resolution=""; summary=value; detail=getString(R.string.saved_value,GlobalText.savedSummary(MainActivity.this,saved));
            } else { value=GlobalText.savedPrimary(MainActivity.this,saved); resolution=GlobalText.savedSecondary(saved); summary=GlobalText.savedSummary(MainActivity.this,saved); }
            primary.setText(value); secondary.setText(resolution); secondary.setVisibility(resolution.isEmpty()?GONE:VISIBLE); hint.setText(detail); hint.setVisibility(detail.isEmpty()?GONE:VISIBLE);
            setContentDescription(getString(docked?R.string.mode_docked:R.string.mode_handheld)+" · "+getString(R.string.setting_output_mode)+": "+summary+(detail.isEmpty()?"":". "+detail));
        }
    }
    private int dp(int value) { return NativeDialog.dp(this,value); }
    private LinearLayout.LayoutParams spaced(int top) { LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2); params.topMargin=dp(top); return params; }
}
