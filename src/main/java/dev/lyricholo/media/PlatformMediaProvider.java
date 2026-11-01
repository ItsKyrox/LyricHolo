package dev.lyricholo.media;

import java.util.Locale;

/** Selects the native media-session provider for the current operating system. */
public final class PlatformMediaProvider implements MediaProvider {
    private final MediaProvider delegate;

    public PlatformMediaProvider() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) delegate = new WindowsMediaSessionProvider();
        else if (os.contains("mac")) delegate = new MacOSMediaSessionProvider();
        else delegate = new UnsupportedMediaProvider();
    }

    @Override public void start() { delegate.start(); }
    @Override public MediaTrack poll() { return delegate.poll(); }
    @Override public String error() { return delegate.error(); }
    @Override public void stop() { delegate.stop(); }

    private static final class UnsupportedMediaProvider implements MediaProvider {
        @Override public void start() { }
        @Override public MediaTrack poll() { return null; }
        @Override public String error() { return "Media detection currently supports Windows and macOS"; }
        @Override public void stop() { }
    }
}
