package dev.lyricholo.media;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reads playback metadata from the scriptable Spotify and Music apps on macOS.
 * Apple does not expose a public, system-wide Now Playing API to third-party Java
 * applications, so this provider uses each player's AppleScript dictionary.
 */
public final class MacOSMediaSessionProvider implements MediaProvider {
    private static final long POLL_INTERVAL_MS = 250;
    private static final long EMPTY_SESSION_GRACE_MS = 1_500;
    private static final long READ_ERROR_GRACE_MS = 5_000;
    // Apple Events may be held while macOS waits for the user to approve Automation.
    // Keep the request alive long enough for that permission prompt to be answered.
    private static final long COMMAND_TIMEOUT_MS = 30_000;
    private static final String SEPARATOR = String.valueOf((char) 31);

    private final AtomicReference<MediaTrack> current = new AtomicReference<>();
    private volatile boolean running;
    private volatile String error = "";
    private volatile long missingSinceMs;
    private Thread worker;

    @Override public synchronized void start() {
        if (running || !isMacOS()) return;
        running = true;
        worker = new Thread(this::run, "LyricHolo-macOS-Media");
        worker.setDaemon(true);
        worker.start();
    }

    @Override public MediaTrack poll() { return current.get(); }
    @Override public String error() { return error; }

    @Override public synchronized void stop() {
        running = false;
        if (worker != null) worker.interrupt();
        worker = null;
        current.set(null);
        missingSinceMs = 0;
    }

    private void run() {
        while (running) {
            long sampleStartedNanos = System.nanoTime();
            long sampleStartedMs = System.currentTimeMillis();
            try {
                MediaTrack found = readPlayers(0, 0);
                if (found != null && found.isValid()) {
                    long sampleEndedNanos = System.nanoTime();
                    long sampleNanos = sampleStartedNanos + (sampleEndedNanos - sampleStartedNanos) / 2;
                    long sampleMs = sampleStartedMs + (sampleNanos - sampleStartedNanos) / 1_000_000L;
                    found = new MediaTrack(found.title(), found.artist(), found.album(), found.durationMs(),
                            found.positionMs(), found.playing(), sampleMs, sampleNanos, found.playbackRate());
                    current.set(found);
                    missingSinceMs = 0;
                    error = "";
                } else {
                    onNothingPlaying(sampleStartedMs);
                }
            } catch (InterruptedException stopping) {
                if (!running) break;
            } catch (Exception unavailable) {
                error = "macOS media access failed: " + conciseMessage(unavailable);
                onReadFailure(sampleStartedMs);
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException stopping) {
                if (!running) break;
            }
        }
        current.set(null);
        running = false;
    }

