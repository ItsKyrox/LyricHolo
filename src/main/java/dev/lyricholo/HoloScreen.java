package dev.lyricholo;

import dev.lyricholo.media.MediaTrack;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Rounded, animated control deck driven by the active desktop media session. */
public final class HoloScreen extends Screen {
    private long openedAt;
    private float pulse;

    public HoloScreen() { super(Text.literal("LYRICHОLO // " + HoloText.t("CONTROL DECK"))); }

    @Override protected void init() {
        openedAt = System.nanoTime();
        int cx = width / 2, top = Math.max(18, height / 2 - 164);
        addDrawableChild(new HoloButton(cx-132, top+129, 128, 24, HoloText.t("DISPLAY")+"  " + String.format("%.1fm", Math.abs(LyricHoloClient.CONFIG.offsetZ)), () -> {
            LyricHoloClient.CONFIG.offsetZ = LyricHoloClient.CONFIG.offsetZ >= -1.1f ? -3.5f : LyricHoloClient.CONFIG.offsetZ + 0.3f; clearAndInit();
        }));
        addDrawableChild(new HoloButton(cx+4, top+129, 128, 24, HoloText.t("SYNC")+"  " + LyricHoloClient.CONFIG.syncOffsetMs + "ms", () -> {
            LyricHoloClient.CONFIG.syncOffsetMs += 250; if (LyricHoloClient.CONFIG.syncOffsetMs > 3_000) LyricHoloClient.CONFIG.syncOffsetMs = -3_000; clearAndInit();
        }));
        addDrawableChild(new HoloButton(cx-132, top+157, 128, 24, HoloText.t("KEEP AHEAD")+"  " + state(LyricHoloClient.CONFIG.keepAhead3D), () -> {
            LyricHoloClient.CONFIG.keepAhead3D = !LyricHoloClient.CONFIG.keepAhead3D;
            clearAndInit();
        }));
        addDrawableChild(new HoloButton(cx+4, top+157, 128, 24, HoloText.t("SCREEN STICKY")+"  " + state(LyricHoloClient.CONFIG.screenSticky3D), () -> {
            LyricHoloClient.CONFIG.screenSticky3D = !LyricHoloClient.CONFIG.screenSticky3D;
            clearAndInit();
        }));
        addDrawableChild(new HoloButton(cx-132, top+185, 128, 24, HoloText.t("LYRICS")+"  " + state(LyricHoloClient.CONFIG.lyricsEnabled), () -> { LyricHoloClient.CONFIG.lyricsEnabled = !LyricHoloClient.CONFIG.lyricsEnabled; clearAndInit(); }));
        addDrawableChild(new HoloButton(cx+4, top+185, 128, 24, HoloText.t("CORNER LYRIC")+"  " + state(LyricHoloClient.CONFIG.cornerLyrics), () -> { LyricHoloClient.CONFIG.cornerLyrics = !LyricHoloClient.CONFIG.cornerLyrics; clearAndInit(); }));
        addDrawableChild(new HoloButton(cx-132, top+213, 128, 24, HoloText.t("WORD SYNC")+"  ▾  " + wordSyncName(), () -> {
            var style = dev.lyricholo.common.HoloConfig.WordSyncStyle.values();
            int selected = !LyricHoloClient.CONFIG.wordSync ? 3
                    : LyricHoloClient.CONFIG.wordSyncStyle.ordinal();
            client.setScreen(new HoloSelectorScreen(this, HoloText.t("WORD SYNC MODE"),
                    java.util.List.of(HoloText.t("HIGHLIGHT"), HoloText.t("POP UP"), HoloText.t("STACK"), HoloText.t("OFF")), selected, index -> {
                        LyricHoloClient.CONFIG.wordSync = index != 3;
                        if (index < 3) LyricHoloClient.CONFIG.wordSyncStyle = style[index];
                        LyricHoloClient.saveConfig();
                    }));
        }));
        addDrawableChild(new HoloButton(cx+4, top+213, 128, 24, HoloText.t("3D ANIM")+"  " + animationName(LyricHoloClient.CONFIG.textAnimation), () -> {
            var modes = dev.lyricholo.common.HoloConfig.TextAnimation.values();
            client.setScreen(new HoloSelectorScreen(this, HoloText.t("3D ANIMATION"),
                    java.util.Arrays.stream(modes).map(HoloScreen::animationName).toList(), LyricHoloClient.CONFIG.textAnimation.ordinal(),
                    index -> { LyricHoloClient.CONFIG.textAnimation = modes[index]; LyricHoloClient.saveConfig(); }));
        }));
        addDrawableChild(new HoloButton(cx-132, top+241, 264, 24,
                HoloText.t("LYRICS SOURCE") + "  ▾  " + sourceName(LyricHoloClient.CONFIG.lyricsProvider), () -> {
            var sources = new dev.lyricholo.common.HoloConfig.LyricsProvider[] {
                    dev.lyricholo.common.HoloConfig.LyricsProvider.YOUTUBE_MUSIC,
                    dev.lyricholo.common.HoloConfig.LyricsProvider.LRCLIB };
            int selectedSource = LyricHoloClient.CONFIG.lyricsProvider == sources[1] ? 1 : 0;
            client.setScreen(new HoloSelectorScreen(this, HoloText.t("CHOOSE LYRICS SOURCE"),
                    java.util.List.of("YouTube Music API", "LRCLIB"),
                    selectedSource, index -> {
                        LyricHoloClient.CONFIG.lyricsProvider = sources[index];
                        LyricHoloClient.reloadLyrics();
                        LyricHoloClient.saveConfig();
                    }));
        }));
        addDrawableChild(new HoloButton(cx-132, top+269, 128, 24, HoloText.t("FACE CAMERA")+"  " + state(LyricHoloClient.CONFIG.billboard), () -> {
            LyricHoloClient.CONFIG.billboard = !LyricHoloClient.CONFIG.billboard;
            clearAndInit();
        }));
        addDrawableChild(new HoloButton(cx+4, top+269, 128, 24, HoloText.t("TEXT / THEME"), () -> client.setScreen(new TextSettingsScreen(this))));
        addDrawableChild(new HoloButton(cx-132, top+297, 128, 24, HoloText.t("LANGUAGE")+"  ▾  "+languageName(), () -> {
            HoloText.Language[] options=HoloText.Language.values();
            HoloText.Language current;
            try { current=HoloText.Language.valueOf(LyricHoloClient.CONFIG.language); }
            catch (RuntimeException ignored) { current=HoloText.Language.ENGLISH; }
            client.setScreen(new HoloSelectorScreen(this, HoloText.t("LANGUAGE"),
                    java.util.Arrays.stream(options).map(HoloScreen::languageOptionName).toList(), current.ordinal(), index -> {
                        LyricHoloClient.CONFIG.language=options[index].name();
                        LyricHoloClient.saveConfig();
                    }));
        }));
        addDrawableChild(new HoloButton(cx+4, top+297, 128, 24, HoloText.t("CLOSE / ESC"), this::close));
    }

