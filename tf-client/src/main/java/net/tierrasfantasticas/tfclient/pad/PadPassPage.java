package net.tierrasfantasticas.tfclient.pad;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphics;

/**
 * El TF Pass, el pase de temporada gratis. 1.3.67, rehecho de cero como un MAPA DE LA TEMPORADA (el dueño: «el pase
 * sigue igual, no veo innovación»):
 * <ul>
 *   <li>Arriba, el HUD: el escudo con tu nivel, «TF PASS» y la temporada, la barra de XP (con sus marcas) hasta el
 *       siguiente nivel, tus monedas verdes, la cuenta atrás y RECLAMAR TODO.</li>
 *   <li>En medio, el mapa: un escenario de noche (1.3.74, ver drawNight), colinas en tres capas que se desplazan a
 *       distinta velocidad (paralaje) y un camino que ondula de nivel en nivel. Cada nivel es un medallón redondo con su premio y su placa con el número; los
 *       hitos (de 5 en 5), más grandes y con banderín. El camino es de oro hasta tu nivel, donde está tu chincheta «TÚ».
 *       Los listos laten en verde con «COBRAR» flotando encima; los tuyos llevan la marca de oro; los de más adelante,
 *       velados y con candado. Al pasar el ratón por uno, un bocadillo con todos sus premios y lo que falta. Se mueve
 *       con la rueda, arrastrando o con las flechas; se abre en tu nivel.</li>
 *   <li>Abajo, una tira con cómo se gana XP.</li>
 * </ul>
 * Al cobrar: chispas que salen del medallón y una fanfarria. Lo mismo en tools/pad/sim/pages.py (pass_page).
 */
final class PadPassPage extends PadDataPage {
    private static final int PITCH = 60, R = 18, R_BIG = 22, HUD_H = 40, FOOT_H = 15;
    private static final Random RANDOM = new Random();
    private float scroll, scrollTarget;
    private boolean placed;
    private int focus = -1, hoverLevel = -1;
    private long flashLevel = -1, flashAt;
    private long lastNs = System.nanoTime();

    private static final class Spark {
        float x, y, vx, vy, life, max;
        int color;
    }

