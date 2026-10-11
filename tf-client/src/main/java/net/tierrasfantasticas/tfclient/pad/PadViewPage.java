package net.tierrasfantasticas.tfclient.pad;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Una app que dibuja el servidor (PadView), en una sola ventana (1.3.28): pestañas de carpeta encima (siempre caben: si
 * no, se estrechan); dentro, la barra de información (header), la cabecera grande (hero), la lista (casillas, tarjetas
 * con vitrina, filas) con su barra de desplazamiento y, abajo, la barra de acciones (botones y campo para escribir).
 * Cada botón, fila o casilla manda su acción al servidor, que contesta con la vista nueva; las que empiezan por
 * «§open:» abren otra app del pad.
 */
final class PadViewPage extends PadPage {
    private static final int ROW_PAD = 3;
    private static final int CELL_W = 30, CELL_H = 32;
    /**
     * Tarjetas: ranura con el objeto al doble, nombre y segunda línea (y, si la vista lo pide, una barra de progreso).
     * Llenan el ancho de la ventana; si son pocas, se agrandan hasta CARD_MAX y van centradas.
     */
    private static final int CARD_MIN = 76, CARD_MAX = 116, CARD_H = 76, CARD_BAR = 8;
    /** Alto de la vitrina de las tarjetas (donde va el objeto al doble con su luz). */
    private static final int SHOW = 40;
    /** Cabecera grande de una ficha: el objeto al doble en su medallón de 40. */
    private static final int HERO_MIN = 50, HERO_SLOT = 40;
    /** Alto de la barra de acciones de abajo. */
    private static final int DOCK_H = 20;
    /** Icono de las filas: el objeto al doble dentro de su recuadro. */
    private static final int ICON_BOX = 26;
    /** Hueco entre filas (cada fila es su propia tarjeta). */
    private static final int ROW_GAP = 3;

    private PadView view;
    private int scroll;
    private int contentH;
    private String input = "";
    private boolean waiting = true;
    private ItemStack tooltip = ItemStack.EMPTY;

    /** Geometría calculada en el último dibujo, para los clics. kind: 0 botón, 1 pestaña, 2 fila, 3 casilla. */
    private record Hit(int x, int y, int w, int h, int kind, String action, int style) {}

    private final List<Hit> hits = new ArrayList<>();
    private int listTop, listBottom;

    PadViewPage(TFPadScreen pad, String app) {
        super(pad, app);
    }

    void set(PadView v) {
        boolean sameTab = view != null && view.tab().equals(v.tab());
        if (!sameTab) scroll = 0;
        if (view == null || !sameTab || v.input() == null) input = "";
        view = v;
        waiting = false;
        for (PadView.Row r : v.rows()) PadUi.skin(r.icon());
        if (v.hero() != null) PadUi.skin(v.hero().icon());
        for (PadView.Cell c : v.cells()) PadUi.skin(c.icon());
    }

    @Override
    String title() {
        PadHomePage.App a = PadHomePage.find(app);
        return a != null ? a.name() : PadFont.upper(app);
    }

    // ---------------------------------------------------------------------------------------------------------------