    private static String state(boolean on) { return HoloText.t(on ? "ON" : "OFF"); }
    private static String sourceName(dev.lyricholo.common.HoloConfig.LyricsProvider provider) {
        return provider == dev.lyricholo.common.HoloConfig.LyricsProvider.LRCLIB ? "LRCLIB" : "YouTube Music API";
    }
    private static String wordSyncName() {
        if (!LyricHoloClient.CONFIG.wordSync) return HoloText.t("OFF");
        return HoloText.t(switch (LyricHoloClient.CONFIG.wordSyncStyle) {
            case POP_UP -> "POP";
            case STACK -> "STACK";
            default -> "HIGHLIGHT";
        });
    }
    private static String animationName(dev.lyricholo.common.HoloConfig.TextAnimation animation) {
        return animation == dev.lyricholo.common.HoloConfig.TextAnimation.POP_FADE
                ? "FADE IN UP" : animation.name().replace('_',' ');
    }
    private static String languageOptionName(HoloText.Language language) { return switch (language) {
        case ENGLISH -> "English"; case RUSSIAN -> "Русский"; case BULGARIAN -> "Български"; case GERMAN -> "Deutsch"; case DUTCH -> "Nederlands";
    }; }
    private static String languageName() {
        try { return switch (HoloText.Language.valueOf(LyricHoloClient.CONFIG.language)) {
            case ENGLISH -> "English"; case RUSSIAN -> "Русский"; case BULGARIAN -> "Български"; case GERMAN -> "Deutsch"; case DUTCH -> "Nederlands";
        }; } catch (RuntimeException ignored) { return "English"; }
    }

