package net.tierrasfantasticas.tfclient.pad;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Comunidad: la red social del servidor. Pestañas RECIENTES / POPULARES / MÍAS, una foto grande con su autor, cuándo
 * se publicó, el texto y el corazón de likes; flechas (o la rueda) para pasar; clic en la foto para verla en grande.
 * BORRAR (las tuyas, o el staff) y DENUNCIAR (las de otros). «+ FOTO» lleva a la Cámara.
 */
final class PadCommunityPage extends PadPage {
    private static final String[][] TABS = {{"recientes", "RECIENTES"}, {"populares", "POPULARES"}, {"mias", "MÍAS"}};
    private static final int PW = 160, PH = 90;

    private int index;
    private boolean full;
    private long confirm;
    private String confirmId = "";
    private final List<int[]> hits = new ArrayList<>();
    private final List<Runnable> actions = new ArrayList<>();

    PadCommunityPage(TFPadScreen pad) {
        super(pad, "comunidad");
        PadCommunityClient.open(PadCommunityClient.tab);
    }

    @Override
    String title() {
        return "COMUNIDAD";
    }

    void updated() {
        index = Math.max(0, Math.min(index, PadCommunityClient.POSTS.size() - 1));
    }

    private PadCommunityNet.Post current() {
        List<PadCommunityNet.Post> posts = PadCommunityClient.POSTS;
        return posts.isEmpty() ? null : posts.get(Math.max(0, Math.min(index, posts.size() - 1)));
    }

    @Override
    void render(GuiGraphics g, double mx, double my, float partial) {
        hits.clear();
        actions.clear();
        // pestañas y + FOTO
        int x = X;
        for (String[] t : TABS) {
            int w = PadFont.width(t[1]) + 10;
            boolean sel = t[0].equals(PadCommunityClient.tab);
            boolean hover = PadUi.inside(mx, my, x, Y, w, 13);
            PadUi.box(g, x, Y, w, 13, PadUi.NAVY);
            PadUi.box(g, x + 1, Y + 1, w - 2, 11, sel ? 0xFFF6B628 : hover ? 0xFF96D6FF : 0xFFE8F8FF);
            PadFont.drawCentered(g, t[1], x + w / 2, Y + 1, sel ? 0xFFFFFF : 0x18265C, sel);
            String key = t[0];
            hit(x, Y, w, 13, () -> {
                if (!key.equals(PadCommunityClient.tab)) {
                    index = 0;
                    full = false;
                    PadCommunityClient.open(key);
                }
            });
            x += w + 3;
        }
        int fw = PadUi.buttonWidth("+ FOTO");
        boolean fh = PadUi.inside(mx, my, X + W - fw, Y - 1, fw, 15);
        PadUi.button(g, X + W - fw, Y - 1, fw, "+ FOTO", PadView.GOLD, fh, true);
        hit(X + W - fw, Y - 1, fw, 15, () -> pad.openApp("camara"));

        int top = Y + 16;
        PadUi.panel(g, X, top, W, H - 16);
        PadCommunityNet.Post post = current();
        if (post == null) {
            String msg = PadCommunityClient.loading ? "Cargando..." : PadCommunityClient.tab.equals("mias")
                    ? "Aún no has publicado fotos. Hazte una con la Cámara y publícala." : "Aún no hay fotos. ¡Publica la primera desde la Cámara!";
            PadUi.wrap(g, msg, X + 20, top + 38, W - 40, PadUi.MUTED, 3);
            return;
        }
        int px = X + 6, py = top + 5;
        PadUi.box(g, px - 1, py - 1, PW + 2, PH + 2, PadUi.NAVY);
        g.fill(px, py, px + PW, py + PH, 0xFF1A2440);
        ResourceLocation tex = PadCommunityClient.texture(post.id());
        if (tex != null) {
            int[] sz = PadCommunityClient.textureSize(post.id());
            g.blit(tex, px, py, PW, PH, 0, 0, sz[0], sz[1], sz[0], sz[1]);
        } else {
            PadFont.drawCentered(g, "...", px + PW / 2, py + PH / 2 - 5, 0xE0ECFF, false);
        }
        hit(px, py, PW, PH, () -> full = true);
        int count = PadCommunityClient.total;
        if (count > 1) {
            arrow(g, px + 2, py + PH / 2 - 7, true, mx, my, () -> move(-1));
            arrow(g, px + PW - 12, py + PH / 2 - 7, false, mx, my, () -> move(1));
        }
        // columna derecha: autor, hace cuánto, texto, likes y botones
        int cx = X + 172, cw = W - 178;
        PadUi.text(g, post.name(), cx, top + 6, PadUi.TEXT);
        String when = ago(post.time()), num = (index + 1) + "/" + Math.max(count, 1);
        PadUi.text(g, when, cx, top + 16, PadUi.MUTED);
        int nw = PadUi.font().width(num);
        if (PadUi.font().width(when) + 6 + nw <= cw) PadUi.text(g, num, cx + cw - nw, top + 16, PadUi.MUTED);
        if (!post.caption().isEmpty()) PadUi.wrap(g, "«" + post.caption() + "»", cx, top + 29, cw, PadUi.TEXT, 3);
        int ly = top + 62;
        boolean hh = !post.mine() && PadUi.inside(mx, my, cx - 1, ly - 1, 40, 14);
        g.pose().pushPose();
        g.pose().translate(cx, ly, 0);
        g.pose().scale(2, 2, 1);
        pad.blit(g, post.liked() || post.mine() ? "heart" : "heart_off", 0, 0);
        g.pose().popPose();
        PadUi.text(g, post.likes() + (post.likes() == 1 ? " like" : " likes"), cx + 20, ly + 3, hh ? 0xFFF63C96 : PadUi.TEXT);
        if (!post.mine()) {
            if (hh) pad.hover("§like");
            hit(cx - 1, ly - 1, 60, 14, () -> {
                pad.sound("like", post.liked() ? 0.5F : 0.9F);
                PadCommunityClient.act("like", post.id());
            });
        }
        int by = top + H - 16 - 19;
        if (post.canDelete()) {
            boolean sure = post.id().equals(confirmId) && System.currentTimeMillis() - confirm < 3000;
            button(g, cx, by, cw, sure ? "¿SEGURO?" : "BORRAR", PadView.RED, mx, my, () -> {
                if (!post.id().equals(confirmId) || System.currentTimeMillis() - confirm > 3000) {
                    confirm = System.currentTimeMillis();
                    confirmId = post.id();
                } else {
                    confirm = 0;
                    PadCommunityClient.act("borrar", post.id());
                }
            });
        } else if (!post.mine()) {
            button(g, cx, by, cw, "DENUNCIAR", PadView.BLUE, mx, my, () -> PadCommunityClient.act("denunciar", post.id()));
        }
        if (index >= PadCommunityClient.POSTS.size() - 2) PadCommunityClient.more();
        if (full) drawFull(g, post);
    }

