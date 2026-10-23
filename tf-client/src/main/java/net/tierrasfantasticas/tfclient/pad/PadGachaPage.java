/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.pad;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * El Gachapón. 1.3.67, rehecho de cero (el dueño: «la ruleta es una mierda: animación lenta, sin sonidos»):
 * <ul>
 *   <li>Pestañas RULETA · PREMIOS · HISTORIAL: la ruleta tiene toda la pantalla.</li>
 *   <li>Un escenario oscuro con focos que giran. Arriba tus monedas verdes y la garantía como una barra de pasos.</li>
 *   <li>El carrete: tarjetas en perspectiva (la del centro grande, las de los lados más pequeñas y en sombra), una
 *       lengüeta de oro que salta con cada premio que pasa, estela de velocidad y un tic que sube de tono cuanto más
 *       rápido va. Gira en 2,4 s: arranca de golpe (ya al pulsar, mientras contesta el servidor, el carrete acelera),
 *       frena, se pasa un poco y vuelve a encajar con un clac.</li>
 *   <li>Al salir el premio: destello del color de su rareza, la tarjeta da un salto, rayos, chispas que saltan y caen
 *       y una fanfarria según la rareza (cuatro, de común a la más rara); en las más raras, además, la pantalla tiembla.
 *       En las tiradas de 5, las cinco cartas se voltean una a una debajo, con la mejor marcada.</li>
 *   <li>Sin girar: cómo se juega y una barra con la probabilidad de cada rareza.</li>
 * </ul>
 * El servidor tira, cobra y da el premio; aquí solo se anima (la tira se monta con premios al azar por su probabilidad
 * y el ganador en su sitio). Lo mismo en tools/pad/sim/pages.py (gacha_page).
 */
final class PadGachaPage extends PadDataPage {
    /** Tiempo de frenada (hasta pasarse) y de volver a encajar, en ms; dónde va el ganador en la tira. */
    private static final int SPIN_MS = 2100, SETTLE_MS = 330, WIN_INDEX = 34;
    /** Cuánto se pasa (en tarjetas) antes de volver. */
    private static final float OVERSHOOT = 0.32F;
    private static final Random RANDOM = new Random();

    private String tab = "ruleta";
    private int listScroll;
    /** La tira de la ruleta: premios con su rareza. */
    private final List<JsonObject> strip = new ArrayList<>();
    private long spinStart = -1, lastEvent, revAt = -1;
    private float spinFrom, spinTo, offset, speed;
    private int lastTickCard = Integer.MIN_VALUE;
    private long lastTickAt;
    private boolean stopped, revealed;
    private long revealAt;
    private List<JsonObject> result = List.of();
    private int resultBest;
    private float kick; // la lengüeta: cuánto está doblada (0 quieta, 1 del todo)
    private long lastNs = System.nanoTime();
    /** Medidas del carrete (dependen del alto de la pantalla). */
    private int cardW = 44, cardH = 56, pitch = 50;

    /** Chispas del premio: posición, velocidad, vida y color. */
    private static final class Spark {
        float x, y, vx, vy, life, max;
        int color, size;
    }

    private final List<Spark> sparks = new ArrayList<>();

    PadGachaPage(TFPadScreen pad) {
        super(pad, "gachapon");
    }

    @Override
    String title() {
        return "GACHAPÓN";
    }

    @Override
    void received(JsonObject o) {
        if (strip.isEmpty()) fillIdle();
        JsonObject ev = o.has("evento") && o.get("evento").isJsonObject() ? o.getAsJsonObject("evento") : null;
        if (ev == null || num(ev, "id", 0) == lastEvent) {
            revAt = -1;
            return;
        }
        lastEvent = num(ev, "id", 0);
        result = list(ev, "premios");
        revAt = -1;
        if (result.isEmpty()) return;
        resultBest = (int) Math.max(0, Math.min(result.size() - 1, num(ev, "mejor", 0)));
        // la tira: lo que hay ahora en la ventana sigue igual, luego premios al azar y el ganador en WIN_INDEX
        int first = (int) Math.floor(offset / pitch) - 6;
        List<JsonObject> keep = new ArrayList<>();
        for (int i = first; i < first + 14; i++) if (!strip.isEmpty()) keep.add(strip.get(Math.floorMod(i, strip.size())));
        strip.clear();
        strip.addAll(keep);
        while (strip.size() < WIN_INDEX + 10) strip.add(randomPrize());
        strip.set(WIN_INDEX, result.get(resultBest));
        spinFrom = offset - first * (float) pitch;
        offset = spinFrom;
        spinTo = WIN_INDEX * pitch + (RANDOM.nextFloat() - 0.5F) * (cardW - 14);
        spinStart = System.currentTimeMillis();
        stopped = revealed = false;
        sparks.clear();
        tab = "ruleta";
    }

    /** Tira de espera: los premios en orden, para que la ventana no esté vacía. */
    private void fillIdle() {
        strip.clear();
        List<JsonObject> ps = list(data, "premios");
        if (ps.isEmpty()) return;
        for (int i = 0; i < 24; i++) strip.add(ps.get(i % ps.size()));
    }

