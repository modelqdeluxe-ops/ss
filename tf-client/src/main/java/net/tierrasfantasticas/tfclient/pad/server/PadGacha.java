package net.tierrasfantasticas.tfclient.pad.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.pad.TFPadNet;
import net.tierrasfantasticas.tfclient.util.TFConfigDir;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * El Gachapón (1.3.38): se gira gastando monedas verdes (las del TF Pass) y toca un premio al azar.
 * <ul>
 *   <li>Config: config/tfclient/gachapon.json (se crea la primera vez; se recarga con /tf reload): precio de una tirada,
 *       la tirada múltiple (cuántas y por cuánto), cuántos premios se guardan en el historial, la garantía (cada N
 *       tiradas sin una rareza, la siguiente es de esa rareza o mejor), las rarezas (nombre, color, peso) y los premios
 *       (cada uno con su rareza y su peso dentro de ella; ver PadPrize).</li>
 *   <li>Cómo se elige: primero la rareza por su peso y luego el premio dentro de ella por el suyo. El pad enseña la
 *       probabilidad de cada premio (sin letra pequeña).</li>
 *   <li>El servidor tira, cobra y da el premio al momento (cerrar el pad a mitad de la animación no cambia nada); el
 *       cliente hace la animación de la ruleta con el resultado que le llega.</li>
 *   <li>Datos: &lt;mundo&gt;/tfclient/gachapon.json: jugadores.&lt;uuid&gt;.{historial: [...], sinRareza: n, tiradas: n}.</li>
 * </ul>
 */
public final class PadGacha {
    record Rarity(String id, String name, int color, double weight, int order) {}

    record Prize(PadPrize prize, Rarity rarity, double weight) {}

    private static final List<Rarity> RARITIES = new ArrayList<>();
    private static final List<Prize> PRIZES = new ArrayList<>();
    private static long price = 1, multiCount = 5, multiPrice = 5;
    private static int historySize = 30, pityEvery = 10;
    private static String pityRarity = "epico";
    private static boolean enabled = true;
    private static final Random RANDOM = new Random();

    static final JsonStore STORE_IMPL = new JsonStore("gachapon.json") {
        @Override
        void loaded(MinecraftServer server) {
            loadConfig();
        }
    };
    public static final PadServer.Store STORE = STORE_IMPL;
    public static final PadServer.DataApp APP = new App();

    private PadGacha() {}

    // ---------------------------------------------------------------------------------------------------------------
    // Tirar
    // ---------------------------------------------------------------------------------------------------------------

    private static Rarity rarity(String id) {
        for (Rarity r : RARITIES) if (r.id.equals(id)) return r;
        return null;
    }

    /** Una tirada: rareza por peso (o la garantizada) y premio dentro de ella por peso. */
    private static Prize roll(int minOrder) {
        List<Rarity> pool = new ArrayList<>();
        double total = 0;
        for (Rarity r : RARITIES) {
            if (r.order < minOrder || !hasPrizes(r)) continue;
            pool.add(r);
            total += r.weight;
        }
        if (pool.isEmpty()) return PRIZES.isEmpty() ? null : PRIZES.get(RANDOM.nextInt(PRIZES.size()));
        double x = RANDOM.nextDouble() * total;
        Rarity chosen = pool.get(pool.size() - 1);
        for (Rarity r : pool) {
            x -= r.weight;
            if (x < 0) {
                chosen = r;
                break;
            }
        }
        List<Prize> in = new ArrayList<>();
        double sum = 0;
        for (Prize p : PRIZES) {
            if (p.rarity == chosen) {
                in.add(p);
                sum += p.weight;
            }
        }
        double y = RANDOM.nextDouble() * sum;
        for (Prize p : in) {
            y -= p.weight;
            if (y < 0) return p;
        }
        return in.get(in.size() - 1);
    }

    private static boolean hasPrizes(Rarity r) {
        for (Prize p : PRIZES) if (p.rarity == r && p.weight > 0) return true;
        return false;
    }

