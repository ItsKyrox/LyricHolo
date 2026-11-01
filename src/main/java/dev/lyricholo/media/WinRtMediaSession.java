package dev.lyricholo.media;

import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.ptr.DoubleByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;

import java.util.Locale;
import java.util.regex.Pattern;

/** Minimal COM/WinRT reader for Global System Media Transport Controls sessions. */
final class WinRtMediaSession {
    private static final int RO_INIT_MULTITHREADED = 1;
    private static final int RPC_E_CHANGED_MODE = 0x80010106;
    private static final int STATUS_PLAYING = 4;
    private static final int STATUS_PAUSED = 5;
    private static final long TICKS_PER_MILLISECOND = 10_000L;
    private static final long WINDOWS_EPOCH_TICKS = 116_444_736_000_000_000L;
    private static final Pattern IGNORED_APPS = Pattern.compile("TikTok|Default IME|MSCTFIME|GDI", Pattern.CASE_INSENSITIVE);
    private static final Memory MANAGER_STATICS = guid("2050c4ee-11a0-57de-aed7-c97c70338245");
    private static final Memory ASYNC_INFO = guid("00000036-0000-0000-c000-000000000046");

    interface Listener {
        void onTrack(String title, String artist, String album, long durationMs, long positionMs,
                     boolean playing, double playbackRate, long sampledAtMs, long sampledAtNanos);
        void onNothingPlaying();
    }

    private WinRtMediaSession() {}

    static void read(Listener listener, WindowsMediaSessionProvider.RunningCheck running) throws Exception {
        Combase combase = Native.load("combase", Combase.class);
        int init = combase.RoInitialize(RO_INIT_MULTITHREADED);
        if (init < 0 && init != RPC_E_CHANGED_MODE) throw new IllegalStateException("RoInitialize failed: 0x" + Integer.toHexString(init));
        try {
            while (running.isRunning()) {
                Pointer manager = null;
                try {
                    manager = requestManager(combase, running);
                    if (manager == null) {
                        listener.onNothingPlaying();
                        pause(running, 400);
                        continue;
                    }
                    poll(combase, manager, listener, running);
                } catch (ManagerGone gone) {
                    listener.onNothingPlaying();
                    pause(running, 300);
                } finally {
                    release(manager);
                }
            }
        } finally {
            if (init >= 0) combase.RoUninitialize();
        }
    }

    private static Pointer requestManager(Combase combase, WindowsMediaSessionProvider.RunningCheck running) throws InterruptedException {
        String runtimeClass = "Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager";
        PointerByReference classId = new PointerByReference();
        int hr = combase.WindowsCreateString(new WString(runtimeClass), runtimeClass.length(), classId);
        if (hr < 0) return null;
        PointerByReference factoryOut = new PointerByReference();
        try {
            hr = combase.RoGetActivationFactory(classId.getValue(), MANAGER_STATICS, factoryOut);
        } finally {
            combase.WindowsDeleteString(classId.getValue());
        }
        if (hr < 0 || factoryOut.getValue() == null) return null;
        Pointer factory = factoryOut.getValue();
        try {
            return await(factory, 6, 5_000, running);
        } finally {
            release(factory);
        }
    }

    private static void poll(Combase combase, Pointer manager, Listener listener,
                             WindowsMediaSessionProvider.RunningCheck running) throws Exception {
        while (running.isRunning()) {
            Snapshot snapshot = readActive(combase, manager, running);
            if (snapshot == null) listener.onNothingPlaying();
            else listener.onTrack(snapshot.title, snapshot.artist, snapshot.album,
                    snapshot.durationMs, snapshot.positionMs, snapshot.playing, snapshot.playbackRate,
                    snapshot.sampledAtMs, snapshot.sampledAtNanos);
            pause(running, 50);
        }
    }

