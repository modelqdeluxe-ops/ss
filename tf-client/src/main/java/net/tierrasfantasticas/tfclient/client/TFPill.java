/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Los botones de TF (1.3.120, el dueño: «rework completo de los botones del menú principal, todos; no me gustan las
 * texturas actuales; como los de los diálogos de los NPC, pero mejor diseñados, con mejor efecto»): la pastilla de
 * cristal oscuro con hilo de oro de las respuestas de los caballeros, sin texturas, dibujada en píxeles reales de
 * pantalla ({@link TFMenuPaint}: nítida con cualquier escala de interfaz).
 * <ul>
 *   <li>Debajo, una sombra suave; con el ratón encima, un halo del color del botón y el relleno que se enciende con un
 *   fundido.</li>
 *   <li>(1.3.121, el dueño: «los de las redes no están con su color, el de salir tampoco») Salir, WhatsApp, Web y
 *   Discord rellenos de su color siempre (rojo, verde, morado y el azul de Discord), no solo con el ratón.</li>
 *   <li>Cristal: luz arriba y sombra abajo, dentro del hilo; el hilo de oro con degradado (más claro con el ratón).</li>
 *   <li>El principal (Tierras Fantásticas): doble hilo y siempre un halo de oro tenue.</li>
 *   <li>Hilos de oro a los lados del texto (como el nombre de los caballeros) que crecen hacia las puntas con el ratón
 *   (en el principal, siempre) y acaban en un rombo.</li>
 * </ul>
 * (1.3.121, el dueño: «quítales esa animación de gema reluciente, los blanquitos que van de izquierda a derecha, porque
 * laguea») Sin destello. Las esquinas y el borde se calculan una vez por tamaño y color ({@link TFMenuPaint}).
 * Vista previa sin abrir el juego: tools/menu_sim.py (mismas cuentas).
 */
public final class TFPill {
    private TFPill() {}

    /** Colores de cada estilo: relleno (arriba, abajo), relleno con el ratón, hilo, hilo con el ratón y halo. */
    public enum Style {
        GOLD(0x2C1A4A, 0x120922, 0x4E2E82, 0x24123E, 0xE9C46E, 0x7A5A22, 0xFFEDB0, 0xE0B456, 0xFFC860),
        VIOLET(0x2A1846, 0x110920, 0x4A2C7E, 0x22123C, 0xD9B465, 0x6E5020, 0xFFE9A8, 0xD8AC50, 0xC9A0FF),
        RED(0x9A2A2E, 0x561018, 0xC23A3C, 0x701620, 0xD9B465, 0x6E5020, 0xFFC0B0, 0xD8604A, 0xFF6A50),
        GREEN(0x1E9A52, 0x0B5A2C, 0x28BE66, 0x12763C, 0xD9B465, 0x6E5020, 0xC0F6D6, 0x48C080, 0x5CE89A),
        PURPLE(0x6E36B8, 0x381670, 0x8A4AE0, 0x4A2090, 0xD9B465, 0x6E5020, 0xFFE9A8, 0xD8AC50, 0xC9A0FF),
        BLURPLE(0x5865F2, 0x2C34A0, 0x7480FF, 0x3A44C4, 0xD9B465, 0x6E5020, 0xD6DAFF, 0x7C84F4, 0x8C96FF);

        final int top, bot, hotTop, hotBot, ring, ringBot, hotRing, hotRingBot, glow;

        Style(int top, int bot, int hotTop, int hotBot, int ring, int ringBot, int hotRing, int hotRingBot, int glow) {
            this.top = top;
            this.bot = bot;
            this.hotTop = hotTop;
            this.hotBot = hotBot;
            this.ring = ring;
            this.ringBot = ringBot;
            this.hotRing = hotRing;
            this.hotRingBot = hotRingBot;
            this.glow = glow;
        }
    }

    /** La escala de la interfaz (píxeles reales por píxel de interfaz), entera. */
    public static int scale() {
        return Math.max(1, (int) Math.round(Minecraft.getInstance().getWindow().getGuiScale()));
    }

    private static int rgb(int a, int b, float t) {
        return TFMenuPaint.lerp(0xFF000000 | a, 0xFF000000 | b, t) & 0xFFFFFF;
    }

