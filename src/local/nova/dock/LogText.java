package local.nova.dock;

import android.content.Context;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Stable events are localized at read time; unknown legacy records retain their exact text. */
final class LogText {
    static final class Item {
        String time="—",title,detail="",badge;
        int color=0xffa5bab5;
    }
    private static final Pattern LEGACY=Pattern.compile("^(\\d{2}:\\d{2}:\\d{2})\\s{2}(.*)$");
    static Item item(Context c,String raw) {
        LogEvent event=LogEvent.parse(raw);
        if ("legacy".equals(event.code)) return legacy(c,raw);
        Item item=new Item(); item.time=event.time; item.title=c.getString(R.string.event_unknown); item.badge=c.getString(R.string.badge_event);
        String[] a=event.args;
        try {
            switch (event.code) {
                case "auto_changed":item.title=c.getString(R.string.event_auto_changed); onOff(c,item,a[0]);break;
                case "boot_changed":item.title=c.getString(R.string.event_boot_changed); onOff(c,item,a[0]); item.detail=c.getString("1".equals(a[0])?R.string.detail_startup_enabled:R.string.detail_startup_disabled);break;
                case "session_started":item.title=c.getString(R.string.event_session_started); item.badge=c.getString(R.string.badge_started); item.color=0xff74e3cc; item.detail=c.getString("1".equals(a[0])?R.string.event_automation:R.string.event_manual);break;
                case "mode_applied": {
                    boolean dock="1".equals(a[0]);
                    Profile profile=new Profile(dock,number(a[1]),number(a[2]),number(a[3]),number(a[4]),"1".equals(a[5]));
                    item.title=c.getString(dock?R.string.event_dock_profile:R.string.event_handheld_profile);
                    item.detail=ProfileText.status(c,profile)+" · "+ProfileText.performance(c,profile.performance)+" · "+c.getString("manual".equals(a[6])?R.string.detail_manual_selection:R.string.detail_automatic_selection);
                    if (a.length>8) item.detail+=" · "+ProfileText.setting(c,SettingChanges.OUTPUT)+": "+GlobalText.savedSummary(c,OutputSpec.stored(dock,a[7],a[8]));
                    applied(c,item,dock); break;
                }
                case "profile_saved": {
                    boolean dock="1".equals(a[0]); int setting=number(a[1]);
                    item.title=c.getString(dock?R.string.event_dock_profile:R.string.event_handheld_profile); item.badge=c.getString(R.string.badge_saved); item.color=dock?0xff74e3cc:0xffbeb1f7;
                    String before=a.length>6 && "1".equals(a[6]) && setting==Profile.FAN?c.getString(R.string.fan_custom):ProfileText.value(c,setting,number(a[2]));
                    item.detail=c.getString(R.string.format_setting_change,ProfileText.setting(c,setting),before,ProfileText.value(c,setting,number(a[3])));
                    if (a.length>5 && !a[4].equals(a[5])) item.detail+=" · "+c.getString(R.string.format_profile_fan_change,ProfileText.fan(c,number(a[4])),ProfileText.fan(c,number(a[5])));
                    break;
                }
                case "setting_applied":item.title=c.getString(R.string.event_setting_applied); applied(c,item,"1".equals(a[0])); item.detail=c.getString("1".equals(a[0])?R.string.mode_docked:R.string.mode_handheld)+" · "+ProfileText.setting(c,number(a[1]))+": "+ProfileText.value(c,number(a[1]),number(a[2]));break;
                case "output_saved": {
                    boolean dock="1".equals(a[0]); item.title=c.getString(dock?R.string.event_dock_profile:R.string.event_handheld_profile); item.badge=c.getString(R.string.badge_saved); item.color=dock?0xff74e3cc:0xffbeb1f7;
                    item.detail=c.getString(R.string.format_setting_change,ProfileText.setting(c,SettingChanges.OUTPUT),GlobalText.savedSummary(c,OutputSpec.stored(dock,a[1],a[2])),GlobalText.savedSummary(c,OutputSpec.stored(dock,a[3],a[4]))); break;
                }
                case "output_applied": {
                    boolean dock="1".equals(a[0]); item.title=c.getString(R.string.event_setting_applied); applied(c,item,dock); item.detail=c.getString(dock?R.string.mode_docked:R.string.mode_handheld)+" · "+ProfileText.setting(c,SettingChanges.OUTPUT)+": "+GlobalText.savedSummary(c,OutputSpec.stored(dock,a[1],a[2])); break;
                }
                case "output_observed":item.title=c.getString(R.string.event_output_observed); item.detail=c.getString("1".equals(a[0])?R.string.mode_docked:R.string.mode_handheld)+" · "+a[2]+" · "+c.getString(R.string.format_output_resolution_rate,number(a[3]),number(a[4]),a[5]);break;
                case "global_applied": {
                    item.title=c.getString(R.string.event_global_applied); applied(c,item,true); GlobalChanges.Key key=GlobalChanges.Key.valueOf(a[0]);
                    if (key==GlobalChanges.Key.OUTPUT) {
                        OutputMode.Aspect aspect=OutputMode.Aspect.valueOf(a[2]); OutputMode.Preset preset=a[1].isEmpty()?null:OutputMode.Preset.valueOf(a[1]);
                        item.detail=preset==null?GlobalText.aspect(c,aspect):c.getString(R.string.format_output_summary,GlobalText.preset(c,preset,aspect),GlobalText.aspect(c,aspect));
                    } else item.detail=GlobalText.key(c,key)+": "+c.getString("1".equals(a[1])?R.string.value_enabled_state:R.string.value_disabled_state);
                    break;
                }
                case "connection_changed":item.title=c.getString(R.string.event_connection_changed); item.detail=c.getString(R.string.detail_connection_changed);break;
                case "stopped":item.title=c.getString(R.string.event_stopped); item.badge=c.getString(R.string.badge_stopped); item.detail=c.getString(R.string.status_stopped);break;
                case "restored":item.title=c.getString(R.string.event_restored); item.badge=c.getString(R.string.badge_restored); item.detail=c.getString(R.string.status_restored); item.color=0xff74e3cc;break;
                case "error":item.title=c.getString(R.string.event_error); item.badge=c.getString(R.string.badge_error); item.color=0xfff4aaa9; item.detail=AppText.error(c,a.length>0?a[0]:"unknown",a.length>1?a[1]:"");break;
                case "boot_skipped":item.title=c.getString(R.string.event_startup); item.badge=c.getString(R.string.badge_skipped); item.detail=c.getString(R.string.detail_disabled_in_settings);break;
                case "resume_failed":item.title=c.getString(R.string.event_startup); item.badge=c.getString(R.string.badge_error); item.color=0xfff4aaa9; item.detail=AppText.error(c,a[0],a.length>1?a[1]:"");break;
                default:item.detail=event.raw;break;
            }
            return item;
        } catch (RuntimeException ignored) { return legacy(c,raw); }
    }
    private static void applied(Context c,Item item,boolean dock) { item.badge=c.getString(R.string.badge_applied); item.color=dock?0xff74e3cc:0xffbeb1f7; }
    private static void onOff(Context c,Item item,String value) { item.badge=c.getString("1".equals(value)?R.string.badge_on:R.string.badge_off); item.color="1".equals(value)?0xff74e3cc:0xffa5bab5; }
    private static int number(String value) { return Integer.parseInt(value); }
    private static Item legacy(Context c,String raw) {
        Item item=new Item(); item.badge=c.getString(R.string.badge_event);
        Matcher match=LEGACY.matcher(raw); String message=raw;
        if (match.matches()) { item.time=match.group(1); message=match.group(2); }
        item.title=message;
        String[] split=message.split(" · ",2); String head=split[0]; String detail=split.length>1?split[1]:"";
        if (message.startsWith("Ошибка: ")) { item.title=c.getString(R.string.event_error); item.detail=AppText.error(c,new IllegalStateException(message.substring(8))); item.badge=c.getString(R.string.badge_error); item.color=0xfff4aaa9; }
        else if (head.equals("Профиль дока сохранён") || head.equals("Портативный профиль сохранён")) {
            boolean dock=head.equals("Профиль дока сохранён"); item.title=c.getString(dock?R.string.event_dock_profile:R.string.event_handheld_profile); item.badge=c.getString(R.string.badge_saved); item.color=dock?0xff74e3cc:0xffbeb1f7; item.detail=known(c,detail);
        } else if (head.equals("В доке") || head.equals("Портативный режим")) {
            boolean dock=head.equals("В доке"); item.title=c.getString(dock?R.string.event_dock_profile:R.string.event_handheld_profile); applied(c,item,dock); item.detail=known(c,detail);
        } else if (head.equals("Настройка применена")) { item.title=c.getString(R.string.event_setting_applied); applied(c,item,true); item.detail=known(c,detail); }
        else if (head.equals("Автоматика запущена") || head.equals("Ручное управление запущено")) { item.title=c.getString(head.equals("Автоматика запущена")?R.string.event_automation:R.string.event_manual); item.badge=c.getString(R.string.badge_started); item.detail=c.getString(R.string.detail_service_started); item.color=0xff74e3cc; }
        else if (head.equals("Автозапуск включён") || head.equals("Автозапуск выключен") || head.equals("Автозапуск пропущен")) {
            item.title=c.getString(R.string.event_startup); item.badge=c.getString(head.equals("Автозапуск включён")?R.string.badge_on:head.equals("Автозапуск выключен")?R.string.badge_off:R.string.badge_skipped);
            item.detail=c.getString(head.equals("Автозапуск включён")?R.string.detail_startup_enabled:head.equals("Автозапуск выключен")?R.string.detail_startup_disabled:R.string.detail_disabled_in_settings);
        } else if (head.equals("Управление остановлено") || head.equals("Исходные настройки восстановлены")) {
            boolean restored=head.equals("Исходные настройки восстановлены"); item.title=c.getString(restored?R.string.event_restored:R.string.event_stopped); item.badge=c.getString(restored?R.string.badge_restored:R.string.badge_stopped);
        } else if (head.startsWith("Вентилятор восстановлен: ") || head.startsWith("Обходная зарядка восстановлена: ")) {
            int colon=head.indexOf(": "); boolean fan=head.startsWith("Вентилятор"); item.title=c.getString(fan?R.string.event_fan:R.string.event_bypass); item.badge=c.getString(R.string.badge_restored); item.detail=known(c,head.substring(colon+2)+(detail.isEmpty()?"":" · "+detail));
        } else if (head.startsWith("Автоматика выключена")) { item.title=c.getString(R.string.event_automation); item.badge=c.getString(R.string.badge_off); item.detail=known(c,detail); }
        else if (head.startsWith("Повторная проверка профиля:")) { item.title=c.getString(R.string.event_profile_check); item.badge=c.getString(R.string.badge_retry); item.detail=known(c,head.substring(head.indexOf(':')+1).trim()); }
        else if (head.startsWith("Самопроверка: ")) { item.title=c.getString(R.string.event_self_test); item.detail=known(c,message.substring(14)); }
        return item;
    }
    private static String known(Context c,String value) {
        String[][] replacements={
            {"Вентилятор профиля",c.getString(R.string.setting_fan)}, {"Портативный режим",c.getString(R.string.mode_handheld)}, {"В руках",c.getString(R.string.mode_handheld)}, {"В доке",c.getString(R.string.mode_docked)},
            {"Обходная зарядка",c.getString(R.string.setting_bypass)}, {"Производительность",c.getString(R.string.setting_performance)}, {"Контроллер",c.getString(R.string.setting_controller)}, {"Вентилятор",c.getString(R.string.setting_fan)}, {"Частота",c.getString(R.string.setting_rate)},
            {"частота без изменения",c.getString(R.string.value_unchanged)}, {"Не менять",c.getString(R.string.value_unchanged)}, {"Максимум",c.getString(R.string.fan_maximum)}, {"Выключен в настройках",c.getString(R.string.detail_disabled_in_settings)}, {"Выключен",c.getString(R.string.value_disabled)},
            {"Неизвестно",c.getString(R.string.value_unknown)}, {"Custom",c.getString(R.string.fan_custom)},
            {"Выключена",c.getString(R.string.value_off)}, {"Включена",c.getString(R.string.value_on)}, {"Включен",c.getString(R.string.value_on)},
            {"Ручной выбор",c.getString(R.string.detail_manual_selection)}, {"событие питания/экрана",c.getString(R.string.detail_automatic_selection)},
            {"выкл.",c.getString(R.string.value_off)}, {"вкл.",c.getString(R.string.value_on)}, {"Гц",c.getString(R.string.format_rate_value_hz,"").trim()}
        };
        return LegacyText.translate(value,replacements);
    }
}
