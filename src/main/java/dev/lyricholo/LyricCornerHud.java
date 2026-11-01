package dev.lyricholo;

import dev.lyricholo.common.LrcTrack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;

/** Animated compact lyric card in the lower-right HUD corner. */
final class LyricCornerHud {
    private static float visibility;
    private static float linePop;
    private static int lastLine = Integer.MIN_VALUE;
    private static int lastSegment = Integer.MIN_VALUE;

    static void render(DrawContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        var track = LyricHoloClient.TRACK.get();
        boolean allowed = LyricHoloClient.CONFIG.cornerLyrics && LyricHoloClient.CONFIG.lyricsEnabled
                && !mc.options.hudHidden && mc.world != null && mc.player != null && mc.currentScreen == null
                && track != null && !track.lines().isEmpty()
                && !(LyricHoloClient.CONFIG.combatDim && mc.player.hurtTime > 0);

        long playhead = LyricHoloClient.renderPositionMs();
        int index = allowed ? track.activeIndex(playhead, LyricHoloClient.CONFIG.syncOffsetMs) : -1;
        String line = index >= 0 && index < track.lines().size() ? track.lines().get(index).text() : "";
        allowed &= !line.isBlank();
        visibility += ((allowed ? 1f : 0f) - visibility) * 0.22f;
        if (visibility < 0.01f || !allowed) return;

        int segment = LyricTextLayout.segmentIndex(track,index,playhead,LyricHoloClient.CONFIG.syncOffsetMs,
                LyricHoloClient.CONFIG.wordsPerLine,LyricHoloClient.CONFIG.wordSync,LyricHoloClient.CONFIG.wordSyncStyle);
        if (index != lastLine || segment != lastSegment) {
            lastLine=index; lastSegment=segment; linePop=1f;
        }
        linePop += (0f - linePop) * 0.16f;
        TextRenderer font = mc.textRenderer;
        float textScale = Math.max(8, Math.min(128, LyricHoloClient.CONFIG.textSizePx)) / 16.0f;
        int lineStep = Math.max(8, Math.round(10 * textScale));
        int width = Math.min(310, Math.max(220, mc.getWindow().getScaledWidth() - 32));
        var rows = LyricTextLayout.wrap(track, index, playhead,
                LyricHoloClient.CONFIG.syncOffsetMs, Math.max(60,(int)((width-28)/textScale)), font, LyricHoloClient.CONFIG.color,
                LyricHoloClient.CONFIG.wordSync, LyricHoloClient.CONFIG.wordsPerLine, LyricHoloClient.CONFIG.wordSyncStyle,
                LyricTextLayout.font(LyricHoloClient.CONFIG.fontStyle));
        int height = 57 + Math.max(0,rows.size()-1)*lineStep + Math.round(10*textScale);
        int x = mc.getWindow().getScaledWidth() - width - 16;
        int y = mc.getWindow().getScaledHeight() - height - 22;
        int alpha = (int)(visibility * 255);
        int accent = LyricHoloClient.CONFIG.color & 0x00FFFFFF;

        if (LyricHoloClient.CONFIG.cornerBackplate) {
            rounded(context, x-3, y-3, width+6, height+6, 11, (alpha/7 << 24) | accent);
            int panelAlpha = Math.max(0, Math.min(255, LyricHoloClient.CONFIG.backplateOpacity));
            rounded(context, x, y, width, height, 9, (alpha*panelAlpha/255 << 24) | 0x100D1B);
            rounded(context, x+1, y+1, width-2, height-2, 8, (alpha*Math.max(0, panelAlpha-16)/255 << 24) | 0x171225);
            context.fill(x+12, y+10, x+width-12, y+11, (alpha << 24) | accent);
        }

        context.drawText(font, Text.literal(HoloText.t("NOW PLAYING / LIVE LYRICS")), x+14, y+17,
                (alpha << 24) | 0xFFB983, false);
        String title = LyricHoloClient.title;
        if (title != null && !title.isBlank()) {
            title = font.trimToWidth(title, width-28);
            context.drawText(font, Text.literal(title), x+14, y+29, (alpha*0xA0/255 << 24) | 0xFFD8FF, false);
        }

        int slide = (int)(linePop * 4);
        for (int i = 0; i < rows.size(); i++) {
            int rowY = y+43+i*lineStep-slide;
            if (LyricHoloClient.CONFIG.textStroke) {
                int outline = (alpha * Math.max(0, Math.min(255, LyricHoloClient.CONFIG.strokeOpacity)) / 255 << 24) | 0x120B1E;
                for (int dx=-1; dx<=1; dx++) for (int dy=-1; dy<=1; dy++) {
                    if (dx != 0 || dy != 0) context.drawText(font, rows.get(i), x+14+dx, rowY+dy, outline, false);
                }
            }
            var matrices = context.getMatrices();
            matrices.pushMatrix();
            matrices.translate(x+14, rowY);
            matrices.scale(textScale, textScale);
            context.drawText(font, rows.get(i), 0, 0, (alpha << 24) | 0xFFF5FF, true);
            matrices.popMatrix();
        }
        int pulse = (int)(30 + 18*Math.sin(System.nanoTime()/280_000_000.0));
        if (LyricHoloClient.CONFIG.cornerBackplate) context.fill(x+14, y+height-7, x+14+pulse, y+height-6, (alpha/2 << 24) | accent);
    }

    private static void rounded(DrawContext ctx, int x, int y, int width, int height, int radius, int color) {
        int r = Math.min(radius, Math.min(width/2, height/2));
        for (int row=0; row<height; row++) {
            double dy = row<r ? r-row-0.5 : row>=height-r ? row-(height-r)+0.5 : 0;
            int inset = dy==0 ? 0 : Math.max(0, r-(int)Math.sqrt(Math.max(0,r*r-dy*dy)));
            ctx.fill(x+inset, y+row, x+width-inset, y+row+1, color);
        }
    }

    private LyricCornerHud() {}
}
