package dev.lyricholo.common;

import java.util.*;
import java.util.regex.*;
import java.io.StringReader;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/** Parses standard LRC timestamped lines, including multiple timestamps and [offset:] metadata. */
public final class LrcTrack {
    private static final Pattern TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern WORD_TIME = Pattern.compile("<(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?>");
    private static final Pattern OFFSET = Pattern.compile("(?im)^\\[offset:([+-]?\\d+)]");
    private final List<LyricLine> lines;
    private final long fileOffsetMs;

    public record WordTiming(long timeMs, String text) {}
    public record LyricLine(long timeMs, String text, List<WordTiming> wordTimings) {
        public LyricLine(long timeMs, String text) { this(timeMs, text, List.of()); }
        public LyricLine { wordTimings = List.copyOf(wordTimings); }
    }

    private LrcTrack(List<LyricLine> lines, long offset) { this.lines = List.copyOf(lines); this.fileOffsetMs = offset; }
    public List<LyricLine> lines() { return lines; }

    /** Builds a track from timestamped cues returned by the YouTube Music lyrics endpoint. */
    public static LrcTrack fromTimedLines(List<LyricLine> lines) {
        List<LyricLine> sorted = new ArrayList<>(lines);
        sorted.sort(Comparator.comparingLong(LyricLine::timeMs));
        return new LrcTrack(sorted, 0);
    }

    public static LrcTrack parse(String lrc) {
        long offset = 0;
        Matcher om = OFFSET.matcher(lrc);
        if (om.find()) try { offset = Long.parseLong(om.group(1)); } catch (NumberFormatException ignored) { }
        List<LyricLine> out = new ArrayList<>();
        for (String row : lrc.split("\\R")) {
            Matcher m = TIME.matcher(row);
            List<Long> times = new ArrayList<>(); int end = 0;
            while (m.find()) {
                long fractionMs = fractionToMillis(m.group(3));
                times.add((Long.parseLong(m.group(1)) * 60_000L) + (Long.parseLong(m.group(2)) * 1_000L) + fractionMs);
                end = m.end();
            }
            if (!times.isEmpty()) {
                String rawText = row.substring(end).replaceAll("\\[[^]]*]", "").trim();
                List<WordTiming> timedWords = parseWordTimings(rawText, 0);
                String text = WORD_TIME.matcher(rawText).replaceAll("").trim();
                long firstLineTime = times.get(0);
                long lineOffset = offset;
                for (long t : times) {
                    List<WordTiming> shifted = timedWords.stream()
                            .map(word -> new WordTiming(Math.max(0, t + lineOffset + word.timeMs() - firstLineTime), word.text())).toList();
                    out.add(new LyricLine(Math.max(0, t + offset), text, shifted));
                }
            }
        }
        out.sort(Comparator.comparingLong(LyricLine::timeMs));
        return new LrcTrack(out, offset);
    }