    /** Probabilidad de cada rareza (en %), contando solo las que tienen premios. */
    private static double chance(Rarity r) {
        double total = 0;
        for (Rarity x : RARITIES) if (hasPrizes(x)) total += x.weight;
        return total <= 0 || !hasPrizes(r) ? 0 : r.weight * 100 / total;
    }

    private static double chance(Prize p) {
        double sum = 0;
        for (Prize q : PRIZES) if (q.rarity == p.rarity) sum += q.weight;
        return sum <= 0 ? 0 : chance(p.rarity) * p.weight / sum;
    }

    /** Gira n veces: cobra, tira, da los premios y los apunta. Devuelve el evento para la animación (o null). */
    private static JsonObject spin(ServerPlayer player, int n, long cost) {
        if (!enabled || PRIZES.isEmpty()) {
            TFPadNet.notice(player, "El Gachapón está cerrado.");
            return null;
        }
        if (!PadGreen.take(player, cost)) {
            TFPadNet.notice(player, "Te faltan monedas verdes: se ganan con el TF Pass.");
            return null;
        }
        JsonObject data = STORE_IMPL.player(player.getUUID());
        Rarity pity = rarity(pityRarity);
        long without = JsonStore.num(data, "sinRareza");
        JsonArray history = data.has("historial") && data.get("historial").isJsonArray() ? data.getAsJsonArray("historial") : new JsonArray();
        JsonArray won = new JsonArray();
        int best = 0, bestOrder = -1;
        for (int i = 0; i < n; i++) {
            boolean forced = pity != null && pityEvery > 0 && without >= pityEvery - 1;
            Prize p = roll(forced ? pity.order : 0);
            if (p == null) break;
            if (pity != null && p.rarity.order >= pity.order) without = 0;
            else without++;
            boolean ok = p.prize.give(player);
            if (!ok) TFPadNet.notice(player, "No se pudo dar un premio: avisa al staff.");
            JsonObject j = p.prize.json();
            j.addProperty("r", p.rarity.id);
            j.addProperty("t", System.currentTimeMillis());
            history.add(j);
            JsonObject w = p.prize.json();
            w.addProperty("r", p.rarity.id);
            won.add(w);
            if (p.rarity.order > bestOrder) {
                bestOrder = p.rarity.order;
                best = i;
            }
        }
        while (history.size() > historySize) history.remove(0);
        data.add("historial", history);
        data.addProperty("sinRareza", without);
        data.addProperty("tiradas", JsonStore.num(data, "tiradas") + n);
        STORE_IMPL.changed();
        PadStats.add(player, "gacha", n);
        JsonObject event = new JsonObject();
        event.addProperty("id", System.nanoTime());
        event.add("premios", won);
        event.addProperty("mejor", best);
        return event;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Lo que se manda al pad
    // ---------------------------------------------------------------------------------------------------------------

    static final class App implements PadServer.DataApp {
        @Override
        public JsonObject data(ServerPlayer player) {
            JsonObject o = new JsonObject();
            o.addProperty("activo", enabled && !PRIZES.isEmpty());
            o.addProperty("verdes", PadGreen.balance(player.getUUID()));
            o.addProperty("precio", price);
            o.addProperty("multi", multiCount);
            o.addProperty("precioMulti", multiPrice);
            JsonObject p = STORE_IMPL.playerIfAny(player.getUUID());
            Rarity pity = rarity(pityRarity);
            if (pity != null && pityEvery > 0) {
                JsonObject g = new JsonObject();
                g.addProperty("cada", pityEvery);
                g.addProperty("rareza", pity.name);
                g.addProperty("color", pity.color);
                long without = p == null ? 0 : JsonStore.num(p, "sinRareza");
                g.addProperty("faltan", Math.max(1, pityEvery - without));
                o.add("garantia", g);
            }
            JsonArray rs = new JsonArray();
            for (Rarity r : RARITIES) {
                JsonObject j = new JsonObject();
                j.addProperty("id", r.id);
                j.addProperty("n", r.name);
                j.addProperty("c", r.color);
                j.addProperty("p", Math.round(chance(r) * 100) / 100.0);
                rs.add(j);
            }
            o.add("rarezas", rs);
            JsonArray ps = new JsonArray();
            List<Prize> sorted = new ArrayList<>(PRIZES);
            sorted.sort((a, b) -> a.rarity.order != b.rarity.order ? b.rarity.order - a.rarity.order : Double.compare(chance(a), chance(b)));
            for (Prize q : sorted) {
                JsonObject j = q.prize.json();
                j.addProperty("r", q.rarity.id);
                j.addProperty("p", Math.round(chance(q) * 100) / 100.0);
                ps.add(j);
            }
            o.add("premios", ps);
            JsonArray hist = new JsonArray();
            if (p != null && p.has("historial") && p.get("historial").isJsonArray()) {
                JsonArray h = p.getAsJsonArray("historial");
                for (int i = h.size() - 1; i >= 0; i--) hist.add(h.get(i)); // lo último, primero
            }
            o.add("historial", hist);
            o.addProperty("tiradas", p == null ? 0 : JsonStore.num(p, "tiradas"));
            return o;
        }

        @Override
        public JsonObject action(ServerPlayer player, String action) {
            return switch (action) {
                case "girar" -> spin(player, 1, price);
                case "girarMulti" -> spin(player, (int) multiCount, multiPrice);
                default -> null;
            };
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Configuración
    // ---------------------------------------------------------------------------------------------------------------

    static Path configFile() {
        return TFConfigDir.file("gachapon.json", null);
    }

    public static void loadConfig() {
        Path file = configFile();
        JsonObject o = TFJson.read(file);
        if (o == null) {
            o = defaults();
            if (!Files.exists(file)) TFJson.write(file, o);
        }
        RARITIES.clear();
        PRIZES.clear();
        enabled = TFJson.bool(o, "activado", true);
        price = Math.max(1, TFJson.num(o, "precio", 1));
        JsonObject multi = TFJson.obj(o, "tiradaMultiple");
        multiCount = Math.max(1, Math.min(10, TFJson.num(multi, "tiradas", 5)));
        multiPrice = Math.max(1, TFJson.num(multi, "precio", price * multiCount));
        historySize = (int) Math.max(5, Math.min(100, TFJson.num(o, "historial", 30)));
        JsonObject pity = TFJson.obj(o, "garantia");
        pityEvery = (int) Math.max(0, TFJson.num(pity, "cada", 10));
        pityRarity = TFJson.str(pity, "rareza", "epico");
        if (o.has("rarezas") && o.get("rarezas").isJsonArray()) {
            int order = 0;
            for (JsonElement e : o.getAsJsonArray("rarezas")) {
                try {
                    JsonObject r = e.getAsJsonObject();
                    RARITIES.add(new Rarity(TFJson.str(r, "id", "r" + order), TFJson.str(r, "nombre", "?"), color(TFJson.str(r, "color", "#8FA3BF")),
                            Math.max(0, TFJson.dec(r, "peso", 1)), order++));
                } catch (Exception ex) {
                    TFClient.LOGGER.warn("TF Pad: gachapon.json: una rareza no se pudo leer: {}", ex.getMessage());
                }
            }
        }
        if (o.has("premios") && o.get("premios").isJsonArray()) {
            int i = 0;
            for (JsonElement e : o.getAsJsonArray("premios")) {
                i++;
                PadPrize p = PadPrize.read(e, "gachapon.json (premio " + i + ")");
                if (p == null) continue;
                Rarity r = rarity(TFJson.str(e.getAsJsonObject(), "rareza", ""));
                if (r == null) {
                    TFClient.LOGGER.warn("TF Pad: gachapon.json: el premio {} tiene una rareza que no existe", i);
                    continue;
                }
                PRIZES.add(new Prize(p, r, Math.max(0, TFJson.dec(e.getAsJsonObject(), "peso", 1))));
            }
        }
        TFClient.LOGGER.info("TF Pad: Gachapón con {} premios en {} rarezas", PRIZES.size(), RARITIES.size());
    }

    private static int color(String hex) {
        try {
            return Integer.parseInt(hex.replace("#", ""), 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return 0x8FA3BF;
        }
    }

    private static JsonObject rarityJson(String id, String name, String color, double weight) {
        JsonObject r = new JsonObject();
        r.addProperty("id", id);
        r.addProperty("nombre", name);
        r.addProperty("color", color);
        r.addProperty("peso", weight);
        return r;
    }

    private static JsonObject prize(String rarity, double weight, String type, long amount, String id) {
        JsonObject p = PadPrize.of(type, amount, id);
        p.addProperty("rareza", rarity);
        p.addProperty("peso", weight);
        return p;
    }

    /**
     * Lo de fábrica: 1 moneda verde la tirada, 5 tiradas por 5, épico o mejor asegurado cada 10 tiradas. Común 62 %,
     * raro 27 %, épico 9 %, legendario 2 %. Premios pensados para la economía del servidor (una tirada vale de media
     * unas 300 monedas en objetos o dinero, y lo legendario, mucho más).
     */
    private static JsonObject defaults() {
        JsonObject o = new JsonObject();
        o.addProperty("version", 1);
        o.addProperty("activado", true);
        o.addProperty("precio", 1);
        JsonObject multi = new JsonObject();
        multi.addProperty("tiradas", 5);
        multi.addProperty("precio", 5);
        o.add("tiradaMultiple", multi);
        o.addProperty("historial", 30);
        JsonObject pity = new JsonObject();
        pity.addProperty("cada", 10);
        pity.addProperty("rareza", "epico");
        o.add("garantia", pity);
        JsonArray rs = new JsonArray();
        rs.add(rarityJson("comun", "Común", "#8FA3BF", 62));
        rs.add(rarityJson("raro", "Raro", "#3496FA", 27));
        rs.add(rarityJson("epico", "Épico", "#A855F7", 9));
        rs.add(rarityJson("legendario", "Legendario", "#F6B628", 2));
        o.add("rarezas", rs);
        JsonArray ps = new JsonArray();
        ps.add(prize("comun", 10, "monedas", 100, null));
        ps.add(prize("comun", 7, "monedas", 200, null));
        ps.add(prize("comun", 8, "objeto", 16, "minecraft:bread"));
        ps.add(prize("comun", 8, "objeto", 8, "minecraft:cooked_beef"));
        ps.add(prize("comun", 8, "objeto", 8, "minecraft:iron_ingot"));
        ps.add(prize("comun", 6, "objeto", 8, "minecraft:experience_bottle"));
        ps.add(prize("comun", 6, "objeto", 16, "minecraft:arrow"));
        ps.add(prize("raro", 8, "monedas", 500, null));
        ps.add(prize("raro", 8, "objeto", 3, "minecraft:diamond"));
        ps.add(prize("raro", 6, "objeto", 2, "minecraft:golden_apple"));
        ps.add(prize("raro", 6, "objeto", 8, "minecraft:golden_carrot"));
        ps.add(prize("raro", 5, "objeto", 4, "minecraft:ender_pearl"));
        ps.add(prize("raro", 4, "objeto", 16, "minecraft:experience_bottle"));
        ps.add(prize("epico", 6, "monedas", 1500, null));
        ps.add(prize("epico", 5, "objeto", 8, "minecraft:diamond"));
        ps.add(prize("epico", 4, "objeto", 1, "minecraft:enchanted_golden_apple"));
        ps.add(prize("epico", 4, "objeto", 2, "minecraft:netherite_scrap"));
        ps.add(prize("epico", 3, "objeto", 1, "minecraft:totem_of_undying"));
        ps.add(prize("legendario", 5, "monedas", 5000, null));
        ps.add(prize("legendario", 4, "objeto", 1, "minecraft:netherite_ingot"));
        ps.add(prize("legendario", 3, "objeto", 2, "minecraft:totem_of_undying"));
        ps.add(prize("legendario", 3, "objeto", 3, "minecraft:enchanted_golden_apple"));
        o.add("premios", ps);
        return o;
    }
}
