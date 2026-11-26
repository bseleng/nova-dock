package local.nova.dock;

import android.app.LocaleManager;
import android.content.Context;
import android.os.Build;
import android.os.LocaleList;
import java.text.NumberFormat;
import java.util.Locale;

/** Native Android resources and per-app locales; no separate language state. */
final class AppText {
    private AppText() { }

    static String get(Context c,int resource,Object... arguments) {
        return arguments.length==0?c.getString(resource):c.getString(resource,arguments);
    }

    static String language(Context c) {
        if (Build.VERSION.SDK_INT<33) return "system";
        LocaleManager manager=c.getSystemService(LocaleManager.class);
        if (manager==null) return "system";
        LocaleList locales=manager.getApplicationLocales();
        if (locales.isEmpty()) return "system";
        String language=locales.get(0).getLanguage();
        return "ru".equals(language)||"en".equals(language)?language:"system";
    }

    static void setLanguage(Context c,String language) {
        if (!"system".equals(language) && !"ru".equals(language) && !"en".equals(language))
            throw new IllegalArgumentException("invalid_value");
        if (Build.VERSION.SDK_INT<33) {
            if (!"system".equals(language)) throw new IllegalStateException("unsupported_device");
            return;
        }
        LocaleManager manager=c.getSystemService(LocaleManager.class);
        if (manager==null) throw new IllegalStateException("unsupported_device");
        LocaleList selected="system".equals(language)?LocaleList.getEmptyLocaleList():LocaleList.forLanguageTags(language);
        if (!selected.equals(manager.getApplicationLocales())) manager.setApplicationLocales(selected);
    }

    static void applyLanguage(Context c,String language) { setLanguage(c,language); }

    static String entries(Context c,int count) {
        return c.getResources().getQuantityString(R.plurals.history_entries,count,count);
    }

    static String bytes(Context c,long count) {
        count=Math.max(0,count);
        if (count<1024) return get(c,R.string.format_bytes,count);
        LocaleList locales=c.getResources().getConfiguration().getLocales();
        Locale locale=locales.isEmpty()?Locale.ENGLISH:locales.get(0);
        NumberFormat format=NumberFormat.getNumberInstance(locale);
        format.setMinimumFractionDigits(0); format.setMaximumFractionDigits(1);
        format.setGroupingUsed(false);
        return get(c,R.string.format_kibibytes,format.format(count/1024d));
    }

    /** Error codes stay stable in storage; only their presentation is localized. */
    static String error(Context c,String code,String detail) {
        String legacy=code==null?null:legacyCode(code.trim());
        String message=get(c,errorResource(legacy==null?code:legacy));
        if (detail==null || detail.trim().isEmpty() || detail.equals(code)) return message;
        String localized=legacyMessage(c,detail);
        return message.equals(localized)?message:get(c,R.string.format_error_detail,message,localized);
    }

    /** Maps older pure-model/vendor exception messages without an Android dependency there. */
    static String error(Context c,Throwable failure) {
        String message=failure==null?null:failure.getMessage();
        if (message==null || message.trim().isEmpty()) return get(c,R.string.error_unknown);
        return legacyMessage(c,message);
    }

    private static String legacyMessage(Context c,String message) {
        String text=message.trim();
        String code=legacyCode(text);
        if (code!=null) return get(c,errorResource(code));
        String[] prefixes={"Сервис Retroid: ","Retroid service: ","Ошибка: ","Error: "};
        for (String prefix:prefixes) if (text.startsWith(prefix)) {
            String detail=legacyMessage(c,text.substring(prefix.length()));
            if (prefix.startsWith("Сервис") || prefix.startsWith("Retroid"))
                return error(c,"command_failed",detail);
            return detail;
        }
        return text;
    }

    private static String legacyCode(String message) {
        String text=message.endsWith(".")?message.substring(0,message.length()-1):message;
        switch (text) {
            case "vendor_unavailable": case "Сервис Retroid недоступен": return "vendor_unavailable";
            case "command_failed": return "command_failed";
            case "unsupported_device": case "Требуется Nova с Android 13 и доступный сервис Retroid": return "unsupported_device";
            case "invalid_value": case "Недопустимое значение": return "invalid_value";
            case "profile_save_failed": case "Не удалось сохранить профиль": return "profile_save_failed";
            case "original_save_failed": case "Не удалось сохранить исходные настройки": return "original_save_failed";
            case "control_save_failed": case "Не удалось сохранить режим управления": return "control_save_failed";
            case "fan_incompatible": case "Режим вентилятора несовместим с производительностью":
            case "Вентилятор недоступен при текущей производительности": return "fan_incompatible";
            case "unknown_setting": case "Неизвестная настройка": return "unknown_setting";
            case "global_read_failed": return "global_read_failed";
            case "global_write_failed": return "global_write_failed";
            case "invalid_key": case "Недопустимый ключ": return "invalid_key";
            case "invalid_rate": case "Неизвестная частота": return "invalid_rate";
            case "invalid_layout": case "Неизвестная раскладка": return "invalid_layout";
            case "invalid_performance": case "Неизвестный режим производительности": return "invalid_performance";
            case "unknown_charge_mode": case "Неизвестный режим зарядки": return "unknown_charge_mode";
            case "display_unavailable": case "Нет доступа к состоянию видеовыхода": return "display_unavailable";
            case "vendor_rejected": case "Сервис Retroid не принял команду": return "vendor_rejected";
            case "vendor_no_reply": case "Нет ответа сервиса Retroid": return "vendor_no_reply";
            default:return null;
        }
    }

    private static int errorResource(String code) {
        if (code==null) return R.string.error_unknown;
        switch (code) {
            case "vendor_unavailable":return R.string.error_vendor_unavailable;
            case "command_failed":return R.string.error_command_failed;
            case "unsupported_device":return R.string.error_unsupported_device;
            case "invalid_value":return R.string.error_invalid_value;
            case "profile_save_failed":return R.string.error_profile_save_failed;
            case "original_save_failed":return R.string.error_original_save_failed;
            case "control_save_failed":return R.string.error_control_save_failed;
            case "fan_incompatible":return R.string.error_fan_incompatible;
            case "unknown_setting":return R.string.error_unknown_setting;
            case "global_read_failed":return R.string.error_global_read_failed;
            case "global_write_failed":return R.string.error_global_write_failed;
            case "invalid_key":return R.string.error_invalid_key;
            case "invalid_rate":return R.string.error_invalid_rate;
            case "invalid_layout":return R.string.error_invalid_layout;
            case "invalid_performance":return R.string.error_invalid_performance;
            case "unknown_charge_mode":return R.string.error_unknown_charge_mode;
            case "display_unavailable":return R.string.error_display_unavailable;
            case "vendor_rejected":return R.string.error_vendor_rejected;
            case "vendor_no_reply":return R.string.error_vendor_no_reply;
            default:return R.string.error_unknown;
        }
    }
}