    private MediaTrack readPlayers(long sampledAtMs, long sampledAtNanos) throws IOException, InterruptedException {
        List<String> runningPlayers = new ArrayList<>(2);
        if (isProcessRunning("Spotify")) runningPlayers.add("spotify");
        if (isProcessRunning("Music")) runningPlayers.add("music");
        if (runningPlayers.isEmpty()) {
            error = "Start the Spotify desktop app or Apple Music to enable macOS media detection";
            return null;
        }
        error = "";

        String script = makeScript(runningPlayers);
        Process process = new ProcessBuilder("/usr/bin/osascript", "-e", script)
                .redirectErrorStream(true).start();
        boolean finished;
        try {
            finished = process.waitFor(COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException stopping) {
            process.destroyForcibly();
            throw stopping;
        }
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("AppleScript query timed out");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (process.exitValue() != 0) {
            if (output.toLowerCase(Locale.ROOT).contains("not authorized")
                    || output.toLowerCase(Locale.ROOT).contains("-1743")) {
                throw new IOException("allow Minecraft or its launcher to control Spotify/Music in System Settings → Privacy & Security → Automation");
            }
            throw new IOException(output.isBlank() ? "osascript exited with " + process.exitValue() : output);
        }
        if (output.startsWith("ERROR" + SEPARATOR)) {
            String detail = output.substring(("ERROR" + SEPARATOR).length()).trim();
            if (detail.contains("-1743") || detail.toLowerCase(Locale.ROOT).contains("not authorized")) {
                throw new IOException("allow Minecraft or its launcher to control Spotify/Music in System Settings → Privacy & Security → Automation");
            }
            throw new IOException(detail.isBlank() ? "AppleScript could not read player metadata" : detail);
        }
        return parse(output, sampledAtMs, sampledAtNanos);
    }

    private static String makeScript(List<String> players) {
        StringBuilder script = new StringBuilder("on cleanText(valueToConvert)\n")
                .append("try\nset valueText to valueToConvert as text\n")
                .append("on error\nset valueText to \"\"\nend try\n")
                .append("set AppleScript's text item delimiters to {character id 31, tab, return, linefeed}\n")
                .append("set textParts to every text item of valueText\n")
                .append("set AppleScript's text item delimiters to \" \"\n")
                .append("set valueText to textParts as text\nset AppleScript's text item delimiters to \"\"\nreturn valueText\nend cleanText\n")
                .append("on run\nset failureText to \"\"\n");
        for (String player : players) {
            boolean spotify = player.equals("spotify");
            String appId = spotify ? "com.spotify.client" : "com.apple.Music";
            script.append("try\ntell application id \"").append(appId).append("\"\n")
                    .append("set playbackState to player state as text\n")
                    .append("if playbackState is not \"stopped\" then\n")
                    .append("set currentTrack to current track\n")
                    .append("set trackTitle to my cleanText(name of currentTrack)\n")
                    .append("set trackArtist to my cleanText(artist of currentTrack)\n")
                    .append("set trackAlbum to my cleanText(album of currentTrack)\n")
                    .append(spotify
                            ? "set trackDurationMs to duration of currentTrack\n"
                            : "set trackDurationMs to (duration of currentTrack) * 1000\n")
                    .append("set trackPositionMs to (player position) * 1000\n")
                    .append("set playingFlag to \"\"\nif playbackState is \"playing\" then set playingFlag to \"true\"\n")
                    .append("return \"").append(player).append("\" & (character id 31) & trackTitle & (character id 31) & trackArtist & (character id 31) & trackAlbum & (character id 31) & ((trackDurationMs as integer) as text) & (character id 31) & ((trackPositionMs as integer) as text) & (character id 31) & playingFlag\n")
                    .append("end if\nend tell\non error errorMessage number errorNumber\n")
                    .append("set failureText to (errorMessage as text) & \" (\" & (errorNumber as text) & \")\"\n")
                    .append("end try\n");
        }
        return script.append("if failureText is not \"\" then return \"ERROR\" & (character id 31) & failureText\n")
                .append("return \"\"\nend run").toString();
    }

    private static MediaTrack parse(String output, long sampledAtMs, long sampledAtNanos) {
        if (output == null || output.isBlank()) return null;
        String[] fields = output.split(SEPARATOR, -1);
        if (fields.length < 7) return null;
        String title = fields[1].trim();
        if (title.isEmpty()) return null;
        // AppleScript's number-to-text conversion follows the user's locale (for
        // example, "12,5" in some regions). Emit integer milliseconds and parse
        // them as longs so a decimal separator can never silently pin lyrics at 0.
        long durationMs = parseLong(fields[4]);
        long positionMs = parseLong(fields[5]);
        if (durationMs > 0) positionMs = Math.min(positionMs, durationMs);
        boolean playing = "true".equalsIgnoreCase(fields[6].trim());
        return new MediaTrack(title, fields[2].trim(), fields[3].trim(), durationMs,
                positionMs, playing, sampledAtMs, sampledAtNanos, 1.0);
    }

    private static long parseLong(String value) {
        try { return Math.max(0L, Long.parseLong(value.trim())); }
        catch (RuntimeException ignored) { return 0L; }
    }

    private static boolean isProcessRunning(String name) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("/usr/bin/pgrep", "-x", name)
                .redirectErrorStream(true).start();
        boolean finished = process.waitFor(500, TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            return false;
        }
        return process.exitValue() == 0;
    }

    private void onNothingPlaying(long now) {
        if (current.get() == null) return;
        long since = missingSinceMs;
        if (since == 0) missingSinceMs = now;
        else if (now - since >= EMPTY_SESSION_GRACE_MS) current.set(null);
    }

    /** Keep the last good snapshot through temporary Apple Event/permission hiccups. */
    private void onReadFailure(long now) {
        if (current.get() == null) return;
        long since = missingSinceMs;
        if (since == 0) missingSinceMs = now;
        else if (now - since >= READ_ERROR_GRACE_MS) current.set(null);
    }

    private static String conciseMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return exception.getClass().getSimpleName();
        return message.length() > 180 ? message.substring(0, 180) : message;
    }

    private static boolean isMacOS() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }
}
