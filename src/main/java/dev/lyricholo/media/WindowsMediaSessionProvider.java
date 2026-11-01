package dev.lyricholo.media;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/** Reads Windows Global System Media Transport Controls directly through WinRT. */
public final class WindowsMediaSessionProvider implements WinRtMediaSession.Listener {
    private static final long EMPTY_SESSION_GRACE_MS = 500;
    private final AtomicReference<MediaTrack> current = new AtomicReference<>();
    private volatile String error = "";
    private volatile boolean running;
    private volatile long missingSinceMs;
    private Thread worker;

    public synchronized void start() {
        if (running || !System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) return;
        running = true;
        worker = new Thread(this::run, "LyricHolo-WinRT-Media");
        worker.setDaemon(true);
        worker.start();
    }

    public MediaTrack poll() { return current.get(); }
    public String error() { return error; }

    public synchronized void stop() {
        running = false;
        if (worker != null) worker.interrupt();
        worker = null;
        current.set(null);
        missingSinceMs = 0;
    }

    @Override public void onTrack(String title, String artist, String album, long durationMs,
                                  long positionMs, boolean playing, double playbackRate,
                                  long sampledAtMs, long sampledAtNanos) {
        missingSinceMs = 0;
        long duration = Math.max(0L, durationMs);
        long position = Math.max(0L, positionMs);
        if (duration > 0) position = Math.min(position, duration);
        current.set(new MediaTrack(title, artist, album, duration, position, playing,
                sampledAtMs, sampledAtNanos,
                Double.isFinite(playbackRate) && playbackRate > 0.0 ? playbackRate : 1.0));
        error = "";
    }

    @Override public void onNothingPlaying() {
        // WinRT can briefly return no snapshot while its async manager or metadata
        // operation is being recreated. Keep the last sample through those gaps so the
        // client does not discard lyrics and start the same lookup again every time.
        long now = System.currentTimeMillis();
        if (current.get() == null) return;
        long since = missingSinceMs;
        if (since == 0) {
            missingSinceMs = now;
        } else if (now - since >= EMPTY_SESSION_GRACE_MS) {
            current.set(null);
        }
    }

    private void run() {
        long retryDelayMs = 250;
        while (running) {
            try {
                WinRtMediaSession.read(this, () -> running);
                if (running) error = "Windows media session stopped unexpectedly";
            } catch (Throwable unavailable) {
                if (!running) break;
                error = "Windows media session unavailable: " + unavailable.getMessage();
            }
            current.set(null);
            missingSinceMs = 0;
            if (!running) break;
            try {
                Thread.sleep(retryDelayMs);
            } catch (InterruptedException stopping) {
                if (!running) break;
            }
            retryDelayMs = Math.min(2_000, retryDelayMs * 2);
        }
        current.set(null);
        running = false;
    }

    @FunctionalInterface
    interface RunningCheck { boolean isRunning(); }

}
