package net.tierrasfantasticas.tfclient.pad;

import java.util.List;
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
    static final java.text.DecimalFormat THOUSANDS = new java.text.DecimalFormat("#,##0",
            java.text.DecimalFormatSymbols.getInstance(java.util.Locale.forLanguageTag("es-ES")));

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

    /** Como wrap, pero si no cabe en maxLines la última línea acaba en «…» (nunca se corta el texto sin avisar). */
    static int wrapEllipsis(GuiGraphics g, String s, int x, int y, int w, int color, int maxLines) {
        List<FormattedCharSequence> all = font().split(Component.literal(s), w);
        if (all.size() <= maxLines) return wrap(g, s, x, y, w, color, maxLines);
        // las primeras líneas tal cual; la última, con lo que queda del texto recortado
        int pos = 0;
        for (int i = 0; i < maxLines - 1; i++) {
            StringBuilder b = new StringBuilder();
            all.get(i).accept((idx, style, cp) -> {
                b.appendCodePoint(cp);
                return true;
            });
            if (g != null) g.drawString(font(), all.get(i), x, y + i * 10, color, false);
            String line = b.toString().strip();
            int at = s.indexOf(line, pos);
            pos = at < 0 ? Math.min(s.length(), pos + line.length()) : at + line.length();
        }
        String rest = s.substring(Math.min(s.length(), pos)).stripLeading();
        if (g != null) text(g, fitEnd(rest, w), x, y + (maxLines - 1) * 10, color);
        return maxLines;
    }

    static int lines(String s, int w) {
        return font().split(Component.literal(s), w).size();
    }

    /** Recorta el texto (letra de Minecraft) por el final con «…» para que quepa en w. */
    static String fitEnd(String s, int w) {
        Font font = font();
        if (font.width(s) <= w) return s;
        while (!s.isEmpty() && font.width(s + "…") > w) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    /** Recorta por el principio (para lo que se está escribiendo: se ve el final). */
    static String fitStart(String s, int w) {
        Font font = font();
        if (font.width(s) <= w) return s;
        while (!s.isEmpty() && font.width("…" + s) > w) s = s.substring(1);
        return "…" + s;
    }

    /** Campo de texto de 15 de alto: lo escrito (o la pista en gris) y el cursor que parpadea. */
    static void field(GuiGraphics g, int x, int y, int w, String text, String hint) {
        box(g, x, y, w, 15, NAVY);
        box(g, x + 1, y + 1, w - 2, 13, 0xFFFFFFFF);
        g.fill(x + 2, y + 1, x + w - 2, y + 2, 0xFFD6E6F6);
        String shown = text.isEmpty() ? fitEnd(hint, w - 10) : fitStart(text, w - 10);
        text(g, shown, x + 5, y + 4, text.isEmpty() ? 0xFF96AACC : TEXT);
        if ((System.currentTimeMillis() / 500) % 2 == 0) {
            int cx = x + 5 + (text.isEmpty() ? 0 : font().width(shown));
            g.fill(cx, y + 3, cx + 1, y + 12, TEXT);
        }
    }

    /** Indicador de «cargando»: cuatro puntos que se encienden en rueda (centrado en cx, desde y). */
    static void spinner(GuiGraphics g, int cx, int y) {
        int step = (int) (System.currentTimeMillis() / 140 % 4);
        int[][] at = {{0, -3}, {3, 0}, {0, 3}, {-3, 0}};
        for (int i = 0; i < 4; i++) {
            int c = i == step ? 0xFF3496FA : 0xFFAAC8E8;
            g.fill(cx + at[i][0] - 1, y + 4 + at[i][1] - 1, cx + at[i][0] + 1, y + 4 + at[i][1] + 1, c);
        }
    }

    static boolean inside(double x, double y, int x0, int y0, int w, int h) {
        return x >= x0 && y >= y0 && x < x0 + w && y < y0 + h;
    }
}
