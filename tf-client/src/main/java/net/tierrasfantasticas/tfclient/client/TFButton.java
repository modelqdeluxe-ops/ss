package net.tierrasfantasticas.tfclient.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Botón con el estilo de la web: dorado (principal) o azul noche, con esquinas pixeladas. */
public class TFButton extends Button {
    private final boolean primary;

    public TFButton(int x, int y, int width, int height, Component message, OnPress onPress, boolean primary) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.primary = primary;
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float a = this.alpha;
        if (a <= 0.02f) return;
        boolean hot = this.active && this.isHoveredOrFocused();
        int x = getX();
        int y = getY() - (hot ? 1 : 0);
        int w = getWidth();
        int h = getHeight();

        int outline = TFDraw.argb(a, TFDraw.OUTLINE);
        g.fill(x + 1, y, x + w - 1, y + h, outline);
        g.fill(x, y + 1, x + w, y + h - 1, outline);

        int body, top, bottom, text;
        if (primary) {
            body = hot ? TFDraw.GOLD_LIGHT : TFDraw.GOLD;
            top = 0xFFF3C4;
            bottom = TFDraw.GOLD_DEEP;
            text = 0x1A1205;
        } else {
            body = hot ? 0x26305C : 0x131A38;
            top = hot ? 0x4A5A99 : 0x2A3566;
            bottom = 0x0B1020;
            text = hot ? TFDraw.GOLD_LIGHT : TFDraw.QUARTZ;
        }
        if (!this.active) text = 0x7A8094;
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, TFDraw.argb(primary ? a : a * 0.9f, body));
        g.fill(x + 1, y + 1, x + w - 1, y + 2, TFDraw.argb(a, top));
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, TFDraw.argb(a, bottom));

        Font font = Minecraft.getInstance().font;
        Component message = getMessage();
        int textX = x + (w - font.width(message)) / 2;
        int textY = y + (h - 8) / 2;
        g.drawString(font, message, textX, textY, TFDraw.argb(Math.max(a, 0.1f), text), !primary);
    }
}
