package net.tierrasfantasticas.tfclient.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** Utilidades de dibujo compartidas por la pantalla de carga y los menús. */
public final class TFDraw {
    private TFDraw() {}

    /** Color ARGB con la opacidad indicada (0..1). */
    public static int argb(float alpha, int rgb) {
        int a = (int) (Mth.clamp(alpha, 0f, 1f) * 255f);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    /** Imagen a pantalla completa, recortada para cubrirla sin deformarse. */
    public static boolean cover(GuiGraphics g, TFTextures texture, int width, int height, float alpha) {
        if (alpha <= 0f || !texture.ready()) return false;
        int tw = texture.width();
        int th = texture.height();
        float scale = Math.max(width / (float) tw, height / (float) th);
        int drawW = (int) Math.ceil(tw * scale);
        int drawH = (int) Math.ceil(th * scale);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1f, 1f, 1f, alpha);
        g.blit(texture.id(), (width - drawW) / 2, (height - drawH) / 2, drawW, drawH, 0f, 0f, tw, th, tw, th);
        g.setColor(1f, 1f, 1f, 1f);
        return true;
    }

    /** Imagen escalada a la altura indicada, centrada en horizontal en cx y con su borde superior en top. */
    public static void image(GuiGraphics g, TFTextures texture, int cx, int top, int drawHeight, float alpha) {
        if (alpha <= 0f || !texture.ready()) return;
        int tw = texture.width();
        int th = texture.height();
        int drawW = Math.round(tw * (drawHeight / (float) th));
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1f, 1f, 1f, alpha);
        g.blit(texture.id(), cx - drawW / 2, top, drawW, drawHeight, 0f, 0f, tw, th, tw, th);
        g.setColor(1f, 1f, 1f, 1f);
    }

    /** Barra de progreso como la de Minecraft (borde y relleno blancos), con un fondo oscuro para que se lea. */
    public static void progressBar(GuiGraphics g, int minX, int minY, int maxX, int maxY, float progress, float alpha) {
        g.fill(minX - 3, minY - 3, maxX + 3, maxY + 3, argb(alpha * 0.5f, 0x000000));
        int white = argb(alpha, 0xFFFFFF);
        int filled = Mth.ceil((maxX - minX - 2) * Mth.clamp(progress, 0f, 1f));
        g.fill(minX + 2, minY + 2, minX + filled, maxY - 2, white);
        g.fill(minX + 1, minY, maxX - 1, minY + 1, white);
        g.fill(minX + 1, maxY, maxX - 1, maxY - 1, white);
        g.fill(minX, minY, minX + 1, maxY, white);
        g.fill(maxX, minY, maxX - 1, maxY, white);
    }
}
