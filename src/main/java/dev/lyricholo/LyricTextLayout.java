package dev.lyricholo;

import dev.lyricholo.common.LrcTrack;
import dev.lyricholo.common.HoloConfig;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.util.Identifier;

import java.util.*;

/** Wraps lyric text and caches styled rows so render frames do not rebuild a lyric's object tree. */
final class LyricTextLayout {
    private static final Map<LrcTrack, Map<Integer, LineData>> LINES = new WeakHashMap<>();
    private static final Map<LayoutKey, List<Text>> LAYOUTS = new LinkedHashMap<>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<LayoutKey, List<Text>> eldest) { return size() > 128; }
    };

    static Identifier font(HoloConfig.FontStyle style) {
        if (style == null) return Identifier.of("minecraft", "default");
        return switch (style) {
            case UNIFORM -> Identifier.of("minecraft", "uniform");
            case PACIFICO -> Identifier.of("lyricholo", "pacifico");
            case SMOOTH -> Identifier.of("lyricholo", "smooth");
            default -> Identifier.of("minecraft", "default");
        };
    }

    /** Returns the current display-group index, or -1 when the complete line is shown. */
    static int segmentIndex(LrcTrack track, int lineIndex, long positionMs, long syncOffsetMs,
                            int wordsPerLine, boolean wordSync, HoloConfig.WordSyncStyle syncStyle) {
        int groupSize = groupSize(wordsPerLine, wordSync, syncStyle);
        if (groupSize <= 0) return -1;
        LineData line = line(track, lineIndex);
        if (line.words.length == 0) return -1;
        return activeWord(track, lineIndex, positionMs + syncOffsetMs, line) / groupSize;
    }

    static String wordAt(LrcTrack track, int lineIndex, int wordIndex) {
        LineData line = line(track, lineIndex);
        return wordIndex >= 0 && wordIndex < line.words.length ? line.words[wordIndex] : "";
    }

    static List<Text> wrap(LrcTrack track, int lineIndex, long positionMs, long syncOffsetMs,
                           int maxWidth, TextRenderer renderer, int baseColor, boolean wordSync,
                           int wordsPerLine, HoloConfig.WordSyncStyle syncStyle, Identifier font) {
        LineData line = line(track, lineIndex);
        if (line.words.length == 0) return List.of();
        int timedWord = activeWord(track, lineIndex, positionMs + syncOffsetMs, line);
        int activeWord = wordSync && syncStyle != HoloConfig.WordSyncStyle.POP_UP
                && syncStyle != HoloConfig.WordSyncStyle.STACK ? timedWord : -1;
        int groupSize = groupSize(wordsPerLine, wordSync, syncStyle);
        int visibleStart = wordSync && syncStyle == HoloConfig.WordSyncStyle.STACK
                ? 0 : groupSize > 0 ? timedWord / groupSize * groupSize : 0;
        int visibleEnd = wordSync && syncStyle == HoloConfig.WordSyncStyle.STACK
                ? Math.min(line.words.length, timedWord + 1)
                : groupSize > 0 ? Math.min(line.words.length, visibleStart + groupSize) : line.words.length;
        LayoutKey key = new LayoutKey(line, activeWord, visibleStart, visibleEnd, maxWidth, baseColor, renderer, font);
        List<Text> cached = LAYOUTS.get(key);
        if (cached != null) return cached;

        List<List<Integer>> wrapped = new ArrayList<>();
        List<Integer> row = new ArrayList<>();
        StringBuilder candidate = new StringBuilder();
        for (int i = visibleStart; i < visibleEnd; i++) {
            String next = candidate.isEmpty() ? line.words[i] : candidate + " " + line.words[i];
            if (!row.isEmpty() && renderer.getWidth(next) > maxWidth) {
                wrapped.add(row);
                row = new ArrayList<>();
                candidate.setLength(0);
            }
            if (!candidate.isEmpty()) candidate.append(' ');
            candidate.append(line.words[i]);
            row.add(i);
        }
        if (!row.isEmpty()) wrapped.add(row);

        List<Text> result = new ArrayList<>(wrapped.size());
        for (List<Integer> indexes : wrapped) {
            Text text = Text.literal("");
            for (int n = 0; n < indexes.size(); n++) {
                int word = indexes.get(n);
                if (n > 0) text = text.copy().append(Text.literal(" "));
                int color = baseColor & 0xFFFFFF;
                Text part = Text.literal(line.words[word]).styled(style -> style.withColor(color).withBold(word == activeWord));
                text = text.copy().append(part);
            }
            result.add(text.copy().styled(style -> style.withFont(new StyleSpriteSource.Font(font))));
        }
        List<Text> immutable = List.copyOf(result);
        LAYOUTS.put(key, immutable);
        return immutable;
    }

    private static int groupSize(int configuredWords, boolean wordSync, HoloConfig.WordSyncStyle style) {
        if (wordSync && style == HoloConfig.WordSyncStyle.POP_UP) return configuredWords > 0 ? configuredWords : 1;
        if (wordSync && style == HoloConfig.WordSyncStyle.STACK) return 1;
        return configuredWords;
    }

    private static LineData line(LrcTrack track, int index) {
        return LINES.computeIfAbsent(track, ignored -> new HashMap<>())
                .computeIfAbsent(index, ignored -> new LineData(track, index));
    }

    private static int activeWord(LrcTrack track, int lineIndex, long position, LineData line) {
        int wordCount = line.words.length;
        if (wordCount <= 1) return 0;
        if (line.timingStarts.length > 0) {
            int active = 0;
            for (int i = 0; i < line.timingStarts.length; i++) {
                long boundary = line.timingStarts[i];
                if (i > 0) {
                    long gap = boundary - line.timingStarts[i - 1];
                    // LRC word marks and the Windows media clock are both sampled
                    // discretely. Advance through very short gaps slightly early so
                    // the prior word does not visibly hang while waiting for the next poll.
                    if (gap > 0 && gap <= 200) boundary -= Math.round(gap * 0.75);
                }
                if (boundary > position) break;
                active = Math.max(active, line.timingEnds[i] - 1);
            }
            return Math.min(wordCount - 1, active);
        }
        long start = track.lines().get(lineIndex).timeMs();
        double progress = Math.max(0, Math.min(0.999, (position - start) / (double)line.fallbackDurationMs));
        return Math.min(wordCount - 1, (int)(progress * wordCount));
    }

    private static final class LineData {
        final String[] words;
        final long[] timingStarts;
        final int[] timingEnds;
        final long fallbackDurationMs;

        LineData(LrcTrack track, int index) {
            var lyricLine = track.lines().get(index);
            String text = lyricLine.text().trim();
            words = text.isEmpty() ? new String[0] : text.split("\\s+");
            List<Long> starts = new ArrayList<>();
            List<Integer> ends = new ArrayList<>();
            int cursor = 0;
            for (var timing : lyricLine.wordTimings()) {
                int count = timing.text().trim().split("\\s+").length;
                int end = Math.min(words.length, cursor + count);
                if (end > cursor) {
                    starts.add(timing.timeMs());
                    ends.add(end);
                    cursor = end;
                }
            }
            timingStarts = starts.stream().mapToLong(Long::longValue).toArray();
            timingEnds = ends.stream().mapToInt(Integer::intValue).toArray();
            long duration = 3_200;
            for (int i = index + 1; i < track.lines().size(); i++) {
                long next = track.lines().get(i).timeMs();
                if (next > lyricLine.timeMs()) {
                    duration = Math.max(800, Math.min(6_000, next - lyricLine.timeMs()));
                    break;
                }
            }
            fallbackDurationMs = duration;
        }
    }

    private record LayoutKey(LineData line, int activeWord, int visibleStart, int visibleEnd,
                             int width, int color, TextRenderer renderer, Identifier font) {}

    private LyricTextLayout() {}
}
