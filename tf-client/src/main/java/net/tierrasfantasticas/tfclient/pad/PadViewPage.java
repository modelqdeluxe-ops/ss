package net.tierrasfantasticas.tfclient.pad;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * Una app que dibuja el servidor (PadView): pestañas arriba, líneas de cabecera, la lista de filas con su barra de
 * desplazamiento, y abajo los botones o el campo para escribir. Cada botón manda su acción al servidor, que contesta
 * con la vista nueva.
 */
final class PadViewPage extends PadPage {
    private static final int ROW_PAD = 3;
    private static final int SEP = 0xFFC8E4F8;

    private PadView view;
    private int scroll;
    private int contentH;
    private String input = "";
    private boolean waiting = true;
    private ItemStack tooltip = ItemStack.EMPTY;

    /** Geometría calculada en el último dibujo, para los clics. */
    private record Hit(int x, int y, int w, int h, PadView.Btn btn, String tab) {}

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
        return PadHomePage.APPS.stream().filter(a -> a.id().equals(app)).map(PadHomePage.App::name).findFirst().orElse(app);
    }

    // ---------------------------------------------------------------------------------------------------------------

    @Override
    void render(GuiGraphics g, double mx, double my, float partial) {
        hits.clear();
        tooltip = ItemStack.EMPTY;
        if (view == null) {
            PadUi.panel(g, X, Y, W, H);
            PadFont.drawCentered(g, "CARGANDO…".replace("…", "..."), X + W / 2, Y + H / 2 - 6, 0x4A6694, false);
            return;
        }
        int y = Y;
        // pestañas
        if (!view.tabs().isEmpty()) {
            int x = X;
            for (PadView.Tab t : view.tabs()) {
                int w = PadFont.width(t.label()) + 10;
                boolean sel = t.key().equals(view.tab());
                boolean hover = PadUi.inside(mx, my, x, y, w, 13);
                PadUi.box(g, x, y, w, 13, PadUi.NAVY);
                PadUi.box(g, x + 1, y + 1, w - 2, 11, sel ? 0xFFF6B628 : hover ? 0xFF96D6FF : 0xFFE8F8FF);
                PadFont.drawCentered(g, t.label(), x + w / 2, y + 1, sel ? 0xFFFFFF : 0x18265C, sel);
                hits.add(new Hit(x, y, w, 13, null, t.key()));
                x += w + 3;
            }
            y += 16;
        }
        for (String line : view.header()) {
            y += 10 * PadUi.wrap(g, line, X + 2, y + 1, W - 4, PadUi.TEXT, 2);
        }
        if (!view.header().isEmpty()) y += 2;
        boolean bottomBar = view.input() != null || !view.footer().isEmpty();
        listTop = y;
        listBottom = Y + H - (bottomBar ? 19 : 0);
        PadUi.panel(g, X, listTop, W, listBottom - listTop);
        // filas (con recorte)
        int innerX = X + 3, innerW = W - 6 - 4;
        contentH = 0;
        for (PadView.Row r : view.rows()) contentH += rowHeight(r, innerW);
        int visible = listBottom - listTop - 4;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - visible)));
        pad.scissor(g, X + 1, listTop + 2, W - 2, visible);
        int ry = listTop + 2 - scroll;
        for (PadView.Row r : view.rows()) {
            int h = rowHeight(r, innerW);
            if (ry + h > listTop && ry < listBottom) drawRow(g, r, innerX, ry, innerW, h, mx, my);
            ry += h;
        }
        g.disableScissor();
        if (view.rows().isEmpty()) {
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
        // abajo: campo de texto o botones
        int fy = Y + H - 16;
        // los botones de abajo van a la derecha; si hay campo de texto, ocupa lo que queda a la izquierda
        int footerW = 0;
        for (PadView.Btn b : view.footer()) footerW += PadUi.buttonWidth(b.label()) + 4;
        {
            int x = X + W - footerW + 4;
            for (PadView.Btn b : view.footer()) {
                int bw = PadUi.buttonWidth(b.label());
                button(g, x, fy, bw, b, mx, my);
                x += bw + 4;
            }
        }
        if (view.input() != null) {
            PadView.Input in = view.input();
            int bw = PadUi.buttonWidth(in.button());
            int right = X + W - footerW;
            int fw = right - X - bw - 4;
            PadUi.box(g, X, fy, fw, 15, PadUi.NAVY);
            PadUi.box(g, X + 1, fy + 1, fw - 2, 13, 0xFFFFFFFF);
            String shown = input.isEmpty() ? in.hint() : input;
            String fitted = fit(shown, fw - 10);
            PadUi.text(g, fitted, X + 5, fy + 4, input.isEmpty() ? 0xFF96AACC : PadUi.TEXT);
            if (!input.isEmpty() && (System.currentTimeMillis() / 500) % 2 == 0) {
                int cx = X + 5 + PadUi.font().width(fitted);
                g.fill(cx, fy + 3, cx + 1, fy + 12, PadUi.TEXT);
            } else if (input.isEmpty() && (System.currentTimeMillis() / 500) % 2 == 0) {
                g.fill(X + 5, fy + 3, X + 6, fy + 12, PadUi.TEXT);
            }
            PadView.Btn send = new PadView.Btn(in.button(), "§input", PadView.GREEN, !input.isBlank());
            button(g, right - bw, fy, bw, send, mx, my);
        }
        if (waiting) {
            PadFont.draw(g, "...", X + W - 16, Y + 2, 0x4A6694, false);
        }
    }

    private String fit(String s, int w) {
        var font = PadUi.font();
        if (font.width(s) <= w) return s;
        while (!s.isEmpty() && font.width("…" + s) > w) s = s.substring(1);
        return "…" + s;
    }

    private int rowHeight(PadView.Row r, int w) {
        boolean textOnly = r.icon().isEmpty() && r.button() == null && r.button2() == null;
        if (textOnly) {
            int n = 0;
            for (String line : r.lines()) n += Math.max(1, PadUi.lines(line, w - 8));
            return 6 + (r.title().isEmpty() ? 0 : 11) + n * 10 + ROW_PAD;
        }
        int textW = w - (r.icon().isEmpty() ? 6 : 24) - buttonsWidth(r) - 4;
        int n = 0;
        for (String line : r.lines()) n += Math.min(2, Math.max(1, PadUi.lines(line, textW)));
        int h = 4 + 10 + n * 10 + (r.progress() >= 0 ? 8 : 0) + ROW_PAD;
        return Math.max(26, h);
    }

    private int buttonsWidth(PadView.Row r) {
        int w = 0;
        if (r.button() != null) w += PadUi.buttonWidth(r.button().label()) + 4;
        if (r.button2() != null) w += PadUi.buttonWidth(r.button2().label()) + 4;
        return w;
    }

    private void drawRow(GuiGraphics g, PadView.Row r, int x, int y, int w, int h, double mx, double my) {
        g.fill(x + 2, y + h - 1, x + w - 2, y + h, SEP);
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
        int tx = x + 4;
        if (!r.icon().isEmpty()) {
            int iy = y + (h - 16) / 2;
            g.renderItem(r.icon(), x + 3, iy);
            g.renderItemDecorations(PadUi.font(), r.icon(), x + 3, iy);
            if (PadUi.inside(mx, my, x + 3, iy, 16, 16) && my >= listTop + 2 && my < listBottom - 2) tooltip = r.icon();
            tx = x + 24;
        }
        int bw = buttonsWidth(r);
        int textW = w - (tx - x) - bw - 4;
        // badge a la derecha del título
        int badgeW = r.badge().isEmpty() ? 0 : PadUi.font().width(r.badge()) + 4;
        PadUi.text(g, fitMc(r.title(), textW - badgeW), tx, y + 4, 0xFF000000 | r.color());
        if (badgeW > 0) PadUi.text(g, r.badge(), tx + textW - badgeW + 4, y + 4, 0xFFC27A10);
        int ly = y + 14;
        for (String line : r.lines()) ly += Math.max(1, PadUi.wrap(g, line, tx, ly, textW, PadUi.MUTED, 2)) * 10;
        if (r.progress() >= 0) PadUi.progress(g, tx, ly + 1, Math.min(textW, 120), r.progress());
        int bx = x + w - bw;
        int by = y + (h - 16) / 2;
        if (r.button() != null) {
            int b1 = PadUi.buttonWidth(r.button().label());
            button(g, bx, by, b1, r.button(), mx, my);
            bx += b1 + 4;
        }
        if (r.button2() != null) {
            button(g, bx, by, PadUi.buttonWidth(r.button2().label()), r.button2(), mx, my);
        }
    }

    private String fitMc(String s, int w) {
        var font = PadUi.font();
        if (font.width(s) <= w) return s;
        while (!s.isEmpty() && font.width(s + "…") > w) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private void button(GuiGraphics g, int x, int y, int w, PadView.Btn b, double mx, double my) {
        boolean inList = y >= listTop && y + 16 <= listBottom + 1;
        boolean clickable = b.enabled() && (y >= Y + H - 16 || inList);
        boolean hover = clickable && PadUi.inside(mx, my, x, y, w, 15) && PadUi.inside(mx, my, X, Y, W, H);
        PadUi.button(g, x, y, w, b.label(), b.style(), hover, b.enabled());
        if (clickable) hits.add(new Hit(x, y, w, 15, b, null));
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
            if (h.tab != null) {
                if (!h.tab.equals(view.tab())) {
                    pad.sound("select", 0.7F);
                    send("tab:" + h.tab, "");
                }
                return true;
            }
            if (h.btn.action().equals("§input")) {
                submit();
                return true;
            }
            pad.sound(h.btn.style() == PadView.RED ? "back" : "select", 0.75F);
            send(h.btn.action(), "");
            return true;
        }
        return false;
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
            String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
            add(clip);
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
