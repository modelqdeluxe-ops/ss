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
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;

/**
 * (1.3.156, el dueño: «guerras de clanes … diseña bien bonito ese apartado; una barra de progreso vertical, así como la
 * del pase está en horizontal pero esta es vertical, y ambos lados con su clan y color y todo, y la barra se llena hasta
 * la meta completando misiones de la guerra»)
 * <p>
 * La pantalla de la guerra (datos de la app «guerra», pad/server/PadClanWar): a la izquierda, los dos clanes con su
 * insignia, su etiqueta en su color, el porcentaje y los que más aportan, y entre ellos la barra vertical doble (una
 * por clan, de su color, que se llena de abajo arriba hasta la META de oro); a la derecha, los objetivos con el avance
 * de cada lado. Arriba, lo que queda o quién ganó.
 */
final class PadClanWarPage extends PadDataPage {
    private static final int BG_DARK = 0xFF1A2236;
    /** Para la prueba en el juego: el último porcentaje dibujado de cada lado (A, B). */
    static volatile float testDrawnA = -1, testDrawnB = -1;
    private int ticks;

    PadClanWarPage(TFPadScreen pad) {
        super(pad, "guerra");
    }

    @Override
    String title() {
        return "GUERRA DE CLANES";
    }

    @Override
    PadPage backTo() {
        pad.openApp("clanes", "guerra");
        return pad.page();
    }

    @Override
    void received(JsonObject o) {}

    @Override
    void tick() {
        // la guerra va cambiando mientras se mira: cada 10 s, los datos al día
        if (++ticks % 200 == 0 && !waiting) send("refrescar");
    }

    @Override
    void render(GuiGraphics g, double mx, double my, float partial) {
        if (loading(g)) return;
        hits.clear();
        int acc = PadSkin.accent("clanes");
        PadSkin.window(g, X, Y, W, H, acc);
        int ink = PadSkin.darken(acc, 0.7F);
        int bottom = Y + H - 24;
        backButton(g, mx, my);
        if (!data.has("hay") || !data.get("hay").getAsBoolean()) {
            String a = "Tu clan no está en guerra.", b = "El líder o un colíder la declara en Clanes › GUERRA.";
            PadUi.title(g, a, X + (W - PadUi.titleWidth(a, W - 20)) / 2, Y + H / 2 - 16, W - 20, ink);
            PadUi.text(g, PadUi.fitEnd(b, W - 20), X + (W - Math.min(W - 20, PadUi.font().width(b))) / 2, Y + H / 2, PadUi.MUTED);
            return;
        }
        JsonObject a = data.getAsJsonObject("a"), b = data.getAsJsonObject("b");
        String mine = str(data, "tuLado", "A"), winner = str(data, "ganador", "");
        boolean over = str(data, "estado", "").equals("acabada");
        // arriba: lo que queda (o quién ganó). (1.3.158, el dueño: «frases repetidas») sin «GUERRA DE CLANES» otra vez: ya
        // es el título de la ventana
        String status;
        int statusColor;
        if (over) {
            status = winner.equals("empate") ? "Acabó en empate" : "¡Ganó [" + str(winner.equals("A") ? a : b, "tag", "?") + "]!";
            statusColor = winner.equals(mine) ? 0xFF1E7C2C : winner.equals("empate") ? 0xFFC27A10 : 0xFFB0342C;
        } else {
            long left = num(data, "quedan", 0) - (System.currentTimeMillis() - dataAt);
            status = "Quedan " + until(left);
            statusColor = PadUi.TEXT;
        }
        PadUi.text(g, status, X + 8, Y + 7, statusColor);
        // la parte de la izquierda: los dos clanes y la barra doble en medio
        int top = Y + 22;
        int lw = Math.max(150, (int) (W * 0.56));
        int bw = Math.max(12, Math.min(20, lw / 10)), gap = 6, barsW = bw * 2 + gap;
        int cx = X + 4 + lw / 2;
        int pw = (lw - barsW - 20) / 2;
        float k = Math.min(1F, (System.currentTimeMillis() - dataAt) / 900F);
        float ease = 1 - (1 - k) * (1 - k) * (1 - k);
        float pa = (float) dec(a, "pct", 0) * ease, pb = (float) dec(b, "pct", 0) * ease;
        testDrawnA = pa;
        testDrawnB = pb;
        int barTop = top + 14, barBot = bottom - 14;
        int xa = cx - gap / 2 - bw, xb = cx + gap / 2;
        // la META de oro arriba, de lado a lado de las dos barras
        g.fill(xa - 4, barTop - 4, xb + bw + 4, barTop - 1, 0xFFE8961A);
        g.fill(xa - 4, barTop - 4, xb + bw + 4, barTop - 3, 0xFFFFE070);
        PadFont.drawShadowCentered(g, "META", cx, barTop - 13, 0xFFD650, 0xFF6A3E08);
        bar(g, xa, barTop, barBot, bw, pa, (int) num(a, "color", 0x5AB4FF));
        bar(g, xb, barTop, barBot, bw, pb, (int) num(b, "color", 0xFF6274));
        // las marcas del 25, 50 y 75 %
        for (int q = 1; q <= 3; q++) {
            int y = barBot - (barBot - barTop) * q / 4;
            g.fill(xa - 2, y, xa, y + 1, PadUi.MUTED);
            g.fill(xb + bw, y, xb + bw + 2, y + 1, PadUi.MUTED);
            g.fill(xa, y, xa + bw, y + 1, 0x30FFFFFF);
            g.fill(xb, y, xb + bw, y + 1, 0x30FFFFFF);
        }
        // (1.3.158) sin el % debajo de cada barra: ya sale en grande al lado de cada clan
        side(g, a, X + 6, top, pw, bottom, mine.equals("A"), over && winner.equals("A"), pa);
        side(g, b, cx + barsW / 2 + 6, top, pw, bottom, mine.equals("B"), over && winner.equals("B"), pb);
        // la derecha: los objetivos, con el avance de cada lado
        int rx = X + 8 + lw, rw = X + W - 8 - rx;
        g.fill(rx - 4, top, rx - 3, bottom, 0x3018265C);
        PadUi.title(g, "OBJETIVOS", rx, top, rw, ink);
        List<JsonObject> objs = list(data, "objetivos");
        int rowY = top + 13, rh = objs.isEmpty() ? 0 : Math.max(20, (bottom - rowY) / objs.size());
        for (JsonObject o : objs) {
            objective(g, o, rx, rowY, rw, rh, (int) num(a, "color", 0x5AB4FF), (int) num(b, "color", 0xFF6274), ease, mx, my);
            rowY += rh;
        }
    }