    private static Snapshot readActive(Combase combase, Pointer manager,
                                       WindowsMediaSessionProvider.RunningCheck running) throws Exception {
        Pointer selectedSession = null;
        PlaybackInfo selectedPlayback = null;
        Candidate selectedCandidate = null;
        PointerByReference sessionsOut = new PointerByReference();
        int hr = call(manager, 7, sessionsOut);
        if (hr < 0) throw new ManagerGone(hr);
        Pointer sessions = sessionsOut.getValue();
        try {
            if (sessions != null) {
                IntByReference count = new IntByReference();
                if (call(sessions, 7, count) >= 0) {
                    for (int i = 0; i < count.getValue(); i++) {
                        PointerByReference sessionOut = new PointerByReference();
                        if (call(sessions, 6, i, sessionOut) < 0) continue;
                        Pointer session = sessionOut.getValue();
                        if (session == null) continue;
                        String appId = readString(combase, session, 6);
                        if (!appId.isBlank() && IGNORED_APPS.matcher(appId).find()) {
                            release(session);
                            continue;
                        }
                        PlaybackInfo playback = playbackInfo(session);
                        boolean isPlaying = playback.status == STATUS_PLAYING;
                        boolean isPaused = playback.status == STATUS_PAUSED;
                        if (isPlaying) {
                            if (selectedSession != null) release(selectedSession);
                            selectedSession = null;
                            selectedPlayback = null;
                            // A session may claim to be playing while it has no media
                            // metadata (for example, a browser tab during handoff).
                            // Keep scanning; otherwise it masks Spotify/another valid
                            // session and the mod appears to have no lyrics at all.
                            Candidate candidate = readMetadata(combase, session, playback, running);
                            if (candidate != null) {
                                selectedSession = session;
                                selectedPlayback = playback;
                                selectedCandidate = candidate;
                                break;
                            }
                            release(session);
                            continue;
                        }
                        if (isPaused && selectedSession == null) {
                            selectedSession = session;
                            selectedPlayback = playback;
                        } else release(session);
                    }
                }
            }
        } finally {
            release(sessions);
        }
        if (selectedSession == null) {
            PointerByReference currentOut = new PointerByReference();
            if (call(manager, 6, currentOut) >= 0 && currentOut.getValue() != null) {
                Pointer current = currentOut.getValue();
                String appId = readString(combase, current, 6);
                PlaybackInfo playback = playbackInfo(current);
                if ((!appId.isBlank() && IGNORED_APPS.matcher(appId).find())
                        || (playback.status != STATUS_PLAYING && playback.status != STATUS_PAUSED)) {
                    release(current);
                } else {
                    selectedSession = current;
                    selectedPlayback = playback;
                }
            }
        }
        if (selectedSession == null || selectedPlayback == null) return null;
        try {
            Candidate candidate = selectedCandidate != null ? selectedCandidate
                    : readMetadata(combase, selectedSession, selectedPlayback, running);
            return candidate == null ? null : snapshot(candidate);
        } finally {
            release(selectedSession);
        }
    }

    private static PlaybackInfo playbackInfo(Pointer session) {
        int status = 0;
        double rate = 1.0;
        PointerByReference playbackOut = new PointerByReference();
        if (call(session, 9, playbackOut) >= 0 && playbackOut.getValue() != null) {
            Pointer playback = playbackOut.getValue();
            try {
                IntByReference statusOut = new IntByReference();
                if (call(playback, 7, statusOut) >= 0) status = statusOut.getValue();
                rate = readRate(playback);
            } finally { release(playback); }
        }
        return new PlaybackInfo(status, rate);
    }

    private static Candidate readMetadata(Combase combase, Pointer session, PlaybackInfo playback,
                                          WindowsMediaSessionProvider.RunningCheck running) throws InterruptedException {
        Pointer media = await(session, 7, 350, running);
        if (media == null) return null;
        String title, artist, album;
        try {
            title = readString(combase, media, 6).trim();
            artist = readString(combase, media, 9).trim();
            album = readString(combase, media, 10).trim();
        } finally { release(media); }
        if (title.isBlank()) return null;
        return new Candidate(session, playback.status, playback.rate, title, artist, album);
    }

    private static Snapshot snapshot(Candidate candidate) {
        long durationMs = 0;
        long positionMs = 0;
        long sampledAtMs = System.currentTimeMillis();
        long sampledAtNanos = System.nanoTime();
        PointerByReference timelineOut = new PointerByReference();
        if (call(candidate.session, 8, timelineOut) >= 0 && timelineOut.getValue() != null) {
            Pointer timeline = timelineOut.getValue();
            try {
                long start = readTicks(timeline, 6);
                long end = readTicks(timeline, 7);
                long position = readTicks(timeline, 10);
                long updatedTicks = readTicks(timeline, 11);
                // WinRT defines Position as current *at LastUpdatedTime*, not at the
                // instant GetTimelineProperties is queried. Anchor this raw position
                // to that timestamp; the client clock then interpolates it exactly
                // once. Adding the age to position here and again during rendering
                // caused the lyrics to run ahead.
                long updatedUnixMs = (updatedTicks - WINDOWS_EPOCH_TICKS) / TICKS_PER_MILLISECOND;
                sampledAtMs = System.currentTimeMillis();
                long ageMs = updatedUnixMs > 946_684_800_000L
                        ? Math.max(0L, sampledAtMs - updatedUnixMs) : 0L;
                sampledAtNanos = System.nanoTime() - ageMs * 1_000_000L;
                durationMs = Math.max(0, (end - start) / TICKS_PER_MILLISECOND);
                positionMs = Math.max(0, (position - start) / TICKS_PER_MILLISECOND);
                if (durationMs > 0) positionMs = Math.min(positionMs, durationMs);
            } finally { release(timeline); }
        }
        return new Snapshot(candidate.title, candidate.artist, candidate.album,
                durationMs, positionMs, candidate.status == STATUS_PLAYING,
                Double.isFinite(candidate.rate) && candidate.rate > 0.0 ? candidate.rate : 1.0,
                sampledAtMs, sampledAtNanos);
    }

