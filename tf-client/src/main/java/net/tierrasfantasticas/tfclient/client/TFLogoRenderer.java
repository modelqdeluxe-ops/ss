package net.tierrasfantasticas.tfclient.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * Emblema TF del menú, animado como en la web: flota subiendo y bajando (solo en vertical), tiene un halo dorado y
 * azul que late detrás, y cada pocos segundos lo cruza despacio un destello de luz y brilla la gema de la corona.
 * Las texturas del efecto las genera tools/gen_logo_fx.py.
 */
public final class TFLogoRenderer {
    private static final long FLOAT_MS = 6000L;      // un vaivén completo
    static final long SHINE_CYCLE_MS = 7000L; // cada cuánto pasa el destello
    static final long SHINE_MS = 3300L;       // lo que tarda en cruzar
    private static final int SHINE_COLS = 8;
    private static final int SHINE_ROWS = 5;
    private static final float GLOW_PAD = 0.25f;      // margen del halo (ver gen_logo_fx.py)
    private static final float GEM_X = 0.499f;        // posición de la gema de la corona en el emblema
    private static final float GEM_Y = 0.303f;

    private TFLogoRenderer() {}

    /** Dibuja el emblema centrado en cx, con su borde superior en top y la altura indicada. */
    public static void render(GuiGraphics g, int cx, int top, int height, float alpha) {
        if (alpha <= 0f || !TFTextures.LOGO.ready()) return;
        long now = Util.getMillis();
        float h = height;
        float w = h * TFTextures.LOGO.width() / (float) TFTextures.LOGO.height();

        double phase = (now % FLOAT_MS) / (double) FLOAT_MS * Math.PI * 2;
        float bob = (float) Math.sin(phase) * Math.max(2f, h * 0.035f);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.pose().pushPose();
        g.pose().translate(cx, top + h / 2f + bob, 0f);

        // Halo detrás, latiendo despacio
        if (TFTextures.LOGO_GLOW.ready()) {
            float pulse = 0.55f + 0.35f * (float) (0.5 + 0.5 * Math.sin(now / 6000.0 * Math.PI * 2));
            float gw = w * (1 + 2 * GLOW_PAD);
            float gh = h * (1 + 2 * GLOW_PAD);
            blit(g, TFTextures.LOGO_GLOW, -gw / 2f, -gh / 2f, gw, gh, 0, 0,
                    TFTextures.LOGO_GLOW.width(), TFTextures.LOGO_GLOW.height(), alpha * pulse);
        }

        // Emblema
        blit(g, TFTextures.LOGO, -w / 2f, -h / 2f, w, h, 0, 0, TFTextures.LOGO.width(), TFTextures.LOGO.height(), alpha);

        long cycle = now % SHINE_CYCLE_MS;
        // Destello que cruza el emblema (suma de luz)
        if (cycle < SHINE_MS && TFTextures.LOGO_SHINE.ready()) {
            int frames = SHINE_COLS * SHINE_ROWS;
            // Posición continua entre fotogramas: se funden los dos vecinos para que el destello avance suave
            float position = cycle / (float) SHINE_MS * (frames - 1);
            int frame = Mth.clamp((int) position, 0, frames - 1);
            int next = Math.min(frame + 1, frames - 1);
            float blend = position - frame;
            int fw = TFTextures.LOGO_SHINE.width() / SHINE_COLS;
            int fh = TFTextures.LOGO_SHINE.height() / SHINE_ROWS;
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            blit(g, TFTextures.LOGO_SHINE, -w / 2f, -h / 2f, w, h, (frame % SHINE_COLS) * fw, (frame / SHINE_COLS) * fh,
                    fw, fh, alpha * 0.9f * (1f - blend));
            blit(g, TFTextures.LOGO_SHINE, -w / 2f, -h / 2f, w, h, (next % SHINE_COLS) * fw, (next / SHINE_COLS) * fh,
                    fw, fh, alpha * 0.9f * blend);
            RenderSystem.defaultBlendFunc();
        }

        // Chispa en la gema justo cuando el destello pasa por el centro
        long sparkStart = SHINE_MS / 2 - 200L;
        long sparkLength = 1400L;
        if (cycle >= sparkStart && cycle < sparkStart + sparkLength && TFTextures.SPARKLE.ready()) {
            float t = (cycle - sparkStart) / (float) sparkLength;
            float strength = (float) Math.sin(t * Math.PI);
            float size = h * 0.22f * (0.4f + 0.6f * strength);
            float gx = -w / 2f + w * GEM_X;
            float gy = -h / 2f + h * GEM_Y;
            g.pose().pushPose();
            g.pose().translate(gx, gy, 0f);
            g.pose().mulPose(Axis.ZP.rotationDegrees(t * 45f));
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            blit(g, TFTextures.SPARKLE, -size / 2f, -size / 2f, size, size, 0, 0,
                    TFTextures.SPARKLE.width(), TFTextures.SPARKLE.height(), alpha * strength);
            RenderSystem.defaultBlendFunc();
            g.pose().popPose();
        }

        g.pose().popPose();
        g.setColor(1f, 1f, 1f, 1f);
    }

    /** blit con posición y tamaño en decimales (a través de la matriz) para que el movimiento sea suave. */
    private static void blit(GuiGraphics g, TFTextures texture, float x, float y, float w, float h,
                             int u, int v, int uw, int vh, float alpha) {
        if (alpha <= 0f) return;
        g.setColor(1f, 1f, 1f, Mth.clamp(alpha, 0f, 1f));
        g.pose().pushPose();
        g.pose().translate(x, y, 0f);
        g.pose().scale(w / uw, h / vh, 1f);
        g.blit(texture.id(), 0, 0, u, v, uw, vh, texture.width(), texture.height());
        g.pose().popPose();
    }
}
