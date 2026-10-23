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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Comunidad: la red social del servidor. Pestañas RECIENTES / POPULARES / MÍAS y, debajo, las publicaciones una
 * debajo de otra (cada una en su tarjeta: la foto, quién la subió, cuándo, el texto con sus emojis, las reacciones que
 * ya tiene y, abajo del todo, la barra con el corazón del like, Reaccionar y Comentarios); se baja con la rueda o
 * arrastrando. Clic en una foto para verla en grande. BORRAR (las tuyas, o el staff) y DENUNCIAR (las de otros), arriba
 * a la derecha. «+ FOTO» lleva a la Cámara.
 * <p>Comentarios (1.3.120, el dueño: «que el comentario salga con nombre y foto de la skin; reaccionar a los
 * comentarios; que no joda la vista de la foto: un botón como el de reaccionar que abra su ventana propia»): el botón
 * Comentarios abre una ventana encima de la lista con la foto en pequeño, los comentarios (la cara de la skin, el
 * nombre, cuándo, el texto con emojis y sus reacciones) y abajo el campo para escribir. Cada comentario tiene su botón
 * de reaccionar (el mismo selector de emojis) y, si es tuyo, de tu foto o eres staff, la ✕ para borrarlo.
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
    /** La ventana de comentarios: lo que se está escribiendo, su lista y si debe bajar al final al llegar la respuesta. */
    private String draft = "";
    private int commentScroll;
    private boolean stickBottom;
    private long stickSince;
    /** El selector de emojis abierto para un comentario (su id) y el centro de su botón. */
    private String pickerComment;
    private int pickerCommentX;
    /** Zonas de clic de la ventana de comentarios (la lista de debajo no recibe nada mientras está abierta). */
    private final List<Hit> winHits = new ArrayList<>();
    private int winX, winY, winW, winH;
    /** Lo alto de una fila de comentario sin texto ni reacciones y el hueco entre comentarios. */
    private static final int COMMENT_GAP = 3;
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
        // con el selector o los comentarios abiertos, lo de debajo no se ilumina
        if ((pickerFor != null || commentsOpen()) && full == null) {
            mx = -1000;
            my = -1000;
        }
        // la ventana con sus pestañas encima (las mismas que en todas las apps: PadSkin.tabRow) y + FOTO a la derecha
        int top = Y + PadSkin.TABS, bottom = Y + H;
        PadUi.panel(g, X, top, W, bottom - top);
        int fw = PadUi.buttonWidth("+ FOTO");
        java.util.List<String> labels = new java.util.ArrayList<>();
        int sel = -1;
        for (int i = 0; i < TABS.length; i++) {
            labels.add(TABS[i][1]);
            if (TABS[i][0].equals(PadCommunityClient.tab)) sel = i;
        }
        int[][] at = PadSkin.tabRow(g, pad, X + 4, Y, W - 8 - fw - 8, labels, sel, mx, my, PadSkin.accent("comunidad"));
        for (int i = 0; i < TABS.length; i++) {
            String key = TABS[i][0];
            hit(at[i][0], Y, at[i][1], PadSkin.TAB_H, () -> {
                if (!key.equals(PadCommunityClient.tab)) {
                    scroll = 0;
                    pad.sound("tab", 0.7F);
                    PadCommunityClient.open(key);
                }
            });
        }
        boolean fh = PadUi.inside(mx, my, X + W - fw - 4, Y, fw, 16);
        if (fh) pad.hover("§foto");
        PadUi.button(g, X + W - fw - 4, Y, fw, "+ FOTO", PadView.GOLD, fh, true);
        hit(X + W - fw - 4, Y, fw, 16, () -> pad.openApp("camara"));

        List<PadCommunityNet.Post> posts = PadCommunityClient.POSTS;
        if (posts.isEmpty()) {
            if (commentsOpen()) closeComments();
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
        if (pickerFor == null && !commentsOpen()) PadUi.grabbable(X + W - 7, top + 5, visible - 2, visible, contentH, () -> scroll, v -> scroll = v);
        // cerca del final, la página siguiente
        if (scroll + visible > contentH - ch * 2) PadCommunityClient.more();
        if (pickerFor != null) {
            PadCommunityNet.Post open = find(pickerFor);
            if (open == null || !pickerSeen) pickerFor = null;
            else drawPicker(g, open, pmx, pmy);
        }
        if (commentsOpen()) drawComments(g, full == null ? pmx : -1000, full == null ? pmy : -1000);
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
        // columna derecha: arriba quién y cuándo (y BORRAR o DENUNCIAR a la derecha), el texto, las reacciones que ya
        // tiene y, abajo del todo (a ras del pie de la foto), la barra: like, Reaccionar y Comentarios
        int cx = px + pw + 10, cw = x + w - 6 - cx;
        int tbw = 0;
        // (siempre se ven; solo se pulsan con el ratón dentro de la lista)
        if (post.canDelete()) {
            boolean sure = post.id().equals(confirmId) && System.currentTimeMillis() - confirm < 3000;
            String label = sure ? "¿SEGURO?" : "BORRAR";
            tbw = PadUi.buttonWidth(label);
            button(g, x + w - 6 - tbw, py - 1, tbw, label, PadView.RED, mx, my, inList, () -> {
                if (!post.id().equals(confirmId) || System.currentTimeMillis() - confirm > 3000) {
                    confirm = System.currentTimeMillis();
                    confirmId = post.id();
                } else {
                    confirm = 0;
                    PadCommunityClient.act("borrar", post.id());
                }
            });
        } else if (!post.mine()) {
            tbw = PadUi.buttonWidth("DENUNCIAR");
            button(g, x + w - 6 - tbw, py - 1, tbw, "DENUNCIAR", PadView.BLUE, mx, my, inList, () -> PadCommunityClient.act("denunciar", post.id()));
        }
        int headW = Math.max(24, cw - (tbw > 0 ? tbw + 4 : 0));
        PadUi.text(g, PadUi.fitEnd(post.name(), headW), cx, py + 1, PadUi.GOLD_TEXT);
        PadUi.text(g, PadUi.fitEnd(ago(post.time()), headW), cx, py + 12, PadUi.MUTED);
        int by = py + ph - 16;
        bottomBar(g, post, cx, by, cw, mx, my, inList);
        // las reacciones, en filas justo encima de la barra (las tuyas, en oro; clic = sumarte o quitarte); lo que no
        // quepa sale como «+N»
        int capTop = py + 26;
        boolean caption = !post.caption().isEmpty();
        int chipsTop = by;
        if (!post.reactions().isEmpty()) {
            int maxRows = Math.max(1, (by - 2 - capTop - (caption ? 10 : 0)) / CHIP_ROW);
            Chips chips = chips(post, cx, cx, cx + cw, maxRows);
            chipsTop = by - chips.rows() * CHIP_ROW;
            for (int[] c : chips.placed()) drawChip(g, post, c, by - (chips.rows() - c[1]) * CHIP_ROW, mx, my, inList);
        }
        if (caption) {
            int maxLines = Math.max(1, (chipsTop - 2 - capTop) / 10);
            PadEmojiDraw.wrap(g, "«" + post.caption() + "»", cx, capTop, cw, PadUi.TEXT, maxLines, PadEmojiDraw.px(pad));
        }
    }

    /** Ancho de un botón de la barra de abajo: el icono y su texto (o solo el icono si el texto va vacío). */
    private static int barWidth(String label) {
        return label.isEmpty() ? 21 : 20 + PadUi.font().width(label) + 6;
    }

    /**
     * La barra de abajo de una tarjeta: el corazón con los likes (clic = dar o quitar el like, también en tus fotos),
     * Reaccionar (abre el selector de emojis) y Comentarios (abre su ventana). Si no caben con su texto entero, primero se
     * acortan los números y al final Reaccionar se queda en su icono.
     */
    private void bottomBar(GuiGraphics g, PadCommunityNet.Post post, int x, int y, int w, double mx, double my, boolean inList) {
        int n = post.comments();
        String likesFull = post.likes() + (post.likes() == 1 ? " like" : " likes"), likesShort = String.valueOf(post.likes());
        String comFull = n == 0 ? "Comentar" : n + (n == 1 ? " comentario" : " comentarios"), comShort = n == 0 ? "" : String.valueOf(n);
        String[][] options = {{likesFull, "Reaccionar", comFull}, {likesShort, "Reaccionar", comFull}, {likesShort, "Reaccionar", comShort},
                {likesShort, "", comShort}};
        String[] use = options[options.length - 1];
        for (String[] o : options) {
            if (barWidth(o[0]) + barWidth(o[1]) + barWidth(o[2]) + 6 <= w) {
                use = o;
                break;
            }
        }
        int bx = x;
        // like
        int lw = barWidth(use[0]);
        boolean lh = inList && PadUi.inside(mx, my, bx, y, lw, 16);
        likeButton(g, bx, y, lw, use[0], post.liked(), lh);
        String id = post.id();
        if (inList) {
            if (lh) pad.hover("§like" + id);
            boolean liked = post.liked();
            hits.add(0, new Hit(bx, y, lw, 16, () -> {
                pad.sound("like", liked ? 0.5F : 0.9F);
                PadCommunityClient.act("like", id);
            }));
        }
        bx += lw + 3;
        // reaccionar
        int rw = barWidth(use[1]);
        boolean open = id.equals(pickerFor);
        if (open) {
            pickerSeen = true;
            pickerX = bx + rw / 2;
        }
        boolean rh = inList && PadUi.inside(mx, my, bx, y, rw, 16);
        barButton(g, bx, y, rw, "react", use[1], rh || open);
        if (inList) {
            if (rh) pad.hover("§reaccionar" + id);
            hits.add(0, new Hit(bx, y, rw, 16, () -> openPicker(post)));
        }
        bx += rw + 3;
        // comentarios
        int cw = barWidth(use[2]);
        boolean ch = inList && PadUi.inside(mx, my, bx, y, cw, 16);
        barButton(g, bx, y, cw, "comment", use[2], ch || id.equals(PadCommunityClient.commentsPost));
        if (inList) {
            if (ch) pad.hover("§comentarios" + id);
            hits.add(0, new Hit(bx, y, cw, 16, () -> openComments(post)));
        }
    }

    /** Un botón de la barra de abajo (como el del like): claro, con su icono y su texto; azul con el ratón encima. */
    private void barButton(GuiGraphics g, int x, int y, int w, String icon, String label, boolean hover) {
        PadUi.box(g, x, y, w, 16, PadUi.INK);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + 15, hover ? 0xFFF2F9FF : 0xFFFFFFFF, hover ? 0xFFC8E2FF : 0xFFDCEAF8);
        g.fill(x + 2, y + 1, x + w - 2, y + 2, 0xFFFFFFFF);
        pad.blit(g, icon, x + 5, y + 3);
        if (!label.isEmpty()) PadUi.text(g, label, x + 20, y + 4, hover ? 0xFF1E5AA8 : PadUi.TEXT);
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
        chipBox(g, ex, cy, bw, e, r.count(), mine, hov);
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

    /** Una etiqueta de reacción: el emoji y cuántos (la tuya, en oro). */
    private void chipBox(GuiGraphics g, int ex, int cy, int bw, PadEmojis.Emoji e, int count, boolean mine, boolean hov) {
        PadUi.box(g, ex, cy, bw, CHIP_H, mine ? 0xFFE0A030 : hov ? 0xFF6A9AD0 : PadUi.SLOT_EDGE);
        g.fill(ex + 1, cy + 1, ex + bw - 1, cy + CHIP_H - 1, mine ? (hov ? 0xFFFFEBB0 : 0xFFFFF3C8) : hov ? 0xFFE4F0FC : 0xFFF2F8FE);
        PadEmojiDraw.draw(g, e, ex + 2, cy + 2, CHIP_EMOJI, PadEmojiDraw.px(pad));
        PadUi.text(g, String.valueOf(count), ex + CHIP_EMOJI + 4, cy + 3, mine ? PadUi.GOLD_TEXT : PadUi.TEXT);
    }

    private PadCommunityNet.Post find(String id) {
        for (PadCommunityNet.Post p : PadCommunityClient.POSTS) if (p.id().equals(id)) return p;
        return null;
    }

    private static Set<String> mineOf(PadCommunityNet.Post post) {
        return mineOf(post.reactions());
    }

    private static Set<String> mineOf(List<PadCommunityNet.Reaction> rs) {
        Set<String> s = new HashSet<>();
        for (PadCommunityNet.Reaction r : rs) if (r.mine()) s.add(r.emoji());
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
        if (pickerComment != null) {
            PadCommunityNet.Comment c = findComment(pickerComment);
            pickerComment = null;
            if (c == null) return;
            boolean had = false, exists = false;
            for (PadCommunityNet.Reaction r : c.reactions()) {
                if (!r.emoji().equals(e.id())) continue;
                exists = true;
                had = r.mine();
            }
            if (!exists && c.reactions().size() >= PadCommunityNet.COMMENT_REACTIONS) {
                pad.showNotice("Un comentario puede tener " + PadCommunityNet.COMMENT_REACTIONS + " emojis distintos como mucho.");
                return;
            }
            pad.sound("like", had ? 0.5F : 0.9F);
            PadCommunityClient.commentAct("react:" + e.id(), c.id());
            return;
        }
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
        g.fill(X, Y, X + W, Y + H, 0x5518265C);
        picker.mine(mineOf(post));
        picker.place(X, Y, W, H, pickerX);
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

    // ---------------------------------------------------------------------------------------------------------------
    // Comentarios: su ventana, encima de la lista
    // ---------------------------------------------------------------------------------------------------------------

    private boolean commentsOpen() {
        return PadCommunityClient.commentsPost != null;
    }

    /** Comentarios: abre su ventana (o la cierra si ya era la de esa foto). */
    private void openComments(PadCommunityNet.Post post) {
        pad.sound("tab", 0.6F);
        pickerFor = null;
        if (post.id().equals(PadCommunityClient.commentsPost)) {
            closeComments();
            return;
        }
        commentScroll = 0;
        draft = "";
        pickerComment = null;
        stickBottom = true;
        stickSince = System.currentTimeMillis();
        PadCommunityClient.openComments(post.id());
    }

    private void closeComments() {
        PadCommunityClient.closeComments();
        pickerComment = null;
        draft = "";
    }

    private PadCommunityNet.Comment findComment(String id) {
        for (PadCommunityNet.Comment c : PadCommunityClient.COMMENTS) if (c.id().equals(id)) return c;
        return null;
    }

    private void sendComment() {
        if (draft.isBlank()) return;
        pad.sound("like", 0.9F);
        PadCommunityClient.comment(draft);
        draft = "";
        stickBottom = true;
        stickSince = System.currentTimeMillis();
    }

    /** Dónde va cada reacción de un comentario: {x, fila, ancho, índice}, de izquierda a derecha y en filas de ancho w. */
    private static List<int[]> flow(List<PadCommunityNet.Reaction> rs, int x0, int w) {
        List<int[]> out = new ArrayList<>();
        int rx = x0, row = 0;
        for (int k = 0; k < rs.size(); k++) {
            if (PadEmojis.get(rs.get(k).emoji()) == null) continue;
            int bw = chipWidth(String.valueOf(rs.get(k).count()));
            if (rx > x0 && rx + bw > x0 + w) {
                row++;
                rx = x0;
            }
            out.add(new int[] {rx, row, bw, k});
            rx += bw + 3;
        }
        return out;
    }

    /** Lo alto de un comentario: la cabecera (nombre y cuándo), su texto (hasta 8 líneas) y sus reacciones. */
    private int commentHeight(PadCommunityNet.Comment c, int tw) {
        int lines = Math.max(1, PadEmojiDraw.wrap(null, c.text(), 0, 0, tw, 0, 8, PadEmojiDraw.px(pad)));
        List<int[]> chips = flow(c.reactions(), 0, tw);
        int rows = chips.isEmpty() ? 0 : chips.get(chips.size() - 1)[1] + 1;
        return Math.max(26, 17 + lines * 10 + (rows > 0 ? 1 + rows * CHIP_ROW : 0) + 3);
    }

    /**
     * La ventana: arriba la foto en pequeño (clic = verla en grande), COMENTARIOS (N), de quién es y CERRAR; en medio
     * los comentarios (del más viejo al más nuevo, se baja con la rueda o arrastrando); abajo el campo y ENVIAR.
     */
    private void drawComments(GuiGraphics g, double mx, double my) {
        winHits.clear();
        PadCommunityNet.Post post = find(PadCommunityClient.commentsPost);
        if (post == null) {
            closeComments();
            return;
        }
        boolean picking = pickerComment != null;
        double hx = picking ? -1000 : mx, hy = picking ? -1000 : my;
        g.pose().pushPose();
        g.pose().translate(0, 0, 150);
        g.fill(X, Y, X + W, Y + H, 0x8018265C);
        winW = Math.min(W - 10, 340);
        winH = H - 4;
        winX = X + (W - winW) / 2;
        winY = Y + 2;
        PadUi.panel(g, winX, winY, winW, winH);
        // cabecera
        int thW = 48, thH = 27, thX = winX + 8, thY = winY + 7;
        PadUi.box(g, thX - 1, thY - 1, thW + 2, thH + 2, PadUi.GOLD_LO);
        g.fill(thX, thY, thX + thW, thY + thH, 0xFF1A2440);
        ResourceLocation tex = PadCommunityClient.texture(post.id());
        if (tex != null) {
            int[] sz = PadCommunityClient.textureSize(post.id());
            g.blit(tex, thX, thY, thW, thH, 0, 0, sz[0], sz[1], sz[0], sz[1]);
        }
        if (PadUi.inside(hx, hy, thX, thY, thW, thH)) pad.hover("§miniatura" + post.id());
        winHits.add(new Hit(thX, thY, thW, thH, () -> full = post));
        String close = "CERRAR";
        int cbw = PadUi.buttonWidth(close);
        winButton(g, winX + winW - 7 - cbw, winY + 7, cbw, close, PadView.RED, hx, hy, this::closeComments);
        int total = PadCommunityClient.commentsTotal;
        int hx0 = thX + thW + 8, hw = winX + winW - 7 - cbw - 6 - hx0;
        PadUi.title(g, "COMENTARIOS" + (total > 0 ? " (" + total + ")" : ""), hx0, winY + 9, hw, PadSkin.darken(PadSkin.accent("comunidad"), 0.7F));
        PadUi.text(g, PadUi.fitEnd("Foto de " + post.name() + " · " + ago(post.time()), hw), hx0, winY + 22, PadUi.MUTED);
        g.fill(winX + 6, winY + 39, winX + winW - 6, winY + 40, 0x40285AA0);
        // la lista
        int listTop = winY + 43, footY = winY + winH - 23, listH = footY - 3 - listTop;
        int lx = winX + 6, lw = winW - 12 - 7, tw = lw - 26 - 6;
        List<PadCommunityNet.Comment> list = PadCommunityClient.COMMENTS;
        int[] hs = new int[list.size()];
        int content = 0;
        for (int i = 0; i < list.size(); i++) {
            hs[i] = commentHeight(list.get(i), tw);
            content += hs[i] + COMMENT_GAP;
        }
        if (stickBottom && PadCommunityClient.commentsAt >= stickSince && !PadCommunityClient.commentsLoading) {
            commentScroll = Integer.MAX_VALUE;
            stickBottom = false;
        }
        commentScroll = Math.max(0, Math.min(commentScroll, Math.max(0, content - listH)));
        if (list.isEmpty()) {
            if (PadCommunityClient.commentsLoading) {
                PadUi.spinner(g, winX + winW / 2, listTop + listH / 2 - 4);
            } else {
                String msg = "Aún no hay comentarios. ¡Escribe el primero!";
                int n = PadUi.lines(msg, lw - 20);
                PadUi.wrap(g, msg, lx + 10, listTop + listH / 2 - n * 5, lw - 20, PadUi.MUTED, 3);
            }
        } else {
            pad.scissor(g, lx - 1, listTop, lw + 2, listH);
            int ry = listTop - commentScroll;
            boolean inList = hy >= listTop && hy < listTop + listH;
            for (int i = 0; i < list.size(); i++) {
                if (ry + hs[i] > listTop && ry < listTop + listH) drawComment(g, list.get(i), lx, ry, lw, hs[i], tw, hx, hy, inList);
                ry += hs[i] + COMMENT_GAP;
            }
            pad.noScissor(g);
            PadUi.scrollbar(g, winX + winW - 11, listTop, listH, listH, content, commentScroll);
            if (!picking) PadUi.grabbable(winX + winW - 11, listTop, listH, listH, content, () -> commentScroll, v -> commentScroll = v);
        }
        // abajo: el campo y ENVIAR
        String send = "ENVIAR";
        int sw = PadUi.buttonWidth(send);
        int fw = winW - 14 - sw - 4;
        PadUi.field(g, winX + 7, footY, fw, draft, "Escribe un comentario...");
        if (draft.length() > PadCommunityNet.COMMENT_MAX - 30) {
            String left = String.valueOf(PadCommunityNet.COMMENT_MAX - draft.length());
            PadUi.text(g, left, winX + 7 + fw - 4 - PadUi.font().width(left), footY + 4, 0xFFC8327A);
        }
        winButton(g, winX + winW - 7 - sw, footY, sw, send, PadView.GREEN, hx, hy, this::sendComment);
        // el selector de emojis de un comentario, encima
        if (picking) {
            PadCommunityNet.Comment c = findComment(pickerComment);
            if (c == null) {
                pickerComment = null;
            } else {
                g.pose().translate(0, 0, 30);
                g.fill(winX, winY, winX + winW, winY + winH, 0x4018265C);
                picker.mine(mineOf(c.reactions()));
                picker.place(X, Y, W, H, pickerCommentX);
                picker.render(g, mx, my);
            }
        }
        g.pose().popPose();
    }

    /**
     * Un comentario: la cara de su skin (con la capa de encima), el nombre y cuándo, a la derecha su botón de reaccionar
     * (y la ✕ si lo puedes borrar), el texto con sus emojis y debajo sus reacciones (clic = sumarte o quitarte).
     */
    private void drawComment(GuiGraphics g, PadCommunityNet.Comment c, int x, int y, int w, int h, int tw, double mx, double my, boolean inList) {
        PadUi.card(g, x, y, w, h, c.mine() ? 2 : 0);
        int fx = x + 5, fy = y + 4;
        PadUi.box(g, fx - 1, fy - 1, 18, 18, PadUi.INK);
        net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g, PadCommunityClient.face(c.author(), c.skin()), fx, fy, 16);
        int tx = x + 26;
        int bx = x + w - 5;
        if (c.canDelete()) {
            bx -= 13;
            boolean armed = ("c:" + c.id()).equals(confirmId) && System.currentTimeMillis() - confirm < 3000;
            boolean dh = inList && PadUi.inside(mx, my, bx, y + 3, 13, 13);
            cross(g, bx, y + 3, armed, dh);
            if (inList) {
                if (dh) pad.hover("§borrarcom" + c.id());
                winHits.add(0, new Hit(bx, y + 3, 13, 13, () -> deleteComment(c)));
            }
            bx -= 3;
        }
        bx -= 17;
        boolean open = c.id().equals(pickerComment);
        boolean rh = inList && PadUi.inside(mx, my, bx, y + 3, 17, 13);
        PadUi.box(g, bx, y + 3, 17, 13, rh || open ? 0xFF6A9AD0 : PadUi.SLOT_EDGE);
        g.fill(bx + 1, y + 4, bx + 16, y + 15, rh || open ? 0xFFE4F0FC : 0xFFF2F8FE);
        pad.blit(g, "react", bx + 3, y + 4);
        if (inList) {
            if (rh) pad.hover("§reaccom" + c.id());
            int ax = bx + 8;
            winHits.add(0, new Hit(bx, y + 3, 17, 13, () -> openCommentPicker(c, ax)));
        }
        // nombre y cuándo
        String when = ago(c.time());
        int ww = PadUi.font().width(when);
        String name = PadUi.fitEnd(c.name(), Math.max(20, bx - 4 - tx - ww - 6));
        PadUi.text(g, name, tx, y + 5, PadUi.GOLD_TEXT);
        if (tx + PadUi.font().width(name) + 6 + ww <= bx - 4) PadUi.text(g, when, tx + PadUi.font().width(name) + 6, y + 5, PadUi.MUTED);
        // el texto
        int lines = Math.max(1, PadEmojiDraw.wrap(g, c.text(), tx, y + 17, tw, PadUi.TEXT, 8, PadEmojiDraw.px(pad)));
        // sus reacciones
        int cy0 = y + 17 + lines * 10 + 1;
        for (int[] ch : flow(c.reactions(), tx, tw)) {
            PadCommunityNet.Reaction r = c.reactions().get(ch[3]);
            PadEmojis.Emoji e = PadEmojis.get(r.emoji());
            int cy = cy0 + ch[1] * CHIP_ROW;
            boolean hov = inList && PadUi.inside(mx, my, ch[0], cy, ch[2], CHIP_H);
            chipBox(g, ch[0], cy, ch[2], e, r.count(), r.mine(), hov);
            if (!inList) continue;
            if (hov) {
                pad.hover("§chipcom" + c.id() + e.id());
                chipHover = e;
            }
            String id = c.id(), action = "react:" + e.id();
            boolean mine = r.mine();
            winHits.add(0, new Hit(ch[0], cy, ch[2], CHIP_H, () -> {
                pad.sound("like", mine ? 0.5F : 0.9F);
                PadCommunityClient.commentAct(action, id);
            }));
        }
    }

    /** La ✕ de borrar un comentario: roja (con la ✕ blanca) mientras espera el segundo clic que lo confirma. */
    private static void cross(GuiGraphics g, int x, int y, boolean armed, boolean hover) {
        PadUi.box(g, x, y, 13, 13, armed ? 0xFF9A1E2E : hover ? 0xFFD05A6A : PadUi.SLOT_EDGE);
        g.fill(x + 1, y + 1, x + 12, y + 12, armed ? 0xFFE0485A : hover ? 0xFFFFE6EA : 0xFFF2F8FE);
        int c = armed ? 0xFFFFFFFF : hover ? 0xFFB02838 : 0xFF6A7FA8;
        for (int i = 0; i < 5; i++) {
            g.fill(x + 4 + i, y + 4 + i, x + 5 + i, y + 5 + i, c);
            g.fill(x + 8 - i, y + 4 + i, x + 9 - i, y + 5 + i, c);
        }
    }

    /** La ✕: el primer clic pide confirmación (3 s), el segundo lo borra. */
    private void deleteComment(PadCommunityNet.Comment c) {
        String key = "c:" + c.id();
        if (!key.equals(confirmId) || System.currentTimeMillis() - confirm > 3000) {
            confirm = System.currentTimeMillis();
            confirmId = key;
            pad.sound("tab", 0.5F);
            pad.showNotice("Pulsa otra vez la X para borrar el comentario.");
            return;
        }
        confirm = 0;
        confirmId = "";
        pad.sound("tab", 0.5F);
        PadCommunityClient.commentAct("borrar", c.id());
    }

    /** El botón de reaccionar de un comentario: abre el selector para él (o lo cierra si ya era suyo). */
    private void openCommentPicker(PadCommunityNet.Comment c, int anchorX) {
        pad.sound("tab", 0.6F);
        if (c.id().equals(pickerComment)) {
            pickerComment = null;
            return;
        }
        pickerComment = c.id();
        pickerCommentX = anchorX;
        picker.open(mineOf(c.reactions()));
    }

    /**
     * (La prueba en el juego) «comentarios»: abre los de la primera foto de la lista; «emojis-comentario»: el selector
     * de emojis del primer comentario.
     */
    void actForTest(String action) {
        if (action.equals("comentarios") && !PadCommunityClient.POSTS.isEmpty() && !commentsOpen()) {
            openComments(PadCommunityClient.POSTS.get(0));
        } else if (action.equals("emojis-comentario") && commentsOpen() && !PadCommunityClient.COMMENTS.isEmpty()) {
            openCommentPicker(PadCommunityClient.COMMENTS.get(0), winX + winW / 2);
        }
    }

    private void winButton(GuiGraphics g, int x, int y, int w, String label, int style, double mx, double my, Runnable action) {
        boolean hover = PadUi.inside(mx, my, x, y, w, 16);
        if (hover) pad.hover("§win" + label + x + "," + y);
        PadUi.button(g, x, y, w, label, style, hover, true);
        winHits.add(0, new Hit(x, y, w, 16, action));
    }

    private static String ago(long time) {
        long s = Math.max(0, (System.currentTimeMillis() - time) / 1000);
        if (s < 60) return "ahora";
        if (s < 3600) return "hace " + (s / 60) + " min";
        if (s < 86400) return "hace " + (s / 3600) + " h";
        long d = s / 86400;
        return d == 1 ? "ayer" : "hace " + d + " días";
    }

    private void button(GuiGraphics g, int x, int y, int w, String label, int style, double mx, double my, boolean active, Runnable action) {
        boolean hover = active && PadUi.inside(mx, my, x, y, w, 16);
        if (hover) pad.hover("§" + label + x + "," + y);
        PadUi.button(g, x, y, w, label, style, hover, true);
        // los botones van antes que la foto y la tarjeta en los clics
        if (active) hits.add(0, new Hit(x, y, w, 16, action));
    }

    private void hit(int x, int y, int w, int h, Runnable action) {
        hits.add(new Hit(x, y, w, h, action));
    }

    @Override
    boolean click(double mx, double my, int button) {
        pressY = my;
        dragging = false;
        if (button != 0) return false;
        // con los comentarios abiertos, todo va a su ventana (y a su selector de emojis); un clic fuera la cierra
        if (full == null && commentsOpen()) {
            if (pickerComment != null) {
                if (!picker.inside(mx, my)) pickerComment = null;
                else picker.click(mx, my);
                return true;
            }
            if (!PadUi.inside(mx, my, winX, winY, winW, winH)) {
                pad.sound("tab", 0.5F);
                closeComments();
                return true;
            }
            for (Hit h : new ArrayList<>(winHits)) {
                if (!PadUi.inside(mx, my, h.x(), h.y(), h.w(), h.h())) continue;
                h.action().run();
                return true;
            }
            return true;
        }
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
        if (commentsOpen()) {
            if (pickerComment != null) return picker.scroll(delta);
            commentScroll -= (int) Math.signum(delta) * 24;
            return true;
        }
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
        if (commentsOpen()) {
            if (pickerComment != null) picker.drag(dy);
            else commentScroll += (int) Math.round(dy);
        } else if (pickerFor != null) {
            picker.drag(dy);
        } else {
            scroll += (int) Math.round(dy); // arrastrar hacia abajo baja la lista (como la barra)
        }
        return true;
    }

    @Override
    void release(double mx, double my, int button) {
        picker.release();
    }

    /**
     * Con el selector abierto se escribe en su buscador; con los comentarios, en su campo (la tecla del pad no lo
     * cierra).
     */
    @Override
    boolean typing() {
        return pickerFor != null || commentsOpen();
    }

    @Override
    boolean chr(char c) {
        if (full != null) return false;
        if (commentsOpen()) {
            if (pickerComment != null) return picker.chr(c);
            if (c < ' ' || c == '§' || draft.length() >= PadCommunityNet.COMMENT_MAX) return true;
            draft += c;
            return true;
        }
        return pickerFor != null && picker.chr(c);
    }

    @Override
    void closed() {
        closeComments();
    }

    @Override
    boolean key(int key, int scan, int mods) {
        if (full != null && (key == 256 || key == 259)) {
            full = null;
            return true;
        }
        if (commentsOpen()) {
            if (pickerComment != null) { // Esc cierra el selector; lo demás, a su buscador
                if (key == 256) pickerComment = null;
                else picker.key(key);
                return true;
            }
            switch (key) {
                case 256 -> closeComments(); // Esc
                case 259 -> { // borrar la última letra
                    if (!draft.isEmpty()) draft = draft.substring(0, draft.offsetByCodePoints(draft.length(), -1));
                }
                case 257, 335 -> sendComment(); // Intro
                case 264 -> commentScroll += 24;
                case 265 -> commentScroll -= 24;
                default -> {
                    // Ctrl+V: pegar
                    if (key == 86 && (mods & 2) != 0) {
                        String clip = net.minecraft.client.Minecraft.getInstance().keyboardHandler.getClipboard().replaceAll("[\\p{Cntrl}§]", "");
                        draft = (draft + clip).length() > PadCommunityNet.COMMENT_MAX ? (draft + clip).substring(0, PadCommunityNet.COMMENT_MAX) : draft + clip;
                        return true;
                    }
                    return false;
                }
            }
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