    @Override
    void render(GuiGraphics g, double mx, double my, float partial) {
        hits.clear();
        tooltip = ItemStack.EMPTY;
        if (view == null) {
            PadUi.panel(g, X, Y, W, H);
            PadUi.spinner(g, X + W / 2, Y + H / 2 - 8);
            PadFont.drawCentered(g, "CARGANDO", X + W / 2, Y + H / 2 + 4, PadUi.MUTED & 0xFFFFFF, true);
            return;
        }
        // una sola ventana: las pestañas de carpeta encima (la elegida se une a ella) y dentro, de arriba abajo, la
        // barra de información, la cabecera grande, la lista y la barra de acciones
        boolean tabs = !view.tabs().isEmpty();
        int top = Y + (tabs ? 14 : 0);
        PadUi.panel(g, X, top, W, Y + H - top);
        if (tabs) drawTabs(g, Y, mx, my);
        int y = top + 1;
        if (!view.header().isEmpty()) y += PadUi.infobar(g, X + 1, y, W - 2, view.header(), tabs ? 1 : 2);
        y += 3;
        boolean bottomBar = view.input() != null || !view.footer().isEmpty();
        int dockH = bottomBar ? DOCK_H : 0;
        int dockY = Y + H - 1 - dockH;
        listBottom = dockY - (bottomBar ? 3 : 2);
        // una ficha sin nada debajo (solo su cabecera grande) va centrada en el hueco
        boolean list = view.hero() == null || !view.cells().isEmpty() || !view.rows().isEmpty() || !view.empty().isEmpty();
        if (view.hero() != null) {
            int hh = heroHeight(view.hero(), W - 10);
            int hy = list ? y : y + Math.max(0, (listBottom - y - hh) / 2);
            drawHero(g, view.hero(), X + 5, hy, W - 10, hh, mx, my);
            y += hh + 4;
        }
        listTop = y;
        int visible = listBottom - listTop;
        if (list) {
            int innerX = X + 5, innerW = W - 15;
            boolean cards = view.cellStyle() == PadView.CARDS;
            int[] grid = grid(innerW);
            int cols = grid[0], cw = grid[1], ch = grid[2], gx = grid[3];
            int gridRows = (view.cells().size() + cols - 1) / cols;
            int gridH = gridRows * ch + (gridRows > 0 ? 2 : 0);
            contentH = gridH;
            for (PadView.Row r : view.rows()) contentH += rowHeight(r, innerW);
            scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - visible)));
            pad.scissor(g, X + 1, listTop, W - 2, visible);
            int ry = listTop + 1 - scroll;
            // solo tarjetas y caben: centradas también en alto
            if (cards && view.rows().isEmpty() && contentH < visible) ry += (visible - contentH) / 2;
            if (gridRows > 0) {
                for (int i = 0; i < view.cells().size(); i++) {
                    int cx = gx + (i % cols) * cw, cy = ry + 1 + (i / cols) * ch;
                    if (cy + ch > listTop && cy < listBottom) {
                        if (cards) drawCard(g, view.cells().get(i), cx, cy, cw, ch, mx, my);
                        else drawCell(g, view.cells().get(i), cx, cy, mx, my);
                    }
                }
                ry += gridH;
            }
            for (PadView.Row r : view.rows()) {
                int h = rowHeight(r, innerW);
                if (ry + h > listTop && ry < listBottom) drawRow(g, r, innerX, ry, innerW, h, mx, my);
                ry += h;
            }
            pad.noScissor(g);
            if (view.rows().isEmpty() && view.cells().isEmpty()) {
                int lines = PadUi.lines(view.empty(), W - 40);
                int mid = (listTop + listBottom) / 2;
                PadHomePage.App a = PadHomePage.find(app);
                if (a != null && listBottom - listTop > 60) {
                    pad.blit(g, "icon_" + a.icon(), X + W / 2 - 16, mid - 26 - lines * 5);
                    mid += 10;
                }
                for (FormattedLine line : centered(view.empty(), W - 40)) {
                    PadUi.text(g, line.text, X + W / 2 - line.width / 2, mid - lines * 5 + line.index * 10, PadUi.MUTED);
                }
            }
            PadUi.scrollbar(g, X + W - 8, listTop + 1, visible - 2, visible, contentH, scroll);
        } else {
            contentH = 0;
            scroll = 0;
        }
        // la barra de acciones: botones a la derecha; el campo de texto ocupa lo que queda a la izquierda
        if (bottomBar) {
            PadUi.dock(g, X + 1, dockY, W - 2, dockH);
            int fy = dockY + (dockH - 16) / 2;
            int footerW = 0;
            for (PadView.Btn b : view.footer()) footerW += PadUi.buttonWidth(b.label()) + 4;
            int x = X + W - 5 - footerW + 4;
            for (PadView.Btn b : view.footer()) {
                int bw = PadUi.buttonWidth(b.label());
                button(g, x, fy, bw, b, mx, my, true);
                x += bw + 4;
            }
            if (view.input() != null) {
                PadView.Input in = view.input();
                int bw = PadUi.buttonWidth(in.button());
                int right = X + W - 5 - footerW;
                int fw = Math.max(40, right - (X + 5) - bw - 4);
                PadUi.field(g, X + 5, fy, fw, input, in.hint());
                PadView.Btn send = new PadView.Btn(in.button(), "§input", PadView.GREEN, !input.isBlank());
                button(g, right - bw, fy, bw, send, mx, my, true);
            }
        }
        // esperando al servidor: la ruedita va en el hueco que la barra de información deja a la derecha (no encima del texto)
        if (waiting) PadUi.spinner(g, X + W - 10, top + 2);
    }

    /**
     * Pestañas de carpeta encima de la ventana (si no caben con su margen, se estrechan y, si aún no, se recorta el
     * texto). La elegida se dibuja la última: baja y se une a la ventana.
     */
    private void drawTabs(GuiGraphics g, int y, double mx, double my) {
        List<PadView.Tab> tabs = view.tabs();
        int gap = 2, pad = 14, room = W - 8;
        int total = 0;
        for (PadView.Tab t : tabs) total += PadFont.width(t.label()) + pad;
        total += gap * (tabs.size() - 1);
        if (total > room) {
            pad = 8;
            total = 0;
            for (PadView.Tab t : tabs) total += PadFont.width(t.label()) + pad;
            total += gap * (tabs.size() - 1);
        }
        int maxLabel = total > room ? Math.max(12, (room - gap * (tabs.size() - 1)) / tabs.size() - pad) : Integer.MAX_VALUE;
        int x = X + 4;
        int[] chosen = null;
        String chosenLabel = "";
        for (PadView.Tab t : tabs) {
            String label = PadFont.fit(t.label(), maxLabel);
            int w = PadFont.width(label) + pad;
            boolean sel = t.key().equals(view.tab());
            boolean hover = PadUi.inside(mx, my, x, y, w, 15);
            if (sel) {
                chosen = new int[] {x, w};
                chosenLabel = label;
            } else {
                if (hover) pad().hover("§tab" + t.key());
                PadUi.tab(g, x, y, w, label, hover ? 1 : 0);
            }
            hits.add(new Hit(x, y, w, 15, 1, t.key(), 0));
            x += w + gap;
        }
        if (chosen != null) PadUi.tab(g, chosen[0], y, chosen[1], chosenLabel, 2);
    }

    /**
     * La rejilla: {columnas, ancho, alto, x de la primera}. Las casillas pequeñas van de 30 en 30 (centradas si son
     * pocas); las tarjetas llenan el ancho y, si son menos que las que caben, se agrandan hasta CARD_MAX y van centradas.
     */
    private int[] grid(int innerW) {
        int n = view.cells().size();
        if (view.cellStyle() == PadView.CARDS) {
            int cols = Math.max(1, innerW / CARD_MIN);
            int cw;
            if (n > 0 && n < cols) {
                cols = n;
                cw = Math.min(CARD_MAX, innerW / cols);
            } else {
                cw = innerW / cols;
            }
            boolean bar = false;
            for (PadView.Cell c : view.cells()) bar |= c.progress() >= 0;
            return new int[] {cols, cw, CARD_H + (bar ? CARD_BAR : 0), X + 5 + (innerW - cols * cw) / 2};
        }
        int cols = Math.max(1, innerW / CELL_W);
        int used = Math.max(1, Math.min(cols, n));
        return new int[] {cols, CELL_W, CELL_H, X + 5 + (innerW - used * CELL_W) / 2};
    }

    private TFPadScreen pad() {
        return pad;
    }

    private int rowHeight(PadView.Row r, int w) {
        boolean textOnly = r.icon().isEmpty() && r.button() == null && r.button2() == null;
        if (textOnly && r.lines().isEmpty() && !r.title().isEmpty()) return 16; // título de sección
        if (textOnly) {
            int n = 0;
            for (String line : r.lines()) n += Math.max(1, PadUi.lines(line, w - 14));
            return 8 + (r.title().isEmpty() ? 0 : 12) + n * 10 + ROW_PAD + ROW_GAP;
        }
        int textW = textWidth(r, w);
        int h = 4 + 11 + linesHeight(r, textW) + (r.progress() >= 0 ? 8 : 0) + ROW_PAD;
        return Math.max(r.icon().isEmpty() ? 26 : ICON_BOX + 10, h + 2) + ROW_GAP;
    }

    private int linesHeight(PadView.Row r, int textW) {
        int n = 0;
        for (String line : r.lines()) n += Math.min(2, Math.max(1, PadUi.lines(line, textW)));
        return n * 10;
    }

    /** Lo que ocupa a la derecha de una fila: su etiqueta y sus botones. */
    private int rightWidth(PadView.Row r) {
        return (r.badge().isEmpty() ? 0 : PadUi.chipWidth(r.badge()) + 6) + buttonsWidth(r);
    }

    private int textWidth(PadView.Row r, int w) {
        return Math.max(30, w - (r.icon().isEmpty() ? 12 : ICON_BOX + 15) - rightWidth(r) - 6);
    }

    private int buttonsWidth(PadView.Row r) {
        int w = 0;
        if (r.button() != null) w += PadUi.buttonWidth(r.button().label()) + 4;
        if (r.button2() != null) w += PadUi.buttonWidth(r.button2().label()) + 4;
        return w;
    }

    private boolean inList(double my) {
        return my >= listTop && my < listBottom;
    }

    /** Casilla pequeña: el objeto en su tarjeta, con su cantidad y, si hay, un texto pixel debajo. */
    private void drawCell(GuiGraphics g, PadView.Cell c, int x, int y, double mx, double my) {
        boolean clickable = !c.action().isEmpty();
        boolean hover = PadUi.inside(mx, my, x + 1, y, CELL_W - 2, CELL_H - 1) && inList(my);
        PadUi.card(g, x + 1, y, CELL_W - 2, CELL_H - 1, c.selected() ? 2 : hover && clickable ? 1 : 0);
        if (!c.icon().isEmpty()) {
            int iy = c.label().isEmpty() ? y + (CELL_H - 1 - 16) / 2 : y + 4;
            if (hover && clickable) iy -= 1;
            PadUi.item(g, c.icon(), x + (CELL_W - 16) / 2, iy, 1);
            if (hover) tooltip = c.icon();
        }
        if (!c.label().isEmpty()) {
            String label = PadFont.fit(c.label(), CELL_W - 4);
            PadFont.drawCentered(g, label, x + CELL_W / 2, y + 20, PadUi.onDark(c.color()) & 0xFFFFFF, false);
        }
        if (clickable) {
            if (hover) pad.hover("§cell" + c.action());
            hits.add(new Hit(x + 1, y, CELL_W - 2, CELL_H - 1, 3, c.action(), 0));
        }
    }

    /** Un objeto al doble de tamaño (nítido: cada píxel del objeto, dos de pantalla); el número, a tamaño normal. */
    private static void bigItem(GuiGraphics g, ItemStack stack, int x, int y) {
        PadUi.item(g, stack, x, y, 2);
    }

    /**
     * Tarjeta: arriba la vitrina tintada del color de la tarjeta (con la textura, una luz detrás del objeto al doble y
     * su sombra en el suelo); abajo el nombre en negrita y su etiqueta (precio en oro con la moneda, «+…» en verde,
     * estado en azul, gris si no se puede pulsar); la barra de progreso si tiene y la burbuja de aviso arriba.
     */
    private void drawCard(GuiGraphics g, PadView.Cell c, int x, int y, int cw, int ch, double mx, double my) {
        boolean clickable = !c.action().isEmpty();
        int w = cw - 4, h = ch - 4, x0 = x + 2;
        boolean hover = PadUi.inside(mx, my, x0, y, w, h) && inList(my);
        boolean lift = hover && clickable;
        int yy = lift ? y - 1 : y;
        int color = PadUi.onDark(c.color());
        PadUi.card(g, x0, yy, w, h, c.selected() ? 2 : lift ? 1 : 0);
        // la vitrina
        g.fillGradient(x0 + 1, yy + 1, x0 + w - 1, yy + 1 + SHOW, PadUi.tint(color, 0.30F), PadUi.tint(color, 0.10F));
        PadUi.texture(g, x0 + 1, yy + 1, w - 2, SHOW);
        g.fill(x0 + 1, yy + 1 + SHOW, x0 + w - 1, yy + 2 + SHOW, PadUi.tint(color, 0.55F));
        g.fill(x0 + 1, yy + 2 + SHOW, x0 + w - 1, yy + 3 + SHOW, 0xFFFFFFFF);
        int cx = x0 + w / 2;
        PadUi.stretch(g, "glow", cx - 20, yy + 2, 40, SHOW - 2);
        PadUi.stretch(g, "floor", cx - 13, yy + SHOW - 6, 26, 5);
        if (!c.icon().isEmpty()) {
            int bob = lift ? Math.round((float) Math.sin(System.currentTimeMillis() / 180.0)) : 0;
            bigItem(g, c.icon(), cx - 16, yy + 4 + bob);
            if (hover) tooltip = c.icon();
        }
        int nameW = PadUi.titleWidth(c.label(), w - 6);
        PadUi.title(g, c.label(), cx - nameW / 2, yy + SHOW + 6, w - 6, PadUi.TEXT);
        if (!c.sub().isEmpty()) {
            // si no cabe, se recorta lo de dentro de la etiqueta (sin contar la moneda ni el «+»: antes se salía de la tarjeta)
            String sub = PadUi.fitChip(c.sub(), w - 6);
            int tone = PadUi.tone(sub, clickable || c.selected(), c.tone());
            PadUi.chip(g, cx - PadUi.chipWidth(sub) / 2, yy + SHOW + 17, sub, tone, 12);
        }
        if (c.progress() >= 0) {
            String pct = Math.round(Math.min(1F, c.progress()) * 100) + "%";
            int pw = PadUi.font().width(pct);
            int bw = w - 12 - pw - 4;
            PadUi.progress(g, x0 + 6, yy + SHOW + 33, bw, c.progress());
            PadUi.text(g, pct, x0 + 6 + bw + 4, yy + SHOW + 32, PadUi.MUTED);
        }
        if (!c.badge().isEmpty()) {
            int bw = PadUi.chipWidth(c.badge());
            g.pose().pushPose();
            g.pose().translate(0, 0, 200); // encima del objeto
            PadUi.chip(g, x0 + w - bw - 3, yy + 4, c.badge(), PadUi.TONE_GREEN, 11);
            g.pose().popPose();
        }
        if (clickable) {
            if (hover) pad.hover("§card" + c.action());
            hits.add(new Hit(x0, y, w, h, 3, c.action(), 0));
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Cabecera grande de una ficha

    private int heroRight(PadView.Row r) {
        int w = r.badge().isEmpty() ? 0 : PadUi.chipWidth(r.badge()) + 6;
        return w + buttonsWidth(r);
    }

    private int heroTextWidth(PadView.Row r, int w) {
        return Math.max(30, w - (HERO_SLOT + 18) - heroRight(r) - 6);
    }

    private int heroHeight(PadView.Row r, int w) {
        int h = 8 + 11 + linesHeight(r, heroTextWidth(r, w)) + (r.progress() >= 0 ? 10 : 0) + 6;
        return Math.max(HERO_MIN, h);
    }

    /**
     * La cabecera grande: un banner tintado del color de la ficha, con su textura; el objeto al doble en su medallón con
     * luz; el título en negrita, las líneas, la barra de progreso (con el tanto por ciento) y a la derecha la etiqueta
     * (precio, con su moneda) y los botones.
     */
    private void drawHero(GuiGraphics g, PadView.Row r, int x, int y, int w, int h, double mx, double my) {
        int color = PadUi.onDark(r.color());
        g.fill(x + 1, y + h, x + w - 1, y + h + 1, 0x3C285AA0);
        PadUi.box(g, x, y, w, h, PadUi.tint(color, 0.65F));
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, PadUi.tint(color, 0.24F), PadUi.tint(color, 0.07F));
        PadUi.texture(g, x + 1, y + 1, w - 2, h - 2);
        g.fill(x + 2, y + 1, x + w - 2, y + 2, 0xFFFFFFFF);
        int ms = HERO_SLOT, sx = x + 6, sy = y + (h - ms) / 2;
        PadUi.slot(g, sx, sy, ms, ms, color);
        PadUi.stretch(g, "glow", sx + 1, sy + 1, ms - 2, ms - 2);
        PadUi.stretch(g, "floor", sx + 8, sy + ms - 7, ms - 16, 4);
        if (!r.icon().isEmpty()) {
            bigItem(g, r.icon(), sx + (ms - 32) / 2, sy + (ms - 32) / 2 - 1);
            if (PadUi.inside(mx, my, sx, sy, ms, ms)) tooltip = r.icon();
        }
        int tx = x + ms + 14, textW = heroTextWidth(r, w);
        int textH = 11 + linesHeight(r, textW) + (r.progress() >= 0 ? 10 : 0);
        int top = y + Math.max(6, (h - textH) / 2 + 1);
        PadUi.title(g, r.title(), tx, top, textW, PadUi.TEXT);
        int ly = top + 12;
        for (String line : r.lines()) ly += Math.max(1, PadUi.wrapEllipsis(g, line, tx, ly, textW, PadUi.MUTED, 2)) * 10;
        if (r.progress() >= 0) {
            String pct = Math.round(Math.min(1F, r.progress()) * 100) + "%";
            int pw = PadUi.font().width(pct);
            int bw = Math.min(textW - pw - 4, 160);
            PadUi.progress(g, tx, ly + 1, bw, r.progress());
            PadUi.text(g, pct, tx + bw + 4, ly, PadUi.MUTED);
        }
        int bx = x + w - heroRight(r) - 2;
        if (!r.badge().isEmpty()) {
            PadUi.chip(g, bx, y + (h - 14) / 2, r.badge(), PadUi.tone(r.badge(), true, 0), 14);
            bx += PadUi.chipWidth(r.badge()) + 6;
        }
        int by = y + (h - 16) / 2;
        if (r.button() != null) {
            int b1 = PadUi.buttonWidth(r.button().label());
            button(g, bx, by, b1, r.button(), mx, my, true);
            bx += b1 + 4;
        }
        if (r.button2() != null) button(g, bx, by, PadUi.buttonWidth(r.button2().label()), r.button2(), mx, my, true);
    }

    /**
     * Una fila: tarjeta con el objeto en su medallón (del color de la fila, o azul hielo), el título en negrita, las
     * líneas, la barra y a la derecha la etiqueta (precio, premio, estado) y los botones. Sin icono ni botones, una
     * nota de texto; con solo título, un título de sección.
     */
    private void drawRow(GuiGraphics g, PadView.Row r, int x, int y, int w, int h, double mx, double my) {
        boolean clickable = !r.click().isEmpty();
        h -= ROW_GAP; // cada fila es su propia tarjeta, con aire debajo
        boolean hover = clickable && PadUi.inside(mx, my, x, y, w, h) && inList(my);
        boolean textOnly = r.icon().isEmpty() && r.button() == null && r.button2() == null;
        boolean section = textOnly && r.lines().isEmpty() && !r.title().isEmpty();
        int titleColor = PadUi.onDark(r.color());
        if (section) { // título de sección: texto de oro (o de su color), una línea y un rombo
            int c = (r.color() & 0xFFFFFF) == 0x18265C ? PadUi.GOLD_TEXT : titleColor;
            PadUi.divider(g, x + 2, y + 4, w - 4, r.title(), c);
            return;
        }
        PadUi.card(g, x, y, w, h, r.selected() ? 2 : hover ? 1 : 0);
        if (clickable) {
            if (hover) pad.hover("§row" + r.click());
            hits.add(new Hit(x, y, w, h, 2, r.click(), 0));
        }
        if (textOnly) {
            int ty = y + 5;
            if (!r.title().isEmpty()) {
                PadUi.title(g, r.title(), x + 7, ty, w - 14, titleColor);
                ty += 12;
            }
            for (String line : r.lines()) ty += Math.max(1, PadUi.wrap(g, line, x + 7, ty, w - 14, PadUi.MUTED, 30)) * 10;
            return;
        }
        int tx = x + 8;
        if (!r.icon().isEmpty()) {
            int iy = y + (h - ICON_BOX) / 2;
            int rgb = r.color() & 0xFFFFFF;
            PadUi.slot(g, x + 5, iy, ICON_BOX, ICON_BOX, rgb == 0x18265C || rgb == 0x7E8CA8 ? 0 : titleColor);
            int bob = hover ? Math.round((float) Math.sin(System.currentTimeMillis() / 180.0)) : 0;
            PadUi.item(g, r.icon(), x + 5 + (ICON_BOX - 16) / 2, iy + (ICON_BOX - 16) / 2 + bob, 1);
            if (PadUi.inside(mx, my, x + 5, iy, ICON_BOX, ICON_BOX) && inList(my)) tooltip = r.icon();
            tx = x + ICON_BOX + 12;
        }
        int textW = textWidth(r, w);
        int textH = 11 + linesHeight(r, textW) + (r.progress() >= 0 ? 8 : 0);
        int top = r.icon().isEmpty() ? y + 5 : y + Math.max(5, (h - textH) / 2 + 1);
        PadUi.title(g, r.title(), tx, top, textW, titleColor);
        int ly = top + 11;
        for (String line : r.lines()) ly += Math.max(1, PadUi.wrapEllipsis(g, line, tx, ly, textW, PadUi.MUTED, 2)) * 10;
        if (r.progress() >= 0) PadUi.progress(g, tx, ly + 1, Math.min(textW, 140), r.progress());
        int bx = x + w - rightWidth(r) - 2;
        if (!r.badge().isEmpty()) {
            PadUi.chip(g, bx + 2, y + (h - 12) / 2, r.badge(), PadUi.tone(r.badge(), true, 0), 12);
            bx += PadUi.chipWidth(r.badge()) + 6;
        }
        int by = y + (h - 16) / 2;
        if (r.button() != null) {
            int b1 = PadUi.buttonWidth(r.button().label());
            button(g, bx, by, b1, r.button(), mx, my, false);
            bx += b1 + 4;
        }
        if (r.button2() != null) button(g, bx, by, PadUi.buttonWidth(r.button2().label()), r.button2(), mx, my, false);
    }

    private void button(GuiGraphics g, int x, int y, int w, PadView.Btn b, double mx, double my, boolean footer) {
        boolean visible = footer || (y >= listTop && y + 16 <= listBottom + 1);
        boolean clickable = b.enabled() && visible && !b.action().isEmpty();
        boolean hover = clickable && PadUi.inside(mx, my, x, y, w, 16) && (footer || inList(my));
        if (hover) pad.hover("§btn" + b.action() + x + "," + y);
        PadUi.button(g, x, y, w, b.label(), b.style(), hover, b.enabled());
        // los botones van encima de su fila: se miran antes
        if (clickable) hits.add(0, new Hit(x, y, w, 16, 0, b.action(), b.style()));
    }

    /**
     * La leyenda del objeto bajo el ratón: solo en la Tienda y en Kits (en el resto los objetos son iconos), y a dos
     * tercios de su tamaño para que no tape la pantalla.
     */
    @Override
    void renderOver(GuiGraphics g, int mx, int my) {
        if (tooltip.isEmpty() || !(app.equals("tienda") || app.equals("kits"))) return;
        float k = 2F / 3F;
        // a 2/3 el juego no sabe si se sale de la pantalla: se mide aquí y, si no cabe, va al otro lado del ratón
        List<Component> lines = Screen.getTooltipFromItem(Minecraft.getInstance(), tooltip);
        int tw = 0;
        for (Component c : lines) tw = Math.max(tw, PadUi.font().width(c));
        float w = (tw + 20) * k, h = (lines.size() * 10 + 8) * k;
        float tx = mx + w > g.guiWidth() ? Math.max(0, mx - w - 4) : mx;
        float ty = my + h > g.guiHeight() ? Math.max(0, g.guiHeight() - h) : my;
        g.pose().pushPose();
        g.pose().translate(tx, ty, 0);
        g.pose().scale(k, k, 1);
        g.renderTooltip(PadUi.font(), tooltip, 0, 0);
        g.pose().popPose();
    }

    // ---------------------------------------------------------------------------------------------------------------

    @Override
    boolean click(double mx, double my, int button) {
        if (button != 0 || view == null) return false;
        for (Hit h : hits) {
            if (!PadUi.inside(mx, my, h.x, h.y, h.w, h.h)) continue;
            // filas y tarjetas a medio esconder: solo cuenta la parte que se ve (no lo que queda bajo la cabecera o el campo)
            if ((h.kind == 2 || h.kind == 3) && !inList(my)) continue;
            switch (h.kind) {
                case 1 -> {
                    if (!h.action.equals(view.tab())) {
                        pad.sound("tab", 0.7F);
                        send("tab:" + h.action, "");
                    }
                }
                case 0 -> {
                    if (h.action.equals("§input")) {
                        submit();
                    } else {
                        pad.sound(h.style == PadView.RED ? "back" : "select", 0.75F);
                        run(h.action);
                    }
                }
                default -> {
                    pad.sound("select", 0.7F);
                    run(h.action);
                }
            }
            return true;
        }
        return false;
    }

    /** Si la vista tiene ATRÁS (una ficha dentro de la app), lo pulsa. Devuelve false si ya está en la entrada. */
    boolean goBack() {
        if (view == null || waiting) return false;
        for (PadView.Btn b : view.footer()) {
            if (b.enabled() && b.label().startsWith("ATR") && !b.action().isEmpty()) {
                run(b.action());
                return true;
            }
        }
        return false;
    }

    /** Acciones del propio pad («§open:app») o del servidor. */
    private void run(String action) {
        if (action.startsWith("§open:")) {
            pad.openApp(action.substring(6));
            return;
        }
        send(action, "");
    }

    private void send(String action, String text) {
        waiting = true;
        TFPadNet.CHANNEL.sendToServer(new TFPadNet.Action(app, view == null ? "" : view.tab(), action, text));
    }

    private void submit() {
        if (view == null || view.input() == null || input.isBlank()) return;
        pad.sound("select", 0.75F);
        send(view.input().action(), input.trim());
        input = "";
    }

    @Override
    boolean scroll(double mx, double my, double delta) {
        scroll -= (int) Math.signum(delta) * 20;
        return true;
    }

    @Override
    boolean drag(double mx, double my, double dy) {
        if (!inList(my)) return false;
        scroll -= (int) Math.round(dy);
        return true;
    }

    @Override
    boolean typing() {
        return view != null && view.input() != null;
    }

    @Override
    boolean key(int key, int scan, int mods) {
        if (!typing()) return false;
        if (key == 259) { // borrar
            if (!input.isEmpty()) input = input.substring(0, input.length() - 1);
            return true;
        }
        if (key == 257 || key == 335) { // Enter
            submit();
            return true;
        }
        if (key == 86 && (mods & 2) != 0) { // Ctrl+V
            add(Minecraft.getInstance().keyboardHandler.getClipboard());
            return true;
        }
        return false;
    }

    @Override
    boolean chr(char c) {
        if (!typing() || c < ' ') return false;
        add(String.valueOf(c));
        return true;
    }

    private void add(String s) {
        int max = view.input().max();
        StringBuilder b = new StringBuilder(input);
        for (char c : s.toCharArray()) {
            if (c >= ' ' && c != '§' && b.length() < max) b.append(c);
        }
        input = b.toString();
    }

    private record FormattedLine(String text, int width, int index) {}

    /** Las líneas de un texto partido a w, para centrarlas una a una. */
    private static List<FormattedLine> centered(String text, int w) {
        List<FormattedLine> out = new ArrayList<>();
        int i = 0;
        for (net.minecraft.util.FormattedCharSequence seq : PadUi.font().split(net.minecraft.network.chat.Component.literal(text), w)) {
            StringBuilder b = new StringBuilder();
            seq.accept((idx, style, cp) -> {
                b.appendCodePoint(cp);
                return true;
            });
            out.add(new FormattedLine(b.toString(), PadUi.font().width(seq), i++));
            if (i >= 6) break;
        }
        return out;
    }
}
