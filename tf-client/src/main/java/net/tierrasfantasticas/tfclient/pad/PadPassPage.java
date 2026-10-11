package net.tierrasfantasticas.tfclient.pad;

import com.google.gson.JsonObject;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;

/**
 * El TF Pass (1.3.38), el pase de temporada gratis:
 * <ul>
 *   <li>Arriba, la cabecera: el escudo con tu nivel, «TF PASS · Temporada 1: El Despertar», la barra de XP hasta el
 *       siguiente nivel, la cuenta atrás de la temporada, tus monedas verdes y RECLAMAR TODO si tienes niveles por
 *       reclamar.</li>
 *   <li>En medio, la pista de niveles (se desplaza con la rueda, arrastrando o con las flechas; se abre en tu nivel):
 *       cada nivel con su número sobre el raíl (que se llena de oro hasta donde llegas), su premio y su estado: verde y
 *       con RECLAMAR si está listo, marca de oro si ya es tuyo, candado si aún no llegas. Los niveles de 5 en 5 son
 *       hitos (marco de oro). Tu nivel lleva «TÚ».</li>
 *   <li>Abajo: el nivel que señales con el ratón (o el siguiente) con todos sus premios, y cómo se gana XP.</li>
 * </ul>
 */
final class PadPassPage extends PadDataPage {
    private static final int NODE_W = 46, GAP = 6, PITCH = NODE_W + GAP;
    private float scroll, scrollTarget;
    private boolean placed;
    private int focus = -1, hoverLevel = -1;
    private long flashLevel = -1, flashAt;

    PadPassPage(TFPadScreen pad) {
        super(pad, "pase");
    }

    @Override
    String title() {
        return "TF PASS";
    }

