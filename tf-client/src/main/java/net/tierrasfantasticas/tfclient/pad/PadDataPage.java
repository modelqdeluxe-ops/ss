package net.tierrasfantasticas.tfclient.pad;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Base de las apps que dibuja el cliente con los datos en JSON que manda el servidor (1.3.38: Gachapón y TF Pass): pide
 * los datos al abrir, los guarda al llegar (received) y manda los botones como acciones. Trae lo común: el objeto y el
 * nombre de un premio, las zonas de clic, la ruedita de «cargando» y unos dibujitos (candado, marca, flechas).
 */
abstract class PadDataPage extends PadPage {
    JsonObject data;
    /** Cuándo llegaron los datos (para las cuentas atrás). */
    long dataAt;
    boolean waiting = true;

    record Hit(int x, int y, int w, int h, Runnable action) {}

    final List<Hit> hits = new ArrayList<>();

    PadDataPage(TFPadScreen pad, String app) {
        super(pad, app);
    }

    void set(String json) {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            data = o;
            dataAt = System.currentTimeMillis();
            waiting = false;
            received(o);
        } catch (Exception e) {
            waiting = false;
        }
    }

    /** Llegaron datos nuevos (con «evento» si es la respuesta a un botón). */
    abstract void received(JsonObject o);

    void send(String action) {
        waiting = true;
        TFPadNet.CHANNEL.sendToServer(new TFPadNet.Action(app, "", action, ""));
    }

    void hit(int x, int y, int w, int h, Runnable action) {
        hits.add(new Hit(x, y, w, h, action));
    }

    @Override
    boolean click(double mx, double my, int button) {
        if (button != 0) return false;
        for (Hit h : new ArrayList<>(hits)) {
            if (PadUi.inside(mx, my, h.x, h.y, h.w, h.h)) {
                h.action.run();
                return true;
            }
        }
        return false;
    }

    /** Pantalla de espera mientras llegan los datos. */
    boolean loading(GuiGraphics g) {
        if (data != null) return false;
        PadUi.panel(g, X, Y, W, H);
        PadUi.spinner(g, X + W / 2, Y + H / 2 - 8);
        PadFont.drawCentered(g, "CARGANDO", X + W / 2, Y + H / 2 + 4, PadUi.MUTED & 0xFFFFFF, true);
        return true;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Premios
    // ---------------------------------------------------------------------------------------------------------------

    /** El objeto de un premio {tipo, n, id, nombre, nbt?}, con su cantidad (para el numerito). */
    static ItemStack stack(JsonObject p) {
        long n = num(p, "n", 1);
        if (p.has("nbt")) {
            try {
                ItemStack s = ItemStack.of(TagParser.parseTag(p.get("nbt").getAsString()));
                if (!s.isEmpty()) return s;
            } catch (Exception ignored) {
                // se usa el id
            }
        }
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(str(p, "id", "minecraft:paper")));
        if (item == null || item == Items.AIR) item = Items.PAPER;
        ItemStack s = new ItemStack(item);
        String type = str(p, "tipo", "objeto");
        // las monedas no llevan número en el icono (se dice en el nombre); los objetos, sí
        if (type.equals("objeto")) s.setCount((int) Math.max(1, Math.min(99, n)));
        return s;
    }

    /** El nombre de un premio: el que manda el servidor o, si es un objeto, el del juego en tu idioma («Diamante x8»). */
    static String name(JsonObject p) {
        String n = str(p, "nombre", "");
        if (!n.isEmpty()) return n;
        ItemStack s = stack(p);
        long count = num(p, "n", 1);
        return s.getHoverName().getString() + (count > 1 ? " x" + count : "");
    }

    static String str(JsonObject o, String key, String def) {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : def;
    }

    static long num(JsonObject o, String key, long def) {
        try {
            JsonElement e = o == null ? null : o.get(key);
            return e != null && e.isJsonPrimitive() ? e.getAsLong() : def;
        } catch (Exception ex) {
            return def;
        }
    }

    static double dec(JsonObject o, String key, double def) {
        try {
            JsonElement e = o == null ? null : o.get(key);
            return e != null && e.isJsonPrimitive() ? e.getAsDouble() : def;
        } catch (Exception ex) {
            return def;
        }
    }

    static List<JsonObject> list(JsonObject o, String key) {
        List<JsonObject> out = new ArrayList<>();
        JsonElement e = o == null ? null : o.get(key);
        if (e == null || !e.isJsonArray()) return out;
        for (JsonElement x : (JsonArray) e) if (x.isJsonObject()) out.add(x.getAsJsonObject());
        return out;
    }

    /** «2,5 %» / «0,25 %» / «12 %». */
    static String pct(double p) {
        String s = p >= 10 ? String.valueOf(Math.round(p)) : p >= 1 ? String.format(java.util.Locale.ROOT, "%.1f", p)
                : String.format(java.util.Locale.ROOT, "%.2f", p);
        if (s.contains(".")) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s.replace('.', ',') + " %";
    }

    /** «3 d 4 h», «5 h 12 min», «8 min». */
    static String until(long ms) {
        long m = Math.max(0, ms / 60000), h = m / 60, d = h / 24;
        if (d > 0) return d + " d " + (h % 24) + " h";
        if (h > 0) return h + " h " + (m % 60) + " min";
        return Math.max(1, m) + " min";
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Dibujitos
    // ---------------------------------------------------------------------------------------------------------------

    /** Candado de 7x8 con su esquina en (x, y). */
    static void lock(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 2, y, x + 5, y + 1, color);
        g.fill(x + 1, y + 1, x + 2, y + 4, color);
        g.fill(x + 5, y + 1, x + 6, y + 4, color);
        g.fill(x, y + 3, x + 7, y + 8, color);
        g.fill(x + 3, y + 5, x + 4, y + 7, 0xFFFFFFFF);
    }

    /** Marca (✓) de 7x6 con su esquina en (x, y). */
    static void check(GuiGraphics g, int x, int y, int color) {
        int[][] px = {{6, 0}, {5, 1}, {6, 1}, {4, 2}, {5, 2}, {0, 2}, {0, 3}, {1, 3}, {3, 3}, {4, 3}, {1, 4}, {2, 4}, {3, 4}, {2, 5}};
        for (int[] p : px) g.fill(x + p[0], y + p[1], x + p[0] + 1, y + p[1] + 1, color);
    }

    /** Flecha de 4x7 (izquierda o derecha) centrada en (cx, cy). */
    static void arrow(GuiGraphics g, int cx, int cy, boolean left, int color) {
        for (int i = 0; i < 4; i++) {
            int x = left ? cx - 2 + i : cx + 1 - i;
            g.fill(x, cy - i, x + 1, cy + i + 1, color);
        }
    }

    /** Tarjeta oscura con ribete de oro (como la del reproductor de Música), con su pestaña oscura encima. */
    static void darkCard(GuiGraphics g, int x, int top, int w, int bottom, int tabY, String tab) {
        int tw = PadFont.width(tab) + 14;
        PadUi.box(g, x + 4, tabY, tw, 15, PadUi.NAVY);
        g.fillGradient(x + 5, tabY + 1, x + 4 + tw - 1, tabY + 16, 0xFF323C80, 0xFF262E66);
        g.fill(x + 6, tabY + 1, x + 4 + tw - 2, tabY + 3, PadUi.GOLD);
        g.fill(x + 6, tabY + 1, x + 4 + tw - 2, tabY + 2, PadUi.GOLD_HI);
        PadFont.drawCentered(g, tab, x + 4 + tw / 2, tabY + 4, 0xFFE680, false);
        int h = bottom - top;
        PadUi.box(g, x, top, w, h, PadUi.NAVY);
        g.fillGradient(x + 1, top + 1, x + w - 1, top + h - 1, 0xFF262E66, 0xFF141A3C);
        g.fill(x + 2, top + 1, x + w - 2, top + 2, 0xFF4A56A0);
        int gold = 0x55F6B628;
        g.fill(x + 2, top + 2, x + w - 2, top + 3, gold);
        g.fill(x + 2, top + h - 3, x + w - 2, top + h - 2, gold);
        g.fill(x + 2, top + 3, x + 3, top + h - 3, gold);
        g.fill(x + w - 3, top + 3, x + w - 2, top + h - 3, gold);
        g.fill(x + 5, top, x + 4 + tw - 1, top + 2, 0xFF262E66); // la pestaña se une a la tarjeta
    }

    /** Botón con el precio en monedas verdes a la derecha del texto: [GIRAR · ● 5]. Devuelve su ancho. */
    int priceButton(GuiGraphics g, double mx, double my, int x, int y, String label, long price, int style, boolean enabled,
                    String id, Runnable action) {
        String n = Long.toString(price);
        int w = buttonWidth(label, price);
        boolean hover = enabled && PadUi.inside(mx, my, x, y, w, 16);
        if (hover) pad.hover(id);
        PadUi.button(g, x, y, w, "", style, hover, enabled);
        int tx = x + 7;
        PadFont.draw(g, label, tx, y + 2, enabled ? 0xFFFFFF : 0x8C9AC4, true);
        tx += PadFont.width(label) + 6;
        g.fill(tx - 3, y + 4, tx - 2, y + 11, enabled ? 0x66FFFFFF : 0x338C9AC4);
        pad.blit(g, "coin_green_s", tx, y + 4);
        PadFont.draw(g, n, tx + 10, y + 2, enabled ? 0xFFFFFF : 0x8C9AC4, true);
        if (enabled) hit(x, y, w, 16, action);
        return w;
    }

    static int buttonWidth(String label, long price) {
        return PadFont.width(label) + 6 + 10 + PadFont.width(Long.toString(price)) + 14;
    }
}
