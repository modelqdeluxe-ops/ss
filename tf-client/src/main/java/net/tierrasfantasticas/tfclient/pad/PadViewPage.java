/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.pad;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * Una app que dibuja el servidor (PadView), con el diseño 1.4 (PadSkin), en una sola ventana: pestañas encima, con
 * aire (siempre caben: si no, se estrechan; la elegida, del color de la app, con su flechita hacia la cabecera); dentro, la
 * cabecera de la app (su color, su icono y el texto del servidor o, si no manda nada, su frase), la ficha grande
 * (hero), la lista (casillas, tarjetas, galería de imágenes y filas) con su barra de desplazamiento y, abajo, la barra
 * de acciones (botones y campo para escribir). Cada botón, fila o casilla manda su acción al servidor, que contesta con
 * la vista nueva; las que empiezan por «§open:» abren otra app del pad. Al cambiar de pestaña la lista entra subiendo;
 * los botones se hunden al pulsarlos. Lo mismo en tools/pad/sim/sim.py (view, card, row, hero).
 */
final class PadViewPage extends PadPage {
    private static final int ROW_PAD = 3;
    private static final int CELL_W = 30, CELL_H = 32;
    /**
     * Tarjetas: vitrina con el objeto al doble, nombre y segunda línea (y, si la vista lo pide, una barra de progreso).
     * Llenan el ancho de la ventana; si son pocas, se agrandan hasta CARD_MAX y van centradas.
     */
    private static final int CARD_MIN = 76, CARD_MAX = 116, CARD_H = 78, CARD_BAR = 8;
    /** Alto de la vitrina de las tarjetas (donde va el objeto al doble con su luz). */
    private static final int SHOW = 40;
    /** Galería (PadView.GALLERY): tarjetas más grandes con la imagen a GAL_IMG. */
    private static final int GAL_MIN = 88, GAL_MAX = 124, GAL_H = 100, GAL_SHOW = 60, GAL_IMG = 50;
    /** Cabecera grande de una ficha: el objeto al doble en su medallón de 40. */
    private static final int HERO_MIN = 52, HERO_SLOT = 40;
    /** Alto de la barra de acciones de abajo. */
    private static final int DOCK_H = 22;
    /** Icono de las filas: el objeto dentro de su medallón. */
    private static final int ICON_BOX = 36; // el icono de las filas va al doble (32) en su pastilla de color
    /** Hueco entre filas (cada fila es su propia tarjeta). */
    private static final int ROW_GAP = 4;
    /** Filas compactas (PadView.COMPACT): el icono a 16 en una pastilla de 22 y menos aire. */
    private static final int ICON_C = 22, ROW_GAP_C = 3;

    private boolean compact() {
        return view != null && view.compact();
    }

    private int iconBox() {
        return compact() ? ICON_C : ICON_BOX;
    }

    private int rowGap() {
        return compact() ? ROW_GAP_C : ROW_GAP;
    }
    /** Lo que tarda la lista en entrar al cambiar de pestaña (ms) y lo que se ve hundido un botón al pulsarlo. */
    private static final int ENTER_MS = 220, PRESS_MS = 160;

    private PadView view;
    private int scroll;
    private int contentH;
    private String input = "";
    private boolean waiting = true;
    private ItemStack tooltip = ItemStack.EMPTY;
    private long shownAt;
    private String pressed = "";
    private long pressedAt;

    /** Geometría calculada en el último dibujo, para los clics. kind: 0 botón, 1 pestaña, 2 fila, 3 casilla. */
    private record Hit(int x, int y, int w, int h, int kind, String action, int style) {}

    private final List<Hit> hits = new ArrayList<>();
    private int listTop, listBottom;

    PadViewPage(TFPadScreen pad, String app) {
        super(pad, app);
    }

