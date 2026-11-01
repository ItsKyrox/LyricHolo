package dev.lyricholo;

import dev.lyricholo.common.HoloConfigStore;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;

/** The profile shelf stores complete settings snapshots under config/lyricholo/profiles. */
final class ProfilesScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget nameField;
    private List<String> profiles = List.of();
    private int selectedIndex;
    private String message = "Profiles are saved across restarts";
    private long openedAt;

    ProfilesScreen(Screen parent) {
        super(Text.literal("LyricHolo // PROFILES"));
        this.parent = parent;
    }

    @Override protected void init() {
        openedAt = System.nanoTime();
        refreshProfiles();
        int cx = width / 2;
        int top = Math.max(14, height / 2 - 112);
        nameField = new TextFieldWidget(textRenderer, cx - 142, top + 43, 284, 22, Text.literal("PROFILE NAME"));
        nameField.setMaxLength(32);
        nameField.setDrawsBackground(false);
        nameField.setEditableColor(0xFFF5EBFF);
        addDrawableChild(nameField);

        addDrawableChild(new HoloButton(cx - 146, top + 74, 140, 23, "SAVE CURRENT", this::saveCurrent));
        addDrawableChild(new HoloButton(cx + 6, top + 74, 140, 23, "LOAD SELECTED", this::loadSelected));
        addDrawableChild(new HoloButton(cx - 146, top + 103, 222, 23, selectedLabel() + "  ▾", this::openProfileSelector));
        addDrawableChild(new HoloButton(cx + 82, top + 103, 64, 23, "DELETE", this::deleteSelected));
        addDrawableChild(new HoloButton(cx - 146, top + 134, 140, 23, "RESET DEFAULTS", this::resetDefaults));
        addDrawableChild(new HoloButton(cx + 6, top + 134, 140, 23, "BACK", () -> client.setScreen(parent)));
    }

    private void refreshProfiles() {
        profiles = HoloConfigStore.listProfiles();
        if (profiles.isEmpty()) selectedIndex = 0;
        else selectedIndex = Math.max(0, Math.min(selectedIndex, profiles.size() - 1));
    }

    private String selectedLabel() {
        if (profiles.isEmpty()) return "PROFILE: NONE";
        String value = profiles.get(selectedIndex);
        if (value.length() > 21) value = value.substring(0, 18) + "...";
        return "PROFILE: " + value;
    }

    private void saveCurrent() {
        String name = nameField.getText().trim();
        if (name.isBlank()) {
            message = "Enter a profile name first";
            return;
        }
        if (HoloConfigStore.saveProfile(name, LyricHoloClient.CONFIG)) {
            refreshProfiles();
            selectedIndex = profiles.indexOf(name);
            if (selectedIndex < 0) selectedIndex = 0;
            message = "Saved profile: " + name;
            clearAndInit();
        } else message = "Use 1-32 letters, numbers, spaces, dots or dashes";
    }

    private void loadSelected() {
        if (profiles.isEmpty()) return;
        String name = profiles.get(selectedIndex);
        if (HoloConfigStore.loadProfile(name, LyricHoloClient.CONFIG)) {
            message = "Loaded profile: " + name;
            clearAndInit();
        } else message = "Could not load profile";
    }

    private void openProfileSelector() {
        if (profiles.isEmpty()) return;
        client.setScreen(new HoloSelectorScreen(this, "CHOOSE SAVED PROFILE", profiles, selectedIndex, index -> {
            selectedIndex = index;
        }));
    }

    private void deleteSelected() {
        if (profiles.isEmpty()) return;
        String name = profiles.get(selectedIndex);
        if (name.equalsIgnoreCase("One Word") || name.equalsIgnoreCase("Sky Hologram")) {
            message = "Built-in presets are protected; save over them to customize";
            return;
        }
        if (HoloConfigStore.deleteProfile(name)) {
            message = "Deleted profile: " + name;
            refreshProfiles();
            clearAndInit();
        } else message = "Could not delete profile";
    }

    private void resetDefaults() {
        HoloConfigStore.reset(LyricHoloClient.CONFIG);
        message = "Settings reset to defaults";
        clearAndInit();
    }

    @Override public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        int cx = width / 2;
        int top = Math.max(14, height / 2 - 112);
        HexColorScreen.rounded(ctx, cx - 158, top - 9, 316, 191, 13, 0xEB0D0A1A);
        HexColorScreen.rounded(ctx, cx - 161, top - 12, 322, 197, 15, (0x26 << 24) | HoloTheme.accentRgb());
        ctx.fill(cx - 146, top - 7, cx + 146, top - 5, HoloTheme.accent());
        ctx.drawText(textRenderer, HoloTheme.uiText("LYRIC HOLO  /  PROFILE SHELF"), cx - 145, top + 3, 0xFFE8D8FF, false);
        ctx.drawText(textRenderer, HoloTheme.uiText("Save, load, or reset complete settings snapshots"), cx - 145, top + 23, 0xFF9E8CB5, false);
        HexColorScreen.rounded(ctx, cx - 146, top + 41, 292, 25, 6, 0xEE1B1527);
        ctx.drawText(textRenderer, HoloTheme.uiText(message), cx - 145, top + 166, 0xFFBDA9D0, false);
    }

    @Override public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        float enter = Math.min(1f, (System.nanoTime() - openedAt) / 250_000_000f);
        float eased = 1f - (float)Math.pow(1f - enter, 3);
        var matrices = ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate(0f, 12f * (1f - eased));
        super.render(ctx, mouseX, mouseY, delta);
        matrices.popMatrix();
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void removed() { LyricHoloClient.saveConfig(); super.removed(); }
    void refreshOptions() { clearAndInit(); }
}
