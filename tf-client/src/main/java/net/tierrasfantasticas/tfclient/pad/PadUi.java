package net.tierrasfantasticas.tfclient.pad;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Piezas de dibujo del pad (1.3.28), en blanco con textura: la ventana de cada app (borde azul noche, bisel, rombos de
 * cristal tallado y esquinas de oro) con sus pestañas de carpeta encima, su barra de información y su barra de acciones;
 * tarjetas en relieve, medallones del color de cada cosa, vitrinas con luz, etiquetas de estado y de precio (con su
 * moneda), botones con degradado y barras redondeadas. Todo en unidades de la página. Textos en azul marino (TEXT),
 * gris azulado (MUTED) y oro (GOLD_TEXT); los títulos, en negrita.
 */
final class PadUi {
    static final int NAVY = 0xFF18265C;
    /** Contorno de botones, pestañas y campos. */
    static final int INK = 0xFF18265C;
    static final int TEXT = 0xFF18265C;
    static final int MUTED = 0xFF4A6694;
    static final int GOLD_TEXT = 0xFFC27A10;
    static final int GREEN_TEXT = 0xFF1E9E46;
    static final int RED_TEXT = 0xFFC8323C;
    /** Texto sobre el cristal azul claro del pad. */
    static final int GLASS_TEXT = 0xFF18265C;
    static final int GOLD = 0xFFF6B628, GOLD_HI = 0xFFFFE58A, GOLD_LO = 0xFFC07818;
    static final int PANEL_TOP = 0xFFF8FCFF, PANEL_BOT = 0xFFDCEEFC, PANEL_HI = 0xFFFFFFFF;
    static final int SLOT = 0xFFE2EEFA, SLOT_EDGE = 0xFF9DBCE0, SLOT_SHADE = 0xFFC2D8EE;
    /** Compatibilidad: el fondo de las ventanas. */
    static final int PANEL = PANEL_TOP;
    static final java.text.DecimalFormat THOUSANDS = new java.text.DecimalFormat("#,##0",
            java.text.DecimalFormatSymbols.getInstance(java.util.Locale.forLanguageTag("es-ES")));

    /** Botones por estilo de PadView: cara arriba, cara abajo, luz, sombra. */
    private static final int[][] BTN = {
            {0xFF5AB4FF, 0xFF2C74E4, 0xFFB4E0FF, 0xFF1A4AA8},   // azul
            {0xFFFFD650, 0xFFE8961A, 0xFFFFF2B0, 0xFFA05E0E},   // oro
            {0xFFFF6274, 0xFFC82038, 0xFFFFB4BC, 0xFF861428},   // rojo
            {0xFF68E886, 0xFF22A84C, 0xFFC4FFD0, 0xFF147034},   // verde
            {0xFF4A5884, 0xFF36426C, 0xFF6E7CA8, 0xFF252E50},   // desactivado (no se puede pulsar)
            {0xFF97A3BF, 0xFF66728F, 0xFFC9D0E2, 0xFF434D69},   // gris (PadView.GRAY: «apagado», «permitido»…)
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

    /** Tachuela de oro de 3x3 con brillo, centrada en (cx, cy). */
    static void stud(GuiGraphics g, int cx, int cy) {
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, GOLD_LO);
        g.fill(cx, cy - 1, cx + 1, cy + 2, GOLD);
        g.fill(cx - 1, cy, cx + 2, cy + 1, GOLD);
        g.fill(cx, cy - 1, cx + 1, cy + 1, GOLD_HI);
    }

    /** El color c (RGB o ARGB) mezclado con blanco: k = 1 el color tal cual, k = 0 blanco. Devuelve ARGB opaco. */
    static int tint(int c, float k) {
        int r = Math.round(255 + ((c >> 16 & 255) - 255) * k), gg = Math.round(255 + ((c >> 8 & 255) - 255) * k);
        int b = Math.round(255 + ((c & 255) - 255) * k);
        return 0xFF000000 | r << 16 | gg << 8 | b;
    }

    /** El dibujo de rombos de cristal tallado (pattern.png, 16x16) en mosaico: la textura se repite sola. */
    static void texture(GuiGraphics g, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        RenderSystem.enableBlend();
        g.blit(TFPadScreen.tex("pattern"), x, y, 0, 0, w, h, 16, 16);
    }

