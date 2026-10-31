/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.pad.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.jobs.TFJobsConfig;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * (1.3.156, el dueño: «la actualización de los clanes») Lo que un clan gana y gasta:
 * <ul>
 *   <li><b>Fantastic Clan Coin</b> (azul, «monedasClan»): son del clan; solo el líder y los colíderes las ven (en su
 *   pad, junto a las otras monedas) y las gastan (tienda del clan, mejoras del banco). Se ganan con las misiones del clan,
 *   al subir de nivel, en los duelos y en las guerras. En los clanes no hay premios de monedas amarillas: solo del clan
 *   o verdes.</li>
 *   <li><b>Nivel</b> («xp», «nivel»): sube con la XP de las misiones del clan; cada nivel da monedas del clan y dos plazas
 *   más (de 20 a 40 miembros).</li>
 *   <li><b>Prestigio</b> («prestigio»): misiones, duelos y guerras; el ranking de clanes del pad va por él.</li>
 *   <li><b>Insignia</b> («insignia»): una de las de tools/pad/clanes.py (assets/tfclient/pad/insignias.json).</li>
 *   <li><b>Misiones del clan</b> («misiones»): cada semana, 4 misiones de equipo del inicio del juego (nada del Nether ni
 *   del End), con la meta según los miembros. Cuenta lo que hace cada miembro (las mismas reglas que los oficios: nada de
 *   bloques puestos ni de generadores); los aliados suman la mitad. Al cumplirse: monedas del clan, XP y prestigio para el
 *   clan, y una moneda verde para cada miembro que aportó.</li>
 * </ul>
 */
public final class PadClanProgress {
    public static final int MAX_LEVEL = 20, MISSIONS_PER_WEEK = 4;
    /** Lo que suma a las misiones lo que hace un clan aliado (la mitad). */
    static final double ALLY_SHARE = 0.5;

    /** Una misión de clan: lo que pide (como los oficios: tipo y objetivo), la meta base para 5 miembros y su premio. */
    public record Mission(String id, String name, String desc, ItemStack icon, String type, String target, int amount, long coins,
                          long xp, long prestige) {}

    /**
     * Las misiones de clan (inicio del juego: nada del Nether ni del End; todas se pueden cumplir con lo del Overworld).
     * La meta es para un clan de 5; cambia con los miembros ({@link #goal}).
     */
    public static final List<Mission> POOL = List.of(
            m("cantera", "Cantera del clan", "Rompan piedra entre todos.", Items.COBBLESTONE, "romper", "#minecraft:base_stone_overworld", 20000, 60, 400, 30),
            m("lenadores", "Leñadores", "Talen troncos de cualquier árbol.", Items.OAK_LOG, "romper", "#minecraft:logs", 4000, 60, 400, 30),
            m("carbon", "Vetas de carbón", "Minen carbón.", Items.COAL, "romper", "#forge:ores/coal", 1200, 50, 350, 25),
            m("hierro", "Hierro para el clan", "Minen hierro.", Items.RAW_IRON, "romper", "#forge:ores/iron", 700, 70, 450, 35),
            m("cobre", "Brillo de cobre", "Minen cobre.", Items.RAW_COPPER, "romper", "#forge:ores/copper", 900, 50, 350, 25),
            m("excavadores", "Excavadores", "Caven tierra, arena y grava.", Items.IRON_SHOVEL, "romper", "#minecraft:mineable/shovel", 12000, 50, 350, 25),
            m("cosecha", "Gran cosecha", "Cosechen cultivos maduros.", Items.WHEAT, "cosechar", "#minecraft:crops", 5000, 60, 400, 30),
            m("cazadores", "Noche de cacería", "Derroten monstruos.", Items.BONE, "matar", "hostil", 1200, 70, 450, 35),
            m("zombis", "Plaga de zombis", "Derroten zombis.", Items.ROTTEN_FLESH, "matar", "minecraft:zombie", 500, 50, 350, 25),
            m("esqueletos", "Huesos al viento", "Derroten esqueletos.", Items.ARROW, "matar", "minecraft:skeleton", 400, 50, 350, 25),
            m("aranas", "Telarañas", "Derroten arañas.", Items.STRING, "matar", "minecraft:spider", 350, 50, 350, 25),
            m("creepers", "Sin explosiones", "Derroten creepers.", Items.GUNPOWDER, "matar", "minecraft:creeper", 200, 60, 400, 30),
            m("pesca", "Flota pesquera", "Pesquen lo que sea.", Items.COD, "pescar", "*", 350, 60, 400, 30),
            m("granja", "Granja del clan", "Críen animales.", Items.WHEAT_SEEDS, "criar", "*", 250, 50, 350, 25),
            m("antorchas", "Luz para todos", "Fabriquen antorchas.", Items.TORCH, "fabricar", "minecraft:torch", 1500, 40, 300, 20),
            m("herramientas", "Forja del clan", "Fabriquen herramientas.", Items.IRON_PICKAXE, "fabricar", "#forge:tools", 120, 60, 400, 30),
            m("pan", "Panadería", "Hagan pan.", Items.BREAD, "fabricar", "minecraft:bread", 1000, 40, 300, 20),
            m("lingotes", "Fundición", "Fundan lingotes.", Items.IRON_INGOT, "fundir", "#forge:ingots", 1500, 60, 400, 30),
            m("constructores", "Constructores", "Coloquen bloques nuevos.", Items.BRICKS, "colocar", "*", 15000, 60, 400, 30),
            m("encantadores", "Biblioteca arcana", "Encanten objetos.", Items.ENCHANTED_BOOK, "encantar", "*", 120, 70, 450, 35));

