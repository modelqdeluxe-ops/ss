/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.client;

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
 * Lo usan los botones de TF (TFPill) y el diálogo de los caballeros. Herramienta para verlo sin abrir el juego: el
 * simulador tools/menu_sim.py (mismas cuentas).
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
     * (1.3.121, el dueño: «al pasar el mouse laguea los botones») Las formas ya calculadas: cada relleno o borde se
     * guarda como una lista de rectángulos (relativos a su esquina) por tamaño, radio y colores, y se vuelve a pintar
     * tal cual. Antes se recalculaba píxel a píxel cada fotograma (decenas de miles de cuentas por botón).
     */
    private record Shape(int kind, int w, int h, int r, int a, int b, int c, int top, int bot) {}

    private static final Map<Shape, int[]> SHAPES = new java.util.LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Shape, int[]> eldest) {
            return size() > 768;
        }
    };

    /** Pinta los rectángulos guardados (x0, y0, x1, y1, color arriba, color abajo) en (x, y). */
    private static void replay(GuiGraphics g, int x, int y, int[] ops) {
        for (int i = 0; i < ops.length; i += 6) {
            if (ops[i + 4] == ops[i + 5]) g.fill(x + ops[i], y + ops[i + 1], x + ops[i + 2], y + ops[i + 3], ops[i + 4]);
            else g.fillGradient(x + ops[i], y + ops[i + 1], x + ops[i + 2], y + ops[i + 3], ops[i + 4], ops[i + 5]);
        }
    }

    /** Donde se apuntan los rectángulos de una forma (y junta los píxeles seguidos del mismo color de una fila). */
    private static final class Ops {
        private int[] a = new int[96];
        private int n;
        private int rx0, rx1, ry, rc;

        void rect(int x0, int y0, int x1, int y1, int c0, int c1) {
            if (x1 <= x0 || y1 <= y0 || ((c0 >>> 24) == 0 && (c1 >>> 24) == 0)) return;
            if (n + 6 > a.length) a = java.util.Arrays.copyOf(a, a.length * 2);
            a[n++] = x0;
            a[n++] = y0;
            a[n++] = x1;
            a[n++] = y1;
            a[n++] = c0;
            a[n++] = c1;
        }

        void put(int x, int y, int color) {
            if (rc != 0 && y == ry && x == rx1 && color == rc) {
                rx1++;
                return;
            }
            flush();
            rx0 = x;
            rx1 = x + 1;
            ry = y;
            rc = color;
        }

        void flush() {
            if (rc != 0) rect(rx0, ry, rx1, ry + 1, rc, rc);
            rc = 0;
        }

        int[] done() {
            flush();
            return java.util.Arrays.copyOf(a, n);
        }
    }

    /**
     * Relleno del rectángulo redondeado (x, y, w, h, radio r) metido `inset` píxeles (lo de dentro del borde), solo en
     * las filas [ya, yb) relativas a y, con degradado vertical de top a bot repartido en esas filas.
     */
    public static void roundFill(GuiGraphics g, int x, int y, int w, int h, int r, int inset, int ya, int yb, int top, int bot) {
        Shape key = new Shape(0, w, h, r, inset, ya, yb, top, bot);
        int[] ops = SHAPES.get(key);
        if (ops == null) {
            ops = buildFill(w, h, r, inset, ya, yb, top, bot);
            SHAPES.put(key, ops);
        }
        replay(g, x, y, ops);
    }

    private static int[] buildFill(int w, int h, int r, int inset, int ya, int yb, int top, int bot) {
        Ops o = new Ops();
        ya = Math.max(ya, inset);
        yb = Math.min(yb, h - inset);
        if (yb <= ya) return o.done();
        float span = yb - ya;
        // Filas rectas: todo el ancho dentro del borde
        int a = Math.max(ya, r);
        int b = Math.min(yb, h - r);
        if (b > a) o.rect(inset, a, w - inset, b, lerp(top, bot, (a - ya) / span), lerp(top, bot, (b - ya) / span));
        // Filas de las esquinas: el centro de un bloque y las esquinas píxel a píxel
        for (int part = 0; part < 2; part++) {
            int r0 = part == 0 ? ya : Math.max(ya, h - r);
            int r1 = part == 0 ? Math.min(yb, r) : yb;
            if (r1 <= r0) continue;
            o.rect(r, r0, w - r, r1, lerp(top, bot, (r0 - ya) / span), lerp(top, bot, (r1 - ya) / span));
            for (int row = r0; row < r1; row++) {
                int c = lerp(top, bot, (row + 0.5f - ya) / span);
                for (int i = 0; i < 2 * r; i++) {
                    int col = i < r ? i : w - 2 * r + i;
                    float k = cov(sdf(col + 0.5f, row + 0.5f, w, h, r) + inset);
                    if (k > 0f) o.put(col, row, withAlpha(c, k));
                }
                o.flush();
            }
        }
        return o.done();
    }

    /** Borde de grosor t con degradado vertical (top arriba, bot abajo) y las esquinas suavizadas. */
    public static void roundRing(GuiGraphics g, int x, int y, int w, int h, int r, int t, int top, int bot) {
        Shape key = new Shape(1, w, h, r, t, 0, 0, top, bot);
        int[] ops = SHAPES.get(key);
        if (ops == null) {
            ops = buildRing(w, h, r, t, top, bot);
            SHAPES.put(key, ops);
        }
        replay(g, x, y, ops);
    }

    private static int[] buildRing(int w, int h, int r, int t, int top, int bot) {
        Ops o = new Ops();
        float fh = h;
        o.rect(0, r, t, h - r, lerp(top, bot, r / fh), lerp(top, bot, (h - r) / fh));
        o.rect(w - t, r, w, h - r, lerp(top, bot, r / fh), lerp(top, bot, (h - r) / fh));
        o.rect(r, 0, w - r, t, top, lerp(top, bot, t / fh));
        o.rect(r, h - t, w - r, h, lerp(top, bot, (h - t) / fh), bot);
        for (int j = 0; j < 2 * r; j++) {
            int row = j < r ? j : h - 2 * r + j;
            int c = lerp(top, bot, (row + 0.5f) / fh);
            for (int i = 0; i < 2 * r; i++) {
                int col = i < r ? i : w - 2 * r + i;
                float d = sdf(col + 0.5f, row + 0.5f, w, h, r);
                float k = cov(d) - cov(d + t);
                if (k > 0f) o.put(col, row, withAlpha(c, k));
            }
            o.flush();
        }
        return o.done();
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
}
