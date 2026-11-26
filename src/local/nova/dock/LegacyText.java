package local.nova.dock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Presentation-only token replacement; the underlying legacy log is never rewritten. */
final class LegacyText {
    static String translate(String text,String[][] translations) {
        List<String[]> entries=new ArrayList<>(Arrays.asList(translations));
        entries.sort(Comparator.comparingInt((String[] entry)->entry[0].length()).reversed());
        for (String[] entry:entries) {
            String token="(?<![\\p{L}\\p{N}_])"+Pattern.quote(entry[0])+"(?![\\p{L}\\p{N}_])";
            text=Pattern.compile(token).matcher(text).replaceAll(Matcher.quoteReplacement(entry[1]));
        }
        return text;
    }
}
