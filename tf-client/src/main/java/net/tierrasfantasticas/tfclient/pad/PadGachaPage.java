package net.tierrasfantasticas.tfclient.pad;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * El Gachapón (1.3.38): a la izquierda, la máquina (tarjeta oscura como el reproductor de Música): tus monedas verdes,
 * la ruleta (una tira de premios que pasa por la ventana y se para en el que te tocó, debajo de la flecha de oro), lo
 * que te tocó, la garantía («Épico o mejor asegurado en 7 tiradas») y los botones GIRAR (1) y GIRAR x5. A la derecha,
 * pestañas PREMIOS (todos, con su rareza y su probabilidad) e HISTORIAL (lo último que te tocó).
 * <p>
 * El servidor tira, cobra y da el premio; aquí solo se anima: la tira se monta con premios al azar (por su
 * probabilidad) y el ganador en su sitio, y se frena con una curva suave en ~4,5 s, con un tic en cada premio que pasa.
 */
final class PadGachaPage extends PadDataPage {
    private static final int CARD_W = 40, CARD_H = 50, PITCH = 44, SPIN_MS = 4600, WIN_INDEX = 38;
    private static final Random RANDOM = new Random();

    private String tab = "premios";
    private int listScroll;
    /** La tira de la ruleta: premios con su rareza. */
    private final List<JsonObject> strip = new ArrayList<>();
    private long spinStart = -1, lastEvent;
    private float spinFrom, spinTo, idleOffset;
    private int lastTickCard = -1;
    private long lastTickAt;
    private List<JsonObject> result = List.of();
    private int resultBest;
    private boolean revealed;
    private long revealAt;
    private long lastNs = System.nanoTime();

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
        if (ev == null || num(ev, "id", 0) == lastEvent) return;
        lastEvent = num(ev, "id", 0);
        result = list(ev, "premios");
        if (result.isEmpty()) return;
        resultBest = (int) Math.max(0, Math.min(result.size() - 1, num(ev, "mejor", 0)));
        // la tira: lo que hay ahora en la ventana sigue igual, luego premios al azar y el ganador en WIN_INDEX
        float now = currentOffset();
        // 6 tarjetas antes de la que está en la flecha: así la ventana se ve igual al empezar a girar
        int first = (int) Math.floor(now / PITCH) - 6;
        List<JsonObject> keep = new ArrayList<>();
        for (int i = first; i < first + 14; i++) if (!strip.isEmpty()) keep.add(strip.get(Math.floorMod(i, strip.size())));
        strip.clear();
        strip.addAll(keep);
        while (strip.size() < WIN_INDEX + 8) strip.add(randomPrize());
        strip.set(WIN_INDEX, result.get(resultBest));
        spinFrom = now - first * (float) PITCH;
        spinTo = WIN_INDEX * PITCH + (RANDOM.nextFloat() - 0.5F) * (CARD_W - 12);
        spinStart = System.currentTimeMillis();
        revealed = false;
        lastTickCard = -1;
        pad.sound("select", 0.7F);
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
        return spinStart >= 0 && System.currentTimeMillis() - spinStart < SPIN_MS;
    }

    private float currentOffset() {
        if (spinStart < 0) return idleOffset;
        float t = Math.min(1F, (System.currentTimeMillis() - spinStart) / (float) SPIN_MS);
        float e = 1 - (float) Math.pow(1 - t, 4); // frena suave
        return spinFrom + (spinTo - spinFrom) * e;
    }

    private int rarityColor(String id) {
        for (JsonObject r : list(data, "rarezas")) if (str(r, "id", "").equals(id)) return 0xFF000000 | (int) num(r, "c", 0x8FA3BF);
        return 0xFF8FA3BF;
    }

    private String rarityName(String id) {
        for (JsonObject r : list(data, "rarezas")) if (str(r, "id", "").equals(id)) return str(r, "n", id);
        return id;
    }

    private int leftW() {
        return Math.max(210, Math.min(270, Math.round(W * 0.56F)));
    }

    // ---------------------------------------------------------------------------------------------------------------

    @Override
    void render(GuiGraphics g, double mx, double my, float partial) {
        hits.clear();
        if (loading(g)) return;
        long ns = System.nanoTime();
        float dt = Math.min(0.1F, (ns - lastNs) / 1e9F);
        lastNs = ns;
        if (spinStart < 0 && PadSettings.animations) idleOffset += dt * 7;
        if (spinStart >= 0 && !spinning() && !revealed) {
            revealed = true;
            revealAt = System.currentTimeMillis();
            pad.sound("like", 0.9F);
        }
        drawMachine(g, mx, my);
        drawList(g, mx, my);
    }

    private void drawMachine(GuiGraphics g, double mx, double my) {
        int x = X, w = leftW(), top = Y + 14, bottom = Y + H;
        darkCard(g, x, top, w, bottom, Y, "RULETA");
        boolean active = data.has("activo") && data.get("activo").getAsBoolean();
        // arriba: tus monedas verdes y el precio
        long greens = num(data, "verdes", 0);
        pad.blit(g, "coin_green", x + 8, top + 6);
        String bal = PadUi.THOUSANDS.format(greens) + (greens == 1 ? " moneda verde" : " monedas verdes");
        PadUi.text(g, bal, x + 22, top + 8, 0xFF8CF0B4);
        // de dónde salen (solo si cabe entero: nada de textos cortados)
        String hint = "Se ganan en el TF Pass";
        if (PadUi.font().width(bal) + PadUi.font().width(hint) + 40 <= w) {
            PadUi.text(g, hint, x + w - 8 - PadUi.font().width(hint), top + 8, 0xFF8C96C8);
        }
        // abajo van los botones y la garantía; la ruleta, lo que te tocó y las demás tiradas, centrados en lo que queda
        JsonObject pity = data.has("garantia") && data.get("garantia").isJsonObject() ? data.getAsJsonObject("garantia") : null;
        int by = bottom - 6 - 16, py = by - 13;
        boolean many = revealed && result.size() > 1;
        int rh = CARD_H + 8, blockH = rh + 6 + 10 + (many ? 26 : 0);
        int zoneTop = top + 20, zoneBot = (pity != null ? py : by) - 4;
        // la ventana de la ruleta
        int rx = x + 8, ry = zoneTop + Math.max(2, (zoneBot - zoneTop - blockH) / 2), rw = w - 16;
        PadUi.box(g, rx - 1, ry - 1, rw + 2, rh + 2, 0xFF0C1030);
        g.fillGradient(rx, ry, rx + rw, ry + rh, 0xFF0E1432, 0xFF1C2452);
        float off = currentOffset();
        int center = rx + rw / 2;
        pad.scissor(g, rx, ry, rw, rh);
        if (!strip.isEmpty()) {
            int firstCard = (int) Math.floor((off - rw / 2F) / PITCH) - 1;
            for (int i = firstCard; i < firstCard + rw / PITCH + 4; i++) {
                int idx = Math.floorMod(i, strip.size());
                if (spinStart >= 0 && (i < 0 || i >= strip.size())) continue;
                int cx = Math.round(center + i * PITCH - off);
                boolean win = revealed && i == WIN_INDEX;
                drawCard(g, strip.get(idx), cx - CARD_W / 2, ry + 4, win);
            }
            // tic en cada premio que pasa por la flecha
            if (spinning()) {
                int under = Math.round(off / PITCH);
                if (under != lastTickCard && System.currentTimeMillis() - lastTickAt > 45) {
                    lastTickCard = under;
                    lastTickAt = System.currentTimeMillis();
                    pad.sound("hover", 0.45F);
                }
            }
        } else {
            PadFont.drawCentered(g, "SIN PREMIOS", center, ry + rh / 2 - 5, 0x8C96C8, false);
        }
        // sombra en los lados de la ventana (la tira entra y sale)
        for (int i = 0; i < 16; i++) {
            int a = (int) (150 * (1 - i / 16F)) << 24;
            g.fill(rx + i, ry, rx + i + 1, ry + rh, a | 0x0C1030);
            g.fill(rx + rw - 1 - i, ry, rx + rw - i, ry + rh, a | 0x0C1030);
        }
        pad.noScissor(g);
        // la flecha de oro arriba y abajo, y la línea del centro
        g.fill(center, ry, center + 1, ry + rh, 0x66FFE680);
        for (int i = 0; i < 4; i++) {
            g.fill(center - 3 + i, ry - 2 + i, center + 4 - i, ry - 1 + i, i == 0 ? PadUi.GOLD_LO : PadUi.GOLD);
            g.fill(center - 3 + i, ry + rh + 1 - i, center + 4 - i, ry + rh + 2 - i, i == 0 ? PadUi.GOLD_LO : PadUi.GOLD);
        }
        // lo que te tocó (o cómo se juega)
        int ly = ry + rh + 6;
        if (revealed && !result.isEmpty()) {
            JsonObject best = result.get(resultBest);
            int col = rarityColor(str(best, "r", ""));
            String rn = PadFont.upper(rarityName(str(best, "r", "")));
            String text = result.size() > 1 ? "MEJOR: " + rn : "¡" + rn + "!";
            float pulse = PadSettings.animations ? 0.5F + 0.5F * (float) Math.sin((System.currentTimeMillis() - revealAt) / 160.0) : 1;
            int tw = PadFont.width(text) + PadUi.font().width(name(best)) + 10;
            int tx = x + (w - tw) / 2;
            PadFont.draw(g, text, tx, ly - 1, PadUi.lighten(col, Math.round(40 * pulse)) & 0xFFFFFF, true);
            PadUi.text(g, PadUi.fitEnd(name(best), w - 16 - PadFont.width(text) - 10), tx + PadFont.width(text) + 8, ly, 0xFFFFFFFF);
        } else {
            String how = active ? "Una tirada: " + num(data, "precio", 1) + " · " + num(data, "multi", 5) + " tiradas: " + num(data, "precioMulti", 5)
                    : "El Gachapón está cerrado.";
            PadUi.text(g, PadUi.fitEnd(how, w - 16), x + (w - Math.min(w - 16, PadUi.font().width(how))) / 2, ly, 0xFFB8C2F0);
        }
        // abajo: los botones y, encima, la garantía
        boolean can1 = active && !spinning() && !waiting && greens >= num(data, "precio", 1);
        boolean can5 = active && !spinning() && !waiting && greens >= num(data, "precioMulti", 5);
        int w1 = buttonWidth("GIRAR", num(data, "precio", 1));
        int w5 = buttonWidth("GIRAR X" + num(data, "multi", 5), num(data, "precioMulti", 5));
        int bx = x + (w - w1 - w5 - 8) / 2;
        priceButton(g, mx, my, bx, by, "GIRAR", num(data, "precio", 1), PadView.GREEN, can1, "§girar", () -> spin("girar"));
        priceButton(g, mx, my, bx + w1 + 8, by, "GIRAR X" + num(data, "multi", 5), num(data, "precioMulti", 5), PadView.GOLD, can5,
                "§girar5", () -> spin("girarMulti"));
        if (pity != null) {
            long left = num(pity, "faltan", 1);
            String r = str(pity, "rareza", "");
            String txt = left <= 1 ? r + " o mejor asegurado en la próxima" : r + " o mejor asegurado en " + left + " tiradas";
            txt = PadUi.fitEnd(txt, w - 16);
            PadUi.text(g, txt, x + (w - PadUi.font().width(txt)) / 2, py, 0xFF000000 | PadUi.lighten((int) num(pity, "color", 0xA855F7), 40));
        }
        // varias tiradas: las demás, en fila, entre lo que te tocó y la garantía
        if (many) {
            int s = 22, gap = 4, n = result.size();
            int rowW = n * s + (n - 1) * gap, sx = x + (w - rowW) / 2;
            int sy = ly + 14;
            if (sy + s <= py - 2) {
                for (int i = 0; i < n; i++) {
                    JsonObject p = result.get(i);
                    int col = rarityColor(str(p, "r", ""));
                    int cx = sx + i * (s + gap);
                    PadUi.slot(g, cx, sy, s, s, col);
                    PadUi.item(g, stack(p), cx + (s - 16) / 2, sy + (s - 16) / 2, 1);
                    if (PadUi.inside(mx, my, cx, sy, s, s)) pad.hover("§res" + i);
                }
            }
        }
    }

    private void spin(String action) {
        if (spinning() || waiting) return;
        send(action);
        pad.sound("select", 0.75F);
    }

    /** Una tarjeta de la tira: marco del color de su rareza, el objeto al doble y la cantidad. */
    private void drawCard(GuiGraphics g, JsonObject p, int x, int y, boolean win) {
        int col = rarityColor(str(p, "r", ""));
        if (win) {
            float pulse = PadSettings.animations ? 0.5F + 0.5F * (float) Math.sin((System.currentTimeMillis() - revealAt) / 140.0) : 1;
            int a = (int) (90 + 120 * pulse);
            PadUi.box(g, x - 3, y - 3, CARD_W + 6, CARD_H + 6, a << 24 | (col & 0xFFFFFF));
        }
        PadUi.box(g, x, y, CARD_W, CARD_H, col);
        g.fillGradient(x + 1, y + 1, x + CARD_W - 1, y + CARD_H - 1, PadUi.tint(col, 0.45F) & 0xFFFFFFFF, PadUi.tint(col, 0.85F));
        g.fill(x + 2, y + 1, x + CARD_W - 2, y + 2, 0x88FFFFFF);
        PadUi.stretch(g, "glow", x + 2, y + 3, CARD_W - 4, CARD_W - 6);
        ItemStack s = stack(p);
        PadUi.item(g, s, x + (CARD_W - 32) / 2, y + 5, 2);
        // franja de abajo con la cantidad (o el número de monedas)
        String t = str(p, "tipo", "objeto");
        String q = t.equals("objeto") ? "" : shortAmount(num(p, "n", 1));
        g.fill(x + 1, y + CARD_H - 11, x + CARD_W - 1, y + CARD_H - 1, 0xCC000000 | (darken(col, 0.55F) & 0xFFFFFF));
        if (!q.isEmpty()) PadFont.drawCentered(g, q, x + CARD_W / 2, y + CARD_H - 12, 0xFFFFFF, true);
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

    private static final int ROW = 24;

    private void drawList(GuiGraphics g, double mx, double my) {
        int x = X + leftW() + 6, w = W - leftW() - 6, top = Y + 14, bottom = Y + H;
        PadUi.panel(g, x, top, w, bottom - top);
        // pestañas de carpeta
        String[][] tabs = {{"premios", "PREMIOS"}, {"historial", "HISTORIAL"}};
        int tx = x + 4;
        int[] sel = null;
        String selLabel = "";
        for (String[] t : tabs) {
            int tw = PadFont.width(t[1]) + 14;
            boolean s = t[0].equals(tab);
            boolean hover = PadUi.inside(mx, my, tx, Y, tw, 15);
            if (s) {
                sel = new int[] {tx, tw};
                selLabel = t[1];
            } else {
                if (hover) pad.hover("§tab" + t[0]);
                PadUi.tab(g, tx, Y, tw, t[1], hover ? 1 : 0);
            }
            String key = t[0];
            hit(tx, Y, tw, 15, () -> {
                if (!key.equals(tab)) {
                    tab = key;
                    listScroll = 0;
                    pad.sound("tab", 0.6F);
                }
            });
            tx += tw + 2;
        }
        if (sel != null) PadUi.tab(g, sel[0], Y, sel[1], selLabel, 2);
        List<JsonObject> rows = list(data, tab.equals("premios") ? "premios" : "historial");
        int inner = bottom - top - 8;
        int content = rows.size() * ROW;
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, content - inner)));
        pad.scissor(g, x + 2, top + 4, w - 4, inner);
        int ry = top + 4 - listScroll;
        long now = System.currentTimeMillis();
        for (JsonObject p : rows) {
            if (ry + ROW > top && ry < bottom) drawPrizeRow(g, p, x + 5, ry, w - 14, now);
            ry += ROW;
        }
        pad.noScissor(g);
        if (rows.isEmpty()) {
            String msg = tab.equals("premios") ? "Sin premios." : "Aún no has girado.";
            PadUi.text(g, msg, x + (w - PadUi.font().width(msg)) / 2, top + (bottom - top) / 2 - 4, PadUi.MUTED);
        }
        PadUi.scrollbar(g, x + w - 7, top + 5, inner - 2, inner, content, listScroll);
    }

    private void drawPrizeRow(GuiGraphics g, JsonObject p, int x, int y, int w, long now) {
        int col = rarityColor(str(p, "r", ""));
        g.fill(x + 3, y + ROW - 1, x + w - 3, y + ROW, 0xFFC8E4F8);
        PadUi.slot(g, x + 1, y + 2, 20, 20, col);
        PadUi.item(g, stack(p), x + 3, y + 4, 1);
        int tx = x + 26;
        String right = tab.equals("premios") ? pct(dec(p, "p", 0)) : ago(now - num(p, "t", now));
        int rw = PadUi.font().width(right);
        PadUi.text(g, right, x + w - 4 - rw, y + 8, PadUi.MUTED);
        int room = w - 26 - rw - 10;
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
        if (mx >= X + leftW()) listScroll -= (int) Math.signum(delta) * ROW;
        return true;
    }

    @Override
    boolean drag(double mx, double my, double dy) {
        if (mx < X + leftW()) return false;
        listScroll -= (int) Math.round(dy);
        return true;
    }

    @Override
    boolean key(int key, int scan, int mods) {
        if (key == 32) { // espacio: girar una
            spin("girar");
            return true;
        }
        return false;
    }
}
