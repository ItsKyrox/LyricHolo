package dev.lyricholo.common;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Small asynchronous LRCLIB client. Does not block Minecraft's client thread. */
public final class LyricsClient {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "LyricHolo-lookup"); t.setDaemon(true); return t; });
    private LyricsClient() {}

    public static void lookup(String title, String artist, Consumer<LrcTrack> success, Consumer<String> failure) {
        WORKER.execute(() -> {
            try {
                String query = "track_name=" + enc(title) + "&artist_name=" + enc(artist);
                HttpRequest req = HttpRequest.newBuilder(URI.create("https://lrclib.net/api/get?" + query))
                    .timeout(Duration.ofSeconds(10)).header("User-Agent", "LyricHolo/1.0 (Fabric client)").GET().build();
                HttpResponse<String> response = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() != 200) throw new IllegalStateException("Lyrics not found (HTTP " + response.statusCode() + ")");
                String json = response.body();
                String synced = stringField(json, "syncedLyrics");
                if (synced == null || synced.isBlank()) throw new IllegalStateException("No time-synced lyrics found for this track");
                success.accept(LrcTrack.parse(synced));
            } catch (Exception e) { failure.accept(e.getMessage() == null ? "Lookup failed" : e.getMessage()); }
        });
    }
    private static String enc(String s) { return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8); }
    // Narrow string-field reader for the service's JSON response; handles escaped strings.
    private static String stringField(String json, String key) {
        String needle = "\"" + key + "\""; int p = json.indexOf(needle); if (p < 0) return null;
        p = json.indexOf(':', p + needle.length()); if (p < 0) return null;
        p++; while (p < json.length() && Character.isWhitespace(json.charAt(p))) p++;
        if (json.startsWith("null", p) || p >= json.length() || json.charAt(p) != '"') return null;
        StringBuilder out = new StringBuilder(); boolean escape = false;
        for (int i = p + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escape) { out.append(switch (c) { case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t'; default -> c; }); escape = false; }
            else if (c == '\\') escape = true;
            else if (c == '"') return out.toString();
            else out.append(c);
        }
        return null;
    }
}
