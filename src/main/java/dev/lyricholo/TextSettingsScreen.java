package dev.lyricholo;

import dev.lyricholo.common.HoloConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Appearance controls are kept on their own page so the media deck stays compact. */
final class TextSettingsScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget pixelsField;
    private HoloSlider pixelsSlider;
    private TextFieldWidget wordsField;
    private HoloSlider wordsSlider;
    private TextFieldWidget skyYField;
    private HoloSlider skyYSlider;
    private long openedAt;

    TextSettingsScreen(Screen parent) {
        super(Text.literal("LyricHolo // " + HoloText.t("TEXT / HOLOGRAM SETTINGS")));
        this.parent=parent;
    }

    @Override protected void init() {
        openedAt=System.nanoTime();
        HoloConfig c=LyricHoloClient.CONFIG;
        int cx=width/2;
        // Centered single-column sliders keep their labels and value controls together.
        boolean compact=height<460;
        int row=compact?18:28, slider=compact?16:26, buttonH=compact?18:23;
        int panelHeight=compact?244:342;
        int left=cx-150, right=cx+6, top=Math.max(5,(height-panelHeight)/2);
        addDrawableChild(new HoloButton(left,top+26,146,buttonH,HoloText.t("SKY TEXT")+"  "+state(c.skyMode),()->{c.skyMode=!c.skyMode;clearAndInit();}));
        addDrawableChild(new HoloButton(right,top+26,146,buttonH,HoloText.t("THEME")+"  ▾  "+name(c.uiTheme),()->{
            var values=HoloConfig.UiTheme.values();
            client.setScreen(new HoloSelectorScreen(this,HoloText.t("UI THEME"),java.util.Arrays.stream(values).map(Enum::name).toList(),c.uiTheme.ordinal(),index->{c.uiTheme=values[index];LyricHoloClient.saveConfig();}));
        }));
        addDrawableChild(new HoloButton(left,top+26+row,146,buttonH,HoloText.t("FONT")+"  ▾  "+fontName(c.fontStyle),()->{
            var values=HoloConfig.FontStyle.values();
            client.setScreen(new HoloSelectorScreen(this,HoloText.t("LYRIC FONT"),java.util.Arrays.stream(values).map(TextSettingsScreen::fontName).toList(),c.fontStyle.ordinal(),index->{c.fontStyle=values[index];LyricHoloClient.saveConfig();}));
        }));
        addDrawableChild(new HoloButton(right,top+26+row,146,buttonH,HoloText.t("TEXT SHADOW")+"  "+state(c.shadow),()->{c.shadow=!c.shadow;clearAndInit();}));
        addDrawableChild(new HoloButton(left,top+26+2*row,146,buttonH,HoloText.t("TEXT STROKE")+"  "+state(c.textStroke),()->{c.textStroke=!c.textStroke;clearAndInit();}));
        addDrawableChild(new HoloButton(right,top+26+2*row,146,buttonH,HoloText.t("3D BACKPLATE")+"  "+state(c.worldBackplate),()->{c.worldBackplate=!c.worldBackplate;clearAndInit();}));

        int sy=top+26+3*row;
        addDrawableChild(new HoloSlider(left,sy,302,slider,Math.max(1,Math.min(20,-c.offsetZ))/20.0,
                value->HoloText.t("DISTANCE")+"  "+String.format("%.1f m",Math.max(1,value*20)),value->c.offsetZ=-(float)Math.max(1,Math.min(20,value*20))));
        skyYSlider=new HoloSlider(left,sy+slider,236,slider,clamp(Math.round(c.skyHeight),0,88)/88.0,
                value->HoloText.t("FIXED SKY Y OFFSET"),value->{
                    c.skyHeight=(float)Math.max(0,Math.min(88,value*88));
                    if(skyYField!=null&&!skyYField.isFocused())skyYField.setText(Integer.toString(Math.round(c.skyHeight)));
                });
        addDrawableChild(skyYSlider);
        skyYField=new TextFieldWidget(textRenderer,left+244,sy+slider+2,58,20,Text.literal("Y+"));
        skyYField.setMaxLength(2);
        skyYField.setText(Integer.toString(Math.round(c.skyHeight)));
        skyYField.setDrawsBackground(false);
        skyYField.setEditableColor(0xFFF5EBFF);
        skyYField.setChangedListener(this::applySkyYOffset);
        addDrawableChild(skyYField);
        addDrawableChild(new HoloSlider(left,sy+2*slider,302,slider,c.oldTextHoldMs/10_000.0,
                value->HoloText.t("PREVIOUS TEXT HOLD")+"  "+String.format("%.1f s",value*10),value->c.oldTextHoldMs=clamp((int)Math.round(value*10_000),0,10_000)));
        int textSizeY=sy+3*slider;
        pixelsSlider=new HoloSlider(left,textSizeY,236,slider,(clamp(c.textSizePx,8,48)-8)/40.0,
                value->HoloText.t("TEXT SIZE"),value->{
                    c.textSizePx=clamp((int)Math.round(8+value*40),8,48);
                    if(pixelsField!=null&&!pixelsField.isFocused())pixelsField.setText(Integer.toString(c.textSizePx));
                });
        addDrawableChild(pixelsSlider);
        pixelsField=new TextFieldWidget(textRenderer,left+244,textSizeY+2,58,20,Text.literal("px"));
        pixelsField.setMaxLength(2);
        pixelsField.setText(Integer.toString(c.textSizePx));
        pixelsField.setDrawsBackground(false);
        pixelsField.setEditableColor(0xFFF5EBFF);
        pixelsField.setChangedListener(this::applyPixelsLive);
        addDrawableChild(pixelsField);
        int opacityY=textSizeY+slider;
        addDrawableChild(new HoloSlider(left,opacityY,302,slider,c.backplateOpacity/255.0,
                value->HoloText.t("BACKPLATE OPACITY")+"  "+Math.round(value*100)+"%",value->c.backplateOpacity=clamp((int)Math.round(value*255),0,255)));
        int wordY=opacityY+slider;
        wordsSlider=new HoloSlider(left,wordY,236,slider,c.wordsPerLine/20.0,
                value->HoloText.t("WORDS PER DISPLAY"),value->{
                    c.wordsPerLine=clamp((int)Math.round(value*20),0,20);
                    if(wordsField!=null&&!wordsField.isFocused())wordsField.setText(Integer.toString(c.wordsPerLine));
                });
        addDrawableChild(wordsSlider);
        wordsField=new TextFieldWidget(textRenderer,left+244,wordY+2,58,20,Text.literal("#"));
        wordsField.setMaxLength(2);
        wordsField.setText(Integer.toString(c.wordsPerLine));
        wordsField.setDrawsBackground(false);
        wordsField.setEditableColor(0xFFF5EBFF);
        wordsField.setChangedListener(this::applyWordsLive);
        addDrawableChild(wordsField);
        int bottomY=wordY+slider+(compact?4:5);
        addDrawableChild(new HoloButton(left,bottomY,146,buttonH,HoloText.t("HUD BACKPLATE")+"  "+state(c.cornerBackplate),()->{c.cornerBackplate=!c.cornerBackplate;clearAndInit();}));
        addDrawableChild(new HoloButton(right,bottomY,146,buttonH,HoloText.t("TEXT COLOR / HEX"),()->client.setScreen(new HexColorScreen(this))));
        int bottomRowY=bottomY+buttonH+5;
        addDrawableChild(new HoloButton(left,bottomRowY,146,buttonH,HoloText.t("PROFILES"),()->client.setScreen(new ProfilesScreen(this))));
        addDrawableChild(new HoloButton(right,bottomRowY,146,buttonH,HoloText.t("BACK TO CONTROL DECK"),()->client.setScreen(parent)));
    }

    private void applyPixelsLive(String raw) {
        try {
            int pixels=clamp(Integer.parseInt(raw.trim()),8,48);
            LyricHoloClient.CONFIG.textSizePx=pixels;
            if(pixelsSlider!=null)pixelsSlider.setNormalizedValue((pixels-8)/40.0);
        } catch (RuntimeException ignored) { }
    }

    private void applyWordsLive(String raw) {
        try {
            int words=clamp(Integer.parseInt(raw.trim()),0,20);
            LyricHoloClient.CONFIG.wordsPerLine=words;
            if(wordsSlider!=null)wordsSlider.setNormalizedValue(words/20.0);
        } catch (RuntimeException ignored) { }
    }

    private void applySkyYOffset(String raw) {
        try {
            int offset=clamp(Integer.parseInt(raw.trim()),0,88);
            LyricHoloClient.CONFIG.skyHeight=offset;
            skyYSlider.setNormalizedValue(offset/88.0);
        } catch (RuntimeException ignored) { }
    }

    @Override public void renderBackground(DrawContext ctx,int mouseX,int mouseY,float delta) {
        super.renderBackground(ctx,mouseX,mouseY,delta);
        int cx=width/2;
        // Centered glass card leaves the controls' left/right columns aligned to one grid.
        boolean compact=height<460;
        int panelHeight=compact?244:342;
        int top=Math.max(5,(height-panelHeight)/2);
        HexColorScreen.rounded(ctx,cx-160,top-9,320,panelHeight,13,0xE90D0A1A);
        HexColorScreen.rounded(ctx,cx-163,top-12,326,panelHeight+6,15,(0x24<<24)|HoloTheme.accentRgb());
        int rail=(int)(296*Math.min(1f,(System.nanoTime()-openedAt)/260_000_000f));
        ctx.fill(cx-148,top-7,cx-148+rail,top-5,HoloTheme.accent());
        ctx.drawCenteredTextWithShadow(textRenderer,HoloTheme.uiText(HoloText.t("TEXT / HOLOGRAM SETTINGS")),cx,top+2,0xFFF0E3FF);
        ctx.drawCenteredTextWithShadow(textRenderer,HoloTheme.uiText(HoloText.t("FONT • EFFECTS • SKY • WORD WINDOW")),cx,top+12,0xFF9E8CB5);
    }

    @Override public void render(DrawContext ctx,int mouseX,int mouseY,float delta){
        // Translate the entire panel and its controls together during the entrance.
        float enter=Math.min(1f,(System.nanoTime()-openedAt)/260_000_000f);
        float eased=1f-(float)Math.pow(1f-enter,3);
        var matrices=ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate(0f,14f*(1f-eased));
        super.render(ctx,mouseX,mouseY,delta);
        matrices.popMatrix();
    }
    @Override public boolean shouldPause(){return false;}
    @Override public void removed(){LyricHoloClient.saveConfig();super.removed();}

    void refreshOptions(){clearAndInit();}

    private static String state(boolean value){return HoloText.t(value?"ON":"OFF");}
    private static String name(Enum<?> value){return value==null?"DEFAULT":value.name();}
    private static String fontName(HoloConfig.FontStyle style){return switch(style){case PACIFICO->"Pacifico";case SMOOTH->"Smooth";default->style.name();};}
    private static int clamp(int value,int min,int max){return Math.max(min,Math.min(max,value));}
    private static <T extends Enum<T>> T next(T value){T[] values=value.getDeclaringClass().getEnumConstants();return values[((value==null?0:value.ordinal())+1)%values.length];}
}
