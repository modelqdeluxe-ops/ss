/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * Dibujo de los botones del menú principal en píxeles reales de pantalla (no en píxeles de interfaz): así los bordes
 * finos, las esquinas redondeadas suavizadas y los halos se ven nítidos con cualquier escala de interfaz y resolución.
 * Quien llama escala la pose a 1/escala y pasa coordenadas en píxeles reales. Relleno y borde se hacen con
 * rectángulos (las esquinas píxel a píxel, juntando los iguales); los halos con una textura 9-slice generada para
 * cada tamaño y dibujada 1:1 (las tiras del medio solo se estiran a lo largo, donde no cambian).
 * Herramienta para verlo sin abrir el juego: el simulador tools/menu_sim.py (mismas cuentas).
 */
public final class TFMenuPaint {
    private TFMenuPaint() {}

    // --------------------------------------------------------------------------------------------- colores

    public static int argb(float alpha, int rgb) {
        return (Math.round(Mth.clamp(alpha, 0f, 1f) * 255f) << 24) | (rgb & 0xFFFFFF);
    }

    /** Multiplica la opacidad de un color ARGB. */
    public static int withAlpha(int c, float k) {
        return argb(((c >>> 24) & 255) / 255f * k, c);
    }

    /** Mezcla dos colores ARGB canal a canal (t de 0 a 1). */
    public static int lerp(int c0, int c1, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int out = 0;
        for (int sh = 24; sh >= 0; sh -= 8) {
            int a = (c0 >>> sh) & 255;
            int b = (c1 >>> sh) & 255;
            out |= Math.round(a + (b - a) * t) << sh;
        }
        return out;
    }

    // --------------------------------------------------------------------------------------------- forma

    /** Distancia con signo al rectángulo redondeado w x h de radio r desde (px, py); negativa dentro. */
    static float sdf(float px, float py, int w, int h, float r) {
        float qx = Math.abs(px - w / 2f) - (w / 2f - r);
        float qy = Math.abs(py - h / 2f) - (h / 2f - r);
        float ox = Math.max(qx, 0f);
        float oy = Math.max(qy, 0f);
        return (float) Math.sqrt(ox * ox + oy * oy) + Math.min(Math.max(qx, qy), 0f) - r;
    }

    /** Cuánto cubre la forma un píxel cuyo centro está a distancia d del borde. */
    static float cov(float d) {
        return Mth.clamp(0.5f - d, 0f, 1f);
    }

    /**
     * Relleno del rectángulo redondeado (x, y, w, h, radio r) metido `inset` píxeles (lo de dentro del borde), solo en
     * las filas [ya, yb) relativas a y, con degradado vertical de top a bot repartido en esas filas.
     */
    public static void roundFill(GuiGraphics g, int x, int y, int w, int h, int r, int inset, int ya, int yb, int top, int bot) {
        ya = Math.max(ya, inset);
        yb = Math.min(yb, h - inset);
        if (yb <= ya) return;
        float span = yb - ya;
        // Filas rectas: todo el ancho dentro del borde
        int a = Math.max(ya, r);
        int b = Math.min(yb, h - r);
        if (b > a) g.fillGradient(x + inset, y + a, x + w - inset, y + b, lerp(top, bot, (a - ya) / span), lerp(top, bot, (b - ya) / span));
        // Filas de las esquinas: el centro de un bloque y las esquinas píxel a píxel
        Runs runs = new Runs(g);
        for (int part = 0; part < 2; part++) {
            int r0 = part == 0 ? ya : Math.max(ya, h - r);
            int r1 = part == 0 ? Math.min(yb, r) : yb;
            if (r1 <= r0) continue;
            g.fillGradient(x + r, y + r0, x + w - r, y + r1, lerp(top, bot, (r0 - ya) / span), lerp(top, bot, (r1 - ya) / span));
            for (int row = r0; row < r1; row++) {
                int c = lerp(top, bot, (row + 0.5f - ya) / span);
                for (int i = 0; i < 2 * r; i++) {
                    int col = i < r ? i : w - 2 * r + i;
                    float k = cov(sdf(col + 0.5f, row + 0.5f, w, h, r) + inset);
                    if (k > 0f) runs.put(x + col, y + row, withAlpha(c, k));
                }
                runs.flush();
            }
        }
    }

    /** Borde de grosor t con degradado vertical (top arriba, bot abajo) y las esquinas suavizadas. */
    public static void roundRing(GuiGraphics g, int x, int y, int w, int h, int r, int t, int top, int bot) {
        float fh = h;
        g.fillGradient(x, y + r, x + t, y + h - r, lerp(top, bot, r / fh), lerp(top, bot, (h - r) / fh));
        g.fillGradient(x + w - t, y + r, x + w, y + h - r, lerp(top, bot, r / fh), lerp(top, bot, (h - r) / fh));
        g.fillGradient(x + r, y, x + w - r, y + t, top, lerp(top, bot, t / fh));
        g.fillGradient(x + r, y + h - t, x + w - r, y + h, lerp(top, bot, (h - t) / fh), bot);
        Runs runs = new Runs(g);
        for (int j = 0; j < 2 * r; j++) {
            int row = j < r ? j : h - 2 * r + j;
            int c = lerp(top, bot, (row + 0.5f) / fh);
            for (int i = 0; i < 2 * r; i++) {
                int col = i < r ? i : w - 2 * r + i;
                float d = sdf(col + 0.5f, row + 0.5f, w, h, r);
                float k = cov(d) - cov(d + t);
                if (k > 0f) runs.put(x + col, y + row, withAlpha(c, k));
            }
            runs.flush();
        }
    }

