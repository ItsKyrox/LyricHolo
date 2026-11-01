package dev.lyricholo;

import dev.lyricholo.common.HoloConfig;
import net.minecraft.text.Text;

/** Small color palette shared by custom controls and screens. */
final class HoloTheme {
    static int accent() {
        HoloConfig.UiTheme theme = LyricHoloClient.CONFIG.uiTheme;
        if (theme == null) theme = HoloConfig.UiTheme.VIOLET;
        return switch (theme) {
            case PINK -> 0xFFFF4FCB;
            case CYAN -> 0xFF45DDF2;
            case EMERALD -> 0xFF52E6AE;
            default -> 0xFFB968FF;
        };
    }

    static int accentRgb() { return accent() & 0xFFFFFF; }

    static Text uiText(String value) {
        return Text.literal(value);
    }

    private HoloTheme() {}
}
