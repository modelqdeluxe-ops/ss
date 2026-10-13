package net.tierrasfantasticas.tfclient.pad;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/**
 * El selector de emojis de Comunidad, como el de Discord: buscador arriba (escribe y filtra al momento, en español o en
 * inglés: «fuego», «risa», «joy»...), pestañas con el emoji de cada categoría (Frecuentes, Animados, Sonrisas,
 * Personas, Naturaleza, Comida, Actividades, Viajes, Objetos, Símbolos y Banderas: clic = salta a ella; la elegida
 * sigue a lo que se ve), la rejilla con todas una debajo de otra (rueda o arrastrar) y abajo el emoji bajo el ratón en
 * grande (sin nombres). El que ya pusiste sale en oro. Clic = elegir.
 * Todo en unidades de la página (a escala).
 */
final class PadEmojiPicker {
    /** Celda de la rejilla, emoji dentro, alto de los títulos de sección, de las pestañas y del pie. */
    static final int CELL = 17, EMOJI = 13, HEAD = 12, TAB_H = 18, TAB_EMOJI = 12, FOOT = 24;
    private static final int PAD = 5, BAR = 6;

    private final TFPadScreen pad;
    private final Consumer<PadEmojis.Emoji> onPick;
    private String query = "";
    private int scroll;
    /** Lo que sale en la rejilla: secciones con su título y sus emojis (se rehace al escribir). */
    private final List<Section> sections = new ArrayList<>();
    private int contentH;
    private PadEmojis.Emoji hovered;
    private String hoveredTab;
    private Set<String> mine = Set.of();
    /** Dónde quedó en el último fotograma (para los clics). */
    private int x, y, w, h, cols;
    private int gridY, gridH;
    private final List<int[]> tabBoxes = new ArrayList<>();
    private final List<String> tabIds = new ArrayList<>();
    private boolean dragging;

    private record Section(String id, String title, List<PadEmojis.Emoji> emojis, int top) {}

    PadEmojiPicker(TFPadScreen pad, Consumer<PadEmojis.Emoji> onPick) {
        this.pad = pad;
        this.onPick = onPick;
    }

    /** Se abre limpio: sin búsqueda y arriba del todo; mine = los emojis que ya pusiste (en oro). */
    void open(Set<String> mine) {
        this.mine = mine;
        query = "";
        scroll = 0;
        sections.clear();
    }

    void mine(Set<String> mine) {
        this.mine = mine;
    }

    /** Medidas: lo más ancho que quepa (13 columnas como mucho) y lo más alto (190 como mucho), centrado en anchorX. */
    void place(int areaX, int areaY, int areaW, int areaH, int anchorX) {
        cols = Math.max(6, Math.min(13, (areaW - 12 - PAD * 2 - BAR) / CELL));
        w = cols * CELL + PAD * 2 + BAR;
        h = Math.max(110, Math.min(190, areaH - 6));
        x = Math.max(areaX + 4, Math.min(anchorX - w / 2, areaX + areaW - w - 4));
        y = areaY + (areaH - h) / 2;
        gridY = y + PAD + 16 + 3 + TAB_H + 3;
        gridH = y + h - FOOT - gridY;
        if (sections.isEmpty()) layout();
    }

