package dev.lyricholo;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.navigation.GuiNavigation;
import net.minecraft.client.gui.navigation.GuiNavigationPath;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

/** Custom rounded glass button with eased hover bloom; intentionally avoids vanilla button skin. */
final class HoloButton extends ClickableWidget {
    private final Runnable action;
    private float hover;

    HoloButton(int x, int y, int width, int height, String label, Runnable action) {
        super(x, y, width, height, HoloTheme.uiText(label));
        this.action = action;
    }

    @Override protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        boolean over = mouseX >= getX() && mouseX < getRight() && mouseY >= getY() && mouseY < getBottom();
        float target = over && active ? 1f : 0f;
        float speed = Math.min(1f, Math.max(0.08f, delta * 0.24f));
        hover += (target - hover) * speed;
        int x=getX(), y=getY(), w=getWidth(), h=getHeight();
        int bloom = (int)(hover*46);
        if (bloom > 0) rounded(ctx,x-2,y-2,w+4,h+4,8,(bloom<<24)|HoloTheme.accentRgb());
        rounded(ctx,x,y,w,h,6,active ? 0xE51B1428 : 0x88302A36);
        rounded(ctx,x+1,y+1,w-2,h-2,5,active ? 0xC9201834 : 0x66302A36);
        int edge = (int)(120+hover*110);
        int edgeColor=(edge<<24)|HoloTheme.accentRgb();
        ctx.fill(x+7,y,x+w-7,y+1,edgeColor);
        ctx.fill(x+7,y+h-1,x+w-7,y+h,0x66785992);
        int textColor = active ? (over ? 0xFFFFFFFF : 0xFFE2D5F5) : 0xFF887F91;
        ctx.drawCenteredTextWithShadow(net.minecraft.client.MinecraftClient.getInstance().textRenderer, getMessage(), x+w/2, y+(h-8)/2, textColor);
    }

    @Override public void onClick(Click click, boolean doubleClick) { if (active) action.run(); }
    @Override protected void appendClickableNarrations(NarrationMessageBuilder builder) { appendDefaultNarrations(builder); }

    private static void rounded(DrawContext ctx, int x, int y, int width, int height, int radius, int color) {
        int r=Math.min(radius,Math.min(width/2,height/2));
        for (int row=0; row<height; row++) {
            double dy = row<r ? r-row-0.5 : row>=height-r ? row-(height-r)+0.5 : 0;
            int inset = dy==0 ? 0 : Math.max(0,r-(int)Math.sqrt(Math.max(0,r*r-dy*dy)));
            ctx.fill(x+inset,y+row,x+width-inset,y+row+1,color);
        }
    }
}