    /** La foto en grande, encima de todo; un clic la cierra. */
    private void drawFull(GuiGraphics g, PadCommunityNet.Post post) {
        hits.clear();
        actions.clear();
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        g.fill(X - 3, Y - 2, X + W + 3, Y + H + 1, 0xE618265C);
        int w = 196, h = 110, x = X + (W - w) / 2, y = Y;
        PadUi.box(g, x - 1, y - 1, w + 2, h + 2, 0xFFF6B628);
        ResourceLocation tex = PadCommunityClient.texture(post.id());
        if (tex != null) {
            int[] sz = PadCommunityClient.textureSize(post.id());
            g.blit(tex, x, y, w, h, 0, 0, sz[0], sz[1], sz[0], sz[1]);
        }
        g.pose().popPose();
        hit(X - 3, Y - 2, W + 6, H + 3, () -> full = false);
    }

    private static String ago(long time) {
        long s = Math.max(0, (System.currentTimeMillis() - time) / 1000);
        if (s < 60) return "ahora mismo";
        if (s < 3600) return "hace " + (s / 60) + " min";
        if (s < 86400) return "hace " + (s / 3600) + " h";
        long d = s / 86400;
        return d == 1 ? "ayer" : "hace " + d + " días";
    }

    private void arrow(GuiGraphics g, int x, int y, boolean left, double mx, double my, Runnable action) {
        boolean hover = PadUi.inside(mx, my, x, y, 10, 14);
        PadUi.box(g, x, y, 10, 14, PadUi.NAVY);
        PadUi.box(g, x + 1, y + 1, 8, 12, hover ? 0xFFF6B628 : 0xCC3496FA);
        int c = 0xFFFFFFFF;
        if (left) {
            g.fill(x + 3, y + 6, x + 4, y + 8, c);
            g.fill(x + 4, y + 5, x + 5, y + 9, c);
            g.fill(x + 5, y + 4, x + 6, y + 10, c);
        } else {
            g.fill(x + 6, y + 6, x + 7, y + 8, c);
            g.fill(x + 5, y + 5, x + 6, y + 9, c);
            g.fill(x + 4, y + 4, x + 5, y + 10, c);
        }
        if (hover) pad.hover("§arrow" + left);
        // las flechas van antes que la foto en los clics
        hits.add(0, new int[] {x, y, 10, 14});
        actions.add(0, action);
    }

    private void button(GuiGraphics g, int x, int y, int w, String label, int style, double mx, double my, Runnable action) {
        boolean hover = PadUi.inside(mx, my, x, y, w, 15);
        if (hover) pad.hover("§" + label);
        PadUi.button(g, x, y, w, label, style, hover, true);
        hit(x, y, w, 15, action);
    }

    private void hit(int x, int y, int w, int h, Runnable action) {
        hits.add(new int[] {x, y, w, h});
        actions.add(action);
    }

    private void move(int d) {
        int n = PadCommunityClient.POSTS.size();
        if (n == 0) return;
        index = Math.max(0, Math.min(n - 1, index + d));
        pad.sound("hover", 0.6F);
    }

    @Override
    boolean click(double mx, double my, int button) {
        if (button != 0) return false;
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
        if (!full) move(delta < 0 ? 1 : -1);
        return true;
    }

    @Override
    boolean key(int key, int scan, int mods) {
        if (full && (key == 256 || key == 259)) {
            full = false;
            return true;
        }
        if (key == 262) {
            move(1);
            return true;
        }
        if (key == 263) {
            move(-1);
            return true;
        }
        return false;
    }
}
