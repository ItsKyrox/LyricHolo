package dev.lyricholo.common;

import java.util.*;
import java.util.regex.*;

/** Parses standard LRC timestamped lines, including multiple timestamps and [offset:] metadata. */
public final class LrcTrack {
    private static final Pattern TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern OFFSET = Pattern.compile("(?im)^\\[offset:([+-]?\\d+)]");
    private final List<LyricLine> lines;
    private final long fileOffsetMs;

    public record LyricLine(long timeMs, String text) {}

    private LrcTrack(List<LyricLine> lines, long offset) { this.lines = List.copyOf(lines); this.fileOffsetMs = offset; }
    public List<LyricLine> lines() { return lines; }

    public static LrcTrack parse(String lrc) {
        long offset = 0;
        Matcher om = OFFSET.matcher(lrc);
        if (om.find()) try { offset = Long.parseLong(om.group(1)); } catch (NumberFormatException ignored) { }
        List<LyricLine> out = new ArrayList<>();
        for (String row : lrc.split("\\R")) {
            Matcher m = TIME.matcher(row);
            List<Long> times = new ArrayList<>(); int end = 0;
            while (m.find()) {
                long cs = fractionToCentiseconds(m.group(3));
                times.add((Long.parseLong(m.group(1)) * 60_000L) + (Long.parseLong(m.group(2)) * 1_000L) + (cs * 10L));
                end = m.end();
            }
            if (!times.isEmpty()) {
                String text = row.substring(end).replaceAll("\\[[^]]*]", "").trim();
                for (long t : times) out.add(new LyricLine(Math.max(0, t + offset), text));
            }
        }
        out.sort(Comparator.comparingLong(LyricLine::timeMs));
        return new LrcTrack(out, offset);
    }
    private static long fractionToCentiseconds(String fraction) {
        if (fraction == null || fraction.isEmpty()) return 0;
        String normalized = (fraction + "00").substring(0, 2);
        return Long.parseLong(normalized);
    }
    public int activeIndex(long positionMs, long syncOffsetMs) {
        int low = 0, high = lines.size() - 1, result = -1;
        long time = positionMs + syncOffsetMs;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (lines.get(mid).timeMs() <= time) { result = mid; low = mid + 1; } else high = mid - 1;
        }
        return result;
    }
}