    void set(PadView v) {
        if (app.equals("efectos")) v = PadCombat.withTab(v); // COMBATE: los ajustes de las estelas, de este cliente
        boolean sameTab = view != null && view.tab().equals(v.tab());
        if (!sameTab) {
            scroll = 0;
            shownAt = System.currentTimeMillis();
        }
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
        long nowNs = System.nanoTime();
        frameDt = lastFrame == 0 ? 0F : Math.min(0.1F, (nowNs - lastFrame) / 1e9F);
        lastFrame = nowNs;
        hits.clear();
        tooltip = ItemStack.EMPTY;
        int acc = PadSkin.accent(app);
        String icon = PadSkin.icon(app);
        if (view == null) {
            PadSkin.window(g, X, Y, W, H, acc);
            int bh = PadSkin.bannerHeight(1);
            PadSkin.banner(g, pad, X + 1, Y + 1, W - 2, bh, acc, icon, List.of(PadSkin.tagline(app)));
            PadUi.spinner(g, X + W / 2, Y + bh + (H - bh) / 2 - 8);
            PadFont.drawCentered(g, "CARGANDO", X + W / 2, Y + bh + (H - bh) / 2 + 4, PadUi.MUTED & 0xFFFFFF, true);
            return;
        }
        float t = PadSkin.time();
        boolean tabs = !view.tabs().isEmpty();
        int top = Y + (tabs ? PadSkin.TABS : 0);
        PadSkin.window(g, X, top, W, Y + H - top, acc);
        int y = top + 1;
        // la cabecera de la app: lo que manda el servidor (o su frase) junto a su icono, del color de la app. En una
        // ficha sin texto arriba no va: la ficha grande ya hace de cabecera
        if (!view.header().isEmpty() || view.hero() == null) {
            List<String> lines = bannerLines(view.header().isEmpty() ? List.of(PadSkin.tagline(app)) : view.header(), tabs ? 1 : 2);
            int bh = PadSkin.bannerHeight(lines.size());
            PadSkin.banner(g, pad, X + 1, y, W - 2, bh, acc, icon, lines);
            y += bh + 1;
        } else if (tabs) {
            g.fillGradient(X + 1, y, X + W - 1, y + 4, PadSkin.mix(acc, PadSkin.WHITE, 0.14F), PadSkin.darken(acc, 0.78F));
            y += 4;
        }
        if (tabs) drawTabs(g, Y, mx, my, acc);
        // esperando al servidor: la ruedita, en blanco, a la derecha de la cabecera
        if (waiting) PadSkin.spinner(g, X + W - 10, top + 3);
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
            drawHero(g, view.hero(), X + 5, hy, W - 10, hh, mx, my, t);
            y += hh + 5;
        }
        listTop = y;
        int visible = listBottom - listTop;
        if (list) {
            int innerX = X + 5, innerW = W - 15;
            int style = view.style();
            int[] grid = grid(innerW);
            int cols = grid[0], cw = grid[1], ch = grid[2], gx = grid[3];
            int gridRows = (view.cells().size() + cols - 1) / cols;
            int gridH = gridRows * ch + (gridRows > 0 ? 3 : 0);
            contentH = gridH;
            for (PadView.Row r : view.rows()) contentH += rowHeight(r, innerW);
            scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - visible)));
            // al cambiar de pestaña, la lista entra subiendo un poco
            float enter = PadSettings.animations ? Math.min(1F, (System.currentTimeMillis() - shownAt) / (float) ENTER_MS) : 1F;
            int slide = Math.round((1 - ease(enter)) * 10);
            pad.scissor(g, X + 1, listTop, W - 2, visible);
            int ry = listTop + 2 - scroll + slide;
            // solo tarjetas y caben: centradas también en alto
            if (style != PadView.SMALL && view.rows().isEmpty() && contentH < visible) ry += (visible - contentH) / 2;
            if (gridRows > 0) {
                for (int i = 0; i < view.cells().size(); i++) {
                    int cx = gx + (i % cols) * cw, cy = ry + 1 + (i / cols) * ch;
                    if (cy + ch > listTop && cy < listBottom) {
                        if (style != PadView.SMALL) drawCard(g, view.cells().get(i), cx, cy, cw, ch, mx, my, acc, style == PadView.GALLERY, t);
                        else drawCell(g, view.cells().get(i), cx, cy, mx, my, acc);
                    }
                }
                ry += gridH;
            }
            for (PadView.Row r : view.rows()) {
                int h = rowHeight(r, innerW);
                if (ry + h > listTop && ry < listBottom) drawRow(g, r, innerX, ry, innerW, h, mx, my, acc, t);
                ry += h;
            }
            // una lista corta: abajo, el icono de la app grande y suave (no un hueco blanco)
            int left = listBottom - (ry + 4);
            if (!view.rows().isEmpty() && left > 0) PadSkin.listFooterArt(g, pad, X + W / 2, ry + 4 + left / 2, left, acc, icon);
            pad.noScissor(g);
            if (view.rows().isEmpty() && view.cells().isEmpty()) {
                PadSkin.emptyState(g, pad, X + W / 2, (listTop + listBottom) / 2, W - 40, view.empty(), acc, icon, listBottom - listTop);
            }
            PadSkin.scrollbar(g, X + W - 8, listTop + 1, visible - 2, visible, contentH, scroll, acc);
            PadUi.grabbable(X + W - 8, listTop + 1, visible - 2, visible, contentH, () -> scroll, v -> scroll = v);
        } else {
            contentH = 0;
            scroll = 0;
        }
        // la barra de acciones: botones a la derecha; el campo de texto ocupa lo que queda a la izquierda
        if (bottomBar) {
            PadSkin.dock(g, X + 1, dockY, W - 2, dockH, acc);
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
                PadSkin.field(g, X + 5, fy, fw, input, in.hint(), acc, in.action().startsWith("buscar"));
                PadView.Btn send = new PadView.Btn(in.button(), "§input", PadView.GREEN, !input.isBlank());
                button(g, right - bw, fy, bw, send, mx, my, true);
            }
        }
    }

    private static float ease(float x) {
        x = Math.max(0, Math.min(1, x));
        return 1 - (1 - x) * (1 - x) * (1 - x);
    }

    // la cabecera ya partida (1.3.67: solo se vuelve a partir si cambia el texto, el ancho o las líneas que caben)
    private List<String> bannerFor = List.of(), bannerCache = List.of();
    private int bannerKey = -1;

    /** El texto de la cabecera partido al ancho que deja el icono (room líneas como mucho, la última con «...»). */
    private List<String> bannerLines(List<String> header, int room) {
        int key = W * 8 + room;
        if (key == bannerKey && header.equals(bannerFor)) return bannerCache;
        bannerKey = key;
        bannerFor = header;
        bannerCache = splitBanner(header, room);
        return bannerCache;
    }

    private List<String> splitBanner(List<String> header, int room) {
        int w = W - 2 - 46;
        List<String> lines = new ArrayList<>();
        for (String line : header) {
            for (FormattedCharSequence seq : PadUi.font().split(Component.literal(line), w)) {
                StringBuilder b = new StringBuilder();
                seq.accept((i, st, cp) -> {
                    b.appendCodePoint(cp);
                    return true;
                });
                lines.add(b.toString());
            }
        }
        if (lines.size() > room) {
            lines = new ArrayList<>(lines.subList(0, room));
            lines.set(room - 1, PadUi.fitEnd(lines.get(room - 1) + "...", w));
        }
        return lines;
    }

    /**
     * Las pestañas encima de la ventana (PadSkin.tabRow: si no caben con su margen, se estrechan y, si aún no, se
     * recorta el texto). La elegida, del color de la app, con su flechita hacia la cabecera.
     */
    private void drawTabs(GuiGraphics g, int y, double mx, double my, int acc) {
        List<PadView.Tab> tabs = view.tabs();
        List<String> labels = new ArrayList<>();
        int sel = -1;
        for (int i = 0; i < tabs.size(); i++) {
            labels.add(tabs.get(i).label());
            if (tabs.get(i).key().equals(view.tab())) sel = i;
        }
        int[][] at = PadSkin.tabRow(g, pad, X + 4, y, W - 8, labels, sel, mx, my, acc);
        for (int i = 0; i < at.length; i++) hits.add(new Hit(at[i][0], y - 1, at[i][1], PadSkin.TAB_H + 1, 1, tabs.get(i).key(), 0));
    }

    /**
     * La rejilla: {columnas, ancho, alto, x de la primera}. Las casillas pequeñas van de 30 en 30 (centradas si son
     * pocas); las tarjetas (y las de la galería) llenan el ancho y, si son menos que las que caben, se agrandan hasta su
     * máximo y van centradas.
     */
    private int[] grid(int innerW) {
        int n = view.cells().size();
        if (view.style() != PadView.SMALL) {
            boolean gallery = view.style() == PadView.GALLERY;
            int min = gallery ? GAL_MIN : CARD_MIN, max = gallery ? GAL_MAX : CARD_MAX;
            int cols = Math.max(1, innerW / min);
            int cw;
            if (n > 0 && n < cols) {
                cols = n;
                cw = Math.min(max, innerW / cols);
            } else {
                cw = innerW / cols;
            }
            boolean bar = false;
            for (PadView.Cell c : view.cells()) bar |= c.progress() >= 0;
            return new int[] {cols, cw, (gallery ? GAL_H : CARD_H) + (bar ? CARD_BAR : 0), X + 5 + (innerW - cols * cw) / 2};
        }
        int cols = Math.max(1, innerW / CELL_W);
        int used = Math.max(1, Math.min(cols, n));
        return new int[] {cols, CELL_W, CELL_H, X + 5 + (innerW - used * CELL_W) / 2};
    }

    private static boolean hasIcon(PadView.Row r) {
        return !r.icon().isEmpty() || r.hasImage();
    }

    private int rowHeight(PadView.Row r, int w) {
        boolean textOnly = !hasIcon(r) && r.button() == null && r.button2() == null;
        if (textOnly && r.lines().isEmpty() && !r.title().isEmpty()) return 17; // título de sección
        if (textOnly) {
            int n = 0;
            for (String line : r.lines()) n += Math.max(1, PadUi.lines(line, w - 18));
            return 8 + (r.title().isEmpty() ? 0 : 12) + n * 10 + ROW_PAD + rowGap();
        }
        int textW = textWidth(r, w);
        int h = 4 + 11 + (compact() ? r.lines().size() * 10 : linesHeight(r, textW)) + (r.progress() >= 0 ? 8 : 0) + ROW_PAD;
        if (compact()) return Math.max(hasIcon(r) ? ICON_C + 6 : 22, h) + ROW_GAP_C;
        return Math.max(hasIcon(r) ? ICON_BOX + 10 : 26, h + 2) + ROW_GAP;
    }

    private int linesHeight(PadView.Row r, int textW) {
        int n = 0;
        for (String line : r.lines()) n += Math.min(2, Math.max(1, PadUi.lines(line, textW)));
        return n * 10;
    }

    /** Lo que ocupa a la derecha de una fila: su etiqueta y sus botones. */
    private int rightWidth(PadView.Row r) {
        if (!r.click().isEmpty() && r.button() == null && r.button2() == null && r.badge().isEmpty()) return 12; // la flechita
        return (r.badge().isEmpty() ? 0 : PadUi.chipWidth(r.badge()) + 6) + buttonsWidth(r);
    }

    private int textWidth(PadView.Row r, int w) {
        return Math.max(30, w - (hasIcon(r) ? iconBox() + (compact() ? 13 : 17) : 14) - rightWidth(r) - 6);
    }

    /**
     * Interruptor (1.3.66): una fila con un solo botón SÍ o NO (los interruptores de los pads de administrador, el fuego
     * amigo del clan…) o con el par SÍ + NO (las preguntas de Protección) se dibuja como el de Ajustes, con la bola que
     * corre de un lado a otro. on: lo que está puesto; action: la que lo cambia.
     */
    private record Switch(boolean on, String action) {}

    private static final int SWITCH_W = 28, SWITCH_H = 14;
    /** Dónde está la bola de cada interruptor (por el título de su fila), para que corra al cambiar. */
    private final java.util.Map<String, Float> knobs = new java.util.HashMap<>();
    private long lastFrame;
    private float frameDt;

    private static Switch switchOf(PadView.Row r) {
        PadView.Btn a = r.button(), b = r.button2();
        if (a == null || !a.enabled() || a.action().isEmpty()) return null;
        boolean yes = a.label().equals("SÍ"), no = a.label().equals("NO");
        if (b == null && (yes || no)) return new Switch(yes, a.action());
        if (yes && b != null && b.label().equals("NO") && b.enabled()) {
            boolean on = a.style() != PadView.GRAY;
            return new Switch(on, on ? b.action() : a.action());
        }
        return null;
    }

    private int buttonsWidth(PadView.Row r) {
        if (switchOf(r) != null) return SWITCH_W + 4;
        int w = 0;
        if (r.button() != null) w += PadUi.buttonWidth(r.button().label()) + 4;
        if (r.button2() != null) w += PadUi.buttonWidth(r.button2().label()) + 4;
        return w;
    }

    private boolean inList(double my) {
        return my >= listTop && my < listBottom;
    }

    /** Casilla pequeña: el objeto en su tarjetita (sube al pasar el ratón), con su cantidad y un texto pixel debajo. */
    private void drawCell(GuiGraphics g, PadView.Cell c, int x, int y, double mx, double my, int acc) {
        boolean clickable = !c.action().isEmpty();
        boolean hover = PadUi.inside(mx, my, x + 1, y, CELL_W - 2, CELL_H - 1) && inList(my);
        int yy = PadSkin.card(g, x + 1, y, CELL_W - 2, CELL_H - 1, c.selected() ? 2 : hover && clickable ? 1 : 0, acc, 0);
        int iy = c.label().isEmpty() ? yy + (CELL_H - 1 - 16) / 2 : yy + 4;
        if (!(c.hasImage() && PadSkin.image(g, pad, c.image(), x + (CELL_W - 16) / 2, iy, 16)) && !c.icon().isEmpty()) {
            PadUi.item(g, c.icon(), x + (CELL_W - 16) / 2, iy, 1);
        }
        if (hover && !c.icon().isEmpty()) tooltip = c.icon();
        if (!c.label().isEmpty()) {
            String label = PadFont.fit(c.label(), CELL_W - 4);
            PadFont.drawCentered(g, label, x + CELL_W / 2, yy + 20, PadUi.onDark(c.color()) & 0xFFFFFF, false);
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
     * Tarjeta: arriba la vitrina del color de la tarjeta (rayas que se mueven, una luz detrás del objeto o de la imagen
     * y una franja de color abajo); el nombre en negrita y su etiqueta (precio en oro con la moneda, «+…» en verde,
     * estado en azul, gris si no se puede pulsar); la barra de progreso si tiene y la burbuja de aviso arriba. La
     * marcada: borde de oro con un aura que late, rayos de luz que giran y la cinta con la marca; al pasar el ratón,
     * sube, el objeto se mece, salen los rayos y la cruza un brillo. gallery: la vitrina grande con la imagen a 50.
     */
    /**
     * (1.3.159) Un nombre que no cabe en la tarjeta (en español son largos: «Espada de diamante», «Alfombra celeste»): en
     * dos líneas, un poco más pequeñas, en vez de «Espada de...». top es el borde de abajo de la vitrina.
     */
    private static void longName(GuiGraphics g, String name, int cx, int top, int w) {
        net.minecraft.client.gui.Font font = PadUi.font();
        float k = 0.7F;
        int lw = Math.round(w / k);
        String[] words = name.split(" ");
        StringBuilder first = new StringBuilder();
        int i = 0;
        while (i < words.length) {
            String next = first.length() == 0 ? words[i] : first + " " + words[i];
            if (font.width(next) > lw && first.length() > 0) break;
            first.setLength(0);
            first.append(next);
            i++;
        }
        String l1 = PadUi.fitEnd(first.toString(), lw);
        String l2 = i < words.length ? PadUi.fitEnd(String.join(" ", java.util.Arrays.copyOfRange(words, i, words.length)), lw) : "";
        g.pose().pushPose();
        g.pose().translate(cx, top + (l2.isEmpty() ? 8 : 5), 0);
        g.pose().scale(k, k, 1);
        PadUi.text(g, l1, -font.width(l1) / 2, 0, PadUi.TEXT);
        if (!l2.isEmpty()) PadUi.text(g, l2, -font.width(l2) / 2, 9, PadUi.TEXT);
        g.pose().popPose();
    }

    private void drawCard(GuiGraphics g, PadView.Cell c, int x, int y, int cw, int ch, double mx, double my, int acc, boolean gallery,
                          float t) {
        boolean clickable = !c.action().isEmpty();
        int w = cw - 4, h = ch - 4, x0 = x + 2;
        boolean hover = PadUi.inside(mx, my, x0, y, w, h) && inList(my);
        boolean lift = hover && clickable;
        int color = PadUi.onDark(c.color());
        if (c.selected()) {
            float a = 0.55F + 0.45F * (float) Math.sin(t * 3.2F);
            PadUi.box(g, x0 - 1, y - 1, w + 2, h + 2, PadSkin.alpha(PadUi.GOLD_HI, Math.round(90 + 90 * a)));
        }
        int yy = PadSkin.card(g, x0, y, w, h, c.selected() ? 2 : lift ? 1 : 0, acc, PadUi.tint(color, 0.45F));
        int sh = gallery ? GAL_SHOW : SHOW;
        int cx = x0 + w / 2;
        // la vitrina
        g.fillGradient(x0 + 1, yy + 1, x0 + w - 1, yy + 1 + sh, PadUi.tint(color, 0.62F), PadUi.tint(color, 0.22F));
        PadSkin.stripes(g, x0 + 1, yy + 1, w - 2, sh, 0.10F, t * 3);
        pad.clipPush(g, x0 + 1, yy + 1, w - 2, sh);
        if (c.selected() || lift) PadSkin.rays(g, cx, yy + 1 + sh / 2F, sh * 0.95F, c.selected() ? 0.55F : 0.35F, t * 25);
        PadSkin.glow(g, cx, yy + 1 + sh / 2F, sh * 0.62F, 0xFFFFFF, 165);
        pad.clipPop(g);
        PadUi.stretch(g, "floor", cx - 14, yy + sh - 6, 28, 5);
        g.fill(x0 + 1, yy + 1 + sh, x0 + w - 1, yy + 2 + sh, color);
        g.fill(x0 + 1, yy + 2 + sh, x0 + w - 1, yy + 3 + sh, PadUi.tint(color, 0.35F));
        g.fill(x0 + 2, yy + 1, x0 + w - 2, yy + 2, 0x96FFFFFF);
        int bob = lift || c.selected() ? Math.round((float) Math.sin(t * 4.4F)) : 0;
        int size = gallery ? GAL_IMG : 32;
        boolean model = PadSkinModel.is(c.image());
        boolean modelDrawn = false;
        if (model) {
            // una skin: el muñeco 3D con SOLO la skin (sin armadura ni cosméticos), dentro de la vitrina y mirando al ratón
            pad.clipPush(g, x0 + 1, yy + 1, w - 2, sh);
            modelDrawn = PadSkinModel.draw(g, c.image(), cx, yy + sh - 3 - bob, sh - 9, mx, my);
            pad.clipPop(g);
        }
        boolean photo = !model && c.hasImage() && PadSkin.wide(c.image());
        int photoH = photo ? Math.min(sh - 5, Math.round((w - 8) / PadSkin.aspect(c.image()))) : 0;
        boolean drawn = model ? modelDrawn : photo ? PadSkin.photo(g, c.image(), cx, yy + 1 + (sh - photoH) / 2 + bob, photoH) > 0
                : c.hasImage() && PadSkin.image(g, pad, c.image(), cx, yy + 1 + sh / 2F + bob, w - 10, size);
        if (!drawn && !c.icon().isEmpty()) bigItem(g, c.icon(), cx - 16, yy + 4 + bob + (sh - 40) / 2);
        if (hover && !c.icon().isEmpty() && !drawn) tooltip = c.icon();
        if (lift) PadSkin.sheen(g, x0 + 1, yy + 1, w - 2, sh, 1.6F, 0.3F);
        if (PadUi.font().width(c.label()) <= w - 6) {
            int nameW = PadUi.titleWidth(c.label(), w - 6);
            PadUi.title(g, c.label(), cx - nameW / 2, yy + sh + 7, w - 6, PadUi.TEXT);
        } else {
            longName(g, c.label(), cx, yy + sh, w - 6);
        }
        if (!c.sub().isEmpty()) {
            // si no cabe, se recorta lo de dentro de la etiqueta (sin contar la moneda ni el «+»)
            String sub = PadUi.fitChip(c.sub(), w - 6);
            int tone = PadUi.tone(sub, clickable || c.selected(), c.tone());
            PadUi.chip(g, cx - PadUi.chipWidth(sub) / 2, yy + sh + 18, sub, tone, 12);
        }
        if (c.progress() >= 0) {
            String pct = Math.round(Math.min(1F, c.progress()) * 100) + "%";
            int pw = PadUi.font().width(pct);
            int bw = w - 12 - pw - 4;
            PadSkin.progress(g, x0 + 6, yy + sh + 34, bw, c.progress());
            PadUi.text(g, pct, x0 + 6 + bw + 4, yy + sh + 33, PadUi.MUTED);
        }
        if (!c.badge().isEmpty()) {
            int bw = PadUi.chipWidth(c.badge());
            g.pose().pushPose();
            g.pose().translate(0, 0, 200); // encima del objeto
            PadUi.chip(g, x0 + w - bw - 3, yy + 4, c.badge(), PadUi.TONE_GREEN, 11);
            g.pose().popPose();
        }
        if (c.selected()) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            PadSkin.ribbon(g, x0 + 1, yy + 1);
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
        return Math.max(30, w - (HERO_SLOT + 20) - heroRight(r) - 6);
    }

    private int heroHeight(PadView.Row r, int w) {
        int h = 8 + 11 + linesHeight(r, heroTextWidth(r, w)) + (r.progress() >= 0 ? 10 : 0) + 6;
        if (PadSkinModel.is(r.image())) return Math.max(58, h); // el escaparate del Armario: la skin en grande (y caben las tarjetas)
        if (PadView.TRAIL_PREVIEW.equals(r.image())) return Math.max(60, h); // el escenario de la vista previa de las estelas
        return Math.max(HERO_MIN, h);
    }

    /**
     * La ficha grande: banner del color de la ficha (claro arriba, oscuro abajo) con rayas que se mueven; rayos de luz
     * que giran detrás del medallón con el objeto o la imagen; el título en blanco y negrita, las líneas, la barra (con
     * el tanto por ciento) y a la derecha la etiqueta (precio, con su moneda) y los botones. Destellos y un brillo.
     */
    private void drawHero(GuiGraphics g, PadView.Row r, int x, int y, int w, int h, double mx, double my, float t) {
        int color = PadSkin.deep(PadUi.onDark(r.color()));
        g.fill(x + 1, y + h, x + w - 1, y + h + 1, 0x5018265C);
        g.fill(x + 3, y + h + 1, x + w - 3, y + h + 2, 0x1E18265C);
        PadUi.box(g, x, y, w, h, PadSkin.darken(color, 0.45F));
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, PadSkin.mix(color, PadSkin.WHITE, 0.16F), PadSkin.darken(color, 0.74F));
        PadSkin.stripes(g, x + 1, y + 1, w - 2, h - 2, 0.08F, t * 3);
        int ms = HERO_SLOT, sx = x + 7, sy = y + (h - ms) / 2;
        pad.clipPush(g, x + 1, y + 1, w - 2, h - 2);
        PadSkin.rays(g, sx + ms / 2F, sy + ms / 2F, h * 1.15F, 0.5F, t * 18);
        PadSkin.glow(g, sx + ms / 2F, sy + ms / 2F, h * 0.8F, 0xFFFFFF, 90);
        PadSkin.glow(g, x + w - 30, y + h / 2F, 70, 0xFFFFFF, 55);
        pad.clipPop(g);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, PadSkin.lighten(color, 70));
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, PadSkin.darken(color, 0.55F));
        boolean photo = r.hasImage() && PadSkin.wide(r.image());
        int extra = 0;
        boolean drawn;
        if (PadSkinModel.is(r.image())) {
            // el escaparate del Armario: la skin en 3D (solo la skin) sobre una peana de luz, en su propio hueco
            int mh = h - 14, mw = Math.max(ms, Math.round(mh * 0.62F));
            float mcx = sx + mw / 2F;
            PadSkin.glow(g, mcx, y + h / 2F, mh * 0.7F, 0xFFFFFF, 120);
            PadUi.stretch(g, "floor", Math.round(mcx - mw * 0.45F), y + h - 12, Math.round(mw * 0.9F), 6);
            pad.clipPush(g, x + 1, y + 1, mw + 12, h - 2);
            drawn = PadSkinModel.draw(g, r.image(), mcx, y + h - 9, mh, mx, my);
            pad.clipPop(g);
            if (!drawn) bigItem(g, r.icon(), Math.round(mcx) - 16, y + (h - 32) / 2);
            drawn = true;
            extra = mw - ms;
        } else if (PadView.TRAIL_PREVIEW.equals(r.image())) {
            // Efectos › COMBATE: una espada dando tajos con la estela de los ajustes de ahora, en su escenario de noche
            int ph = h - 10, pw = Math.round(ph * 1.75F);
            PadUi.box(g, sx - 3, y + 4, pw + 2, ph + 2, PadSkin.darken(color, 0.3F));
            pad.clipPush(g, sx - 2, y + 5, pw, ph);
            try {
                net.tierrasfantasticas.tfclient.trail.TrailPreview.draw(g, sx - 2, y + 5, pw, ph, r.icon());
            } catch (RuntimeException e) {
                net.tierrasfantasticas.tfclient.TFClient.LOGGER.warn("TF Pad: la vista previa de las estelas falló", e);
            }
            pad.clipPop(g);
            drawn = true;
            extra = pw - ms - 4;
        } else if (photo) {
            // un retrato (oficios): en grande, como una foto, en vez de dentro del medallón
            int ph = Math.min(h - 8, 46);
            int pw = Math.round(ph * PadSkin.aspect(r.image()));
            PadSkin.photo(g, r.image(), sx + pw / 2F, y + (h - ph) / 2, ph);
            extra = pw - ms;
            drawn = true;
        } else {
            PadSkin.medallion(g, sx, sy, ms, color);
            PadUi.stretch(g, "floor", sx + 8, sy + ms - 7, ms - 16, 4);
            drawn = r.hasImage() && PadSkin.image(g, pad, r.image(), sx + 3, sy + 3, ms - 6);
        }
        if (!drawn && !r.icon().isEmpty()) {
            bigItem(g, r.icon(), sx + (ms - 32) / 2, sy + (ms - 32) / 2 - 1);
            if (PadUi.inside(mx, my, sx, sy, ms, ms)) tooltip = r.icon();
        }
        int tx = x + ms + 15 + extra, textW = heroTextWidth(r, w) - extra;
        int textH = 11 + linesHeight(r, textW) + (r.progress() >= 0 ? 10 : 0);
        int top = y + Math.max(6, (h - textH) / 2 + 1);
        int shade = PadSkin.darken(color, 0.38F), soft = PadSkin.mix(PadSkin.WHITE, color, 0.10F);
        PadSkin.shadowTitle(g, r.title(), tx, top, textW, PadSkin.WHITE, shade);
        int ly = top + 12;
        for (String line : r.lines()) {
            int n = PadUi.wrapEllipsis(g, line, tx + 1, ly + 1, textW, shade, 2);
            PadUi.wrapEllipsis(g, line, tx, ly, textW, soft, 2);
            ly += Math.max(1, n) * 10;
        }
        if (r.progress() >= 0) {
            String pct = Math.round(Math.min(1F, r.progress()) * 100) + "%";
            int pw = PadUi.font().width(pct);
            int bw = Math.min(textW - pw - 4, 160);
            PadSkin.progress(g, tx, ly + 1, bw, r.progress());
            PadSkin.shadowText(g, pct, tx + bw + 4, ly, PadSkin.WHITE, shade);
        }
        int bx = x + w - heroRight(r) - 3;
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
        int[][] sp = {{w - 14, 6}, {w - 50, h - 7}, {ms + 10, 5}};
        for (int i = 0; i < sp.length; i++) PadSkin.sparkle(g, x + sp[i][0], y + sp[i][1], 2, PadSkin.twinkle(i * 1.9F + 1.1F) * 0.9F, 0xFFFFFF);
        PadSkin.sheen(g, x + 1, y + 1, w - 2, h - 2, 7F, 0.2F);
    }

    /**
     * Una fila: tarjeta con una franja de color a la izquierda (la de la fila o, si es azul noche, la de la app; de oro
     * si está marcada), el objeto o la imagen en su medallón con luz, el título en negrita, las líneas, la barra y a la
     * derecha la etiqueta (precio, premio, estado) y los botones. Sin icono ni botones, una nota de texto; con solo
     * título, un título de sección.
     */
    private void drawRow(GuiGraphics g, PadView.Row r, int x, int y, int w, int h, double mx, double my, int acc, float t) {
        boolean clickable = !r.click().isEmpty();
        h -= rowGap(); // cada fila es su propia tarjeta, con aire debajo
        boolean hover = clickable && PadUi.inside(mx, my, x, y, w, h) && inList(my);
        boolean icon = hasIcon(r);
        boolean textOnly = !icon && r.button() == null && r.button2() == null;
        boolean section = textOnly && r.lines().isEmpty() && !r.title().isEmpty();
        int rgb = r.color() & 0xFFFFFF;
        boolean plain = rgb == 0x18265C || rgb == 0x7E8CA8;
        int titleColor = PadUi.onDark(r.color());
        if (section) {
            PadSkin.section(g, x + 2, y + 4, w - 4, r.title(), plain ? acc : titleColor);
            return;
        }
        int yy = PadSkin.card(g, x, y, w, h, r.selected() ? 2 : hover ? 1 : 0, acc, 0);
        int spine = r.selected() ? PadUi.GOLD : rgb == 0x7E8CA8 ? 0xFFAAB8CC : plain ? acc : titleColor;
        g.fillGradient(x + 1, yy + 1, x + 4, yy + h - 1, PadSkin.lighten(spine, 40), spine);
        if (clickable) {
            if (hover) pad.hover("§row" + r.click());
            hits.add(new Hit(x, y, w, h, 2, r.click(), 0));
        }
        if (textOnly) {
            int ty = yy + 5;
            if (!r.title().isEmpty()) {
                PadUi.title(g, r.title(), x + 9, ty, w - 16, plain ? PadSkin.darken(acc, 0.75F) : PadSkin.ink(titleColor));
                ty += 12;
            }
            for (String line : r.lines()) ty += Math.max(1, PadUi.wrap(g, line, x + 9, ty, w - 16, PadUi.MUTED, 30)) * 10;
            return;
        }
        int tx = x + 10;
        int tint = plain ? acc : titleColor;
        // un lavado suave del color de la fila que se apaga hacia la derecha (franjas verticales de 4)
        for (int i = 0, n = Math.min(24, (w - 8) / 4); i < n; i++) {
            int a = Math.round(58 * (1 - i / (float) n) * (1 - i / (float) n));
            if (a > 0) g.fill(x + 4 + i * 4, yy + 1, x + 8 + i * 4, yy + h - 1, PadSkin.alpha(tint & 0xFFFFFF, a));
        }
        if (hover) PadSkin.sheen(g, x + 1, yy + 1, w - 2, h - 2, 1.4F, 0.25F);
        if (icon && compact()) {
            // compacta: el objeto a su tamaño en una pastilla de 22
            int iy = yy + (h - ICON_C) / 2;
            PadSkin.tile(g, x + 6, iy, ICON_C, tint, hover);
            boolean drawn = r.hasImage() && PadSkin.image(g, pad, r.image(), x + 8, iy + 2, ICON_C - 4);
            if (!drawn && !r.icon().isEmpty()) {
                PadUi.item(g, r.icon(), x + 6 + (ICON_C - 16) / 2, iy + (ICON_C - 16) / 2, 1);
                if (PadUi.inside(mx, my, x + 6, iy, ICON_C, ICON_C) && inList(my)) tooltip = r.icon();
            }
            tx = x + ICON_C + 11;
        } else if (icon) {
            int iy = yy + (h - ICON_BOX) / 2;
            // (1.3.120, el dueño con Oculus + Photon: «detrás de todos los iconos del pad están esos círculos, ¿qué es?»)
            // el brillo redondo detrás del icono asomaba por encima y por debajo de la fila (y con la mezcla de Photon
            // salía como un disco sólido): fuera; el icono va solo en su recuadro
            PadSkin.tile(g, x + 7, iy, ICON_BOX, tint, hover);
            int bob = hover ? Math.round((float) Math.sin(t * 4.4F)) : 0;
            boolean drawn = r.hasImage() && PadSkin.image(g, pad, r.image(), x + 9, iy + 2 + bob, ICON_BOX - 4);
            if (!drawn && !r.icon().isEmpty()) {
                bigItem(g, r.icon(), x + 7 + (ICON_BOX - 32) / 2, iy + (ICON_BOX - 32) / 2 + bob);
                if (PadUi.inside(mx, my, x + 7, iy, ICON_BOX, ICON_BOX) && inList(my)) tooltip = r.icon();
            }
            tx = x + ICON_BOX + 14;
        }
        int textW = textWidth(r, w);
        int textH = 11 + (compact() ? r.lines().size() * 10 : linesHeight(r, textW)) + (r.progress() >= 0 ? 8 : 0);
        int top = icon ? yy + Math.max(5, (h - textH) / 2 + 1) : yy + 5;
        PadUi.title(g, r.title(), tx, top, textW, PadSkin.ink(titleColor));
        int ly = top + 11;
        int maxLines = compact() ? 1 : 2; // compactas: cada línea en una sola, con «...»
        for (String line : r.lines()) ly += Math.max(1, PadUi.wrapEllipsis(g, line, tx, ly, textW, PadUi.MUTED, maxLines)) * 10;
        if (r.progress() >= 0) PadSkin.progress(g, tx, ly + 1, Math.min(textW, 140), r.progress());
        int bx = x + w - rightWidth(r) - 2;
        if (clickable && r.button() == null && r.button2() == null && r.badge().isEmpty()) {
            // se puede entrar: una flechita del color de la fila (se mueve un poco al pasar el ratón)
            int ax = x + w - 11 + (hover ? 2 : 0), ay = yy + h / 2;
            int ac = hover ? PadSkin.darken(tint, 0.8F) : PadSkin.mix(tint, PadSkin.WHITE, 0.35F);
            for (int i = 0; i < 4; i++) g.fill(ax + i, ay - 3 + i, ax + i + 2, ay - 2 + i, ac);
            for (int i = 0; i < 3; i++) g.fill(ax + 2 - i, ay + 1 + i, ax + 4 - i, ay + 2 + i, ac);
        }
        if (!r.badge().isEmpty()) {
            PadUi.chip(g, bx + 2, yy + (h - 12) / 2, r.badge(), PadUi.tone(r.badge(), true, 0), 12);
            bx += PadUi.chipWidth(r.badge()) + 6;
        }
        int by = yy + (h - 16) / 2;
        Switch sw = switchOf(r);
        if (sw != null) {
            drawSwitch(g, r.title(), sw, bx, yy + (h - SWITCH_H) / 2, mx, my);
            return;
        }
        if (r.button() != null) {
            int b1 = PadUi.buttonWidth(r.button().label());
            button(g, bx, by, b1, r.button(), mx, my, false);
            bx += b1 + 4;
        }
        if (r.button2() != null) button(g, bx, by, PadUi.buttonWidth(r.button2().label()), r.button2(), mx, my, false);
    }

    /** El interruptor de una fila: la bola va hacia donde esté puesto (al cambiar, corre); pulsarlo lo cambia. */
    private void drawSwitch(GuiGraphics g, String key, Switch sw, int x, int y, double mx, double my) {
        float target = sw.on() ? 1F : 0F;
        Float now = knobs.get(key);
        float k = now == null || !PadSettings.animations ? target : now + (target - now) * Math.min(1F, frameDt * 14F);
        if (Math.abs(k - target) < 0.01F) k = target;
        knobs.put(key, k);
        boolean visible = y >= listTop && y + SWITCH_H <= listBottom + 1;
        boolean hover = visible && PadUi.inside(mx, my, x - 2, y - 2, SWITCH_W + 4, SWITCH_H + 4) && inList(my);
        if (hover) pad.hover("§sw" + sw.action());
        PadSkin.toggle(g, x, y, k, hover);
        if (visible) hits.add(0, new Hit(x - 2, y - 2, SWITCH_W + 4, SWITCH_H + 4, 0, sw.action(), PadView.GREEN));
    }

    private void button(GuiGraphics g, int x, int y, int w, PadView.Btn b, double mx, double my, boolean footer) {
        boolean visible = footer || (y >= listTop && y + 16 <= listBottom + 1);
        boolean clickable = b.enabled() && visible && !b.action().isEmpty();
        boolean hover = clickable && PadUi.inside(mx, my, x, y, w, 16) && (footer || inList(my));
        if (hover) pad.hover("§btn" + b.action() + x + "," + y);
        boolean down = pressed.equals(b.action() + "@" + x + "," + y) && System.currentTimeMillis() - pressedAt < PRESS_MS;
        PadSkin.button(g, x, y, w, b.label(), b.style(), hover, b.enabled(), down);
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
        List<Component> lines = new java.util.ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), tooltip));
        // (1.3.159, el dueño: «pon los items en español») el nombre, en español aunque el juego esté en otro idioma
        if (!lines.isEmpty() && !tooltip.hasCustomHoverName()) {
            String es = net.tierrasfantasticas.tfclient.util.TFNames.es(tooltip);
            if (es != null && !es.isBlank()) lines.set(0, Component.literal(es).withStyle(lines.get(0).getStyle()));
        }
        int tw = 0;
        for (Component c : lines) tw = Math.max(tw, PadUi.font().width(c));
        float w = (tw + 20) * k, h = (lines.size() * 10 + 8) * k;
        float tx = mx + w > g.guiWidth() ? Math.max(0, mx - w - 4) : mx;
        float ty = my + h > g.guiHeight() ? Math.max(0, g.guiHeight() - h) : my;
        g.pose().pushPose();
        g.pose().translate(tx, ty, 0);
        g.pose().scale(k, k, 1);
        g.renderTooltip(PadUi.font(), lines, tooltip.getTooltipImage(), 0, 0);
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
                    pressed = h.action + "@" + h.x + "," + h.y;
                    pressedAt = System.currentTimeMillis();
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

    /** (1.3.109) Para la prueba en el juego: hace esa acción como si se pulsara (p. ej. «tab:combate»). */
    void actForTest(String action) {
        if (action.startsWith("§scroll:")) scroll = Integer.parseInt(action.substring(8));
        else if (action.startsWith("§type:")) {
            // (1.3.159) escribir en el campo de abajo y pulsar su botón (como el jugador)
            input = action.substring(6);
            submit();
        } else run(action);
    }

    /** Acciones del propio pad («§open:app») o del servidor. */
    private void run(String action) {
        if (action.startsWith("§open:")) {
            pad.openApp(action.substring(6));
            return;
        }
        // (1.3.156) la paleta de colores (PadColorPicker): «§color:<app>:<pestaña>:<RRGGBB>:<texto>»
        if (action.startsWith("§color:")) {
            PadColorPicker picker = PadColorPicker.parse(pad, action);
            if (picker != null) {
                pad.sound("page", 0.55F);
                pad.setPage(picker);
            }
            return;
        }
        // (1.3.156) la guerra de clanes con su barra vertical doble (PadClanWarPage, datos de la app «guerra»)
        if (action.equals("§guerra")) {
            pad.sound("page", 0.55F);
            pad.setPage(new PadClanWarPage(pad));
            TFPadClient.openServerApp("guerra", "");
            return;
        }
        send(action, "");
    }

    private void send(String action, String text) {
        if (app.equals("efectos") && PadCombat.local(view, action)) {
            set(PadCombat.act(view, action));
            return;
        }
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
        scroll += (int) Math.round(dy); // arrastrar hacia abajo baja la lista (como la barra)
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