    /** Un premio al azar según su probabilidad (para rellenar la tira). */
    private JsonObject randomPrize() {
        List<JsonObject> ps = list(data, "premios");
        double total = 0;
        for (JsonObject p : ps) total += Math.max(0.01, dec(p, "p", 1));
        double x = RANDOM.nextDouble() * total;
        for (JsonObject p : ps) {
            x -= Math.max(0.01, dec(p, "p", 1));
            if (x < 0) return p;
        }
        return ps.isEmpty() ? new JsonObject() : ps.get(ps.size() - 1);
    }

    private boolean spinning() {
        return spinStart >= 0 && !revealed;
    }

    /** Dónde va la tira en este instante (y su velocidad, para la estela y el tic). */
    private void advance(float dt) {
        float before = offset;
        if (spinStart >= 0) {
            long t = System.currentTimeMillis() - spinStart;
            float over = spinTo + OVERSHOOT * pitch;
            if (t < SPIN_MS) {
                float k = t / (float) SPIN_MS;
                float e = 1 - (1 - k) * (1 - k) * (1 - k); // arranca de golpe y frena
                offset = spinFrom + (over - spinFrom) * e;
            } else if (t < SPIN_MS + SETTLE_MS) {
                if (!stopped) {
                    stopped = true;
                    pad.sound("gacha_stop", 0.8F);
                }
                float k = (t - SPIN_MS) / (float) SETTLE_MS;
                float e = (1 - (float) Math.cos(Math.PI * k)) / 2;
                offset = over + (spinTo - over) * e;
            } else {
                offset = spinTo;
                if (!revealed) reveal();
            }
        } else if (revAt >= 0) {
            // esperando al servidor tras pulsar: el carrete ya acelera
            float k = Math.min(1F, (System.currentTimeMillis() - revAt) / 400F);
            offset += dt * (40 + 900 * k * k);
        } else if (PadSettings.animations) {
            offset += dt * 14;
        }
        speed = dt > 0 ? Math.abs(offset - before) / dt : 0;
    }

    /** Salió el premio: fanfarria de su rareza, destello y chispas. */
    private void reveal() {
        revealed = true;
        revealAt = System.currentTimeMillis();
        if (result.isEmpty()) return;
        JsonObject best = result.get(resultBest);
        int rank = rank(str(best, "r", ""));
        pad.sound("gacha_win" + rank, rank >= 3 ? 1.0F : 0.85F);
        int col = rarityColor(str(best, "r", ""));
        int n = PadSettings.animations ? 10 + rank * 10 : 0;
        for (int i = 0; i < n; i++) {
            Spark s = new Spark();
            double a = RANDOM.nextDouble() * Math.PI * 2;
            float v = 50 + RANDOM.nextFloat() * (60 + rank * 25);
            s.vx = (float) Math.cos(a) * v;
            s.vy = (float) Math.sin(a) * v - 40;
            s.max = s.life = 0.7F + RANDOM.nextFloat() * 0.7F;
            s.color = RANDOM.nextInt(3) == 0 ? 0xFFFFFF : RANDOM.nextInt(3) == 0 ? 0xFFE680 : col & 0xFFFFFF;
            s.size = RANDOM.nextInt(4) == 0 ? 2 : 1;
            sparks.add(s);
        }
    }

    /** De 1 (la más común) a 4 (la más rara), por la probabilidad de cada rareza. */
    private int rank(String id) {
        List<JsonObject> rs = new ArrayList<>(list(data, "rarezas"));
        if (rs.size() < 2) return 1;
        rs.sort(Comparator.comparingDouble(r -> -dec(r, "p", 0)));
        for (int i = 0; i < rs.size(); i++) {
            if (str(rs.get(i), "id", "").equals(id)) return 1 + Math.round(i * 3F / (rs.size() - 1));
        }
        return 1;
    }

    private int rarityColor(String id) {
        for (JsonObject r : list(data, "rarezas")) if (str(r, "id", "").equals(id)) return 0xFF000000 | (int) num(r, "c", 0x8FA3BF);
        return 0xFF8FA3BF;
    }

    private String rarityName(String id) {
        for (JsonObject r : list(data, "rarezas")) if (str(r, "id", "").equals(id)) return str(r, "n", id);
        return id;
    }

    // ---------------------------------------------------------------------------------------------------------------

    @Override
    void render(GuiGraphics g, double mx, double my, float partial) {
        hits.clear();
        if (loading(g)) return;
        long ns = System.nanoTime();
        float dt = Math.min(0.1F, (ns - lastNs) / 1e9F);
        lastNs = ns;
        advance(dt);
        kick = Math.max(0, kick - dt * 9);
        drawTabs(g, mx, my);
        int top = Y + PadSkin.TABS, bottom = Y + H;
        switch (tab) {
            case "premios" -> drawPrizes(g, mx, my, top, bottom);
            case "historial" -> drawHistory(g, mx, my, top, bottom);
            default -> drawStage(g, mx, my, top, bottom, dt);
        }
    }

