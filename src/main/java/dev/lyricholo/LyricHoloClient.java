package dev.lyricholo;

import dev.lyricholo.common.*;
import dev.lyricholo.media.MediaTrack;
import dev.lyricholo.media.PlatformMediaProvider;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.util.math.Vec3d;

public final class LyricHoloClient implements ClientModInitializer {
    public static final HoloConfig CONFIG = new HoloConfig();
    public static final AtomicReference<LrcTrack> TRACK = new AtomicReference<>();
    public static final PlatformMediaProvider MEDIA = new PlatformMediaProvider();
    public static volatile String activeTrackKey = "";
    public static volatile String title = "", artist = "", status = "Waiting for a song to play";
    private static KeyBinding open;
    private static volatile String currentTrackKey = "";
    private static long lookupGeneration;
    private static long lookupRetryAtMs;
    private static boolean lookupPending;
    private static int lookupRetryCount;
    private static final int MAX_AUTOMATIC_LOOKUP_RETRIES = 5;
    private static final long LOOKUP_RETRY_BASE_MS = 15_000L;
    private static final long LOOKUP_RETRY_MAX_MS = 5 * 60_000L;

    @Override public void onInitializeClient() {
        HoloConfigStore.load(CONFIG);
        HoloConfigStore.ensureBuiltInProfiles();
        open = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.lyricholo.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, KeyBinding.Category.MISC));
        MEDIA.start();
        HudElementRegistry.addLast(Identifier.of("lyricholo", "corner_lyrics"),
                (context, tickCounter) -> LyricCornerHud.render(context));
        WorldRenderEvents.BEFORE_ENTITIES.register(LyricWorldRenderer::render);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            MEDIA.stop();
            HoloConfigStore.save(CONFIG);
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (open.wasPressed()) client.setScreen(new HoloScreen());
            updateMedia();
        });
    }

    private static void updateMedia() {
        MediaTrack media = MEDIA.poll();
        if (media == null || !media.isValid()) {
            if (!currentTrackKey.isEmpty()) {
                lookupGeneration++;
                currentTrackKey = "";
                activeTrackKey = "";
                TRACK.set(null);
            }
            lookupPending = false;
            lookupRetryAtMs = 0;
            lookupRetryCount = 0;
            title = ""; artist = "";
            status = MEDIA.error().isBlank() ? "Waiting for Spotify, Apple Music or browser audio" : MEDIA.error();
            return;
        }
        title = media.title(); artist = media.artist();
        String key = media.key();
        if (!key.equals(currentTrackKey)) {
            // Use the current media session immediately; a stability debounce here made
            // every genuine song change wait before looking up its lyrics.
            currentTrackKey = key; activeTrackKey = displayTrackKey(key); ++lookupGeneration; TRACK.set(null); lookupRetryAtMs = 0; lookupPending = false; lookupRetryCount = 0;
            requestLyrics(media);
        } else {
            LrcTrack available = TRACK.get();
            if ((available == null || available.lines().isEmpty()) && !lookupPending
                    && System.currentTimeMillis() >= lookupRetryAtMs) requestLyrics(media);
        }
        LrcTrack loaded = TRACK.get();
        if (loaded != null && !loaded.lines().isEmpty()) status = media.playing() ? "Playing  •  lyrics synced" : "Paused  •  lyrics hold position";
    }

    private static void requestLyrics(MediaTrack media) {
        long generation = lookupGeneration;
        lookupPending = true;
        status = "Finding synced lyrics…";
        LyricsClient.lookup(CONFIG.lyricsProvider, media.title(), media.artist(), media.album(), media.durationMs(), parsed -> MinecraftClient.getInstance().execute(() -> {
            if (generation != lookupGeneration) return;
            lookupPending = false;
            TRACK.set(parsed);
            status = parsed.lines().isEmpty() ? "No synced lyrics found" : "Synced lyrics ready";
            if (parsed.lines().isEmpty()) scheduleLookupRetry();
        }), error -> MinecraftClient.getInstance().execute(() -> {
            if (generation != lookupGeneration) return;
            lookupPending = false;
            status = error;
            scheduleLookupRetry();
        }));
    }

    /** Exponential retry backoff prevents an unavailable provider from being polled every few seconds forever. */
    private static void scheduleLookupRetry() {
        if (lookupRetryCount >= MAX_AUTOMATIC_LOOKUP_RETRIES) {
            lookupRetryAtMs = Long.MAX_VALUE;
            status = "Lyrics lookup paused after repeated misses";
            return;
        }
        long multiplier = 1L << Math.min(lookupRetryCount, 5);
        long delay = Math.min(LOOKUP_RETRY_MAX_MS, LOOKUP_RETRY_BASE_MS * multiplier);
        lookupRetryCount++;
        lookupRetryAtMs = System.currentTimeMillis() + delay;
    }

    /** Re-fetches the current song immediately after the user selects a different lyric source. */
    public static void reloadLyrics() {
        MediaTrack media = MEDIA.poll();
        lookupGeneration++;
        lookupPending = false;
        lookupRetryAtMs = 0;
        lookupRetryCount = 0;
        TRACK.set(null);
        if (media == null || !media.isValid()) return;
        currentTrackKey = media.key();
        activeTrackKey = displayTrackKey(media.key());
        requestLyrics(media);
    }

    private static String displayTrackKey(String trackKey) {
        return trackKey + "\u0000" + CONFIG.lyricsProvider;
    }

    /**
     * Read the newest media-thread snapshot directly. This stays correct when client
     * ticks stall: a pause, seek, or track change during a frame hitch is visible on
     * the first frame after it resumes, rather than extrapolating the old game-tick
     * sample. The small bounded extrapolation bridges the 50 ms native poll interval.
     */
    public static long renderPositionMs() {
        MediaTrack latest = MEDIA.poll();
        if (latest == null || !latest.isValid() || currentTrackKey.isEmpty()
                || !latest.key().equals(currentTrackKey)) return -1_000_000L;
        return estimatePosition(latest, System.nanoTime());
    }

    private static long estimatePosition(MediaTrack media, long nowNanos) {
        long ageNanos = Math.max(0L, nowNanos - media.sampledAtNanos());
        // sampledAtNanos is anchored to WinRT LastUpdatedTime, the instant its
        // Position value represents, so use the full elapsed interval here.
        long ageMs = ageNanos / 1_000_000L;
        double rate = Double.isFinite(media.playbackRate())
                ? Math.max(0.1, Math.min(4.0, media.playbackRate())) : 1.0;
        double interpolated = Math.max(0L, media.positionMs())
                + (media.playing() ? ageMs * rate : 0.0);
        long estimated = interpolated >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.round(interpolated);
        return media.durationMs() > 0 ? Math.min(estimated, media.durationMs()) : estimated;
    }

    public static void clear() { TRACK.set(null); status = "Lyrics hidden"; }
    public static void saveConfig() { HoloConfigStore.save(CONFIG); }
}
