package net.tierrasfantasticas.tfclient.pad;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Piezas de dibujo del pad (1.3.26): ventanas azul noche con borde de oro y tachuelas en las esquinas, tarjetas
 * azules en relieve (más claras al pasar el ratón; con borde de oro si están marcadas), ranuras oscuras para los
 * objetos, botones con relieve y luz, pestañas doradas, campos y barras hundidos. Todo en unidades de la página.
 * Los textos dentro de las ventanas son claros (TEXT, MUTED); los que van sobre el cristal del pad, azul marino
 * (GLASS_TEXT).
 */
final class PadUi {
    static final int NAVY = 0xFF18265C;
    /** Contorno de todo (tinta). */
    static final int INK = 0xFF0B1430;
    /** Texto sobre las ventanas oscuras. */
    static final int TEXT = 0xFFFFFFFF;
    static final int MUTED = 0xFFA9BCE8;
    static final int GOLD_TEXT = 0xFFFFD86A;
    static final int GREEN_TEXT = 0xFF7CF0A0;
    static final int RED_TEXT = 0xFFFF8A8A;
    /** Texto sobre el cristal azul claro del pad. */
    static final int GLASS_TEXT = 0xFF18265C;
    static final int GOLD = 0xFFF6B628, GOLD_HI = 0xFFFFE58A, GOLD_LO = 0xFFC07818;
    static final int PANEL_TOP = 0xFF1E2F6E, PANEL_BOT = 0xFF111B48, PANEL_HI = 0xFF3A58B0;
    static final int SLOT = 0xFF0C1538, SLOT_EDGE = 0xFF2C4488, SLOT_SHADE = 0xFF060C24;
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
            {0xFF4A5884, 0xFF36426C, 0xFF6E7CA8, 0xFF252E50},   // gris (desactivado)
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

