package net.tierrasfantasticas.tfclient.pad.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * Contadores de cada jugador para el pad (&lt;mundo&gt;/tfclient/pad-estadisticas.json): misiones y cazas hechas,
 * exploraciones, viajes, kits, fotos publicadas, likes recibidos, ventas en el GTS… Los usan Títulos, Ranking y
 * Jugadores. También guarda el último nombre conocido de cada uno.
 */
public final class PadStats {
    public static final String MISSIONS = "misiones", HUNTS = "cazas", EXPLORES = "exploraciones", TRAVELS = "viajes",
            KITS = "kits", PHOTOS = "fotos", LIKES = "likes", GTS_SALES = "ventas_gts", CLAN_FOUNDED = "clanes_fundados";

    static final JsonStore STORE_IMPL = new JsonStore("pad-estadisticas.json");
    public static final net.tierrasfantasticas.tfclient.pad.PadServer.Store STORE = STORE_IMPL;

    private PadStats() {}

    public static void add(UUID uuid, String key, long n) {
        JsonObject p = STORE_IMPL.player(uuid);
        p.addProperty(key, JsonStore.num(p, key) + n);
        STORE_IMPL.changed();
    }

    public static void add(ServerPlayer player, String key, long n) {
        name(player);
        add(player.getUUID(), key, n);
        PadTitles.check(player);
    }

    public static long get(UUID uuid, String key) {
        JsonObject p = STORE_IMPL.playerIfAny(uuid);
        return p == null ? 0 : JsonStore.num(p, key);
    }

    public static void name(ServerPlayer player) {
        JsonObject p = STORE_IMPL.player(player.getUUID());
        String name = player.getGameProfile().getName();
        if (!name.equals(p.has("nombre") ? p.get("nombre").getAsString() : "")) {
            p.addProperty("nombre", name);
            STORE_IMPL.changed();
        }
    }

    public static String nameOf(UUID uuid) {
        JsonObject p = STORE_IMPL.playerIfAny(uuid);
        return p != null && p.has("nombre") ? p.get("nombre").getAsString() : "?";
    }

    public static UUID byName(String name) {
        JsonElement all = STORE_IMPL.root.get("jugadores");
        if (all == null || !all.isJsonObject()) return null;
        for (Map.Entry<String, JsonElement> e : all.getAsJsonObject().entrySet()) {
            JsonObject p = e.getValue().getAsJsonObject();
            if (p.has("nombre") && p.get("nombre").getAsString().equalsIgnoreCase(name)) {
                try {
                    return UUID.fromString(e.getKey());
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    /** Los n primeros en un contador (para Ranking). */
    public static List<Map.Entry<UUID, Long>> top(String key, int n) {
        List<Map.Entry<UUID, Long>> list = new ArrayList<>();
        JsonElement all = STORE_IMPL.root.get("jugadores");
        if (all == null || !all.isJsonObject()) return list;
        for (Map.Entry<String, JsonElement> e : all.getAsJsonObject().entrySet()) {
            long v = JsonStore.num(e.getValue().getAsJsonObject(), key);
            if (v <= 0) continue;
            try {
                list.add(Map.entry(UUID.fromString(e.getKey()), v));
            } catch (IllegalArgumentException ignored) {
                // clave rara: se salta
            }
        }
        list.sort(Comparator.comparingLong((Map.Entry<UUID, Long> e) -> e.getValue()).reversed());
        return list.size() > n ? list.subList(0, n) : list;
    }
}
