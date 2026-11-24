package local.nova.dock;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Formats legacy transition history without changing or discarding stored entries. */
final class HistoryDialog {
    private static final int TEXT=0xffedf6f3, MUTED=0xff9bb2ad, MINT=0xff74e3cc, LILAC=0xffbeb1f7;
    private static final Pattern LINE=Pattern.compile("^(\\d{2}:\\d{2}:\\d{2})\\s{2}(.*)$");
    private final Activity activity;
    private HistoryDialog(Activity activity) { this.activity=activity; }
    static void show(Activity activity) { new HistoryDialog(activity).open(); }

    private void open() {
        Dialog dialog=new Dialog(activity); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root=column(); root.setPadding(dp(18),dp(10),dp(18),dp(14));
        root.setBackground(shape(0xff182427,24)); root.setClipToOutline(true);
        LinearLayout header=new LinearLayout(activity); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text("Журнал",22,TEXT); title.setTypeface(null,Typeface.BOLD);
        header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView close=text("Закрыть",14,MINT); close.setGravity(Gravity.CENTER); close.setPadding(dp(10),0,dp(10),0);
        GamepadControls.style(close,0xff243639,MINT,14);
        close.setOnClickListener(v->dialog.dismiss());
        header.addView(close,new LinearLayout.LayoutParams(-2,dp(44))); root.addView(header);
        TextView subtitle=text("Последние события · новые сверху",12,MUTED); subtitle.setPadding(0,dp(3),0,dp(14)); root.addView(subtitle);
        ScrollView scroll=new ScrollView(activity); scroll.setClipToPadding(false);
        LinearLayout entries=column(); scroll.addView(entries);
        String history=Store.prefs(activity).getString("history","");
        if (history.trim().isEmpty()) {
            TextView empty=text("Событий пока нет. Здесь появятся запуск автоматики, смена профилей и изменения настроек.",15,MUTED);
            empty.setPadding(dp(14),dp(24),dp(14),dp(24)); entries.addView(empty);
        } else for (String raw:history.split("\n")) {
            if (raw.trim().isEmpty()) continue;
            Entry entry=new Entry(raw);
            LinearLayout row=new LinearLayout(activity); row.setGravity(Gravity.TOP);
            row.setPadding(dp(12),dp(12),dp(12),dp(12)); row.setBackground(shape(0xff233236,15));
            TextView time=text(entry.time,18,MUTED); time.setTypeface(Typeface.MONOSPACE,Typeface.NORMAL);
            time.setSingleLine(true);
            int timeWidth=Math.max(dp(96),(int)Math.ceil(time.getPaint().measureText("00:00:00"))+dp(12));
            row.addView(time,new LinearLayout.LayoutParams(timeWidth,-2));
            LinearLayout content=column();
            LinearLayout event=new LinearLayout(activity); event.setGravity(Gravity.CENTER_VERTICAL);
            TextView name=text(entry.title,14,TEXT); name.setTypeface(null,Typeface.BOLD);
            event.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            TextView badge=text(entry.badge,9,entry.color); badge.setTypeface(null,Typeface.BOLD); badge.setPadding(dp(8),dp(4),dp(8),dp(4));
            badge.setBackground(shape((entry.color&0x00ffffff)|0x18000000,8));
            LinearLayout.LayoutParams tag=new LinearLayout.LayoutParams(-2,-2); tag.leftMargin=dp(8); event.addView(badge,tag);
            content.addView(event);
            if (!entry.detail.isEmpty()) {
                TextView detail=text(entry.detail,12,MUTED); detail.setPadding(0,dp(5),0,0); detail.setLineSpacing(dp(2),1f); detail.setTextIsSelectable(true); content.addView(detail);
            }
            row.addView(content,new LinearLayout.LayoutParams(0,-2,1));
            LinearLayout.LayoutParams item=new LinearLayout.LayoutParams(-1,-2); item.bottomMargin=dp(9); entries.addView(row,item);
        }
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        dialog.setContentView(root); dialog.show();
        GamepadControls.attach(dialog);
        DisplayMetrics screen=activity.getResources().getDisplayMetrics();
        Window window=dialog.getWindow(); window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setDimAmount(.65f);
        window.setLayout(Math.min(screen.widthPixels-dp(28),dp(960)),Math.min(screen.heightPixels-dp(56),dp(720)));
        close.setFocusableInTouchMode(true); close.requestFocus();
    }

    static final class Entry {
        String time="—", title, detail="", badge="СОБЫТИЕ";
        int color=MUTED;
        Entry(String raw) {
            Matcher match=LINE.matcher(raw);
            String message=raw;
            if (match.matches()) { time=match.group(1); message=match.group(2); }
            title=message;
            String[] parts=message.split(" · ",2);
            if (parts.length==2) { title=parts[0]; detail=parts[1]; }
            if (message.startsWith("Ошибка: ")) {
                title="Ошибка применения"; detail=message.substring("Ошибка: ".length()); badge="ОШИБКА"; color=0xfff4aaa9;
            } else if (message.startsWith("Профиль дока сохранён")) {
                title="Профиль дока"; badge="СОХРАНЁН"; color=MINT;
            } else if (message.startsWith("Портативный профиль сохранён")) {
                title="Портативный профиль"; badge="СОХРАНЁН"; color=LILAC;
            } else if (message.startsWith("В доке ·")) {
                title="Профиль дока"; badge="ПРИМЕНЁН"; color=MINT;
            } else if (message.startsWith("Портативный режим ·")) {
                title="Портативный профиль"; badge="ПРИМЕНЁН"; color=LILAC;
            } else if (message.equals("Автоматика запущена")) {
                title="Автоматика"; badge="ЗАПУЩЕНА"; color=MINT; detail="Запуск фоновой службы";
            } else if (message.startsWith("Автоматика выключена")) {
                title="Автоматика"; badge="ВЫКЛЮЧЕНА";
            } else if (message.startsWith("Настройка применена")) {
                title="Настройка"; badge="ПРИМЕНЕНА"; color=MINT;
            } else if (message.equals("Автозапуск включён")) {
                title="Автозапуск"; badge="ВКЛЮЧЁН"; color=MINT; detail="Возобновление после перезагрузки разрешено";
            } else if (message.equals("Автозапуск выключен")) {
                title="Автозапуск"; badge="ВЫКЛЮЧЕН"; detail="После перезагрузки служба сама не запустится";
            } else if (message.startsWith("Автозапуск пропущен")) {
                title="Автозапуск"; badge="ПРОПУЩЕН";
            } else if (message.startsWith("Вентилятор восстановлен: ")) {
                title="Вентилятор"; badge="ВОССТАНОВЛЕН"; color=MINT; detail=message.substring("Вентилятор восстановлен: ".length());
            } else if (message.startsWith("Обходная зарядка восстановлена: ")) {
                title="Обходная зарядка"; badge="ВОССТАНОВЛЕНА"; color=MINT; detail=message.substring("Обходная зарядка восстановлена: ".length());
            } else if (message.startsWith("Повторная проверка профиля:")) {
                title="Проверка профиля"; badge="ПОВТОР"; color=0xffeed39b; detail=message;
            }
        }
    }
    private int dp(int n) { return Math.round(n*activity.getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout l=new LinearLayout(activity); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private TextView text(String s,int size,int color) { TextView t=new TextView(activity); t.setText(s); t.setTextSize(size); t.setTextColor(color); return t; }
    private GradientDrawable shape(int color,int radius) { GradientDrawable s=new GradientDrawable(); s.setColor(color); s.setCornerRadius(dp(radius)); return s; }
}