    /** Una de las barras verticales: oscura por dentro y llena de abajo arriba con el color del clan. */
    private static void bar(GuiGraphics g, int x, int top, int bot, int w, float f, int rgb) {
        PadUi.box(g, x - 1, top - 1, w + 2, bot - top + 2, PadUi.INK);
        g.fillGradient(x, top, x + w, bot, 0xFF0E1424, 0xFF1C2640);
        int fh = Math.round((bot - top) * Math.max(0, Math.min(1, f)));
        if (fh <= 0) return;
        int fy = bot - fh;
        int c = 0xFF000000 | rgb;
        g.fillGradient(x, fy, x + w, bot, PadSkin.mix(c, 0xFFFFFFFF, 0.28F), PadSkin.darken(c, 0.72F));
        // el brillo de la izquierda y la línea de arriba
        g.fill(x + 1, fy, x + 3, bot, 0x38FFFFFF);
        g.fill(x, fy, x + w, fy + 1, PadSkin.mix(c, 0xFFFFFFFF, 0.65F));
        // la burbuja que sube (se mueve un poco con el tiempo)
        float t = PadSkin.time();
        int by = bot - 3 - (int) ((t * 18) % Math.max(4, fh - 4));
        if (fh > 8) g.fill(x + w / 2, by, x + w / 2 + 2, by + 2, 0x60FFFFFF);
    }

