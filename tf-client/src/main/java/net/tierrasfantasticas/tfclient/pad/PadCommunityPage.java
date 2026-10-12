package net.tierrasfantasticas.tfclient.pad;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Comunidad: la red social del servidor. Pestañas RECIENTES / POPULARES / MÍAS y, debajo, las publicaciones una
 * debajo de otra (cada una en su tarjeta: la foto, quién la subió, cuándo, el texto con sus emojis, REACCIONAR con las
 * reacciones que ya tiene y el botón del corazón para dar o quitar like, también en las tuyas); se baja con la rueda o
 * arrastrando. Clic en una foto para verla en grande. BORRAR (las tuyas, o el staff) y DENUNCIAR (las de otros).
 * «+ FOTO» lleva a la Cámara.
 * <p>Reacciones como en Discord: REACCIONAR abre el selector de emojis ({@link PadEmojiPicker}: casi 2.000 de Discord y
 * casi 100 animados, con buscador y categorías) y cada jugador reacciona con uno (otro lo cambia); las etiquetas de cada foto (emoji y
 * cuántos) se pulsan para sumarte o quitarte de esa reacción. Las tuyas, en oro.
 */
final class PadCommunityPage extends PadPage {
    private static final String[][] TABS = {{"recientes", "RECIENTES"}, {"populares", "POPULARES"}, {"mias", "MÍAS"}};

    private int scroll;
    private PadCommunityNet.Post full;
    private long confirm;
    private String confirmId = "";
    /** Zonas de clic de lo dibujado en el último fotograma (la primera que contiene el ratón se lleva el clic). */
    private final List<Hit> hits = new ArrayList<>();
    private record Hit(int x, int y, int w, int h, Runnable action) {}
    /** Emojis de las reacciones de cada tarjeta, alto de sus etiquetas y hueco entre filas de etiquetas. */
    private static final int CHIP_EMOJI = 9, CHIP_H = 13, CHIP_ROW = 15;
    /** El selector de emojis abierto (id de la foto) y dónde está su botón REACCIONAR. */
    private String pickerFor;
    private boolean pickerSeen;
    private int pickerX;
    private final PadEmojiPicker picker;
    /** La etiqueta de reacción bajo el ratón (para su globo con el :atajo:). */
    private PadEmojis.Emoji chipHover;
    /**
     * Arrastrar para bajar la lista empieza a partir de DRAG_START unidades desde donde se pulsó. Minecraft llama a
     * mouseDragged con cualquier temblor del ratón entre pulsar y soltar: antes eso cerraba el selector recién abierto
     * con el mismo clic de REACCIONAR (por eso «no abría»).
     */
    private static final int DRAG_START = 4;
    private double pressY;
    private boolean dragging;

    PadCommunityPage(TFPadScreen pad) {
        super(pad, "comunidad");
        picker = new PadEmojiPicker(pad, this::picked);
        // el índice de emojis (casi 2.000) se lee en segundo plano la primera vez, sin parar el juego
        net.minecraft.Util.backgroundExecutor().execute(PadEmojis::all);
        PadCommunityClient.open(PadCommunityClient.tab);
    }

    @Override
    String title() {
        return "COMUNIDAD";
    }

    void updated() {}

    /** Ancho y alto de la foto de cada tarjeta (16:9, la mitad del ancho como mucho). */
    private int photoW() {
        return Math.max(128, Math.min(256, (W - 20) / 2)) / 16 * 16;
    }

    private int photoH() {
        return photoW() * 9 / 16;
    }

    private int cardH() {
        return photoH() + 14;
    }

