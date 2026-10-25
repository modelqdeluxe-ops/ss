/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.rastreo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import net.tierrasfantasticas.tfclient.items.TFSets;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * (1.3.142, el dueño: «que salga la lista de jugadores que compraron, como lo dice la página, y al picar en uno todos sus
 * objetos comprados») Las compras pagadas de la web (las manda por el puente cuando se piden: al arrancar y cada 5
 * min) con lo que entrega cada una, sacado de sus comandos: «tf web sets give {player} &lt;set&gt; [pieza]» (el set
 * entero o una pieza) y «give {player} &lt;objeto&gt; [n]». Se guardan en &lt;mundo&gt;/tfclient/compras.json para
 * tenerlas aunque la web no conteste.
 */
public final class TFPurchases {
    /** Un objeto que entrega la compra: id (tfclient:mecha_sword) y cuántos. */
    public record Expected(String id, int count) {}

    /** Una compra pagada. */
    public record Purchase(String order, String player, String uuid, String product, String productId, int quantity, String status,
                           String refunded, String paidAt, String giftFrom, List<Expected> items) {
        public boolean refundedOrDisputed() {
            return refunded != null && !refunded.isEmpty() || "refunded".equals(status) || "disputed".equals(status);
        }
    }

    private static final Pattern SETS = Pattern.compile("^tf web sets give \\{player} ([a-z0-9_]+)(?: ([a-z0-9_]+))?\\s*$");
    private static final Pattern GIVE = Pattern.compile("^give \\{player} ([a-z0-9_.-]+:[a-z0-9_./-]+)(?:\\{.*})?(?: (\\d+))?\\s*$");

    private static final Map<String, Purchase> ALL = new LinkedHashMap<>();
    private static boolean loaded;
    private static long askedAt;
    private static volatile boolean want = true;
    private static long receivedAt;

    private TFPurchases() {}

    static Path file(MinecraftServer server) {
        return TFJson.worldFile(server, "compras.json");
    }

    /** ¿Pedírselas a la web en esta consulta? (al arrancar, cada 5 min o si alguien abrió la app) */
    public static boolean wantFromWeb() {
        if (want || System.currentTimeMillis() - askedAt > 5 * 60_000L) {
            want = false;
            askedAt = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    /** Que la siguiente consulta las pida (la app Rastreador, al abrirla). */
    public static void askSoon() {
        if (System.currentTimeMillis() - askedAt > 30_000L) want = true;
    }

    public static long receivedAt() {
        return receivedAt;
    }

    public static synchronized void fromWeb(MinecraftServer server, JsonArray orders) {
        load(server);
        ALL.clear();
        for (JsonElement e : orders) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            String id = TFJson.str(o, "id", "");
            if (id.isEmpty()) continue;
            List<Expected> items = new ArrayList<>();
            int qty = (int) Math.max(1, TFJson.num(o, "quantity", 1));
            if (o.has("commands") && o.get("commands").isJsonArray()) {
                for (JsonElement c : o.getAsJsonArray("commands")) items.addAll(expected(c.getAsString(), qty));
            }
            String gift = o.has("gift") && o.get("gift").isJsonObject() ? TFJson.str(o.getAsJsonObject("gift"), "from", "") : "";
            ALL.put(id, new Purchase(id, TFJson.str(o, "player", "?"), TFJson.str(o, "uuid", ""), TFJson.str(o, "product", "?"),
                    TFJson.str(o, "productId", ""), qty, TFJson.str(o, "status", ""), TFJson.str(o, "refunded", ""),
                    TFJson.str(o, "paidAt", ""), gift, merge(items)));
        }
        receivedAt = System.currentTimeMillis();
        save(server);
    }

    /** Lo que entrega un comando de la compra. */
    static List<Expected> expected(String command, int qty) {
        List<Expected> out = new ArrayList<>();
        String c = command.trim().toLowerCase(Locale.ROOT);
        Matcher m = SETS.matcher(c);
        if (m.matches()) {
            TFSets.SetDef set = TFSets.all().get(m.group(1));
            if (set == null) return out;
            for (TFSets.ItemDef def : set.items()) {
                if (m.group(2) == null || m.group(2).equals(def.id())) out.add(new Expected("tfclient:" + def.id(), 1));
            }
            return out;
        }
        m = GIVE.matcher(c);
        if (m.matches()) {
            ResourceLocation rl = ResourceLocation.tryParse(m.group(1));
            Item item = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
            if (item != null) out.add(new Expected(m.group(1), (m.group(2) == null ? 1 : Integer.parseInt(m.group(2))) * qty));
        }
        return out;
    }

    private static List<Expected> merge(List<Expected> in) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (Expected e : in) m.merge(e.id(), e.count(), Integer::sum);
        List<Expected> out = new ArrayList<>();
        m.forEach((k, v) -> out.add(new Expected(k, v)));
        return out;
    }

