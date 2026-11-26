package local.nova.dock;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** One safe, complete line. Old unstructured lines remain opaque and unchanged. */
final class LogEvent {
    final String time,code,raw;
    final String[] args;
    private LogEvent(String time,String code,String[] args,String raw) { this.time=time; this.code=code; this.args=args; this.raw=raw; }
    static String encode(String time,String code,String...args) {
        StringBuilder line=new StringBuilder(time).append("  @1|").append(code);
        for (String arg:args) line.append('|').append(Base64.getUrlEncoder().withoutPadding().encodeToString((arg==null?"":arg).getBytes(StandardCharsets.UTF_8)));
        return line.toString();
    }
    static LogEvent parse(String line) {
        int marker=line.indexOf("  @1|");
        if (marker<0) return new LogEvent("","legacy",new String[0],line);
        try {
            String[] parts=line.substring(marker+5).split("\\|",-1);
            String[] args=new String[parts.length-1];
            for (int i=1;i<parts.length;i++) args[i-1]=new String(Base64.getUrlDecoder().decode(parts[i]),StandardCharsets.UTF_8);
            return new LogEvent(line.substring(0,marker),parts[0],args,line);
        } catch (IllegalArgumentException e) { return new LogEvent("","legacy",new String[0],line); }
    }
}