    /** Junta los píxeles seguidos del mismo color de una fila en un solo rectángulo. */
    private static final class Runs {
        private final GuiGraphics g;
        private int x0;
        private int x1;
        private int y;
        private int c;

        Runs(GuiGraphics g) {
            this.g = g;
        }

        void put(int x, int y, int color) {
            if (c != 0 && y == this.y && x == x1 && color == c) {
                x1++;
                return;
            }
            flush();
            x0 = x;
            x1 = x + 1;
            this.y = y;
            c = color;
        }

        void flush() {
            if (c != 0 && (c >>> 24) != 0) g.fill(x0, y, x1, y + 1, c);
            c = 0;
        }
    }

    // --------------------------------------------------------------------------------------------- halo

    /** Texturas de halo ya hechas, por radio y tamaño (en píxeles reales). */
    private static final Map<Long, ResourceLocation> HALOS = new HashMap<>();

    /**
     * Halo suave fuera de la forma (sombra oscura o brillo de color): opacidad (1 - d/size)² según la distancia d al
     * borde, con el color y la opacidad de `color`. Se dibuja con 9 trozos de una textura blanca 1:1.
     */
    public static void halo(GuiGraphics g, int x, int y, int w, int h, int r, int size, int color) {
        float a = ((color >>> 24) & 255) / 255f;
        if (size <= 0 || a <= 0f || w < 2 * r || h < 2 * r) return;
        ResourceLocation id = haloTexture(r, size);
        if (id == null) return;
        int e = size + r;     // lado de cada esquina
        int tex = 2 * e + 1;  // la textura: esquinas + una fila/columna central que se estira
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f, a);
        int midW = w - 2 * r;
        int midH = h - 2 * r;
        g.blit(id, x - size, y - size, e, e, 0, 0, e, e, tex, tex);
        g.blit(id, x + w - r, y - size, e, e, e + 1, 0, e, e, tex, tex);
        g.blit(id, x - size, y + h - r, e, e, 0, e + 1, e, e, tex, tex);
        g.blit(id, x + w - r, y + h - r, e, e, e + 1, e + 1, e, e, tex, tex);
        if (midW > 0) {
            g.blit(id, x + r, y - size, midW, e, e, 0, 1, e, tex, tex);
            g.blit(id, x + r, y + h - r, midW, e, e, e + 1, 1, e, tex, tex);
        }
        if (midH > 0) {
            g.blit(id, x - size, y + r, e, midH, 0, e, e, 1, tex, tex);
            g.blit(id, x + w - r, y + r, e, midH, e + 1, e, e, 1, tex, tex);
        }
        g.setColor(1f, 1f, 1f, 1f);
    }

    /** Textura del halo de un rectángulo redondeado de (2r+1) x (2r+1) con `size` píxeles de halo alrededor. */
    private static ResourceLocation haloTexture(int r, int size) {
        long key = ((long) r << 32) | size;
        ResourceLocation cached = HALOS.get(key);
        if (cached != null) return cached;
        int n = 2 * (size + r) + 1;
        int shape = 2 * r + 1;
        try {
            NativeImage image = new NativeImage(NativeImage.Format.RGBA, n, n, true);
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < n; i++) {
                    float d = sdf(i - size + 0.5f, j - size + 0.5f, shape, shape, r);
                    float k = 0f;
                    if (d > -0.5f && d < size) {
                        float f = 1f - Mth.clamp(d / size, 0f, 1f);
                        k = (1f - cov(d)) * f * f;
                    }
                    image.setPixelRGBA(i, j, (Math.round(k * 255f) << 24) | 0xFFFFFF);
                }
            }
            ResourceLocation id = new ResourceLocation(TFClient.MOD_ID, "dynamic/menu_halo_" + r + "_" + size);
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
            HALOS.put(key, id);
            return id;
        } catch (Throwable t) {
            TFClient.LOGGER.error("TF Client: no se pudo crear el halo de los botones", t);
            HALOS.put(key, null);
            return null;
        }
    }

    // --------------------------------------------------------------------------------------------- destello

    /**
     * Línea luminosa (button_shine.png) que cruza el botón de izquierda a derecha; t de 0 a 1 y fuerza máxima
     * `strength`. En unidades de interfaz; se recorta dentro del borde y lejos de las esquinas redondeadas.
     */
    public static void sweep(GuiGraphics g, int x, int y, int w, int h, float t, float strength) {
        if (!TFTextures.BUTTON_SHINE.ready() || w < 20 || t <= 0f || t >= 1f) return;
        float eased = t * t * (3f - 2f * t); // entra y sale suave
        float bandW = h * 2.4f;
        float bandX = x - bandW + eased * (w + bandW);
        float k = Mth.clamp(strength * (float) Math.sin(t * Math.PI), 0f, 1f); // se enciende y se apaga, nunca de golpe
        if (k <= 0.004f) return;
        g.enableScissor(x + 3, y + 1, x + w - 3, y + h - 1);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        g.setColor(1f, 1f, 1f, k);
        g.pose().pushPose();
        g.pose().translate(bandX, y, 0f);
        int tw = TFTextures.BUTTON_SHINE.width();
        int th = TFTextures.BUTTON_SHINE.height();
        g.pose().scale(bandW / tw, h / (float) th, 1f);
        g.blit(TFTextures.BUTTON_SHINE.id(), 0, 0, 0f, 0f, tw, th, tw, th);
        g.pose().popPose();
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.defaultBlendFunc();
        g.disableScissor();
    }
}