    boolean inside(double mx, double my) {
        return PadUi.inside(mx, my, x, y, w, h);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Contenido
    // ---------------------------------------------------------------------------------------------------------------

    private void layout() {
        sections.clear();
        int top = 0;
        if (!query.isBlank()) {
            List<PadEmojis.Emoji> found = PadEmojis.search(query, 26 * 13);
            sections.add(new Section("resultados", found.isEmpty() ? "SIN RESULTADOS" : "RESULTADOS", found, 0));
            top = HEAD + rows(found.size()) * CELL;
        } else {
            List<PadEmojis.Emoji> freq = PadCommunityClient.frequent(cols * 2);
            sections.add(new Section("frecuentes", "FRECUENTES", freq, top));
            top += HEAD + rows(freq.size()) * CELL;
            for (PadEmojis.Category c : PadEmojis.categories()) {
                List<PadEmojis.Emoji> l = PadEmojis.inCategory(c.id());
                if (l.isEmpty()) continue;
                sections.add(new Section(c.id(), c.name().toUpperCase(java.util.Locale.ROOT), l, top));
                top += HEAD + rows(l.size()) * CELL;
            }
        }
        contentH = top + 2;
    }

    private int rows(int n) {
        return Math.max(n == 0 ? 0 : 1, (n + cols - 1) / cols);
    }

    private void clamp() {
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - gridH)));
    }

    /** La sección que se ve arriba (para encender su pestaña). */
    private String current() {
        String cur = sections.isEmpty() ? "" : sections.get(0).id();
        for (Section s : sections) if (s.top() <= scroll + 2) cur = s.id();
        return cur;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Dibujo
    // ---------------------------------------------------------------------------------------------------------------

    void render(GuiGraphics g, double mx, double my) {
        int px = PadEmojiDraw.px(pad);
        clamp();
        hovered = null;
        hoveredTab = null;
        // sombra, marco de tinta y de oro, y fondo blanco
        g.fill(x + 3, y + 3, x + w + 3, y + h + 3, 0x5518265C);
        PadUi.box(g, x, y, w, h, PadUi.INK);
        PadUi.box(g, x + 1, y + 1, w - 2, h - 2, PadUi.GOLD_LO);
        g.fillGradient(x + 2, y + 2, x + w - 2, y + h - 2, 0xFFFFFFFF, PadUi.PANEL_TOP);
        search(g);
        tabs(g, mx, my, px);
        // la rejilla, recortada
        g.fill(x + 2, gridY - 2, x + w - 2, gridY - 1, 0xFFD6E6F6);
        pad.scissor(g, x + 2, gridY, w - 4, gridH);
        boolean inGrid = PadUi.inside(mx, my, x + PAD, gridY, cols * CELL, gridH);
        for (Section s : sections) {
            int sy = gridY + s.top() - scroll;
            int n = s.emojis().size();
            int sh = HEAD + rows(n) * CELL;
            if (sy + sh < gridY || sy > gridY + gridH) continue;
            g.drawString(PadUi.font(), PadUi.bold(s.title()), x + PAD + 1, sy + 2, PadUi.MUTED, false);
            if (s.id().equals("resultados") && n == 0) {
                PadUi.text(g, PadUi.fitEnd("Nada con «" + query + "». Prueba en inglés o en español.", w - PAD * 2 - 4),
                        x + PAD + 1, sy + HEAD + 4, PadUi.MUTED);
            }
            for (int i = 0; i < n; i++) {
                int cx = x + PAD + i % cols * CELL, cy = sy + HEAD + i / cols * CELL;
                if (cy + CELL < gridY || cy > gridY + gridH) continue;
                PadEmojis.Emoji e = s.emojis().get(i);
                boolean hov = inGrid && !dragging && PadUi.inside(mx, my, cx, cy, CELL, CELL);
                boolean sel = mine.contains(e.id());
                if (sel || hov) {
                    PadUi.box(g, cx, cy, CELL, CELL, sel ? 0xFFE0A030 : 0xFF9DBCE0);
                    g.fill(cx + 1, cy + 1, cx + CELL - 1, cy + CELL - 1, sel ? 0xFFFFF3C8 : 0xFFEAF4FD);
                }
                int o = (CELL - EMOJI) / 2;
                PadEmojiDraw.draw(g, e, cx + o, cy + o - (hov ? 1 : 0), EMOJI, px);
                if (hov) {
                    hovered = e;
                    pad.hover("§emo" + e.id());
                }
            }
        }
        pad.noScissor(g);
        PadUi.scrollbar(g, x + w - PAD - 4, gridY + 1, gridH - 2, gridH, contentH, scroll);
        footer(g, px);
    }

    /** El buscador: siempre escribiendo (como en Discord, se teclea nada más abrir). */
    private void search(GuiGraphics g) {
        int fx = x + PAD, fy = y + PAD, fw = w - PAD * 2;
        PadUi.box(g, fx, fy, fw, 16, PadUi.INK);
        g.fill(fx + 1, fy + 1, fx + fw - 1, fy + 15, 0xFFFFFFFF);
        g.fill(fx + 1, fy + 1, fx + fw - 1, fy + 2, 0xFFD6E6F6);
        // la lupa
        int lx = fx + fw - 13, ly = fy + 4;
        int c = query.isEmpty() ? 0xFF96AACC : PadUi.INK;
        g.fill(lx + 1, ly, lx + 5, ly + 1, c);
        g.fill(lx + 1, ly + 5, lx + 5, ly + 6, c);
        g.fill(lx, ly + 1, lx + 1, ly + 5, c);
        g.fill(lx + 5, ly + 1, lx + 6, ly + 5, c);
        for (int i = 0; i < 3; i++) g.fill(lx + 5 + i, ly + 5 + i, lx + 7 + i, ly + 6 + i, c);
        String shown = query.isEmpty() ? PadUi.fitEnd("Buscar: fuego, risa, corazón...", fw - 24) : PadUi.fitStart(query, fw - 24);
        PadUi.text(g, shown, fx + 5, fy + 4, query.isEmpty() ? 0xFF96AACC : PadUi.TEXT);
        if ((System.currentTimeMillis() / 500) % 2 == 0) {
            int cx = fx + 5 + (query.isEmpty() ? 0 : PadUi.font().width(shown));
            g.fill(cx, fy + 3, cx + 1, fy + 13, PadUi.TEXT);
        }
    }

    /** Las pestañas: un emoji por categoría (Frecuentes = el reloj); la que se ve, con fondo y raya de oro. */
    private void tabs(GuiGraphics g, double mx, double my, int px) {
        tabBoxes.clear();
        tabIds.clear();
        List<String[]> tabs = new ArrayList<>();
        tabs.add(new String[] {"frecuentes", "Frecuentes", "clock3"});
        for (PadEmojis.Category c : PadEmojis.categories()) tabs.add(new String[] {c.id(), c.name(), c.icon()});
        int n = tabs.size();
        int tw = Math.min(20, (w - PAD * 2) / n);
        int tx = x + (w - tw * n) / 2, ty = y + PAD + 16 + 3;
        String cur = query.isBlank() ? current() : "";
        for (int i = 0; i < n; i++) {
            String[] t = tabs.get(i);
            int bx = tx + i * tw;
            boolean sel = t[0].equals(cur);
            boolean hov = PadUi.inside(mx, my, bx, ty, tw, TAB_H);
            if (sel) {
                g.fill(bx + 1, ty, bx + tw - 1, ty + TAB_H, 0xFFDCEBFA);
                g.fill(bx + 2, ty + TAB_H - 2, bx + tw - 2, ty + TAB_H, PadUi.GOLD);
            } else if (hov) {
                g.fill(bx + 1, ty, bx + tw - 1, ty + TAB_H, 0xFFEEF5FD);
            }
            PadEmojis.Emoji icon = PadEmojis.byName(t[2]);
            int o = (tw - TAB_EMOJI) / 2;
            if (icon != null) PadEmojiDraw.draw(g, icon, bx + o, ty + 2 - (hov && !sel ? 1 : 0), TAB_EMOJI, px);
            if (hov) {
                hoveredTab = t[1];
                pad.hover("§emotab" + t[0]);
            }
            tabBoxes.add(new int[] {bx, ty, tw, TAB_H});
            tabIds.add(t[0]);
        }
    }

    /** Abajo: el emoji bajo el ratón en grande, sin nombre (o la pestaña, o una pista para buscar). */
    private void footer(GuiGraphics g, int px) {
        int fy = y + h - FOOT;
        g.fill(x + 2, fy, x + w - 2, fy + 1, 0xFFD6E6F6);
        g.fill(x + 2, fy + 1, x + w - 2, y + h - 2, 0xFFEFF6FD);
        int tx = x + PAD + 22, tw = w - PAD * 2 - 24;
        if (hovered != null) {
            // solo el emoji, en grande (sin nombre ni atajo)
            PadEmojiDraw.draw(g, hovered, x + PAD + 1, fy + 3, 18, px);
        } else if (hoveredTab != null) {
            g.drawString(PadUi.font(), PadUi.bold(hoveredTab), x + PAD + 2, fy + 8, PadUi.TEXT, false);
        } else {
            PadUi.text(g, PadUi.fitEnd("Elige un emoji o escribe para buscar", w - PAD * 2 - 4),
                    x + PAD + 2, fy + 8, PadUi.MUTED);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Ratón y teclado
    // ---------------------------------------------------------------------------------------------------------------

    /** Clic dentro del selector (true si lo usó). */
    boolean click(double mx, double my) {
        dragging = false;
        for (int i = 0; i < tabBoxes.size(); i++) {
            int[] b = tabBoxes.get(i);
            if (!PadUi.inside(mx, my, b[0], b[1], b[2], b[3])) continue;
            String id = tabIds.get(i);
            if (!query.isEmpty()) {
                query = "";
                layout();
            }
            for (Section s : sections) if (s.id().equals(id)) scroll = s.top();
            clamp();
            pad.sound("tab", 0.6F);
            return true;
        }
        if (hovered != null && PadUi.inside(mx, my, x + PAD, gridY, cols * CELL, gridH)) {
            onPick.accept(hovered);
            return true;
        }
        return inside(mx, my);
    }

    boolean scroll(double delta) {
        scroll -= (int) Math.signum(delta) * CELL * 2;
        clamp();
        return true;
    }

    /** Arrastrar la rejilla (en unidades). */
    void drag(double dy) {
        dragging = true;
        scroll -= (int) Math.round(dy);
        clamp();
    }

    void release() {
        dragging = false;
    }

    boolean key(int key) {
        if (key == 259) { // borrar
            if (!query.isEmpty()) {
                query = query.substring(0, query.length() - 1);
                scroll = 0;
                layout();
            }
            return true;
        }
        if (key == 264) { // flechas: bajar y subir
            scroll += CELL;
            clamp();
            return true;
        }
        if (key == 265) {
            scroll -= CELL;
            clamp();
            return true;
        }
        if (key == 257 || key == 335) { // Intro: el primero de los resultados
            if (!query.isBlank() && !sections.isEmpty() && !sections.get(0).emojis().isEmpty()) onPick.accept(sections.get(0).emojis().get(0));
            return true;
        }
        return false;
    }

    boolean chr(char c) {
        if (c < ' ' || c == '§' || query.length() >= 32) return false;
        query += c;
        scroll = 0;
        layout();
        return true;
    }
}