    private static Mission m(String id, String name, String desc, net.minecraft.world.item.Item icon, String type, String target, int amount,
                             long coins, long xp, long prestige) {
        return new Mission(id, name, desc, new ItemStack(icon), type, target, amount, coins, xp, prestige);
    }

    private PadClanProgress() {}

    // ---------------------------------------------------------------------------------------------------------------
    // Monedas, nivel, prestigio
    // ---------------------------------------------------------------------------------------------------------------

    public static long coins(JsonObject c) {
        return c == null ? 0 : Math.max(0, TFJson.num(c, "monedasClan", 0));
    }

    public static void addCoins(JsonObject c, long n) {
        if (c == null || n == 0) return;
        c.addProperty("monedasClan", Math.max(0, coins(c) + n));
        PadClans.STORE_IMPL.changed();
    }

    /** Gasta monedas del clan; false si no hay bastantes. */
    public static boolean takeCoins(JsonObject c, long n) {
        if (c == null || n < 0 || coins(c) < n) return false;
        c.addProperty("monedasClan", coins(c) - n);
        PadClans.STORE_IMPL.changed();
        return true;
    }

    /** ¿Ve y gasta las monedas del clan? El líder y los colíderes. */
    public static boolean treasurer(JsonObject c, UUID uuid) {
        String r = PadClans.role(c, uuid);
        return r.equals("lider") || r.equals("colider");
    }

    /** Las monedas del clan de ese jugador si es líder o colíder; -1 si no (lo que sale arriba en su pad). */
    public static long walletOf(UUID uuid) {
        String id = PadClans.clanIdOf(uuid);
        if (id == null) return -1;
        JsonObject c = PadClans.clan(id);
        return treasurer(c, uuid) ? coins(c) : -1;
    }

    public static int level(JsonObject c) {
        return (int) Math.max(1, Math.min(MAX_LEVEL, TFJson.num(c, "nivel", 1)));
    }

    public static long xp(JsonObject c) {
        return Math.max(0, TFJson.num(c, "xp", 0));
    }

    /** XP para pasar del nivel dado al siguiente. */
    public static long xpFor(int level) {
        return Math.round(1000 * Math.pow(1.35, Math.max(0, level - 1)) / 50.0) * 50;
    }

    public static long prestige(JsonObject c) {
        return Math.max(0, TFJson.num(c, "prestigio", 0));
    }

    public static void addPrestige(JsonObject c, long n) {
        if (c == null || n == 0) return;
        c.addProperty("prestigio", Math.max(0, prestige(c) + n));
        PadClans.STORE_IMPL.changed();
    }

    /** Las plazas del clan: 20 y dos más por nivel, hasta 40. */
    public static int maxMembers(JsonObject c) {
        return Math.min(40, 20 + 2 * (level(c) - 1));
    }

