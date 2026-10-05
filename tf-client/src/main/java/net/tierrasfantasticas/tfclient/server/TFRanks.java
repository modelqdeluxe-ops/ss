package net.tierrasfantasticas.tfclient.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * Rangos de la tienda dentro del juego: el prefijo de color delante del nombre en el nametag, la lista de
 * jugadores (Tab) y el chat. Usa equipos del marcador vanilla («tf_rank_…»), así que funciona sin plugins y en
 * Mohist. La web es la que sabe el rango de cada jugador (por UUID) y lo manda en cada consulta del puente.
 *
 * <p>Si un jugador ya está en un equipo de otro sistema (otro plugin, un minijuego…), no se le toca.
 */
public final class TFRanks {
    public static final String TEAM_PREFIX = "tf_rank_";

    /** Un rango tal como lo describe la web (config/products.json → rank). */
    public record Rank(String id, String name, int tier, String group, String prefix, String color, int hex) {
        static Rank parse(JsonObject o) {
            if (o == null || !o.has("id") || o.get("id").isJsonNull()) return null;
            String hexText = str(o, "hex", "");
            int hex = -1;
            if (hexText.matches("#[0-9a-fA-F]{6}")) hex = Integer.parseInt(hexText.substring(1), 16);
            return new Rank(str(o, "id", ""), str(o, "name", ""), o.has("tier") ? o.get("tier").getAsInt() : 0,
                    str(o, "group", ""), str(o, "prefix", ""), str(o, "color", ""), hex);
        }

        /** Nombre corto para comandos y sugerencias: «rey», «dragon»… */
        public String key() {
            return group.isEmpty() ? id.replaceFirst("^rango-", "") : group;
        }

        /** Equipo del marcador: los rangos altos van primero en la lista de jugadores (orden alfabético). */
        String team() {
            return TEAM_PREFIX + (100 - tier) + "_" + key().replaceAll("[^a-z0-9_]", "");
        }

        Style style() {
            if (hex >= 0) return Style.EMPTY.withColor(TextColor.fromRgb(hex));
            ChatFormatting format = ChatFormatting.getByName(color);
            return Style.EMPTY.withColor(format != null && format.isColor() ? format : ChatFormatting.GOLD);
        }

        /** «[DRAGÓN] » con el color del rango. */
        public MutableComponent tag() {
            return Component.literal("[").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(prefix.toUpperCase(Locale.ROOT)).withStyle(style().withBold(true)))
                    .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static final Map<String, Rank> LIST = new LinkedHashMap<>();
    private static final Map<UUID, Rank> BY_PLAYER = new HashMap<>();
    private static boolean warnedForeignTeam;

    private TFRanks() {}

    private static String str(JsonObject o, String key, String def) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : def;
    }

    /** Lista de rangos de la tienda (llega en cada consulta). */
    public static void setList(JsonArray list) {
        LIST.clear();
        for (JsonElement element : list) {
            Rank rank = Rank.parse(element.getAsJsonObject());
            if (rank != null) LIST.put(rank.id(), rank);
        }
    }

    public static Collection<Rank> list() {
        List<Rank> out = new ArrayList<>(LIST.values());
        out.sort((a, b) -> Integer.compare(a.tier(), b.tier()));
        return out;
    }

    /** Busca un rango por id («rango-rey») o por nombre corto («rey»). */
    public static Rank find(String text) {
        String key = text.toLowerCase(Locale.ROOT);
        for (Rank rank : LIST.values()) {
            if (rank.id().equalsIgnoreCase(key) || rank.key().equalsIgnoreCase(key)) return rank;
        }
        return null;
    }

    public static Rank of(UUID uuid) {
        return BY_PLAYER.get(uuid);
    }

    /** Rangos de los jugadores conectados según la web: el que no viene en la lista no tiene rango. */
    public static void update(MinecraftServer server, JsonArray ranks) {
        Map<UUID, Rank> fresh = new HashMap<>();
        for (JsonElement element : ranks) {
            JsonObject o = element.getAsJsonObject();
            Rank rank = Rank.parse(o);
            if (rank == null || !o.has("uuid")) continue;
            try {
                fresh.put(UUID.fromString(o.get("uuid").getAsString()), rank);
            } catch (IllegalArgumentException ignored) {
                // UUID no válido.
            }
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            set(server, player, fresh.get(player.getUUID()));
        }
    }

    /** Pone (o quita, con null) el rango de un jugador conectado y actualiza su nametag. */
    public static void set(MinecraftServer server, ServerPlayer player, Rank rank) {
        if (rank == null) BY_PLAYER.remove(player.getUUID());
        else BY_PLAYER.put(player.getUUID(), rank);
        apply(server, player, rank);
    }

    public static void forget(UUID uuid) {
        BY_PLAYER.remove(uuid);
    }

    private static void apply(MinecraftServer server, ServerPlayer player, Rank rank) {
        if (!TFServerConfig.nametag()) return;
        ServerScoreboard scoreboard = server.getScoreboard();
        String name = player.getScoreboardName();
        PlayerTeam current = scoreboard.getPlayersTeam(name);
        if (current != null && !current.getName().startsWith(TEAM_PREFIX)) {
            if (!warnedForeignTeam) {
                warnedForeignTeam = true;
                TFClient.LOGGER.info("TF Ranks: {} está en el equipo «{}» de otro sistema; no se cambia su nametag", name, current.getName());
            }
            return;
        }
        if (rank == null) {
            if (current != null) scoreboard.removePlayerFromTeam(name, current);
            return;
        }
        PlayerTeam team = scoreboard.getPlayerTeam(rank.team());
        if (team == null) team = scoreboard.addPlayerTeam(rank.team());
        Component prefix = rank.tag();
        // Solo se manda el cambio si es distinto (si no, cada consulta reenviaría el equipo a todos).
        if (!Objects.equals(team.getPlayerPrefix(), prefix)) team.setPlayerPrefix(prefix);
        if (current != team) scoreboard.addPlayerToTeam(name, team);
    }
}
