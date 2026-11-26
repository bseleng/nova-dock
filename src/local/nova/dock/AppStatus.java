package local.nova.dock;

import android.content.Context;

final class AppStatus {
    static String text(Context c) {
        String argument=Store.prefs(c).getString("status_argument","");
        switch (Store.statusCode(c)) {
            case "waiting":return c.getString(R.string.status_waiting);
            case "applied":return ProfileText.status(c,Profiles.get(c,"1".equals(argument)));
            case "error":return c.getString(R.string.status_error,AppText.error(c,argument,Store.prefs(c).getString("status_detail","")));
            case "stopped":return c.getString(R.string.status_stopped);
            case "restored":return c.getString(R.string.status_restored);
            case "checking":return c.getString(R.string.status_checking);
            default:return c.getString(R.string.status_idle);
        }
    }
}
