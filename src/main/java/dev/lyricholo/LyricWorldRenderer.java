package dev.lyricholo;

import dev.lyricholo.common.HoloConfig;
import dev.lyricholo.common.LrcTrack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import java.util.concurrent.ThreadLocalRandom;
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3f;

/** Each lyric line spawns beside the camera, then remains at that world position until the next line. */
final class LyricWorldRenderer {
    private static final List<Ghost> GHOSTS = new ArrayList<>();
    private static final List<StackWord> STACK_WORDS = new ArrayList<>();
    private static final float STACK_MAX_WIDTH = 155.0f;
    private static String anchorTrackKey = "";
    private static Object anchorWorld;
    private static Vec3d anchor;
    /** One fixed world Y for all sky lyric spawns in the current world. */
    private static double fixedSkyY = Double.NaN;
    private static Quaternionf fixedOrientation;
    private static int lastLine = Integer.MIN_VALUE;
    private static int lastSegment = Integer.MIN_VALUE;
    private static long animationStartNanos;
    private static long placedSegmentStartMs;
    private static double placedDistance = -1.0;
    private static float placedHeight = Float.NaN;
    private static boolean placedSkyMode;
    private static boolean lastSideRight;
    private static double placedSideJitter;
    private static double placedHeightJitter;
    private static float stickyYaw;
    private static float stickyPitch;
    private static Vec3d lastCameraPos;
    private static List<GhostText> lastDrawnTexts = List.of();
    private static Vec3d lastDrawnAnchor;
    private static Quaternionf lastDrawnOrientation;
    private static float lastDrawnScale;
    private static int lastDrawnBackplate;
    private static int lastDrawnStrokeOpacity;
    private static boolean lastDrawnShadow, lastDrawnStroke;
    private static HoloConfig.TextAnimation lastDrawnAnimation = HoloConfig.TextAnimation.POP;
    private static int lastDrawnLine = Integer.MIN_VALUE, lastDrawnSegment = Integer.MIN_VALUE;

