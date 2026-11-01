package dev.lyricholo.common;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import net.fabricmc.loader.api.FabricLoader;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.concurrent.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Small asynchronous LRCLIB client. Does not block Minecraft's client thread. */
public final class LyricsClient {
    private static final Pattern BRACKETS = Pattern.compile("\\([^)]*\\)|\\[[^]]*]|\\{[^}]*}");
    private static final Pattern VIDEO_NOISE = Pattern.compile("\\b(?:official|lyrics?|lyric video|music video|audio|visualizer|hd|4k|remastered|explicit|mv)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FEATURING = Pattern.compile("\\s+(?:feat\\.?|ft\\.?|featuring|with)\\s+.*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPACES = Pattern.compile("\\s{2,}");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    // A single interruptible worker guarantees that only the newest track lookup is
    // allowed to consume network/CPU. The bounded queue and purge prevent rapid skips
    // from accumulating stale requests behind the active one.
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(1, 1, 0L,
            TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), r -> {
        Thread t = new Thread(r, "LyricHolo-lookup"); t.setDaemon(true); return t;
    });
    private static final Object LOOKUP_LOCK = new Object();
    private static Future<?> activeLookup;
    private static final Map<String, LrcTrack> SUCCESS_CACHE = java.util.Collections.synchronizedMap(
            new LinkedHashMap<>(128, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, LrcTrack> eldest) { return size() > 128; }
            });
    private LyricsClient() {}

    public static void lookup(String title, String artist, Consumer<LrcTrack> success, Consumer<String> failure) {
        lookup(title, artist, "", 0, success, failure);
    }

    public static void lookup(String title, String artist, String album, long durationMs, Consumer<LrcTrack> success, Consumer<String> failure) {
        lookup(HoloConfig.LyricsProvider.LRCLIB, title, artist, album, durationMs, success, failure);
    }

    public static void lookup(HoloConfig.LyricsProvider provider, String title, String artist, String album,
                              long durationMs, Consumer<LrcTrack> success, Consumer<String> failure) {
        if (success == null || failure == null) return;
        if (title == null || title.isBlank()) {
            failure.accept("No active track title was provided");
            return;
        }
        if (provider == null) provider = HoloConfig.LyricsProvider.YOUTUBE_MUSIC;
        HoloConfig.LyricsProvider selectedProvider = provider;
        String memoKey = selectedProvider.name() + "\u0000"
                + normalizeKey(artist) + "\u0000" + normalizeKey(title);
        LrcTrack cached = SUCCESS_CACHE.get(memoKey);
        if (cached != null) {
            cancelActiveLookup();
            success.accept(cached);
            return;
        }
        Runnable lookup = () -> {
            try {
                if (selectedProvider == HoloConfig.LyricsProvider.YOUTUBE_MUSIC) {
                    LrcTrack synced = YouTubeMusicApi.fetchTimedLyrics(title, artist, album, durationMs);
                    if (synced == null || synced.lines().isEmpty())
                        throw new IllegalStateException("YouTube Music did not return timed lyrics for this track");
                    SUCCESS_CACHE.put(memoKey, synced);
                    success.accept(synced);
                    return;
                }
                String cleanTitle = clean(title), cleanArtist = clean(artist);
                cleanTitle = stripArtistPrefix(cleanTitle, cleanArtist);
                String cacheKey = (cleanArtist + "\u0000" + cleanTitle).toLowerCase(java.util.Locale.ROOT);
                Path cache = cachePath(cacheKey);
                String synced = null;
                if (cache != null && Files.isReadable(cache)) {
                    try {
                        String cachedText = Files.readString(cache, StandardCharsets.UTF_8);
                        if (!LrcTrack.parse(cachedText).lines().isEmpty()) synced = cachedText;
                    } catch (Exception ignored) {
                        // Discard stale or damaged cache entries and try the live provider.
                    }
                }
                if (synced == null || synced.isBlank()) synced = fetchSynced(cleanTitle, cleanArtist, album, durationMs);
                if ((synced == null || synced.isBlank()) && cleanTitle.contains(" - ")) {
                    String[] parts = cleanTitle.split("\\s-\\s", 2);
                    synced = fetchSynced(parts[1], cleanArtist, album, durationMs);
                    if ((synced == null || synced.isBlank()) && cleanArtist.isBlank()) synced = fetchSynced(parts[0], "", album, durationMs);
                }
                if (synced == null || synced.isBlank()) throw new IllegalStateException("No time-synced lyrics found for this track");
                LrcTrack parsed = LrcTrack.parse(synced);
                if (parsed.lines().isEmpty()) throw new IllegalStateException("The lyrics source returned no usable timed lines");
                if (cache != null) try {
                    Files.createDirectories(cache.getParent());
                    Files.writeString(cache, synced, StandardCharsets.UTF_8);
                } catch (Exception ignored) { }
                SUCCESS_CACHE.put(memoKey, parsed);
                success.accept(parsed);
            } catch (InterruptedException cancelled) {
                // A track change cancels the old network request so it cannot delay the
                // newest song's lyrics behind a stale lookup.
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                if (!Thread.currentThread().isInterrupted())
                    failure.accept(e.getMessage() == null ? "Lookup failed" : e.getMessage());
            }
        };
        synchronized (LOOKUP_LOCK) {
            if (activeLookup != null) activeLookup.cancel(true);
            WORKER.purge();
            activeLookup = WORKER.submit(lookup);
        }
    }

    private static void cancelActiveLookup() {
        synchronized (LOOKUP_LOCK) {
            if (activeLookup != null) activeLookup.cancel(true);
            WORKER.purge();
            activeLookup = null;
        }
    }

    private static String normalizeKey(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
    }

    private static String clean(String value) {
        if (value == null || value.isBlank()) return "";
        String clean = value.replaceAll("(?i)\\s*(?:[|•-]\\s*)?(?:youtube music|youtube|topic|vevo)\\s*$", "");
        clean = clean.replaceAll("(?i)\\s*[-|•]\\s*(?:provided to youtube by|official audio|official video|official music video).*?$", "");
        clean = BRACKETS.matcher(clean).replaceAll(" ");
        clean = FEATURING.matcher(clean).replaceAll("");
        clean = VIDEO_NOISE.matcher(clean).replaceAll(" ").replace('_', ' ');
        clean = SPACES.matcher(clean).replaceAll(" ").trim();
        return clean.replaceAll("(?i)\\s*[-|•]\\s*$", "").trim();
    }

    private static String stripArtistPrefix(String title, String artist) {
        if (title == null || artist == null || artist.isBlank()) return title;
        String lowerTitle = title.toLowerCase(java.util.Locale.ROOT);
        String lowerArtist = artist.toLowerCase(java.util.Locale.ROOT);
        for (String separator : new String[]{" - ", " — ", " – ", " • ", " · "}) {
            String prefix = lowerArtist + separator;
            if (lowerTitle.startsWith(prefix)) return title.substring(prefix.length()).trim();
            String suffix = separator + lowerArtist;
            if (lowerTitle.endsWith(suffix)) return title.substring(0, title.length() - suffix.length()).trim();
        }
        return title;
    }

    private static String fetchSynced(String title, String artist, String album, long durationMs) throws Exception {
        String result = exact(title, artist, album, durationMs);
        // Browser media sessions often report an edition, remaster, or video duration that
        // differs from the canonical audio track by more than LRCLIB's strict +/-2 seconds.
        // Retry with less restrictive signatures before broadening to search.
        if (result == null && album != null && !album.isBlank()) result = exact(title, artist, album, 0);
        if (result == null) result = exact(title, artist, "", 0);
        if (result != null) return result;
        String best = search(title, artist, durationMs);
        if (best == null && artist != null && !artist.isBlank()) best = search(title, "", durationMs);
        return best;
    }

    private static String exact(String title, String artist, String album, long durationMs) throws Exception {
        StringBuilder url = new StringBuilder("https://lrclib.net/api/get?track_name=").append(enc(title))
                .append("&artist_name=").append(enc(artist));
        if (album != null && !album.isBlank()) url.append("&album_name=").append(enc(album));
        if (durationMs >= 1_000 && durationMs <= 3_600_000) url.append("&duration=").append(Math.round(durationMs / 1000.0));
        return syncedField(get(url.toString()));
    }

    private static String search(String title, String artist, long durationMs) throws Exception {
        String url = "https://lrclib.net/api/search?track_name=" + enc(title);
        if (artist != null && !artist.isBlank()) url += "&artist_name=" + enc(artist);
        JsonElement search = get(url);
        if (search == null || !search.isJsonArray()) return null;
        JsonObject best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (JsonElement item : search.getAsJsonArray()) {
            if (!item.isJsonObject()) continue;
            JsonObject candidate = item.getAsJsonObject();
            String lyrics = syncedField(candidate);
            if (lyrics == null || lyrics.isBlank()) continue;
            String candidateTitle = str(candidate, "trackName");
            String candidateArtist = str(candidate, "artistName");
            double titleMatch = similarity(title, candidateTitle);
            double artistMatch = artist == null || artist.isBlank() ? 100.0 : similarity(artist, candidateArtist);
            // LRCLIB search is fuzzy. Reject a merely top-ranked result unless its identity
            // matches the requested track, especially for short titles.
            if (titleMatch < (artist == null || artist.isBlank() ? 90.0 : 75.0)
                    || (artist != null && !artist.isBlank() && artistMatch < 60.0)) continue;
            double score = titleMatch * 5.0;
            if (artist != null && !artist.isBlank()) score += artistMatch * 2.5;
            if (durationMs > 0 && candidate.has("duration")) {
                double gapSeconds = Math.abs(candidate.get("duration").getAsDouble() - durationMs / 1000.0);
                score -= Math.min(100, gapSeconds * 1.5);
            }
            if (score > bestScore) { bestScore = score; best = candidate; }
        }
        return best == null ? null : syncedField(best);
    }

    private static String str(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    private static double similarity(String first, String second) {
        String a = clean(first).toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        String b = clean(second).toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        if (a.isEmpty() || b.isEmpty()) return 0;
        if (a.equals(b)) return 100;
        java.util.Set<String> left = new java.util.HashSet<>(java.util.List.of(a.split("\\s+")));
        java.util.Set<String> right = new java.util.HashSet<>(java.util.List.of(b.split("\\s+")));
        left.retainAll(right);
        return 200.0 * left.size() / (a.split("\\s+").length + b.split("\\s+").length);
    }

    private static JsonElement get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
            .header("User-Agent", "LyricHolo/1.0 (Fabric client mod)").header("Accept", "application/json").GET().build();
        HttpResponse<String> response = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200 || response.body().length() > 2_000_000) return null;
        return JsonParser.parseString(response.body());
    }
    private static String syncedField(JsonElement json) {
        if (json == null || !json.isJsonObject()) return null;
        JsonObject obj = json.getAsJsonObject();
        return obj.has("syncedLyrics") && !obj.get("syncedLyrics").isJsonNull() ? obj.get("syncedLyrics").getAsString() : null;
    }
    private static Path cachePath(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(("lookup-v2\u0000" + key).getBytes(StandardCharsets.UTF_8));
            String name = java.util.HexFormat.of().formatHex(digest);
            return FabricLoader.getInstance().getConfigDir().resolve("lyricholo").resolve("cache").resolve(name + ".lrc");
        } catch (Exception e) { return null; }
    }
    private static String enc(String s) { return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8); }
}
