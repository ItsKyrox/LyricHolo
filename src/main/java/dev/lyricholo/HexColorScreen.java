package dev.lyricholo;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Small themed color-picker dialog with a swatch grid and a validated hex entry. */
final class HexColorScreen extends Screen {
    private static final int[] HUES = {
            0xFFFF5050, 0xFFFF9A42, 0xFFFFD84A, 0xFF9FE34D,
            0xFF42DDA4, 0xFF42DDE8, 0xFF4A9DFF, 0xFF7568FF,
            0xFFAE61FF, 0xFFEC62D4, 0xFFF078A2, 0xFFF4EFFF
    };
    private static final int[] TONES = {0xFF49365F, 0xFF70558E, 0xFF9D7CC4, 0xFFD0B5F0};

    private final Screen parent;
    private TextFieldWidget hexField;
    private int draftColor;
    private long openedAt;

    HexColorScreen(Screen parent) {
        super(Text.literal("LyricHolo // " + HoloText.t("TEXT COLOR")));
        this.parent = parent;
        this.draftColor = LyricHoloClient.CONFIG.color | 0xFF000000;
    }

    @Override protected void init() {
        openedAt=System.nanoTime();
        int cx = width / 2;
        int top = Math.max(12, height / 2 - 123);
        int swatch = 17, gap = 4;
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < HUES.length; col++) {
                int color = row == 0 ? HUES[col] : blend(HUES[col], TONES[row - 1], row * 0.18f);
                final int selected = color;
                addDrawableChild(new HoloColorSwatch(cx - 126 + col * (swatch + gap), top + 51 + row * 23,
                        swatch, 18, selected, () -> choose(selected)));
            }
        }
        hexField = new TextFieldWidget(textRenderer, cx - 124, top + 153, 124, 22, Text.literal(HoloText.t("HEX COLOR")));
        hexField.setMaxLength(7);
        hexField.setText(String.format("#%06X", draftColor & 0xFFFFFF));
        hexField.setDrawsBackground(false);
        hexField.setEditableColor(0xFFF5EBFF);
        hexField.setUneditableColor(0xFF8C8299);
        hexField.setChangedListener(this::readHex);
        addDrawableChild(hexField);
        addDrawableChild(new HoloButton(cx + 7, top + 152, 117, 24, HoloText.t("APPLY COLOR"), this::apply));
        addDrawableChild(new HoloButton(cx - 126, top + 184, 253, 24, HoloText.t("BACK TO CONTROLS"), this::apply));
    }

    private void choose(int color) {
        draftColor = color | 0xFF000000;
        if (hexField != null) hexField.setText(String.format("#%06X", draftColor & 0xFFFFFF));
    }

    private void readHex(String value) {
        String digits = value.startsWith("#") ? value.substring(1) : value;
        if (digits.matches("(?i)[0-9a-f]{6}")) draftColor = 0xFF000000 | Integer.parseInt(digits, 16);
    }

    private void apply() {
        LyricHoloClient.CONFIG.color = draftColor;
        if (client != null) client.setScreen(parent);
    }

    @Override public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        int cx = width / 2;
        int top = Math.max(12, height / 2 - 123);
        rounded(ctx, cx - 139, top - 8, 278, 228, 12, 0xEE100D1D);
        rounded(ctx, cx - 142, top - 11, 284, 226, 14, (0x27 << 24) | HoloTheme.accentRgb());
        ctx.fill(cx - 130, top - 6, cx + 130, top - 4, HoloTheme.accent());
        ctx.drawText(textRenderer, HoloTheme.uiText(HoloText.t("TEXT COLOR")), cx - 126, top + 4, 0xFFE6D5FF, false);
        ctx.drawText(textRenderer, HoloTheme.uiText(HoloText.t("SWATCHES / HEX VALUE")), cx - 126, top + 24, 0xFF9D8CAF, false);
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < HUES.length; col++) {
                int color = row == 0 ? HUES[col] : blend(HUES[col], TONES[row - 1], row * 0.18f);
                int x = cx - 126 + col * 21;
                int y = top + 51 + row * 23;
                rounded(ctx, x, y, 17, 18, 4, color);
                if ((draftColor & 0xFFFFFF) == (color & 0xFFFFFF)) {
                    ctx.fill(x + 3, y + 2, x + 14, y + 3, 0xFFFFFFFF);
                }
            }
        }
        rounded(ctx, cx - 126, top + 151, 128, 25, 6, 0xEE1B1527);
        rounded(ctx, cx + 105, top + 154, 21, 19, 5, draftColor);
        ctx.drawText(textRenderer, "#RRGGBB", cx - 126, top + 140, 0xFFAC9BBC, false);
    }

    @Override public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        float enter=Math.min(1f,(System.nanoTime()-openedAt)/240_000_000f);
        float eased=1f-(float)Math.pow(1f-enter,3);
        var matrices=ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate(0f,10f*(1f-eased));
        super.render(ctx, mouseX, mouseY, delta);
        matrices.popMatrix();
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void removed() { LyricHoloClient.saveConfig(); super.removed(); }

    private static int blend(int a, int b, float amount) {
        float t = Math.min(1.0f, amount);
        int r = (int)(((a >>> 16) & 255) * (1 - t) + ((b >>> 16) & 255) * t);
        int g = (int)(((a >>> 8) & 255) * (1 - t) + ((b >>> 8) & 255) * t);
        int bl = (int)((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    static void rounded(DrawContext ctx, int x, int y, int width, int height, int radius, int color) {
        int r = Math.min(radius, Math.min(width / 2, height / 2));
        for (int row = 0; row < height; row++) {
            double dy = row < r ? r - row - 0.5 : row >= height - r ? row - (height - r) + 0.5 : 0;
            int inset = dy == 0 ? 0 : Math.max(0, r - (int)Math.sqrt(Math.max(0, r * r - dy * dy)));
            ctx.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
        }
    }
}

/** Compact clickable palette swatch with a matching rounded hover glow. */
final class HoloColorSwatch extends net.minecraft.client.gui.widget.ClickableWidget {
    private final int color;
    private final Runnable action;

    HoloColorSwatch(int x, int y, int width, int height, int color, Runnable action) {
        super(x, y, width, height, Text.empty());
        this.color = color;
        this.action = action;
    }

    @Override protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        if (hovered) HexColorScreen.rounded(ctx, x - 2, y - 2, w + 4, h + 4, 6, (0xAA << 24) | HoloTheme.accentRgb());
        HexColorScreen.rounded(ctx, x, y, w, h, 4, color);
        if (hovered) ctx.fill(x + 3, y + 2, x + w - 3, y + 3, 0xFFFFFFFF);
    }

    @Override public void onClick(net.minecraft.client.gui.Click click, boolean doubleClick) { action.run(); }
    @Override protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) { appendDefaultNarrations(builder); }
}