    static void render(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        LrcTrack track = LyricHoloClient.TRACK.get();
        if (mc.world == null || mc.player == null || mc.options.hudHidden || !LyricHoloClient.CONFIG.lyricsEnabled
                || (LyricHoloClient.CONFIG.combatDim && mc.player.hurtTime > 0)) {
            GHOSTS.clear();
            return;
        }

        var worldState = context.worldState();
        var camera = worldState == null ? null : worldState.cameraRenderState;
        if (camera == null || !camera.initialized || camera.pos == null || camera.orientation == null) return;

        long nowNanos = System.nanoTime();
        if (track == null || track.lines().isEmpty()) {
            renderGhosts(context, camera.pos, nowNanos);
            return;
        }
        long position = LyricHoloClient.renderPositionMs();
        int index = track.activeIndex(position, LyricHoloClient.CONFIG.syncOffsetMs);
        if (index < 0 || index >= track.lines().size() || track.lines().get(index).text().isBlank()) {
            renderGhosts(context, camera.pos, nowNanos);
            return;
        }

        boolean changedWorld = anchorWorld != mc.world;
        if (!LyricHoloClient.activeTrackKey.equals(anchorTrackKey) || changedWorld) {
            anchorTrackKey = LyricHoloClient.activeTrackKey;
            anchorWorld = mc.world;
            anchor = null;
            lastLine = Integer.MIN_VALUE;
            lastSegment = Integer.MIN_VALUE;
            lastCameraPos = null;
            STACK_WORDS.clear();
            if (changedWorld) fixedSkyY = Double.NaN;
        }
        double configuredDistance = Math.max(1.0, -LyricHoloClient.CONFIG.offsetZ);
        double distance = configuredDistance + speedLead(mc);
        // Sky text lives on a distant spherical shell around the player, like a point
        // attached to the celestial dome. The anchor is translated with player motion,
        // but never with camera rotation, so looking around reveals it as part of the sky.
        double placementDistance = LyricHoloClient.CONFIG.skyMode
                ? Math.max(LyricHoloClient.CONFIG.skyShellRadius, distance) : distance;
        int segment = LyricTextLayout.segmentIndex(track, index, position,
                LyricHoloClient.CONFIG.syncOffsetMs, LyricHoloClient.CONFIG.wordsPerLine,
                LyricHoloClient.CONFIG.wordSync, LyricHoloClient.CONFIG.wordSyncStyle);
        boolean displayChanged = index != lastLine || segment != lastSegment;
        float anchorHeight = LyricHoloClient.CONFIG.skyMode
                ? LyricHoloClient.CONFIG.skyHeight : LyricHoloClient.CONFIG.offsetY;
        boolean stackMode = LyricHoloClient.CONFIG.wordSync
                && LyricHoloClient.CONFIG.wordSyncStyle == HoloConfig.WordSyncStyle.STACK;
        boolean stackAppend = stackMode && anchor != null && index == lastLine && segment > lastSegment;
        boolean placementChanged = placedDistance != configuredDistance
                || placedHeight != anchorHeight || placedSkyMode != LyricHoloClient.CONFIG.skyMode;
        boolean newAnchor = anchor == null || (displayChanged && !stackAppend);
        boolean placeAnchor = newAnchor || placementChanged;
        if (stackAppend && !placementChanged) {
            // Stack mode grows the current phrase in place; do not create a ghost or
            // move its world anchor just because another synchronized word arrived.
            lastLine = index;
            lastSegment = segment;
            animationStartNanos = nowNanos;
            placedSegmentStartMs = position + LyricHoloClient.CONFIG.syncOffsetMs;
        }
        if (placeAnchor) {
            if (newAnchor) preserveLastText(nowNanos, lastLine, lastSegment);
            if (newAnchor && stackMode) STACK_WORDS.clear();
            lastLine = index;
            lastSegment = segment;
            animationStartNanos = System.nanoTime();
            placedSegmentStartMs = position + LyricHoloClient.CONFIG.syncOffsetMs;
            if (LyricHoloClient.CONFIG.skyMode && Double.isFinite(fixedSkyY)
                    && Float.isFinite(placedHeight) && placedHeight != anchorHeight) {
                fixedSkyY += anchorHeight - placedHeight;
            }
            Vec3d placed = placeAnchor(camera.pos, camera.orientation, placementDistance, anchorHeight,
                    LyricHoloClient.CONFIG.keepOnScreen3D ? maxHorizontalAngle(mc, placementDistance) : Float.POSITIVE_INFINITY,
                    newAnchor);
            if (LyricHoloClient.CONFIG.skyMode) {
                if (!Double.isFinite(fixedSkyY)) fixedSkyY = camera.pos.y + anchorHeight;
                anchor = new Vec3d(placed.x, fixedSkyY, placed.z);
            } else anchor = placed;
            fixedOrientation = new Quaternionf(camera.orientation);
            placedDistance = configuredDistance;
            placedHeight = anchorHeight;
            placedSkyMode = LyricHoloClient.CONFIG.skyMode;
            lastCameraPos = camera.pos;
            captureStickyAngles(camera.pos, camera.orientation);
        }
        double renderDistance = placementDistance;
        double distanceScale = LyricHoloClient.CONFIG.skyMode ? placementDistance / 48.0 * 3.0 : 1.0;
        if (LyricHoloClient.CONFIG.screenSticky3D && !LyricHoloClient.CONFIG.skyMode) {
            // Rebuild the 3D point from its spawn-time screen angle. It moves through
            // world space with the view, while distance and inverse scale compensation
            // keep its projected size steady.
            if (LyricHoloClient.CONFIG.keepOnScreen3D) {
                float maxYaw = maxHorizontalAngle(mc, renderDistance);
                float maxPitch = maxVerticalAngle(mc);
                stickyYaw = Math.max(-maxYaw, Math.min(maxYaw, stickyYaw));
                stickyPitch = Math.max(-maxPitch, Math.min(maxPitch, stickyPitch));
            }
            anchor = anchorAtScreenAngle(camera.pos, camera.orientation, renderDistance);
            distanceScale = renderDistance / Math.max(1.0, configuredDistance);
        } else if (LyricHoloClient.CONFIG.skyMode) {
            // Freeze the spawn Y through jumps and vertical travel; only horizontal
            // camera movement carries the shell anchor along with the player.
            if (lastCameraPos != null) {
                Vec3d movement = camera.pos.subtract(lastCameraPos);
                anchor = anchor.add(movement.x, 0.0, movement.z);
            }
        } else if (LyricHoloClient.CONFIG.keepAhead3D) {
            pushAnchor(camera.pos, horizontalSpeed(mc));
        }
        lastCameraPos = camera.pos;

        renderGhosts(context, camera.pos, nowNanos);

        if (stackMode) {
            renderStackWords(context, mc, camera.pos, track, index, segment, distanceScale, nowNanos);
            return;
        }

        Vec3d cameraPos = camera.pos;
        HoloConfig.TextAnimation animation = LyricHoloClient.CONFIG.textAnimation;
        float progress = Math.min(1.0f, (System.nanoTime() - animationStartNanos) / 420_000_000.0f);
        float eased = 1.0f - (float)Math.pow(1.0f - progress, 3.0);
        boolean wordPop = LyricHoloClient.CONFIG.wordSync
                && LyricHoloClient.CONFIG.wordSyncStyle == HoloConfig.WordSyncStyle.POP_UP;
        boolean popFade = animation == HoloConfig.TextAnimation.POP_FADE;
        float scaleMultiplier = animation == HoloConfig.TextAnimation.POP || wordPop
                ? 0.58f + eased * 0.42f : 1.0f;
        int alpha = animation == HoloConfig.TextAnimation.FADE || popFade
                ? Math.max(0, Math.min(255, (int)(255 * eased))) : 255;

        MatrixStack matrices = context.matrices();
        matrices.push();
        matrices.translate(anchor.x-cameraPos.x, anchor.y-cameraPos.y, anchor.z-cameraPos.z);
        matrices.multiply(LyricHoloClient.CONFIG.billboard ? camera.orientation : fixedOrientation);
        float riseIn = (popFade ? 0.30f : 0.0f) + (wordPop ? 0.14f : 0.0f);
        if (riseIn > 0.0f) matrices.translate(0.0f, -(1.0f - eased) * riseIn, 0.0f);
        // Keep the projection peripheral and unobtrusive: at this scale a full line
        // occupies roughly one block of world space instead of covering the view.
        float configuredTextScale = Math.max(8, Math.min(48, LyricHoloClient.CONFIG.textSizePx)) / 16.0f;
        float scale = 0.025f * configuredTextScale * scaleMultiplier * (float)distanceScale;
        matrices.scale(scale, -scale, scale);

        TextRenderer renderer = mc.textRenderer;
        var rows = LyricTextLayout.wrap(track, index, position, LyricHoloClient.CONFIG.syncOffsetMs,
                155, renderer, LyricHoloClient.CONFIG.color, LyricHoloClient.CONFIG.wordSync,
                LyricHoloClient.CONFIG.wordsPerLine, LyricHoloClient.CONFIG.wordSyncStyle,
                LyricTextLayout.font(LyricHoloClient.CONFIG.fontStyle));
        int typewriterChars = -1;
        if (animation == HoloConfig.TextAnimation.TYPEWRITER) {
            int totalChars = 0;
            for (var row : rows) totalChars += codePointCount(row.getString());
            long linePosition = position + LyricHoloClient.CONFIG.syncOffsetMs;
            long elapsed = Math.max(0L, linePosition - placedSegmentStartMs);
            long revealDuration = typewriterWindowMs(track, index, totalChars);
            typewriterChars = revealDuration <= 0 ? totalChars
                    : Math.min(totalChars, (int)(totalChars * Math.min(1.0, elapsed / (double)revealDuration)));
            // Keep the matrix stack balanced while the first character is still waiting
            // for its reveal time to elapse.
            if (typewriterChars <= 0) {
                matrices.pop();
                return;
            }
        }
        int plateAlpha = Math.max(0, Math.min(255, LyricHoloClient.CONFIG.backplateOpacity));
        // Sky projections are glyph-only; any configured backplate is suppressed here
        // so no opaque card can appear around the words.
        int backplate = LyricHoloClient.CONFIG.worldBackplate && !LyricHoloClient.CONFIG.skyMode
                ? (plateAlpha << 24) | 0x301050 : 0;
        List<GhostText> drawnTexts = new ArrayList<>(rows.size());
        int remainingTypewriterChars = typewriterChars;
        boolean stackWords = LyricHoloClient.CONFIG.wordSync
                && LyricHoloClient.CONFIG.wordSyncStyle == HoloConfig.WordSyncStyle.STACK;
        List<GhostText> textItems = new ArrayList<>();
        if (stackWords) {
            // Keep each word as its own world text item and append it to the right.
            // Starting from a fixed origin keeps earlier words from shifting as the
            // phrase grows, unlike re-centering one combined text object every beat.
            float x = 0.0f;
            for (var row : rows) {
                for (String word : row.getString().trim().split("\\s+")) {
                    if (word.isEmpty()) continue;
                    Text wordText = Text.literal(word).styled(style -> style
                            .withColor(LyricHoloClient.CONFIG.color & 0xFFFFFF)
                            .withFont(new StyleSpriteSource.Font(LyricTextLayout.font(LyricHoloClient.CONFIG.fontStyle))));
                    textItems.add(new GhostText(x, 0.0f, wordText.asOrderedText(), word));
                    x += renderer.getWidth(wordText) + renderer.getWidth(" ");
                }
            }
        } else {
            float firstY = -rows.size() * renderer.fontHeight / 2.0f;
            for (int i = 0; i < rows.size(); i++) {
                var text = rows.get(i);
                textItems.add(new GhostText(-renderer.getWidth(text) / 2.0f,
                        firstY + i * renderer.fontHeight, text.asOrderedText(), text.getString()));
            }
        }
        for (GhostText item : textItems) {
            float x = item.x(), y = item.y();
            OrderedText ordered = item.text();
            if (typewriterChars >= 0) {
                int rowChars = codePointCount(item.plainText());
                int shown = Math.min(remainingTypewriterChars, rowChars);
                remainingTypewriterChars -= shown;
                if (shown <= 0) continue;
                ordered = revealPrefix(ordered, shown);
            }
            drawnTexts.add(new GhostText(x, y, ordered, item.plainText()));
            if (animation == HoloConfig.TextAnimation.GLITCH && LyricHoloClient.CONFIG.glitch && progress < 1.0f) {
                int ghostAlpha = (int)(105 * (1.0f - progress));
                submitText(context, mc, matrices, x - 1.5f, y, ordered, false,
                        TextRenderer.TextLayerType.POLYGON_OFFSET,
                        LightmapTextureManager.MAX_LIGHT_COORDINATE,
                        (ghostAlpha << 24) | 0xFF48F2, 0, 0);
                submitText(context, mc, matrices, x + 1.5f, y, ordered, false,
                        TextRenderer.TextLayerType.POLYGON_OFFSET,
                        LightmapTextureManager.MAX_LIGHT_COORDINATE,
                        (ghostAlpha << 24) | 0x7ACBFF, 0, 0);
            }
            int tint = (alpha << 24) | (LyricHoloClient.CONFIG.color & 0xFFFFFF);
            if (LyricHoloClient.CONFIG.textStroke) {
                int strokeAlpha = Math.max(0, Math.min(255, LyricHoloClient.CONFIG.strokeOpacity));
                int stroke = (strokeAlpha << 24) | (LyricHoloClient.CONFIG.skyMode ? 0x25113D : 0x120B1E);
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    submitText(context, mc, matrices, x + dx * 1.0f, y + dy * 1.0f, ordered, false,
                            TextRenderer.TextLayerType.POLYGON_OFFSET,
                            LightmapTextureManager.MAX_LIGHT_COORDINATE,
                            stroke, 0, 0);
                }
            }
            submitText(context, mc, matrices, x, y, ordered, LyricHoloClient.CONFIG.shadow,
                    TextRenderer.TextLayerType.POLYGON_OFFSET, LightmapTextureManager.MAX_LIGHT_COORDINATE,
                    tint, backplate, 0);
        }
        matrices.pop();
        if (!drawnTexts.isEmpty()) {
            lastDrawnTexts = List.copyOf(drawnTexts);
            lastDrawnAnchor = anchor;
            lastDrawnOrientation = new Quaternionf(LyricHoloClient.CONFIG.billboard ? camera.orientation : fixedOrientation);
            lastDrawnScale = scale;
            lastDrawnBackplate = backplate;
            lastDrawnShadow = LyricHoloClient.CONFIG.shadow;
            lastDrawnStroke = LyricHoloClient.CONFIG.textStroke;
            lastDrawnStrokeOpacity = LyricHoloClient.CONFIG.strokeOpacity;
            lastDrawnAnimation = animation;
            lastDrawnLine = lastLine;
            lastDrawnSegment = lastSegment;
        }
    }

    /** Draw every synced word as its own world-space hologram. Only the newest word animates. */
    private static void renderStackWords(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext context,
                                         MinecraftClient mc, Vec3d cameraPos, LrcTrack track, int lineIndex,
                                         int segment, double distanceScale, long nowNanos) {
        TextRenderer renderer = mc.textRenderer;
        if (anchor == null || renderer == null) return;

        while (STACK_WORDS.size() <= segment) {
            int wordIndex = STACK_WORDS.size();
            String word = LyricTextLayout.wordAt(track, lineIndex, wordIndex);
            if (word.isEmpty()) break;
            Text styled = Text.literal(word).styled(style -> style
                    .withColor(LyricHoloClient.CONFIG.color & 0xFFFFFF)
                    .withFont(new StyleSpriteSource.Font(LyricTextLayout.font(LyricHoloClient.CONFIG.fontStyle))));
            float x = 0.0f;
            float y = 0.0f;
            if (!STACK_WORDS.isEmpty()) {
                StackWord previous = STACK_WORDS.get(STACK_WORDS.size() - 1);
                x = previous.offsetPx() + renderer.getWidth(previous.text()) + renderer.getWidth(" ") + 3.0f;
                y = previous.offsetYpx();
                // Keep prior holograms fixed and start a new row when the phrase grows too wide.
                if (x + renderer.getWidth(styled) > STACK_MAX_WIDTH) {
                    x = 0.0f;
                    y += renderer.fontHeight + 3.0f;
                }
            }
            STACK_WORDS.add(new StackWord(styled, x, y, nowNanos));
        }

        HoloConfig.TextAnimation animation = LyricHoloClient.CONFIG.textAnimation;
        float configuredTextScale = Math.max(8, Math.min(48, LyricHoloClient.CONFIG.textSizePx)) / 16.0f;
        float baseScale = 0.025f * configuredTextScale * (float)distanceScale;
        int plateAlpha = Math.max(0, Math.min(255, LyricHoloClient.CONFIG.backplateOpacity));
        int backplate = LyricHoloClient.CONFIG.worldBackplate && !LyricHoloClient.CONFIG.skyMode
                ? (plateAlpha << 24) | 0x301050 : 0;
        Quaternionf orientation = fixedOrientation == null ? new Quaternionf() : fixedOrientation;
        List<GhostText> drawn = new ArrayList<>(STACK_WORDS.size());

        for (int i = 0; i < STACK_WORDS.size(); i++) {
            StackWord word = STACK_WORDS.get(i);
            float progress = Math.min(1.0f, (nowNanos - word.startedNanos()) / 420_000_000.0f);
            float eased = 1.0f - (float)Math.pow(1.0f - progress, 3.0);
            boolean newest = i == STACK_WORDS.size() - 1;
            boolean popFade = animation == HoloConfig.TextAnimation.POP_FADE;
            float scaleMultiplier = newest && animation == HoloConfig.TextAnimation.POP
                    ? 0.58f + eased * 0.42f : 1.0f;
            int alpha = newest && (animation == HoloConfig.TextAnimation.FADE || popFade)
                    ? Math.max(0, Math.min(255, (int)(255 * eased))) : 255;
            String value = word.text().getString();
            OrderedText ordered = word.text().asOrderedText();
            if (newest && animation == HoloConfig.TextAnimation.TYPEWRITER) {
                int count = codePointCount(value);
                int shown = Math.min(count, (int)(count * Math.min(1.0f, progress)));
                if (shown <= 0) continue;
                ordered = revealPrefix(ordered, shown);
            }

            MatrixStack matrices = context.matrices();
            matrices.push();
            matrices.translate(anchor.x-cameraPos.x, anchor.y-cameraPos.y, anchor.z-cameraPos.z);
            matrices.multiply(orientation);
            if (newest && popFade) matrices.translate(0.0f, -(1.0f - eased) * 0.30f, 0.0f);
            matrices.scale(baseScale * scaleMultiplier, -baseScale * scaleMultiplier, baseScale * scaleMultiplier);
            matrices.translate(word.offsetPx(), word.offsetYpx(), 0.0f);
            float y = -renderer.fontHeight / 2.0f;

            if (newest && animation == HoloConfig.TextAnimation.GLITCH && LyricHoloClient.CONFIG.glitch && progress < 1.0f) {
                int glitchAlpha = (int)(105 * (1.0f - progress));
                submitText(context, mc, matrices, 1.5f, y, ordered, false,
                        TextRenderer.TextLayerType.POLYGON_OFFSET,
                        LightmapTextureManager.MAX_LIGHT_COORDINATE, (glitchAlpha << 24) | 0xFF48F2, 0, 0);
                submitText(context, mc, matrices, -1.5f, y, ordered, false,
                        TextRenderer.TextLayerType.POLYGON_OFFSET,
                        LightmapTextureManager.MAX_LIGHT_COORDINATE, (glitchAlpha << 24) | 0x7ACBFF, 0, 0);
            }
            if (LyricHoloClient.CONFIG.textStroke) {
                int strokeAlpha = Math.max(0, Math.min(255, LyricHoloClient.CONFIG.strokeOpacity));
                int stroke = (strokeAlpha << 24) | (LyricHoloClient.CONFIG.skyMode ? 0x25113D : 0x120B1E);
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    submitText(context, mc, matrices, dx, y + dy, ordered, false,
                            TextRenderer.TextLayerType.POLYGON_OFFSET,
                            LightmapTextureManager.MAX_LIGHT_COORDINATE, stroke, 0, 0);
                }
            }
            int tint = (alpha << 24) | (LyricHoloClient.CONFIG.color & 0xFFFFFF);
            submitText(context, mc, matrices, 0.0f, y, ordered, LyricHoloClient.CONFIG.shadow,
                    TextRenderer.TextLayerType.POLYGON_OFFSET, LightmapTextureManager.MAX_LIGHT_COORDINATE, tint, backplate, 0);
            matrices.pop();
            drawn.add(new GhostText(word.offsetPx(), word.offsetYpx() + y, word.text().asOrderedText(), value));
        }

        lastDrawnTexts = List.copyOf(drawn);
        lastDrawnAnchor = anchor;
        lastDrawnOrientation = new Quaternionf(orientation);
        lastDrawnScale = baseScale;
        lastDrawnBackplate = backplate;
        lastDrawnShadow = LyricHoloClient.CONFIG.shadow;
        lastDrawnStroke = LyricHoloClient.CONFIG.textStroke;
        lastDrawnStrokeOpacity = LyricHoloClient.CONFIG.strokeOpacity;
        lastDrawnAnimation = animation;
        lastDrawnLine = lineIndex;
        lastDrawnSegment = segment;
    }

    private static void preserveLastText(long nowNanos, int previousLine, int previousSegment) {
        int durationMs = Math.max(0, Math.min(10_000, LyricHoloClient.CONFIG.oldTextHoldMs));
        if (durationMs == 0 || lastDrawnTexts.isEmpty() || lastDrawnAnchor == null
                || lastDrawnLine != previousLine || lastDrawnSegment != previousSegment) return;
        long durationNanos = durationMs * 1_000_000L;
        GHOSTS.add(new Ghost(lastDrawnTexts, lastDrawnAnchor, lastDrawnOrientation, lastDrawnScale,
                lastDrawnBackplate, lastDrawnShadow, lastDrawnStroke, lastDrawnStrokeOpacity,
                !LyricHoloClient.CONFIG.skyMode, lastDrawnAnimation,
                nowNanos, nowNanos + durationNanos, durationNanos));
        // Keep memory and render submissions bounded if tracks advance unusually quickly.
        while (GHOSTS.size() > 24) GHOSTS.remove(0);
    }

    private static void renderGhosts(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext context,
                                     Vec3d cameraPos, long nowNanos) {
        for (int i = GHOSTS.size() - 1; i >= 0; i--) {
            Ghost ghost = GHOSTS.get(i);
            if (nowNanos >= ghost.expiresAtNanos()) {
                GHOSTS.remove(i);
                continue;
            }
            double remaining = (ghost.expiresAtNanos() - nowNanos) / (double)ghost.durationNanos();
            boolean popFade = ghost.animation() == HoloConfig.TextAnimation.POP_FADE;
            double fade = popFade ? remaining : Math.min(1.0, remaining / 0.28);
            int alpha = (int)Math.round((popFade ? 255 : 190) * fade);
            MatrixStack matrices = context.matrices();
            matrices.push();
            matrices.translate(ghost.anchor().x-cameraPos.x, ghost.anchor().y-cameraPos.y, ghost.anchor().z-cameraPos.z);
            matrices.multiply(ghost.orientation());
            if (popFade) matrices.translate(0.0f, (float)((1.0 - remaining) * 0.30), 0.0f);
            matrices.scale(ghost.scale(), -ghost.scale(), ghost.scale());
            int plateAlpha = (int)Math.round(((ghost.backplate() >>> 24) & 255) * fade);
            int plate = (plateAlpha << 24) | (ghost.backplate() & 0xFFFFFF);
            for (GhostText text : ghost.texts()) {
                if (ghost.stroke()) {
                    int outlineAlpha = (int)Math.round(ghost.strokeOpacity() * fade);
                    int outline = (outlineAlpha << 24) | 0x25113D;
                    for (int dx=-1; dx<=1; dx++) for (int dy=-1; dy<=1; dy++) {
                        if (dx == 0 && dy == 0) continue;
                        submitText(context, MinecraftClient.getInstance(), matrices,
                                text.x()+dx*1.0f, text.y()+dy*1.0f, text.text(), false,
                                TextRenderer.TextLayerType.POLYGON_OFFSET,
                                LightmapTextureManager.MAX_LIGHT_COORDINATE,
                                outline, 0, 0);
                    }
                }
                int tint = (alpha << 24) | (LyricHoloClient.CONFIG.color & 0xFFFFFF);
                submitText(context, MinecraftClient.getInstance(), matrices, text.x(), text.y(), text.text(), ghost.shadow(),
                        TextRenderer.TextLayerType.POLYGON_OFFSET, LightmapTextureManager.MAX_LIGHT_COORDINATE,
                        tint, plate, 0);
            }
            matrices.pop();
        }
    }

    /** Sends sky glyphs through a fog-free pipeline while preserving the world's depth buffer. */
    private static void submitText(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext context,
                                   MinecraftClient mc, MatrixStack matrices, float x, float y, OrderedText text,
                                   boolean shadow, TextRenderer.TextLayerType layer, int light,
                                   int color, int background, int outline) {
        if (LyricHoloClient.CONFIG.skyMode) {
            SkyTextRenderer.draw(mc.textRenderer, text, x, y, color, shadow,
                    matrices, context.consumers(), background, light);
        } else {
            context.commandQueue().submitText(matrices, x, y, text, shadow,
                    layer, light, color, background, outline);
        }
    }

    private record GhostText(float x, float y, OrderedText text, String plainText) {}
    private record StackWord(Text text, float offsetPx, float offsetYpx, long startedNanos) {}
    private record Ghost(List<GhostText> texts, Vec3d anchor, Quaternionf orientation, float scale,
                         int backplate, boolean shadow, boolean stroke, int strokeOpacity,
                         boolean seeThrough, HoloConfig.TextAnimation animation,
                         long createdAtNanos, long expiresAtNanos, long durationNanos) {}

    private static Vec3d placeAnchor(Vec3d cameraPos, Quaternionf orientation, double distance, double height,
                                     float safeHorizontalAngle, boolean newLine) {
        Vector3f forward = orientation.transform(new Vector3f(0.0f, 0.0f, -1.0f));
        Vector3f right = orientation.transform(new Vector3f(1.0f, 0.0f, 0.0f));
        if (LyricHoloClient.CONFIG.skyMode) {
            // A sky shell is laid out on a fixed horizontal world plane. Use camera yaw
            // for its horizontal direction so camera pitch cannot lift the anchor overhead.
            forward.y = 0.0f;
            right.y = 0.0f;
            if (forward.lengthSquared() > 1.0e-6f) forward.normalize();
            if (right.lengthSquared() > 1.0e-6f) right.normalize();
        }
        Vector3f up = LyricHoloClient.CONFIG.skyMode
                ? new Vector3f(0.0f, 1.0f, 0.0f)
                : orientation.transform(new Vector3f(0.0f, 1.0f, 0.0f));
        if (newLine) {
            lastSideRight = !lastSideRight;
            placedSideJitter = Math.toRadians(ThreadLocalRandom.current().nextDouble(-3.0, 3.0));
            placedHeightJitter = LyricHoloClient.CONFIG.skyMode ? 0.0
                    : ThreadLocalRandom.current().nextDouble(-0.12, 0.12);
        }
        double spread = Math.toRadians(Math.max(0.0, LyricHoloClient.CONFIG.sideSpread3D));
        double sideAngle = (lastSideRight ? spread : -spread) + placedSideJitter;
        if (Float.isFinite(safeHorizontalAngle)) {
            sideAngle = Math.max(-safeHorizontalAngle, Math.min(safeHorizontalAngle, sideAngle));
        }
        double sideDistance = Math.tan(sideAngle) * distance + LyricHoloClient.CONFIG.offsetX;
        double heightOffset = height + placedHeightJitter;
        Vector3f offset = new Vector3f(
                (float)(forward.x * distance + right.x * sideDistance + up.x * heightOffset),
                (float)(forward.y * distance + right.y * sideDistance + up.y * heightOffset),
                (float)(forward.z * distance + right.z * sideDistance + up.z * heightOffset));
        if (LyricHoloClient.CONFIG.skyMode) offset.y = 0.0f;
        return cameraPos.add(offset.x, offset.y, offset.z);
    }

    /** Cache the spawn angle relative to the view for the screen-sticky 3D mode. */
    private static void captureStickyAngles(Vec3d cameraPos, Quaternionf orientation) {
        Vector3f local = new Quaternionf(orientation).conjugate().transform(new Vector3f(
                (float)(anchor.x - cameraPos.x), (float)(anchor.y - cameraPos.y), (float)(anchor.z - cameraPos.z)));
        float depth = Math.max(0.5f, -local.z);
        stickyYaw = (float)Math.atan2(local.x, depth);
        stickyPitch = (float)Math.atan2(local.y, depth);
    }

    private static Vec3d anchorAtScreenAngle(Vec3d cameraPos, Quaternionf orientation, double distance) {
        float safeDepth = (float)Math.max(0.5, distance);
        Vector3f local = new Vector3f((float)Math.tan(stickyYaw) * safeDepth,
                (float)Math.tan(stickyPitch) * safeDepth, -safeDepth);
        orientation.transform(local);
        return cameraPos.add(local.x, local.y, local.z);
    }

    private static OrderedText revealPrefix(OrderedText text, int characters) {
        return visitor -> {
            int[] seen = {0};
            return text.accept((index, style, codePoint) -> {
                if (seen[0] >= characters) return false;
                seen[0]++;
                return visitor.accept(index, style, codePoint);
            });
        };
    }

    private static int codePointCount(String text) {
        return text.codePointCount(0, text.length());
    }

    private static long typewriterWindowMs(LrcTrack track, int index, int characters) {
        if (characters <= 0) return 0L;
        long start = track.lines().get(index).timeMs();
        long lineDuration = 3_200L;
        for (int i = index + 1; i < track.lines().size(); i++) {
            long next = track.lines().get(i).timeMs();
            if (next > start) {
                lineDuration = Math.max(300L, Math.min(8_000L, next - start));
                break;
            }
        }
        return Math.max(120L, Math.min(characters * 35L, Math.round(lineDuration * 0.45)));
    }

    /** Leave the anchor fixed through normal movement; drag it only above 7 blocks/sec. */
    private static void pushAnchor(Vec3d cameraPos, double speed) {
        if (anchor == null || lastCameraPos == null || LyricHoloClient.CONFIG.anchorFollow3D <= 0.0f) return;
        double follow = Math.min(1.0, LyricHoloClient.CONFIG.anchorFollow3D) * followStrength(speed);
        if (follow > 0.0) anchor = anchor.add(cameraPos.subtract(lastCameraPos).multiply(follow));
    }

    private static double followStrength(double speed) {
        if (speed <= 7.0) return 0.0;
        return Math.min(1.0, (speed - 7.0) / 13.0);
    }

    private static float maxHorizontalAngle(MinecraftClient client, double distance) {
        double fov = client.options.getFov().getValue();
        double aspect = client.getWindow().getFramebufferHeight() <= 0 ? 1.0
                : client.getWindow().getFramebufferWidth() / (double)client.getWindow().getFramebufferHeight();
        double halfVertical = Math.toRadians(fov) * 0.5;
        double halfHorizontal = Math.atan(Math.tan(halfVertical) * aspect);
        double halfTextWidth = 155.0 * 0.5 * 0.025;
        double textHalfAngle = Math.atan(halfTextWidth / Math.max(1.0, distance));
        double frustumSafe = Math.max(0.0, halfHorizontal * 0.9 - textHalfAngle);
        double configured = Math.toRadians(Math.max(0.0, Math.min(80.0, LyricHoloClient.CONFIG.maxOnScreenAngle)));
        return (float)Math.min(configured, frustumSafe);
    }

    private static float maxVerticalAngle(MinecraftClient client) {
        double fov = client.options.getFov().getValue();
        double frustumSafe = Math.max(Math.toRadians(5.0), Math.toRadians(fov) * 0.5 - Math.toRadians(4.0));
        double configured = Math.toRadians(Math.max(5.0, Math.min(80.0, LyricHoloClient.CONFIG.maxOnScreenAngle)));
        return (float)Math.min(configured, frustumSafe);
    }

    private static double horizontalSpeed(MinecraftClient client) {
        if (client.player == null) return 0.0;
        Vec3d velocity = client.player.getVelocity();
        return Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z) * 20.0;
    }

    private static double speedLead(MinecraftClient client) {
        if (LyricHoloClient.CONFIG.speedLead3D <= 0.0) return 0.0;
        return Math.min(40.0, horizontalSpeed(client) * LyricHoloClient.CONFIG.speedLead3D);
    }

    private LyricWorldRenderer() {}
}
