
package com.example.mod.spotify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public final class SyncedLyrics {
    public static final SyncedLyrics EMPTY = new SyncedLyrics(new Line[0]);
    private static final Pattern TIMESTAMP = Pattern.compile("(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?");
    private static final Pattern WORD_TIMESTAMP = Pattern.compile("<(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?>");
    private static final Pattern METADATA = Pattern.compile("[A-Za-z#][A-Za-z0-9_ ]*:.*", 32);
    private static final Pattern OFFSET = Pattern.compile("offset\\s*:\\s*([+-]?\\d{1,7})\\s*");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final long WORD_EARLY_MS = 1000L;
    private final Line[] lines;
    private final long[] times;

    private SyncedLyrics(Line[] lines) {
        this.lines = lines;
        this.times = new long[lines.length];
        for (int i = 0; i < lines.length; ++i) {
            this.times[i] = lines[i].timeMs();
        }
    }

    public static SyncedLyrics parse(String lrc) {
        if (lrc == null || lrc.isBlank()) {
            return EMPTY;
        }
        ArrayList<Line> parsed = new ArrayList<Line>();
        ArrayList<Long> stamps = new ArrayList<Long>(4);
        int offset = 0;
        for (String raw : lrc.split("\r\n|\r|\n")) {
            int close;
            String line = raw.strip();
            if (!line.isEmpty() && line.charAt(0) == '\ufeff') {
                line = line.substring(1).strip();
            }
            stamps.clear();
            int pos = 0;
            while (pos < line.length() && line.charAt(pos) == '[' && (close = line.indexOf(93, pos + 1)) >= 0) {
                String tag = line.substring(pos + 1, close).strip();
                Matcher time = TIMESTAMP.matcher(tag);
                if (time.matches()) {
                    stamps.add(SyncedLyrics.toMillis(time));
                } else {
                    if (!METADATA.matcher(tag).matches()) break;
                    Matcher shift = OFFSET.matcher(tag.toLowerCase(Locale.ROOT));
                    if (shift.matches()) {
                        offset = Integer.parseInt(shift.group(1));
                    }
                }
                for (pos = close + 1; pos < line.length() && line.charAt(pos) == ' '; ++pos) {
                }
            }
            if (stamps.isEmpty()) continue;
            String body = line.substring(pos);
            String text = SyncedLyrics.cleanText(body);
            long first = (Long)Collections.min(stamps);
            List<Long> words = SyncedLyrics.wordTimes(body, text, first);
            Iterator iterator = stamps.iterator();
            while (iterator.hasNext()) {
                long stamp = (Long)iterator.next();
                parsed.add(new Line(stamp, text, SyncedLyrics.shift(words, stamp - first)));
            }
        }
        if (parsed.isEmpty()) {
            return EMPTY;
        }
        Line[] sorted = parsed.toArray(new Line[0]);
        if (offset != 0) {
            for (int i = 0; i < sorted.length; ++i) {
                Line moved = sorted[i];
                sorted[i] = new Line(Math.max(0L, moved.timeMs() - (long)offset), moved.text(), SyncedLyrics.shift(moved.wordTimes(), -offset));
            }
        }
        Arrays.sort(sorted, Comparator.comparingLong(Line::timeMs));
        return new SyncedLyrics(sorted);
    }

    public int indexAt(long timeMs) {
        int low = 0;
        int high = this.times.length;
        while (low < high) {
            int mid = low + high >>> 1;
            if (this.times[mid] <= timeMs) {
                low = mid + 1;
                continue;
            }
            high = mid;
        }
        return low - 1;
    }

    public int firstWithTimeOf(int index) {
        if (index < 0 || index >= this.times.length) {
            return index;
        }
        long time = this.times[index];
        while (index > 0 && this.times[index - 1] == time) {
            --index;
        }
        return index;
    }

    public int lastWithTimeOf(int index) {
        if (index < 0 || index >= this.times.length) {
            return index;
        }
        long time = this.times[index];
        while (index + 1 < this.times.length && this.times[index + 1] == time) {
            ++index;
        }
        return index;
    }

    public Line line(int index) {
        return this.lines[index];
    }

    public int size() {
        return this.lines.length;
    }

    public boolean isEmpty() {
        return this.lines.length == 0;
    }

    public List<String> texts() {
        ArrayList<String> texts = new ArrayList<String>(this.lines.length);
        for (Line line : this.lines) {
            if (line.isGap()) continue;
            texts.add(line.text());
        }
        return List.copyOf(texts);
    }

    private static long toMillis(Matcher time) {
        long minutes = Long.parseLong(time.group(1));
        long seconds = Long.parseLong(time.group(2));
        String fraction = time.group(3);
        long millis = 0L;
        if (fraction != null) {
            millis = Long.parseLong(fraction);
            if (fraction.length() == 1) {
                millis *= 100L;
            } else if (fraction.length() == 2) {
                millis *= 10L;
            }
        }
        return minutes * 60000L + seconds * 1000L + millis;
    }

    private static String cleanText(String text) {
        if (text.indexOf(60) >= 0) {
            text = WORD_TIMESTAMP.matcher(text).replaceAll("");
        }
        return WHITESPACE.matcher(text).replaceAll(" ").strip();
    }

    private static List<Long> wordTimes(String body, String text, long lineTime) {
        if (body.indexOf(60) < 0 || text.isEmpty()) {
            return List.of();
        }
        ArrayList<Long> starts = new ArrayList<Long>();
        Matcher tag = WORD_TIMESTAMP.matcher(body);
        long pending = -1L;
        boolean inWord = false;
        int i = 0;
        while (i < body.length()) {
            char c;
            if (body.charAt(i) == '<' && tag.region(i, body.length()).lookingAt()) {
                i = tag.end();
                boolean syllable = inWord && i < body.length() && body.charAt(i) > ' ';
                if (syllable) continue;
                pending = SyncedLyrics.toMillis(tag);
                continue;
            }
            if ((c = body.charAt(i++)) <= ' ') {
                inWord = false;
                continue;
            }
            if (inWord) continue;
            inWord = true;
            if (pending < 0L) {
                return List.of();
            }
            if (starts.isEmpty() ? pending < lineTime - 1000L : pending < (Long)starts.getLast()) {
                return List.of();
            }
            starts.add(pending);
            pending = -1L;
        }
        int words = 1;
        for (int i2 = 0; i2 < text.length(); ++i2) {
            if (text.charAt(i2) != ' ') continue;
            ++words;
        }
        return starts.size() == words ? starts : List.of();
    }

    private static List<Long> shift(List<Long> times, long by) {
        if (by == 0L || times.isEmpty()) {
            return times;
        }
        ArrayList<Long> moved = new ArrayList<Long>(times.size());
        for (long time : times) {
            moved.add(Math.max(0L, time + by));
        }
        return moved;
    }

    @Environment(value=EnvType.CLIENT)
    public record Line(long timeMs, String text, List<Long> wordTimes) {
        public Line {
            text = text == null ? "" : text;
            wordTimes = wordTimes == null ? List.of() : List.copyOf(wordTimes);
        }

        public boolean isGap() {
            return this.text.isEmpty();
        }
    }
}
