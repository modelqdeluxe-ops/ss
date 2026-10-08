package net.tierrasfantasticas.tfclient.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * El cielo animado de la web en los menús: la misma nebulosa de neón sobre el reino de noche, con las mismas capas y
 * tiempos que el fondo de la web (styles.css, «Fondo vivo»):
 * <ul>
 *   <li>la nebulosa base, que se acerca muy despacio (60 s);</li>
 *   <li>la nebulosa A, que se desplaza y respira (24 s), y la B, que aparece y se apaga (15 s);</li>
 *   <li>auroras: tres manchas de luz (rosa, cian y magenta) que cruzan el cielo y cambian su color (18 s);</li>
 *   <li>dos capas de estrellas que bajan muy despacio y titilan a destiempo, solo en el cielo;</li>
 *   <li>de vez en cuando, una estrella fugaz; y la viñeta que oscurece los bordes.</li>
 * </ul>
 * Las imágenes son las de la web (textures/gui/sky).
 */
public final class TFSky {
    private TFSky() {}

    private static float seconds() {
        return (Util.getMillis() % 3_600_000L) / 1000f;
    }

    /** 0 → 1 → 0 suave (como animation-direction: alternate con ease-in-out); period = lo que dura la ida. */
    private static float wave(float t, float period, float phase) {
        return 0.5f - 0.5f * Mth.cos((float) Math.PI * (t + phase) / period);
    }

    /** Dibuja el cielo entero. dim: oscurecido extra para los menús con mucho texto (0 = como en la web). */
    public static void render(GuiGraphics g, int width, int height, float dim) {
        float t = seconds();
        g.fill(0, 0, width, height, 0xFF07061A);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // Nebulosa base: se acerca muy despacio
        float base = wave(t, 60f, 0f);
        cover(TFTextures.SKY_NEBULA, g, width, height, 1f + 0.06f * base, 0.02f * base, -0.01f * base, 1f, 0.62f, 0.3f);

        // Nebulosa A: se desplaza y respira
        float a = wave(t, 24f, 0f);
        cover(TFTextures.SKY_NEBULA_A, g, width, height, 1.16f * (1f + 0.14f * a), Mth.lerp(a, 0.015f, -0.05f),
                Mth.lerp(a, -0.01f, 0.03f), 0.8f * Mth.lerp(a, 0.6f, 1f), 0.62f, 0.3f);
        // Nebulosa B: aparece y se apaga (cambia el color del cielo)
        float b = wave(t, 15f, 0f);
        cover(TFTextures.SKY_NEBULA_B, g, width, height, 1.16f * Mth.lerp(b, 1.06f, 1.16f), Mth.lerp(b, 0.04f, -0.04f),
                Mth.lerp(b, -0.02f, 0.03f), 0.95f * b, 0.62f, 0.3f);

        // Auroras: manchas de luz que se suman (rosa, cian y magenta)
        float c = wave(t, 18f, 0f);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        float glowAlpha = Mth.lerp(c < 0.5f ? c * 2 : 2 - c * 2, 0.55f, 1f);
        float dx = Mth.lerp(c, -0.08f, 0.08f), dy = Mth.lerp(c, 0.04f, -0.05f);
        glow(g, width, height, 0.30f + dx, 0.35f + dy, 0.56f, 0.44f, 0xFF5EDB, 0.34f * glowAlpha);
        glow(g, width, height, 0.70f + dx, 0.55f + dy, 0.48f, 0.40f, 0x45E9FF, 0.26f * glowAlpha);
        glow(g, width, height, 0.55f + dx, 0.20f + dy, 0.60f, 0.48f, 0xE23CC8, 0.30f * glowAlpha);
        RenderSystem.defaultBlendFunc();

        // Estrellas (solo en el cielo: se apagan hacia el valle) y la estrella fugaz
        int scale = (int) Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        stars(g, TFTextures.SKY_STARS_A, width, height, scale, t / 260f, Mth.lerp(wave(t, 4.2f, 0f), 0.35f, 1f), 0);
        stars(g, TFTextures.SKY_STARS_B, width, height, scale, t / 170f, Mth.lerp(wave(t, 3.1f, 1.6f), 0.35f, 1f), 190);
        shootingStar(g, width, height, t, 13f, 4f, 0.68f, 0.09f, 150f);
        shootingStar(g, width, height, t, 17f, 11f, 0.34f, 0.22f, 110f);

        // Hacia abajo, más oscuro (para que se lea lo de encima) y la viñeta en los bordes
        g.fillGradient(0, 0, width, height, TFDraw.argb(0.08f + dim, 0x07061A), TFDraw.argb(Math.min(1f, 0.62f + dim), 0x07061A));
        if (TFTextures.SKY_VIGNETTE.ready()) {
            g.setColor(1f, 1f, 1f, 1f);
            g.blit(TFTextures.SKY_VIGNETTE.id(), 0, 0, width, height, 0f, 0f, TFTextures.SKY_VIGNETTE.width(),
                    TFTextures.SKY_VIGNETTE.height(), TFTextures.SKY_VIGNETTE.width(), TFTextures.SKY_VIGNETTE.height());
        }
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.defaultBlendFunc();
    }

