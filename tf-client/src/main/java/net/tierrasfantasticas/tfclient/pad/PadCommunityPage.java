package net.tierrasfantasticas.tfclient.pad;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Comunidad: la red social del servidor. Pestañas RECIENTES / POPULARES / MÍAS y, debajo, las publicaciones una
 * debajo de otra (cada una en su tarjeta: la foto, quién la subió, cuándo, el texto, REACCIONAR con las reacciones que
 * ya tiene y el botón del corazón para dar o quitar like, también en las tuyas); se baja con la rueda o arrastrando. Clic en una foto para verla en grande. BORRAR
 * (las tuyas, o el staff) y DENUNCIAR (las de otros). «+ FOTO» lleva a la Cámara.
 */
final class PadCommunityPage extends PadPage {
    private static final String[][] TABS = {{"recientes", "RECIENTES"}, {"populares", "POPULARES"}, {"mias", "MÍAS"}};

    private int scroll;
    private PadCommunityNet.Post full;
    private long confirm;
    private String confirmId = "";
    /** Zonas de clic de lo dibujado en el último fotograma (la primera que contiene el ratón se lleva el clic). */
    private final List<Hit> hits = new ArrayList<>();
    /** opener: un botón REACCIONAR (con el selector abierto, un clic en otro lo cambia a esa foto en vez de solo cerrar). */
    private record Hit(int x, int y, int w, int h, boolean opener, Runnable action) {}
    /** El selector de emojis abierto (id de la foto) y dónde está su botón REACCIONAR. */
    private static final int PICK_CELL = 16, PICK_EMOJI = 12;
    /** Emojis de las reacciones de cada tarjeta y alto de sus etiquetas. */
    private static final int CHIP_EMOJI = 9, CHIP_H = 13;
    private String pickerFor;
    private boolean pickerSeen;
    private int pickerX, pickerY;
    private int[] pickerBox;
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
        hits.clear();
        pickerBox = null;
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
        if (pickerFor != null) {
            PadCommunityNet.Post open = null;
            for (PadCommunityNet.Post p : posts) if (p.id().equals(pickerFor)) open = p;
            if (open == null || !pickerSeen || pickerY < top + 2 || pickerY + 16 > bottom - 2) pickerFor = null;
            else drawPicker(g, open, mx, my, top, bottom);
        }
        PadUi.scrollbar(g, X + W - 7, top + 5, visible - 2, visible, contentH, scroll);
        // cerca del final, la página siguiente
        if (scroll + visible > contentH - ch * 2) PadCommunityClient.more();
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
        if (!post.caption().isEmpty()) {
            int maxLines = Math.max(1, (ph - 60) / 10);
            PadUi.wrapEllipsis(g, "«" + post.caption() + "»", cx, py + 26, cw, PadUi.TEXT, maxLines);
        }
        // reacciones: el botón REACCIONAR (abre el selector con los emojis; también en tus fotos) y al lado, en etiquetas
        // iguales, solo las que ya tiene la foto con cuántas lleva (la tuya, en oro). Sin reacciones, solo el botón.
        int ey = py + ph - 32;
        int ex = cx;
        String rlabel = "REACCIONAR";
        int rbw = PadUi.buttonWidth(rlabel);
        boolean open = post.id().equals(pickerFor);
        if (open) {
            pickerSeen = true;
            pickerX = ex;
            pickerY = ey - 1;
        }
        boolean rhov = inList && PadUi.inside(mx, my, ex, ey - 1, rbw, 16);
        if (rhov) pad.hover("§reaccionar" + post.id());
        PadUi.button(g, ex, ey - 1, rbw, rlabel, PadView.BLUE, rhov || open, true);
        if (inList) {
            String id = post.id();
            hits.add(0, new Hit(ex, ey - 1, rbw, 16, true, () -> {
                pickerFor = id.equals(pickerFor) ? null : id;
                pad.sound("tab", 0.6F);
            }));
        }
        ex += rbw + 4;
        int chipY = ey + 1;
        for (int k = 0; k < PadCommunityNet.REACTIONS.size(); k++) {
            int n = k < post.reactions().length ? post.reactions()[k] : 0;
            if (n <= 0) continue;
            String count = String.valueOf(n);
            int bw = CHIP_EMOJI + 6 + PadUi.font().width(count);
            if (ex + bw > cx + cw) break;
            boolean mine = post.myReaction() == k;
            PadUi.box(g, ex, chipY, bw, CHIP_H, mine ? 0xFFE0A030 : PadUi.SLOT_EDGE);
            g.fill(ex + 1, chipY + 1, ex + bw - 1, chipY + CHIP_H - 1, mine ? 0xFFFFF3C8 : 0xFFF2F8FE);
            pad.blitSharp(g, "emo_" + PadCommunityNet.REACTIONS.get(k), ex + 2, chipY + 2, CHIP_EMOJI);
            PadUi.text(g, count, ex + CHIP_EMOJI + 4, chipY + 3, mine ? PadUi.GOLD_TEXT : PadUi.TEXT);
            ex += bw + 3;
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
            hits.add(0, new Hit(cx, ly - 1, lw, 16, false, () -> {
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
     * El selector de REACCIONAR: una burbuja encima del botón (o debajo, si no cabe) con los emojis; clic en uno para
     * reaccionar con él (el que ya pusiste, en oro: otra vez lo quita). Un clic fuera la cierra.
     */
    private void drawPicker(GuiGraphics g, PadCommunityNet.Post post, double mx, double my, int top, int bottom) {
        int n = PadCommunityNet.REACTIONS.size();
        int w = n * PICK_CELL + 6, h = PICK_CELL + 6;
        // encima del botón REACCIONAR (debajo si no cabe), sin salirse de la ventana
        int x = Math.max(X + 4, Math.min(pickerX, X + W - w - 4));
        int y = pickerY - h - 2;
        if (y < top + 2) y = pickerY + 18;
        pickerBox = new int[] {x, y, w, h};
        g.pose().pushPose();
        g.pose().translate(0, 0, 150);
        g.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x5518265C); // sombra
        PadUi.box(g, x, y, w, h, PadUi.INK);
        PadUi.box(g, x + 1, y + 1, w - 2, h - 2, PadUi.GOLD_LO);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFFFFFFFF);
        for (int k = 0; k < n; k++) {
            String name = PadCommunityNet.REACTIONS.get(k);
            int ex = x + 3 + k * PICK_CELL, ey = y + 3;
            boolean mine = post.myReaction() == k;
            boolean hov = PadUi.inside(mx, my, ex, ey, PICK_CELL, PICK_CELL);
            if (mine || hov) {
                PadUi.box(g, ex, ey, PICK_CELL, PICK_CELL, mine ? 0xFFE0A030 : 0xFF9DBCE0);
                g.fill(ex + 1, ey + 1, ex + PICK_CELL - 1, ey + PICK_CELL - 1, mine ? 0xFFFFF3C8 : 0xFFEAF4FD);
            }
            // mismo tamaño siempre (nítido): con el ratón encima, el emoji sube una unidad
            int o = (PICK_CELL - PICK_EMOJI) / 2;
            pad.blitSharp(g, "emo_" + name, ex + o, ey + o - (hov ? 1 : 0), PICK_EMOJI);
            if (hov) pad.hover("§pick" + name);
            String id = post.id(), action = "react:" + name;
            hits.add(0, new Hit(ex, ey, PICK_CELL, PICK_CELL, false, () -> {
                pad.sound("like", mine ? 0.5F : 0.9F);
                PadCommunityClient.act(action, id);
                pickerFor = null;
            }));
        }
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
        hits.add(0, new Hit(x, y, w, 16, false, action));
    }

    private void hit(int x, int y, int w, int h, Runnable action) {
        hits.add(new Hit(x, y, w, h, false, action));
    }

    @Override
    boolean click(double mx, double my, int button) {
        pressY = my;
        dragging = false;
        if (button != 0) return false;
        // con el selector de emojis abierto, un clic fuera lo cierra (y si es en otro REACCIONAR, abre ese)
        boolean picker = full == null && pickerFor != null && pickerBox != null;
        boolean outside = picker && !PadUi.inside(mx, my, pickerBox[0], pickerBox[1], pickerBox[2], pickerBox[3]);
        for (Hit h : hits) {
            if (!PadUi.inside(mx, my, h.x(), h.y(), h.w(), h.h())) continue;
            if (outside && !h.opener()) break;
            // dentro de la burbuja solo cuentan sus emojis (no lo que queda debajo)
            if (picker && !outside && !PadUi.inside(h.x(), h.y(), pickerBox[0], pickerBox[1], pickerBox[2], pickerBox[3])) return true;
            h.action().run();
            return true;
        }
        if (outside) {
            pickerFor = null;
            return true;
        }
        return false;
    }

    @Override
    boolean scroll(double mx, double my, double delta) {
        if (full == null) scroll -= (int) Math.signum(delta) * 30;
        pickerFor = null;
        return true;
    }

    @Override
    boolean drag(double mx, double my, double dy) {
        if (full != null) return false;
        if (!dragging) {
            // un temblor al hacer clic no es arrastrar (ni cierra el selector)
            if (Math.abs(my - pressY) < DRAG_START) return true;
            dragging = true;
            pickerFor = null;
        }
        scroll -= (int) Math.round(dy);
        return true;
    }

    @Override
    boolean key(int key, int scan, int mods) {
        if (full != null && (key == 256 || key == 259)) {
            full = null;
            return true;
        }
        if (pickerFor != null && key == 256) { // Esc cierra el selector de emojis
            pickerFor = null;
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