    /** Un lado: la insignia, la etiqueta en su color, el nombre, su porcentaje y los que más aportan. */
    private void side(GuiGraphics g, JsonObject c, int x, int top, int w, int bottom, boolean mine, boolean won, float pct) {
        int rgb = 0xFF000000 | (int) num(c, "color", 0xFFFFFF);
        int cx = x + w / 2;
        int y = top + 2;
        if (won) {
            PadFont.drawShadowCentered(g, "GANADOR", cx, y, 0xFFD650, 0xFF6A3E08);
            y += 9;
        } else if (mine) {
            PadFont.drawCentered(g, "TU CLAN", cx, y, 0x3A5888, false);
            y += 9;
        }
        int size = Math.max(18, Math.min(36, w - 8));
        String badge = str(c, "insignia", "");
        if (badge.isEmpty() || !PadSkin.image(g, pad, badge, cx - size / 2, y, size)) {
            g.fill(cx - size / 2, y, cx + size / 2, y + size, rgb);
        }
        y += size + 3;
        // la etiqueta en una pastilla oscura (los colores claros también se leen)
        String tag = "[" + str(c, "tag", "?") + "]";
        int tw = PadUi.font().width(tag) + 8;
        PadUi.box(g, cx - tw / 2, y, tw, 12, BG_DARK);
        g.drawString(PadUi.font(), tag, cx - tw / 2 + 4, y + 2, rgb, true);
        y += 14;
        String name = PadUi.fitEnd(str(c, "nombre", ""), w);
        PadUi.text(g, name, cx - PadUi.font().width(name) / 2, y, PadUi.MUTED);
        y += 11;
        // el porcentaje, grande
        String p = Math.round(pct * 100) + " %";
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(2F, 2F, 1F);
        int pwid = PadUi.font().width(p);
        g.drawString(PadUi.font(), p, -pwid / 2, 0, PadSkin.darken(rgb, 0.85F), false);
        g.pose().popPose();
        y += 20;
        // los que más aportan
        int i = 0;
        for (JsonObject t : list(c, "top")) {
            if (y + 9 > bottom) break;
            String line = PadUi.fitEnd((++i) + ". " + str(t, "nombre", "?") + " " + PadView.shortNumber(num(t, "n", 0)), w);
            PadUi.text(g, line, cx - PadUi.font().width(line) / 2, y, PadUi.TEXT);
            y += 10;
        }
    }

    /** Un objetivo: su icono, su nombre y dos barritas (un lado y el otro) con lo que lleva cada uno. */
    private void objective(GuiGraphics g, JsonObject o, int x, int y, int w, int h, int ca, int cb, float ease, double mx, double my) {
        net.minecraft.world.item.ItemStack icon = stack(o.has("icono") ? json("objeto", str(o, "icono", "minecraft:paper")) : json("objeto", "minecraft:paper"));
        PadUi.item(g, icon, x, y + 1, 1);
        int tx = x + 19, tw = w - 19;
        PadUi.text(g, PadUi.fitEnd(str(o, "nombre", "?"), tw), tx, y + 1, PadUi.TEXT);
        double ma = Math.max(1, dec(o, "metaA", 1)), mb = Math.max(1, dec(o, "metaB", 1));
        float fa = (float) Math.min(1, dec(o, "progA", 0) / ma) * ease, fb = (float) Math.min(1, dec(o, "progB", 0) / mb) * ease;
        int by = y + 11;
        mini(g, tx, by, tw, fa, ca);
        mini(g, tx, by + 5, tw, fb, cb);
        if (PadUi.inside(mx, my, x, y, w, h)) {
            pad.hover("§obj" + str(o, "nombre", ""));
            String tip = PadView.shortNumber(num(o, "progA", 0)) + "/" + PadView.shortNumber(num(o, "metaA", 0)) + " · "
                    + PadView.shortNumber(num(o, "progB", 0)) + "/" + PadView.shortNumber(num(o, "metaB", 0));
            PadUi.text(g, PadUi.fitEnd(tip, tw), tx, by + 10, PadUi.MUTED);
        }
    }

    private static void mini(GuiGraphics g, int x, int y, int w, float f, int rgb) {
        g.fill(x, y, x + w, y + 4, 0xFF1C2640);
        int fw = Math.round((w - 2) * Math.max(0, Math.min(1, f)));
        int c = 0xFF000000 | rgb;
        if (fw > 0) g.fillGradient(x + 1, y + 1, x + 1 + fw, y + 3, PadSkin.mix(c, 0xFFFFFFFF, 0.3F), c);
        if (f >= 1) g.fill(x + w - 3, y, x + w, y + 4, 0xFFFFD650);
    }

    private static JsonObject json(String type, String id) {
        JsonObject o = new JsonObject();
        o.addProperty("tipo", type);
        o.addProperty("id", id);
        o.addProperty("n", 1);
        return o;
    }

    private void backButton(GuiGraphics g, double mx, double my) {
        int bw = PadUi.buttonWidth("ATRÁS");
        int x = X + 8, y = Y + H - 20;
        boolean hover = PadUi.inside(mx, my, x, y, bw, 16);
        if (hover) pad.hover("§atras");
        PadSkin.button(g, x, y, bw, "ATRÁS", PadView.BLUE, hover, true, false);
        hit(x, y, bw, 16, () -> pad.back());
    }
}