    private void drawTabs(GuiGraphics g, double mx, double my) {
        String[][] tabs = {{"ruleta", "RULETA"}, {"premios", "PREMIOS"}, {"historial", "HISTORIAL"}};
        int sel = 0;
        for (int i = 0; i < tabs.length; i++) if (tabs[i][0].equals(tab)) sel = i;
        int[][] at = PadSkin.tabRow(g, pad, X + 4, Y, W - 8, List.of(tabs[0][1], tabs[1][1], tabs[2][1]), sel, mx, my, PadSkin.accent(app));
        for (int i = 0; i < tabs.length; i++) {
            String key = tabs[i][0];
            hit(at[i][0], Y - 1, at[i][1], PadSkin.TAB_H + 1, () -> {
                if (!key.equals(tab)) {
                    tab = key;
                    listScroll = 0;
                    pad.sound("tab", 0.6F);
                }
            });
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // La ruleta
    // ---------------------------------------------------------------------------------------------------------------

    private void drawStage(GuiGraphics g, double mx, double my, int top, int bottom, float dt) {
        int x = X, w = W, h = bottom - top;
        long now = System.currentTimeMillis();
        float t = PadSkin.time();
        boolean active = data.has("activo") && data.get("activo").getAsBoolean();
        JsonObject best = revealed && !result.isEmpty() ? result.get(resultBest) : null;
        int bestColor = best == null ? 0 : rarityColor(str(best, "r", ""));
        int bestRank = best == null ? 0 : rank(str(best, "r", ""));
        float since = revealed ? (now - revealAt) / 1000F : 99F;
        // las rarezas altas hacen temblar el escenario un momento
        float shake = PadSettings.animations && bestRank >= 3 && since < 0.45F ? (1 - since / 0.45F) * (bestRank == 4 ? 2.4F : 1.4F) : 0;
        g.pose().pushPose();
        if (shake > 0) g.pose().translate((float) Math.sin(now / 17.0) * shake, (float) Math.cos(now / 23.0) * shake, 0);
        // el escenario: violeta noche, focos que giran y un charco de luz en el centro
        PadUi.box(g, x, top, w, h, 0xFF0A0620);
        g.fillGradient(x + 1, top + 1, x + w - 1, top + h - 1, 0xFF2B1747, 0xFF100A26);
        pad.scissor(g, x + 1, top + 1, w - 2, h - 2);
        int cx = x + w / 2;
        PadSkin.rays(g, cx, top + h * 0.42F, w * 0.75F, revealed ? 0.22F : 0.10F, t * (spinning() ? 40 : 6));
        PadSkin.glow(g, cx, top + h * 0.42F, h * 0.85F, best != null ? bestColor & 0xFFFFFF : PadSkin.accent(app) & 0xFFFFFF, best != null ? 120 : 70);
        PadSkin.stripes(g, x + 1, top + 1, w - 2, h - 2, 0.04F, t * 4);
        pad.noScissor(g);
        g.fill(x + 2, top + 1, x + w - 2, top + 2, 0x55FFFFFF);
        // arriba: tus monedas verdes y la garantía
        long greens = num(data, "verdes", 0);
        int hy = top + 5;
        pad.blit(g, "coin_green", x + 7, hy - 1);
        PadFont.drawShadow(g, PadUi.THOUSANDS.format(greens), x + 21, hy, 0x8CF0B4, 0x0A3A20);
        PadUi.text(g, greens == 1 ? "Fantastic Gacha Coin" : "Fantastic Gacha Coins", x + 25 + PadFont.width(PadUi.THOUSANDS.format(greens)), hy + 1, 0xFF8C96C8);
        drawPity(g, x + w - 7, hy);
        // el carrete
        int rh = Math.max(44, Math.min(70, h - 22 - 28 - 40));
        cardH = rh - 10;
        cardW = Math.round(cardH * 0.8F);
        pitch = cardW + 6;
        int rx = x + 6, rw = w - 12, ry = top + 20;
        drawReel(g, rx, ry, rw, rh, best, bestColor, since);
        // abajo: los botones; en medio, lo que te tocó o cómo se juega
        int by = bottom - 21;
        int zoneTop = ry + rh + 5, zoneBot = by - 4;
        if (best != null) drawResult(g, mx, my, x, w, zoneTop, zoneBot, best, bestColor, since);
        else drawHowTo(g, x, w, zoneTop, zoneBot, active);
        // las chispas (por encima de todo, dentro del escenario)
        drawSparks(g, cx, ry + rh / 2F, dt);
        drawButtons(g, mx, my, x, w, by, active, greens);
        g.pose().popPose();
    }

    /** «ÉPICO ASEGURADO» y una barra de pasos: cuántas tiradas llevas sin una de esa rareza. Alineada a la derecha en x. */
    private void drawPity(GuiGraphics g, int right, int y) {
        JsonObject pity = data.has("garantia") && data.get("garantia").isJsonObject() ? data.getAsJsonObject("garantia") : null;
        if (pity == null) return;
        int every = (int) Math.max(1, Math.min(20, num(pity, "cada", 10)));
        int left = (int) Math.max(1, num(pity, "faltan", every));
        int done = Math.max(0, every - left);
        int col = 0xFF000000 | (int) num(pity, "color", 0xA855F7);
        int seg = 5, gap = 1, bw = every * (seg + gap) - gap;
        int bx = right - bw;
        for (int i = 0; i < every; i++) {
            int sx = bx + i * (seg + gap);
            boolean on = i < done;
            g.fill(sx, y + 1, sx + seg, y + 8, on ? col : 0xFF2A2350);
            if (on) g.fill(sx, y + 1, sx + seg, y + 2, PadSkin.lighten(col, 60));
        }
        String label = PadFont.upper(str(pity, "rareza", "")) + (left <= 1 ? " EN LA PRÓXIMA" : " EN " + left);
        int lw = PadFont.width(label);
        PadFont.drawShadow(g, label, bx - 5 - lw, y, PadUi.lighten(col, 50) & 0xFFFFFF, 0x0A0620);
    }

    private void drawReel(GuiGraphics g, int rx, int ry, int rw, int rh, JsonObject best, int bestColor, float since) {
        long now = System.currentTimeMillis();
        PadUi.box(g, rx - 1, ry - 1, rw + 2, rh + 2, 0xFF05030F);
        g.fillGradient(rx, ry, rx + rw, ry + rh, 0xFF0B0820, 0xFF1A1238);
        int center = rx + rw / 2, cy = ry + rh / 2;
        pad.scissor(g, rx, ry, rw, rh);
        // el hueco del centro, iluminado
        PadSkin.glow(g, center, cy, rh * 0.9F, best != null ? bestColor & 0xFFFFFF : 0xFFE680, best != null ? 150 : 60);
        if (!strip.isEmpty()) {
            float half = rw / 2F;
            int firstCard = (int) Math.floor((offset - half) / pitch) - 1;
            int lastCard = firstCard + rw / pitch + 3;
            // primero las de los lados (más pequeñas), al final la del centro: queda por encima
            List<int[]> order = new ArrayList<>();
            for (int i = firstCard; i <= lastCard; i++) order.add(new int[] {i});
            order.sort(Comparator.comparingDouble(a -> -Math.abs(a[0] * pitch - offset)));
            for (int[] o : order) {
                int i = o[0];
                if (spinStart >= 0 && (i < 0 || i >= strip.size())) continue;
                float dx = i * pitch - offset;
                float d = Math.min(1F, Math.abs(dx) / half);
                float sc = 1 - 0.24F * d;
                boolean win = best != null && i == WIN_INDEX && spinStart >= 0;
                if (win) sc *= 1 + 0.16F * popCurve(since);
                drawCard(g, strip.get(Math.floorMod(i, strip.size())), center + dx, cy, sc, d, win, since);
            }
            // tic en cada premio que pasa por la flecha (más agudo cuanto más rápido) y la lengüeta salta
            int under = Math.round(offset / pitch);
            if (under != lastTickCard) {
                if ((spinning() || revAt >= 0) && now - lastTickAt > 28) {
                    lastTickAt = now;
                    float pitchK = 0.85F + Math.min(0.55F, speed / 2600F);
                    pad.sound("gacha_tick", 0.55F, pitchK);
                    kick = 1;
                }
                lastTickCard = under;
            }
        } else {
            PadFont.drawCentered(g, "SIN PREMIOS", center, cy - 5, 0x8C96C8, false);
        }
        // estela de velocidad: rayas de luz que cruzan el carrete
        if (speed > 300 && PadSettings.animations) {
            float k = Math.min(1F, (speed - 300) / 1800F);
            for (int i = 0; i < 9; i++) {
                float ly = ry + 4 + (i * 37 % (rh - 8));
                float len = 20 + (i * 53 % 50) * k;
                float lx = rx + ((i * 97 + now * (0.6F + i * 0.07F)) % (rw + len)) - len;
                PadSkin.hgrad(g, lx, ly, lx + len, ly + 1, 0x00FFFFFF, PadSkin.alpha(0xFFFFFF, Math.round(110 * k)));
            }
        }
        // destello al salir el premio
        if (best != null && since < 0.35F) {
            PadSkin.rect(g, rx, ry, rx + rw, ry + rh, PadSkin.alpha(PadUi.lighten(bestColor, 80) & 0xFFFFFF, Math.round(170 * (1 - since / 0.35F))));
        }
        // los lados se funden con el fondo (la tira entra y sale)
        int fade = Math.min(40, rw / 6);
        PadSkin.hgrad(g, rx, ry, rx + fade, ry + rh, 0xF00B0820, 0x000B0820);
        PadSkin.hgrad(g, rx + rw - fade, ry, rx + rw, ry + rh, 0x000B0820, 0xF00B0820);
        g.flush();
        if (best != null) {
            int[][] sp = {{-34, 6}, {30, 9}, {-26, rh - 9}, {36, rh - 13}, {0, 3}, {-48, rh / 2}, {50, rh / 2 - 6}};
            for (int i = 0; i < sp.length; i++) {
                PadSkin.sparkle(g, center + sp[i][0], ry + sp[i][1], 2, PadSkin.twinkle(i * 1.3F + 0.2F), 0xFFFFFF);
            }
        }
        pad.noScissor(g);
        // el marco de oro del centro y la lengüeta (salta con cada premio)
        int fw = cardW + 8, fh = cardH + 6;
        int fx = center - fw / 2, fy = cy - fh / 2;
        int frame = best != null ? PadUi.lighten(bestColor, 40) : PadUi.GOLD;
        outline(g, fx, fy, fw, fh, PadSkin.alpha(frame & 0xFFFFFF, 220));
        outline(g, fx - 1, fy - 1, fw + 2, fh + 2, PadSkin.alpha(frame & 0xFFFFFF, 80));
        float k = PadSettings.animations ? kick : 0;
        float tipX = center - k * 3, tipY = ry + 7 - k * 1.5F;
        PadSkin.tri(g, center - 5, ry - 3, center + 6, ry - 3, tipX + 0.5F, tipY, PadUi.GOLD_LO);
        PadSkin.tri(g, center - 4, ry - 3, center + 5, ry - 3, tipX + 0.5F, tipY - 1.5F, PadUi.GOLD);
        PadSkin.rect(g, center - 5, ry - 5, center + 6, ry - 2, PadUi.GOLD_HI);
        PadSkin.tri(g, center - 4, ry + rh + 3, center + 5, ry + rh + 3, center + 0.5F, ry + rh - 4, PadUi.GOLD);
        g.flush();
    }

    /** Un marco de 1 de grueso (sin relleno). */
    private static void outline(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x + 1, y, x + w - 1, y + 1, c);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, c);
        g.fill(x, y + 1, x + 1, y + h - 1, c);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, c);
    }

    /** El salto de la tarjeta ganadora: sube rápido, se pasa y vuelve (0 a 1, luego late un poco). */
    private static float popCurve(float s) {
        if (!PadSettings.animations) return 0.6F;
        if (s < 0.3F) {
            float x = s / 0.3F - 1;
            return 1 + 2.7F * x * x * x + 1.7F * x * x;
        }
        return 0.75F + 0.25F * (float) Math.cos((s - 0.3F) * 5);
    }

    /**
     * Una tarjeta del carrete con su centro en (cx, cy), a escala sc: marco del color de su rareza, el objeto al doble y
     * la cantidad abajo. d (0 en el centro, 1 en el borde): las de los lados, en sombra.
     */
    private void drawCard(GuiGraphics g, JsonObject p, float cx, float cy, float sc, float d, boolean win, float since) {
        int col = rarityColor(str(p, "r", ""));
        int w = cardW, h = cardH;
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(sc, sc, 1);
        int x = -w / 2, y = -h / 2;
        if (win) {
            float pulse = PadSettings.animations ? 0.5F + 0.5F * (float) Math.sin(since * 7) : 1;
            PadUi.box(g, x - 3, y - 3, w + 6, h + 6, PadSkin.alpha(col & 0xFFFFFF, Math.round(110 + 120 * pulse)));
            PadUi.box(g, x - 2, y - 2, w + 4, h + 4, PadSkin.alpha(PadUi.lighten(col, 70) & 0xFFFFFF, 200));
        }
        PadUi.box(g, x, y, w, h, col);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, PadUi.tint(col, 0.5F), PadUi.tint(col, 0.88F));
        g.fill(x + 2, y + 1, x + w - 2, y + 2, 0x99FFFFFF);
        PadUi.stretch(g, "glow", x + 2, y + 3, w - 4, w - 6);
        ItemStack s = stack(p);
        int big = h >= 50 ? 2 : 1;
        PadUi.item(g, s, -8 * big, y + 4 + (h - 14 - 16 * big) / 2, big);
        String type = str(p, "tipo", "objeto");
        String q = type.equals("objeto") ? "" : shortAmount(num(p, "n", 1));
        g.fill(x + 1, y + h - 11, x + w - 1, y + h - 1, 0xDD000000 | (darken(col, 0.5F) & 0xFFFFFF));
        if (!q.isEmpty()) PadFont.drawCentered(g, q, 0, y + h - 11, 0xFFFFFF, true);
        // las de los lados, en sombra
        if (d > 0.05F) g.fill(x, y, x + w, y + h, PadSkin.alpha(0x0B0820, Math.round(170 * d)));
        g.pose().popPose();
    }

    /** Lo que te tocó: la rareza en grande (entra de golpe) y el nombre; en las de 5, las cinco cartas volteándose. */
    private void drawResult(GuiGraphics g, double mx, double my, int x, int w, int zoneTop, int zoneBot, JsonObject best, int col, float since) {
        boolean many = result.size() > 1;
        String rn = PadFont.upper(rarityName(str(best, "r", "")));
        String title = many ? "MEJOR: " + rn : "¡" + rn + "!";
        float in = PadSettings.animations ? Math.min(1F, since / 0.22F) : 1F;
        float sc = 2 + (1 - in) * 1.6F;
        int tw = PadFont.width(title);
        int cx = x + w / 2;
        int ty = zoneTop + 2;
        if (zoneBot - zoneTop >= 32) {
            g.pose().pushPose();
            g.pose().translate(cx, ty + 9, 0);
            g.pose().scale(sc, sc, 1);
            PadFont.drawShadow(g, title, -tw / 2, -5, PadUi.lighten(col, 40) & 0xFFFFFF, darken(col, 0.35F) & 0xFFFFFF);
            g.pose().popPose();
            ty += 22;
        } else {
            PadFont.drawShadow(g, title, cx - tw / 2, ty, PadUi.lighten(col, 40) & 0xFFFFFF, darken(col, 0.35F) & 0xFFFFFF);
            ty += 12;
        }
        String nm = PadUi.fitEnd(name(best), w - 20);
        if (ty + 9 <= zoneBot) PadUi.text(g, nm, cx - PadUi.font().width(nm) / 2, ty, 0xFFFFFFFF);
        ty += 12;
        if (!many) return;
        // las cinco cartas, volteándose una a una
        int s = 22, gap = 5, n = result.size();
        int rowW = n * s + (n - 1) * gap, sx = cx - rowW / 2;
        if (ty + s > zoneBot) return;
        for (int i = 0; i < n; i++) {
            float k = PadSettings.animations ? Math.min(1F, Math.max(0F, (since - 0.15F - i * 0.16F) / 0.14F)) : 1F;
            if (k > 0 && k < 1 && !flipped[Math.min(flipped.length - 1, i)]) {
                flipped[Math.min(flipped.length - 1, i)] = true;
                pad.sound("gacha_flip", 0.5F, 0.9F + i * 0.06F);
            }
            JsonObject p = result.get(i);
            int pc = rarityColor(str(p, "r", ""));
            int bx = sx + i * (s + gap);
            g.pose().pushPose();
            g.pose().translate(bx + s / 2F, ty + s / 2F, 0);
            g.pose().scale(Math.abs(k * 2 - 1), 1, 1); // se da la vuelta: se estrecha y vuelve a abrirse
            if (k < 0.5F) {
                PadUi.box(g, -s / 2, -s / 2, s, s, 0xFF4A3A8A);
                g.fillGradient(-s / 2 + 1, -s / 2 + 1, s / 2 - 1, s / 2 - 1, 0xFF3A2A70, 0xFF1E1440);
                PadFont.drawCentered(g, "?", 0, -5, 0xB8A8F0, true);
            } else {
                PadUi.slot(g, -s / 2, -s / 2, s, s, pc);
                PadUi.item(g, stack(p), -8, -8, 1);
            }
            g.pose().popPose();
            if (k >= 1 && i == resultBest) {
                outline(g, bx - 2, ty - 2, s + 4, s + 4, PadSkin.alpha(PadUi.GOLD_HI & 0xFFFFFF, 230));
                outline(g, bx - 1, ty - 1, s + 2, s + 2, PadSkin.alpha(PadUi.GOLD & 0xFFFFFF, 200));
                PadSkin.sparkle(g, bx + s, ty, 2, PadSkin.twinkle(i), 0xFFE680);
            }
            if (PadUi.inside(mx, my, bx, ty, s, s)) pad.hover("§res" + i);
        }
    }

    private final boolean[] flipped = new boolean[16];

    /** Sin girar: el precio y la barra con la probabilidad de cada rareza. */
    private void drawHowTo(GuiGraphics g, int x, int w, int zoneTop, int zoneBot, boolean active) {
        java.util.Arrays.fill(flipped, false);
        String how = active ? "1 tirada: " + num(data, "precio", 1) + "  ·  " + num(data, "multi", 5) + " tiradas: " + num(data, "precioMulti", 5)
                + "  ·  ESPACIO para girar" : "El Gachapón está cerrado ahora.";
        how = PadUi.fitEnd(how, w - 20);
        int cx = x + w / 2;
        PadUi.text(g, how, cx - PadUi.font().width(how) / 2, zoneTop + 2, 0xFFB8C2F0);
        List<JsonObject> rs = list(data, "rarezas");
        if (rs.isEmpty() || zoneBot - zoneTop < 30) return;
        double total = 0;
        for (JsonObject r : rs) total += Math.max(0, dec(r, "p", 0));
        if (total <= 0) return;
        int bw = Math.min(w - 40, 300), bx = cx - bw / 2, by = zoneTop + 16;
        PadUi.box(g, bx - 1, by - 1, bw + 2, 8, 0xFF05030F);
        float at = bx;
        for (JsonObject r : rs) {
            float sw = (float) (bw * Math.max(0, dec(r, "p", 0)) / total);
            int c = 0xFF000000 | (int) num(r, "c", 0x8FA3BF);
            PadSkin.rect(g, at, by, at + sw, by + 6, c);
            PadSkin.rect(g, at, by, at + sw, by + 1, PadSkin.lighten(c, 60));
            at += sw;
        }
        g.flush();
        // la leyenda: cada rareza con su color y su probabilidad, en una línea centrada
        int ly = by + 10;
        if (ly + 9 > zoneBot) return;
        int lw = 0;
        for (JsonObject r : rs) lw += 8 + PadUi.font().width(str(r, "n", "") + " " + pct(dec(r, "p", 0))) + 10;
        int lx = cx - lw / 2;
        for (JsonObject r : rs) {
            int c = 0xFF000000 | (int) num(r, "c", 0x8FA3BF);
            String label = str(r, "n", "") + " " + pct(dec(r, "p", 0));
            g.fill(lx, ly + 1, lx + 5, ly + 6, c);
            PadUi.text(g, label, lx + 8, ly, PadUi.lighten(c, 70));
            lx += 8 + PadUi.font().width(label) + 10;
        }
    }

    private void drawSparks(GuiGraphics g, float ox, float oy, float dt) {
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
            s.vy += 160 * dt;
            s.vx *= 1 - 0.6F * dt;
            float a = Math.min(1F, s.life / s.max * 1.6F);
            PadSkin.rect(g, ox + s.x, oy + s.y, ox + s.x + s.size, oy + s.y + s.size, PadSkin.alpha(s.color, Math.round(255 * a)));
        }
        g.flush();
    }

    private void drawButtons(GuiGraphics g, double mx, double my, int x, int w, int by, boolean active, long greens) {
        boolean busy = spinning() || waiting || revAt >= 0;
        boolean can1 = active && !busy && greens >= num(data, "precio", 1);
        boolean can5 = active && !busy && greens >= num(data, "precioMulti", 5);
        int w1 = buttonWidth("GIRAR", num(data, "precio", 1));
        int w5 = buttonWidth("GIRAR X" + num(data, "multi", 5), num(data, "precioMulti", 5));
        int bx = x + (w - w1 - w5 - 10) / 2;
        priceButton(g, mx, my, bx, by, "GIRAR", num(data, "precio", 1), PadView.GREEN, can1, "§girar", () -> spin("girar"));
        priceButton(g, mx, my, bx + w1 + 10, by, "GIRAR X" + num(data, "multi", 5), num(data, "precioMulti", 5), PadView.GOLD, can5,
                "§girar5", () -> spin("girarMulti"));
        if (active && greens < num(data, "precio", 1)) {
            String need = "Te faltan Fantastic Gacha Coins: se ganan en el TF Pass";
            PadUi.text(g, need, x + (w - PadUi.font().width(need)) / 2, by - 11, 0xFFE07890);
        }
    }

    private void spin(String action) {
        if (spinning() || waiting || revAt >= 0) return;
        send(action);
        revAt = System.currentTimeMillis();
        spinStart = -1;
        revealed = stopped = false;
        result = List.of();
        sparks.clear();
        java.util.Arrays.fill(flipped, false);
        pad.sound("gacha_start", 0.8F);
    }

    /** El color oscurecido (k = 1 tal cual, 0 negro). */
    static int darken(int c, float k) {
        int r = Math.round((c >> 16 & 255) * k), gg = Math.round((c >> 8 & 255) * k), b = Math.round((c & 255) * k);
        return 0xFF000000 | r << 16 | gg << 8 | b;
    }

    private static String shortAmount(long n) {
        if (n >= 1_000_000) return (n / 1_000_000) + "M";
        if (n >= 10_000) return (n / 1000) + "K";
        return PadUi.THOUSANDS.format(n);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Premios e historial
    // ---------------------------------------------------------------------------------------------------------------

    private static final int PRIZE_W = 84, PRIZE_H = 62, ROW = 24;
    private int contentH;

    /** Todos los premios, por rareza (de la más rara a la más común): un título con su color y sus tarjetas. */
    private void drawPrizes(GuiGraphics g, double mx, double my, int top, int bottom) {
        int acc = PadSkin.accent(app);
        PadSkin.window(g, X, top, W, bottom - top, acc);
        int inner = bottom - top - 6;
        int cols = Math.max(1, (W - 16) / PRIZE_W);
        int cw = (W - 16) / cols;
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, contentH - inner)));
        pad.scissor(g, X + 2, top + 3, W - 4, inner);
        int y = top + 5 - listScroll;
        String current = null;
        int col = 0;
        List<JsonObject> ps = list(data, "premios");
        for (JsonObject p : ps) {
            String r = str(p, "r", "");
            if (!r.equals(current)) {
                if (current != null && col > 0) y += PRIZE_H + 4;
                current = r;
                col = 0;
                int c = rarityColor(r);
                double sum = 0;
                for (JsonObject q : list(data, "rarezas")) if (str(q, "id", "").equals(r)) sum = dec(q, "p", 0);
                g.fill(X + 6, y + 4, X + 8, y + 12, c);
                PadFont.drawShadow(g, PadFont.upper(rarityName(r)), X + 12, y + 3, PadUi.lighten(c, 10) & 0xFFFFFF, darken(c, 0.4F) & 0xFFFFFF);
                String pc = pct(sum);
                PadUi.text(g, pc, X + W - 12 - PadUi.font().width(pc), y + 4, PadUi.MUTED);
                g.fill(X + 14 + PadFont.width(PadFont.upper(rarityName(r))) + 4, y + 8, X + W - 18 - PadUi.font().width(pc), y + 9, PadSkin.alpha(c & 0xFFFFFF, 90));
                y += 16;
            }
            int px = X + 8 + col * cw + (cw - PRIZE_W + 4) / 2;
            if (y + PRIZE_H > top && y < bottom) drawPrize(g, p, px, y, PRIZE_W - 4, PRIZE_H, mx, my);
            col++;
            if (col >= cols) {
                col = 0;
                y += PRIZE_H + 4;
            }
        }
        if (col > 0) y += PRIZE_H + 4;
        contentH = y + listScroll - (top + 5);
        pad.noScissor(g);
        if (ps.isEmpty()) {
            String msg = "Sin premios.";
            PadUi.text(g, msg, X + (W - PadUi.font().width(msg)) / 2, top + (bottom - top) / 2 - 4, PadUi.MUTED);
        }
        PadSkin.scrollbar(g, X + W - 7, top + 4, inner - 2, inner, contentH, listScroll, acc);
        PadUi.grabbable(X + W - 7, top + 4, inner - 2, inner, contentH, () -> listScroll, v -> listScroll = v);
    }

    private void drawPrize(GuiGraphics g, JsonObject p, int x, int y, int w, int h, double mx, double my) {
        int col = rarityColor(str(p, "r", ""));
        boolean hover = PadUi.inside(mx, my, x, y, w, h);
        PadUi.box(g, x, y, w, h, hover ? PadUi.lighten(col, 30) : col);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + 38, PadUi.tint(col, 0.55F), PadUi.tint(col, 0.85F));
        g.fill(x + 1, y + 38, x + w - 1, y + h - 1, 0xFFF4F7FC);
        g.fill(x + 2, y + 1, x + w - 2, y + 2, 0x99FFFFFF);
        PadUi.stretch(g, "glow", x + w / 2 - 17, y + 2, 34, 34);
        PadUi.item(g, stack(p), x + w / 2 - 16, y + 3, 2);
        String nm = PadUi.fitEnd(name(p), w - 6);
        PadUi.text(g, nm, x + (w - PadUi.font().width(nm)) / 2, y + 41, PadUi.TEXT);
        String pc = pct(dec(p, "p", 0));
        PadUi.text(g, pc, x + (w - PadUi.font().width(pc)) / 2, y + 51, darken(col, 0.7F));
        if (hover) pad.hover("§premio" + name(p));
    }

    private void drawHistory(GuiGraphics g, double mx, double my, int top, int bottom) {
        int acc = PadSkin.accent(app);
        PadSkin.window(g, X, top, W, bottom - top, acc);
        List<JsonObject> rows = list(data, "historial");
        int inner = bottom - top - 8;
        contentH = rows.size() * ROW;
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, contentH - inner)));
        pad.scissor(g, X + 2, top + 4, W - 4, inner);
        int ry = top + 4 - listScroll;
        long now = System.currentTimeMillis();
        for (JsonObject p : rows) {
            if (ry + ROW > top && ry < bottom) drawHistoryRow(g, p, X + 6, ry, W - 18, now);
            ry += ROW;
        }
        pad.noScissor(g);
        if (rows.isEmpty()) {
            String msg = "Aún no has girado.";
            PadUi.text(g, msg, X + (W - PadUi.font().width(msg)) / 2, top + (bottom - top) / 2 - 4, PadUi.MUTED);
        }
        PadSkin.scrollbar(g, X + W - 7, top + 5, inner - 2, inner, contentH, listScroll, acc);
        PadUi.grabbable(X + W - 7, top + 5, inner - 2, inner, contentH, () -> listScroll, v -> listScroll = v);
    }

    private void drawHistoryRow(GuiGraphics g, JsonObject p, int x, int y, int w, long now) {
        int col = rarityColor(str(p, "r", ""));
        g.fill(x + 3, y + ROW - 1, x + w - 3, y + ROW, 0xFFC8E4F8);
        g.fillGradient(x, y + 3, x + 2, y + ROW - 3, PadSkin.lighten(col, 50), col);
        PadSkin.medallion(g, x + 4, y + 2, 20, col);
        PadUi.item(g, stack(p), x + 6, y + 4, 1);
        int tx = x + 29;
        String right = ago(now - num(p, "t", now));
        int rw = PadUi.font().width(right);
        PadUi.text(g, right, x + w - 4 - rw, y + 8, PadUi.MUTED);
        int room = w - 29 - rw - 10;
        PadUi.text(g, PadUi.fitEnd(name(p), room), tx, y + 3, PadUi.TEXT);
        PadUi.text(g, PadUi.fitEnd(rarityName(str(p, "r", "")), room), tx, y + 13, darken(col, 0.7F));
    }

    private static String ago(long ms) {
        long m = ms / 60000;
        if (m < 1) return "ahora";
        if (m < 60) return "hace " + m + " min";
        if (m < 60 * 24) return "hace " + m / 60 + " h";
        return "hace " + m / 1440 + " d";
    }

    @Override
    boolean scroll(double mx, double my, double delta) {
        if (!tab.equals("ruleta")) listScroll -= (int) Math.signum(delta) * ROW;
        return true;
    }

    @Override
    boolean drag(double mx, double my, double dy) {
        if (tab.equals("ruleta")) return false;
        listScroll += (int) Math.round(dy);
        return true;
    }

    @Override
    boolean key(int key, int scan, int mods) {
        if (key == 32 && tab.equals("ruleta")) { // espacio: girar una
            spin("girar");
            return true;
        }
        return false;
    }
}
