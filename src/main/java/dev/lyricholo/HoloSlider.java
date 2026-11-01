package dev.lyricholo;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/** Native slider behavior with the mod's custom violet-glass track and handle. */
final class HoloSlider extends SliderWidget {
    private final DoubleConsumer changed;
    private final DoubleFunction<String> label;

    HoloSlider(int x, int y, int width, int height, double value,
               DoubleFunction<String> label, DoubleConsumer changed) {
        super(x, y, width, height, Text.empty(), value);
        this.label = label;
        this.changed = changed;
        updateMessage();
    }

    @Override protected void updateMessage() { setMessage(Text.literal(label.apply(value))); }
    @Override protected void applyValue() { changed.accept(value); }

    void setNormalizedValue(double value) {
        this.value = Math.max(0.0, Math.min(1.0, value));
        updateMessage();
    }

    @Override public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        int x=getX(), y=getY(), w=getWidth(), h=getHeight();
        HexColorScreen.rounded(context, x, y+7, w, 5, 3, 0xFF30243D);
        int fill=(int)Math.round((w-6)*value);
        if (fill>0) HexColorScreen.rounded(context, x+1, y+7, fill+4, 5, 3, HoloTheme.accent());
        boolean hovered=mouseX>=x&&mouseX<x+w&&mouseY>=y&&mouseY<y+h;
        int knobX=x+3+(int)Math.round((w-10)*value);
        HexColorScreen.rounded(context,knobX-2,y+3,14,14,7,hovered?0xFFFFFFFF:0xFFEAD8FF);
        context.drawCenteredTextWithShadow(net.minecraft.client.MinecraftClient.getInstance().textRenderer,
                getMessage(),x+w/2,y-5,0xFFF1E8FF);
    }
}
