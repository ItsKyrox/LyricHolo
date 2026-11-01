package dev.lyricholo.common;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small, unauthenticated YouTube Music InnerTube client, following the public
 * request flow used by ytmusicapi: search -> next/lyrics browse id -> timed browse.
 * This is an unofficial protocol and may need updates when YouTube changes it.
 */
public final class YouTubeMusicApi {
    private static final String ROOT = "https://music.youtube.com/youtubei/v1/";
    private static final String CLIENT_VERSION = "1." + LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE) + ".01.00";
    private static final String SEARCH_SONGS_PARAMS = "EgWKAQIIAWoMEA4QChADEAQQCRAF";
    private static final Pattern WORDS = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern DURATION = Pattern.compile("(?:(\\d+):)?(\\d{1,2}):(\\d{2})");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();

    private YouTubeMusicApi() {}

    public static LrcTrack fetchTimedLyrics(String title, String artist, String album, long durationMs) throws Exception {
        if (title == null || title.isBlank()) return null;
        String cleanTitle = clean(title);
        String cleanArtist = clean(artist);
        if (cleanTitle.isBlank()) return null;

        JsonObject search = request("search", webContext(), object(
                "query", cleanTitle + (cleanArtist.isBlank() ? "" : " " + cleanArtist),
                "params", SEARCH_SONGS_PARAMS));
        Candidate candidate = chooseCandidate(search, cleanTitle, cleanArtist, durationMs);
        if (candidate == null) return null;

        JsonObject nextBody = object(
                "enablePersistentPlaylistPanel", true,
                "isAudioOnly", true,
                "tunerSettingValue", "AUTOMIX_SETTING_NORMAL",
                "videoId", candidate.videoId(),
                "playlistId", "RDAMVM" + candidate.videoId());
        nextBody.add("watchEndpointMusicSupportedConfigs", JsonParser.parseString(
                "{\"watchEndpointMusicConfig\":{\"hasPersistentPlaylistPanel\":true,\"musicVideoType\":\"MUSIC_VIDEO_TYPE_ATV\"}}"));
        JsonObject next = request("next", webContext(), nextBody);
        String lyricsBrowseId = findLyricsBrowseId(next);
        if (lyricsBrowseId == null || lyricsBrowseId.isBlank()) return null;

        JsonObject timed = request("browse", mobileContext(), object("browseId", lyricsBrowseId));
        return parseTimedLyrics(timed);
    }

    private static JsonObject webContext() {
        JsonObject client = object("clientName", "WEB_REMIX", "clientVersion", CLIENT_VERSION,
                "hl", "en", "gl", "US");
        return object("context", object("client", client, "user", new JsonObject()));
    }

    private static JsonObject mobileContext() {
        JsonObject client = object("clientName", "ANDROID_MUSIC", "clientVersion", "7.21.50",
                "hl", "en", "gl", "US");
        return object("context", object("client", client, "user", new JsonObject()));
    }

    private static JsonObject request(String endpoint, JsonObject context, JsonObject body) throws Exception {
        body.add("context", context.getAsJsonObject("context"));
        String url = ROOT + endpoint + "?alt=json";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(12))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0 Safari/537.36")
                .header("Origin", "https://music.youtube.com")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8)).build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200 || response.body().length() > 3_000_000) return new JsonObject();
        JsonElement parsed = JsonParser.parseString(response.body());
        return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
    }

    private static Candidate chooseCandidate(JsonObject response, String title, String artist, long durationMs) {
        List<Candidate> candidates = new ArrayList<>();
        collectSearchRows(response, title, artist, durationMs, candidates);
        candidates.sort(Comparator.comparingDouble(Candidate::score).reversed());
        return candidates.isEmpty() ? null : candidates.getFirst();
    }

    private static void collectSearchRows(JsonElement node, String title, String artist, long durationMs, List<Candidate> out) {
        if (node == null || node.isJsonNull()) return;
        if (node.isJsonArray()) {
            for (JsonElement child : node.getAsJsonArray()) collectSearchRows(child, title, artist, durationMs, out);
            return;
        }
        if (!node.isJsonObject()) return;
        JsonObject object = node.getAsJsonObject();
        if (object.has("musicResponsiveListItemRenderer") && object.get("musicResponsiveListItemRenderer").isJsonObject()) {
            JsonObject row = object.getAsJsonObject("musicResponsiveListItemRenderer");
            Candidate candidate = parseCandidate(row, title, artist, durationMs);
            if (candidate != null) out.add(candidate);
        }
        for (var entry : object.entrySet()) collectSearchRows(entry.getValue(), title, artist, durationMs, out);
    }

    private static Candidate parseCandidate(JsonObject row, String requestedTitle, String requestedArtist, long durationMs) {
        JsonArray columns = row.has("flexColumns") && row.get("flexColumns").isJsonArray()
                ? row.getAsJsonArray("flexColumns") : new JsonArray();
        if (columns.size() < 2) return null;
        JsonObject first = columns.get(0).isJsonObject()
                ? child(columns.get(0).getAsJsonObject(), "musicResponsiveListItemFlexColumnRenderer") : null;
        JsonObject second = columns.get(1).isJsonObject()
                ? child(columns.get(1).getAsJsonObject(), "musicResponsiveListItemFlexColumnRenderer") : null;
        String foundTitle = first == null ? "" : runsText(first, false);
        String foundArtist = second == null ? "" : runsText(second, true);
        String videoId = findVideoId(row);
        if (videoId.isBlank() || foundTitle.isBlank()) return null;

        double titleScore = Math.max(similarity(requestedTitle, foundTitle),
                similarity(requestedTitle, stripArtist(foundTitle, foundArtist)));
        double artistScore = requestedArtist.isBlank() ? 100 : similarity(requestedArtist, foundArtist);
        // Search metadata often decorates titles with edition names and featured
        // artists. The cleaner below normalizes those before scoring; keep identity
        // checks firm enough to avoid unrelated tracks, but don't reject valid YTM
        // rows because of harmless presentation differences.
        if (titleScore < (requestedArtist.isBlank() ? 88 : 80)
                || (!requestedArtist.isBlank() && artistScore < 58)) return null;

        long candidateDuration = second == null ? 0 : parseDuration(runsText(second, false));
        double score = titleScore * 0.7 + artistScore * 0.3;
        if (durationMs > 0 && candidateDuration > 0) {
            long durationGapMs = Math.abs(candidateDuration - durationMs);
            // Reject obvious wrong versions (live/remix/another song) before they can
            // win on a shared title. YouTube's displayed duration can differ slightly.
            if (durationGapMs > 45_000L) return null;
            score -= Math.min(30, durationGapMs / 1000.0 * 0.35);
        }
        return new Candidate(videoId, score);
    }

    private static String runsText(JsonObject column, boolean artistOnly) {
        try {
            JsonArray runs = column.getAsJsonObject("text").getAsJsonArray("runs");
            StringBuilder text = new StringBuilder();
            for (JsonElement element : runs) {
                String value = element.getAsJsonObject().has("text") ? element.getAsJsonObject().get("text").getAsString() : "";
                if (artistOnly && (value.contains("•") || DURATION.matcher(value.trim()).matches())) break;
                if (!value.isBlank()) text.append(value);
            }
            return text.toString().trim();
        } catch (RuntimeException ignored) { return ""; }
    }

    private static String findVideoId(JsonElement node) {
        if (node != null && node.isJsonObject()) {
            JsonObject row = node.getAsJsonObject();
            String id = stringAt(row, "navigationEndpoint", "watchEndpoint", "videoId");
            if (!id.isBlank()) return id;
            id = stringAt(row, "playlistItemData", "videoId");
            if (!id.isBlank()) return id;
            id = stringAt(row, "overlay", "musicItemThumbnailOverlayRenderer", "content",
                    "musicPlayButtonRenderer", "playNavigationEndpoint", "watchEndpoint", "videoId");
            if (!id.isBlank()) return id;
        }
        if (node == null || node.isJsonNull()) return "";
        if (node.isJsonArray()) {
            for (JsonElement child : node.getAsJsonArray()) {
                String found = findVideoId(child);
                if (!found.isBlank()) return found;
            }
        } else if (node.isJsonObject()) {
            JsonObject object = node.getAsJsonObject();
            if (object.has("watchEndpoint") && object.get("watchEndpoint").isJsonObject()) {
                JsonObject watch = object.getAsJsonObject("watchEndpoint");
                String id = string(watch, "videoId");
                if (!id.isBlank()) return id;
            }
            for (var entry : object.entrySet()) {
                String found = findVideoId(entry.getValue());
                if (!found.isBlank()) return found;
            }
        }
        return "";
    }

    private static String findLyricsBrowseId(JsonElement node) {
        if (node == null || node.isJsonNull()) return null;
        // Follow ytmusicapi's actual watch-next navigation: the lyrics tab lives in
        // the watch-next renderer's tab list. Searching the entire JSON tree can pick
        // up an unrelated browse endpoint from queue/recommendation content.
        JsonObject watchNext = at(node, "contents", "singleColumnMusicWatchNextResultsRenderer",
                "tabbedRenderer", "watchNextTabbedResultsRenderer");
        if (watchNext != null && watchNext.has("tabs") && watchNext.get("tabs").isJsonArray()) {
            for (JsonElement item : watchNext.getAsJsonArray("tabs")) {
                if (!item.isJsonObject()) continue;
                JsonObject tabRenderer = child(item.getAsJsonObject(), "tabRenderer");
                JsonObject endpoint = child(tabRenderer, "endpoint");
                JsonObject browse = child(endpoint, "browseEndpoint");
                JsonObject configs = child(browse, "browseEndpointContextSupportedConfigs");
                JsonObject music = child(configs, "browseEndpointContextMusicConfig");
                if (music != null && "MUSIC_PAGE_TYPE_TRACK_LYRICS".equals(string(music, "pageType"))) {
                    String id = string(browse, "browseId");
                    if (id.startsWith("MPLYt")) return id;
                }
            }
        }
        // Keep a narrow fallback for response variants, still validating the ID.
        if (node.isJsonArray()) {
            for (JsonElement child : node.getAsJsonArray()) {
                String found = findLyricsBrowseId(child);
                if (found != null) return found;
            }
        } else if (node.isJsonObject()) {
            JsonObject object = node.getAsJsonObject();
            if (object.has("browseEndpoint") && object.get("browseEndpoint").isJsonObject()) {
                JsonObject browse = object.getAsJsonObject("browseEndpoint");
                JsonObject configs = browse.has("browseEndpointContextSupportedConfigs")
                        && browse.get("browseEndpointContextSupportedConfigs").isJsonObject()
                        ? browse.getAsJsonObject("browseEndpointContextSupportedConfigs") : new JsonObject();
                JsonObject music = configs.has("browseEndpointContextMusicConfig")
                        && configs.get("browseEndpointContextMusicConfig").isJsonObject()
                        ? configs.getAsJsonObject("browseEndpointContextMusicConfig") : new JsonObject();
                String id = string(browse, "browseId");
                if ("MUSIC_PAGE_TYPE_TRACK_LYRICS".equals(string(music, "pageType")) && id.startsWith("MPLYt")) return id;
            }
            for (var entry : object.entrySet()) {
                String found = findLyricsBrowseId(entry.getValue());
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JsonObject at(JsonElement node, String... path) {
        JsonElement current = node;
        for (String key : path) {
            if (current == null || !current.isJsonObject()) return null;
            current = current.getAsJsonObject().get(key);
        }
        return current != null && current.isJsonObject() ? current.getAsJsonObject() : null;
    }

    private static JsonObject child(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonObject()
                ? object.getAsJsonObject(key) : null;
    }

    private static String stringAt(JsonObject object, String... path) {
        JsonElement current = object;
        for (String key : path) {
            if (current == null || !current.isJsonObject()) return "";
            current = current.getAsJsonObject().get(key);
        }
        return current != null && current.isJsonPrimitive() ? current.getAsString() : "";
    }

    private static LrcTrack parseTimedLyrics(JsonElement node) {
        if (node == null || node.isJsonNull()) return null;
        if (node.isJsonObject()) {
            JsonObject object = node.getAsJsonObject();
            if (object.has("timedLyricsData") && object.get("timedLyricsData").isJsonArray()) {
                List<LrcTrack.LyricLine> lines = new ArrayList<>();
                for (JsonElement item : object.getAsJsonArray("timedLyricsData")) {
                    if (!item.isJsonObject()) continue;
                    JsonObject line = item.getAsJsonObject();
                    if (!line.has("cueRange") || !line.get("cueRange").isJsonObject()) continue;
                    JsonObject cue = line.getAsJsonObject("cueRange");
                    long start = longValue(cue, "startTimeMilliseconds", -1);
                    String text = string(line, "lyricLine").trim();
                    if (start >= 0 && !text.isBlank()) lines.add(new LrcTrack.LyricLine(start, text));
                }
                lines.sort(Comparator.comparingLong(LrcTrack.LyricLine::timeMs));
                return lines.isEmpty() ? null : LrcTrack.fromTimedLines(lines);
            }
            for (var entry : object.entrySet()) {
                LrcTrack found = parseTimedLyrics(entry.getValue());
                if (found != null) return found;
            }
        } else if (node.isJsonArray()) {
            for (JsonElement child : node.getAsJsonArray()) {
                LrcTrack found = parseTimedLyrics(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static long parseDuration(String text) {
        Matcher matcher = DURATION.matcher(text);
        long result = 0;
        while (matcher.find()) {
            long hours = matcher.group(1) == null ? 0 : Long.parseLong(matcher.group(1));
            long minutes = Long.parseLong(matcher.group(2));
            long seconds = Long.parseLong(matcher.group(3));
            result = (hours * 3600 + minutes * 60 + seconds) * 1000;
        }
        return result;
    }

    private static String stripArtist(String title, String artist) {
        if (artist == null || artist.isBlank()) return title;
        String cleanTitle = clean(title), cleanArtist = clean(artist);
        for (String separator : new String[]{" - ", " — ", " – "}) {
            if (cleanTitle.toLowerCase(Locale.ROOT).startsWith((cleanArtist + separator).toLowerCase(Locale.ROOT)))
                return cleanTitle.substring(cleanArtist.length() + separator.length()).trim();
            if (cleanTitle.toLowerCase(Locale.ROOT).endsWith((separator + cleanArtist).toLowerCase(Locale.ROOT)))
                return cleanTitle.substring(0, cleanTitle.length() - cleanArtist.length() - separator.length()).trim();
        }
        return cleanTitle;
    }

    private static String clean(String value) {
        if (value == null) return "";
        return value.replaceAll("(?i)\\s*[-|•]\\s*(?:official audio|official video|lyrics?|youtube music).*?$", "")
                .replaceAll("\\([^)]*\\)|\\[[^]]*]|\\{[^}]*}", " ")
                .replaceAll("(?i)\\s+(?:feat\\.?|ft\\.?|featuring)\\s+.*$", "")
                .replaceAll("\\s+", " ").trim();
    }

    private static double similarity(String a, String b) {
        String leftText = WORDS.matcher(clean(a).toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
        String rightText = WORDS.matcher(clean(b).toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
        if (leftText.isEmpty() || rightText.isEmpty()) return 0;
        if (leftText.equals(rightText)) return 100;
        java.util.Set<String> left = new java.util.HashSet<>(List.of(leftText.split("\\s+")));
        java.util.Set<String> right = new java.util.HashSet<>(List.of(rightText.split("\\s+")));
        left.retainAll(right);
        return 200.0 * left.size() / (leftText.split("\\s+").length + rightText.split("\\s+").length);
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    private static long longValue(JsonObject object, String key, long fallback) {
        try { return object.has(key) ? Long.parseLong(object.get(key).getAsString()) : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static JsonObject object(Object... keyValues) {
        JsonObject object = new JsonObject();
        com.google.gson.Gson gson = new com.google.gson.Gson();
        for (int i = 0; i + 1 < keyValues.length; i += 2) object.add(keyValues[i].toString(), gson.toJsonTree(keyValues[i + 1]));
        return object;
    }

    private record Candidate(String videoId, double score) {}
}
