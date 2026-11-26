package local.nova.dock;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inspector.WindowInspector;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Debug UI instrumentation; it issues no native-setting commands. Launching Main may
 * resume its stored managed session. The coordinator owns external state snapshots/restoration.
 */
final class AnalogUiCheck {
    private interface Ui<T> { T get() throws Exception; }
    private static final long TOTAL_TIMEOUT_MS=120_000L,ACTIVITY_TIMEOUT_MS=6_000L;
    private final Instrumentation instrumentation;
    private volatile UiAutomation automation;
    private final StringBuffer report=new StringBuffer();
    private MainActivity activity;
    private View mainRoot;
    private volatile String phase="launch",timeoutPhase;
    private volatile boolean cancelled;
    private boolean loggedMainTouch,loggedDialogTouch;
    private final boolean holdOnly;
    private long fixtureADown,fixtureTouchDown;
    private float fixtureTouchX,fixtureTouchY;

    private AnalogUiCheck(Instrumentation instrumentation,boolean holdOnly) {
        this.instrumentation=instrumentation; this.holdOnly=holdOnly;
    }

    static void run(Instrumentation instrumentation) { run(instrumentation,false); }
    static void run(Instrumentation instrumentation,boolean holdOnly) {
        Bundle result=new Bundle(); int code=Activity.RESULT_OK;
        AnalogUiCheck check=new AnalogUiCheck(instrumentation,holdOnly);
        FutureTask<Throwable> work=new FutureTask<>(()->{
            Throwable failure=null;
            try { check.exercise(); } catch (Throwable error) { failure=error; }
            finally {
                if (!check.cancelled) try { check.neutral(); check.closeToMain(); }
                catch (Throwable error) {
                    check.report.append("cleanup=FAIL; ").append(error).append('\n');
                    if (failure==null) failure=error; else failure.addSuppressed(error);
                }
            }
            return failure;
        });
        Thread runner=new Thread(work,"NovaAnalogUiCheck"); runner.setDaemon(true); runner.start();
        try {
            Throwable failure=work.get(TOTAL_TIMEOUT_MS,TimeUnit.MILLISECONDS);
            if (failure!=null) {
                code=Activity.RESULT_CANCELED;
                check.report.append("AnalogUi=FAIL; phase=").append(check.phase).append("; ").append(failure).append('\n');
            }
        }
        catch (TimeoutException error) {
            code=Activity.RESULT_CANCELED; check.timeoutPhase=check.phase; check.cancelled=true; work.cancel(true);
            check.report.append("AnalogUi=TIMEOUT; totalLimitMs=").append(TOTAL_TIMEOUT_MS).append("; phase=").append(check.timeoutPhase)
                .append("; no further UI input attempted; coordinator restores external state\n");
        }
        catch (Throwable error) {
            code=Activity.RESULT_CANCELED; check.cancelled=true; work.cancel(true);
            check.report.append("AnalogUi=FAIL; phase=").append(check.phase).append("; ").append(error).append('\n');
        } finally {
            result.putString("stream",check.report.toString());
            instrumentation.finish(code,result);
        }
    }

