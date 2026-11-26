package local.nova.dock;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Newest first, bounded only at complete UTF-8 record boundaries. No scheduled cleanup. */
final class LogBuffer {
    static final int MAX_RECORDS=200, MAX_BYTES=64*1024;
    private final List<String> records;
    private final String data;
    LogBuffer(String raw) {
        List<String> kept=new ArrayList<>(); int bytes=0;
        if (raw!=null) for (String line:raw.split("\\r?\\n")) {
            if (line.trim().isEmpty()) continue;
            int length=line.getBytes(StandardCharsets.UTF_8).length+1;
            if (kept.size()==MAX_RECORDS) break;
            if (length>MAX_BYTES) continue; // An oversized record is discarded whole.
            if (bytes+length>MAX_BYTES) break;
            kept.add(line); bytes+=length;
        }
        records=Collections.unmodifiableList(kept);
        StringBuilder text=new StringBuilder(); for (String line:kept) text.append(line).append('\n');
        data=text.toString();
    }
    LogBuffer append(String record) {
        if (record==null || record.trim().isEmpty()) return this;
        String complete=record.replace('\r',' ').replace('\n',' ');
        if (complete.getBytes(StandardCharsets.UTF_8).length+1>MAX_BYTES) return this;
        return new LogBuffer(complete+"\n"+data);
    }
    List<String> records() { return records; }
    int count() { return records.size(); }
    int bytes() { return data.getBytes(StandardCharsets.UTF_8).length; }
    String data() { return data; }
}