    /** Una textura del pad estirada a w x h (la luz y la sombra de las vitrinas). */
    static void stretch(GuiGraphics g, String name, int x, int y, int w, int h) {
        int[] sz = TFPadScreen.size(name);
        if (sz[0] == 0 || w <= 0 || h <= 0) return;
        RenderSystem.enableBlend();
        g.blit(TFPadScreen.tex(name), x, y, w, h, 0, 0, sz[0], sz[1], sz[0], sz[1]);
    }

    /**
     * La ventana de una app: contorno azul noche, blanco en degradado con bisel (luz arriba, sombra abajo), la textura
     * de cristal tallado y una escuadra de oro en cada esquina.
     */
    static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 2, y + h, x + w - 2, y + h + 1, 0x5A286EC8);
        box(g, x, y, w, h, INK);
        if (w <= 4 || h <= 4) return;
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, PANEL_TOP, PANEL_BOT);
        texture(g, x + 1, y + 1, w - 2, h - 2);
        g.fill(x + 2, y + 1, x + w - 2, y + 2, PANEL_HI);
        g.fill(x + 2, y + h - 2, x + w - 2, y + h - 1, 0xFFC6DAEE);
        if (w >= 16 && h >= 16) corners(g, x, y, w, h);
    }

    /** Escuadras de oro de 4 px en las esquinas de dentro. */
    static void corners(GuiGraphics g, int x, int y, int w, int h) {
        int l = x + 2, r = x + w - 3, t = y + 2, b = y + h - 3;
        g.fill(l, t, l + 4, t + 1, GOLD);
        g.fill(l, t, l + 1, t + 4, GOLD);
        g.fill(r - 3, t, r + 1, t + 1, GOLD);
        g.fill(r, t, r + 1, t + 4, GOLD);
        g.fill(l, b, l + 4, b + 1, GOLD);
        g.fill(l, b - 3, l + 1, b + 1, GOLD);
        g.fill(r - 3, b, r + 1, b + 1, GOLD);
        g.fill(r, b - 3, r + 1, b + 1, GOLD);
        for (int[] p : new int[][] {{l, t}, {r, t}, {l, b}, {r, b}}) g.fill(p[0], p[1], p[0] + 1, p[1] + 1, GOLD_HI);
    }

    /** Tarjeta (fila, casilla, ficha) dentro de una ventana. state: 0 normal, 1 ratón encima, 2 marcada (oro). */
    static void card(GuiGraphics g, int x, int y, int w, int h, int state) {
        int edge = state == 2 ? 0xFFE0A030 : state == 1 ? 0xFF3496FA : 0xFFBBD3EC;
        int top = state == 2 ? 0xFFFFFBEA : state == 1 ? 0xFFF2F9FF : 0xFFFFFFFF;
        int bot = state == 2 ? 0xFFFFEFC0 : state == 1 ? 0xFFDCEEFF : 0xFFF1F7FD;
        g.fill(x + 1, y + h, x + w - 1, y + h + 1, state == 1 ? 0x5A3496FA : 0x2D285AA0); // sombra
        box(g, x, y, w, h, edge);
        if (w > 2 && h > 2) g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, top, bot);
        g.fill(x + 2, y + 1, x + w - 2, y + 2, 0xFFFFFFFF);
    }

    /** Medallón para un objeto: azul hielo, o del color de su fila (color != 0). */
    static void slot(GuiGraphics g, int x, int y, int w, int h, int color) {
        box(g, x, y, w, h, color != 0 ? tint(color, 0.45F) : SLOT_EDGE);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, color != 0 ? tint(color, 0.08F) : 0xFFF0F6FD,
                color != 0 ? tint(color, 0.20F) : SLOT);
        g.fill(x + 2, y + 1, x + w - 2, y + 2, 0xFFFFFFFF);
    }

    static void slot(GuiGraphics g, int x, int y, int w, int h) {
        slot(g, x, y, w, h, 0);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Etiquetas: estado (azul), precio (oro, con su moneda), lo que ganas (verde), no disponible (gris)

    static final int TONE_GOLD = 1, TONE_GREEN = 2, TONE_GRAY = 3, TONE_BLUE = 4;
    private static final int[][] CHIP = {
            {0xFFD69A1E, 0xFFFFF7D8, 0xFFFFE2A0, 0xFF7A4C00},   // oro
            {0xFF3DAA5C, 0xFFEAFBEF, 0xFFC2F0CF, 0xFF136B30},   // verde
            {0xFFB4C6DC, 0xFFF7FAFD, 0xFFE4ECF5, 0xFF5A7398},   // gris
            {0xFF6FA8E8, 0xFFF0F7FF, 0xFFD4E7FC, 0xFF1E4E9C},   // azul
    };

    /**
     * El color de una etiqueta: el que pida el servidor (tone 1 oro, 2 verde, 3 gris, 4 azul) o, si no (0): gris si
     * no se puede pulsar, oro si es un precio («¤…»), verde si es lo que ganas («+…»), azul si es un estado.
     */
    static int tone(String text, boolean on, int tone) {
        if (tone >= 1 && tone <= 4) return tone;
        if (!on) return TONE_GRAY;
        return text.startsWith("¤") ? TONE_GOLD : text.startsWith("+") ? TONE_GREEN : TONE_BLUE;
    }

    /** Ancho de una etiqueta. «¤1.250» lleva la moneda delante; «+¤48», más y la moneda. */
    static int chipWidth(String text) {
        boolean plus = text.startsWith("+¤"), coin = plus || text.startsWith("¤");
        String rest = plus ? text.substring(2) : coin ? text.substring(1) : text;
        return (plus ? font().width("+") : 0) + (coin ? 9 : 0) + font().width(rest) + 8;
    }

    /** La etiqueta recortada (con «…») para que mida como mucho w, contando la moneda y el «+» que lleve delante. */
    static String fitChip(String text, int w) {
        if (chipWidth(text) <= w) return text;
        boolean plus = text.startsWith("+¤"), coin = plus || text.startsWith("¤");
        String head = plus ? text.substring(0, 2) : coin ? text.substring(0, 1) : "";
        String rest = text.substring(head.length());
        int room = w - (chipWidth(head + "x") - font().width("x"));
        return head + fitEnd(rest, Math.max(6, room));
    }

    /** Dibuja la etiqueta (alto h: 11, 12 o 14) del tono dado. Devuelve su ancho. */
    static int chip(GuiGraphics g, int x, int y, String text, int tone, int h) {
        int[] c = CHIP[Math.max(1, Math.min(4, tone)) - 1];
        int w = chipWidth(text);
        box(g, x, y, w, h, c[0]);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, c[1], c[2]);
        boolean plus = text.startsWith("+¤"), coin = plus || text.startsWith("¤");
        String rest = plus ? text.substring(2) : coin ? text.substring(1) : text;
        int tx = x + 4, ty = y + (h - 8) / 2 + 1;
        if (plus) {
            g.drawString(font(), "+", tx, ty, c[3], false);
            tx += font().width("+");
        }
        if (coin) {
            RenderSystem.enableBlend();
            g.blit(TFPadScreen.tex("coin_s"), tx, y + (h - 8) / 2, 0, 0, 8, 8, 8, 8);
            tx += 9;
        }
        g.drawString(font(), rest, tx, ty, c[3], false);
        return w;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Negrita (títulos)

    static Component bold(String s) {
        return Component.literal(s).withStyle(ChatFormatting.BOLD);
    }

    static int boldWidth(String s) {
        return font().width(bold(s));
    }

    /** Título en negrita recortado a w con «...»; si en negrita no cabe pero normal sí, normal. */
    static void title(GuiGraphics g, String s, int x, int y, int w, int color) {
        if (boldWidth(s) <= w) {
            g.drawString(font(), bold(s), x, y, color, false);
        } else if (font().width(s) <= w) {
            g.drawString(font(), s, x, y, color, false);
        } else {
            String cut = s;
            while (!cut.isEmpty() && boldWidth(cut + "...") > w) cut = cut.substring(0, cut.length() - 1);
            g.drawString(font(), bold(cut + "..."), x, y, color, false);
        }
    }

    /** Ancho con el que title() dibuja s en w. */
    static int titleWidth(String s, int w) {
        int b = boldWidth(s);
        if (b <= w) return b;
        return font().width(s) <= w ? font().width(s) : w;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Partes de la ventana

    /**
     * La barra de información arriba de la ventana (lo que el servidor manda en header): un rombo de oro y el texto, en
     * room líneas como mucho (el texto deja 12 libres a la derecha: ahí va la ruedita de «cargando»). Devuelve su alto.
     */
    static int infobar(GuiGraphics g, int x, int y, int w, List<String> header, int room) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (String line : header) {
            for (FormattedCharSequence seq : font().split(Component.literal(line), w - 30)) {
                StringBuilder b = new StringBuilder();
                seq.accept((i, st, cp) -> {
                    b.appendCodePoint(cp);
                    return true;
                });
                lines.add(b.toString());
            }
        }
        if (lines.isEmpty()) return 0;
        if (lines.size() > room) {
            lines = new java.util.ArrayList<>(lines.subList(0, room));
            lines.set(room - 1, fitEnd(lines.get(room - 1) + "...", w - 30));
        }
        int h = lines.size() * 10 + 4;
        g.fillGradient(x, y, x + w, y + h, 0xFFF0F7FE, 0xFFE0EDFA);
        g.fill(x, y + h, x + w, y + h + 1, 0xFFC9DCF0);
        g.fill(x, y + h + 1, x + w, y + h + 2, 0xFFFFFFFF);
        diamond(g, x + 7, y + 6);
        for (int i = 0; i < lines.size(); i++) text(g, lines.get(i), x + 13, y + 3 + i * 10, TEXT);
        return h + 2;
    }

    /** La barra de acciones abajo de la ventana (botones y campo), con su filo arriba. */
    static void dock(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y - 2, x + w, y - 1, 0xFFC6DAEE);
        g.fill(x, y - 1, x + w, y, 0xFFFFFFFF);
        g.fillGradient(x, y, x + w, y + h, 0xFFEAF3FC, 0xFFD8E7F6);
    }

    /** Rombo de oro de 5x5 con centro en (cx, cy). */
    static void diamond(GuiGraphics g, int cx, int cy) {
        g.fill(cx - 2, cy, cx + 3, cy + 1, GOLD_TEXT);
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, GOLD);
        g.fill(cx, cy - 2, cx + 1, cy + 3, GOLD_TEXT);
        g.fill(cx, cy - 1, cx + 1, cy, 0xFFFFEC96);
    }

    /** Compatibilidad: la etiqueta con los estilos de PadView (GOLD, GREEN, GRAY). */
    static int pillWidth(String text) {
        return chipWidth(text);
    }

    static int pill(GuiGraphics g, int x, int y, String text, int style, int h) {
        return chip(g, x, y, text, style == 3 ? TONE_GREEN : style == 4 ? TONE_GRAY : TONE_GOLD, h);
    }

    static int buttonWidth(String label) {
        return Math.max(36, PadFont.width(label) + 14 + (isBack(label) ? 8 : 0));
    }

    /** Los botones de volver llevan una flecha delante. */
    static boolean isBack(String label) {
        return label.startsWith("ATR") || label.equals("VOLVER");
    }

    /** Botón de 16 de alto: tinta, cara en degradado, luz arriba, sombra abajo; al pasar el ratón, más claro. */
    static void button(GuiGraphics g, int x, int y, int w, String label, int style, boolean hover, boolean enabled) {
        // PadView.GRAY (4) es el gris de pulsar (antes salía verde); el de desactivado es otro, más oscuro
        int[] c = BTN[!enabled ? 4 : style == PadView.GRAY ? 5 : Math.max(0, Math.min(3, style))];
        int top = c[0], bot = c[1];
        if (hover && enabled) {
            top = lighten(top, 30);
            bot = lighten(bot, 30);
        }
        box(g, x, y, w, 16, INK);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + 14, top, bot);
        g.fill(x + 2, y + 1, x + w - 2, y + 2, c[2]);
        g.fill(x + 1, y + 13, x + w - 1, y + 15, c[3]);
        int text = enabled ? 0xFFFFFF : 0x8C9AC4;
        if (isBack(label)) {
            int ax = x + 6, ay = y + 7;
            for (int i = 0; i < 4; i++) g.fill(ax + i, ay - i, ax + i + 1, ay + i + 1, 0xFF000000 | text);
            g.fill(ax + 1, ay, ax + 7, ay + 1, 0xFF000000 | text);
            PadFont.drawCentered(g, label, x + w / 2 + 4, y + 2, text, true);
        } else {
            PadFont.drawCentered(g, label, x + w / 2, y + 2, text, true);
        }
    }

    /**
     * Pestaña de carpeta, 15 de alto, para ir encima de una ventana cuyo borde de arriba está en y + 14. state: 0
     * normal, 1 ratón encima, 2 elegida: baja hasta la ventana y se une a ella, blanca y con su filo de oro.
     */
    static void tab(GuiGraphics g, int x, int y, int w, String label, int state) {
        if (state == 2) {
            // su borde llega hasta el de la ventana (y + 14) y por dentro tapa ese borde y la luz de debajo: se unen
            box(g, x, y, w, 15, INK);
            g.fillGradient(x + 1, y + 1, x + w - 1, y + 16, 0xFFFFFFFF, PANEL_TOP);
            g.fill(x + 2, y + 1, x + w - 2, y + 3, GOLD);
            g.fill(x + 2, y + 1, x + w - 2, y + 2, GOLD_HI);
            PadFont.drawCentered(g, label, x + w / 2, y + 4, 0x18265C, false);
        } else {
            box(g, x, y + 2, w, 13, 0xFF6A88B8);
            g.fillGradient(x + 1, y + 3, x + w - 1, y + 14, state == 1 ? 0xFFF2F9FF : 0xFFE2EFFB, state == 1 ? 0xFFCFE3F6 : 0xFFC0D7EE);
            PadFont.drawCentered(g, label, x + w / 2, y + 4, 0x34507E, false);
        }
    }

    /** Título de sección dentro de una ventana: texto en negrita, una línea y un rombo al final. */
    static void divider(GuiGraphics g, int x, int y, int w, String title, int color) {
        g.drawString(font(), bold(title), x, y + 1, color, false);
        int lx = x + boldWidth(title) + 5;
        if (lx < x + w - 8) {
            g.fill(lx, y + 5, x + w - 6, y + 6, 0xFFDAB46E);
            g.fill(lx, y + 6, x + w - 6, y + 7, 0xFFFFFFFF);
        }
        int cx = x + w - 4;
        g.fill(cx - 2, y + 5, cx + 3, y + 6, GOLD_LO);
        g.fill(cx - 1, y + 4, cx + 2, y + 7, GOLD);
        g.fill(cx, y + 3, cx + 1, y + 8, GOLD);
        g.fill(cx, y + 4, cx + 1, y + 5, GOLD_HI);
    }

    /** Barra de desplazamiento de 4: carril redondeado y asa de oro. */
    static void scrollbar(GuiGraphics g, int x, int y, int h, int visible, int content, int scroll) {
        if (content <= visible || h <= 4) return;
        box(g, x, y, 4, h, 0xFFD2E2F2);
        int th = Math.max(10, h * visible / content);
        int ty = y + (h - th) * scroll / Math.max(1, content - visible);
        box(g, x, ty, 4, th, GOLD_LO);
        g.fillGradient(x + 1, ty + 1, x + 3, ty + th - 1, GOLD_HI, GOLD);
    }

    /** Aclara un color ARGB n puntos por canal. */
    static int lighten(int c, int n) {
        int r = Math.min(255, (c >> 16 & 255) + n), gg = Math.min(255, (c >> 8 & 255) + n), b = Math.min(255, (c & 255) + n);
        return c & 0xFF000000 | r << 16 | gg << 8 | b;
    }

    /** Los colores que manda el servidor ya son para fondo claro: se usan tal cual (con su alfa). */
    static int onDark(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    /** Barra de progreso hundida (verde; oro cuando está completa). 6 de alto. */
    static void progress(GuiGraphics g, int x, int y, int w, float f) {
        f = Math.max(0, Math.min(1, f));
        box(g, x, y, w, 6, 0xFF9DB8D8);
        g.fill(x + 1, y + 1, x + w - 1, y + 5, 0xFFDCE8F4);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFFC6D8EC);
        int fw = Math.round((w - 2) * f);
        if (fw > 0) {
            boolean full = f >= 1;
            g.fillGradient(x + 1, y + 1, x + 1 + fw, y + 5, full ? 0xFFFFE070 : 0xFF8CF0A8, full ? 0xFFE08E14 : 0xFF1E9E46);
            g.fill(x + 1, y + 1, x + 1 + fw, y + 2, full ? 0xFFFFF6C0 : 0xFFD8FFE2);
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

    /** Campo de texto hundido de 16 de alto: lo escrito (o la pista) y el cursor de oro que parpadea. */
    static void field(GuiGraphics g, int x, int y, int w, String text, String hint) {
        box(g, x, y, w, 16, INK);
        g.fill(x + 1, y + 1, x + w - 1, y + 15, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFFD6E6F6);
        g.fill(x + 1, y + 2, x + 2, y + 15, 0xFFE6EFF8);
        String shown = text.isEmpty() ? fitEnd(hint, w - 10) : fitStart(text, w - 10);
        text(g, shown, x + 5, y + 4, text.isEmpty() ? 0xFF96AACC : TEXT);
        if ((System.currentTimeMillis() / 500) % 2 == 0) {
            int cx = x + 5 + (text.isEmpty() ? 0 : font().width(shown));
            g.fill(cx, y + 3, cx + 1, y + 13, TEXT);
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

    /**
     * Un objeto a escala k (1 o 2: nítido) con su barra de durabilidad a esa escala, pero el número de cantidad siempre
     * a tamaño normal en la esquina de abajo a la derecha (al doble salía enorme).
     */
    static void item(GuiGraphics g, net.minecraft.world.item.ItemStack stack, int x, int y, int k) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(k, k, 1);
        g.renderItem(stack, 0, 0);
        g.renderItemDecorations(font(), stack, 0, 0, "");
        g.pose().popPose();
        // la cantidad: en blanco sobre una etiqueta azul noche, sin sombra (se lee sobre cualquier fondo)
        if (stack.getCount() > 1) {
            String n = Integer.toString(stack.getCount());
            int w = font().width(n) + 2, rx = x + 16 * k + 1 - w, ry = y + 16 * k - 8;
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            box(g, rx, ry, w + 1, 9, 0xFF22346E);
            g.drawString(font(), n, rx + 1, ry + 1, 0xFFFFFFFF, false);
            g.pose().popPose();
        }
    }

    /**
     * Cabeza de jugador sin skin (el servidor no la sabía): si ese jugador está en la lista de jugadores del cliente, se
     * le pone el perfil que ya tiene el cliente (con su skin, también la de SkinsRestorer).
     */
    static void skin(net.minecraft.world.item.ItemStack stack) {
        if (!stack.is(net.minecraft.world.item.Items.PLAYER_HEAD) || stack.getTag() == null) return;
        net.minecraft.nbt.CompoundTag tag = stack.getTag();
        if (!tag.contains("SkullOwner", 10)) return;
        net.minecraft.nbt.CompoundTag owner = tag.getCompound("SkullOwner");
        if (owner.getCompound("Properties").contains("textures", 9) && !owner.getCompound("Properties").getList("textures", 10).isEmpty()) return;
        var conn = Minecraft.getInstance().getConnection();
        if (conn == null) return;
        net.minecraft.client.multiplayer.PlayerInfo info = owner.hasUUID("Id") ? conn.getPlayerInfo(owner.getUUID("Id")) : null;
        if (info == null && owner.contains("Name", 8)) info = conn.getPlayerInfo(owner.getString("Name"));
        if (info == null || info.getProfile().getProperties().get("textures").isEmpty()) return;
        tag.put("SkullOwner", net.minecraft.nbt.NbtUtils.writeGameProfile(new net.minecraft.nbt.CompoundTag(), info.getProfile()));
    }

    static boolean inside(double x, double y, int x0, int y0, int w, int h) {
        return x >= x0 && y >= y0 && x < x0 + w && y < y0 + h;
    }
}