    /**
     * La pastilla (sin el texto) en (x, y, w, h) de interfaz. e: lo encendida que está con el ratón (0..1); pressed: se
     * está pulsando; primary: doble hilo y halo de oro siempre; alpha: la opacidad del botón (el menú entra con un
     * fundido).
     */
    public static void paint(GuiGraphics g, int x, int y, int w, int h, Style st, float e, boolean pressed, boolean active, boolean primary,
                             float alpha) {
        if (alpha <= 0.01f || w <= 2 || h <= 2) return;
        int s = scale();
        int bx = x * s, by = y * s, bw = w * s, bh = h * s;
        int r = Math.min(bw, bh) / 2;
        int t = Math.max(1, Math.round(s * 0.6f));
        float k = active ? (pressed ? 0.6f * e : e) : 0f;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.pose().pushPose();
        g.pose().scale(1f / s, 1f / s, 1f);
        // sombra y halo (texturas: van antes que los rellenos, que se pintan juntos al final)
        TFMenuPaint.halo(g, bx, by + Math.max(1, s / 2), bw, bh, r, Math.round(4 * s), TFMenuPaint.argb(0.55f * alpha, 0x000000));
        float glow = !active ? 0f : pressed ? 0.5f * e : e;
        if (primary && active) glow = Math.max(glow, 0.27f);
        if (glow > 0.01f) TFMenuPaint.halo(g, bx, by, bw, bh, r, Math.round(7 * s), TFMenuPaint.argb(0.55f * glow * alpha, st.glow));
        g.drawManaged(() -> {
            int top = active ? rgb(st.top, st.hotTop, k) : 0x26222E, bot = active ? rgb(st.bot, st.hotBot, k) : 0x15131A;
            TFMenuPaint.roundFill(g, bx, by, bw, bh, r, 0, 0, bh, TFMenuPaint.argb(0.93f * alpha, top), TFMenuPaint.argb(0.95f * alpha, bot));
            // cristal: luz arriba y sombra abajo, dentro del hilo
            TFMenuPaint.roundFill(g, bx, by, bw, bh, r, t, t, bh / 2, TFMenuPaint.argb((0.13f + 0.07f * k) * alpha, 0xFFFFFF),
                    TFMenuPaint.argb(0f, 0xFFFFFF));
            TFMenuPaint.roundFill(g, bx, by, bw, bh, r, t, bh * 3 / 4, bh - t, TFMenuPaint.argb(0f, 0x000000), TFMenuPaint.argb(0.22f * alpha, 0x000000));
            // el hilo de oro (y en el principal, otro fino por dentro)
            int rt = active ? rgb(st.ring, st.hotRing, k) : 0x6A6470, rb = active ? rgb(st.ringBot, st.hotRingBot, k) : 0x3A3640;
            TFMenuPaint.roundRing(g, bx, by, bw, bh, r, t, TFMenuPaint.argb(0.95f * alpha, rt), TFMenuPaint.argb(0.95f * alpha, rb));
            if (primary) {
                int in = t + Math.max(1, Math.round(s * 1.2f));
                if (bw > 2 * in + 4 && bh > 2 * in + 4) {
                    TFMenuPaint.roundRing(g, bx + in, by + in, bw - 2 * in, bh - 2 * in, r - in, 1, TFMenuPaint.argb((0.45f + 0.35f * k) * alpha, rt),
                            TFMenuPaint.argb((0.25f + 0.3f * k) * alpha, rb));
                }
            }
        });
        g.pose().popPose();
    }

    /**
     * Hilos de oro a los dos lados del texto (de tx0 a tx1, en interfaz) del botón (x, y, w, h): salen del texto hacia
     * las puntas (amount de 0 a 1), se apagan hacia fuera y acaban en un rombo pequeño; e: el ratón (más claros).
     */
    public static void threads(GuiGraphics g, int x, int y, int w, int h, float tx0, float tx1, float amount, float e, float alpha) {
        if (amount <= 0.01f || alpha <= 0.01f) return;
        int s = scale();
        int bx = x * s, by = y * s, bw = w * s, bh = h * s, r = Math.min(bw, bh) / 2;
        int th = Math.max(1, Math.round(s * 0.5f));
        int cy = by + bh / 2 - th / 2;
        int gap = Math.round(5 * s), d = Math.max(1, Math.round(s * 0.8f));
        int col = rgb(0xE9C46E, 0xFFF0C0, e);
        float shown = Math.min(1f, amount * 1.5f) * alpha;
        g.pose().pushPose();
        g.pose().scale(1f / s, 1f / s, 1f);
        g.drawManaged(() -> {
            for (int side = -1; side <= 1; side += 2) {
                float start = side < 0 ? Math.round(tx0 * s) - gap : Math.round(tx1 * s) + gap;
                float end = side < 0 ? bx + r : bx + bw - r;
                float length = Math.abs(end - start);
                if (length < 4 * s) continue;
                float len = length * amount;
                int steps = 12;
                for (int i = 0; i < steps; i++) {
                    float a0 = i / (float) steps, a1 = (i + 1) / (float) steps;
                    float a = 0.95f * (float) Math.pow(1f - a0, 1.3f) * shown;
                    int p0, p1;
                    if (side < 0) {
                        p0 = Math.round(start - a1 * len);
                        p1 = Math.round(start - a0 * len);
                    } else {
                        p0 = Math.round(start + a0 * len);
                        p1 = Math.round(start + a1 * len);
                    }
                    if (p1 > p0) g.fill(p0, cy, p1, cy + th, TFMenuPaint.argb(a, col));
                }
                int tip = Math.round(side < 0 ? start - len : start + len), ty = cy + th / 2;
                for (int j = -d; j <= d; j++) {
                    int hw = d - Math.abs(j);
                    g.fill(tip - hw, ty + j, tip + hw + 1, ty + j + 1, TFMenuPaint.argb(shown, col));
                }
            }
        });
        g.pose().popPose();
    }

    /** El tirador de un deslizador: una pastilla de oro pequeña (más clara con el ratón). */
    public static void knob(GuiGraphics g, int x, int y, int w, int h, boolean hot, boolean active) {
        int s = scale();
        int bx = x * s, by = y * s, bw = w * s, bh = h * s, r = Math.min(bw, bh) / 2;
        int t = Math.max(1, Math.round(s * 0.6f));
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.pose().pushPose();
        g.pose().scale(1f / s, 1f / s, 1f);
        g.drawManaged(() -> {
            int top = !active ? 0xFF8A8494 : hot ? 0xFFFFF0C0 : 0xFFF0CF7E, bot = !active ? 0xFF4A4650 : hot ? 0xFFE0B456 : 0xFFB0802E;
            TFMenuPaint.roundFill(g, bx, by, bw, bh, r, 0, 0, bh, top, bot);
            TFMenuPaint.roundRing(g, bx, by, bw, bh, r, t, 0xFF3A2306, 0xFF2A1604);
        });
        g.pose().popPose();
    }
}