    /** Suma XP al clan y sube los niveles que toquen (cada uno da 100 × nivel monedas del clan). */
    public static void addXp(MinecraftServer server, JsonObject c, long n) {
        if (c == null || n <= 0) return;
        int level = level(c);
        long xp = xp(c) + n;
        while (level < MAX_LEVEL && xp >= xpFor(level)) {
            xp -= xpFor(level);
            level++;
            long reward = 100L * level;
            addCoins(c, reward);
            tell(server, c, "¡El clan sube al nivel " + level + "! +" + reward + " Fantastic Clan Coins y ya caben " + Math.min(40, 20 + 2 * (level - 1))
                    + " miembros.", ChatFormatting.AQUA);
        }
        if (level >= MAX_LEVEL) xp = 0;
        c.addProperty("nivel", level);
        c.addProperty("xp", xp);
        PadClans.STORE_IMPL.changed();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Insignias
    // ---------------------------------------------------------------------------------------------------------------

    public record Badge(String id, String name) {
        public String image() {
            return net.tierrasfantasticas.tfclient.pad.PadView.padImage("insignias/" + id);
        }
    }

    private static List<Badge> badges;

    /** Las insignias (assets/tfclient/pad/insignias.json, del propio jar: también en el servidor). */
    public static List<Badge> badges() {
        if (badges != null) return badges;
        List<Badge> out = new ArrayList<>();
        try (InputStream in = PadClanProgress.class.getResourceAsStream("/assets/tfclient/pad/insignias.json")) {
            if (in != null) {
                for (JsonElement e : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray()) {
                    out.add(new Badge(e.getAsJsonObject().get("id").getAsString(), e.getAsJsonObject().get("nombre").getAsString()));
                }
            }
        } catch (Exception e) {
            TFClient.LOGGER.warn("TF Clanes: no se pudo leer la lista de insignias", e);
        }
        if (out.isEmpty()) out.add(new Badge("espada_oro", "Espada dorada"));
        badges = out;
        return out;
    }

    public static Badge badge(JsonObject c) {
        String id = TFJson.str(c, "insignia", "");
        for (Badge b : badges()) if (b.id().equals(id)) return b;
        return badges().get(0);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Misiones del clan
    // ---------------------------------------------------------------------------------------------------------------

    /** La semana de ahora (desde el día 0 de las rotaciones). */
    static long week() {
        return Math.floorDiv(net.tierrasfantasticas.tfclient.util.TFRotation.day(), 7);
    }

    /** La meta de una misión para un clan de n miembros (la base es para 5; de x0,7 a x2,5), redondeada. */
    public static long goal(Mission m, int members) {
        double f = Math.max(0.7, Math.min(2.5, 0.5 + 0.1 * members));
        long g = Math.round(m.amount() * f);
        return g >= 1000 ? Math.round(g / 100.0) * 100 : g >= 100 ? Math.round(g / 10.0) * 10 : g;
    }

    /** Las misiones de esta semana del clan (se eligen el primer día que se miran y se guardan con su meta). */
    static JsonObject missions(String id, JsonObject c) {
        JsonObject ms = TFJson.obj(c, "misiones");
        long w = week();
        if (!c.has("misiones") || TFJson.num(ms, "semana", -1) != w) {
            ms = new JsonObject();
            ms.addProperty("semana", w);
            List<Mission> pool = new ArrayList<>(POOL);
            Collections.shuffle(pool, new Random(w * 1_000_003L + id.hashCode()));
            JsonArray list = new JsonArray();
            int n = PadClans.members(c).size();
            for (Mission m : pool.subList(0, Math.min(MISSIONS_PER_WEEK, pool.size()))) {
                JsonObject o = new JsonObject();
                o.addProperty("id", m.id());
                o.addProperty("meta", goal(m, n));
                o.addProperty("progreso", 0.0);
                o.addProperty("hecha", false);
                list.add(o);
            }
            ms.add("lista", list);
            ms.add("aportes", new JsonObject());
            c.add("misiones", ms);
            PadClans.STORE_IMPL.changed();
        }
        return ms;
    }

    static Mission byId(String id) {
        for (Mission m : POOL) if (m.id().equals(id)) return m;
        return null;
    }

    /**
     * Algo que hizo un jugador (desde TFJobs.record, con las mismas reglas que los oficios y las misiones del pad):
     * avanza las misiones de su clan y, a la mitad, las de sus aliados.
     */
    public static void record(ServerPlayer player, String type, Predicate<TFJobsConfig.Target> match, int amount) {
        String id = PadClans.clanIdOf(player.getUUID());
        if (id == null || amount <= 0) return;
        JsonObject c = PadClans.clan(id);
        if (c == null) return;
        advance(player.getServer(), id, c, player.getUUID(), type, match, amount);
        for (String ally : PadClans.allies(c)) {
            JsonObject a = PadClans.clan(ally);
            if (a != null) advance(player.getServer(), ally, a, null, type, match, amount * ALLY_SHARE);
        }
    }

    private static void advance(MinecraftServer server, String id, JsonObject c, UUID who, String type, Predicate<TFJobsConfig.Target> match,
                                double amount) {
        JsonObject ms = missions(id, c);
        JsonArray list = ms.has("lista") ? ms.getAsJsonArray("lista") : new JsonArray();
        for (JsonElement e : list) {
            JsonObject o = e.getAsJsonObject();
            if (TFJson.bool(o, "hecha", false)) continue;
            Mission m = byId(TFJson.str(o, "id", ""));
            if (m == null || !m.type().equals(type) || !match.test(new TFJobsConfig.Target(m.target()))) continue;
            double meta = TFJson.dec(o, "meta", m.amount());
            double now = Math.min(meta, TFJson.dec(o, "progreso", 0) + amount);
            o.addProperty("progreso", now);
            if (who != null) {
                JsonObject ap = TFJson.obj(ms, "aportes");
                ap.addProperty(who.toString(), TFJson.dec(ap, who.toString(), 0) + amount);
                ms.add("aportes", ap);
            }
            PadClans.STORE_IMPL.changed();
            if (now >= meta) complete(server, c, ms, o, m);
        }
    }

    private static void complete(MinecraftServer server, JsonObject c, JsonObject ms, JsonObject o, Mission m) {
        o.addProperty("hecha", true);
        addCoins(c, m.coins());
        addPrestige(c, m.prestige());
        // una moneda verde para cada miembro que aportó algo esta semana
        JsonObject ap = TFJson.obj(ms, "aportes");
        int greens = 0;
        for (String key : ap.keySet()) {
            try {
                UUID u = UUID.fromString(key);
                if (!PadClans.members(c).contains(u) || TFJson.dec(ap, key, 0) <= 0) continue;
                PadGreen.add(server, u, 1);
                greens++;
            } catch (IllegalArgumentException ignored) {
                // una clave rara: se salta
            }
        }
        tell(server, c, "Misión del clan cumplida: «" + m.name() + "». +" + m.coins() + " Fantastic Clan Coins, +" + m.xp() + " XP del clan, +"
                + m.prestige() + " de prestigio" + (greens > 0 ? " y una moneda verde para los " + greens + " que aportaron" : "") + ".", ChatFormatting.GREEN);
        addXp(server, c, m.xp());
    }

    /** Un aviso a los del clan conectados. */
    static void tell(MinecraftServer server, JsonObject c, String text, ChatFormatting color) {
        if (server == null) return;
        for (UUID u : PadClans.members(c)) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p == null) continue;
            p.sendSystemMessage(Component.literal("[Clan] ").withStyle(ChatFormatting.GOLD).append(Component.literal(text).withStyle(color)));
            p.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.5F, 1.4F);
        }
    }

    /** Los que más aportaron esta semana (nombre → cantidad), de más a menos. */
    static Map<UUID, Double> contributors(JsonObject ms) {
        JsonObject ap = TFJson.obj(ms, "aportes");
        List<Map.Entry<UUID, Double>> list = new ArrayList<>();
        for (String k : ap.keySet()) {
            try {
                list.add(Map.entry(UUID.fromString(k), TFJson.dec(ap, k, 0)));
            } catch (IllegalArgumentException ignored) {
                // se salta
            }
        }
        list.sort(Map.Entry.<UUID, Double>comparingByValue().reversed());
        Map<UUID, Double> out = new LinkedHashMap<>();
        for (var e : list) out.put(e.getKey(), e.getValue());
        return out;
    }

    /** Para el ranking de clanes: todos (id → clan), por prestigio. */
    public static List<Map.Entry<String, JsonObject>> byPrestige() {
        List<Map.Entry<String, JsonObject>> out = new ArrayList<>();
        for (Map.Entry<String, JsonElement> e : PadClans.clans().entrySet()) {
            if (e.getValue().isJsonObject()) out.add(Map.entry(e.getKey(), e.getValue().getAsJsonObject()));
        }
        out.sort((a, b) -> Long.compare(prestige(b.getValue()), prestige(a.getValue())));
        return out;
    }

    /** Para el pad: el servidor donde está (para los avisos). */
    static MinecraftServer server() {
        return PadServer.server();
    }
}