    private void exercise() throws Exception {
        mark("launch.startActivitySync");
        Intent intent=new Intent(instrumentation.getTargetContext(),MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        FutureTask<Activity> launch=new FutureTask<>(()->instrumentation.startActivitySync(intent));
        Thread launchThread=new Thread(launch,"NovaAnalogActivityLaunch"); launchThread.setDaemon(true); launchThread.start();
        try { activity=(MainActivity)launch.get(ACTIVITY_TIMEOUT_MS,TimeUnit.MILLISECONDS); }
        catch (TimeoutException timeout) { launch.cancel(true); throw new IllegalStateException("startActivitySync exceeded "+ACTIVITY_TIMEOUT_MS+"ms",timeout); }
        mainRoot=ui(()->activity.getWindow().getDecorView());
        mark("launch.waitMainWindow");
        waitRoot(R.id.action_log,mainRoot);
        mark("automation.connectAfterMainFocus");
        automation=instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        require(automation!=null,"UiAutomation connection returned no instance");
        pause(350);
        if (holdOnly) waitForManagedStartup();
        Map<String,Object> preferences=profilePreferences();
        report.append("input=UiAutomation; source=JOYSTICK; axes=left X/Y,right RZ; device=-1; callbacks=real windows\n");
        if (holdOnly) {
            holdFixture();
            require(preferences.equals(profilePreferences()),"Hold UI changed a saved profile or management choice");
            report.append("savedProfilesAndControlChoices=UNCHANGED\nAnalogUi=PASS\n");
            return;
        }

        mark("main.findScrollView");
        ScrollView main=ui(()->first(mainRoot,ScrollView.class));
        require(main!=null,"Main has no ScrollView");
        probe("Main",mainRoot,main,required(mainRoot,R.id.action_log),false);
        controllerNavigation(main);

        // Exercise normal focus traversal and confirm, without activating any setting.
        mark("main.dpadToSettings");
        focus(required(mainRoot,R.id.action_log));
        key(KeyEvent.KEYCODE_DPAD_RIGHT);
        if (ui(()->mainRoot.findFocus()==mainRoot.findViewById(R.id.action_log))) key(KeyEvent.KEYCODE_DPAD_RIGHT);
        require(ui(()->mainRoot.findFocus()==mainRoot.findViewById(R.id.action_settings)),"D-pad cannot reach Settings from left Log");
        mark("settings.openWithA"); key(KeyEvent.KEYCODE_BUTTON_A);
        View settingsRoot=waitRoot(R.id.toggle_automatic,null);
        mark("settings.findScrollView");
        ScrollView settings=ui(()->first(settingsRoot,ScrollView.class));
        require(settings!=null,"Settings has no ScrollView");
        probe("Settings",settingsRoot,settings,required(settingsRoot,R.id.toggle_automatic),false);
        mark("settings.closeWithB"); key(KeyEvent.KEYCODE_BUTTON_B); waitRoot(R.id.action_settings,mainRoot);
        mark("settings.openWithStart"); key(KeyEvent.KEYCODE_BUTTON_START); waitRoot(R.id.toggle_automatic,null);
        key(KeyEvent.KEYCODE_BUTTON_START); waitRoot(R.id.action_settings,mainRoot);
        report.append("Dpad/A/B/Start=PASS; opened and closed Settings without changing switches\n");

        mark("output.openEditor");
        View outputButton=required(mainRoot,R.id.handheld_output);
        focus(outputButton); key(KeyEvent.KEYCODE_BUTTON_A);
        View editorRoot=waitRoot(R.id.output_aspect,null);
        ScrollView editor=ui(()->first(editorRoot,ScrollView.class));
        require(editor!=null,"Output editor has no ScrollView");
        probe("OutputEditor",editorRoot,editor,required(editorRoot,R.id.output_aspect),false);

        mark("output.openAspectChoices"); focus(required(editorRoot,R.id.output_aspect)); key(KeyEvent.KEYCODE_BUTTON_A);
        View aspectRoot=waitListRoot(2,editorRoot);
        ListView aspects=ui(()->first(aspectRoot,ListView.class));
        probe("AspectChoices",aspectRoot,aspects,aspects,false);
        key(KeyEvent.KEYCODE_BUTTON_B); waitRoot(R.id.output_mode,editorRoot);

        mark("output.openTimingChoices");
        focus(required(editorRoot,R.id.output_mode)); key(KeyEvent.KEYCODE_BUTTON_A);
        View timingRoot=waitListRoot(7,editorRoot);
        ListView timings=ui(()->first(timingRoot,ListView.class));
        probe("TimingChoices",timingRoot,timings,timings,false);
        // A selects a local draft row only; B then discards the entire editor.
        mark("output.nativeDpadSelection");
        key(KeyEvent.KEYCODE_DPAD_UP);
        int timingSelection=ui(timings::getSelectedItemPosition);
        for (int attempt=0;attempt<8 && timingSelection==ListView.INVALID_POSITION;attempt++) {
            key(KeyEvent.KEYCODE_DPAD_UP);
            timingSelection=ui(timings::getSelectedItemPosition);
        }
        for (int attempt=0;attempt<8 && timingSelection==ListView.INVALID_POSITION;attempt++) {
            key(KeyEvent.KEYCODE_DPAD_DOWN);
            timingSelection=ui(timings::getSelectedItemPosition);
        }
        require(timingSelection!=ListView.INVALID_POSITION,"Native D-pad did not create a timing selection after touch-mode scrolling");
        require(ui(()->timingRoot.findFocus()==timings),"Native timing list lost focus before confirmation");
        report.append("TimingDpad=PASS; native selected position=").append(timingSelection).append('\n');
        mark("output.selectAndDiscardDraft");
        // Keep the native D-pad selection: requestFocus/setFocusableInTouchMode here
        // could restore touch mode's mResurrectToPosition instead of a selected row.
        key(KeyEvent.KEYCODE_BUTTON_A); waitRoot(R.id.output_mode,editorRoot);
        key(KeyEvent.KEYCODE_BUTTON_B); waitRoot(R.id.handheld_output,mainRoot);
        report.append("Output Dpad/A/B=PASS; seven native choices; selected draft discarded without Apply/Save\n");

        mark("history.openWithSelect");
        key(KeyEvent.KEYCODE_BUTTON_SELECT);
        View historyRoot=waitRoot(R.id.journal_list,null);
        journalMetadata(historyRoot);
        ListView history=(ListView)required(historyRoot,R.id.journal_list);
        int records=ui(history::getCount);
        require(records>0,"History is empty; natural existing records are required (no synthetic logs are inserted)");
        probe("History",historyRoot,history,history,true);
        speed(historyRoot,history);
        mark("history.dpadAndClose");
        if (records>1) {
            reset(history,history);
            int selected=ui(history::getSelectedItemPosition);
            key(KeyEvent.KEYCODE_DPAD_DOWN);
            require(ui(history::getSelectedItemPosition)!=selected,"History D-pad selection stopped working after analog scroll");
            report.append("HistoryDpad=PASS; selection changed only after a D-pad key\n");
        } else report.append("HistoryDpad=NOT_TESTED_CAPACITY; only one record exists\n");
        key(KeyEvent.KEYCODE_BUTTON_B); waitRoot(R.id.action_log,mainRoot);
        report.append("Select/B=PASS; journal records=").append(records).append('\n');
        headerActions();
        holdFixture();
        require(preferences.equals(profilePreferences()),"UI input changed a saved profile or management choice");
        report.append("savedProfilesAndControlChoices=UNCHANGED\nAnalogUi=PASS\n");
    }

    /** Instrumentation restarts the service; its normal startup log must precede fixture baselines. */
    private void waitForManagedStartup() throws Exception {
        mark("holdFixture.waitForManagedStartup");
        long until=SystemClock.uptimeMillis()+12_000L;
        while (SystemClock.uptimeMillis()<until) {
            if (!Store.managed(activity)) return;
            boolean appliedAfterStart=false;
            for (String record:Store.history(activity).records()) {
                String code=LogEvent.parse(record).code;
                if ("mode_applied".equals(code)) { appliedAfterStart=true; break; }
                if ("session_started".equals(code)) break;
            }
            if (appliedAfterStart && DockService.snapshot()!=null && "applied".equals(Store.statusCode(activity))) {
                report.append("ManagedStartup=SETTLED; profile application precedes counter-only baselines\n");
                return;
            }
            pause(100);
        }
        throw new IllegalStateException("Managed startup did not settle before the counter-only fixture");
    }

    /** Real danger controls receive only short input; complete holds use a counter-only fixture. */
    private void headerActions() throws Exception {
        mark("headers.settingsAndLanguage");
        Map<String,Object> profiles=profilePreferences();
        Map<String,String> nativeBefore=headerNativeState();
        String language=ui(()->AppText.language(activity));
        String history=Store.prefs(activity).getString("history","");

        View settings=openSettings();
        focus(required(settings,R.id.toggle_automatic));
        Map<Integer,Boolean> switches=checkedSwitches(settings);
        assertSwitches(settings,switches,"before Select focuses Restore");
        selectDangerFocus(settings,R.id.action_restore_settings,"Restore");
        assertSwitches(settings,switches,"after Select focuses Restore");
        focus(required(settings,R.id.action_close_dialog));
        for (int code:new int[]{KeyEvent.KEYCODE_BUTTON_START,KeyEvent.KEYCODE_BUTTON_SELECT,KeyEvent.KEYCODE_BUTTON_A,KeyEvent.KEYCODE_BUTTON_B}) {
            assertSwitches(settings,switches,"before canceled/orphan key "+code);
            canceledAndOrphan(settings,code,"Settings header gate");
            assertSwitches(settings,switches,"after canceled/orphan key "+code);
        }
        assertSwitches(settings,switches,"before long Start close");
        mark("headers.settings.longStartClose"); longKey(KeyEvent.KEYCODE_BUTTON_START);
        mark("headers.settings.waitMainAfterLongStart"); waitRoot(R.id.action_settings,mainRoot);
        assertSwitches(settings,switches,"after long Start close");
        mark("headers.settings.orphanStart"); orphan(KeyEvent.KEYCODE_BUTTON_START); sameRoot(mainRoot,"Repeated Settings Start propagated to Main");
        assertSwitches(settings,switches,"after orphan Start");
        mark("headers.settings.touchOpen"); tap(required(mainRoot,R.id.action_settings)); settings=waitRoot(R.id.toggle_automatic,null);
        assertSwitches(settings,switches,"after Settings touch open");
        mark("headers.settings.touchClose"); tap(required(settings,R.id.action_close_dialog));
        mark("headers.settings.waitMainAfterTouchClose"); waitRoot(R.id.action_settings,mainRoot);
        assertSwitches(settings,switches,"after Settings touch close");

        settings=openSettings();
        View languageRoot=openList(settings,R.id.language,3);
        bottomClose(languageRoot,"Language");
        selectNoOp(languageRoot,"Language");
        require(language.equals(ui(()->AppText.language(activity))),"Select changed the app language");
        mark("headers.language.longStartClose"); longKey(KeyEvent.KEYCODE_BUTTON_START);
        mark("headers.language.waitSettingsAfterLongStart"); waitRoot(R.id.language,settings);
        languageRoot=openList(settings,R.id.language,3);
        mark("headers.language.touchClose"); tap(required(languageRoot,R.id.action_close_dialog));
        mark("headers.language.waitSettingsAfterTouchClose"); waitRoot(R.id.language,settings);

        mark("headers.restoreShortInput");
        safeDanger(settings,R.id.action_restore_settings,"Restore");
        headerHoldHint(settings,R.id.action_restore_settings,"Restore");
        topClose(settings,"Settings");
        key(KeyEvent.KEYCODE_BUTTON_START); waitRoot(R.id.action_settings,mainRoot);

        mark("headers.outputDrafts");
        View editor=openEditor();
        bottomClose(editor,"Output editor");
        selectNoOp(editor,"Output editor");
        View aspect=openList(editor,R.id.output_aspect,2);
        bottomClose(aspect,"Aspect picker");
        selectNoOp(aspect,"Aspect picker");
        mark("headers.aspect.longStartClose"); longKey(KeyEvent.KEYCODE_BUTTON_START);
        mark("headers.aspect.waitEditorAfterLongStart"); waitRoot(R.id.output_aspect,editor);
        aspect=openList(editor,R.id.output_aspect,2);
        mark("headers.aspect.touchClose"); tap(required(aspect,R.id.action_close_dialog));
        mark("headers.aspect.waitEditorAfterTouchClose"); waitRoot(R.id.output_aspect,editor);
        // Change only the local aspect draft using a native list selection.
        aspect=openList(editor,R.id.output_aspect,2);
        ListView ratios=nativeList(aspect);
        nativeSelection(ratios); key(KeyEvent.KEYCODE_DPAD_DOWN);
        key(KeyEvent.KEYCODE_BUTTON_A); waitRoot(R.id.output_mode,editor);

        View timing=openList(editor,R.id.output_mode,7);
        bottomClose(timing,"Timing picker");
        require(!visible(timing,android.R.id.button3),"Timing still has an unconverted native neutral action");
        required(timing,R.id.action_keep_output);
        canceledAndOrphan(timing,KeyEvent.KEYCODE_BUTTON_SELECT,"Timing KEEP");
        longKey(KeyEvent.KEYCODE_BUTTON_SELECT); waitRoot(R.id.output_mode,editor);
        require(text(editor,R.id.output_mode).contains(activity.getString(R.string.value_unchanged)),"Timing Select did not choose a KEEP draft");
        orphan(KeyEvent.KEYCODE_BUTTON_SELECT); sameRoot(editor,"Timing Select release affected the editor");
        timing=openList(editor,R.id.output_mode,7);
        tap(required(timing,R.id.action_keep_output)); waitRoot(R.id.output_mode,editor);
        timing=openList(editor,R.id.output_mode,7);
        mark("headers.timing.longStartClose"); longKey(KeyEvent.KEYCODE_BUTTON_START);
        mark("headers.timing.waitEditorAfterLongStart"); waitRoot(R.id.output_mode,editor);
        timing=openList(editor,R.id.output_mode,7);
        mark("headers.timing.touchClose"); tap(required(timing,R.id.action_close_dialog));
        mark("headers.timing.waitEditorAfterTouchClose"); waitRoot(R.id.output_mode,editor);
        mark("headers.editor.longStartClose"); longKey(KeyEvent.KEYCODE_BUTTON_START);
        mark("headers.editor.waitMainAfterLongStart"); waitRoot(R.id.handheld_output,mainRoot);
        editor=openEditor(); mark("headers.editor.touchClose"); tap(required(editor,R.id.action_close_dialog));
        mark("headers.editor.waitMainAfterTouchClose"); waitRoot(R.id.handheld_output,mainRoot);

        mark("headers.historyClearShortInput");
        View journal=openHistory();
        canceledAndOrphan(journal,KeyEvent.KEYCODE_BUTTON_SELECT,"History focus Clear");
        longKey(KeyEvent.KEYCODE_BUTTON_SELECT); sameRoot(journal,"Select opened a Journal child or closed Journal");
        View clearAction=required(journal,R.id.action_clear_log);
        require(ui(clearAction::isFocused),"Journal Select did not focus Hold Clear");
        require(history.equals(Store.prefs(activity).getString("history","")),"Select changed journal data");
        safeDanger(journal,R.id.action_clear_log,"Clear");
        headerHoldHint(journal,R.id.action_clear_log,"Clear");
        topClose(journal,"History");
        journalMetadata(journal);
        mark("headers.history.longStartClose"); longKey(KeyEvent.KEYCODE_BUTTON_START);
        mark("headers.history.waitMainAfterLongStart"); waitRoot(R.id.action_log,mainRoot);
        journal=openHistory(); mark("headers.history.touchClose"); tap(required(journal,R.id.action_close_dialog));
        mark("headers.history.waitMainAfterTouchClose"); waitRoot(R.id.action_log,mainRoot);

        mark("headers.crossWindowReleases");
        crossWindowReleases();
        require(history.equals(Store.prefs(activity).getString("history","")),"Header tests cleared or rewrote journal data");
        require(language.equals(ui(()->AppText.language(activity))),"Header tests changed the app language");
        require(profiles.equals(profilePreferences()),"Header tests committed a profile or management change");
        require(nativeBefore.equals(headerNativeState()),"Closing an output draft or short danger input changed native settings");
        report.append("ModalWindows=PASS; Settings/Output/Aspect/Timing/Language/History; Start and touch Close cancel only top window; Settings/History top-right, other windows bottom-center\n")
            .append("HeaderSelect=PASS; Settings/Journal focus Restore/Clear only; Timing KEEP changes only draft; empty slots no-op\n")
            .append("HeaderPressGates=PASS; repeats/cancel/orphan/cross-window releases safe; profiles/native/language/history unchanged\n");
    }

    private View openSettings() throws Exception {
        waitRoot(R.id.action_settings,mainRoot); key(KeyEvent.KEYCODE_BUTTON_START);
        return waitRoot(R.id.toggle_automatic,null);
    }
    private View openHistory() throws Exception {
        waitRoot(R.id.action_log,mainRoot); key(KeyEvent.KEYCODE_BUTTON_SELECT);
        return waitRoot(R.id.journal_list,null);
    }
    private View openEditor() throws Exception {
        waitRoot(R.id.handheld_output,mainRoot); focus(required(mainRoot,R.id.handheld_output)); key(KeyEvent.KEYCODE_BUTTON_A);
        return waitRoot(R.id.output_aspect,null);
    }
    private View openList(View parent,int action,int count) throws Exception {
        focus(required(parent,action)); key(KeyEvent.KEYCODE_BUTTON_A); return waitListRoot(count,parent);
    }
    private void selectDangerFocus(View root,int id,String label) throws Exception {
        mark("headers.SelectFocus."+label);
        Map<String,Object> preferences=profilePreferences();
        String history=Store.prefs(activity).getString("history","");
        longKey(KeyEvent.KEYCODE_BUTTON_SELECT); sameRoot(root,"Select on "+label+" changed windows");
        HoldButton danger=(HoldButton)required(root,id);
        require(ui(()->danger.isFocused() && !danger.isHolding()),"Select did not focus idle "+label);
        require(preferences.equals(profilePreferences()),"Select on "+label+" changed profile/control choices");
        require(history.equals(Store.prefs(activity).getString("history","")),"Select on "+label+" changed journal data");
    }
    private void headerHoldHint(View root,int dangerId,String name) throws Exception {
        mark("header."+name+".holdHintGeometry");
        HeaderBar header=ui(()->first(root,HeaderBar.class));
        HoldButton danger=(HoldButton)required(root,dangerId);
        TextView hint=(TextView)required(root,R.id.hold_hint);
        View close=required(root,R.id.action_close_dialog);
        require(header!=null && ui(()->header.left==danger),name+": danger action is not header.left");
        require(ui(()->danger.getText().toString().indexOf('\n')<0),name+": hold hint is still inside the button");
        focus(close);
        require(ui(()->hint.getVisibility()==View.INVISIBLE),name+": hint must reserve space while unfocused");
        Map<String,Rect> geometry=ui(()->headerHintBounds(root,header,hint));
        require(geometry.get("hint").height()>0,name+": hold hint has no permanently reserved height");
        focus(danger);
        require(ui(()->hint.getVisibility()==View.VISIBLE),name+": focused danger action did not show hint");
        require(geometry.equals(ui(()->headerHintBounds(root,header,hint))),name+": focusing danger action shifted layout");
        ui(()->{
            Rect button=boundsOnScreen(danger),hintBox=boundsOnScreen(hint),headerBox=boundsOnScreen(header);
            require(hintBox.top>=button.bottom,name+": external hold hint overlaps its button");
            require(headerBox.contains(hintBox),name+": hold hint is outside the reserved header space");
            require(hint.getText().toString().equals(activity.getString(R.string.hint_hold)),name+": hold hint is not localized");
            android.graphics.drawable.Drawable icon=hint.getCompoundDrawables()[2];
            int size=NativeDialog.dp(activity,14);
            require(icon!=null && icon.getBounds().width()==size && icon.getBounds().height()==size && hint.getCompoundDrawablePadding()==NativeDialog.dp(activity,4),name+": hold hint A icon dimensions changed");
            return null;
        });
        // Show a harmless child while the parent's left View keeps focus. Only
        // window focus changes; no A/touch hold is sent to the real danger action.
        NativeDialog child=ui(()->{
            NativeDialog panel=new NativeDialog(activity,R.string.app_name,danger);
            panel.setTitle("Hint window focus check"); panel.show(false,null); return panel;
        });
        try {
            View expectedChild=ui(()->child.dialog.getWindow().getDecorView());
            View childRoot=waitRoot(R.id.action_close_dialog,expectedChild);
            require(childRoot!=root,name+": hint focus fixture did not open a child");
            require(ui(()->danger.isFocused() && hint.getVisibility()==View.INVISIBLE),name+": window focus loss did not hide hint while left View retained focus");
            require(geometry.equals(ui(()->headerHintBounds(root,header,hint))),name+": window focus loss shifted header/content");
            key(KeyEvent.KEYCODE_BUTTON_B); sameRoot(root,name+": hint focus child did not close to parent");
        } finally { ui(()->{ if (child.dialog.isShowing()) child.dialog.dismiss(); return null; }); }
        require(ui(()->danger.isFocused() && hint.getVisibility()==View.VISIBLE),name+": parent window focus did not restore hint");
        require(geometry.equals(ui(()->headerHintBounds(root,header,hint))),name+": restoring parent window focus shifted layout");
        focus(close);
        require(ui(()->hint.getVisibility()==View.INVISIBLE),name+": moving focus away did not hide hint");
        require(geometry.equals(ui(()->headerHintBounds(root,header,hint))),name+": hiding hint shifted layout");
        report.append("HeaderHoldHint=PASS; action=").append(name).append("; single-line header-left danger; focus/window visibility; permanently reserved bounds unchanged\n");
    }
    private static Map<String,Rect> headerHintBounds(View root,HeaderBar header,TextView hint) {
        Map<String,Rect> result=new HashMap<>();
        result.put("header",boundsOnScreen(header)); result.put("left",boundsOnScreen(header.left));
        result.put("right",boundsOnScreen(header.right)); result.put("title",boundsOnScreen(header.titleView));
        result.put("hint",boundsOnScreen(hint));
        View content=visibleContent(root,ListView.class,header.right);
        if (content==null) content=visibleContent(root,ScrollView.class,header.right);
        if (content!=null) result.put("content",boundsOnScreen(content));
        for (int id:new int[]{R.id.journal_order,R.id.journal_summary}) {
            View metadata=root.findViewById(id); if (metadata!=null) result.put("metadata"+id,boundsOnScreen(metadata));
        }
        return result;
    }
    private static Rect boundsOnScreen(View view) {
        int[] location=new int[2]; view.getLocationOnScreen(location);
        return new Rect(location[0],location[1],location[0]+view.getWidth(),location[1]+view.getHeight());
    }
    private void topClose(View root,String name) throws Exception {
        mark("header."+name+".geometry");
        ui(()->{
            HeaderBar header=first(root,HeaderBar.class);
            View close=root.findViewById(R.id.action_close_dialog);
            require(header!=null && header.right==close && close.isShown(),name+": Close is not the header right action");
            TouchTarget target=new TouchTarget(close);
            int[] headerOrigin=new int[2]; header.getLocationOnScreen(headerOrigin);
            int[] origin=new int[2]; root.getLocationOnScreen(origin);
            require(Math.abs(target.screen.right-(headerOrigin[0]+header.getWidth()))<=1,name+": header Close is not at the right edge");
            require(target.screen.exactCenterY()<origin[1]+root.getHeight()/2f,name+": Close is not in the top half");
            require(target.screen.height()>=NativeDialog.dp(activity,44),name+": compact Close is clipped or too short");
            View content=visibleContent(root,ListView.class,close);
            if (content==null) content=visibleContent(root,ScrollView.class,close);
            if (content!=null) {
                int[] location=new int[2]; content.getLocationOnScreen(location);
                require(target.screen.bottom<=location[1]+NativeDialog.dp(activity,2),name+": header Close overlaps scrolling content");
            }
            return null;
        });
        report.append("TopClose=PASS; window=").append(name).append("; header-right/full target/above scrolling content; pinning covered by analog probe\n");
    }
    private void journalMetadata(View root) throws Exception {
        mark("history.metadataGeometry");
        ui(()->{
            TextView order=(TextView)root.findViewById(R.id.journal_order);
            TextView summary=(TextView)root.findViewById(R.id.journal_summary);
            require(order!=null && summary!=null && order.getParent()==summary.getParent(),"Journal metadata is not in one row");
            Rect left=new TouchTarget(order).screen,right=new TouchTarget(summary).screen;
            require(left.right<=right.left+NativeDialog.dp(activity,2),"Journal order and statistics overlap: "+left+" / "+right);
            require(Math.min(left.bottom,right.bottom)-Math.max(left.top,right.top)>0,"Journal order and statistics are on different rows");
            require((summary.getGravity()&android.view.Gravity.HORIZONTAL_GRAVITY_MASK)==android.view.Gravity.RIGHT,"Journal statistics are not right aligned");
            for (TextView text:new TextView[]{order,summary}) {
                android.text.Layout layout=text.getLayout();
                require(layout!=null && layout.getLineCount()==1 && layout.getEllipsisCount(0)==0 && layout.getLineEnd(0)==text.length(),"Journal metadata is wrapped or truncated: "+text.getText());
            }
            return null;
        });
        report.append("JournalMetadata=PASS; newest-first and right-aligned statistics share one row without overlap\n");
    }
    private void bottomClose(View root,String name) throws Exception {
        mark("footer."+name+".geometry");
        ui(()->{
            View close=root.findViewById(R.id.action_close_dialog);
            require(close!=null && close.isShown(),name+": bottom Close missing");
            TouchTarget target=new TouchTarget(close);
            int[] origin=new int[2]; root.getLocationOnScreen(origin);
            float center=origin[0]+root.getWidth()/2f;
            require(Math.abs(target.screen.exactCenterX()-center)<=NativeDialog.dp(activity,4),name+": Close is not horizontally centered: "+target.screen);
            require(target.screen.exactCenterY()>origin[1]+root.getHeight()/2f,name+": Close is not in the bottom half");
            require(target.screen.height()>=NativeDialog.dp(activity,44),name+": bottom Close is clipped or too short");
            // On this firmware android:id/buttonPanel is itself a ScrollView.
            // A footer containing Close is not the content viewport being measured.
            View content=visibleContent(root,ListView.class,close);
            if (content==null) content=visibleContent(root,ScrollView.class,close);
            if (content!=null) {
                int[] location=new int[2]; content.getLocationOnScreen(location);
                Rect bounds=new Rect(location[0],location[1],location[0]+content.getWidth(),location[1]+content.getHeight());
                String detail="window="+name+"; content="+content.getClass().getSimpleName()+"#"+content.getId()+"; contentScreen="+bounds+"; closeScreen="+target.screen;
                report.append("BottomCloseGeometry; ").append(detail).append('\n');
                require(bounds.bottom<=target.screen.top+NativeDialog.dp(activity,2),name+": Close overlaps scrolling content; "+detail);
            }
            return null;
        });
        report.append("BottomClose=PASS; window=").append(name).append("; centered/full target/below scrolling content\n");
    }
    private static <T extends View> T visibleContent(View root,Class<T> type,View close) {
        Rect visible=new Rect();
        if (type.isInstance(root) && root.isShown() && root.getWidth()>0 && root.getHeight()>0
            && root.getGlobalVisibleRect(visible) && !visible.isEmpty() && !containsView(root,close)) return type.cast(root);
        if (root instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)root;
            for (int i=0;i<group.getChildCount();i++) {
                T found=visibleContent(group.getChildAt(i),type,close); if (found!=null) return found;
            }
        }
        return null;
    }
    private static boolean containsView(View ancestor,View descendant) {
        for (View view=descendant;view!=null;view=view.getParent() instanceof View?(View)view.getParent():null)
            if (view==ancestor) return true;
        return false;
    }
    private void safeDanger(View root,int id,String name) throws Exception {
        HoldButton danger=(HoldButton)required(root,id);
        require(ui(danger::isHoldRequired),name+": action is not a hold control");
        Map<String,Object> preferences=profilePreferences();
        Map<String,String> nativeBefore=headerNativeState();
        String history=Store.prefs(activity).getString("history","");
        String language=ui(()->AppText.language(activity));
        focus(danger);
        mark("danger."+name+".shortA"); key(KeyEvent.KEYCODE_BUTTON_A);
        unchangedDanger(root,danger,name+" short A",preferences,nativeBefore,history,language);
        mark("danger."+name+".shortTouch"); tap(danger,true);
        unchangedDanger(root,danger,name+" short touch",preferences,nativeBefore,history,language);
        focus(danger);
        mark("danger."+name+".repeatedShortA"); repeatedShortA();
        unchangedDanger(root,danger,name+" repeated short A",preferences,nativeBefore,history,language);
        mark("danger."+name+".cancelOrphanA"); canceledAndOrphan(root,KeyEvent.KEYCODE_BUTTON_A,name+" hold gate");
        unchangedDanger(root,danger,name+" canceled/orphan A",preferences,nativeBefore,history,language);
        report.append("DangerShortInput=PASS; action=").append(name).append("; short A/touch/repeat/canceled/orphan; no confirmation or mutation\n");
    }
    private void repeatedShortA() throws Exception {
        long down=SystemClock.uptimeMillis();
        require(rawKey(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_A,0,0,down),"Short A DOWN rejected");
        try {
            // Never wait for a window/focus condition with a real danger press held.
            for (int repeat=1;repeat<=2;repeat++) {
                pause(20); require(rawKey(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_A,repeat,KeyEvent.FLAG_LONG_PRESS,down),"Short A repeat rejected");
            }
        } finally {
            require(rawKey(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_A,0,0,down),"Short A UP rejected");
        }
        pause(90);
    }
    private void unchangedDanger(View root,HoldButton danger,String step,Map<String,Object> preferences,Map<String,String> nativeBefore,String history,String language) throws Exception {
        sameRoot(root,step+": short input opened a child or closed its window");
        require(ui(()->!danger.isHolding() && danger.holdProgress()==0),step+": release left hold active");
        require(preferences.equals(profilePreferences()),step+": profile or control choice changed");
        require(nativeBefore.equals(headerNativeState()),step+": native settings changed");
        require(history.equals(Store.prefs(activity).getString("history","")),step+": history changed");
        require(language.equals(ui(()->AppText.language(activity))),step+": language changed");
    }
    private void sameRoot(View expected,String message) throws Exception {
        long until=SystemClock.uptimeMillis()+1500L;
        while (ui(this::activeRoot)!=expected && SystemClock.uptimeMillis()<until) pause(30);
        require(ui(this::activeRoot)==expected,message+"; "+ui(this::activeDescription));
    }
    private void selectNoOp(View root,String label) throws Exception {
        mark("headers.selectNoOp."+label);
        longKey(KeyEvent.KEYCODE_BUTTON_SELECT); sameRoot(root,label+": empty Select slot performed an action");
    }
    private Map<Integer,Boolean> checkedSwitches(View root) throws Exception {
        return ui(()->{
            Map<Integer,Boolean> values=new HashMap<>();
            for (int id:new int[]{R.id.toggle_automatic,R.id.toggle_start_on_boot,R.id.toggle_screen_off,R.id.toggle_show_refresh}) values.put(id,((Switch)root.findViewById(id)).isChecked());
            return values;
        });
    }
    private void assertSwitches(View root,Map<Integer,Boolean> expected,String step) throws Exception {
        Map<Integer,Boolean> actual=checkedSwitches(root);
        require(expected.equals(actual),"Settings switch changed "+step+"; expected="+expected+"; actual="+actual);
        report.append("SettingsSwitches=UNCHANGED; step=").append(step).append('\n');
    }
    private boolean visible(View root,int id) throws Exception { return ui(()->root.findViewById(id)!=null && root.findViewById(id).getVisibility()==View.VISIBLE); }
    private String text(View root,int id) throws Exception { return ui(()->((TextView)root.findViewById(id)).getText().toString()); }
    private ListView nativeList(View root) throws Exception { return ui(()->first(root,ListView.class)); }
    private void nativeSelection(ListView list) throws Exception {
        focus(list); key(KeyEvent.KEYCODE_DPAD_UP);
        int selected=ui(list::getSelectedItemPosition);
        for (int i=0;i<8 && selected==ListView.INVALID_POSITION;i++) { key(KeyEvent.KEYCODE_DPAD_DOWN); selected=ui(list::getSelectedItemPosition); }
        require(selected!=ListView.INVALID_POSITION,"Native list has no D-pad selection");
    }
    private Map<String,String> headerNativeState() {
        synchronized (DockService.hardware) {
            Retroid retroid=new Retroid(activity); Map<String,String> result=retroid.snapshot();
            GlobalSettings.Snapshot global=new GlobalSettings(activity,retroid.bridge).read();
            result.put("window_size",global.window.width+"x"+global.window.height);
            result.put("window_override",Boolean.toString(global.window.overridden));
            result.put("screen_off",global.screenRaw); result.put("refresh_overlay",Boolean.toString(global.showRefresh));
            for (Map.Entry<VendorBridge.DpProperty,String> property:global.properties.entrySet()) result.put(property.getKey().name(),property.getValue());
            return result;
        }
    }
    private void crossWindowReleases() throws Exception {
        // Main's held Start is released in its new child: the child must not close.
        focus(required(mainRoot,R.id.action_settings));
        long down=SystemClock.uptimeMillis(); require(rawKey(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_START,0,0,down),"Start DOWN rejected");
        tap(required(mainRoot,R.id.action_settings)); View settings=waitRoot(R.id.toggle_automatic,null);
        rawKey(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_START,0,0,down); pause(90);
        sameRoot(settings,"Old Main Start release closed new Settings");

        // Hold A on safe Close, open a non-destructive language child by touch,
        // then release the old press there. No danger action is touched.
        View language=required(settings,R.id.language);
        focus(language); focus(required(settings,R.id.action_close_dialog));
        mark("headers.crossLanguage.holdADown");
        down=SystemClock.uptimeMillis(); require(rawKey(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_A,0,0,down),"A DOWN rejected");
        tap(language,true); View child=waitListRoot(3,settings);
        rawKey(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_A,0,0,down); pause(90);
        sameRoot(child,"Old Settings A release activated the language picker");

        // The child's held B must not close its parent after a touch close.
        down=SystemClock.uptimeMillis(); require(rawKey(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_B,0,0,down),"B DOWN rejected");
        tap(required(child,R.id.action_close_dialog)); sameRoot(settings,"Language close touch did not return to Settings");
        rawKey(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_B,0,0,down); pause(90);
        sameRoot(settings,"Old language B release closed parent Settings");
        key(KeyEvent.KEYCODE_BUTTON_START); waitRoot(R.id.action_log,mainRoot);

        focus(required(mainRoot,R.id.action_log));
        down=SystemClock.uptimeMillis(); require(rawKey(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_SELECT,0,0,down),"Select DOWN rejected");
        tap(required(mainRoot,R.id.action_log)); View history=waitRoot(R.id.journal_list,null);
        View initial=ui(history::findFocus);
        rawKey(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_SELECT,0,0,down); pause(90);
        sameRoot(history,"Old Main Select release opened a History action");
        require(ui(()->history.findFocus()==initial),"Old Main Select release focused Hold Clear");
        key(KeyEvent.KEYCODE_BUTTON_START); waitRoot(R.id.action_log,mainRoot);
    }

