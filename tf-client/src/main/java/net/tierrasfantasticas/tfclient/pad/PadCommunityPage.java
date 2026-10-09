package net.tierrasfantasticas.tfclient.pad;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Comunidad: la red social del servidor. Pestañas RECIENTES / POPULARES / MÍAS y, debajo, las publicaciones una
 * debajo de otra (cada una en su tarjeta: la foto, quién la subió, cuándo, el texto, REACCIONAR con las reacciones que
 * ya tiene y el corazón de likes); se baja con la rueda o arrastrando. Clic en una foto para verla en grande. BORRAR
 * (las tuyas, o el staff) y DENUNCIAR (las de otros). «+ FOTO» lleva a la Cámara.
 */
final class PadCommunityPage extends PadPage {
    private static final String[][] TABS = {{"recientes", "RECIENTES"}, {"populares", "POPULARES"}, {"mias", "MÍAS"}};

    private int scroll;
    private PadCommunityNet.Post full;
    private long confirm;
    private String confirmId = "";
    private final List<int[]> hits = new ArrayList<>();
    private final List<Runnable> actions = new ArrayList<>();
    /** El selector de emojis abierto (id de la foto) y dónde está su botón REACCIONAR. */
    private static final int PICK_CELL = 20, PICK_EMOJI = 14;
    private String pickerFor;
    private boolean pickerSeen;
    private int pickerX, pickerY;
    private int[] pickerBox;

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
        actions.clear();
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
        // reacciones: el botón REACCIONAR (abre el selector con los emojis) y al lado solo las que ya tiene la foto, con
        // cuántas lleva (la tuya, en oro). Sin reacciones, la tarjeta queda limpia.
        int ey = py + ph - 32;
        int ex = cx;
        if (!post.mine()) {
            String label = "REACCIONAR";
            int bw = PadUi.buttonWidth(label);
            boolean open = post.id().equals(pickerFor);
            if (open) {
                pickerSeen = true;
                pickerX = ex;
                pickerY = ey - 1;
            }
            if (inList) {
                boolean hov = PadUi.inside(mx, my, ex, ey - 1, bw, 16);
                if (hov) pad.hover("§reaccionar" + post.id());
                PadUi.button(g, ex, ey - 1, bw, label, PadView.BLUE, hov || open, true);
                String id = post.id();
                hits.add(0, new int[] {ex, ey - 1, bw, 16});
                actions.add(0, () -> {
                    pickerFor = id.equals(pickerFor) ? null : id;
                    pad.sound("tab", 0.6F);
                });
            } else {
                PadUi.button(g, ex, ey - 1, bw, label, PadView.BLUE, false, true);
            }
            ex += bw + 4;
        }
        for (int k = 0; k < PadCommunityNet.REACTIONS.size(); k++) {
            int n = k < post.reactions().length ? post.reactions()[k] : 0;
            if (n <= 0) continue;
            String count = String.valueOf(n);
            int bw = 15 + PadUi.font().width(count) + 2;
            if (ex + bw > cx + cw) break;
            if (post.myReaction() == k) {
                PadUi.box(g, ex, ey, bw, 15, 0xFFE0A030);
                g.fill(ex + 1, ey + 1, ex + bw - 1, ey + 14, 0xFFFFF3C8);
            }
            pad.blitFit(g, "emo_" + PadCommunityNet.REACTIONS.get(k), ex + 2, ey + 2, 11, 11);
            PadUi.text(g, count, ex + 15, ey + 4, PadUi.TEXT);
            ex += bw + 2;
        }
        // likes y botón, abajo
        int ly = py + ph - 15;
        boolean canLike = !post.mine() && inList;
        boolean hh = canLike && PadUi.inside(mx, my, cx - 1, ly - 1, 70, 16);
        g.pose().pushPose();
        g.pose().translate(cx, ly, 0);
        pad.blit(g, post.liked() || post.mine() ? "heart" : "heart_off", 0, 0);
        g.pose().popPose();
        PadUi.text(g, post.likes() + (post.likes() == 1 ? " like" : " likes"), cx + 16, ly + 2, hh ? 0xFFFF8CC8 : PadUi.TEXT);
        if (canLike) {
            if (hh) pad.hover("§like" + post.id());
            hit(cx - 1, ly - 1, 70, 16, () -> {
                pad.sound("like", post.liked() ? 0.5F : 0.9F);
                PadCommunityClient.act("like", post.id());
            });
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

    /**
     * El selector de REACCIONAR: una burbuja encima del botón (o debajo, si no cabe) con los emojis; clic en uno para
     * reaccionar con él (el que ya pusiste, en oro: otra vez lo quita). Un clic fuera la cierra.
     */
    private void drawPicker(GuiGraphics g, PadCommunityNet.Post post, double mx, double my, int top, int bottom) {
        int n = PadCommunityNet.REACTIONS.size();
        int w = n * PICK_CELL + 6, h = PICK_CELL + 6;
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
            int s = hov ? PICK_EMOJI + 2 : PICK_EMOJI, o = (PICK_CELL - s) / 2;
            pad.blitFit(g, "emo_" + name, ex + o, ey + o, s, s);
            if (hov) pad.hover("§pick" + name);
            String id = post.id(), action = "react:" + name;
            hits.add(0, new int[] {ex, ey, PICK_CELL, PICK_CELL});
            actions.add(0, () -> {
                pad.sound("like", mine ? 0.5F : 0.9F);
                PadCommunityClient.act(action, id);
                pickerFor = null;
            });
        }
        g.pose().popPose();
    }

    /** La foto en grande, encima de todo; un clic la cierra. */
    private void drawFull(GuiGraphics g, PadCommunityNet.Post post) {
        hits.clear();
        actions.clear();
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
        hits.add(0, new int[] {x, y, w, 16});
        actions.add(0, action);
    }

    private void hit(int x, int y, int w, int h, Runnable action) {
        hits.add(new int[] {x, y, w, h});
        actions.add(action);
    }

    @Override
    boolean click(double mx, double my, int button) {
        if (button != 0) return false;
        // con el selector de emojis abierto, un clic fuera solo lo cierra
        if (pickerFor != null && pickerBox != null
                && !PadUi.inside(mx, my, pickerBox[0], pickerBox[1], pickerBox[2], pickerBox[3])) {
            pickerFor = null;
            return true;
        }
        for (int i = 0; i < hits.size(); i++) {
            int[] h = hits.get(i);
            if (PadUi.inside(mx, my, h[0], h[1], h[2], h[3])) {
                actions.get(i).run();
                return true;
            }
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
        scroll -= (int) Math.round(dy);
        pickerFor = null;
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
