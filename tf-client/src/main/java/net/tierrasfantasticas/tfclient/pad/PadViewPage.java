package net.tierrasfantasticas.tfclient.pad;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * Una app que dibuja el servidor (PadView): pestañas arriba (siempre caben: si no, se estrechan), líneas de cabecera,
 * y dentro del panel la rejilla de objetos (casillas de 44x46: el objeto al doble y su texto debajo) y las filas, todo con su barra de
 * desplazamiento. Abajo, los botones o el campo para escribir. Cada botón, fila o casilla manda su acción al servidor,
 * que contesta con la vista nueva; las que empiezan por «§open:» abren otra app del pad.
 */
final class PadViewPage extends PadPage {
    private static final int ROW_PAD = 3;
    private static final int CELL_W = 30, CELL_H = 32;
    /** Tarjetas: ranura con el objeto al doble, nombre y segunda línea. */
    private static final int CARD_W = 84, CARD_H = 70;
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
        int y = Y;
        if (!view.tabs().isEmpty()) {
            drawTabs(g, y, mx, my);
            y += 17;
        }
        // con pestañas, una línea de cabecera (la lista necesita el sitio); sin pestañas, dos. Lo que no cabe, con «…»
        int headerRoom = view.tabs().isEmpty() ? 2 : 1;
        boolean firstHeader = true;
        for (String line : view.header()) {
            if (headerRoom <= 0) break;
            if (firstHeader) diamond(g, X + 3, y + 4);
            firstHeader = false;
            int n = PadUi.wrapEllipsis(g, line, X + 10, y + 1, W - 12, PadUi.GLASS_TEXT, headerRoom);
            headerRoom -= n;
            y += 10 * n;
        }
        if (!view.header().isEmpty()) y += 2;
        boolean bottomBar = view.input() != null || !view.footer().isEmpty();
        listTop = y;
        listBottom = Y + H - (bottomBar ? 20 : 0);
        PadUi.panel(g, X, listTop, W, listBottom - listTop);
        int innerX = X + 5, innerW = W - 10 - 5;
        boolean cards = view.cellStyle() == PadView.CARDS;
        int cw = cards ? CARD_W : CELL_W, ch = cards ? CARD_H : CELL_H;
        int cols = Math.max(1, innerW / cw);
        int gridRows = (view.cells().size() + cols - 1) / cols;
        int gridH = gridRows * ch + (gridRows > 0 ? 2 : 0);
        contentH = gridH;
        for (PadView.Row r : view.rows()) contentH += rowHeight(r, innerW);
        int visible = listBottom - listTop - 8;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - visible)));
        pad.scissor(g, X + 2, listTop + 4, W - 4, visible);
        int ry = listTop + 4 - scroll;
        // rejilla, centrada
        if (gridRows > 0) {
            int gx = X + (W - 5 - cols * cw) / 2;
            for (int i = 0; i < view.cells().size(); i++) {
                int cx = gx + (i % cols) * cw, cy = ry + 1 + (i / cols) * ch;
                if (cy + ch > listTop && cy < listBottom) {
                    if (cards) drawCard(g, view.cells().get(i), cx, cy, mx, my);
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
            int lines = PadUi.lines(view.empty(), W - 30);
            int mid = (listTop + listBottom) / 2;
            PadHomePage.App a = PadHomePage.find(app);
            if (a != null && listBottom - listTop > 60) {
                pad.blit(g, "icon_" + a.icon(), X + W / 2 - 16, mid - 26 - lines * 5);
                mid += 10;
            }
            for (FormattedLine line : centered(view.empty(), W - 30)) {
                PadUi.text(g, line.text, X + W / 2 - line.width / 2, mid - lines * 5 + line.index * 10, PadUi.MUTED);
            }
        }
        // barra de desplazamiento
        PadUi.scrollbar(g, X + W - 6, listTop + 5, visible - 2, visible, contentH, scroll);
        // abajo: botones a la derecha; el campo de texto ocupa lo que queda a la izquierda
        int fy = Y + H - 17;
        int footerW = 0;
        for (PadView.Btn b : view.footer()) footerW += PadUi.buttonWidth(b.label()) + 4;
        int x = X + W - footerW + 4;
        for (PadView.Btn b : view.footer()) {
            int bw = PadUi.buttonWidth(b.label());
            button(g, x, fy, bw, b, mx, my, true);
            x += bw + 4;
        }
        if (view.input() != null) {
            PadView.Input in = view.input();
            int bw = PadUi.buttonWidth(in.button());
            int right = X + W - footerW;
            int fw = right - X - bw - 4;
            PadUi.field(g, X, fy, fw, input, in.hint());
            PadView.Btn send = new PadView.Btn(in.button(), "§input", PadView.GREEN, !input.isBlank());
            button(g, right - bw, fy, bw, send, mx, my, true);
        }
        if (waiting) PadUi.spinner(g, X + W - 8, Y + 6);
    }

    /** Pestañas: si no caben con su margen, se estrechan y, si aún no, se recorta el texto. */
    private void drawTabs(GuiGraphics g, int y, double mx, double my) {
        List<PadView.Tab> tabs = view.tabs();
        int gap = 3, pad = 14;
        int total = 0;
        for (PadView.Tab t : tabs) total += PadFont.width(t.label()) + pad;
        total += gap * (tabs.size() - 1);
        if (total > W) {
            pad = 8;
            gap = 2;
            total = 0;
            for (PadView.Tab t : tabs) total += PadFont.width(t.label()) + pad;
            total += gap * (tabs.size() - 1);
        }
        int maxLabel = total > W ? Math.max(12, (W - gap * (tabs.size() - 1)) / tabs.size() - pad) : Integer.MAX_VALUE;
        int x = X;
        for (PadView.Tab t : tabs) {
            String label = PadFont.fit(t.label(), maxLabel);
            int w = PadFont.width(label) + pad;
            boolean sel = t.key().equals(view.tab());
            boolean hover = PadUi.inside(mx, my, x, y, w, 14);
            if (hover && !sel) pad().hover("§tab" + t.key());
            PadUi.tab(g, x, y, w, label, sel ? 2 : hover ? 1 : 0);
            hits.add(new Hit(x, y, w, 14, 1, t.key(), 0));
            x += w + gap;
        }
    }

    private TFPadScreen pad() {
        return pad;
    }

    private int rowHeight(PadView.Row r, int w) {
        boolean textOnly = r.icon().isEmpty() && r.button() == null && r.button2() == null;
        if (textOnly && r.lines().isEmpty() && !r.title().isEmpty()) return 16; // título de sección
        if (textOnly) {
            int n = 0;
            for (String line : r.lines()) n += Math.max(1, PadUi.lines(line, w - 12));
            return 8 + (r.title().isEmpty() ? 0 : 11) + n * 10 + ROW_PAD + ROW_GAP;
        }
        int textW = textWidth(r, w);
        int n = 0;
        for (String line : r.lines()) n += Math.min(2, Math.max(1, PadUi.lines(line, textW)));
        int h = 4 + 10 + n * 10 + (r.progress() >= 0 ? 8 : 0) + ROW_PAD;
        return Math.max(r.icon().isEmpty() ? 26 : ICON_BOX + 10, h + 2) + ROW_GAP;
    }

    private int linesHeight(PadView.Row r, int textW) {
        int n = 0;
        for (String line : r.lines()) n += Math.min(2, Math.max(1, PadUi.lines(line, textW)));
        return n * 10;
    }

    private int textWidth(PadView.Row r, int w) {
        return w - (r.icon().isEmpty() ? 12 : ICON_BOX + 13) - buttonsWidth(r) - 8;
    }

    private int buttonsWidth(PadView.Row r) {
        int w = 0;
        if (r.button() != null) w += PadUi.buttonWidth(r.button().label()) + 4;
        if (r.button2() != null) w += PadUi.buttonWidth(r.button2().label()) + 4;
        return w;
    }

    private boolean inList(double my) {
        return my >= listTop + 2 && my < listBottom - 2;
    }

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
            PadFont.drawCentered(g, label, x + CELL_W / 2, y + 20, PadUi.onDark(c.color()) & 0xFFFFFF, true);
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

    /** Tarjeta: el objeto al doble en su ranura, el nombre y una segunda línea en oro (precio, estado…). */
    private void drawCard(GuiGraphics g, PadView.Cell c, int x, int y, double mx, double my) {
        boolean clickable = !c.action().isEmpty();
        int w = CARD_W - 4, h = CARD_H - 4;
        boolean hover = PadUi.inside(mx, my, x + 2, y, w, h) && inList(my);
        boolean lift = hover && clickable;
        int yy = lift ? y - 1 : y;
        PadUi.card(g, x + 2, yy, w, h, c.selected() ? 2 : lift ? 1 : 0);
        // la franja de su color, bajo la luz de arriba
        g.fill(x + 4, yy + 3, x + w, yy + 4, 0xFF000000 | (PadUi.onDark(c.color()) & 0xFFFFFF));
        int sx = x + 2 + (w - 38) / 2;
        PadUi.slot(g, sx, yy + 6, 38, 38);
        if (!c.icon().isEmpty()) {
            int bob = lift ? Math.round((float) Math.sin(System.currentTimeMillis() / 180.0)) : 0;
            bigItem(g, c.icon(), sx + 3, yy + 9 + bob);
            if (hover) tooltip = c.icon();
        }
        String name = PadUi.fitEnd(c.label(), w - 8);
        PadUi.text(g, name, x + 2 + (w - PadUi.font().width(name)) / 2, yy + 47, PadUi.TEXT);
        if (!c.sub().isEmpty()) {
            String sub = PadUi.fitEnd(c.sub(), w - 8);
            PadUi.text(g, sub, x + 2 + (w - PadUi.font().width(sub)) / 2, yy + 56, PadUi.GOLD_TEXT);
        }
        if (clickable) {
            if (hover) pad.hover("§card" + c.action());
            hits.add(new Hit(x + 2, y, w, h, 3, c.action(), 0));
        }
    }

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
                PadUi.text(g, r.title(), x + 7, ty, titleColor);
                ty += 11;
            }
            for (String line : r.lines()) ty += Math.max(1, PadUi.wrap(g, line, x + 7, ty, w - 14, PadUi.MUTED, 30)) * 10;
            return;
        }
        int tx = x + 7;
        if (!r.icon().isEmpty()) {
            int iy = y + (h - ICON_BOX) / 2;
            PadUi.slot(g, x + 5, iy, ICON_BOX, ICON_BOX);
            int bob = hover ? Math.round((float) Math.sin(System.currentTimeMillis() / 180.0)) : 0;
            PadUi.item(g, r.icon(), x + 5 + (ICON_BOX - 16) / 2, iy + (ICON_BOX - 16) / 2 + bob, 1);
            if (PadUi.inside(mx, my, x + 5, iy, ICON_BOX, ICON_BOX) && inList(my)) tooltip = r.icon();
            tx = x + ICON_BOX + 11;
        }
        int bw = buttonsWidth(r);
        int textW = textWidth(r, w);
        // texto a la derecha del título (precio, premio…): el título se recorta para que no lo pise
        int badgeW = r.badge().isEmpty() ? 0 : PadUi.font().width(r.badge()) + 6;
        int textH = 10 + linesHeight(r, textW) + (r.progress() >= 0 ? 8 : 0);
        int top = r.icon().isEmpty() ? y + 5 : y + Math.max(5, (h - textH) / 2 + 1);
        PadUi.text(g, PadUi.fitEnd(r.title(), textW - badgeW), tx, top, titleColor);
        if (badgeW > 0) PadUi.text(g, r.badge(), tx + textW - badgeW + 6, top, PadUi.GOLD_TEXT);
        int ly = top + 10;
        for (String line : r.lines()) ly += Math.max(1, PadUi.wrapEllipsis(g, line, tx, ly, textW, PadUi.MUTED, 2)) * 10;
        if (r.progress() >= 0) PadUi.progress(g, tx, ly + 1, Math.min(textW, 120), r.progress());
        int bx = x + w - bw - 2;
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

    @Override
    void renderOver(GuiGraphics g, int mx, int my) {
        if (!tooltip.isEmpty()) g.renderTooltip(PadUi.font(), tooltip, mx, my);
    }

    // ---------------------------------------------------------------------------------------------------------------

    @Override
    boolean click(double mx, double my, int button) {
        if (button != 0 || view == null) return false;
        for (Hit h : hits) {
            if (!PadUi.inside(mx, my, h.x, h.y, h.w, h.h)) continue;
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

    /** Rombo de oro de 5x5 (adorno de cabeceras y secciones), con centro en (cx, cy). */
    private static void diamond(GuiGraphics g, int cx, int cy) {
        g.fill(cx - 2, cy, cx + 3, cy + 1, 0xFFC27A10);
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFF6B628);
        g.fill(cx, cy - 2, cx + 1, cy + 3, 0xFFC27A10);
        g.fill(cx, cy - 1, cx + 1, cy, 0xFFFFEC96);
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