    @Override
    void render(GuiGraphics g, double mx, double my, float partial) {
        double pmx = mx, pmy = my;
        hits.clear();
        chipHover = null;
        // con el selector abierto, lo de debajo no se ilumina
        if (pickerFor != null && full == null) {
            mx = -1000;
            my = -1000;
        }
        // la ventana con sus pestañas de carpeta encima (la elegida, la última: se une a ella) y + FOTO a la derecha
        int top = Y + 14, bottom = Y + H;
        PadUi.panel(g, X, top, W, bottom - top);
        int x = X + 4;
        int selX = -1, selW = 0;
        String selLabel = "";
        for (String[] t : TABS) {
            int w = PadFont.width(t[1]) + 14;
            boolean sel = t[0].equals(PadCommunityClient.tab);
            boolean hover = PadUi.inside(mx, my, x, Y, w, 15);
            if (sel) {
                selX = x;
                selW = w;
                selLabel = t[1];
            } else {
                if (hover) pad.hover("§tab" + t[0]);
                PadUi.tab(g, x, Y, w, t[1], hover ? 1 : 0);
            }
            String key = t[0];
            hit(x, Y, w, 15, () -> {
                if (!key.equals(PadCommunityClient.tab)) {
                    scroll = 0;
                    pad.sound("tab", 0.7F);
                    PadCommunityClient.open(key);
                }
            });
            x += w + 2;
        }
        if (selX >= 0) PadUi.tab(g, selX, Y, selW, selLabel, 2);
        int fw = PadUi.buttonWidth("+ FOTO");
        boolean fh = PadUi.inside(mx, my, X + W - fw - 4, Y - 3, fw, 16);
        if (fh) pad.hover("§foto");
        PadUi.button(g, X + W - fw - 4, Y - 3, fw, "+ FOTO", PadView.GOLD, fh, true);
        hit(X + W - fw - 4, Y - 3, fw, 16, () -> pad.openApp("camara"));

        List<PadCommunityNet.Post> posts = PadCommunityClient.POSTS;
        if (posts.isEmpty()) {
            if (PadCommunityClient.loading) {
                PadUi.spinner(g, X + W / 2, (top + bottom) / 2 - 4);
            } else {
                String msg = PadCommunityClient.tab.equals("mias") ? "No has publicado fotos."
                        : "Sin fotos.";
                int n = PadUi.lines(msg, W - 60);
                PadUi.wrap(g, msg, X + 30, (top + bottom) / 2 - n * 5, W - 60, PadUi.MUTED, 3);
            }
            return;
        }
        int ch = cardH(), visible = bottom - top - 8;
        int contentH = posts.size() * ch + 2;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - visible)));
        pad.scissor(g, X + 2, top + 4, W - 4, visible);
        int cy = top + 5 - scroll;
        pickerSeen = false;
        for (PadCommunityNet.Post post : posts) {
            if (cy + ch > top && cy < bottom) drawPost(g, post, X + 5, cy, W - 16, ch - 4, mx, my, top, bottom);
            cy += ch;
        }
        pad.noScissor(g);
        PadUi.scrollbar(g, X + W - 7, top + 5, visible - 2, visible, contentH, scroll);
        // cerca del final, la página siguiente
        if (scroll + visible > contentH - ch * 2) PadCommunityClient.more();
        if (pickerFor != null) {
            PadCommunityNet.Post open = find(pickerFor);
            if (open == null || !pickerSeen) pickerFor = null;
            else drawPicker(g, open, pmx, pmy);
        }
        if (full != null) drawFull(g, full);
    }

    private void drawPost(GuiGraphics g, PadCommunityNet.Post post, int x, int y, int w, int h, double mx, double my, int top, int bottom) {
        boolean inList = my >= top + 2 && my < bottom - 2;
        PadUi.card(g, x, y, w, h, post.mine() ? 2 : 0);
        int pw = photoW(), ph = photoH();
        int px = x + 5, py = y + 5;
        PadUi.box(g, px - 2, py - 2, pw + 4, ph + 4, PadUi.INK);
        PadUi.box(g, px - 1, py - 1, pw + 2, ph + 2, PadUi.GOLD_LO);
        g.fill(px, py, px + pw, py + ph, 0xFF1A2440);
        ResourceLocation tex = PadCommunityClient.texture(post.id());
        if (tex != null) {
            int[] sz = PadCommunityClient.textureSize(post.id());
            g.blit(tex, px, py, pw, ph, 0, 0, sz[0], sz[1], sz[0], sz[1]);
        } else {
            PadUi.spinner(g, px + pw / 2, py + ph / 2 - 4);
        }
        if (inList) hit(px, py, pw, ph, () -> full = post);
        // columna derecha
        int cx = px + pw + 10, cw = x + w - 6 - cx;
        PadUi.text(g, PadUi.fitEnd(post.name(), cw), cx, py + 1, PadUi.GOLD_TEXT);
        PadUi.text(g, ago(post.time()), cx, py + 12, PadUi.MUTED);
        // reacciones: el botón REACCIONAR (abre el selector de emojis; también en tus fotos) y, en etiquetas, las que ya
        // tiene la foto con cuántos (las tuyas, en oro; clic = sumarte o quitarte). Si no caben al lado del botón, suben
        // en filas encima (el texto se recorta) y lo que no quepa sale como «+N».
        int ey = py + ph - 32;
        String rlabel = "REACCIONAR";
        int rbw = PadUi.buttonWidth(rlabel);
        boolean open = post.id().equals(pickerFor);
        if (open) {
            pickerSeen = true;
            pickerX = cx;
        }
        boolean rhov = inList && PadUi.inside(mx, my, cx, ey - 1, rbw, 16);
        if (rhov) pad.hover("§reaccionar" + post.id());
        PadUi.button(g, cx, ey - 1, rbw, rlabel, PadView.BLUE, rhov || open, true);
        if (inList) hits.add(0, new Hit(cx, ey - 1, rbw, 16, () -> openPicker(post)));
        int capTop = py + 26;
        int maxRows = 1 + Math.max(0, (ey - 2 - (post.caption().isEmpty() ? 0 : 10) - capTop) / CHIP_ROW);
        Chips chips = chips(post, cx, cx + rbw + 4, cx + cw, maxRows);
        for (int[] c : chips.placed()) drawChip(g, post, c, ey + 1 - (chips.rows() - 1 - c[1]) * CHIP_ROW, mx, my, inList);
        if (!post.caption().isEmpty()) {
            int chipsTop = ey + 1 - (chips.rows() - 1) * CHIP_ROW;
            int maxLines = Math.max(1, Math.min((ph - 60) / 10, (chipsTop - 2 - capTop) / 10));
            PadEmojiDraw.wrap(g, "«" + post.caption() + "»", cx, capTop, cw, PadUi.TEXT, maxLines, PadEmojiDraw.px(pad));
        }
        // like: el corazón es un botón (lleno y rosa si ya diste like) con cuántos lleva; clic = dar o quitar el like.
        // También en tus fotos.
        int ly = py + ph - 15;
        String likes = post.likes() + (post.likes() == 1 ? " like" : " likes");
        int lw = 20 + PadUi.font().width(likes) + 6;
        boolean lh = inList && PadUi.inside(mx, my, cx, ly - 1, lw, 16);
        likeButton(g, cx, ly - 1, lw, likes, post.liked(), lh);
        if (inList) {
            if (lh) pad.hover("§like" + post.id());
            String id = post.id();
            boolean liked = post.liked();
            hits.add(0, new Hit(cx, ly - 1, lw, 16, () -> {
                pad.sound("like", liked ? 0.5F : 0.9F);
                PadCommunityClient.act("like", id);
            }));
        }
        if (!inList) return;
        if (post.canDelete()) {
            boolean sure = post.id().equals(confirmId) && System.currentTimeMillis() - confirm < 3000;
            String label = sure ? "¿SEGURO?" : "BORRAR";
            int bw = PadUi.buttonWidth(label);
            button(g, x + w - 6 - bw, ly - 1, bw, label, PadView.RED, mx, my, () -> {
                if (!post.id().equals(confirmId) || System.currentTimeMillis() - confirm > 3000) {
                    confirm = System.currentTimeMillis();
                    confirmId = post.id();
                } else {
                    confirm = 0;
                    PadCommunityClient.act("borrar", post.id());
                }
            });
        } else if (!post.mine()) {
            int bw = PadUi.buttonWidth("DENUNCIAR");
            button(g, x + w - 6 - bw, ly - 1, bw, "DENUNCIAR", PadView.BLUE, mx, my, () -> PadCommunityClient.act("denunciar", post.id()));
        }
    }

    /** El botón del like: corazón + «N likes»; rosado con el corazón lleno si ya le diste, claro y vacío si no. */
    private void likeButton(GuiGraphics g, int x, int y, int w, String label, boolean liked, boolean hover) {
        int top, bot;
        if (liked) {
            top = hover ? 0xFFFFF2F8 : 0xFFFFEAF3;
            bot = hover ? 0xFFFFD0E4 : 0xFFFFC2DC;
        } else { // con el ratón encima se tiñe de rosa: se ve que es para dar like
            top = hover ? 0xFFFFF4F8 : 0xFFFFFFFF;
            bot = hover ? 0xFFFFD2E4 : 0xFFDCEAF8;
        }
        PadUi.box(g, x, y, w, 16, PadUi.INK);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + 15, top, bot);
        g.fill(x + 2, y + 1, x + w - 2, y + 2, 0xFFFFFFFF);
        pad.blit(g, liked ? "heart" : "heart_off", x + 5, y + 3);
        PadUi.text(g, label, x + 20, y + 4, liked ? 0xFFC8327A : PadUi.TEXT);
    }

    /**
     * Dónde va cada etiqueta de reacción: placed = {x, fila, ancho, índice en post.reactions() (o -1 - N: la de «+N»)};
     * rows = cuántas filas. Las filas de arriba van de xFull a end; la última, a la derecha de REACCIONAR (de xLast).
     */
    private record Chips(List<int[]> placed, int rows) {}

    private Chips chips(PadCommunityNet.Post post, int xFull, int xLast, int end, int maxRows) {
        List<PadCommunityNet.Reaction> rs = post.reactions();
        int known = 0;
        for (PadCommunityNet.Reaction r : rs) if (PadEmojis.get(r.emoji()) != null) known++;
        List<int[]> best = new ArrayList<>();
        int rows = 1;
        // las menos filas en que caben todas (como mucho maxRows)
        for (; rows <= maxRows; rows++) {
            best = new ArrayList<>();
            int row = 0, rx = rows == 1 ? xLast : xFull;
            for (int k = 0; k < rs.size(); k++) {
                if (PadEmojis.get(rs.get(k).emoji()) == null) continue;
                int bw = chipWidth(String.valueOf(rs.get(k).count()));
                if (rx + bw > end) {
                    if (row + 1 >= rows) break;
                    row++;
                    rx = row == rows - 1 ? xLast : xFull;
                    if (rx + bw > end) break;
                }
                best.add(new int[] {rx, row, bw, k});
                rx += bw + 3;
            }
            if (best.size() == known || rows == maxRows) break;
        }
        rows = Math.min(rows, maxRows);
        if (best.size() < known) {
            // «+N» al final de la última fila (quitando etiquetas hasta que quepa)
            int last = rows - 1;
            while (true) {
                int more = known - best.size();
                int mw = PadUi.font().width("+" + more) + 8;
                int[] prev = best.isEmpty() ? null : best.get(best.size() - 1);
                int sx = prev != null && prev[1] == last ? prev[0] + prev[2] + 3 : xLast;
                if (sx + mw <= end || prev == null) {
                    best.add(new int[] {sx, last, mw, -1 - more});
                    break;
                }
                best.remove(best.size() - 1);
            }
        }
        return new Chips(best, rows);
    }

    private static int chipWidth(String count) {
        return CHIP_EMOJI + 6 + PadUi.font().width(count);
    }

    /** Una etiqueta de reacción: el emoji y cuántos (la tuya, en oro); clic = sumarte o quitarte. «+N»: abre el selector. */
    private void drawChip(GuiGraphics g, PadCommunityNet.Post post, int[] c, int cy, double mx, double my, boolean inList) {
        int ex = c[0], bw = c[2];
        boolean hov = inList && PadUi.inside(mx, my, ex, cy, bw, CHIP_H);
        if (c[3] < 0) {
            PadUi.box(g, ex, cy, bw, CHIP_H, hov ? 0xFF6A9AD0 : PadUi.SLOT_EDGE);
            g.fill(ex + 1, cy + 1, ex + bw - 1, cy + CHIP_H - 1, hov ? 0xFFE4F0FC : 0xFFF2F8FE);
            PadUi.text(g, "+" + (-1 - c[3]), ex + 4, cy + 3, PadUi.MUTED);
            if (inList) {
                if (hov) pad.hover("§mas" + post.id());
                hits.add(0, new Hit(ex, cy, bw, CHIP_H, () -> openPicker(post)));
            }
            return;
        }
        PadCommunityNet.Reaction r = post.reactions().get(c[3]);
        PadEmojis.Emoji e = PadEmojis.get(r.emoji());
        boolean mine = r.mine();
        PadUi.box(g, ex, cy, bw, CHIP_H, mine ? 0xFFE0A030 : hov ? 0xFF6A9AD0 : PadUi.SLOT_EDGE);
        g.fill(ex + 1, cy + 1, ex + bw - 1, cy + CHIP_H - 1, mine ? (hov ? 0xFFFFEBB0 : 0xFFFFF3C8) : hov ? 0xFFE4F0FC : 0xFFF2F8FE);
        PadEmojiDraw.draw(g, e, ex + 2, cy + 2, CHIP_EMOJI, PadEmojiDraw.px(pad));
        PadUi.text(g, String.valueOf(r.count()), ex + CHIP_EMOJI + 4, cy + 3, mine ? PadUi.GOLD_TEXT : PadUi.TEXT);
        if (!inList) return;
        if (hov) {
            pad.hover("§chip" + post.id() + e.id());
            chipHover = e;
        }
        String id = post.id(), action = "react:" + e.id();
        hits.add(0, new Hit(ex, cy, bw, CHIP_H, () -> {
            pad.sound("like", mine ? 0.5F : 0.9F);
            PadCommunityClient.act(action, id);
        }));
    }

    private PadCommunityNet.Post find(String id) {
        for (PadCommunityNet.Post p : PadCommunityClient.POSTS) if (p.id().equals(id)) return p;
        return null;
    }

    private static Set<String> mineOf(PadCommunityNet.Post post) {
        Set<String> s = new HashSet<>();
        for (PadCommunityNet.Reaction r : post.reactions()) if (r.mine()) s.add(r.emoji());
        return s;
    }

    /** REACCIONAR: abre el selector para esa foto (o lo cierra si ya era suyo). */
    private void openPicker(PadCommunityNet.Post post) {
        pad.sound("tab", 0.6F);
        if (post.id().equals(pickerFor)) {
            pickerFor = null;
            return;
        }
        pickerFor = post.id();
        picker.open(mineOf(post));
    }

    /** Se eligió un emoji en el selector: se pone (o se quita, si ya era tuyo) y se cierra. */
    private void picked(PadEmojis.Emoji e) {
        PadCommunityNet.Post post = pickerFor == null ? null : find(pickerFor);
        if (post == null) return;
        boolean had = false, exists = false;
        for (PadCommunityNet.Reaction r : post.reactions()) {
            if (!r.emoji().equals(e.id())) continue;
            exists = true;
            had = r.mine();
        }
        if (!exists && post.reactions().size() >= PadCommunityNet.MAX_REACTIONS) {
            pad.showNotice("Una foto puede tener " + PadCommunityNet.MAX_REACTIONS + " emojis distintos como mucho.");
            return;
        }
        pad.sound("like", had ? 0.5F : 0.9F);
        PadCommunityClient.act("react:" + e.id(), post.id());
        pickerFor = null;
    }

    /** El selector, encima de todo lo de la página (con la lista oscurecida debajo). */
    private void drawPicker(GuiGraphics g, PadCommunityNet.Post post, double mx, double my) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 150);
        g.fill(X, Y - 4, X + W, Y + H, 0x5518265C);
        picker.mine(mineOf(post));
        picker.place(X, Y, W, H, pickerX + PadUi.buttonWidth("REACCIONAR") / 2);
        picker.render(g, mx, my);
        g.pose().popPose();
    }

    /** La foto en grande, encima de todo; un clic la cierra. */
    private void drawFull(GuiGraphics g, PadCommunityNet.Post post) {
        hits.clear();
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        g.fill(OX, OY, OX + SW, OY + SH, 0xE618265C);
        int h = SH - 16, w = h * 16 / 9;
        if (w > SW - 16) {
            w = SW - 16;
            h = w * 9 / 16;
        }
        int x = OX + (SW - w) / 2, y = OY + (SH - h) / 2;
        PadUi.box(g, x - 2, y - 2, w + 4, h + 4, PadUi.INK);
        PadUi.box(g, x - 1, y - 1, w + 2, h + 2, PadUi.GOLD);
        ResourceLocation tex = PadCommunityClient.texture(post.id());
        if (tex != null) {
            int[] sz = PadCommunityClient.textureSize(post.id());
            g.blit(tex, x, y, w, h, 0, 0, sz[0], sz[1], sz[0], sz[1]);
        }
        g.pose().popPose();
        hit(OX, OY, SW, SH, () -> full = null);
    }

    private static String ago(long time) {
        long s = Math.max(0, (System.currentTimeMillis() - time) / 1000);
        if (s < 60) return "ahora";
        if (s < 3600) return "hace " + (s / 60) + " min";
        if (s < 86400) return "hace " + (s / 3600) + " h";
        long d = s / 86400;
        return d == 1 ? "ayer" : "hace " + d + " días";
    }

    private void button(GuiGraphics g, int x, int y, int w, String label, int style, double mx, double my, Runnable action) {
        boolean hover = PadUi.inside(mx, my, x, y, w, 16);
        if (hover) pad.hover("§" + label + x + "," + y);
        PadUi.button(g, x, y, w, label, style, hover, true);
        // los botones van antes que la foto y la tarjeta en los clics
        hits.add(0, new Hit(x, y, w, 16, action));
    }

    private void hit(int x, int y, int w, int h, Runnable action) {
        hits.add(new Hit(x, y, w, h, action));
    }

    @Override
    boolean click(double mx, double my, int button) {
        pressY = my;
        dragging = false;
        if (button != 0) return false;
        // con el selector abierto, todo va a él; un clic fuera lo cierra
        if (full == null && pickerFor != null) {
            if (!picker.inside(mx, my)) pickerFor = null;
            else picker.click(mx, my);
            return true;
        }
        for (Hit h : hits) {
            if (!PadUi.inside(mx, my, h.x(), h.y(), h.w(), h.h())) continue;
            h.action().run();
            return true;
        }
        return false;
    }

    @Override
    boolean scroll(double mx, double my, double delta) {
        if (full != null) return true;
        if (pickerFor != null) return picker.scroll(delta);
        scroll -= (int) Math.signum(delta) * 30;
        return true;
    }

    @Override
    boolean drag(double mx, double my, double dy) {
        if (full != null) return false;
        if (!dragging) {
            // un temblor al hacer clic no es arrastrar
            if (Math.abs(my - pressY) < DRAG_START) return true;
            dragging = true;
        }
        if (pickerFor != null) picker.drag(dy);
        else scroll -= (int) Math.round(dy);
        return true;
    }

    @Override
    void release(double mx, double my, int button) {
        picker.release();
    }

    /** Con el selector abierto se escribe en su buscador (la tecla del pad no lo cierra). */
    @Override
    boolean typing() {
        return pickerFor != null;
    }

    @Override
    boolean chr(char c) {
        return pickerFor != null && full == null && picker.chr(c);
    }

    @Override
    boolean key(int key, int scan, int mods) {
        if (full != null && (key == 256 || key == 259)) {
            full = null;
            return true;
        }
        if (pickerFor != null) { // Esc cierra el selector; lo demás, a su buscador
            if (key == 256) pickerFor = null;
            else picker.key(key);
            return true;
        }
        if (key == 264) {
            scroll += 30;
            return true;
        }
        if (key == 265) {
            scroll -= 30;
            return true;
        }
        return false;
    }
}