    private static Pointer await(Pointer self, int slot, long timeoutMs,
                                 WindowsMediaSessionProvider.RunningCheck running) throws InterruptedException {
        PointerByReference operationOut = new PointerByReference();
        if (call(self, slot, operationOut) < 0 || operationOut.getValue() == null) return null;
        Pointer operation = operationOut.getValue();
        try {
            PointerByReference infoOut = new PointerByReference();
            if (call(operation, 0, ASYNC_INFO, infoOut) < 0 || infoOut.getValue() == null) return null;
            Pointer info = infoOut.getValue();
            try {
                IntByReference status = new IntByReference();
                long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
                while (running.isRunning() && System.nanoTime() < deadline) {
                    if (call(info, 7, status) < 0) return null;
                    if (status.getValue() == 1) break;
                    if (status.getValue() != 0) return null;
                    Thread.sleep(2);
                }
                if (!running.isRunning() || System.nanoTime() >= deadline) {
                    call(info, 9);
                    return null;
                }
            } finally { release(info); }
            PointerByReference result = new PointerByReference();
            return call(operation, 8, result) >= 0 ? result.getValue() : null;
        } finally { release(operation); }
    }

    private static String readString(Combase combase, Pointer self, int slot) {
        PointerByReference out = new PointerByReference();
        if (call(self, slot, out) < 0 || out.getValue() == null) return "";
        Pointer value = out.getValue();
        try {
            IntByReference length = new IntByReference();
            Pointer chars = combase.WindowsGetStringRawBuffer(value, length);
            return chars == null || length.getValue() <= 0 ? "" : new String(chars.getCharArray(0, length.getValue()));
        } finally { combase.WindowsDeleteString(value); }
    }

    private static long readTicks(Pointer self, int slot) {
        LongByReference value = new LongByReference();
        return call(self, slot, value) >= 0 ? value.getValue() : 0;
    }

    private static double readRate(Pointer playback) {
        PointerByReference out = new PointerByReference();
        if (call(playback, 10, out) < 0 || out.getValue() == null) return 1.0;
        Pointer boxedValue = out.getValue();
        try {
            DoubleByReference rate = new DoubleByReference();
            return call(boxedValue, 6, rate) >= 0 && rate.getValue() > 0 ? rate.getValue() : 1.0;
        } finally { release(boxedValue); }
    }

    private static int call(Pointer self, int slot, Object... args) {
        Pointer method = self.getPointer(0).getPointer((long)slot * Native.POINTER_SIZE);
        Object[] invocation = new Object[args.length + 1];
        invocation[0] = self;
        System.arraycopy(args, 0, invocation, 1, args.length);
        return Function.getFunction(method, Function.ALT_CONVENTION).invokeInt(invocation);
    }

    private static void release(Pointer value) {
        if (value != null) call(value, 2);
    }

    private static Memory guid(String value) {
        String hex = value.replace("-", "");
        Memory memory = new Memory(16);
        memory.setInt(0, (int)Long.parseLong(hex.substring(0, 8), 16));
        memory.setShort(4, (short)Integer.parseInt(hex.substring(8, 12), 16));
        memory.setShort(6, (short)Integer.parseInt(hex.substring(12, 16), 16));
        for (int i = 0; i < 8; i++) memory.setByte(8L + i, (byte)Integer.parseInt(hex.substring(16 + i * 2, 18 + i * 2), 16));
        return memory;
    }

    private static void pause(WindowsMediaSessionProvider.RunningCheck running, long millis) throws InterruptedException {
        if (running.isRunning()) Thread.sleep(millis);
    }

    private record Candidate(Pointer session, int status, double rate, String title, String artist, String album) {
        void release() { WinRtMediaSession.release(session); }
    }

    private record PlaybackInfo(int status, double rate) {}

    private record Snapshot(String title, String artist, String album, long durationMs,
                            long positionMs, boolean playing, double playbackRate,
                            long sampledAtMs, long sampledAtNanos) {}

    private static final class ManagerGone extends Exception {
        ManagerGone(int hresult) { super("0x" + Integer.toHexString(hresult)); }
    }

    private interface Combase extends StdCallLibrary {
        int RoInitialize(int type);
        void RoUninitialize();
        int RoGetActivationFactory(Pointer classId, Pointer interfaceId, PointerByReference factory);
        int WindowsCreateString(WString value, int length, PointerByReference handle);
        int WindowsDeleteString(Pointer handle);
        Pointer WindowsGetStringRawBuffer(Pointer value, IntByReference length);
    }
}
