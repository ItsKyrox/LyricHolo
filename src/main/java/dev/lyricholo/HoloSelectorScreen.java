package dev.lyricholo;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;
import java.util.function.IntConsumer;

/** Compact option picker used in place of repeated click-to-cycle controls. */
final class HoloSelectorScreen extends Screen {
    private final Screen parent;
    private final String heading;
    private final List<String> options;
    private final int selected;
    private final IntConsumer onSelect;
    private long openedAt;

    HoloSelectorScreen(Screen parent, String heading, List<String> options, int selected, IntConsumer onSelect) {
        super(Text.literal("LyricHolo // SELECT"));
        this.parent = parent;
        this.heading = heading;
        this.options = List.copyOf(options);
        this.selected = selected;
        this.onSelect = onSelect;
    }

    @Override protected void init() {
        openedAt = System.nanoTime();
        int panelHeight = 70 + options.size() * 30;
        int top = Math.max(8, (height - panelHeight) / 2);
        int left = width / 2 - 145;
        for (int i = 0; i < options.size(); i++) {
            final int index = i;
            String label = options.get(i) + (i == selected ? "   ✓" : "");
            addDrawableChild(new HoloButton(left + 12, top + 34 + i * 30, 266, 25, label, () -> {
                onSelect.accept(index);
                if (parent instanceof HoloScreen screen) screen.refreshOptions();
                else if (parent instanceof TextSettingsScreen screen) screen.refreshOptions();
                else if (parent instanceof ProfilesScreen screen) screen.refreshOptions();
                client.setScreen(parent);
            }));
        }
        addDrawableChild(new HoloButton(left + 12, top + 39 + options.size() * 30, 266, 23,
                HoloText.t("BACK"), () -> client.setScreen(parent)));
    }

    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        int panelHeight = 70 + options.size() * 30;
        int top = Math.max(8, (height - panelHeight) / 2);
        int left = width / 2 - 145;
        HexColorScreen.rounded(context, left, top, 290, panelHeight, 12, 0xE90D0A1A);
        HexColorScreen.rounded(context, left - 3, top - 3, 296, panelHeight + 6, 14,
                (0x24 << 24) | HoloTheme.accentRgb());
        context.fill(left + 12, top + 23, left + 278, top + 25, HoloTheme.accent());
        context.drawCenteredTextWithShadow(textRenderer, HoloTheme.uiText(heading), width / 2, top + 10, 0xFFF0E3FF);
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float enter = Math.min(1f, (System.nanoTime() - openedAt) / 220_000_000f);
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(0f, 10f * (1f - (float)Math.pow(1f - enter, 3)));
        super.render(context, mouseX, mouseY, delta);
        matrices.popMatrix();
    }

    @Override public boolean shouldPause() { return false; }
}
