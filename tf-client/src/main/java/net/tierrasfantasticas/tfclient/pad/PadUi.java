package net.tierrasfantasticas.tfclient.pad;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Piezas de dibujo del pad, todas en «píxeles de pad» y con el mismo estilo que las fichas: esquinas recortadas,
 * borde azul marino, luz arriba y sombra abajo.
 */
final class PadUi {
    static final int NAVY = 0xFF18265C;
    static final int TEXT = 0xFF18265C;
    static final int MUTED = 0xFF4A6694;
    static final int PANEL = 0xFFE8F8FF;

    /** Colores de botón: cara, luz, sombra (por estilo de PadView). */
    private static final int[][] BTN = {
            {0xFF3496FA, 0xFF96D6FF, 0xFF1854BE},   // azul
            {0xFFF6B628, 0xFFFFEC96, 0xFFBA7014},   // oro
            {0xFFE83446, 0xFFFF9C9C, 0xFF9A1A30},   // rojo
            {0xFF40C850, 0xFFA0F078, 0xFF1E7C2C},   // verde
            {0xFFAABAD2, 0xFFD6E2F0, 0xFF7E8CA8},   // gris (desactivado)
    };

    private PadUi() {}

    static Font font() {
        return Minecraft.getInstance().font;
    }

    /** Rectángulo con esquinas recortadas de 1 px. */
    static void box(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 1, y, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + w, y + h - 1, color);
    }

    /** Panel blanco con borde, luz arriba y sombra azul debajo. */
    static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 2, y + h, x + w - 2, y + h + 1, 0xFF2882D2);
        box(g, x, y, w, h, NAVY);
        box(g, x + 1, y + 1, w - 2, h - 2, PANEL);
        g.fill(x + 2, y + 1, x + w - 2, y + 2, 0xFFFFFFFF);
        g.fill(x + 2, y + h - 2, x + w - 2, y + h - 1, 0xFFAAD8F8);
    }

    static int buttonWidth(String label) {
        return Math.max(36, PadFont.width(label) + 14);
    }

    /** Botón de 16 de alto (15 + 1 de sombra). */
    static void button(GuiGraphics g, int x, int y, int w, String label, int style, boolean hover, boolean enabled) {
        int[] c = BTN[enabled ? Math.max(0, Math.min(3, style)) : 4];
        int face = c[0], light = c[1], dark = c[2];
        if (hover && enabled) {
            face = BTN[1][0];
            light = BTN[1][1];
            dark = BTN[1][2];
            if (style == PadView.GOLD) {
                face = 0xFFFFD36A;
            }
        }
        g.fill(x + 2, y + 15, x + w - 2, y + 16, 0xFF2882D2);
        box(g, x, y, w, 15, NAVY);
        box(g, x + 1, y + 1, w - 2, 13, face);
        g.fill(x + 2, y + 1, x + w - 2, y + 2, light);
        g.fill(x + 2, y + 12, x + w - 2, y + 14, dark);
        PadFont.drawCentered(g, label, x + w / 2, y + 2, 0xFFFFFF, true);
    }

    /** Barra de progreso (verde; oro cuando está completa). */
    static void progress(GuiGraphics g, int x, int y, int w, float f) {
        f = Math.max(0, Math.min(1, f));
        box(g, x, y, w, 6, NAVY);
        g.fill(x + 1, y + 1, x + w - 1, y + 5, 0xFFC8DCF0);
        int fw = Math.round((w - 2) * f);
        if (fw > 0) {
            boolean full = f >= 1;
            g.fill(x + 1, y + 1, x + 1 + fw, y + 5, full ? 0xFFF6B628 : 0xFF40C850);
            g.fill(x + 1, y + 1, x + 1 + fw, y + 2, full ? 0xFFFFEC96 : 0xFFA0F078);
        }
    }

    /** Texto con la letra de Minecraft, sin sombra (sobre fondo claro). */
    static void text(GuiGraphics g, String s, int x, int y, int color) {
        g.drawString(font(), s, x, y, color, false);
    }

    /** Texto partido en líneas de ancho w; devuelve cuántas líneas ocupó. */
    static int wrap(GuiGraphics g, String s, int x, int y, int w, int color, int maxLines) {
        int n = 0;
        for (FormattedCharSequence line : font().split(Component.literal(s), w)) {
            if (n >= maxLines) break;
            if (g != null) g.drawString(font(), line, x, y + n * 10, color, false);
            n++;
        }
        return n;
    }

    static int lines(String s, int w) {
        return font().split(Component.literal(s), w).size();
    }

    static boolean inside(double x, double y, int x0, int y0, int w, int h) {
        return x >= x0 && y >= y0 && x < x0 + w && y < y0 + h;
    }
}
