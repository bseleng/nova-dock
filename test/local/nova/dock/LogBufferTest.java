package local.nova.dock;

import java.nio.charset.StandardCharsets;

public final class LogBufferTest {
    private static void check(boolean value,String message) { if (!value) throw new AssertionError(message); }
    private static String repeat(String value,int count) { StringBuilder out=new StringBuilder(); for (int i=0;i<count;i++) out.append(value); return out.toString(); }
    public static void main(String[] args) {
        String legacy="12:34:56  Профиль дока сохранён · Обходная зарядка: Выключена → Включена";
        LogBuffer buffer=new LogBuffer(legacy+"\n\n");
        check(buffer.count()==1 && buffer.records().get(0).equals(legacy),"Migration must preserve the complete legacy record");
        check(buffer.bytes()==(legacy+"\n").getBytes(StandardCharsets.UTF_8).length,"Statistics must count UTF-8 payload bytes, not Java chars or preference XML");
        for (int i=0;i<250;i++) buffer=buffer.append("record-"+i);
        check(buffer.count()==200 && buffer.records().get(0).equals("record-249") && buffer.records().get(199).equals("record-50"),"Count cap must drop whole oldest records");
        String unicode=repeat("я🙂",5000);
        buffer=new LogBuffer("").append("old "+unicode).append("middle "+unicode).append("new "+unicode);
        check(buffer.count()==2 && buffer.bytes()<=65536,"The UTF-8 cap must retain only complete records");
        check(buffer.records().get(0).equals("new "+unicode) && buffer.records().get(1).equals("middle "+unicode),"Multibyte records must never be sliced");
        String before=buffer.data(); buffer=buffer.append(repeat("🙂",17000));
        check(buffer.data().equals(before),"An oversized new record must be discarded whole without deleting existing history");
        LogBuffer boundary=new LogBuffer("").append(repeat("x",LogBuffer.MAX_BYTES-1));
        check(boundary.count()==1 && boundary.bytes()==LogBuffer.MAX_BYTES,"65535 ASCII bytes plus LF must fit the exact 64 KiB boundary");
        check(new LogBuffer("").append(repeat("x",LogBuffer.MAX_BYTES)).count()==0,"One byte above the UTF-8 boundary must be rejected whole");
        String encoded=LogEvent.encode("23:59:59","profile_saved","ru/🙂","line one\nline two","|");
        LogEvent parsed=LogEvent.parse(encoded);
        check(parsed.code.equals("profile_saved") && parsed.time.equals("23:59:59") && parsed.args[1].equals("line one\nline two") && parsed.args[2].equals("|"),"Structured events must round-trip arbitrary details as one complete line");
        check(!encoded.contains("\n") && new LogBuffer("").append(encoded).count()==1,"One event must remain one record");
        check(LogEvent.parse("10:00:00  @1|event|invalid$$").raw.equals("10:00:00  @1|event|invalid$$"),"Unknown or malformed records must retain raw fallback");
        String[][] tokens={{"Выключен","Off"},{"Выключена","Off"},{"Включена","On"},{"Обходная зарядка","Bypass charging"}};
        for (String kind:new String[]{"Профиль дока сохранён","Настройка применена"}) for (String change:new String[]{"Выключена → Включена","Включена → Выключена"}) {
            String raw="12:00:00  "+kind+" · Обходная зарядка: "+change;
            String translated=LegacyText.translate(raw,tokens);
            check(translated.contains(change.startsWith("Выключена")?"Off → On":"On → Off") && !translated.contains("Offа"),"Legacy bypass values must translate as complete feminine tokens in both saved/applied directions");
            check(new LogBuffer(raw).records().get(0).equals(raw),"Presentation translation must not mutate stored legacy data");
        }
        check(new LogBuffer("").count()==0 && new LogBuffer("").bytes()==0,"Clear must leave a genuinely empty log");
        System.out.println("LogBuffer tests passed");
    }
}
