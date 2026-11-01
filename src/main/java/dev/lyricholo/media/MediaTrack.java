package dev.lyricholo.media;

import java.util.Locale;

public record MediaTrack(String title, String artist, String album, long durationMs,
                         long positionMs, boolean playing, long sampledAtMs,
                         long sampledAtNanos, double playbackRate) {
    public boolean isValid() { return title != null && !title.isBlank(); }
    public String key() { return keyOf(artist, title); }
    public static String keyOf(String artist, String title) {
        return ((artist == null ? "" : artist.trim()) + "\u0000" + (title == null ? "" : title.trim())).toLowerCase(Locale.ROOT);
    }
}