    /** Parses timed-text XML (TTML), including word-level spans when the source supplies them. */
    public static LrcTrack parseTtml(String ttml) {
        if (ttml == null || ttml.isBlank() || ttml.length() > 2_000_000) return new LrcTrack(List.of(), 0);
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
            var document = builder.parse(new InputSource(new StringReader(ttml)));
            NodeList paragraphs = document.getElementsByTagNameNS("*", "p");
            if (paragraphs.getLength() == 0) paragraphs = document.getElementsByTagName("p");
            List<LyricLine> parsed = new ArrayList<>();
            for (int i = 0; i < paragraphs.getLength(); i++) {
                if (!(paragraphs.item(i) instanceof Element paragraph)) continue;
                long start = parseTtmlTime(paragraph.getAttribute("begin"));
                String text = normalizeXmlText(paragraph.getTextContent());
                if (start < 0 || text.isBlank()) continue;
                List<WordTiming> words = new ArrayList<>();
                NodeList spans = paragraph.getElementsByTagNameNS("*", "span");
                if (spans.getLength() == 0) spans = paragraph.getElementsByTagName("span");
                for (int j = 0; j < spans.getLength(); j++) {
                    if (!(spans.item(j) instanceof Element span) || hasNestedSpan(span)) continue;
                    String word = normalizeXmlText(span.getTextContent());
                    long time = parseTtmlTime(span.getAttribute("begin"));
                    if (word.isBlank() || time < 0) continue;
                    // TTML span clocks may be paragraph-relative or document-relative.
                    if (time < start) time += start;
                    words.add(new WordTiming(Math.max(start, time), word));
                }
                if (words.size() > 1 && !text.contains(" ")) {
                    text = String.join(" ", words.stream().map(WordTiming::text).toList());
                }
                parsed.add(new LyricLine(start, text, words));
            }
            parsed.sort(Comparator.comparingLong(LyricLine::timeMs));
            return new LrcTrack(parsed, 0);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid timed-text XML", exception);
        }
    }

    private static boolean hasNestedSpan(Element element) {
        NodeList children = element.getElementsByTagNameNS("*", "span");
        if (children.getLength() == 0) children = element.getElementsByTagName("span");
        return children.getLength() > 0;
    }

    private static String normalizeXmlText(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static long parseTtmlTime(String value) {
        if (value == null || value.isBlank()) return -1;
        String time = value.trim();
        try {
            if (time.contains(":")) {
                String[] fields = time.split(":");
                double seconds;
                if (fields.length == 3) seconds = Double.parseDouble(fields[0]) * 3600
                        + Double.parseDouble(fields[1]) * 60 + Double.parseDouble(fields[2]);
                else if (fields.length == 2) seconds = Double.parseDouble(fields[0]) * 60 + Double.parseDouble(fields[1]);
                else return -1;
                return Math.max(0, Math.round(seconds * 1000));
            }
            Matcher offset = Pattern.compile("^([+-]?\\d+(?:\\.\\d+)?)(ms|h|m|s|f|t)$", Pattern.CASE_INSENSITIVE).matcher(time);
            if (!offset.matches()) return -1;
            double amount = Double.parseDouble(offset.group(1));
            double millis = switch (offset.group(2).toLowerCase(Locale.ROOT)) {
                case "ms" -> amount;
                case "h" -> amount * 3_600_000;
                case "m" -> amount * 60_000;
                case "f" -> amount * (1000.0 / 30.0);
                case "t" -> amount * 1000;
                default -> amount * 1000;
            };
            return Math.max(0, Math.round(millis));
        } catch (NumberFormatException ignored) { return -1; }
    }
    private static long fractionToMillis(String fraction) {
        if (fraction == null || fraction.isEmpty()) return 0;
        return switch (fraction.length()) {
            case 1 -> Long.parseLong(fraction) * 100;
            case 2 -> Long.parseLong(fraction) * 10;
            default -> Long.parseLong(fraction.substring(0, 3));
        };
    }
    private static List<WordTiming> parseWordTimings(String text, long offset) {
        List<WordTiming> words = new ArrayList<>();
        Matcher matcher = WORD_TIME.matcher(text);
        int previousEnd = 0;
        long previousTime = -1;
        while (matcher.find()) {
            if (previousTime >= 0) {
                String part = text.substring(previousEnd, matcher.start()).trim();
                if (!part.isEmpty()) words.add(new WordTiming(Math.max(0, previousTime + offset), part));
            }
            previousTime = Long.parseLong(matcher.group(1)) * 60_000L
                    + Long.parseLong(matcher.group(2)) * 1_000L
                    + fractionToMillis(matcher.group(3));
            previousEnd = matcher.end();
        }
        if (previousTime >= 0) {
            String part = text.substring(previousEnd).trim();
            if (!part.isEmpty()) words.add(new WordTiming(Math.max(0, previousTime + offset), part));
        }
        return words;
    }
    public int activeIndex(long positionMs, long syncOffsetMs) {
        int low = 0, high = lines.size() - 1, result = -1;
        long time = positionMs + syncOffsetMs;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            LyricLine line = lines.get(mid);
            long onset = line.timeMs();
            if (!line.wordTimings().isEmpty()) {
                // Some word-synced files mark the first sung word slightly before
                // the containing line cue. Use that earlier vocal onset so line
                // selection doesn't wait for a late paragraph timestamp. Bound it
                // to avoid malformed word tags pulling a lyric far ahead.
                long firstWord = line.wordTimings().getFirst().timeMs();
                onset = Math.max(line.timeMs() - 350L, Math.min(line.timeMs(), firstWord));
            }
            if (onset <= time) { result = mid; low = mid + 1; } else high = mid - 1;
        }
        return result;
    }
}
