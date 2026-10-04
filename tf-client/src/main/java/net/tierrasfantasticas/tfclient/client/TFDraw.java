package net.tierrasfantasticas.tfclient.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** Utilidades de dibujo compartidas por la pantalla de carga y el menú. */
public final class TFDraw {
    public static final int NIGHT = 0x070B18;
    public static final int OUTLINE = 0x05070E;
    public static final int GOLD = 0xF4B52E;
    public static final int GOLD_LIGHT = 0xFFD667;
    public static final int GOLD_DEEP = 0xB87412;
    public static final int QUARTZ = 0xEEF0F3;
    public static final int MUTED = 0xA3ABBD;

    private TFDraw() {}

    /** Color ARGB con la opacidad indicada (0..1). */
    public static int argb(float alpha, int rgb) {
        int a = (int) (Mth.clamp(alpha, 0f, 1f) * 255f);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    /**
     * Banner de fondo a pantalla completa (recortado para cubrir), con un zoom lento y un leve parallax
     * con el ratón. {@code dim} oscurece la imagen (0 = original).
     */
    public static void background(GuiGraphics g, int width, int height, float alpha, long now, int mouseX, int mouseY, float dim) {
        if (!TFTextures.ensureLoaded() || alpha <= 0f) return;
        int bw = TFTextures.backgroundWidth();
        int bh = TFTextures.backgroundHeight();
        float zoom = 1.05f + 0.025f * (float) Math.sin(now / 9000.0);
        float scale = Math.max(width / (float) bw, height / (float) bh) * zoom;
        float px = 0f, py = 0f;
        if (mouseX >= 0 && mouseY >= 0) {
            px = (mouseX / (float) width - 0.5f) * -8f;
            py = (mouseY / (float) height - 0.5f) * -5f;
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1f, 1f, 1f, alpha);
        g.pose().pushPose();
        g.pose().translate(width / 2f + px, height / 2f + py, 0f);
        g.pose().scale(scale, scale, 1f);
        g.blit(TFTextures.BACKGROUND, -bw / 2, -bh / 2, 0f, 0f, bw, bh, bw, bh);
        g.pose().popPose();
        g.setColor(1f, 1f, 1f, 1f);
        if (dim > 0f) g.fill(0, 0, width, height, argb(dim * alpha, NIGHT));
    }

    /** Emblema TF centrado en (cx, cy) con la altura indicada. */
    public static void logo(GuiGraphics g, float cx, float cy, float height, float alpha) {
        if (!TFTextures.ensureLoaded() || alpha <= 0f) return;
        int lw = TFTextures.logoWidth();
        int lh = TFTextures.logoHeight();
        float scale = height / lh;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1f, 1f, 1f, alpha);
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0f);
        g.pose().scale(scale, scale, 1f);
        g.blit(TFTextures.LOGO, -lw / 2, -lh / 2, 0f, 0f, lw, lh, lw, lh);
        g.pose().popPose();
        g.setColor(1f, 1f, 1f, 1f);
    }

    /** Barra de progreso dorada con borde pixelado y un brillo que la recorre. */
    public static void progressBar(GuiGraphics g, int x, int y, int width, int height, float progress, float alpha, long now) {
        int filled = Math.round(width * Mth.clamp(progress, 0f, 1f));
        g.fill(x - 2, y - 1, x + width + 2, y + height + 1, argb(alpha, OUTLINE));
        g.fill(x - 1, y - 2, x + width + 1, y + height + 2, argb(alpha, OUTLINE));
        g.fill(x, y, x + width, y + height, argb(alpha * 0.85f, 0x1C2647));
        if (filled > 0) {
            g.fill(x, y, x + filled, y + height, argb(alpha, GOLD));
            g.fill(x, y, x + filled, y + 1, argb(alpha, GOLD_LIGHT));
            g.fill(x, y + height - 1, x + filled, y + height, argb(alpha, GOLD_DEEP));
            int span = Math.max(12, width / 6);
            int shine = (int) ((now / 4L) % (width + span)) - span;
            int s0 = Math.max(x, x + shine);
            int s1 = Math.min(x + filled, x + shine + span / 3);
            if (s1 > s0) g.fill(s0, y, s1, y + height, argb(alpha * 0.55f, 0xFFFFFF));
        }
    }
}