    /**
     * Imagen que cubre la pantalla (como background-size: cover), con un acercamiento extra, un desplazamiento (en
     * fracción de la pantalla) y el punto de la imagen que queda centrado (fx, fy: background-position).
     */
    private static void cover(TFTextures tex, GuiGraphics g, int w, int h, float zoom, float offX, float offY, float alpha,
                              float fx, float fy) {
        if (alpha <= 0.01f || !tex.ready()) return;
        int tw = tex.width(), th = tex.height();
        float s = Math.max(w / (float) tw, h / (float) th) * zoom;
        float drawW = tw * s, drawH = th * s;
        float x = (w - drawW) * fx + offX * w;
        float y = (h - drawH) * fy + offY * h;
        g.setColor(1f, 1f, 1f, Mth.clamp(alpha, 0f, 1f));
        g.pose().pushPose();
        g.pose().translate(x, y, 0f);
        g.pose().scale(s, s, 1f);
        g.blit(tex.id(), 0, 0, 0f, 0f, tw, th, tw, th);
        g.pose().popPose();
        g.setColor(1f, 1f, 1f, 1f);
    }

    /** Mancha de luz de color centrada en (cx, cy) (fracciones de la pantalla), de ancho rw y alto rh. */
    private static void glow(GuiGraphics g, int w, int h, float cx, float cy, float rw, float rh, int rgb, float alpha) {
        if (!TFTextures.SKY_GLOW.ready()) return;
        int tw = TFTextures.SKY_GLOW.width(), th = TFTextures.SKY_GLOW.height();
        float gw = rw * w * 2f, gh = rh * h * 2f;
        g.setColor(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, Mth.clamp(alpha, 0f, 1f));
        g.pose().pushPose();
        g.pose().translate(cx * w - gw / 2f, cy * h - gh / 2f, 0f);
        g.pose().scale(gw / tw, gh / th, 1f);
        g.blit(TFTextures.SKY_GLOW.id(), 0, 0, 0f, 0f, tw, th, tw, th);
        g.pose().popPose();
        g.setColor(1f, 1f, 1f, 1f);
    }

    /**
     * Mosaico de estrellas que baja (progress: vueltas del mosaico) con el titileo (alpha). Las estrellas se apagan hacia
     * abajo como en la web (máscara 30 % → 78 %): se dibujan en franjas con cada vez menos opacidad.
     */
    private static void stars(GuiGraphics g, TFTextures tex, int w, int h, int guiScale, float progress, float alpha, int shiftPx) {
        if (!tex.ready()) return;
        int tw = tex.width(), th = tex.height();
        float tile = 512f / guiScale; // el mismo tamaño en pantalla que en la web
        float fall = (progress % 1f) * tile;
        float shift = shiftPx / (float) guiScale;
        int bands = 10;
        for (int band = 0; band < bands; band++) {
            int y0 = (int) (h * 0.78f * band / bands), y1 = (int) (h * 0.78f * (band + 1) / bands);
            float mid = (y0 + y1) / 2f / h;
            float fade = mid < 0.30f ? 1f : Mth.clamp(1f - (mid - 0.30f) / 0.48f, 0f, 1f);
            if (fade <= 0.02f || y1 <= y0) continue;
            g.enableScissor(0, y0, w, y1);
            g.setColor(1f, 1f, 1f, alpha * fade);
            for (float y = -tile + fall + shift % tile; y < y1; y += tile) {
                if (y + tile < y0) continue;
                for (float x = -(shift % tile); x < w; x += tile) {
                    g.pose().pushPose();
                    g.pose().translate(x, y, 0f);
                    g.pose().scale(tile / tw, tile / th, 1f);
                    g.blit(tex.id(), 0, 0, 0f, 0f, tw, th, tw, th);
                    g.pose().popPose();
                }
            }
            g.disableScissor();
        }
        g.setColor(1f, 1f, 1f, 1f);
    }

    /** Estrella fugaz: cada period segundos (desde delay), en el último 10 % cruza 520 px hacia la izquierda y abajo. */
    private static void shootingStar(GuiGraphics g, int w, int h, float t, float period, float delay, float fx, float fy, float lenPx) {
        if (t < delay || !TFTextures.SKY_GLOW.ready()) return;
        float k = ((t - delay) % period) / period;
        if (k < 0.9f) return;
        float p = (k - 0.9f) / 0.1f;
        float alpha = p < 0.2f ? p / 0.2f : 1f - (p - 0.2f) / 0.8f;
        int scale = (int) Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        float len = lenPx / scale * Mth.lerp(p, 0.3f, 1f), thick = 2.5f;
        float travel = 520f / scale * p;
        float angle = (float) Math.toRadians(-24);
        float x = fx * w - Mth.cos(angle) * travel, y = fy * h - Mth.sin(angle) * travel;
        int tw = TFTextures.SKY_GLOW.width(), th = TFTextures.SKY_GLOW.height();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        g.setColor(1f, 0.9f, 1f, Mth.clamp(alpha, 0f, 1f));
        g.pose().pushPose();
        g.pose().translate(x, y, 0f);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotation(angle));
        g.pose().scale(len / tw, thick / th, 1f);
        g.blit(TFTextures.SKY_GLOW.id(), 0, -th / 2, 0f, 0f, tw, th, tw, th);
        g.pose().popPose();
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.defaultBlendFunc();
    }
}