    @Override
    void received(JsonObject o) {
        JsonObject ev = o.has("evento") && o.get("evento").isJsonObject() ? o.getAsJsonObject("evento") : null;
        if (ev != null && ev.has("reclamado")) {
            flashLevel = num(ev, "reclamado", -1);
            flashAt = System.currentTimeMillis();
            pad.sound("like", 0.9F);
        } else if (ev != null && ev.has("reclamados")) {
            flashLevel = 0;
            flashAt = System.currentTimeMillis();
            pad.sound("like", 0.9F);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Geometría
    // ---------------------------------------------------------------------------------------------------------------

    private static final int HERO_H = 50;

    private int trackTop() {
        return Y + HERO_H + 4;
    }

    private int bottomH() {
        return Math.max(40, Math.min(52, H - HERO_H - 8 - 92));
    }

    private int trackH() {
        return H - HERO_H - 8 - bottomH();
    }

    /** La pista: x, ancho (sin las flechas). */
    private int trackX() {
        return X + 16;
    }

    private int trackW() {
        return W - 32;
    }

    private int levels() {
        return list(data, "niveles").size();
    }

    // ---------------------------------------------------------------------------------------------------------------

    @Override
    void render(GuiGraphics g, double mx, double my, float partial) {
        hits.clear();
        if (loading(g)) return;
        int lv = (int) num(data, "nivel", 0);
        int max = levels();
        float maxScroll = Math.max(0, max * PITCH - GAP - trackW());
        if (!placed) { // se abre con tu nivel (o el siguiente por reclamar) en el centro
            int first = firstReady();
            int at = first > 0 ? first : Math.min(max, lv + 1);
            scrollTarget = scroll = Math.max(0, Math.min(maxScroll, (at - 1) * PITCH + NODE_W / 2F - trackW() / 2F));
            placed = true;
        }
        scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
        scroll += (scrollTarget - scroll) * (PadSettings.animations ? 0.25F : 1F);
        if (Math.abs(scrollTarget - scroll) < 0.5F) scroll = scrollTarget;
        hoverLevel = -1;
        drawHero(g, mx, my, lv, max);
        drawTrack(g, mx, my, lv, max, maxScroll);
        drawBottom(g, mx, my, lv);
    }

    private int firstReady() {
        for (JsonObject l : list(data, "niveles")) if (num(l, "e", 0) == 1) return (int) num(l, "n", 0);
        return -1;
    }

    private int readyCount() {
        int n = 0;
        for (JsonObject l : list(data, "niveles")) if (num(l, "e", 0) == 1) n++;
        return n;
    }

    // ---- cabecera -------------------------------------------------------------------------------------------------

    private void drawHero(GuiGraphics g, double mx, double my, int lv, int max) {
        int x = X, y = Y, w = W, h = HERO_H;
        // banner esmeralda oscuro con su textura y filo de oro
        g.fill(x + 2, y + h, x + w - 2, y + h + 1, 0x5A286EC8);
        PadUi.box(g, x, y, w, h, PadUi.NAVY);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF1E6B52, 0xFF14284F);
        PadUi.texture(g, x + 1, y + 1, w - 2, h - 2);
        g.fill(x + 2, y + 1, x + w - 2, y + 2, 0xFF5FD39A);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, PadUi.GOLD_LO);
        PadUi.corners(g, x, y, w, h);
        // el escudo con el nivel
        int sx = x + 8, sy = y + 6, ss = 38;
        drawBadge(g, sx, sy, ss, lv);
        // título, temporada y barra de XP
        int tx = sx + ss + 9;
        boolean active = data.has("activo") && data.get("activo").getAsBoolean();
        PadFont.draw(g, "TF PASS", tx, y + 4, 0xFFD650, true);
        String season = "Temporada " + num(data, "temporada", 1) + (str(data, "nombre", "").isEmpty() ? "" : ": " + str(data, "nombre", ""));
        // a la derecha: monedas verdes, cuenta atrás y RECLAMAR TODO
        int right = x + w - 6;
        int ready = readyCount();
        if (ready > 0 && active) {
            String label = "RECLAMAR TODO (" + ready + ")";
            int bw = PadUi.buttonWidth(label);
            int bx = right - bw, by = y + h - 6 - 16;
            boolean hv = !waiting && PadUi.inside(mx, my, bx, by, bw, 16);
            if (hv) pad.hover("§todo");
            PadUi.button(g, bx, by, bw, label, PadView.GREEN, hv, !waiting);
            if (!waiting) hit(bx, by, bw, 16, () -> {
                send("todo");
                pad.sound("select", 0.75F);
            });
        }
        long left = num(data, "fin", 0) - num(data, "ahora", 0) - (System.currentTimeMillis() - dataAt);
        String clock = active ? "Termina en " + until(left) : "Sin temporada";
        int cw = PadUi.chipWidth(clock) + 12;
        PadUi.box(g, right - cw, y + 5, cw, 12, 0xFF0C1030);
        g.fillGradient(right - cw + 1, y + 6, right - 1, y + 16, 0xFF2A3470, 0xFF1A2050);
        drawClock(g, right - cw + 4, y + 7);
        PadUi.text(g, clock, right - cw + 14, y + 7, 0xFFFFE9A8);
        long greens = num(data, "verdes", 0);
        String gtxt = PadUi.THOUSANDS.format(greens);
        int gw = PadUi.font().width(gtxt) + 18;
        int gx = right - cw - 4 - gw;
        PadUi.box(g, gx, y + 5, gw, 12, 0xFF0C1030);
        g.fillGradient(gx + 1, y + 6, gx + gw - 1, y + 16, 0xFF1E6B52, 0xFF124436);
        pad.blit(g, "coin_green_s", gx + 3, y + 7);
        PadUi.text(g, gtxt, gx + 13, y + 7, 0xFF8CF0B4);
        int textRoom = gx - 6 - tx;
        PadUi.text(g, PadUi.fitEnd(season, textRoom), tx, y + 15, 0xFFFFFFFF);
        // barra de XP
        int barX = tx, barY = y + 28, barW = Math.max(60, (ready > 0 && active ? right - PadUi.buttonWidth("RECLAMAR TODO (" + ready + ")") - 8
                : right) - tx), barH = 8;
        long xpIn = num(data, "xpEn", 0), xpNeed = num(data, "xpNivel", 0);
        float f = xpNeed <= 0 ? 1F : Math.min(1F, xpIn / (float) xpNeed);
        PadUi.box(g, barX, barY, barW, barH, 0xFF0C1030);
        g.fill(barX + 1, barY + 1, barX + barW - 1, barY + barH - 1, 0xFF233066);
        int fw = Math.round((barW - 2) * f);
        if (fw > 0) {
            g.fillGradient(barX + 1, barY + 1, barX + 1 + fw, barY + barH - 1, 0xFF8CF0A8, 0xFF1E9E46);
            g.fill(barX + 1, barY + 1, barX + 1 + fw, barY + 2, 0xFFD8FFE2);
            if (PadSettings.animations && fw > 6) { // brillo que recorre la barra
                int sh = (int) ((System.currentTimeMillis() / 12) % (fw + 30)) - 15;
                for (int i = 0; i < 6; i++) {
                    int px = barX + 1 + sh + i;
                    if (px > barX && px < barX + fw) g.fill(px, barY + 1, px + 1, barY + barH - 1, 0x55FFFFFF);
                }
            }
        }
        String xpText = lv >= max ? "¡Nivel máximo!" : PadUi.THOUSANDS.format(xpIn) + " / " + PadUi.THOUSANDS.format(xpNeed) + " XP";
        String next = lv >= max ? "" : "nivel " + (lv + 1);
        PadUi.text(g, xpText, barX, barY + 10, 0xFFE0ECFF);
        if (!next.isEmpty()) PadUi.text(g, next, barX + barW - PadUi.font().width(next), barY + 10, 0xFFB8C2F0);
    }