    /** Ventana: tinta, borde de oro, azul noche en degradado, luz arriba y tachuelas en las esquinas. */
    static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 2, y + h, x + w - 2, y + h + 1, 0x78060C28);
        box(g, x, y, w, h, INK);
        box(g, x + 1, y + 1, w - 2, h - 2, GOLD_LO);
        if (w > 4 && h > 4) g.fillGradient(x + 2, y + 2, x + w - 2, y + h - 2, PANEL_TOP, PANEL_BOT);
        g.fill(x + 3, y + 2, x + w - 3, y + 3, PANEL_HI);
        if (w >= 12 && h >= 12) {
            stud(g, x + 2, y + 2);
            stud(g, x + w - 3, y + 2);
            stud(g, x + 2, y + h - 3);
            stud(g, x + w - 3, y + h - 3);
        }
    }

    /** Tarjeta (fila, casilla, ficha) dentro de una ventana. state: 0 normal, 1 ratón encima, 2 marcada. */
    static void card(GuiGraphics g, int x, int y, int w, int h, int state) {
        int edge = state == 2 ? GOLD : state == 1 ? 0xFF7AB4FF : 0xFF34529E;
        int top = state == 1 ? 0xFF3A5CC0 : state == 2 ? 0xFF33509E : 0xFF2A4596;
        int bot = state == 1 ? 0xFF24408E : state == 2 ? 0xFF1E3378 : 0xFF1C3074;
        int light = state == 2 ? GOLD_HI : state == 1 ? 0xFF9CCAFF : 0xFF4E70CC;
        box(g, x, y, w, h, INK);
        box(g, x + 1, y + 1, w - 2, h - 2, edge);
        if (w > 4 && h > 4) g.fillGradient(x + 2, y + 2, x + w - 2, y + h - 2, top, bot);
        g.fill(x + 2, y + 2, x + w - 2, y + 3, light);
        if (state == 2) g.fill(x + 2, y + 3, x + 4, y + h - 2, GOLD);
    }

    /** Ranura hundida para un objeto (como las del inventario, en azul noche). */
    static void slot(GuiGraphics g, int x, int y, int w, int h) {
        box(g, x, y, w, h, SLOT_EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, SLOT);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, SLOT_SHADE);
        g.fill(x + 1, y + 2, x + 2, y + h - 1, SLOT_SHADE);
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
        int[] c = BTN[enabled ? Math.max(0, Math.min(3, style)) : 4];
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

    /** Pestaña de 14 de alto. state: 0 normal, 1 ratón encima, 2 elegida (dorada). */
    static void tab(GuiGraphics g, int x, int y, int w, String label, int state) {
        box(g, x, y, w, 14, INK);
        if (state == 2) {
            g.fillGradient(x + 1, y + 1, x + w - 1, y + 13, 0xFFFFDC64, 0xFFE89A1C);
            g.fill(x + 2, y + 1, x + w - 2, y + 2, GOLD_HI);
        } else {
            g.fillGradient(x + 1, y + 1, x + w - 1, y + 13, state == 1 ? 0xFF2A4596 : PANEL_TOP, state == 1 ? 0xFF1C3074 : 0xFF141F50);
            g.fill(x + 2, y + 1, x + w - 2, y + 2, 0xFF4E70CC);
        }
        PadFont.drawCentered(g, label, x + w / 2, y + 2, state == 2 || state == 1 ? 0xFFFFFF : MUTED & 0xFFFFFF, true);
    }

    /** Título de sección dentro de una ventana: texto de oro, una línea y un rombo al final. */
    static void divider(GuiGraphics g, int x, int y, int w, String title, int color) {
        text(g, title, x, y + 1, color);
        int lx = x + font().width(title) + 5;
        if (lx < x + w - 8) {
            g.fill(lx, y + 5, x + w - 6, y + 6, GOLD_LO);
            g.fill(lx, y + 6, x + w - 6, y + 7, 0xA0060C28);
        }
        int cx = x + w - 4;
        g.fill(cx - 2, y + 5, cx + 3, y + 6, GOLD_LO);
        g.fill(cx - 1, y + 4, cx + 2, y + 7, GOLD);
        g.fill(cx, y + 3, cx + 1, y + 8, GOLD);
        g.fill(cx, y + 4, cx + 1, y + 5, GOLD_HI);
    }

    /** Barra de desplazamiento: carril hundido y asa de oro. */
    static void scrollbar(GuiGraphics g, int x, int y, int h, int visible, int content, int scroll) {
        if (content <= visible || h <= 4) return;
        g.fill(x, y, x + 3, y + h, SLOT);
        int th = Math.max(8, h * visible / content);
        int ty = y + (h - th) * scroll / Math.max(1, content - visible);
        g.fillGradient(x, ty, x + 3, ty + th, GOLD_HI, GOLD_LO);
    }

    /** Aclara un color ARGB n puntos por canal. */
    static int lighten(int c, int n) {
        int r = Math.min(255, (c >> 16 & 255) + n), gg = Math.min(255, (c >> 8 & 255) + n), b = Math.min(255, (c & 255) + n);
        return c & 0xFF000000 | r << 16 | gg << 8 | b;
    }

    /**
     * Los colores que manda el servidor estaban pensados para fondo claro: sobre las ventanas oscuras se pasan a su
     * versión clara (el azul marino del texto normal, a blanco; el oro, a oro claro…).
     */
    static int onDark(int rgb) {
        rgb &= 0xFFFFFF;
        switch (rgb) {
            case 0x18265C, 0x000000, 0x0B1430 -> {
                return 0xFFFFFFFF;
            }
            case 0xC27A10, 0xB8741A, 0xBA7014 -> {
                return GOLD_TEXT;
            }
            case 0x7E8CA8, 0x4A6694, 0xAABAD2 -> {
                return MUTED;
            }
            case 0x1E9E46, 0x1E7C2C, 0x40C850 -> {
                return GREEN_TEXT;
            }
            case 0xC8323C, 0xE83446, 0x9A1A30 -> {
                return RED_TEXT;
            }
            case 0x1854BE, 0x3496FA -> {
                return 0xFF8CC4FF;
            }
            default -> {
            }
        }
        float[] hsb = java.awt.Color.RGBtoHSB(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, null);
        if (hsb[2] >= 0.8F) return 0xFF000000 | rgb;
        return 0xFF000000 | (java.awt.Color.HSBtoRGB(hsb[0], Math.min(hsb[1], 0.6F), Math.max(hsb[2], 0.95F)) & 0xFFFFFF);
    }

    /** Barra de progreso hundida (verde; oro cuando está completa). 6 de alto. */
    static void progress(GuiGraphics g, int x, int y, int w, float f) {
        f = Math.max(0, Math.min(1, f));
        box(g, x, y, w, 6, INK);
        g.fill(x + 1, y + 1, x + w - 1, y + 5, SLOT);
        int fw = Math.round((w - 2) * f);
        if (fw > 0) {
            boolean full = f >= 1;
            g.fillGradient(x + 1, y + 1, x + 1 + fw, y + 5, full ? 0xFFFFE070 : 0xFF7CF09A, full ? 0xFFE08E14 : 0xFF1E9E46);
            g.fill(x + 1, y + 1, x + 1 + fw, y + 2, full ? 0xFFFFF6C0 : 0xFFD0FFDA);
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
        g.fill(x + 1, y + 1, x + w - 1, y + 15, SLOT);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, SLOT_SHADE);
        g.fill(x + 1, y + 14, x + w - 1, y + 15, SLOT_EDGE);
        String shown = text.isEmpty() ? fitEnd(hint, w - 10) : fitStart(text, w - 10);
        text(g, shown, x + 5, y + 4, text.isEmpty() ? 0xFF6C80B4 : TEXT);
        if ((System.currentTimeMillis() / 500) % 2 == 0) {
            int cx = x + 5 + (text.isEmpty() ? 0 : font().width(shown));
            g.fill(cx, y + 3, cx + 1, y + 13, GOLD);
        }
    }

    /** Indicador de «cargando»: cuatro puntos que se encienden en rueda (centrado en cx, desde y). */
    static void spinner(GuiGraphics g, int cx, int y) {
        int step = (int) (System.currentTimeMillis() / 140 % 4);
        int[][] at = {{0, -3}, {3, 0}, {0, 3}, {-3, 0}};
        for (int i = 0; i < 4; i++) {
            int c = i == step ? GOLD : 0xFF4E70CC;
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
        if (stack.getCount() > 1) {
            String n = Integer.toString(stack.getCount());
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.drawString(font(), n, x + 16 * k + 1 - font().width(n), y + 16 * k - 8, 0xFFFFFFFF, true);
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
