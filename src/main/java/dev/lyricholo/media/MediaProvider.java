package dev.lyricholo.media;

/** Platform-specific source of the active desktop media session. */
public interface MediaProvider {
    void start();
    MediaTrack poll();
    String error();
    void stop();
}