    private final List<Spark> sparks = new ArrayList<>();

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
        if (ev != null && (ev.has("reclamado") || ev.has("reclamados"))) {
            flashLevel = ev.has("reclamado") ? num(ev, "reclamado", -1) : 0;
            flashAt = System.currentTimeMillis();
            pad.sound(ev.has("reclamados") ? "gacha_win3" : "gacha_win2", 0.85F);
            burst(flashLevel);
        }
    }

    /** Chispas desde el medallón cobrado (o desde todos los que se ven, si fue RECLAMAR TODO). */
    private void burst(long level) {
        if (!PadSettings.animations) return;
        for (JsonObject l : list(data, "niveles")) {
            int n = (int) num(l, "n", 0);
            if (level > 0 && n != level) continue;
            float[] c = center(n);
            if (c[0] < X || c[0] > X + W) continue;
            for (int i = 0; i < 18; i++) {
                Spark s = new Spark();
                double a = RANDOM.nextDouble() * Math.PI * 2;
                float v = 40 + RANDOM.nextFloat() * 70;
                s.x = c[0];
                s.y = c[1];
                s.vx = (float) Math.cos(a) * v;
                s.vy = (float) Math.sin(a) * v - 50;
                s.max = s.life = 0.6F + RANDOM.nextFloat() * 0.6F;
                s.color = RANDOM.nextInt(3) == 0 ? 0xFFFFFF : RANDOM.nextBoolean() ? 0xFFE680 : 0x5CE48A;
                sparks.add(s);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Geometría
    // ---------------------------------------------------------------------------------------------------------------

    private int mapTop() {
        return Y + HUD_H + 3;
    }

    private int mapBottom() {
        return Y + H - FOOT_H - 3;
    }

    private int levels() {
        return list(data, "niveles").size();
    }

    /** Centro del medallón del nivel n (ya con el desplazamiento): el camino ondula arriba y abajo. */
    private float[] center(int n) {
        int top = mapTop(), bot = mapBottom(), mh = bot - top;
        float mid = top + mh * 0.42F;
        float amp = Math.max(0, Math.min(14, (mh - 2 * R_BIG - 34) / 2F));
        float x = X + 34 + (n - 1) * PITCH - scroll;
        float y = mid + (float) Math.sin(n * 0.95) * amp;
        return new float[] {x, y};
    }

    private float maxScroll() {
        return Math.max(0, (levels() - 1) * PITCH + 68 - W);
    }

    // ---------------------------------------------------------------------------------------------------------------

    @Override
    void render(GuiGraphics g, double mx, double my, float partial) {
        hits.clear();
        if (loading(g)) return;
        long ns = System.nanoTime();
        float dt = Math.min(0.1F, (ns - lastNs) / 1e9F);
        lastNs = ns;
        int lv = (int) num(data, "nivel", 0);
        int max = levels();
        float maxScroll = maxScroll();
        if (!placed) { // se abre con tu nivel (o el primero por cobrar) en el centro
            int first = firstReady();
            int at = first > 0 ? first : Math.min(max, Math.max(1, lv));
            scrollTarget = scroll = Math.max(0, Math.min(maxScroll, (at - 1) * PITCH + 34 - W / 2F));
            placed = true;
        }
        scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
        scroll += (scrollTarget - scroll) * (PadSettings.animations ? Math.min(1F, dt * 14) : 1F);
        if (Math.abs(scrollTarget - scroll) < 0.5F) scroll = scrollTarget;
        hoverLevel = -1;
        drawMap(g, mx, my, lv, max, maxScroll, dt);
        drawHud(g, mx, my, lv, max);
        drawFooter(g);
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

    // ---- HUD ------------------------------------------------------------------------------------------------------

    private void drawHud(GuiGraphics g, double mx, double my, int lv, int max) {
        int x = X, y = Y, w = W, h = HUD_H;
        g.fill(x + 2, y + h, x + w - 2, y + h + 1, 0x4018265C);
        PadUi.box(g, x, y, w, h, 0xFF0A1830);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF183F3A, 0xFF0E1C34);
        float t = PadSkin.time();
        PadSkin.stripes(g, x + 1, y + 1, w - 2, h - 2, 0.05F, t * 3);
        g.fill(x + 2, y + 1, x + w - 2, y + 2, 0xFF4FC88C);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, PadUi.GOLD_LO);
        // el escudo
        int sx = x + 6, sy = y + 2, ss = 36;
        pad.clipPush(g, x + 1, y + 1, w - 2, h - 2);
        PadSkin.rays(g, sx + ss / 2F, sy + ss / 2F, 44, 0.5F, t * 18);
        PadSkin.glow(g, sx + ss / 2F, sy + ss / 2F, 28, 0x8CF0B4, 110);
        pad.clipPop(g);
        drawBadge(g, sx, sy, ss, lv);
        int tx = sx + ss + 7;
        boolean active = data.has("activo") && data.get("activo").getAsBoolean();
        // a la derecha: monedas verdes, tiempo y RECLAMAR TODO
        int right = x + w - 5;
        long left = num(data, "fin", 0) - num(data, "ahora", 0) - (System.currentTimeMillis() - dataAt);
        String clock = active ? until(left) : "Sin temporada";
        int cw = PadUi.font().width(clock) + 16;
        pill(g, right - cw, y + 4, cw, 0xFF2A3470, 0xFF1A2050);
        drawClock(g, right - cw + 4, y + 6);
        PadUi.text(g, clock, right - cw + 12, y + 6, 0xFFFFE9A8);
        long greens = num(data, "verdes", 0);
        String gtxt = PadUi.THOUSANDS.format(greens);
        int gw = PadUi.font().width(gtxt) + 16;
        int gx = right - cw - 4 - gw;
        pill(g, gx, y + 4, gw, 0xFF1E6B52, 0xFF124436);
        pad.blit(g, "coin_green_s", gx + 3, y + 6);
        PadUi.text(g, gtxt, gx + 13, y + 6, 0xFF8CF0B4);
        int ready = readyCount();
        int barRight = right;
        if (ready > 0 && active) {
            String label = "RECLAMAR TODO (" + ready + ")";
            int bw = PadUi.buttonWidth(label);
            int bx = right - bw, by = y + h - 5 - 16;
            boolean hv = !waiting && PadUi.inside(mx, my, bx, by, bw, 16);
            if (hv) pad.hover("§todo");
            PadSkin.button(g, bx, by, bw, label, PadView.GREEN, hv, !waiting, false);
            if (!waiting) hit(bx, by, bw, 16, () -> {
                send("todo");
                pad.sound("select", 0.75F);
            });
            barRight = bx - 6;
        }
        // título y temporada
        PadFont.drawShadow(g, "TF PASS", tx, y + 4, 0xFFD650, 0x3A2A00);
        String season = "Temporada " + num(data, "temporada", 1) + (str(data, "nombre", "").isEmpty() ? "" : ": " + str(data, "nombre", ""));
        int room = gx - 6 - (tx + PadFont.width("TF PASS") + 6);
        if (room > 30) PadUi.text(g, PadUi.fitEnd(season, room), tx + PadFont.width("TF PASS") + 6, y + 5, 0xFFE0ECFF);
        // la barra de XP, con una marca cada 10 %
        int barX = tx, barY = y + 18, barW = Math.max(60, barRight - tx), barH = 9;
        long xpIn = num(data, "xpEn", 0), xpNeed = num(data, "xpNivel", 0);
        float f = lv >= max ? 1F : xpNeed <= 0 ? 1F : Math.min(1F, xpIn / (float) xpNeed);
        PadUi.box(g, barX, barY, barW, barH, 0xFF05101E);
        g.fill(barX + 1, barY + 1, barX + barW - 1, barY + barH - 1, 0xFF1E2C50);
        int fw = Math.round((barW - 2) * f);
        if (fw > 0) {
            g.fillGradient(barX + 1, barY + 1, barX + 1 + fw, barY + barH - 1, 0xFF9CF7B6, 0xFF1E9E46);
            g.fill(barX + 1, barY + 1, barX + 1 + fw, barY + 2, 0xFFE0FFE8);
            if (PadSettings.animations && fw > 8) {
                float sh = (System.currentTimeMillis() / 9F) % (fw + 40) - 20;
                PadSkin.hgrad(g, barX + 1 + Math.max(0, sh), barY + 1, barX + 1 + Math.min(fw, sh + 14), barY + barH - 1, 0x00FFFFFF, 0x66FFFFFF);
            }
        }
        for (int i = 1; i < 10; i++) {
            int mxk = barX + 1 + (barW - 2) * i / 10;
            g.fill(mxk, barY + 1, mxk + 1, barY + barH - 1, 0x4005101E);
        }
        String xpText = lv >= max ? "¡Nivel máximo!" : PadUi.THOUSANDS.format(xpIn) + " / " + PadUi.THOUSANDS.format(xpNeed) + " XP";
        PadUi.text(g, xpText, barX, barY + 11, 0xFFE0ECFF);
        if (lv < max) {
            String next = "nivel " + (lv + 1);
            PadUi.text(g, next, barX + barW - PadUi.font().width(next), barY + 11, 0xFFB8C2F0);
        }
    }

    private static void pill(GuiGraphics g, int x, int y, int w, int top, int bot) {
        PadUi.box(g, x, y, w, 12, 0xFF05101E);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + 11, top, bot);
        g.fill(x + 2, y + 1, x + w - 2, y + 2, 0x33FFFFFF);
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
        g.pose().translate(cx, y + 16, 0);
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

    // ---- el mapa --------------------------------------------------------------------------------------------------

    private static final String[] LAND = {"land_far", "land_mid", "land_near"};
    /** Las colinas de noche (siluetas, la de delante la más oscura) y su borde iluminado por la luna. */
    private static final int[] LAND_TINT = {0x245A68, 0x16404E, 0x0A1F2B}, LAND_RIM = {0x9FE8D8, 0x86DCC0, 0x6CC8A0};
    private static final float[] LAND_SPEED = {0.15F, 0.35F, 0.6F};

    private void drawMap(GuiGraphics g, double mx, double my, int lv, int max, float maxScroll, float dt) {
        int top = mapTop(), bot = mapBottom(), x = X, w = W, mh = bot - top;
        long now = System.currentTimeMillis();
        float t = PadSkin.time();
        PadUi.box(g, x, top, w, mh, 0xFF06101C);
        pad.scissor(g, x + 1, top + 1, w - 2, mh - 2);
        drawNight(g, x, top, w, mh, lv, max, t);
        // el camino: borde oscuro, piedra (lo que falta) y de oro hasta tu nivel; con su raya discontinua
        List<JsonObject> lvls = list(data, "niveles");
        for (int pass = 0; pass < 3; pass++) {
            for (int n = 1; n < max; n++) {
                float[] a = center(n), b = center(n + 1);
                if (b[0] < x - PITCH || a[0] > x + w + PITCH) continue;
                boolean gold = n + 1 <= lv;
                if (pass == 0) PadSkin.line(g, a[0], a[1], b[0], b[1], 11, 0x99040A14);
                else if (pass == 1) PadSkin.line(g, a[0], a[1], b[0], b[1], 8, gold ? PadUi.GOLD : 0xFF33506E);
                else {
                    float len = (float) Math.hypot(b[0] - a[0], b[1] - a[1]);
                    for (float d = R; d < len - R; d += 7) {
                        float k = d / len;
                        float px = a[0] + (b[0] - a[0]) * k, py = a[1] + (b[1] - a[1]) * k;
                        PadSkin.rect(g, px - 1, py - 0.5F, px + 2, py + 0.5F, gold ? 0xFFFFF0B0 : 0xFF7896C0);
                    }
                }
            }
        }
        g.flush();
        // los medallones
        boolean inMap = PadUi.inside(mx, my, x, top, w, mh);
        JsonObject hovered = null;
        float[] hoverAt = null;
        for (JsonObject l : lvls) {
            int n = (int) num(l, "n", 0);
            float[] c = center(n);
            if (c[0] < x - R_BIG - 4 || c[0] > x + w + R_BIG + 4) continue;
            boolean big = n % 5 == 0;
            int r = big ? R_BIG : R;
            boolean hv = inMap && Math.hypot(mx - c[0], my - c[1]) <= r + 2;
            if (hv) {
                hoverLevel = n;
                hovered = l;
                hoverAt = c;
                pad.hover("§nivel" + n);
            }
            drawNode(g, l, n, c[0], c[1], r, big, hv, lv, now);
            int state = (int) num(l, "e", 0);
            final int level = n;
            if (hv) hit(Math.round(c[0] - r), Math.round(c[1] - r), 2 * r, 2 * r, () -> {
                if (state == 1) claim(level);
                else {
                    focus = level;
                    pad.sound("tab", 0.5F);
                }
            });
        }
        // la chincheta «TÚ» (si tu nivel está por cobrar, va en su etiqueta de COBRAR)
        boolean lvReady = false;
        for (JsonObject l : lvls) if (num(l, "n", 0) == lv && num(l, "e", 0) == 1) lvReady = true;
        if (lv > 0 && lv <= max && !lvReady) {
            float[] c = center(lv);
            int r = lv % 5 == 0 ? R_BIG : R;
            float bob = PadSettings.animations ? (float) Math.sin(now / 260.0) * 1.5F : 0;
            drawPin(g, c[0], c[1] - r - 3 + bob);
        }
        // chispas de lo cobrado
        drawSparks(g, dt);
        pad.noScissor(g);
        // flechas a los lados
        boolean canL = scrollTarget > 0, canR = scrollTarget < maxScroll;
        arrowButton(g, mx, my, x + 3, top + mh / 2 - 9, canL, true, () -> scrollTarget -= PITCH * 4);
        arrowButton(g, mx, my, x + w - 13, top + mh / 2 - 9, canR, false, () -> scrollTarget += PITCH * 4);
        // el bocadillo del nivel del ratón (por encima de todo)
        if (hovered != null) drawBubble(g, hovered, hoverAt, top, bot);
    }

    /**
     * El escenario del mapa (1.3.74, el dueño: «el fondo del TF Pass es una mierda, dale rework como al Gachapón»). Antes,
     * un cielo de día casi blanco con colinas verde pálido: sin contraste, los medallones se perdían. Ahora, como el
     * escenario de la ruleta pero suyo: noche esmeralda que se apaga hacia abajo, una aurora que ondula, estrellas que
     * titilan y la luna; detrás de tu nivel, un charco de luz de oro y focos que giran (te siguen al desplazar el mapa);
     * colinas en silueta con el borde de luna (paralaje), luciérnagas que suben, rayas que se mueven y los lados en
     * sombra. Igual en tools/pad/sim/pages.py (pass_page).
     */
    private void drawNight(GuiGraphics g, int x, int top, int w, int mh, int lv, int max, float t) {
        int bot = top + mh, x0 = x + 1, x1 = x + w - 1;
        g.fillGradient(x0, top + 1, x1, bot - 1, 0xFF0F3A44, 0xFF0A1430);
        // la aurora: dos cintas que ondulan (y se mueven un poco con el mapa)
        float drift = scroll * 0.05F;
        for (int band = 0; band < 2; band++) {
            int col = band == 0 ? 0x5CE48A : 0x3FC8E0, a = band == 0 ? 120 : 88;
            float base = top + mh * (band == 0 ? 0.17F : 0.27F), thick = band == 0 ? 13 : 9;
            for (int cx = x0; cx < x1; cx += 3) {
                float u = cx + drift;
                float cy = base + (float) Math.sin(u * 0.019F + t * 0.55F + band * 2.1F) * 6 + (float) Math.sin(u * 0.047F - t * 0.8F) * 2.5F;
                float k = 0.5F + 0.5F * (float) Math.sin(u * 0.011F + t * 0.35F + band * 1.3F);
                int c = PadSkin.alpha(col, Math.round(a * (0.35F + 0.65F * k))), none = PadSkin.alpha(col, 0);
                int cx1 = Math.min(cx + 3, x1);
                PadSkin.vgrad(g, cx, cy - thick, cx1, cy, none, c);
                PadSkin.vgrad(g, cx, cy, cx1, cy + thick * 1.8F, c, none);
            }
        }
        // estrellas (siempre las mismas; casi quietas al desplazar) y la luna
        for (int i = 0; i < 34; i++) {
            int h = hash(i * 7919 + 13);
            float span = w - 6;
            float sx = x + 3 + (((h & 0xFFFF) / 65535F * span - scroll * 0.03F) % span + span) % span;
            float sy = top + 3 + ((h >>> 16) & 0xFF) / 255F * mh * 0.5F;
            float b = 0.3F + 0.7F * PadSkin.twinkle(i * 1.7F + 0.4F);
            if (i % 9 == 0) PadSkin.sparkle(g, Math.round(sx), Math.round(sy), 2, b, 0xCFE8FF);
            else PadSkin.rect(g, sx, sy, sx + 1, sy + 1, PadSkin.alpha(i % 4 == 0 ? 0xFFF0C0 : 0xDDEEFF, Math.round(70 + 150 * b)));
        }
        float moonX = x + w * 0.88F - scroll * 0.01F, moonY = top + 13;
        PadSkin.glow(g, moonX, moonY, 34, 0xFFF4C8, 80);
        PadSkin.texture(g, TFPadScreen.tex("moon"), moonX - 11, moonY - 11, 22, 22, 1F, 0xFFFFFF, false);
        // tu nivel: un charco de luz de oro y focos que giran despacio
        if (lv > 0) {
            float[] c = center(Math.min(max, lv));
            PadSkin.glow(g, c[0], c[1], mh * 0.95F, 0xFFD650, 64);
            PadSkin.rays(g, c[0], c[1], w * 0.62F, 0.10F, t * 6);
        }
        // las colinas: el resplandor del horizonte detrás, el borde de luna (un poco más arriba) y encima la silueta
        float lh = Math.min(56, mh * 0.55F), lw = 480 * lh / 56F;
        PadSkin.vgrad(g, x0, bot - lh - 16, x1, bot - lh * 0.4F, 0x003FC8E0, 0x553FC8E0);
        for (int i = 0; i < LAND.length; i++) {
            float off = ((scroll * LAND_SPEED[i]) % lw + lw) % lw, ly = bot - lh - (2 - i) * 4;
            for (float lx = x0 - off; lx < x + w; lx += lw) {
                PadSkin.texture(g, TFPadScreen.tex(LAND[i]), lx, ly - 1, lw, lh, 0.85F, LAND_RIM[i], false);
                PadSkin.texture(g, TFPadScreen.tex(LAND[i]), lx, ly, lw, lh, 1F, LAND_TINT[i], false);
            }
        }
        g.fill(x0, bot - 6, x1, bot - 1, 0xFF081C26);
        // luciérnagas que suben despacio desde las colinas
        for (int i = 0; i < 12; i++) {
            int h = hash(i * 104_729 + 7);
            float rise = mh * 0.75F, life = (t * (6 + (h & 7)) + (h >>> 8 & 0xFF)) % rise;
            float span = w - 10;
            float fx = x + 5 + (((h >>> 16 & 0xFFFF) / 65535F * span - scroll * 0.5F + (float) Math.sin(t * 0.9F + i) * 5) % span + span) % span;
            float fy = bot - 8 - life;
            float fade = Math.min(1F, life / 12F) * Math.min(1F, (rise - life) / 18F);
            float b = (0.45F + 0.55F * PadSkin.twinkle(i * 2.3F)) * fade;
            int col = i % 3 == 0 ? 0x8CF0B4 : 0xFFE680;
            PadSkin.glow(g, fx, fy, 4, col, Math.round(110 * b));
            PadSkin.rect(g, fx - 0.5F, fy - 0.5F, fx + 0.5F, fy + 0.5F, PadSkin.alpha(0xFFFFFF, Math.round(230 * b)));
        }
        // rayas que se mueven (como la ruleta), los lados en sombra y el filo de luz de arriba
        PadSkin.stripes(g, x0, top + 1, w - 2, mh - 2, 0.04F, t * 4);
        int side = Math.min(36, w / 8);
        PadSkin.hgrad(g, x0, top + 1, x0 + side, bot - 1, 0xA0060E1C, 0x00060E1C);
        PadSkin.hgrad(g, x1 - side, top + 1, x1, bot - 1, 0x00060E1C, 0xA0060E1C);
        g.fill(x + 2, top + 1, x + w - 2, top + 2, 0x55FFFFFF);
    }

    private static int hash(int v) {
        v ^= v >>> 16;
        v *= 0x7FEB352D;
        v ^= v >>> 15;
        v *= 0x846CA68B;
        v ^= v >>> 16;
        return v;
    }

    /**
     * Un nivel: sombra, aro del color de su estado (gris si falta, verde que late si está listo, oro si es tuyo), el
     * premio al doble, la placa con el número debajo; los hitos con banderín, los listos con «COBRAR» flotando y los
     * de más adelante velados y con candado.
     */
    private void drawNode(GuiGraphics g, JsonObject l, int n, float cx, float cy, int r, boolean big, boolean hover, int lv, long now) {
        int state = (int) num(l, "e", 0); // 0 bloqueado, 1 listo, 2 reclamado
        int ring = state == 2 ? PadUi.GOLD : state == 1 ? 0xFF2EB85A : 0xFF8FA6C8;
        int inner = state == 2 ? 0xFFFFF4D0 : state == 1 ? 0xFFEAFFF0 : 0xFFE4EEF8;
        float lift = hover ? 2 : 0;
        cy -= lift;
        if (state == 1 && PadSettings.animations) {
            float p = 0.5F + 0.5F * (float) Math.sin(now / 200.0 + n);
            PadSkin.glow(g, cx, cy, r * 1.9F, 0x5CE48A, Math.round(90 + 90 * p));
        }
        if (n == lv) PadSkin.glow(g, cx, cy, r * 1.7F, 0xFFE680, 120);
        PadSkin.rect(g, cx - r * 0.8F, cy + r + 2 + lift, cx + r * 0.8F, cy + r + 4 + lift, 0x40183040);
        if (big) PadSkin.disc(g, cx, cy, r + 4, PadUi.GOLD_LO);
        PadSkin.disc(g, cx, cy, r + 2, 0xFF102040);
        PadSkin.disc(g, cx, cy, r, hover ? PadSkin.lighten(ring, 30) : ring);
        PadSkin.disc(g, cx, cy, r - 3, inner);
        PadSkin.disc(g, cx - r * 0.25F, cy - r * 0.3F, r * 0.45F, 0x55FFFFFF);
        // el premio
        List<JsonObject> prizes = list(l, "p");
        if (!prizes.isEmpty()) {
            PadUi.item(g, stack(prizes.get(0)), Math.round(cx - 16), Math.round(cy - 16), 2);
            if (prizes.size() > 1) {
                String more = "+" + (prizes.size() - 1);
                g.pose().pushPose();
                g.pose().translate(0, 0, 210);
                int mw = PadFont.width(more) + 6;
                PadUi.box(g, Math.round(cx - r - 2), Math.round(cy - r - 2), mw, 11, 0xFF22346E);
                PadFont.draw(g, more, Math.round(cx - r + 1), Math.round(cy - r - 1), 0xFFFFFF, false);
                g.pose().popPose();
            }
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 220);
        if (state == 0 && n > lv) {
            PadSkin.disc(g, cx, cy, r - 3, 0x80E4EEF8);
            lock(g, Math.round(cx + r - 9), Math.round(cy + r - 11), 0xFF6A80A4);
        } else if (state == 2) {
            PadSkin.disc(g, cx + r - 5, cy + r - 6, 6, PadUi.GOLD_LO);
            PadSkin.disc(g, cx + r - 5, cy + r - 6, 5, PadUi.GOLD);
            check(g, Math.round(cx + r - 8), Math.round(cy + r - 9), 0xFFFFFFFF);
            if (n == flashLevel || flashLevel == 0) {
                long since = now - flashAt;
                if (since < 700) PadSkin.disc(g, cx, cy, r, PadSkin.alpha(0xFFFFFF, Math.round(200 * (1 - since / 700F))));
            }
        }
        // la placa con el número
        String num = Integer.toString(n);
        int pw = Math.max(16, PadFont.width(num) + 8), px = Math.round(cx - pw / 2F), py = Math.round(cy + r + 1);
        PadUi.box(g, px, py, pw, 11, 0xFF102040);
        g.fillGradient(px + 1, py + 1, px + pw - 1, py + 10, state > 0 ? PadUi.GOLD_HI : 0xFFF2F7FD, state > 0 ? PadUi.GOLD : 0xFFC4D6EA);
        PadFont.drawCentered(g, num, Math.round(cx), py + 1, state > 0 ? 0x5A3A00 : 0x34507E, false);
        // los hitos, con su banderín
        if (big) {
            int fx = Math.round(cx + r - 2), fy = Math.round(cy - r - 10);
            g.fill(fx, fy, fx + 1, fy + 12, 0xFF5A3A10);
            float wave = PadSettings.animations ? (float) Math.sin(now / 180.0 + n) : 0;
            PadSkin.tri(g, fx + 1, fy, fx + 1, fy + 6, fx + 9 + wave, fy + 3, 0xFFE2466A);
            g.flush();
        }
        // listo: «COBRAR» flotando encima (si es tu nivel, con «TÚ» delante en oro: una sola etiqueta)
        if (state == 1) {
            float bob = PadSettings.animations ? (float) Math.sin(now / 220.0 + n) * 1.5F : 0;
            int tu = n == lv ? PadFont.width("TÚ") + 8 : 0;
            int tw = tu + PadFont.width("COBRAR") + 8, tx = Math.round(cx - tw / 2F), ty = Math.round(cy - r - 15 + bob);
            PadUi.box(g, tx, ty, tw, 12, 0xFF0E5626);
            g.fillGradient(tx + 1, ty + 1, tx + tw - 1, ty + 11, 0xFF5CE48A, 0xFF1A9442);
            g.fill(tx + 2, ty + 1, tx + tw - 2, ty + 2, 0xFFA8F8C0);
            if (tu > 0) {
                g.fillGradient(tx + 1, ty + 1, tx + tu, ty + 11, PadUi.GOLD_HI, PadUi.GOLD);
                PadFont.draw(g, "TÚ", tx + 4, ty + 1, 0x5A3A00, false);
            }
            PadFont.drawShadow(g, "COBRAR", tx + tu + 4, ty + 2, 0xFFFFFF, 0xFF0E5626);
            for (int i = 0; i < 3; i++) g.fill(Math.round(cx) - 2 + i, ty + 12 + i, Math.round(cx) + 3 - i, ty + 13 + i, 0xFF1A9442);
        }
        g.pose().popPose();
    }

    /** La chincheta «TÚ» con la punta en (x, y). */
    private void drawPin(GuiGraphics g, float x, float y) {
        int w = PadFont.width("TÚ") + 8, h = 12;
        int bx = Math.round(x - w / 2F), by = Math.round(y - h - 4);
        g.pose().pushPose();
        g.pose().translate(0, 0, 230);
        PadSkin.tri(g, x - 3, by + h - 1, x + 4, by + h - 1, x + 0.5F, y, 0xFF102040);
        PadSkin.tri(g, x - 2, by + h - 1, x + 3, by + h - 1, x + 0.5F, y - 1.5F, PadUi.GOLD);
        g.flush();
        PadUi.box(g, bx, by, w, h, 0xFF102040);
        g.fillGradient(bx + 1, by + 1, bx + w - 1, by + h - 1, PadUi.GOLD_HI, PadUi.GOLD);
        PadFont.draw(g, "TÚ", bx + 4, by + 1, 0x5A3A00, false);
        g.pose().popPose();
    }

    /** El bocadillo de un nivel: su número, si es tuyo, si está listo o cuánto falta, y todos sus premios. */
    private void drawBubble(GuiGraphics g, JsonObject l, float[] c, int top, int bot) {
        int n = (int) num(l, "n", 0), state = (int) num(l, "e", 0);
        List<JsonObject> prizes = list(l, "p");
        String head = "Nivel " + n + (n % 5 == 0 ? " · hito" : "");
        String st = state == 2 ? "Ya es tuyo" : state == 1 ? "¡Listo! Pulsa para cobrar"
                : "Te faltan " + PadUi.THOUSANDS.format(Math.max(0, num(l, "x", 0) - num(data, "xp", 0))) + " XP";
        int w = Math.max(PadUi.boldWidth(head), PadUi.font().width(st)) + 14;
        for (JsonObject p : prizes) w = Math.max(w, PadUi.font().width(name(p)) + 34);
        w = Math.min(w, W - 20);
        int h = 26 + prizes.size() * 18;
        int r = n % 5 == 0 ? R_BIG : R;
        int bx = Math.round(Math.max(X + 4, Math.min(X + W - 4 - w, c[0] - w / 2F)));
        boolean below = c[1] - r - 8 - h < top + 2;
        int by = below ? Math.round(c[1] + r + 14) : Math.round(c[1] - r - 10 - h);
        by = Math.max(top + 2, Math.min(bot - h - 2, by));
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.fill(bx + 2, by + h, bx + w - 2, by + h + 2, 0x40102040);
        PadUi.box(g, bx, by, w, h, 0xFF102040);
        g.fillGradient(bx + 1, by + 1, bx + w - 1, by + h - 1, 0xFFFFFFFF, 0xFFEAF2FB);
        int edge = state == 2 ? PadUi.GOLD : state == 1 ? 0xFF2EB85A : 0xFF8FA6C8;
        g.fill(bx + 1, by + 1, bx + w - 1, by + 3, edge);
        PadUi.title(g, head, bx + 7, by + 5, w - 14, PadUi.TEXT);
        PadUi.text(g, PadUi.fitEnd(st, w - 14), bx + 7, by + 15, state == 1 ? 0xFF1A9442 : state == 2 ? PadUi.GOLD_TEXT : PadUi.MUTED);
        int py = by + 25;
        for (JsonObject p : prizes) {
            PadUi.slot(g, bx + 6, py, 18, 18, 0);
            PadUi.item(g, stack(p), bx + 7, py + 1, 1);
            PadUi.text(g, PadUi.fitEnd(name(p), w - 34), bx + 28, py + 5, PadUi.TEXT);
            py += 18;
        }
        g.pose().popPose();
    }

    private void drawSparks(GuiGraphics g, float dt) {
        if (sparks.isEmpty()) return;
        for (int i = sparks.size() - 1; i >= 0; i--) {
            Spark s = sparks.get(i);
            s.life -= dt;
            if (s.life <= 0) {
                sparks.remove(i);
                continue;
            }
            s.x += s.vx * dt;
            s.y += s.vy * dt;
            s.vy += 150 * dt;
            float a = Math.min(1F, s.life / s.max * 1.6F);
            PadSkin.rect(g, s.x, s.y, s.x + 1.5F, s.y + 1.5F, PadSkin.alpha(s.color, Math.round(255 * a)));
        }
        g.flush();
    }

    private void arrowButton(GuiGraphics g, double mx, double my, int x, int y, boolean on, boolean left, Runnable action) {
        boolean hv = on && PadUi.inside(mx, my, x, y, 10, 18);
        if (hv) pad.hover(left ? "§izq" : "§der");
        PadUi.box(g, x, y, 10, 18, on ? (hv ? 0xFF3496FA : 0xFF34507E) : 0x8034507E);
        PadUi.box(g, x + 1, y + 1, 8, 16, on ? (hv ? 0xFFDCEEFF : 0xFFF2F8FF) : 0x80EEF3F9);
        arrow(g, x + 5, y + 9, left, on ? (hv ? 0xFF1E4E9C : 0xFF34507E) : 0xFFB4C6DC);
        if (on) hit(x, y, 10, 18, () -> {
            action.run();
            pad.sound("tab", 0.5F);
        });
    }

    private void claim(int n) {
        if (waiting) return;
        focus = n;
        send("reclamar:" + n);
        pad.sound("select", 0.75F);
    }

    // ---- abajo: cómo se gana XP -----------------------------------------------------------------------------------

    private void drawFooter(GuiGraphics g) {
        int y = Y + H - FOOT_H, x = X, w = W;
        PadUi.box(g, x, y, w, FOOT_H, 0xFF0A1830);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + FOOT_H - 1, 0xFF183F3A, 0xFF0E1C34);
        PadFont.drawShadow(g, "XP:", x + 6, y + 3, 0xFFD650, 0x3A2A00);
        int chx = x + 8 + PadFont.width("XP:") + 4;
        List<JsonObject> src = list(data, "fuentes");
        for (JsonObject s : src) {
            String t = "+" + num(s, "xp", 0) + " " + str(s, "n", "");
            int cw = PadUi.chipWidth(t);
            if (chx + cw > x + w - 6) break;
            PadUi.chip(g, chx, y + 2, t, PadUi.TONE_GREEN, 11);
            chx += cw + 4;
        }
        if (src.isEmpty()) PadUi.text(g, "Las misiones no dan XP.", chx, y + 4, 0xFFB8C2F0);
    }

    @Override
    boolean scroll(double mx, double my, double delta) {
        scrollTarget -= (float) Math.signum(delta) * PITCH;
        return true;
    }

    @Override
    boolean drag(double mx, double my, double dy) {
        return false;
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
