package local.nova.dock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String a = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(a) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) return;
        if (Store.enabled(context)) {
            try { DockService.start(context, null); }
            catch (Exception e) { Store.log(context, "Не удалось запустить после загрузки: " + e.getMessage()); }
        }
    }
}