    /** Las compras, la más nueva primero. */
    public static synchronized List<Purchase> all(MinecraftServer server) {
        load(server);
        List<Purchase> out = new ArrayList<>(ALL.values());
        out.sort(Comparator.comparing(Purchase::paidAt).reversed());
        return out;
    }

    public static synchronized Purchase get(MinecraftServer server, String order) {
        load(server);
        return ALL.get(order);
    }

    private static void load(MinecraftServer server) {
        if (loaded || server == null) return;
        loaded = true;
        JsonObject root = TFJson.read(file(server));
        if (root == null || !root.has("compras")) return;
        for (JsonElement e : root.getAsJsonArray("compras")) {
            JsonObject o = e.getAsJsonObject();
            List<Expected> items = new ArrayList<>();
            if (o.has("objetos")) {
                for (JsonElement i : o.getAsJsonArray("objetos")) {
                    JsonObject io = i.getAsJsonObject();
                    items.add(new Expected(TFJson.str(io, "id", ""), (int) TFJson.num(io, "n", 1)));
                }
            }
            String id = TFJson.str(o, "pedido", "");
            ALL.put(id, new Purchase(id, TFJson.str(o, "jugador", "?"), TFJson.str(o, "uuid", ""), TFJson.str(o, "producto", "?"),
                    TFJson.str(o, "productoId", ""), (int) TFJson.num(o, "cantidad", 1), TFJson.str(o, "estado", ""),
                    TFJson.str(o, "reembolso", ""), TFJson.str(o, "pagada", ""), TFJson.str(o, "regaloDe", ""), items));
        }
    }

    private static void save(MinecraftServer server) {
        JsonArray arr = new JsonArray();
        for (Purchase p : ALL.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("pedido", p.order());
            o.addProperty("jugador", p.player());
            o.addProperty("uuid", p.uuid());
            o.addProperty("producto", p.product());
            o.addProperty("productoId", p.productId());
            o.addProperty("cantidad", p.quantity());
            o.addProperty("estado", p.status());
            o.addProperty("reembolso", p.refunded());
            o.addProperty("pagada", p.paidAt());
            o.addProperty("regaloDe", p.giftFrom());
            JsonArray items = new JsonArray();
            for (Expected x : p.items()) {
                JsonObject io = new JsonObject();
                io.addProperty("id", x.id());
                io.addProperty("n", x.count());
                items.add(io);
            }
            o.add("objetos", items);
            arr.add(o);
        }
        JsonObject root = new JsonObject();
        root.add("compras", arr);
        try {
            TFJson.write(file(server), root);
        } catch (Exception e) {
            net.tierrasfantasticas.tfclient.TFClient.LOGGER.warn("TF Rastreador: no se pudo guardar compras.json: {}", e.toString());
        }
    }

    /** Para la prueba en el juego: mete compras como si las mandara la web. */
    public static void testSet(MinecraftServer server, JsonArray orders) {
        fromWeb(server, orders);
    }
}