    /** Every action is expected on UP; repeat DOWN events must leave the same window. */
    private void longKey(int code) throws Exception {
        View before=ui(this::activeRoot); require(before!=null,"No own window for long key");
        long down=SystemClock.uptimeMillis();
        require(rawKey(KeyEvent.ACTION_DOWN,code,0,0,down),"Long key DOWN rejected"); pause(20);
        sameRoot(before,"Header action fired on DOWN");
        for (int repeat=1;repeat<=2;repeat++) {
            rawKey(KeyEvent.ACTION_DOWN,code,repeat,KeyEvent.FLAG_LONG_PRESS,down); pause(20);
            sameRoot(before,"Header action fired on a repeat DOWN");
        }
        require(rawKey(KeyEvent.ACTION_UP,code,0,0,down),"Long key UP rejected"); pause(90);
    }
    private void canceledAndOrphan(View root,int code,String label) throws Exception {
        phase="headers.canceledAndOrphan.key"+code;
        label=label+" key="+code;
        long down=SystemClock.uptimeMillis();
        require(rawKey(KeyEvent.ACTION_DOWN,code,0,0,down),label+": DOWN rejected");
        require(rawKey(KeyEvent.ACTION_UP,code,0,KeyEvent.FLAG_CANCELED,down),label+": canceled UP rejected"); pause(90);
        sameRoot(root,label+": canceled release performed an action");
        orphan(code); sameRoot(root,label+": orphan release performed an action");
    }
    private void orphan(int code) throws Exception {
        boolean accepted=rawKey(KeyEvent.ACTION_UP,code,0,0,SystemClock.uptimeMillis()); pause(90);
        if (!accepted) report.append("orphanUP=PLATFORM_REJECTED; key=").append(code).append('\n');
    }
    private boolean rawKey(int action,int code,int repeat,int flags,long down) throws Exception {
        running(); require(automation!=null && ui(this::activeRoot)!=null,"No focused own window for raw key");
        return automation.injectInputEvent(new KeyEvent(down,SystemClock.uptimeMillis(),action,code,repeat,0,-1,0,flags,InputDevice.SOURCE_GAMEPAD),true);
    }
    private void traceTarget(String step,View target) throws Exception {
        mark(step);
        String detail=ui(()->{
            View focus=target.getRootView().findFocus();
            return "touchState phase="+step+"; target="+target.getClass().getSimpleName()+"#"+target.getId()
                +"; text="+(target instanceof TextView?((TextView)target).getText().toString().replace('\n','/') : "")
                +"; fTouch="+target.isFocusableInTouchMode()+"; focused="+target.isFocused()+"; inTouchMode="+target.isInTouchMode()
                +"; pressed="+target.isPressed()+"; rootFocus="+(focus==null?"none":focus.getClass().getSimpleName()+"#"+focus.getId())
                +"; "+activeDescription();
        });
        report.append(detail).append('\n'); Bundle progress=new Bundle(); progress.putString("stream",detail+"\n"); instrumentation.sendStatus(0,progress);
    }
    private void tap(View target) throws Exception { tap(target,false); }
    private void tap(View target,boolean forceTrace) throws Exception {
        TouchTarget touch=ui(()->new TouchTarget(target));
        Rect bounds=touch.screen;
        boolean dialogOffset=touch.rootX!=0 || touch.rootY!=0;
        if (forceTrace || (dialogOffset?!loggedDialogTouch:!loggedMainTouch)) {
            String detail="touch phase="+phase+"; rootOffset="+touch.rootX+","+touch.rootY+"; viewScreen="+touch.viewX+","+touch.viewY
                +"; rootVisible="+touch.rootVisible+"; derivedOffset="+touch.offsetX+","+touch.offsetY+"; targetScreen="+bounds;
            report.append(detail).append('\n'); Bundle progress=new Bundle(); progress.putString("stream",detail+"\n"); instrumentation.sendStatus(0,progress);
            if (dialogOffset) loggedDialogTouch=true; else loggedMainTouch=true;
        }
        long down=SystemClock.uptimeMillis();
        MotionEvent.PointerProperties pointer=new MotionEvent.PointerProperties(); pointer.id=0; pointer.toolType=MotionEvent.TOOL_TYPE_FINGER;
        for (int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}) {
            MotionEvent.PointerCoords point=new MotionEvent.PointerCoords(); point.x=bounds.exactCenterX(); point.y=bounds.exactCenterY(); point.pressure=action==MotionEvent.ACTION_DOWN?1:0; point.size=1;
            MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,1,new MotionEvent.PointerProperties[]{pointer},new MotionEvent.PointerCoords[]{point},0,0,1,1,-1,0,InputDevice.SOURCE_TOUCHSCREEN,0);
            try { require(automation.injectInputEvent(event,true),"Header touch injection rejected"); } finally { event.recycle(); }
        }
        pause(90);
    }
    private static final class TouchTarget {
        final Rect rootVisible=new Rect(),screen;
        final int rootX,rootY,viewX,viewY,offsetX,offsetY;
        TouchTarget(View target) {
            Point globalOffset=new Point();
            require(target.isShown() && target.isEnabled() && target.getGlobalVisibleRect(rootVisible,globalOffset),"Touch target is not visible/enabled");
            int[] location=new int[2]; target.getLocationOnScreen(location); viewX=location[0]; viewY=location[1];
            target.getRootView().getLocationOnScreen(location); rootX=location[0]; rootY=location[1];
            // getGlobalVisibleRect is relative to the root, whereas injected touch
            // coordinates are on the physical screen. Keep clipping while converting.
            offsetX=viewX-globalOffset.x; offsetY=viewY-globalOffset.y;
            Rect visibleScreen=new Rect(rootVisible); visibleScreen.offset(offsetX,offsetY);
            screen=new Rect(viewX,viewY,viewX+target.getWidth(),viewY+target.getHeight());
            require(screen.intersect(visibleScreen) && !screen.isEmpty(),"Touch target has no visible screen area");
        }
    }

    /** Own disposable window: its only complete-hold effect is an in-memory counter. */
    private final class HoldFixture {
        final NativeDialog panel;
        final HoldButton hold;
        final Button other;
        final ScrollView scroll;
        final int id;
        int fires;
        HoldFixture(boolean scrollable) {
            panel=new NativeDialog(activity,R.string.app_name,null);
            panel.setTitle("Hold input check · local counter only");
            hold=new HoldButton(activity); id=View.generateViewId(); hold.setId(id);
            hold.setText("Hold · local counter"); hold.setAllCaps(false);
            GamepadControls.style(hold,0xff283a3d,NativeDialog.ACCENT,14);
            hold.setHoldAction(()->fires++);
            panel.root.addView(hold,new LinearLayout.LayoutParams(-1,NativeDialog.dp(activity,64)));
            other=new Button(activity); other.setId(View.generateViewId()); other.setText("Focus target · no action");
            GamepadControls.style(other,0xff283a3d,NativeDialog.ACCENT,14);
            panel.root.addView(other,new LinearLayout.LayoutParams(-1,NativeDialog.dp(activity,48)));
            hold.setNextFocusDownId(other.getId()); other.setNextFocusUpId(id);
            scroll=scrollable?new ScrollView(activity):null;
            if (scroll!=null) {
                TextView text=new TextView(activity); StringBuilder content=new StringBuilder();
                for (int i=0;i<100;i++) content.append("Scroll fixture ").append(i).append('\n');
                text.setText(content); text.setTextSize(16); scroll.addView(text);
                panel.root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
            }
            panel.show(scrollable,hold); // NativeDialog attaches the real window key router.
        }
    }

    private void holdFixture() throws Exception {
        mark("holdFixture.create");
        Map<String,Object> preferences=profilePreferences();
        Map<String,String> nativeBefore=headerNativeState();
        String history=Store.prefs(activity).getString("history","");
        HoldFixture fixture=ui(()->new HoldFixture(true));
        HoldFixture[] child={null};
        try {
            View root=waitRoot(fixture.id,null);
            bottomClose(root,"Hold fixture");
            require(ui(()->fixture.scroll.canScrollVertically(1)),"Hold fixture needs real overflow for RZ cancellation");

            mark("holdFixture.A.completeOnceWhileHeld");
            focus(fixture.hold);
            long down=SystemClock.uptimeMillis();
            fixtureADown=down; require(rawKey(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_A,0,0,down),"Fixture A DOWN rejected");
            pause(400); intermediateHold(fixture,"A progress");
            require(rawKey(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_A,1,KeyEvent.FLAG_LONG_PRESS,down),"Fixture A repeat rejected");
            pause(1200); waitFires(fixture,1,"A threshold");
            require(ui(()->fixture.hold.isHolding() && fixture.hold.holdProgress()==1f),"A completion did not remain visibly full while held");
            pause(450); expectFires(fixture,1,"A continued hold repeated");
            releaseA(down,0); idleHold(fixture,1,"A release");
            mark("holdFixture.A.freshSecondPress");
            down=SystemClock.uptimeMillis(); fixtureADown=down; require(rawKey(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_A,0,0,down),"Fixture A DOWN rejected");
            pause(1600); waitFires(fixture,2,"Fresh A"); releaseA(down,0); idleHold(fixture,2,"Fresh A release");

            mark("holdFixture.A.earlyReleaseAndCancel");
            down=SystemClock.uptimeMillis(); fixtureADown=down; require(rawKey(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_A,0,0,down),"Fixture A DOWN rejected");
            pause(250); releaseA(down,0); idleHold(fixture,2,"Early A release");
            down=SystemClock.uptimeMillis(); fixtureADown=down; require(rawKey(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_A,0,0,down),"Fixture A DOWN rejected");
            pause(200); releaseA(down,KeyEvent.FLAG_CANCELED); idleHold(fixture,2,"Canceled A");
            orphan(KeyEvent.KEYCODE_BUTTON_A); idleHold(fixture,2,"Orphan A");

            mark("holdFixture.A.focusLoss");
            focus(fixture.hold);
            down=SystemClock.uptimeMillis(); fixtureADown=down; require(rawKey(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_A,0,0,down),"Fixture A DOWN rejected");
            pause(250); key(KeyEvent.KEYCODE_DPAD_DOWN);
            require(ui(()->!fixture.hold.isFocused()),"D-pad did not remove fixture hold focus");
            idleHold(fixture,2,"A focus loss"); releaseA(down,0);
            sameRoot(root,"Old A release activated the newly focused button");

            mark("holdFixture.A.rightStickCancels");
            focus(fixture.hold);
            int scrollBefore=ui(fixture.scroll::getScrollY);
            down=SystemClock.uptimeMillis(); fixtureADown=down; require(rawKey(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_A,0,0,down),"Fixture A DOWN rejected");
            pause(250); axis(1f); pause(150); neutral();
            require(ui(()->fixture.scroll.getScrollY()>scrollBefore),"Fixture RZ pulse did not really scroll");
            require(ui(fixture.hold::isShown),"RZ test hid hold control instead of retaining it");
            pause(1350); idleHold(fixture,2,"A after right-stick scroll and original threshold");
            rawKey(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_A,1,KeyEvent.FLAG_LONG_PRESS,down);
            pause(120); idleHold(fixture,2,"A repeat after RZ must not rearm"); releaseA(down,0);

            mark("holdFixture.A.freshAfterRZ");
            down=SystemClock.uptimeMillis(); fixtureADown=down; require(rawKey(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_A,0,0,down),"Fresh A after RZ rejected");
            pause(1600); waitFires(fixture,3,"Fresh A after RZ"); releaseA(down,0); idleHold(fixture,3,"Fresh A after RZ release");

            mark("holdFixture.A.windowLossAndNestedRelease");
            focus(fixture.hold);
            down=SystemClock.uptimeMillis(); fixtureADown=down; require(rawKey(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_A,0,0,down),"Fixture A DOWN rejected");
            pause(250); child[0]=ui(()->new HoldFixture(false));
            View childRoot=waitRoot(child[0].id,null);
            idleHold(fixture,3,"Parent window lost focus");
            releaseA(down,0); sameRoot(childRoot,"Old parent A release closed child");
            pause(1550); idleHold(child[0],0,"Late A release armed child hold"); expectFires(fixture,3,"Window loss fired parent");
            key(KeyEvent.KEYCODE_BUTTON_START); waitRoot(fixture.id,root); child[0]=null;

            mark("holdFixture.touch.completeOnceWhileHeld");
            TouchTarget target=ui(()->new TouchTarget(fixture.hold));
            down=SystemClock.uptimeMillis(); touchEvent(MotionEvent.ACTION_DOWN,target.screen.exactCenterX(),target.screen.exactCenterY(),down);
            pause(400); intermediateHold(fixture,"Touch progress");
            pause(1200); waitFires(fixture,4,"Touch threshold");
            require(ui(()->fixture.hold.isHolding() && fixture.hold.holdProgress()==1f),"Touch completion did not remain full while held");
            pause(450); expectFires(fixture,4,"Continued touch repeated");
            touchEvent(MotionEvent.ACTION_UP,target.screen.exactCenterX(),target.screen.exactCenterY(),down); pause(90);
            idleHold(fixture,4,"Touch release");
            mark("holdFixture.touch.freshSecondPress");
            down=SystemClock.uptimeMillis(); touchEvent(MotionEvent.ACTION_DOWN,target.screen.exactCenterX(),target.screen.exactCenterY(),down);
            pause(1600); waitFires(fixture,5,"Fresh touch");
            touchEvent(MotionEvent.ACTION_UP,target.screen.exactCenterX(),target.screen.exactCenterY(),down); pause(90);
            idleHold(fixture,5,"Fresh touch release");

            mark("holdFixture.touch.earlyRelease");
            down=SystemClock.uptimeMillis(); touchEvent(MotionEvent.ACTION_DOWN,target.screen.exactCenterX(),target.screen.exactCenterY(),down);
            pause(220); touchEvent(MotionEvent.ACTION_UP,target.screen.exactCenterX(),target.screen.exactCenterY(),down); pause(90);
            idleHold(fixture,5,"Early touch release");
            mark("holdFixture.touch.outsideMove");
            down=SystemClock.uptimeMillis(); touchEvent(MotionEvent.ACTION_DOWN,target.screen.exactCenterX(),target.screen.exactCenterY(),down);
            pause(220); touchEvent(MotionEvent.ACTION_MOVE,target.screen.exactCenterX(),target.screen.bottom+NativeDialog.dp(activity,8),down); pause(90);
            idleHold(fixture,5,"Touch moved outside");
            touchEvent(MotionEvent.ACTION_UP,target.screen.exactCenterX(),target.screen.bottom+NativeDialog.dp(activity,8),down);
            mark("holdFixture.touch.explicitCancel");
            down=SystemClock.uptimeMillis(); touchEvent(MotionEvent.ACTION_DOWN,target.screen.exactCenterX(),target.screen.exactCenterY(),down);
            pause(220); touchEvent(MotionEvent.ACTION_CANCEL,target.screen.exactCenterX(),target.screen.exactCenterY(),down); pause(90);
            idleHold(fixture,5,"Touch ACTION_CANCEL");
            mark("holdFixture.touch.secondPointer");
            down=SystemClock.uptimeMillis(); touchEvent(MotionEvent.ACTION_DOWN,target.screen.exactCenterX(),target.screen.exactCenterY(),down);
            pause(220); touchEvent(MotionEvent.ACTION_POINTER_DOWN|1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT,target.screen.exactCenterX(),target.screen.exactCenterY(),down); pause(90);
            idleHold(fixture,5,"Second touch pointer");
            touchEvent(MotionEvent.ACTION_POINTER_UP|1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT,target.screen.exactCenterX(),target.screen.exactCenterY(),down);
            touchEvent(MotionEvent.ACTION_UP,target.screen.exactCenterX(),target.screen.exactCenterY(),down);

            mark("holdFixture.touch.windowLossAndNestedRelease");
            down=SystemClock.uptimeMillis(); touchEvent(MotionEvent.ACTION_DOWN,target.screen.exactCenterX(),target.screen.exactCenterY(),down);
            pause(220); child[0]=ui(()->new HoldFixture(false)); childRoot=waitRoot(child[0].id,null);
            idleHold(fixture,5,"Touch parent window lost focus");
            // Release at the child's safe footer; an UP without its own DOWN must not close it.
            TouchTarget childClose=ui(()->new TouchTarget(child[0].panel.close));
            touchEvent(MotionEvent.ACTION_UP,childClose.screen.exactCenterX(),childClose.screen.exactCenterY(),down); pause(90);
            sameRoot(childRoot,"Late touch release activated child Close");
            pause(1550); idleHold(child[0],0,"Late touch release armed child"); expectFires(fixture,5,"Canceled touch fired after window loss");
            key(KeyEvent.KEYCODE_BUTTON_B); waitRoot(fixture.id,root); child[0]=null;

            mark("holdFixture.noOverflow.deflectedRZBlocksFreshA");
            child[0]=ui(()->new HoldFixture(false)); childRoot=waitRoot(child[0].id,null);
            axis(1f); pause(90);
            down=SystemClock.uptimeMillis(); fixtureADown=down;
            require(rawKey(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_A,0,0,down),"No-overflow A DOWN rejected");
            pause(1600); idleHold(child[0],0,"Deflected RZ armed a fresh A in a no-overflow dialog");
            neutral(); pause(1600); idleHold(child[0],0,"Neutral alone rearmed the old A");
            releaseA(down,0);
            mark("holdFixture.noOverflow.freshAAfterNeutral");
            down=SystemClock.uptimeMillis(); fixtureADown=down;
            require(rawKey(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_A,0,0,down),"Fresh neutral A DOWN rejected");
            pause(1600); waitFires(child[0],1,"Release plus fresh A after neutral"); releaseA(down,0);
            idleHold(child[0],1,"Fresh neutral A release");
            key(KeyEvent.KEYCODE_BUTTON_START); waitRoot(fixture.id,root); child[0]=null;

            require(preferences.equals(profilePreferences()),"Counter fixture changed profile/control choices");
            require(nativeBefore.equals(headerNativeState()),"Counter fixture changed native settings");
            require(history.equals(Store.prefs(activity).getString("history","")),"Counter fixture changed history");
            report.append("HoldFixture=PASS; counterOnly=6; A/touch fire once while held; fresh presses rearm; intermediate progress; early/canceled/orphan/outside/multitouch/focus/window/RZ cancellation; late child releases safe; deflected no-overflow RZ blocks A until neutral plus fresh press\n");
        } finally {
            // Cancel any unfinished synthetic press before removing its window.
            // The counter fixture is the only possible receiver of these releases.
            try {
                if (fixtureADown!=0 && ui(this::activeRoot)!=null) releaseA(fixtureADown,KeyEvent.FLAG_CANCELED);
                if (fixtureTouchDown!=0) touchEvent(MotionEvent.ACTION_CANCEL,fixtureTouchX,fixtureTouchY,fixtureTouchDown);
            } finally {
                ui(()->{
                    if (child[0]!=null) { child[0].hold.cancelHold(); child[0].panel.dialog.dismiss(); }
                    fixture.hold.cancelHold(); fixture.panel.dialog.dismiss(); return null;
                });
            }
            waitRoot(R.id.action_log,mainRoot);
        }
    }
    private void releaseA(long down,int flags) throws Exception {
        require(rawKey(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_A,0,flags,down),"Fixture A UP rejected"); fixtureADown=0; pause(90);
    }
    private void intermediateHold(HoldFixture fixture,String name) throws Exception {
        float progress=ui(fixture.hold::holdProgress);
        require(ui(fixture.hold::isHolding) && progress>.1f && progress<.85f,name+": no intermediate visual progress: "+progress);
        report.append("HoldProgress=PASS; input=").append(name).append("; progress=").append(progress).append('\n');
    }
    private void waitFires(HoldFixture fixture,int count,String name) throws Exception {
        long until=SystemClock.uptimeMillis()+700;
        while (ui(()->fixture.fires)<count && SystemClock.uptimeMillis()<until) pause(30);
        expectFires(fixture,count,name);
    }
    private void expectFires(HoldFixture fixture,int count,String name) throws Exception {
        int actual=ui(()->fixture.fires); require(actual==count,name+": expected counter="+count+", actual="+actual);
    }
    private void idleHold(HoldFixture fixture,int count,String name) throws Exception {
        expectFires(fixture,count,name);
        require(ui(()->!fixture.hold.isHolding() && fixture.hold.holdProgress()==0),name+": canceled/released hold remains active");
    }
    private void touchEvent(int action,float x,float y,long down) throws Exception {
        running(); require(automation!=null,"Touch automation is disconnected");
        int masked=action&MotionEvent.ACTION_MASK;
        if (masked==MotionEvent.ACTION_DOWN) { fixtureTouchDown=down; fixtureTouchX=x; fixtureTouchY=y; }
        int count=masked==MotionEvent.ACTION_POINTER_DOWN || masked==MotionEvent.ACTION_POINTER_UP?2:1;
        MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[count];
        MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[count];
        for (int i=0;i<count;i++) {
            properties[i]=new MotionEvent.PointerProperties(); properties[i].id=i; properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;
            coords[i]=new MotionEvent.PointerCoords(); coords[i].x=x+i*8; coords[i].y=y+i*8;
            coords[i].pressure=masked==MotionEvent.ACTION_UP || masked==MotionEvent.ACTION_CANCEL || masked==MotionEvent.ACTION_POINTER_UP && i==1?0:1; coords[i].size=1;
        }
        MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,count,properties,coords,0,0,1,1,-1,0,InputDevice.SOURCE_TOUCHSCREEN,0);
        try { require(automation.injectInputEvent(event,true),"Fixture touch event rejected: "+action); } finally { event.recycle(); }
        if (masked==MotionEvent.ACTION_UP || masked==MotionEvent.ACTION_CANCEL) fixtureTouchDown=0;
    }

    /** Left-stick motion takes Android's native joystick-to-D-pad path; RZ remains scrolling only. */
    private void controllerNavigation(ScrollView main) throws Exception {
        mark("controller.leftStickFixture");
        require(!ui(()->Store.state(activity).activeProfile(true)),"Dock must be inactive for controller preset navigation");
        require(ui(()->Store.enabled(activity)),"The fixed header-to-card navigation fixture requires automatic mode (no manual row)");
        SharedPreferences preferences=Store.prefs(activity);
        String[] dockKeys={"profile_dock_rate","profile_dock_fan","profile_dock_performance","profile_dock_layout","profile_dock_bypass"};
        Map<String,?> all=preferences.getAll();
        Map<String,Object> originals=new HashMap<>();
        for (String key:dockKeys) if (all.containsKey(key)) originals.put(key,all.get(key));
        Retroid retroid=new Retroid(activity);
        Map<String,String> nativeBefore=nativeSnapshot(retroid);
        try {
            reset(main,required(mainRoot,R.id.action_log));
            // Leave touch mode at the left header boundary, then prepare only the
            // header focus. No test setup directly focuses the controller tile.
            key(KeyEvent.KEYCODE_DPAD_LEFT);
            reset(main,required(mainRoot,R.id.action_log));
            expectFocus(R.id.action_log,"left Log fixture");
            mark("controller.leftStickDown");
            int[] path={R.id.dock_rate,R.id.dock_fan,R.id.dock_controller};
            for (int id:path) { leftPulse(0,1); expectFocus(id,"left-stick Y+ navigation"); }
            mark("controller.leftStickHorizontal");
            leftPulse(1,0); expectFocus(R.id.dock_bypass,"left-stick X+ navigation");
            leftPulse(-1,0); expectFocus(R.id.dock_controller,"left-stick X- navigation");
            mark("controller.dpadNavigation");
            key(KeyEvent.KEYCODE_DPAD_UP); pause(350); expectFocus(R.id.dock_fan,"controller D-pad UP");
            key(KeyEvent.KEYCODE_DPAD_DOWN); pause(350); expectFocus(R.id.dock_controller,"controller D-pad DOWN");

            mark("controller.inactiveSaveCycle");
            int layout=ui(()->Profiles.get(activity,true).layout);
            StringBuilder sequence=new StringBuilder(Integer.toString(layout));
            for (int i=0;i<3;i++) {
                require(!ui(()->Store.state(activity).activeProfile(true)),"Dock became active; refusing to change the live controller");
                expectFocus(R.id.dock_controller,"controller confirm target");
                layout=layout==2?0:layout==0?1:2;
                key(KeyEvent.KEYCODE_BUTTON_A);
                int expected=layout; long until=SystemClock.uptimeMillis()+1200L;
                while (ui(()->Profiles.get(activity,true).layout)!=expected && SystemClock.uptimeMillis()<until) pause(25);
                require(ui(()->Profiles.get(activity,true).layout)==expected,"Inactive Dock controller cycle did not save layout "+expected);
                require(nativeBefore.equals(nativeSnapshot(retroid)),"Inactive Dock controller save changed one of the nine native settings");
                expectFocus(R.id.dock_controller,"focus after inactive controller save");
                sequence.append("->").append(layout);
            }
            report.append("ControllerNavigation=PASS; leftY Log->Rate->Fan->Controller; leftX Controller->Bypass->Controller; Dpad UP/DOWN\n")
                .append("InactiveDockController=PASS; layouts=").append(sequence).append("; nine native settings unchanged after every A\n");
            mark("controller.rightStickDoesNotNavigate");
            Position before=position(mainRoot,main);
            if (ui(()->main.canScrollVertically(-1))) {
                axis(-1f); pause(180); neutral(); pause(70);
                Position after=position(mainRoot,main); unchangedFocus(before,after,"Right stick from controller tile");
                require(after.compareOffset(before)<0,"Right stick from controller tile did not scroll up");
                report.append("ControllerRightStick=PASS; scroll only; controller focus unchanged\n");
            } else report.append("ControllerRightStick=NOT_TESTED_CAPACITY; no upward range; Main probe covers right-stick scrolling\n");
        } finally {
            // Profiles.save writes all five Dock fields, even if some were absent.
            // Restore exactly those fields; output/history/control choices remain.
            SharedPreferences.Editor edit=preferences.edit();
            for (String key:dockKeys) {
                Object original=originals.get(key);
                if (!originals.containsKey(key)) edit.remove(key);
                else if (original instanceof Integer) edit.putInt(key,(Integer)original);
                else if (original instanceof Long) edit.putLong(key,(Long)original);
                else if (original instanceof Float) edit.putFloat(key,(Float)original);
                else if (original instanceof Boolean) edit.putBoolean(key,(Boolean)original);
                else if (original instanceof String) edit.putString(key,(String)original);
                else if (original instanceof Set) {
                    @SuppressWarnings("unchecked") Set<String> values=(Set<String>)original;
                    edit.putStringSet(key,new HashSet<>(values));
                } else throw new IllegalStateException("Unsupported original Dock preference type: "+key);
            }
            require(edit.commit(),"Could not restore the original Dock preferences");
            report.append("DockPreferences=RESTORED; keys=5; originallyPresent=").append(originals.size()).append('\n');
        }
    }

    private Map<String,String> nativeSnapshot(Retroid retroid) {
        synchronized (DockService.hardware) { return retroid.snapshot(); }
    }
    private void leftPulse(float x,float y) throws Exception {
        motion(x,y,0); pause(70); motion(0,0,0); pause(70); pause(350);
    }
    private void expectFocus(int id,String label) throws Exception {
        require(ui(()->mainRoot.findFocus()==mainRoot.findViewById(id)),label+" failed; "+ui(this::activeDescription));
    }

    private void probe(String name,View root,View target,View focus,boolean requireOverflow) throws Exception {
        mark(name+".setup");
        reset(target,focus);
        View fixedRight=required(root,root==mainRoot?R.id.action_settings:R.id.action_close_dialog);
        Rect headerBefore=visibleBox(fixedRight);
        Position before=position(root,target);
        boolean overflow=ui(()->can(target,1));
        if (!overflow) {
            require(!requireOverflow,"History has no overflow; this run cannot verify real list scrolling");
            phase=name+".shortPageHold"; axis(1f); pause(180); neutral(); pause(70);
            Position after=position(root,target);
            unchangedFocus(before,after,name);
            require(before.sameOffset(after),name+": short page moved unexpectedly");
            require(headerBefore.equals(visibleBox(fixedRight)),name+": fixed action moved with short-page input");
            report.append(name).append("=NO_OVERFLOW; injected hold is a no-op; focus/selection unchanged\n");
            return;
        }
        phase=name+".holdDown"; axis(1f); pause(240); neutral(); pause(70);
        Position down=position(root,target);
        unchangedFocus(before,down,name);
        require(headerBefore.equals(visibleBox(fixedRight)),name+": fixed action scrolled with content");
        require(down.compareOffset(before)>0,name+": injected right-stick hold did not scroll down ("+before+" -> "+down+")");
        phase=name+".neutral"; pause(220);
        Position stopped=position(root,target);
        unchangedFocus(down,stopped,name);
        require(down.sameOffset(stopped),name+": neutral did not stop scrolling");
        phase=name+".holdUp"; axis(-1f); pause(180); neutral(); pause(70);
        Position up=position(root,target);
        unchangedFocus(before,up,name);
        require(headerBefore.equals(visibleBox(fixedRight)),name+": fixed action moved during reverse scroll");
        require(up.compareOffset(down)<0,name+": reversed stick did not scroll up");
        report.append(name).append("=PASS; down ").append(before).append(" -> ").append(down)
            .append("; up -> ").append(up).append("; neutral/focus/selection stable\n");
    }
    private Rect visibleBox(View view) throws Exception { return ui(()->{ Rect box=new Rect(); require(view.getGlobalVisibleRect(box),"Pinned window action is not visible"); return box; }); }

    private void speed(View root,ListView list) throws Exception {
        mark("history.amplitudePartial");
        reset(list,list); Position base=position(root,list);
        axis(.45f); pause(240); neutral(); pause(70);
        Position partial=position(root,list); unchangedFocus(base,partial,"History partial amplitude");
        boolean partialCapacity=ui(()->list.canScrollList(1));
        require(partial.compareOffset(base)>0,"Partial right-stick amplitude did not scroll");
        mark("history.amplitudeFull");
        reset(list,list); base=position(root,list);
        axis(1f); pause(240); neutral(); pause(70);
        Position full=position(root,list); unchangedFocus(base,full,"History full amplitude");
        boolean fullCapacity=ui(()->list.canScrollList(1));
        if (partialCapacity && fullCapacity) {
            require(full.compareOffset(partial)>0,"Partial amplitude was not slower than full amplitude: "+partial+" / "+full);
            report.append("HistoryAmplitude=PASS; equal 240ms holds: partial=").append(partial).append("; full=").append(full).append('\n');
        } else {
            report.append("HistoryAmplitude=NOT_TESTED_CAPACITY; a hold reached the list boundary; partial=").append(partial)
                .append("; full=").append(full).append("; partialCanScrollDown=").append(partialCapacity)
                .append("; fullCanScrollDown=").append(fullCapacity).append('\n');
        }
        mark("history.deadZone");
        axis(.1f); pause(180); neutral(); pause(70);
        Position dead=position(root,list); unchangedFocus(full,dead,"History dead zone");
        require(full.sameOffset(dead),"Dead-zone input moved the journal");
        report.append("HistoryDeadZone=PASS; stationary; focus/selection unchanged\n");
    }

    /** Setup may reposition a view; all measured movement is from injected MotionEvents. */
    private void reset(View target,View focus) throws Exception {
        neutral();
        ui(()->{
            focus.setFocusableInTouchMode(true); require(focus.requestFocus(),"Cannot prepare focus for "+focus.getClass().getSimpleName());
            if (target instanceof AbsListView) ((AbsListView)target).setSelectionFromTop(0,target.getPaddingTop());
            else target.scrollTo(0,0);
            return null;
        });
        pause(90);
    }
    private void focus(View target) throws Exception {
        ui(()->{ require(target.isEnabled(),"Requested UI action is disabled"); target.setFocusableInTouchMode(true); require(target.requestFocus(),"Cannot focus UI action"); return null; });
        pause(60);
    }

    private static final class Position {
        final View focus;
        final int scroll,first,top,selected,checked;
        final boolean list;
        Position(View root,View target) {
            // Window.getCurrentFocus() delegates to its decor's findFocus().
            focus=root.findFocus(); list=target instanceof AbsListView; scroll=target.getScrollY();
            if (list) {
                AbsListView items=(AbsListView)target; first=items.getFirstVisiblePosition();
                top=items.getChildCount()==0?0:items.getChildAt(0).getTop(); selected=items.getSelectedItemPosition();
                checked=target instanceof ListView?((ListView)target).getCheckedItemPosition():-1;
            } else { first=0; top=0; selected=-1; checked=-1; }
        }
        int compareOffset(Position other) { return list?(first!=other.first?Integer.compare(first,other.first):Integer.compare(other.top,top)):Integer.compare(scroll,other.scroll); }
        boolean sameOffset(Position other) { return compareOffset(other)==0; }
        @Override public String toString() { return list?"item="+first+",top="+top+",selected="+selected+",checked="+checked:"scrollY="+scroll; }
    }
    private Position position(View root,View target) throws Exception { return ui(()->new Position(root,target)); }
    private static void unchangedFocus(Position before,Position after,String name) {
        require(before.focus==after.focus,name+": analog input moved keyboard focus");
        require(before.selected==after.selected,name+": analog input changed selected position");
        require(before.checked==after.checked,name+": analog input changed checked value");
    }
    private static boolean can(View target,int direction) { return target instanceof AbsListView?((AbsListView)target).canScrollList(direction):target.canScrollVertically(direction); }

    private void axis(float value) throws Exception { motion(0,0,value); }
    private void motion(float x,float y,float rz) throws Exception {
        running(); require(automation!=null,"Input automation is not connected");
        if (ui(this::activeRoot)==null) {
            require(x==0 && y==0 && rz==0,"No focused app window owns joystick input; "+ui(this::activeDescription));
            return;
        }
        long now=SystemClock.uptimeMillis();
        MotionEvent.PointerProperties pointer=new MotionEvent.PointerProperties(); pointer.id=0; pointer.toolType=MotionEvent.TOOL_TYPE_UNKNOWN;
        MotionEvent.PointerCoords axes=new MotionEvent.PointerCoords(); axes.setAxisValue(MotionEvent.AXIS_X,x); axes.setAxisValue(MotionEvent.AXIS_Y,y); axes.setAxisValue(MotionEvent.AXIS_RZ,rz);
        MotionEvent event=MotionEvent.obtain(now,now,MotionEvent.ACTION_MOVE,1,new MotionEvent.PointerProperties[]{pointer},new MotionEvent.PointerCoords[]{axes},0,0,1f,1f,-1,0,InputDevice.SOURCE_JOYSTICK,0);
        try { require(automation.injectInputEvent(event,true),"Joystick event injection was rejected"); }
        finally { event.recycle(); }
    }
    private void neutral() throws Exception { if (automation!=null) axis(0f); }
    private void key(int code) throws Exception {
        running(); require(automation!=null,"Input automation is not connected");
        require(ui(this::activeRoot)!=null,"No focused app window owns gamepad input; "+ui(this::activeDescription));
        long down=SystemClock.uptimeMillis();
        for (int action:new int[]{KeyEvent.ACTION_DOWN,KeyEvent.ACTION_UP}) {
            KeyEvent event=new KeyEvent(down,SystemClock.uptimeMillis(),action,code,0,0,-1,0,0,InputDevice.SOURCE_GAMEPAD);
            require(automation.injectInputEvent(event,true),"Gamepad key injection was rejected: "+code);
        }
        pause(90);
    }
    private View required(View root,int id) throws Exception {
        View found=ui(()->root.findViewById(id)); require(found!=null,"Required UI id missing: "+id); return found;
    }
    private View waitRoot(int id,View expected) throws Exception {
        long until=SystemClock.uptimeMillis()+5000;
        do {
            View found=ui(()->{
                View active=activeRoot();
                return active!=null && (expected==null || active==expected) && active.findViewById(id)!=null?active:null;
            });
            if (found!=null) return found;
            pause(50);
        } while (SystemClock.uptimeMillis()<until);
        throw new IllegalStateException("Timed out waiting for UI id "+id+"; "+ui(this::activeDescription));
    }
    private View waitListRoot(int count,View excluded) throws Exception {
        long until=SystemClock.uptimeMillis()+5000;
        do {
            View found=ui(()->{ View active=activeRoot(); ListView list=active==null?null:first(active,ListView.class); return active!=excluded && list!=null && list.getCount()==count?active:null; });
            if (found!=null) return found;
            pause(50);
        } while (SystemClock.uptimeMillis()<until);
        throw new IllegalStateException("Expected native list with "+count+" choices; "+ui(this::activeDescription));
    }
    private View activeRoot() {
        List<View> roots=WindowInspector.getGlobalWindowViews();
        for (int i=roots.size()-1;i>=0;i--) { View root=roots.get(i); if (root.hasWindowFocus() && root.isShown() && root.isAttachedToWindow()) return root; }
        return null;
    }
    private String activeDescription() {
        View root=activeRoot(),focus=root==null?null:root.findFocus();
        StringBuilder detail=new StringBuilder("active=").append(root==null?"none":root.getClass().getSimpleName())
            .append(",focus=").append(focus==null?"none":focus.getClass().getSimpleName()+"#"+focus.getId());
        List<View> roots=WindowInspector.getGlobalWindowViews(); detail.append(",ownRoots=").append(roots.size());
        for (int i=0;i<roots.size();i++) {
            View candidate=roots.get(i);
            detail.append(" [").append(i).append(candidate==mainRoot?":Main":":Dialog").append(",shown=").append(candidate.isShown())
                .append(",attached=").append(candidate.isAttachedToWindow()).append(",windowFocus=").append(candidate.hasWindowFocus())
                .append(",visibility=").append(candidate.getWindowVisibility()).append(",size=").append(candidate.getWidth()).append('x').append(candidate.getHeight()).append(']');
        }
        if (activity!=null) detail.append(",activityFinishing=").append(activity.isFinishing()).append(",destroyed=").append(activity.isDestroyed());
        return detail.toString();
    }
    private void closeToMain() throws Exception {
        if (mainRoot==null) return;
        for (int i=0;i<6;i++) {
            View root=ui(this::activeRoot);
            if (root==mainRoot) return;
            if (root==null || automation==null) { report.append("cleanup=NO_OWN_FOCUS; no keys sent; ").append(ui(this::activeDescription)).append('\n'); return; }
            key(KeyEvent.KEYCODE_BUTTON_B);
        }
        require(ui(this::activeRoot)==mainRoot,"Could not close test dialogs");
    }
    private Map<String,Object> profilePreferences() throws Exception {
        return ui(()->{
            Map<String,Object> result=new HashMap<>();
            for (Map.Entry<String,?> entry:Store.prefs(activity).getAll().entrySet()) {
                String key=entry.getKey();
                if (key.startsWith("profile_") || key.equals("enabled") || key.equals("managed") || key.equals("start_on_boot")
                    || key.equals("manual_docked") || key.equals("last_docked")) result.put(key,entry.getValue());
            }
            return result;
        });
    }
    private void mark(String name) {
        running();
        phase=name;
        Bundle progress=new Bundle(); progress.putString("stream","AnalogUi phase="+name+"\n");
        instrumentation.sendStatus(0,progress);
    }
    private <T> T ui(Ui<T> task) throws Exception {
        running();
        final Object[] result=new Object[1]; final Throwable[] failure=new Throwable[1];
        instrumentation.runOnMainSync(()->{ try { running(); result[0]=task.get(); } catch (Throwable error) { failure[0]=error; } });
        if (failure[0]!=null) throw new Exception("UI operation failed: "+failure[0],failure[0]);
        @SuppressWarnings("unchecked") T value=(T)result[0]; return value;
    }
    private static <T extends View> T first(View root,Class<T> type) {
        if (type.isInstance(root)) return type.cast(root);
        if (root instanceof ViewGroup) { ViewGroup group=(ViewGroup)root; for (int i=0;i<group.getChildCount();i++) { T found=first(group.getChildAt(i),type); if (found!=null) return found; } }
        return null;
    }
    private static void require(boolean condition,String message) { if (!condition) throw new IllegalStateException(message); }
    private void running() { require(!cancelled,"Instrumentation check has been cancelled"); }
    private static void pause(long millis) { SystemClock.sleep(millis); }
}
