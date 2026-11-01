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
    /** (1.3.158) Estos números y las listas vienen de config/tfclient/clanes.json ({@link PadClanConfig}); aquí, lo de fábrica. */
    public static int MAX_LEVEL = 20, MISSIONS_PER_WEEK = 4;
    /** Lo que suma a las misiones lo que hace un clan aliado (la mitad). */
    static double ALLY_SHARE = 0.5;

    /** Una misión de clan: lo que pide (como los oficios: tipo y objetivo), la meta base para 5 miembros y su premio. */
    public record Mission(String id, String name, String desc, ItemStack icon, String type, String target, int amount, long coins,
                          long xp, long prestige) {}

    /**
     * Las misiones de clan (inicio del juego: nada del Nether ni del End; todas se pueden cumplir con lo del Overworld).
     * La meta es para un clan de 5; cambia con los miembros ({@link #goal}).
     * (1.3.159, el dueño: «las misiones hazlas un poco más exigentes, porque las hacen en grupo») las metas, x1,6 de las
     * de la 1.3.156 ({@link #OLD_AMOUNTS}: clanes.json se pone al día solo en las que no se tocaron).
     */
    public static final List<Mission> FACTORY = List.of(
            m("cantera", "Cantera del clan", "Rompan piedra entre todos.", Items.COBBLESTONE, "romper", "#minecraft:base_stone_overworld", 32000, 1, 400, 30),
            m("lenadores", "Leñadores", "Talen troncos de cualquier árbol.", Items.OAK_LOG, "romper", "#minecraft:logs", 6400, 1, 400, 30),
            m("carbon", "Vetas de carbón", "Minen carbón.", Items.COAL, "romper", "#forge:ores/coal", 2000, 1, 350, 25),
            m("hierro", "Hierro para el clan", "Minen hierro.", Items.RAW_IRON, "romper", "#forge:ores/iron", 1100, 2, 450, 35),
            m("cobre", "Brillo de cobre", "Minen cobre.", Items.RAW_COPPER, "romper", "#forge:ores/copper", 1500, 1, 350, 25),
            m("excavadores", "Excavadores", "Caven tierra, arena y grava.", Items.IRON_SHOVEL, "romper", "#minecraft:mineable/shovel", 19000, 1, 350, 25),
            m("cosecha", "Gran cosecha", "Cosechen cultivos maduros.", Items.WHEAT, "cosechar", "#minecraft:crops", 8000, 1, 400, 30),
            m("cazadores", "Noche de cacería", "Derroten monstruos.", Items.BONE, "matar", "hostil", 2000, 2, 450, 35),
            m("zombis", "Plaga de zombis", "Derroten zombis.", Items.ROTTEN_FLESH, "matar", "minecraft:zombie", 800, 1, 350, 25),
            m("esqueletos", "Huesos al viento", "Derroten esqueletos.", Items.ARROW, "matar", "minecraft:skeleton", 650, 1, 350, 25),
            m("aranas", "Telarañas", "Derroten arañas.", Items.STRING, "matar", "minecraft:spider", 550, 1, 350, 25),
            m("creepers", "Sin explosiones", "Derroten creepers.", Items.GUNPOWDER, "matar", "minecraft:creeper", 320, 2, 400, 30),
            m("pesca", "Flota pesquera", "Pesquen lo que sea.", Items.COD, "pescar", "*", 560, 1, 400, 30),
            m("granja", "Granja del clan", "Críen animales.", Items.WHEAT_SEEDS, "criar", "*", 400, 1, 350, 25),
            m("antorchas", "Luz para todos", "Fabriquen antorchas.", Items.TORCH, "fabricar", "minecraft:torch", 2400, 1, 300, 20),
            m("herramientas", "Forja del clan", "Fabriquen herramientas.", Items.IRON_PICKAXE, "fabricar", "#forge:tools", 200, 1, 400, 30),
            m("pan", "Panadería", "Hagan pan.", Items.BREAD, "fabricar", "minecraft:bread", 1600, 1, 300, 20),
            m("lingotes", "Fundición", "Fundan lingotes.", Items.IRON_INGOT, "fundir", "#forge:ingots", 2400, 2, 400, 30),
            m("constructores", "Constructores", "Coloquen bloques nuevos.", Items.BRICKS, "colocar", "*", 24000, 1, 400, 30),
            m("encantadores", "Biblioteca arcana", "Encanten objetos.", Items.ENCHANTED_BOOK, "encantar", "*", 200, 2, 450, 35));

    /** Las metas de fábrica hasta la 1.3.158 (para poner al día clanes.json: ver PadClanConfig.load). */
    static final java.util.Map<String, Integer> OLD_AMOUNTS = java.util.Map.ofEntries(
            java.util.Map.entry("cantera", 20000),
            java.util.Map.entry("lenadores", 4000),
            java.util.Map.entry("carbon", 1200),
            java.util.Map.entry("hierro", 700),
            java.util.Map.entry("cobre", 900),
            java.util.Map.entry("excavadores", 12000),
            java.util.Map.entry("cosecha", 5000),
            java.util.Map.entry("cazadores", 1200),
            java.util.Map.entry("zombis", 500),
            java.util.Map.entry("esqueletos", 400),
            java.util.Map.entry("aranas", 350),
            java.util.Map.entry("creepers", 200),
            java.util.Map.entry("pesca", 350),
            java.util.Map.entry("granja", 250),
            java.util.Map.entry("antorchas", 1500),
            java.util.Map.entry("herramientas", 120),
            java.util.Map.entry("pan", 1000),
            java.util.Map.entry("lingotes", 1500),
            java.util.Map.entry("constructores", 15000),
            java.util.Map.entry("encantadores", 120));

    /** Las que salen en las semanas nuevas (las activas de clanes.json). */
    public static List<Mission> POOL = FACTORY;
    /** Todas las de clanes.json, también las desactivadas (para las que ya tenía un clan esta semana). */
    static List<Mission> KNOWN = FACTORY;

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

    /** XP para pasar del nivel dado al siguiente (clanes.json: nivel.xpBase · nivel.xpCrece^(nivel−1)). */
    public static long xpFor(int level) {
        return PadClanConfig.xpFor(level);
    }

    public static long prestige(JsonObject c) {
        return Math.max(0, TFJson.num(c, "prestigio", 0));
    }

    public static void addPrestige(JsonObject c, long n) {
        if (c == null || n == 0) return;
        c.addProperty("prestigio", Math.max(0, prestige(c) + n));
        PadClans.STORE_IMPL.changed();
    }

    /** Las plazas del clan: 20 y dos más por nivel (hasta 40), más las que se compran en la tienda del clan (clanes.json: miembros). */
    public static int maxMembers(JsonObject c) {
        return seats(level(c), c);
    }

    static int seats(int level, JsonObject c) {
        return PadClanConfig.seats(level, TFJson.num(c, "plazasCompradas", 0));
    }

    /**
     * Las Clan Coins de subir al nivel dado. (El dueño, 31 oct 2026: «no pongas esa moneda inflada, que sea como las de
     * gacha: 1 o 2 máximo en recompensas».) 1, y 2 en los niveles 5, 10, 15 y 20.
     */
    public static long levelReward(int level) {
        return PadClanConfig.levelReward(level);
    }

    /** Suma XP al clan y sube los niveles que toquen (cada uno da 1 o 2 Clan Coins). */
    public static void addXp(MinecraftServer server, JsonObject c, long n) {
        if (c == null || n <= 0) return;
        int level = level(c);
        long xp = xp(c) + n;
        while (level < MAX_LEVEL && xp >= xpFor(level)) {
            xp -= xpFor(level);
            level++;
            long reward = levelReward(level);
            addCoins(c, reward);
            tell(server, c, "¡El clan sube al nivel " + level + "! +" + reward + " Fantastic Clan Coin" + (reward == 1 ? "" : "s") + " y ya caben "
                    + seats(level, c) + " miembros.", ChatFormatting.AQUA);
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
        double f = PadClanConfig.goalFactor(members);
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
        for (Mission m : KNOWN) if (m.id().equals(id)) return m;
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
        double boost = boosted(c) ? BOOST : 1.0;
        advance(player.getServer(), id, c, player.getUUID(), type, match, amount * boost);
        for (String ally : PadClans.allies(c)) {
            JsonObject a = PadClans.clan(ally);
            if (a != null) advance(player.getServer(), ally, a, null, type, match, amount * ALLY_SHARE * (boosted(a) ? BOOST : 1.0));
        }
        // (1.3.156) la guerra de clanes, si hay una en marcha
        PadClanWar.record(player, id, type, match, amount);
    }

    /** El potenciador de la tienda del clan: las misiones avanzan x1,5 (clanes.json: potenciador.multiplicador). */
    static double BOOST = 1.5;

    static boolean boosted(JsonObject c) {
        return c != null && TFJson.num(c, "potenciadorHasta", 0) > System.currentTimeMillis();
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
        // una moneda verde (clanes.json: misiones.verdes) para cada miembro que aportó algo esta semana
        JsonObject ap = TFJson.obj(ms, "aportes");
        long each = PadClanConfig.missionGreens();
        int greens = 0;
        for (String key : ap.keySet()) {
            try {
                UUID u = UUID.fromString(key);
                if (each <= 0 || !PadClans.members(c).contains(u) || TFJson.dec(ap, key, 0) <= 0) continue;
                PadGreen.add(server, u, each);
                greens++;
            } catch (IllegalArgumentException ignored) {
                // una clave rara: se salta
            }
        }
        tell(server, c, "Misión del clan cumplida: «" + m.name() + "». +" + m.coins() + " Fantastic Clan Coins, +" + m.xp() + " XP del clan, +"
                + m.prestige() + " de prestigio" + (greens > 0 ? " y " + greensText(each) + " para los " + greens + " que aportaron" : "") + ".", ChatFormatting.GREEN);
        addXp(server, c, m.xp());
    }

    /** «una moneda verde» o «3 monedas verdes» (para cada uno). */
    static String greensText(long each) {
        return each == 1 ? "una moneda verde" : each + " monedas verdes";
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

    // ---------------------------------------------------------------------------------------------------------------
    // Prueba en el juego (bosses/BossTestClanes)
    // ---------------------------------------------------------------------------------------------------------------

    /** {Clan Coins, nivel, xp, prestigio} del clan. */
    public static long[] testStats(String clanId) {
        JsonObject c = PadClans.clan(clanId);
        return c == null ? new long[4] : new long[] {coins(c), level(c), xp(c), prestige(c)};
    }

    public static void testSet(String clanId, long coins, int level, long xp, long prestige) {
        JsonObject c = PadClans.clan(clanId);
        if (c == null) return;
        c.addProperty("monedasClan", coins);
        c.addProperty("nivel", level);
        c.addProperty("xp", xp);
        c.addProperty("prestigio", prestige);
        c.remove("misiones");
        c.remove("potenciadorHasta");
        c.remove("compras");
        c.remove("plazasCompradas");
        PadClans.STORE_IMPL.changed();
    }

    /** Sube el clan del nivel en el que esté a «to» con la XP justa; devuelve las Clan Coins que ganó. */
    public static long testLevelTo(MinecraftServer server, String clanId, int to) {
        JsonObject c = PadClans.clan(clanId);
        if (c == null) return -1;
        long before = coins(c);
        long need = -xp(c);
        for (int l = level(c); l < to; l++) need += xpFor(l);
        addXp(server, c, Math.max(1, need));
        return coins(c) - before;
    }

    /**
     * Comprueba que cada objetivo de las misiones del clan y de las guerras existe y cuenta (TFMissionCheck.sample: algo
     * de verdad del juego que lo cumple). Devuelve los que fallan.
     */
    public static List<String> testValidate(MinecraftServer server) {
        List<String> bad = new ArrayList<>();
        List<Mission> all = new ArrayList<>(POOL);
        all.addAll(PadClanWar.POOL);
        for (Mission m : all) {
            Predicate<TFJobsConfig.Target> p = net.tierrasfantasticas.tfclient.jobs.TFMissionCheck.sample(server, m.type(), m.target());
            if (p == null || !p.test(new TFJobsConfig.Target(m.target()))) bad.add(m.id() + " (" + m.type() + " " + m.target() + ")");
        }
        return bad;
    }

    /** Cumple la primera misión de la semana que quede, con lo que haría el juego (sample). Devuelve su id o null. */
    public static String testCompleteMission(ServerPlayer player) {
        String id = PadClans.clanIdOf(player.getUUID());
        if (id == null) return null;
        JsonObject c = PadClans.clan(id);
        JsonObject ms = missions(id, c);
        for (JsonElement e : ms.getAsJsonArray("lista")) {
            JsonObject o = e.getAsJsonObject();
            if (TFJson.bool(o, "hecha", false)) continue;
            Mission m = byId(TFJson.str(o, "id", ""));
            if (m == null) continue;
            int left = (int) Math.ceil(TFJson.dec(o, "meta", m.amount()) - TFJson.dec(o, "progreso", 0));
            Predicate<TFJobsConfig.Target> p = net.tierrasfantasticas.tfclient.jobs.TFMissionCheck.sample(player.getServer(), m.type(), m.target());
            if (p == null) continue;
            record(player, m.type(), p, Math.max(1, left));
            return m.id();
        }
        return null;
    }

    /** El avance de la misión número i de la semana de ese clan (para ver lo que suman el potenciador y los aliados). */
    public static double testFirstProgress(String clanId, int i) {
        JsonObject c = PadClans.clan(clanId);
        if (c == null) return -1;
        JsonObject ms = missions(clanId, c);
        return TFJson.dec(ms.getAsJsonArray("lista").get(i).getAsJsonObject(), "progreso", 0);
    }

    /** Suma n a la misión número i de la semana del clan del jugador (como lo haría el juego). Devuelve su id. */
    public static String testAddFirst(ServerPlayer player, int i, int n) {
        String id = PadClans.clanIdOf(player.getUUID());
        if (id == null) return null;
        JsonObject c = PadClans.clan(id);
        Mission m = byId(TFJson.str(missions(id, c).getAsJsonArray("lista").get(i).getAsJsonObject(), "id", ""));
        if (m == null) return null;
        Predicate<TFJobsConfig.Target> p = net.tierrasfantasticas.tfclient.jobs.TFMissionCheck.sample(player.getServer(), m.type(), m.target());
        if (p != null) record(player, m.type(), p, n);
        return m.id();
    }

    /** Pone las misiones de esta semana del clan «to» iguales que las de «from» (para probar lo que suman los aliados). */
    public static void testCopyMissions(String from, String to) {
        JsonObject a = PadClans.clan(from), b = PadClans.clan(to);
        if (a == null || b == null) return;
        b.add("misiones", missions(from, a).deepCopy());
        JsonObject ms = b.getAsJsonObject("misiones");
        for (JsonElement e : ms.getAsJsonArray("lista")) {
            e.getAsJsonObject().addProperty("progreso", 0.0);
            e.getAsJsonObject().addProperty("hecha", false);
        }
        ms.add("aportes", new JsonObject());
        PadClans.STORE_IMPL.changed();
    }

    public static void testBoost(String clanId, boolean on) {
        JsonObject c = PadClans.clan(clanId);
        if (c == null) return;
        if (on) c.addProperty("potenciadorHasta", System.currentTimeMillis() + 60_000);
        else c.remove("potenciadorHasta");
        PadClans.STORE_IMPL.changed();
    }

    /** Para el pad: el servidor donde está (para los avisos). */
    static MinecraftServer server() {
        return PadServer.server();
    }
}
