package dev.lyricholo.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Persistent settings and named, user-managed configuration profiles. */
public final class HoloConfigStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("LyricHolo/Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern VALID_NAME = Pattern.compile("[\\p{L}\\p{N} _.-]{1,32}");
    private static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("lyricholo");
    private static final Path CONFIG_FILE = ROOT.resolve("settings.json");
    private static final Path PROFILES = ROOT.resolve("profiles");

    private HoloConfigStore() {}

    public static void load(HoloConfig target) {
        if (!Files.isRegularFile(CONFIG_FILE)) return;
        try {
            String json = Files.readString(CONFIG_FILE, StandardCharsets.UTF_8);
            JsonObject stored = GSON.fromJson(json, JsonObject.class);
            HoloConfig loaded = GSON.fromJson(json, HoloConfig.class);
            if (loaded != null) copy(loaded, target);
            boolean migrateSmoothFont = stored == null || !stored.has("fontQualityVersion")
                    || target.fontQualityVersion < 1;
            if (migrateSmoothFont) {
                if (target.fontStyle == HoloConfig.FontStyle.DEFAULT) target.fontStyle = HoloConfig.FontStyle.SMOOTH;
                target.fontQualityVersion = 1;
            }
            // Older builds placed "sky" lyrics a short distance overhead. Migrate those
            // saved settings once to the new panorama shell and visible sky defaults.
            boolean legacySkyShell = stored != null && !stored.has("skyShellRadius");
            int skyVersion = stored != null && stored.has("skyAnchorVersion")
                    ? stored.get("skyAnchorVersion").getAsInt() : 0;
            boolean olderSkyPlane = stored != null && skyVersion < 2;
            if (legacySkyShell) {
                target.skyMode = true;
                target.skyShellRadius = 256.0f;
                target.textSizePx = 48;
                target.textStroke = false;
            }
            if (olderSkyPlane) {
                target.skyHeight = 8.0f;
                target.skyAnchorVersion = 2;
            }
            normalize(target);
            if (legacySkyShell || olderSkyPlane || migrateSmoothFont) save(target);
        } catch (Exception ignored) {
            // A damaged config should fall back to the in-code defaults instead of blocking launch.
            // Normalize even after a partial read so malformed values cannot poison rendering.
            normalize(target);
            LOGGER.warn("Could not fully load LyricHolo settings; normalized values are being used", ignored);
        }
    }

    public static void save(HoloConfig config) {
        try {
            Files.createDirectories(ROOT);
            writeAtomically(CONFIG_FILE, GSON.toJson(config));
        } catch (Exception failure) {
            LOGGER.warn("Could not save LyricHolo settings", failure);
        }
    }

    public static void reset(HoloConfig target) {
        copy(new HoloConfig(), target);
        save(target);
    }

    public static void ensureBuiltInProfiles() {
        try {
            Files.createDirectories(PROFILES);
            if (!Files.exists(profilePath("One Word"))) {
                HoloConfig oneWord = new HoloConfig();
                oneWord.wordsPerLine = 1;
                oneWord.oldTextHoldMs = 500;
                saveProfile("One Word", oneWord);
            }
            if (!Files.exists(profilePath("Sky Hologram"))) {
                HoloConfig sky = new HoloConfig();
                sky.skyMode = true;
                sky.skyHeight = 8.0f;
                sky.skyAnchorVersion = 2;
                sky.skyShellRadius = 256.0f;
                sky.offsetZ = -16.0f;
                sky.sideSpread3D = 12.0f;
                sky.billboard = true;
                sky.keepAhead3D = true;
                sky.keepOnScreen3D = true;
                sky.screenSticky3D = false;
                sky.oldTextHoldMs = 500;
                sky.textSizePx = 48;
                sky.color = 0xFFBFA6FF;
                sky.shadow = true;
                sky.textStroke = false;
                sky.strokeOpacity = 220;
                sky.worldBackplate = false;
                sky.textAnimation = HoloConfig.TextAnimation.FADE;
                sky.wordsPerLine = 3;
                sky.wordSyncStyle = HoloConfig.WordSyncStyle.POP_UP;
                saveProfile("Sky Hologram", sky);
            }
            migrateBuiltInProfile("One Word");
            migrateBuiltInProfile("Sky Hologram");
        } catch (Exception ignored) { }
    }

    public static List<String> listProfiles() {
        ensureBuiltInProfiles();
        List<String> names = new ArrayList<>();
        try (var files = Files.list(PROFILES)) {
            files.filter(path -> path.getFileName().toString().endsWith(".json")).forEach(path -> {
                try {
                    JsonObject object = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), JsonObject.class);
                    if (object != null && object.has("name")) names.add(object.get("name").getAsString());
                } catch (Exception ignored) { }
            });
        } catch (Exception ignored) { }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(names);
    }

    public static boolean saveProfile(String name, HoloConfig config) {
        Path path = profilePath(name);
        if (path == null) return false;
        try {
            Files.createDirectories(PROFILES);
            JsonObject profile = new JsonObject();
            profile.addProperty("name", name.trim());
            if (name.trim().equalsIgnoreCase("One Word") || name.trim().equalsIgnoreCase("Sky Hologram"))
                profile.addProperty("builtinVersion", 6);
            profile.add("settings", GSON.toJsonTree(config));
            writeAtomically(path, GSON.toJson(profile));
            return true;
        } catch (Exception failure) {
            LOGGER.warn("Could not save LyricHolo profile '{}'", name, failure);
            return false;
        }
    }

    public static boolean loadProfile(String name, HoloConfig target) {
        Path path = profilePath(name);
        if (path == null || !Files.isRegularFile(path)) return false;
        try {
            JsonObject profile = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), JsonObject.class);
            if (profile == null || !profile.has("settings")) return false;
            HoloConfig loaded = GSON.fromJson(profile.get("settings"), HoloConfig.class);
            if (loaded == null) return false;
            if (!profile.getAsJsonObject("settings").has("fontQualityVersion")
                    && loaded.fontStyle == HoloConfig.FontStyle.DEFAULT)
                loaded.fontStyle = HoloConfig.FontStyle.SMOOTH;
            copy(loaded, target);
            normalize(target);
            save(target);
            return true;
        } catch (Exception ignored) { return false; }
    }

    public static boolean deleteProfile(String name) {
        Path path = profilePath(name);
        if (path == null) return false;
        try { return Files.deleteIfExists(path); }
        catch (Exception ignored) { return false; }
    }

    private static Path profilePath(String name) {
        if (name == null || !VALID_NAME.matcher(name.trim()).matches()) return null;
        String slug = name.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", "-").replaceAll("^-|-$", "");
        if (slug.isBlank() || slug.contains("..")) return null;
        return PROFILES.resolve(slug + ".json");
    }

    /** Upgrade the shipped presets in place while preserving other customized fields. */
    private static void migrateBuiltInProfile(String name) {
        Path path = profilePath(name);
        if (path == null || !Files.isRegularFile(path)) return;
        try {
            JsonObject profile = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), JsonObject.class);
            if (profile == null || profile.get("builtinVersion") != null
                    && profile.get("builtinVersion").getAsInt() >= 6) return;
            JsonObject settings = profile.getAsJsonObject("settings");
            if (settings == null) return;
            if (name.equalsIgnoreCase("One Word")) {
                settings.addProperty("wordsPerLine", 1);
                settings.addProperty("oldTextHoldMs", 500);
                settings.addProperty("skyHeight", 8.0f);
                settings.addProperty("skyAnchorVersion", 2);
                settings.addProperty("lyricsProvider", HoloConfig.LyricsProvider.YOUTUBE_MUSIC.name());
            } else {
                settings.addProperty("skyMode", true);
                settings.addProperty("skyHeight", 8.0f);
                settings.addProperty("skyAnchorVersion", 2);
                settings.addProperty("skyShellRadius", 256.0f);
                settings.addProperty("offsetZ", -16.0f);
                settings.addProperty("sideSpread3D", 12.0f);
                settings.addProperty("textSizePx", 48);
                settings.addProperty("wordsPerLine", 3);
                settings.addProperty("wordSyncStyle", HoloConfig.WordSyncStyle.POP_UP.name());
                settings.addProperty("lyricsProvider", HoloConfig.LyricsProvider.YOUTUBE_MUSIC.name());
                settings.addProperty("oldTextHoldMs", 500);
                settings.addProperty("billboard", true);
                settings.addProperty("screenSticky3D", false);
                settings.addProperty("textStroke", false);
                settings.addProperty("strokeOpacity", 220);
                settings.addProperty("worldBackplate", false);
                settings.addProperty("textAnimation", HoloConfig.TextAnimation.FADE.name());
            }
            JsonObject storedSettings = profile.getAsJsonObject("settings");
            if (storedSettings != null && "DEFAULT".equals(storedSettings.has("fontStyle")
                    ? storedSettings.get("fontStyle").getAsString() : "DEFAULT"))
                storedSettings.addProperty("fontStyle", HoloConfig.FontStyle.SMOOTH.name());
            profile.addProperty("builtinVersion", 6);
            writeAtomically(path, GSON.toJson(profile));
        } catch (Exception ignored) { }
    }

    /** Replace persisted JSON only after the complete new document has reached disk. */
    private static void writeAtomically(Path target, String contents) throws java.io.IOException {
        Path parent = target.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporary, contents, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void normalize(HoloConfig config) {
        config.offsetX = clampFinite(config.offsetX, -20.0f, 20.0f, 0.0f);
        config.offsetY = clampFinite(config.offsetY, -20.0f, 20.0f, 0.2f);
        config.offsetZ = -clampFinite(-config.offsetZ, 1.0f, 20.0f, 2.8f);
        config.anchorFollow3D = clampFinite(config.anchorFollow3D, 0.0f, 1.0f, 0.65f);
        config.sideSpread3D = clampFinite(config.sideSpread3D, 0.0f, 80.0f, 24.0f);
        config.speedLead3D = clampFinite(config.speedLead3D, 0.0f, 1.0f, 0.08f);
        config.syncOffsetMs = Math.max(-30_000L, Math.min(30_000L, config.syncOffsetMs));
        config.oldTextHoldMs = clamp(config.oldTextHoldMs, 0, 10_000);
        config.wordsPerLine = clamp(config.wordsPerLine, 0, 20);
        config.textSizePx = clamp(config.textSizePx, 8, 48);
        config.historyLines = clamp(config.historyLines, 0, 5);
        config.lookaheadLines = clamp(config.lookaheadLines, 0, 5);
        config.skyHeight = clampFinite(config.skyHeight, 0.0f, 88.0f, 8.0f);
        config.skyShellRadius = clampFinite(config.skyShellRadius, 64.0f, 2048.0f, 256.0f);
        config.maxOnScreenAngle = clampFinite(config.maxOnScreenAngle, 5.0f, 80.0f, 34.0f);
        config.intensity = clampFinite(config.intensity, 0.0f, 1.0f, 0.65f);
        config.backplateOpacity = clamp(config.backplateOpacity, 0, 255);
        config.strokeOpacity = clamp(config.strokeOpacity, 0, 255);
        config.color = 0xFF000000 | (config.color & 0xFFFFFF);
        if (config.textAnimation == null) config.textAnimation = HoloConfig.TextAnimation.POP;
        if (config.fontStyle == null) config.fontStyle = HoloConfig.FontStyle.SMOOTH;
        config.fontQualityVersion = 1;
        if (config.uiTheme == null) config.uiTheme = HoloConfig.UiTheme.VIOLET;
        if (config.lyricsProvider == null) config.lyricsProvider = HoloConfig.LyricsProvider.YOUTUBE_MUSIC;
        if (config.wordSyncStyle == null) config.wordSyncStyle = HoloConfig.WordSyncStyle.HIGHLIGHT;
        if (config.anchor == null) config.anchor = HoloConfig.Anchor.PLAYER;
    }

    private static void copy(HoloConfig source, HoloConfig target) {
        for (Field field : HoloConfig.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            try { field.set(target, field.get(source)); }
            catch (IllegalAccessException ignored) { }
        }
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static float clampFinite(float value, float min, float max, float fallback) {
        return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }
}
