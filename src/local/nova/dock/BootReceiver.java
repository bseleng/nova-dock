package local.nova.dock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String a = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(a) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) return;
        ControlState state=Store.state(context);
        boolean boot=Intent.ACTION_BOOT_COMPLETED.equals(a);
        if (!state.shouldResume(boot,Store.startOnBoot(context))) {
            if (state.managed && boot) Store.log(context,"Автозапуск пропущен · Выключен в настройках");
            return;
        }
        // Replacing the APK resumes a managed session even when boot startup is disabled.
        try { DockService.start(context, null); }
        catch (Exception e) { Store.log(context,"Не удалось возобновить управление: "+e.getMessage()); }
    }
}
