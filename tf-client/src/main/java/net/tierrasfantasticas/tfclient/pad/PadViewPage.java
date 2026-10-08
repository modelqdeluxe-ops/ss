package net.tierrasfantasticas.tfclient.pad;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * Una app que dibuja el servidor (PadView): pestañas arriba (siempre caben: si no, se estrechan), líneas de cabecera,
 * y dentro del panel la rejilla de objetos (casillas de 32x30 con su texto debajo) y las filas, todo con su barra de
 * desplazamiento. Abajo, los botones o el campo para escribir. Cada botón, fila o casilla manda su acción al servidor,
 * que contesta con la vista nueva; las que empiezan por «§open:» abren otra app del pad.
 */
final class PadViewPage extends PadPage {
    private static final int ROW_PAD = 3;
    private static final int SEP = 0xFFC8E4F8;
    private static final int CELL_W = 32, CELL_H = 31;

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
    }

    @Override
    String title() {
        return PadHomePage.APPS.stream().filter(a -> a.id().equals(app)).map(PadHomePage.App::name).findFirst()
                .orElse(PadFont.upper(app));
    }

    // ---------------------------------------------------------------------------------------------------------------

    @Override
    void render(GuiGraphics g, double mx, double my, float partial) {
        hits.clear();
        tooltip = ItemStack.EMPTY;
        if (view == null) {
            PadUi.panel(g, X, Y, W, H);
            PadUi.spinner(g, X + W / 2, Y + H / 2 - 8);
            PadFont.drawCentered(g, "CARGANDO", X + W / 2, Y + H / 2 + 4, 0x4A6694, false);
            return;
        }
        int y = Y;
        if (!view.tabs().isEmpty()) {
            drawTabs(g, y, mx, my);
            y += 16;
        }
        // con pestañas, una línea de cabecera (la lista necesita el sitio); sin pestañas, dos. Lo que no cabe, con «…»
        int headerRoom = view.tabs().isEmpty() ? 2 : 1;
        for (String line : view.header()) {
            if (headerRoom <= 0) break;
            int n = PadUi.wrapEllipsis(g, line, X + 2, y + 1, W - 4, PadUi.TEXT, headerRoom);
            headerRoom -= n;
            y += 10 * n;
        }
        if (!view.header().isEmpty()) y += 2;
        boolean bottomBar = view.input() != null || !view.footer().isEmpty();
        listTop = y;
        listBottom = Y + H - (bottomBar ? 19 : 0);
        PadUi.panel(g, X, listTop, W, listBottom - listTop);
        int innerX = X + 3, innerW = W - 6 - 4;
        int cols = Math.max(1, innerW / CELL_W);
        int gridRows = (view.cells().size() + cols - 1) / cols;
        int gridH = gridRows * CELL_H + (gridRows > 0 ? 2 : 0);
        contentH = gridH;
        for (PadView.Row r : view.rows()) contentH += rowHeight(r, innerW);
        int visible = listBottom - listTop - 4;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - visible)));
        pad.scissor(g, X + 1, listTop + 2, W - 2, visible);
        int ry = listTop + 2 - scroll;
        // rejilla, centrada
        if (gridRows > 0) {
            int gx = X + (W - 4 - cols * CELL_W) / 2;
            for (int i = 0; i < view.cells().size(); i++) {
                int cx = gx + (i % cols) * CELL_W, cy = ry + 1 + (i / cols) * CELL_H;
                if (cy + CELL_H > listTop && cy < listBottom) drawCell(g, view.cells().get(i), cx, cy, mx, my);
            }
            ry += gridH;
        }
        for (PadView.Row r : view.rows()) {
            int h = rowHeight(r, innerW);
            if (ry + h > listTop && ry < listBottom) drawRow(g, r, innerX, ry, innerW, h, mx, my);
            ry += h;
        }
        g.disableScissor();
        if (view.rows().isEmpty() && view.cells().isEmpty()) {
            int lines = PadUi.lines(view.empty(), W - 30);
            PadUi.wrap(g, view.empty(), X + 15, (listTop + listBottom) / 2 - lines * 5, W - 30, PadUi.MUTED, 6);
        }
        // barra de desplazamiento
        if (contentH > visible) {
            int bx = X + W - 5, by = listTop + 3, bh = visible - 2;
            g.fill(bx, by, bx + 2, by + bh, 0xFFC8DCF0);
            int th = Math.max(8, bh * visible / contentH);
            int ty = by + (bh - th) * scroll / Math.max(1, contentH - visible);
            g.fill(bx, ty, bx + 2, ty + th, 0xFF3496FA);
        }
        // abajo: botones a la derecha; el campo de texto ocupa lo que queda a la izquierda
        int fy = Y + H - 16;
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
        int gap = 3, pad = 10;
        int total = 0;
        for (PadView.Tab t : tabs) total += PadFont.width(t.label()) + pad;
        total += gap * (tabs.size() - 1);
        if (total > W) {
            pad = 6;
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
            boolean hover = PadUi.inside(mx, my, x, y, w, 13);
            if (hover && !sel) pad().hover("§tab" + t.key());
            PadUi.box(g, x, y, w, 13, PadUi.NAVY);
            PadUi.box(g, x + 1, y + 1, w - 2, 11, sel ? 0xFFF6B628 : hover ? 0xFF96D6FF : 0xFFE8F8FF);
            if (sel) g.fill(x + 2, y + 1, x + w - 2, y + 2, 0xFFFFEC96);
            PadFont.drawCentered(g, label, x + w / 2, y + 1, sel ? 0xFFFFFF : 0x18265C, sel);
            hits.add(new Hit(x, y, w, 13, 1, t.key(), 0));
            x += w + gap;
        }
    }

    private TFPadScreen pad() {
        return pad;
    }

    private int rowHeight(PadView.Row r, int w) {
        boolean textOnly = r.icon().isEmpty() && r.button() == null && r.button2() == null;
        if (textOnly) {
            int n = 0;
            for (String line : r.lines()) n += Math.max(1, PadUi.lines(line, w - 8));
            return 6 + (r.title().isEmpty() ? 0 : 11) + n * 10 + ROW_PAD;
        }
        int textW = textWidth(r, w);
        int n = 0;
        for (String line : r.lines()) n += Math.min(2, Math.max(1, PadUi.lines(line, textW)));
        int h = 4 + 10 + n * 10 + (r.progress() >= 0 ? 8 : 0) + ROW_PAD;
        return Math.max(26, h);
    }

    private int textWidth(PadView.Row r, int w) {
        return w - (r.icon().isEmpty() ? 8 : 26) - buttonsWidth(r) - 4;
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
        int bg = c.selected() ? 0xFFFFE9A8 : hover && clickable ? 0xFFD6EEFF : 0xFFF6FBFF;
        PadUi.box(g, x + 1, y, CELL_W - 2, CELL_H - 1, c.selected() ? 0xFFC27A10 : hover && clickable ? 0xFF3496FA : 0xFFB8D4EE);
        PadUi.box(g, x + 2, y + 1, CELL_W - 4, CELL_H - 3, bg);
        if (!c.icon().isEmpty()) {
            g.renderItem(c.icon(), x + 8, y + 3);
            g.renderItemDecorations(PadUi.font(), c.icon(), x + 8, y + 3);
            if (hover) tooltip = c.icon();
        }
        if (!c.label().isEmpty()) {
            String label = PadFont.fit(c.label(), CELL_W - 4);
            PadFont.drawCentered(g, label, x + CELL_W / 2, y + 19, c.color() & 0xFFFFFF, false);
        }
        if (clickable) {
            if (hover) pad.hover("§cell" + c.action());
            hits.add(new Hit(x + 1, y, CELL_W - 2, CELL_H - 1, 3, c.action(), 0));
        }
    }

    private void drawRow(GuiGraphics g, PadView.Row r, int x, int y, int w, int h, double mx, double my) {
        boolean clickable = !r.click().isEmpty();
        boolean hover = clickable && PadUi.inside(mx, my, x, y, w, h - 1) && inList(my);
        if (r.selected()) {
            PadUi.box(g, x, y, w, h - 1, 0xFFFFE9A8);
            g.fill(x, y + 2, x + 2, y + h - 3, 0xFFF6B628);
        } else if (hover) {
            PadUi.box(g, x, y, w, h - 1, 0xFFD6EEFF);
            g.fill(x, y + 2, x + 2, y + h - 3, 0xFF3496FA);
        }
        g.fill(x + 2, y + h - 1, x + w - 2, y + h, SEP);
        if (clickable) {
            if (hover) pad.hover("§row" + r.click());
            hits.add(new Hit(x, y, w, h - 1, 2, r.click(), 0));
        }
        boolean textOnly = r.icon().isEmpty() && r.button() == null && r.button2() == null;
        if (textOnly) {
            int ty = y + 4;
            if (!r.title().isEmpty()) {
                PadUi.text(g, r.title(), x + 4, ty, 0xFF000000 | r.color());
                ty += 11;
            }
            for (String line : r.lines()) ty += Math.max(1, PadUi.wrap(g, line, x + 4, ty, w - 8, PadUi.TEXT, 30)) * 10;
            return;
        }
        int tx = x + 5;
        if (!r.icon().isEmpty()) {
            int iy = y + (h - 17) / 2;
            PadUi.box(g, x + 3, iy - 1, 20, 18, 0xFFB8D4EE);
            PadUi.box(g, x + 4, iy, 18, 16, 0xFFFFFFFF);
            g.renderItem(r.icon(), x + 5, iy);
            g.renderItemDecorations(PadUi.font(), r.icon(), x + 5, iy);
            if (PadUi.inside(mx, my, x + 4, iy, 18, 16) && inList(my)) tooltip = r.icon();
            tx = x + 27;
        }
        int bw = buttonsWidth(r);
        int textW = textWidth(r, w);
        // texto a la derecha del título (precio, premio…): el título se recorta para que no lo pise
        int badgeW = r.badge().isEmpty() ? 0 : PadUi.font().width(r.badge()) + 6;
        PadUi.text(g, PadUi.fitEnd(r.title(), textW - badgeW), tx, y + 4, 0xFF000000 | r.color());
        if (badgeW > 0) PadUi.text(g, r.badge(), tx + textW - badgeW + 6, y + 4, 0xFFC27A10);
        int ly = y + 14;
        for (String line : r.lines()) ly += Math.max(1, PadUi.wrapEllipsis(g, line, tx, ly, textW, PadUi.MUTED, 2)) * 10;
        if (r.progress() >= 0) PadUi.progress(g, tx, ly + 1, Math.min(textW, 120), r.progress());
        int bx = x + w - bw;
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
        boolean hover = clickable && PadUi.inside(mx, my, x, y, w, 15) && (footer || inList(my));
        if (hover) pad.hover("§btn" + b.action() + x + "," + y);
        PadUi.button(g, x, y, w, b.label(), b.style(), hover, b.enabled());
        // los botones van encima de su fila: se miran antes
        if (clickable) hits.add(0, new Hit(x, y, w, 15, 0, b.action(), b.style()));
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
        scroll -= (int) Math.signum(delta) * 18;
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
}