    @Override public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        int cx = width/2, top = Math.max(18, height/2-164);
        float enter = Math.min(1f, (System.nanoTime()-openedAt)/280_000_000f);
        float ease = 1f-(float)Math.pow(1f-enter,3);
        pulse = 0.5f + 0.5f*(float)Math.sin(System.nanoTime()/520_000_000.0);
        rounded(ctx, cx-151, top-8, 302, 341, 10, 0xE90D0A1A);
        rounded(ctx, cx-154, top-11, 308, 347, 12, (0x16 << 24) | HoloTheme.accentRgb());
        ctx.fill(cx-143, top-6, cx+143, top-4, HoloTheme.accent());
        int rail = (int)((18+28*pulse)*ease);
        ctx.fill(cx-143, top+31, cx-143+rail, top+32, HoloTheme.accent());
        for (int gx=cx-136; gx<cx+137; gx+=27) ctx.fill(gx, top+36, gx+1, top+330, 0x103D7EC0);
        for (int gy=top+38; gy<top+330; gy+=18) ctx.fill(cx-141, gy, cx+141, gy+1, 0x0B9561D4);
        int scanY = top+38+(int)((System.nanoTime()/8_000_000L)%285);
        ctx.fill(cx-140, scanY, cx+140, scanY+1, (0x23 << 24) | HoloTheme.accentRgb());

        drawMusicIcon(ctx,cx-132,top+1);
        ctx.drawText(textRenderer, HoloTheme.uiText("LYRIC"), cx-117, top+2, 0xFFDCC5FF, false);
        ctx.drawText(textRenderer, HoloTheme.uiText("HOLO  /  CONTROL DECK"), cx-68, top+2, 0xFF9A78C5, false);
        ctx.drawText(textRenderer, HoloTheme.uiText(HoloText.t("AUTOMATIC MEDIA SESSION")), cx-132, top+40, 0xFF9E8CB5, false);
        MediaTrack media = LyricHoloClient.MEDIA.poll();
        String track = media == null ? HoloText.t("NO ACTIVE MEDIA SESSION") : media.title();
        String performer = media == null ? HoloText.t("PLAY AUDIO IN SPOTIFY, YOUTUBE OR A BROWSER") : media.artist();
        ctx.drawText(textRenderer, crop(track, 264), cx-132, top+61, media == null ? 0xFF8C809C : 0xFFE8D7FF, false);
        ctx.drawText(textRenderer, crop(performer, 264), cx-132, top+77, 0xFFAC9BBC, false);
        if (media != null && media.durationMs() > 0) {
            int bx=cx-132, by=top+101, bw=264;
            rounded(ctx,bx,by,bw,4,2,0xFF322743);
            rounded(ctx,bx,by,Math.max(4,(int)(bw*Math.min(1, media.positionMs()/(double)media.durationMs()))),4,2,0xFFD19AFF);
        }
        ctx.drawText(textRenderer, crop(HoloText.t(LyricHoloClient.status), 260), cx-132, top+112, 0xFFCAAEE8, false);
        rounded(ctx, cx+4, top+269, 128, 24, 6, 0xEE181226);
        ctx.fill(cx+10, top+269, cx+126, top+270, HoloTheme.accent());
    }

    /** Draws a font-independent note mark so it still appears with Pacifico selected. */
    private static void drawMusicIcon(DrawContext ctx,int x,int y) {
        int color=HoloTheme.accent();
        ctx.fill(x+8,y,x+10,y+10,color);
        ctx.fill(x+14,y-2,x+16,y+8,color);
        ctx.fill(x+9,y,x+16,y+2,color);
        rounded(ctx,x+3,y+8,6,4,2,color);
        rounded(ctx,x+10,y+6,6,4,2,color);
    }

    @Override public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Move the glass deck, its labels, and its controls as one composed panel.
        float enter=Math.min(1f,(System.nanoTime()-openedAt)/280_000_000f);
        float eased=1f-(float)Math.pow(1f-enter,3);
        var matrices=ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate(0f,12f*(1f-eased));
        super.render(ctx,mouseX,mouseY,delta);
        matrices.popMatrix();
    }
    @Override public boolean shouldPause() { return false; }

    @Override public void removed() { LyricHoloClient.saveConfig(); super.removed(); }

    void refreshOptions() { clearAndInit(); }

    private String crop(String value, int maxWidth) {
        if (value == null || textRenderer.getWidth(value) <= maxWidth) return value == null ? "" : value;
        return textRenderer.trimToWidth(value, maxWidth-8) + "…";
    }

    private static void rounded(DrawContext ctx, int x, int y, int width, int height, int radius, int color) {
        int r=Math.min(radius,Math.min(width/2,height/2));
        for (int row=0; row<height; row++) {
            double dy = row<r ? r-row-0.5 : row>=height-r ? row-(height-r)+0.5 : 0;
            int inset = dy==0 ? 0 : Math.max(0,r-(int)Math.sqrt(Math.max(0,r*r-dy*dy)));
            ctx.fill(x+inset,y+row,x+width-inset,y+row+1,color);
        }
    }
}