    /** El escudo del nivel: hexágono de oro con el centro verde y el número grande. */
    private void drawBadge(GuiGraphics g, int x, int y, int s, int lv) {
        int cx = x + s / 2;
        for (int row = 0; row < s; row++) {
            int half = row < s / 4 ? s / 4 + row : row > s * 3 / 4 ? s / 4 + (s - 1 - row) : s / 2;
            half = Math.min(half, s / 2);
            g.fill(cx - half, y + row, cx + half, y + row + 1, 0xFF0C1030);
            if (half > 2 && row > 0 && row < s - 1) {
                int c = row < s / 2 ? PadUi.GOLD_HI : PadUi.GOLD;
                g.fill(cx - half + 1, y + row, cx + half - 1, y + row + 1, c);
                if (half > 5 && row > 2 && row < s - 3) {
                    int c2 = row < s / 2 ? 0xFF3FCB7C : 0xFF1E8A50;
                    g.fill(cx - half + 3, y + row, cx + half - 3, y + row + 1, c2);
                }
            }
        }
        PadFont.drawCentered(g, "NIVEL", cx, y + 3, 0xFFFFFF, true);
        String n = Integer.toString(lv);
        g.pose().pushPose();
        g.pose().translate(cx, y + 17, 0);
        g.pose().scale(2, 2, 1);
        g.drawString(PadUi.font(), n, -PadUi.font().width(n) / 2, 0, 0xFFFFFFFF, true);
        g.pose().popPose();
    }

    private static void drawClock(GuiGraphics g, int x, int y) {
        g.fill(x + 1, y, x + 6, y + 1, 0xFFFFE9A8);
        g.fill(x + 1, y + 7, x + 6, y + 8, 0xFFFFE9A8);
        g.fill(x + 2, y + 1, x + 5, y + 3, 0xFFF6B628);
        g.fill(x + 3, y + 3, x + 4, y + 5, 0xFFF6B628);
        g.fill(x + 2, y + 5, x + 5, y + 7, 0xFFC27A10);
    }

    // ---- pista de niveles -----------------------------------------------------------------------------------------

