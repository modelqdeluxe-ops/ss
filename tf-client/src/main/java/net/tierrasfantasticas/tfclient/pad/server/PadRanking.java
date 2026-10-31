/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.pad.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.tierrasfantasticas.tfclient.economy.TFEconomy;
import net.tierrasfantasticas.tfclient.economy.TFWallet;
import net.tierrasfantasticas.tfclient.jobs.TFJobsConfig;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.pad.PadView;
import net.tierrasfantasticas.tfclient.server.TFBots;

/**
 * Ranking: los 10 primeros en misiones, cazas, likes, exploraciones, dinero, muertes y mobs matados. (1.3.140, el dueño:
 * «pon rankings del que tiene más dinero, del que muere más veces y de muertes de mobs») DINERO sale con cualquier
 * economía (antes solo con la del TF Client): con Vault/EssentialsX, el saldo de cada uno al conectarse (se apunta cada
 * minuto) y el de los ficheros de EssentialsX para los demás. MUERTES y MOBS, de las estadísticas de Minecraft (también
 * lo de antes de esta versión).
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = net.tierrasfantasticas.tfclient.TFClient.MOD_ID)
public final class PadRanking {
    public static final PadServer.App APP = new App();
    /** Colores del podio: oro, plata y bronce. */
    private static final int[] PODIUM = {0xF6B628, 0xA9B8CC, 0xD08A4E};

    private PadRanking() {}

    static final class App implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String tab) {
            boolean coins = TFEconomy.mode() == TFEconomy.Mode.TF;
            String t = tab.isEmpty() ? "misiones" : tab;
            if (t.equals("monedas")) t = "dinero";
            PadView.Builder b = PadView.of("ranking").tab("misiones", "MISIONES").tab("cazas", "CAZAS").tab("likes", "LIKES")
                    .tab("exploraciones", "EXPLORAR").tab("dinero", "DINERO").tab("muertes", "MUERTES").tab("mobs", "MOBS")
                    .tab("clanes", "CLANES");
            b.selected(t).empty("Sin datos.");
            // (1.3.156, el dueño: «en el ranking del pad agregar una pestaña de clanes, su logo, info y prestigio»)
            if (t.equals("clanes")) {
                PadClanViews.ranking(b, player);
                return b.build();
            }
            List<Map.Entry<UUID, Long>> top;
            String unit;
            switch (t) {
                case "cazas" -> {
                    top = PadStats.top(PadStats.HUNTS, 10);
                    unit = " cazas";
                }
                case "likes" -> {
                    top = PadStats.top(PadStats.LIKES, 10);
                    unit = " likes";
                }
                case "exploraciones" -> {
                    top = PadStats.top(PadStats.EXPLORES, 10);
                    unit = " exploraciones";
                }
                case "dinero" -> {
                    top = coins ? TFWallet.get(player.getServer()).top(10) : moneyTop(player.getServer(), 10);
                    unit = "";
                }
                case "muertes" -> {
                    top = statTop(player.getServer(), net.minecraft.stats.Stats.DEATHS, 10);
                    unit = " muertes";
                }
                case "mobs" -> {
                    top = statTop(player.getServer(), net.minecraft.stats.Stats.MOB_KILLS, 10);
                    unit = " mobs";
                }
                default -> {
                    top = PadStats.top(PadStats.MISSIONS, 10);
                    unit = " misiones";
                }
            }
            // (1.3.117, el dueño: «los bots no tienen ranking; y no veo su nombre», un «2º ?») los bots que han entrado,
            // con lo poco que han hecho (TFBots.rankValue), y cada nombre de donde se sepa
            top = withBots(top, t.equals("dinero") ? "monedas" : t);
            // 1.4: el podio: los tres primeros en tarjetas con su cabeza (oro, plata y bronce, el primero en medio), y
            // del cuarto en adelante, en filas
            int podium = Math.min(3, top.size());
            int[] order = podium == 3 ? new int[] {1, 0, 2} : podium == 2 ? new int[] {0, 1} : new int[] {0};
            for (int k = 0; k < podium && top.size() > 0; k++) {
                int i = order[k];
                UUID u = top.get(i).getKey();
                long v = top.get(i).getValue();
                boolean me = u.equals(player.getUUID());
                String name = name(player, u);
                String value = t.equals("dinero") ? PadView.money(v) : v + unit;
                b.card(PadClans.head(u, name), (i + 1) + "º " + name + (me ? " (tú)" : ""), PODIUM[i], value,
                        i == 0 ? PadView.TONE_GOLD : PadView.TONE_BLUE, -1, i == 0 ? "TOP 1" : "", "", me);
            }
            for (int i = podium; i < top.size(); i++) {
                UUID u = top.get(i).getKey();
                long v = top.get(i).getValue();
                ItemStack medal = new ItemStack(i == 0 ? Items.GOLD_INGOT : i == 1 ? Items.IRON_INGOT : i == 2 ? Items.COPPER_INGOT : Items.PAPER);
                String value = t.equals("dinero") ? PadView.moneyExact(v) : v + unit;
                boolean me = u.equals(player.getUUID());
                // el valor, en su etiqueta a la derecha (oro con la moneda si son monedas; azul si no)
                b.row(new PadView.Row(medal, (i + 1) + ". " + name(player, u) + (me ? " (tú)" : ""), me ? 0xC27A10 : 0x18265C,
                        List.of(), -1, value, null, null).selected(me));
            }
            return b.build();
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Dinero (con Vault / EssentialsX) y estadísticas de Minecraft
    // ---------------------------------------------------------------------------------------------------------------

    /** El saldo de cada jugador la última vez que se vio conectado (pad-estadisticas.json, «saldo»). */
    static final String BALANCE = "saldo";

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        MinecraftServer server = PadServer.server();
        if (server == null || server.getTickCount() % 1200 != 600) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) snapshot(p);
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) snapshot(p);
    }

    static void snapshot(ServerPlayer p) {
        if (TFBots.ofId(p.getUUID()) != null || p instanceof net.minecraftforge.common.util.FakePlayer) return;
        java.util.OptionalLong bal = TFEconomy.balance(p.getServer(), p.getUUID());
        if (bal.isEmpty()) return;
        PadStats.name(p);
        com.google.gson.JsonObject o = PadStats.STORE_IMPL.player(p.getUUID());
        if (JsonStore.num(o, BALANCE) != bal.getAsLong() || !o.has(BALANCE)) {
            o.addProperty(BALANCE, bal.getAsLong());
            PadStats.STORE_IMPL.changed();
        }
    }

    private static Map<UUID, Long> essCache = Map.of();
    private static long essAt;

    /** Los saldos de los ficheros de EssentialsX (plugins/Essentials/userdata/&lt;uuid&gt;.yml, «money»), cada minuto. */
    private static Map<UUID, Long> essentialsBalances(MinecraftServer server) {
        if (System.currentTimeMillis() - essAt < 60_000) return essCache;
        essAt = System.currentTimeMillis();
        Map<UUID, Long> out = new java.util.HashMap<>();
        java.nio.file.Path dir = server.getServerDirectory().toPath()
                .resolve(net.tierrasfantasticas.tfclient.server.TFServerConfig.essentialsConfig()).normalize().resolveSibling("userdata");
        if (java.nio.file.Files.isDirectory(dir)) {
            try (var files = java.nio.file.Files.list(dir)) {
                files.forEach(f -> {
                    String n = f.getFileName().toString();
                    if (!n.endsWith(".yml")) return;
                    try {
                        UUID u = UUID.fromString(n.substring(0, n.length() - 4));
                        for (String line : java.nio.file.Files.readAllLines(f, java.nio.charset.StandardCharsets.UTF_8)) {
                            String l = line.trim();
                            if (!l.startsWith("money:")) continue;
                            String v = l.substring(6).trim().replace("'", "").replace("\"", "");
                            out.put(u, (long) Math.floor(Double.parseDouble(v)));
                            break;
                        }
                    } catch (Exception ignored) {
                        // otro fichero o un saldo raro: fuera
                    }
                });
            } catch (Exception ignored) {
                // sin carpeta legible
            }
        }
        return essCache = out;
    }

    /** Los que más dinero tienen: el de EssentialsX, encima lo apuntado al verlos y encima el de los conectados ahora. */
    static List<Map.Entry<UUID, Long>> moneyTop(MinecraftServer server, int n) {
        Map<UUID, Long> all = new java.util.HashMap<>(essentialsBalances(server));
        com.google.gson.JsonElement players = PadStats.STORE_IMPL.root.get("jugadores");
        if (players != null && players.isJsonObject()) {
            for (Map.Entry<String, com.google.gson.JsonElement> e : players.getAsJsonObject().entrySet()) {
                if (!e.getValue().isJsonObject() || !e.getValue().getAsJsonObject().has(BALANCE)) continue;
                try {
                    all.put(UUID.fromString(e.getKey()), JsonStore.num(e.getValue().getAsJsonObject(), BALANCE));
                } catch (IllegalArgumentException ignored) {
                    // clave rara
                }
            }
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            java.util.OptionalLong bal = TFEconomy.balance(server, p.getUUID());
            if (bal.isPresent()) all.put(p.getUUID(), bal.getAsLong());
        }
        return sorted(all, n);
    }

    private static Map<String, Map<UUID, Long>> statCache = new java.util.HashMap<>();
    private static long statAt;

    /**
     * Los primeros en una estadística de Minecraft (minecraft:custom): la de los conectados, al momento; la de los demás,
     * de &lt;mundo&gt;/stats/&lt;uuid&gt;.json (se lee cada minuto).
     */
    static List<Map.Entry<UUID, Long>> statTop(MinecraftServer server, net.minecraft.resources.ResourceLocation stat, int n) {
        if (System.currentTimeMillis() - statAt > 60_000) {
            statAt = System.currentTimeMillis();
            statCache = readStats(server);
        }
        Map<UUID, Long> all = new java.util.HashMap<>(statCache.getOrDefault(stat.toString(), Map.of()));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (TFBots.ofId(p.getUUID()) != null) continue;
            all.put(p.getUUID(), (long) p.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(stat)));
        }
        all.values().removeIf(v -> v <= 0);
        return sorted(all, n);
    }

    private static Map<String, Map<UUID, Long>> readStats(MinecraftServer server) {
        Map<String, Map<UUID, Long>> out = new java.util.HashMap<>();
        java.nio.file.Path dir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_STATS_DIR);
        if (!java.nio.file.Files.isDirectory(dir)) return out;
        try (var files = java.nio.file.Files.list(dir)) {
            files.forEach(f -> {
                String name = f.getFileName().toString();
                if (!name.endsWith(".json")) return;
                try {
                    UUID u = UUID.fromString(name.substring(0, name.length() - 5));
                    com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(
                            java.nio.file.Files.readString(f, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                    com.google.gson.JsonObject stats = root.getAsJsonObject("stats");
                    com.google.gson.JsonObject custom = stats == null ? null : stats.getAsJsonObject("minecraft:custom");
                    if (custom == null) return;
                    for (Map.Entry<String, com.google.gson.JsonElement> e : custom.entrySet()) {
                        out.computeIfAbsent(e.getKey(), k -> new java.util.HashMap<>()).put(u, e.getValue().getAsLong());
                    }
                } catch (Exception ignored) {
                    // fichero de otra cosa o roto
                }
            });
        } catch (Exception ignored) {
            // sin carpeta legible
        }
        return out;
    }

    /** Para la prueba en el juego: los primeros de dinero, muertes o mobs. */
    public static List<Map.Entry<UUID, Long>> testTop(MinecraftServer server, String what) {
        return switch (what) {
            case "dinero" -> TFEconomy.mode() == TFEconomy.Mode.TF ? TFWallet.get(server).top(10) : moneyTop(server, 10);
            case "muertes" -> statTop(server, net.minecraft.stats.Stats.DEATHS, 10);
            default -> statTop(server, net.minecraft.stats.Stats.MOB_KILLS, 10);
        };
    }

    private static List<Map.Entry<UUID, Long>> sorted(Map<UUID, Long> all, int n) {
        List<Map.Entry<UUID, Long>> list = new ArrayList<>(all.entrySet());
        list.sort(Comparator.comparingLong((Map.Entry<UUID, Long> e) -> e.getValue()).reversed());
        return list.size() > n ? new ArrayList<>(list.subList(0, n)) : list;
    }

    /** Los 10 primeros contando los bots que han entrado alguna vez; fuera los que no tienen nombre (no se sabe quién son). */
    private static List<Map.Entry<UUID, Long>> withBots(List<Map.Entry<UUID, Long>> real, String what) {
        List<Map.Entry<UUID, Long>> all = new ArrayList<>();
        for (Map.Entry<UUID, Long> e : real) if (TFBots.ofId(e.getKey()) == null) all.add(e);
        List<String> jobs = new ArrayList<>(TFJobsConfig.jobs.keySet());
        for (TFBots.Bot bot : TFBots.known()) {
            long v = TFBots.rankValue(bot, what, jobs, TFJobsConfig.maxJobs);
            if (v > 0) all.add(Map.entry(bot.id(), v));
        }
        all.removeIf(e -> name(null, e.getKey()) == null);
        all.sort(Comparator.comparingLong((Map.Entry<UUID, Long> e) -> e.getValue()).reversed());
        return all.size() > 10 ? all.subList(0, 10) : all;
    }

    /** El nombre: el del pad, el del jugador conectado, el del bot o el de la caché de perfiles del servidor (null si no). */
    private static String name(ServerPlayer viewer, UUID u) {
        String n = PadStats.nameOf(u);
        if (!"?".equals(n)) return n;
        TFBots.Bot bot = TFBots.ofId(u);
        if (bot != null) return bot.name();
        MinecraftServer server = viewer != null ? viewer.getServer() : PadServer.server();
        if (server == null) return null;
        ServerPlayer p = server.getPlayerList().getPlayer(u);
        if (p != null) return p.getGameProfile().getName();
        var cache = server.getProfileCache();
        var prof = cache == null ? java.util.Optional.<com.mojang.authlib.GameProfile>empty() : cache.get(u);
        return prof.map(com.mojang.authlib.GameProfile::getName).orElse(null);
    }
}
