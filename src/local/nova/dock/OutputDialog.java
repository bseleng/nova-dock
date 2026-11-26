package local.nova.dock;

import android.app.AlertDialog;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Two stock selectors edit a profile draft; only Apply/Save commits it. */
final class OutputDialog {
    final NativeDialog panel;
    private final MainActivity owner;
    private final boolean docked;
    private final OutputSpec original;
    private final TextView current;
    private final Button ratio,timing,apply;
    private OutputMode.Aspect aspect;
    private OutputMode.Preset preset;
    private AlertDialog child;

    OutputDialog(MainActivity owner,View returnFocus,boolean docked,OutputSpec saved) {
        this.owner=owner; this.docked=docked; original=saved; aspect=saved.aspect; preset=saved.preset;
        panel=new NativeDialog(owner,R.string.output_mode_title,returnFocus);
        panel.setTitle(owner.getString(R.string.output_mode_title)+" · "+owner.getString(docked?R.string.mode_docked:R.string.mode_handheld));
        panel.dismissed=()->{ if (child!=null) child.dismiss(); };
        ScrollView scroll=new ScrollView(owner); LinearLayout body=NativeDialog.column(owner); scroll.addView(body);
        current=NativeDialog.text(owner,"",12,NativeDialog.MUTED); current.setPadding(dp(12),dp(8),dp(12),dp(8)); body.addView(current);
        ratio=selector(R.id.output_aspect,this::chooseAspect); body.addView(ratio,row(0));
        timing=selector(R.id.output_mode,this::chooseTiming); body.addView(timing,row(8));
        panel.root.addView(scroll,row(8));
        apply=NativeDialog.button(owner,R.string.action_apply,0,this::apply); panel.root.addView(apply,row(12));
        update(); panel.show(false,ratio); DockService.refresh(owner);
    }
    private Button selector(int id,Runnable click) {
        Button button=NativeDialog.button(owner,R.string.output_mode_title,id,click);
        button.setGravity(Gravity.START|Gravity.CENTER_VERTICAL); button.setMinHeight(dp(64)); button.setTextSize(16); return button;
    }
    private LinearLayout.LayoutParams row(int top) {
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2); params.topMargin=dp(top); return params;
    }
    private void chooseAspect() {
        if (child!=null && child.isShowing()) return;
        OutputMode.Aspect[] values=OutputMode.Aspect.values(); String[] labels=new String[values.length];
        for (int i=0;i<values.length;i++) labels[i]=GlobalText.aspect(owner,values[i]);
        HeaderAlertDialog chooser=new HeaderAlertDialog(owner,R.string.output_aspect_title,ratio);
        chooser.builder.setSingleChoiceItems(labels,aspect.ordinal(),(dialog,index)->{ aspect=values[index]; update(); dialog.dismiss(); });
        chooser.dismissed=()->{ if (child==chooser.dialog) child=null; }; child=chooser.show(true,aspect.ordinal());
    }
    private void chooseTiming() {
        if (child!=null && child.isShowing()) return;
        OutputMode.Preset[] values=OutputMode.Preset.values(); String[] labels=new String[values.length];
        for (int i=0;i<values.length;i++) labels[i]=GlobalText.preset(owner,values[i],aspect);
        HeaderAlertDialog chooser=new HeaderAlertDialog(owner,R.string.output_timing_title,timing);
        chooser.builder.setSingleChoiceItems(labels,preset==null?-1:preset.ordinal(),(dialog,index)->{ preset=values[index]; update(); dialog.dismiss(); });
        chooser.header.setLeftAction(R.string.value_unchanged,R.id.action_keep_output,()->{ preset=null; update(); chooser.dismiss(); });
        chooser.header.left.setSelected(preset==null);
        chooser.dismissed=()->{ if (child==chooser.dialog) child=null; }; child=chooser.show(preset!=null,preset==null?-1:preset.ordinal());
    }
    void refreshLive() { update(); }
    private void update() {
        ratio.setText(owner.getString(R.string.setting_output_aspect)+"\n"+GlobalText.aspect(owner,aspect));
        timing.setText(owner.getString(R.string.output_timing_title)+"\n"+(preset==null?owner.getString(R.string.value_unchanged):GlobalText.preset(owner,preset,aspect)));
        boolean active=Store.state(owner).activeProfile(docked); ViewSnapshot state=DockService.snapshot();
        GlobalSettings.Snapshot actual=state!=null && state.globalError==null?state.globals:null;
        current.setVisibility(active && actual!=null?View.VISIBLE:View.GONE);
        if (active && actual!=null) current.setText(owner.getString(R.string.current_value,GlobalText.summary(owner,actual)));
        OutputSpec draft=new OutputSpec(aspect,preset);
        apply.setText(active?R.string.action_apply:R.string.action_save);
        apply.setEnabled(!draft.equals(original) || active && actual!=null && !draft.matches(actual.aspect,actual.preset));
    }
    private void apply() { owner.saveOutput(docked,new OutputSpec(aspect,preset)); panel.dialog.dismiss(); }
    void dismiss() { if (child!=null) child.dismiss(); panel.dialog.dismiss(); }
    private int dp(int value) { return NativeDialog.dp(owner,value); }
}