    private void drawTrack(GuiGraphics g, double mx, double my, int lv, int max, float maxScroll) {
        int top = trackTop(), h = trackH();
        int x = X, w = W;
        PadUi.panel(g, x, top, w, h);
        int tx = trackX(), tw = trackW();
        // flechas a los lados
        boolean canL = scrollTarget > 0, canR = scrollTarget < maxScroll;
        arrowButton(g, mx, my, x + 3, top + h / 2 - 9, canL, true, () -> scrollTarget -= PITCH * 4);
        arrowButton(g, mx, my, x + w - 13, top + h / 2 - 9, canR, false, () -> scrollTarget += PITCH * 4);
        List<JsonObject> lvls = list(data, "niveles");
        int railY = top + 12;
        pad.scissor(g, tx, top + 2, tw, h - 4);
        // el raíl: gris de punta a punta y de oro hasta tu nivel
        int x0 = Math.round(tx - scroll + NODE_W / 2F), x1 = Math.round(tx - scroll + (max - 1) * PITCH + NODE_W / 2F);
        g.fill(x0, railY - 2, x1, railY + 2, 0xFF9DB8D8);
        g.fill(x0, railY - 1, x1, railY + 1, 0xFFDCE8F4);
        if (lv > 0) {
            int xl = Math.round(tx - scroll + (lv - 1) * PITCH + NODE_W / 2F);
            g.fill(x0, railY - 2, xl, railY + 2, PadUi.GOLD_LO);
            g.fill(x0, railY - 1, xl, railY + 1, PadUi.GOLD);
            g.fill(x0, railY - 1, xl, railY, PadUi.GOLD_HI);
        }
        boolean inTrack = PadUi.inside(mx, my, tx, top + 2, tw, h - 4);
        for (JsonObject l : lvls) {
            int n = (int) num(l, "n", 0);
            int nx = Math.round(tx - scroll + (n - 1) * PITCH);
            if (nx + NODE_W < tx - 2 || nx > tx + tw + 2) continue;
            drawNode(g, l, n, nx, top, h, railY, mx, my, inTrack, lv);
        }
        pad.noScissor(g);
        // sombras en los bordes de la pista (se ve que sigue)
        for (int i = 0; i < 10; i++) {
            int a = (int) (110 * (1 - i / 10F)) << 24;
            if (scroll > 1) g.fill(tx + i, top + 2, tx + i + 1, top + h - 2, a | 0xFFFFFF);
            if (scroll < maxScroll - 1) g.fill(tx + tw - 1 - i, top + 2, tx + tw - i, top + h - 2, a | 0xFFFFFF);
        }
    }

    private void arrowButton(GuiGraphics g, double mx, double my, int x, int y, boolean on, boolean left, Runnable action) {
        boolean hv = on && PadUi.inside(mx, my, x, y, 10, 18);
        if (hv) pad.hover(left ? "§izq" : "§der");
        PadUi.box(g, x, y, 10, 18, on ? (hv ? 0xFF3496FA : 0xFF6A88B8) : 0xFFC6D6EA);
        PadUi.box(g, x + 1, y + 1, 8, 16, on ? (hv ? 0xFFDCEEFF : 0xFFF2F8FF) : 0xFFEEF3F9);
        arrow(g, x + 5, y + 9, left, on ? (hv ? 0xFF1E4E9C : 0xFF34507E) : 0xFFB4C6DC);
        if (on) hit(x, y, 10, 18, () -> {
            action.run();
            pad.sound("tab", 0.5F);
        });
    }

