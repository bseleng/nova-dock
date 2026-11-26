package local.nova.dock;

import android.app.AlertDialog;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

final class SettingsDialog {
    private final MainActivity owner;
    final NativeDialog panel;
    private final Switch automatic,boot,screen,refresh;
    private final Button language,retry;
    private final TextView error;
    private final long[] pending=new long[GlobalChanges.Key.values().length];
    private boolean refreshing;
    private AlertDialog child;
    SettingsDialog(MainActivity owner,View returnFocus) {
        this.owner=owner; panel=new NativeDialog(owner,R.string.dialog_settings_title,returnFocus,true);
        panel.setLeftHoldAction(R.string.action_restore,R.id.action_restore_settings,this::restore);
        panel.dismissed=()->{ if (child!=null) child.dismiss(); };
        ScrollView scroll=new ScrollView(owner); LinearLayout body=NativeDialog.column(owner); scroll.addView(body);
        automatic=toggle(R.string.label_auto_switch,R.id.toggle_automatic); body.addView(automatic,row(0));
        boot=toggle(R.string.label_start_on_boot,R.id.toggle_start_on_boot); body.addView(boot,row(6));
        screen=toggle(R.string.label_screen_off,R.id.toggle_screen_off); screen.setContentDescription(owner.getString(R.string.a11y_screen_off)); body.addView(screen,row(6));
        refresh=toggle(R.string.label_show_refresh_rate,R.id.toggle_show_refresh); body.addView(refresh,row(6));
        automatic.setOnCheckedChangeListener((button,checked)->{ if (!refreshing) owner.setAutomatic(checked); });
        boot.setOnCheckedChangeListener((button,checked)->{ if (!refreshing) { Store.startOnBoot(owner,checked); Store.event(owner,"boot_changed",checked?"1":"0"); } });
        screen.setOnCheckedChangeListener((button,checked)->{ if (!refreshing) change(GlobalChanges.Key.SCREEN_OFF,checked); });
        refresh.setOnCheckedChangeListener((button,checked)->{ if (!refreshing) change(GlobalChanges.Key.SHOW_REFRESH,checked); });
        language=NativeDialog.button(owner,R.string.label_language,R.id.language,this::language); NativeDialog.compactButton(language); body.addView(language,row(8));
        error=NativeDialog.text(owner,"",12,0xfff4aaa9); error.setPadding(dp(12),0,dp(12),0); body.addView(error,row(8));
        retry=NativeDialog.button(owner,R.string.action_retry,0,()->DockService.refresh(owner)); NativeDialog.compactButton(retry); body.addView(retry,row(6));
        LinearLayout.LayoutParams scrollParams=new LinearLayout.LayoutParams(-1,0,1); scrollParams.topMargin=dp(8);
        panel.root.addView(scroll,scrollParams);
        refresh(); panel.show(true,automatic); DockService.refresh(owner);
    }
    private Switch toggle(int title,int id) {
        Switch control=new Switch(owner); control.setId(id); control.setText(title); control.setTextColor(NativeDialog.TEXT); control.setTextSize(14);
        GamepadControls.style(control,0,NativeDialog.ACCENT,14); control.setMinHeight(dp(44)); control.setMinimumHeight(dp(44)); control.setPadding(dp(10),dp(6),dp(10),dp(6)); return control;
    }
    private void change(GlobalChanges.Key key,boolean value) {
        try { pending[key.ordinal()]=DockService.global(owner,key,value,null).revision; refresh(); }
        catch (Exception e) { Toast.makeText(owner,AppText.error(owner,e),Toast.LENGTH_LONG).show(); refresh(); }
    }
    void refresh() {
        if (automatic==null) return;
        for (GlobalChanges.Key key:GlobalChanges.Key.values()) {
            long revision=pending[key.ordinal()];
            if (revision!=0 && DockService.globalAcknowledged(key,revision)) {
                pending[key.ordinal()]=0;
            }
        }
        ViewSnapshot state=DockService.snapshot();
        boolean ready=state!=null && state.supported && state.permitted && state.globals!=null && state.globalError==null;
        refreshing=true;
        automatic.setChecked(Store.enabled(owner)); boot.setChecked(Store.startOnBoot(owner));
        automatic.setEnabled(state!=null && state.supported && state.permitted);
        GlobalChanges.Request screenTarget=DockService.globalTarget(GlobalChanges.Key.SCREEN_OFF),refreshTarget=DockService.globalTarget(GlobalChanges.Key.SHOW_REFRESH);
        screen.setChecked(screenTarget!=null?screenTarget.value:state==null || state.globals==null || state.globals.screenOff);
        refresh.setChecked(refreshTarget!=null?refreshTarget.value:state!=null && state.globals!=null && state.globals.showRefresh);
        screen.setEnabled(ready); refresh.setEnabled(ready);
        refreshing=false;
        String selected=AppText.language(owner);
        language.setText(owner.getString(R.string.label_language)+" · "+owner.getString("ru".equals(selected)?R.string.language_russian:"en".equals(selected)?R.string.language_english:R.string.language_system));
        error.setText(state!=null && state.globalError!=null?AppText.error(owner,state.globalError,""):"");
        error.setVisibility(state!=null && state.globalError!=null?View.VISIBLE:View.GONE); retry.setVisibility(error.getVisibility());
    }
    private void language() {
        if (child!=null && child.isShowing()) return;
        String[] codes={"system","ru","en"}; String selected=AppText.language(owner); int current=0;
        for (int i=0;i<codes.length;i++) if (codes[i].equals(selected)) current=i;
        String[] labels={owner.getString(R.string.language_system),owner.getString(R.string.language_russian),owner.getString(R.string.language_english)};
        HeaderAlertDialog chooser=new HeaderAlertDialog(owner,R.string.dialog_language_title,language);
        chooser.builder.setSingleChoiceItems(labels,current,(dialog,index)->{
            try { AppText.setLanguage(owner,codes[index]); }
            catch (Exception error) { Toast.makeText(owner,AppText.error(owner,error),Toast.LENGTH_LONG).show(); }
            dialog.dismiss(); refresh();
        });
        chooser.dismissed=()->{ if (child==chooser.dialog) child=null; }; child=chooser.show(true,current);
    }
    private void restore() {
        DockService.restore(owner); panel.dialog.dismiss();
    }
    void dismiss() { if (child!=null) child.dismiss(); panel.dialog.dismiss(); }
    private LinearLayout.LayoutParams row(int topMargin) {
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2); params.topMargin=dp(topMargin); return params;
    }
    private int dp(int value) { return NativeDialog.dp(owner,value); }
}