    private void drawNode(GuiGraphics g, JsonObject l, int n, int x, int top, int h, int railY, double mx, double my, boolean inTrack,
                          int lv) {
        int state = (int) num(l, "e", 0); // 0 bloqueado, 1 listo, 2 reclamado
        boolean milestone = n % 5 == 0;
        boolean you = n == lv;
        long now = System.currentTimeMillis();
        // el número sobre el raíl
        String num = Integer.toString(n);
        int pw = Math.max(16, PadFont.width(num) + 8);
        int px = x + (NODE_W - pw) / 2, py = railY - 6;
        int pillEdge = state > 0 ? PadUi.GOLD_LO : 0xFF6A88B8;
        PadUi.box(g, px, py, pw, 12, PadUi.NAVY);
        g.fillGradient(px + 1, py + 1, px + pw - 1, py + 11, state > 0 ? PadUi.GOLD_HI : 0xFFE2EFFB, state > 0 ? PadUi.GOLD : 0xFFB8CDE6);
        g.fill(px + 1, py + 10, px + pw - 1, py + 11, pillEdge);
        PadFont.drawCentered(g, num, x + NODE_W / 2, py + 1, state > 0 ? 0x5A3A00 : 0x34507E, false);
        // la tarjeta
        int cy = railY + 10, ch = top + h - 6 - cy;
        boolean hv = inTrack && PadUi.inside(mx, my, x, cy, NODE_W, ch);
        if (hv) {
            hoverLevel = n;
            pad.hover("§nivel" + n);
        }
        int edge = state == 1 ? 0xFF2EB85A : milestone ? PadUi.GOLD_LO : hv ? 0xFF3496FA : 0xFFBBD3EC;
        if (state == 1 && PadSettings.animations) { // listo: halo verde que late
            float p = 0.5F + 0.5F * (float) Math.sin(now / 220.0 + n);
            PadUi.box(g, x - 2, cy - 2, NODE_W + 4, ch + 4, ((int) (60 + 100 * p)) << 24 | 0x3DD66E);
        }
        if (you) PadUi.box(g, x - 2, cy - 2, NODE_W + 4, ch + 4, PadUi.GOLD);
        PadUi.box(g, x, cy, NODE_W, ch, edge);
        int topC = state == 2 ? 0xFFFFF6DA : state == 1 ? 0xFFEAFFF0 : milestone ? 0xFFFFF7E0 : 0xFFFFFFFF;
        int botC = state == 2 ? 0xFFF5DEA0 : state == 1 ? 0xFFBDEFCB : milestone ? 0xFFFFE7A8 : 0xFFE4EEF8;
        g.fillGradient(x + 1, cy + 1, x + NODE_W - 1, cy + ch - 1, topC, botC);
        g.fill(x + 2, cy + 1, x + NODE_W - 2, cy + 2, 0xFFFFFFFF);
        if (milestone) PadUi.corners(g, x, cy, NODE_W, ch);
        // el premio: el primero al doble, y si hay más, el segundo pequeño en la esquina
        List<JsonObject> prizes = list(l, "p");
        int iy = cy + 4;
        if (!prizes.isEmpty()) {
            PadUi.stretch(g, "glow", x + 4, iy - 2, NODE_W - 8, 36);
            PadUi.item(g, stack(prizes.get(0)), x + (NODE_W - 32) / 2, iy, 2);
            if (prizes.size() > 1) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 210);
                PadUi.slot(g, x + NODE_W - 19, iy + 20, 18, 18, 0);
                PadUi.item(g, stack(prizes.get(1)), x + NODE_W - 18, iy + 21, 1);
                g.pose().popPose();
            }
        }
        // cantidad de las monedas (los objetos ya llevan su número)
        if (!prizes.isEmpty() && !str(prizes.get(0), "tipo", "").equals("objeto")) {
            String q = "x" + PadUi.THOUSANDS.format(num(prizes.get(0), "n", 1));
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            int qw = PadUi.font().width(q) + 2;
            PadUi.box(g, x + 3, iy + 24, qw + 1, 9, 0xFF22346E);
            PadUi.text(g, q, x + 4, iy + 25, 0xFFFFFFFF);
            g.pose().popPose();
        }
        // abajo: el estado
        int sy = cy + ch - 16;
        if (sy < iy + 34) sy = iy + 34;
        if (state == 1) {
            boolean bh = !waiting && PadUi.inside(mx, my, x + 3, sy, NODE_W - 6, 13) && inTrack;
            g.fill(x + 3, sy, x + NODE_W - 3, sy + 13, bh ? 0xFF22A84C : 0xFF1E9E46);
            g.fill(x + 3, sy, x + NODE_W - 3, sy + 1, 0xFF8CF0A8);
            PadFont.drawCentered(g, "COBRAR", x + NODE_W / 2, sy + 1, 0xFFFFFF, true);
            if (!waiting && inTrack) hit(x + 3, sy, NODE_W - 6, 13, () -> claim(n));
        } else if (state == 2) {
            check(g, x + NODE_W / 2 - 4, sy + 4, 0xFFC27A10);
            if (n == flashLevel && now - flashAt < 900) {
                int a = (int) (200 * (1 - (now - flashAt) / 900F));
                g.fill(x + 1, cy + 1, x + NODE_W - 1, cy + ch - 1, a << 24 | 0xFFFFFF);
            }
        } else {
            lock(g, x + NODE_W / 2 - 3, sy + 3, 0xFF8EA4C4);
            g.fill(x + 1, cy + 1, x + NODE_W - 1, cy + ch - 1, 0x22B4C6DC);
        }
        if (you) { // «TÚ»: etiqueta de oro encima de la tarjeta, sobre el raíl
            int yw = PadFont.width("TÚ") + 6;
            int yx = x + NODE_W - yw + 1, yy = cy - 5;
            g.pose().pushPose();
            g.pose().translate(0, 0, 220);
            PadUi.box(g, yx, yy, yw, 9, PadUi.NAVY);
            g.fill(yx + 1, yy + 1, yx + yw - 1, yy + 8, PadUi.GOLD);
            PadFont.draw(g, "TÚ", yx + 3, yy - 1, 0x5A3A00, false);
            g.pose().popPose();
        }
        if (hv && state == 1 && !waiting) hit(x, cy, NODE_W, ch, () -> claim(n));
        else if (hv) hit(x, cy, NODE_W, ch, () -> {
            focus = n;
            pad.sound("tab", 0.5F);
        });
    }

    private void claim(int n) {
        if (waiting) return;
        focus = n;
        send("reclamar:" + n);
        pad.sound("select", 0.75F);
    }

    // ---- abajo: el nivel señalado y cómo se gana XP ------------------------------------------------------------------

    private void drawBottom(GuiGraphics g, double mx, double my, int lv) {
        int top = trackTop() + trackH() + 4, h = bottomH();
        int x = X, w = W;
        PadUi.panel(g, x, top, w, h);
        int max = levels();
        int show = hoverLevel > 0 ? hoverLevel : focus > 0 ? focus : Math.max(1, Math.min(max, firstReady() > 0 ? firstReady() : lv + 1));
        JsonObject l = null;
        for (JsonObject o : list(data, "niveles")) if (num(o, "n", 0) == show) l = o;
        // derecha: cómo se gana XP (las etiquetas, una tras otra; si no caben en una fila, en la siguiente)
        List<JsonObject> src = list(data, "fuentes");
        String head = "Cómo se gana XP";
        int all = 0;
        for (JsonObject s : src) all += PadUi.chipWidth("+" + num(s, "xp", 0) + " " + str(s, "n", "")) + 4;
        int colW = Math.min(Math.round(w * 0.52F), Math.max(PadUi.boldWidth(head), all - 4));
        int cx = x + w - 6 - colW;
        g.fill(cx - 6, top + 4, cx - 5, top + h - 4, 0xFFC6DAEE);
        PadUi.title(g, head, cx, top + 5, colW, PadUi.GOLD_TEXT);
        int chx = cx, chy = top + 17;
        for (JsonObject s : src) {
            String t = "+" + num(s, "xp", 0) + " " + str(s, "n", "");
            int cw = PadUi.chipWidth(t);
            if (chx > cx && chx + cw > cx + colW) {
                chx = cx;
                chy += 13;
            }
            if (chy + 11 > top + h - 2) break;
            PadUi.chip(g, chx, chy, t, PadUi.TONE_GREEN, 11);
            chx += cw + 4;
        }
        if (src.isEmpty()) PadUi.text(g, "Las misiones no dan XP.", cx, chy, PadUi.MUTED);
        // izquierda: el nivel
        int lx = x + 6, room = cx - 12 - lx;
        if (l == null) return;
        int state = (int) num(l, "e", 0);
        String title = "Nivel " + show + (show % 5 == 0 ? " · hito" : "");
        String st = state == 2 ? "Ya es tuyo" : state == 1 ? "¡Listo para reclamar!"
                : "Te faltan " + PadUi.THOUSANDS.format(Math.max(0, num(l, "x", 0) - num(data, "xp", 0))) + " XP";
        int tone = state == 2 ? PadUi.TONE_GOLD : state == 1 ? PadUi.TONE_GREEN : PadUi.TONE_GRAY;
        PadUi.title(g, title, lx, top + 5, room, PadUi.TEXT);
        int tw = PadUi.titleWidth(title, room);
        if (tw + 8 + PadUi.chipWidth(st) <= room) PadUi.chip(g, lx + tw + 6, top + 3, st, tone, 12);
        // los premios del nivel, uno tras otro
        int px = lx, py = top + 18;
        for (JsonObject p : list(l, "p")) {
            String nm = name(p);
            if (lx + room - px < 50) break; // sin sitio para otro (el nombre se recorta con «…»)
            PadUi.slot(g, px, py, 18, 18, 0);
            PadUi.item(g, stack(p), px + 1, py + 1, 1);
            String fit = PadUi.fitEnd(nm, lx + room - px - 22);
            PadUi.text(g, fit, px + 22, py + 5, PadUi.TEXT);
            px += 22 + PadUi.font().width(fit) + 10;
        }
        if (list(l, "p").isEmpty()) PadUi.text(g, "Sin premio.", lx, py + 5, PadUi.MUTED);
    }

    @Override
    boolean scroll(double mx, double my, double delta) {
        scrollTarget -= (float) Math.signum(delta) * PITCH;
        return true;
    }

    @Override
    boolean key(int key, int scan, int mods) {
        if (key == 262) { // →
            scrollTarget += PITCH * 3;
            return true;
        }
        if (key == 263) { // ←
            scrollTarget -= PITCH * 3;
            return true;
        }
        return false;
    }
}
