package net.tierrasfantasticas.tfclient.pad.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.economy.TFEconomy;
import net.tierrasfantasticas.tfclient.jobs.TFJobsConfig;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.pad.PadView;
import net.tierrasfantasticas.tfclient.pad.TFPadNet;
import net.tierrasfantasticas.tfclient.util.TFJson;
import net.tierrasfantasticas.tfclient.util.TFRotation;

/**
 * Misiones (3 diarias y 3 semanales por jugador) y Cazas (4 objetivos para todo el servidor que cambian cada 12 h).
 * Rotan por temporadas de 90 días (TFRotation): en una temporada no se repite ninguna igual y cantidad y premio suben
 * poco a poco (de x1 a x2,5). La lista de diarias y la de presas de caza se cambian en la config (mobs de mods incluidos).
 * Cuentan lo mismo que los oficios (romper, cosechar, matar, pescar, fabricar, fundir, preparar, colocar, criar…) y con
 * sus mismas reglas: no cuentan los bloques que puso el jugador ni los monstruos de spawner, ni en creativo.
 * Se configuran en config/tfclient/misiones.json (se crea con estas listas la primera vez).
 */
public final class PadMissions {
    /** Una misión de la lista: qué hay que hacer, cuántas veces, cuánto paga y su icono. */
    record Def(String id, String name, String desc, String type, String target, int amount, long coins, String icon) {
        Item item() {
            Item i = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(icon));
            return i == null || i == Items.AIR ? Items.PAPER : i;
        }
    }

    private static final List<Def> DAILY = new ArrayList<>();
    private static final List<Def> HUNT = new ArrayList<>();
    private static int weeklyTimes = 5;
    private static int weeklyPay = 6;

    static final Store STORE_IMPL = new Store();
    public static final PadServer.Store STORE = STORE_IMPL;
    public static final PadServer.App MISSIONS = new Missions();
    public static final PadServer.App HUNTS = new Hunts();

    private PadMissions() {}

    // ---------------------------------------------------------------------------------------------------------------
    // Qué toca hoy / esta semana / estas 12 horas
    // ---------------------------------------------------------------------------------------------------------------

    static long day() {
        return LocalDate.now(ZoneId.systemDefault()).toEpochDay();
    }

    static long week() {
        return Math.floorDiv(day() + 3, 7); // semanas de lunes a domingo
    }

    static long period() {
        return System.currentTimeMillis() / (12L * 3600 * 1000);
    }

    /**
     * Las k de la lista para el periodo, con su cantidad y su premio de esa vuelta de la temporada. El id lleva la
     * vuelta («zombis~2») para que el progreso de una no pase a la siguiente. times/pay: multiplicadores (semanales).
     */
    private static List<Def> rotate(List<Def> pool, int k, long period, long perSeason, long salt, long offset, int times, int pay) {
        List<Def> out = new ArrayList<>();
        for (TFRotation.Pick p : TFRotation.pick(pool.size(), k, period, perSeason, salt, offset)) {
            Def d = pool.get(p.index());
            int amount = TFRotation.amount(d.amount * times, p.factor());
            long coins = TFRotation.coins(d.coins * (double) pay, p.factor());
            out.add(new Def(d.id + "~" + p.round(), d.name, describe(d, amount), d.type, d.target, amount, coins, d.icon));
        }
        return out;
    }

    /** La descripción con la cantidad: «{n}» se cambia por ella (o, si no lo tiene, el número que trajera). */
    private static String describe(Def d, int amount) {
        if (d.desc.contains("{n}")) return d.desc.replace("{n}", Integer.toString(amount));
        return d.desc.replaceFirst("\\b" + d.amount + "\\b", Integer.toString(amount));
    }

    static List<Def> daily(UUID uuid) {
        return rotate(DAILY, 3, TFRotation.day(), TFRotation.SEASON_DAYS, 11, uuid.getMostSignificantBits() >>> 1, 1, 1);
    }

    /** Las semanales cambian el lunes, como su progreso (week()); la temporada, cada 13 semanas. */
    static List<Def> weekly(UUID uuid) {
        return rotate(DAILY, 3, week(), 13, 23, uuid.getLeastSignificantBits() >>> 1, weeklyTimes, weeklyPay);
    }

    /** Las cazas cambian cada 12 h (period()); la temporada, cada 180 medios días. */
    static List<Def> hunts() {
        return rotate(HUNT, 4, period(), TFRotation.SEASON_DAYS * 2L, 37, 0, 1, 1);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Progreso
    // ---------------------------------------------------------------------------------------------------------------

    /** Llama TFJobs con cada cosa que hace un jugador. */
    public static void record(ServerPlayer player, String type, Predicate<TFJobsConfig.Target> match, int amount) {
        if (DAILY.isEmpty() && HUNT.isEmpty() || player instanceof net.minecraftforge.common.util.FakePlayer) return;
        UUID uuid = player.getUUID();
        try {
            progress(player, "d", daily(uuid), type, match, amount);
            progress(player, "s", weekly(uuid), type, match, amount);
            progress(player, "c", hunts(), type, match, amount);
        } catch (RuntimeException ex) {
            // una misión mal escrita en la configuración nunca debe tumbar el servidor
            TFClient.LOGGER.warn("TF Pad: no se pudo contar una misión: {}", ex.toString());
        }
    }

    private static void progress(ServerPlayer player, String kind, List<Def> defs, String type,
                                 Predicate<TFJobsConfig.Target> match, int amount) {
        JsonObject slot = null;
        for (Def d : defs) {
            if (!d.type.equals(type) || !match.test(new TFJobsConfig.Target(d.target))) continue;
            if (slot == null) slot = STORE_IMPL.slot(player.getUUID(), kind);
            long[] st = STORE_IMPL.state(slot, d.id);
            if (st[0] >= d.amount) continue;
            st[0] = Math.min(d.amount, st[0] + amount);
            STORE_IMPL.put(slot, d.id, st);
            if (st[0] >= d.amount) {
                String what = kind.equals("c") ? "Caza completada" : "Misión completada";
                // 1.3.38: completarla da XP del TF Pass (pase.json: xp.diaria / semanal / caza)
                long xp = PadPass.missionDone(player, kind);
                player.displayClientMessage(Component.literal(what + " ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                        .append(Component.literal(d.name + " — reclama tus " + TFEconomy.format(d.coins) + " en el pad (C)"
                                + (xp > 0 ? " · +" + xp + " XP de TF Pass" : "")).withStyle(ChatFormatting.YELLOW)), true);
                player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.6F, 1.4F);
            }
        }
    }

    private static boolean claim(ServerPlayer player, String kind, Def d) {
        JsonObject slot = STORE_IMPL.slot(player.getUUID(), kind);
        long[] st = STORE_IMPL.state(slot, d.id);
        if (st[0] < d.amount || st[1] != 0) return false;
        // primero se paga: si la economía falla, la recompensa sigue pendiente
        if (d.coins > 0 && !TFEconomy.give(player.getServer(), player.getUUID(), player.getGameProfile().getName(), d.coins)) {
            TFPadNet.notice(player, "Error al pagar la recompensa.");
            return false;
        }
        st[1] = 1;
        STORE_IMPL.put(slot, d.id, st);
        PadStats.add(player, kind.equals("c") ? PadStats.HUNTS : PadStats.MISSIONS, 1);
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.6F, 1.0F);
        return true;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Vistas
    // ---------------------------------------------------------------------------------------------------------------

    static String until(ZonedDateTime next) {
        Duration d = Duration.between(ZonedDateTime.now(ZoneId.systemDefault()), next);
        long h = Math.max(0, d.toHours()), m = Math.max(0, d.toMinutesPart());
        if (h >= 24) return (h / 24) + " d " + (h % 24) + " h";
        return h > 0 ? h + " h " + m + " min" : m + " min";
    }

    private static PadView.Row row(ServerPlayer player, String kind, Def d) {
        long[] st = STORE_IMPL.state(STORE_IMPL.slot(player.getUUID(), kind), d.id);
        boolean done = st[0] >= d.amount, claimed = st[1] != 0;
        PadView.Btn btn = claimed ? PadView.Btn.off("HECHA") : done ? PadView.Btn.of("RECLAMAR", "reclamar:" + kind + ":" + d.id, PadView.GREEN) : null;
        List<String> lines = new ArrayList<>();
        long passXp = PadPass.xpOf(kind);
        lines.add(d.desc + "  ·  " + Math.min(st[0], d.amount) + "/" + d.amount + (passXp > 0 ? "  ·  +" + passXp + " XP de pase" : ""));
        return new PadView.Row(new ItemStack(d.item()), d.name, claimed ? 0x7E8CA8 : 0x18265C, lines,
                Math.min(1F, st[0] / (float) d.amount), "+" + PadView.money(d.coins), btn, null);
    }

    private static void claimAll(ServerPlayer player, String kind, List<Def> defs) {
        long total = 0;
        int n = 0;
        for (Def d : defs) {
            if (claim(player, kind, d)) {
                total += d.coins;
                n++;
            }
        }
        if (n > 0) TFPadNet.notice(player, "Cobraste " + TFEconomy.format(total) + " por " + n + (n == 1 ? " recompensa." : " recompensas."));
    }

    private static boolean claimable(ServerPlayer player, String kind, List<Def> defs) {
        JsonObject slot = STORE_IMPL.slot(player.getUUID(), kind);
        int n = 0;
        for (Def d : defs) {
            long[] st = STORE_IMPL.state(slot, d.id);
            if (st[0] >= d.amount && st[1] == 0) n++;
        }
        return n > 1;
    }

    private static Def find(List<Def> defs, String id) {
        for (Def d : defs) if (d.id.equals(id)) return d;
        return null;
    }

    static final class Missions implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String tab) {
            boolean week = tab.equals("semanales");
            List<Def> defs = week ? weekly(player.getUUID()) : daily(player.getUUID());
            ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
            ZonedDateTime next = week ? now.toLocalDate().plusDays(8 - now.getDayOfWeek().getValue()).atStartOfDay(now.getZone())
                    : now.toLocalDate().plusDays(1).atStartOfDay(now.getZone());
            PadView.Builder b = PadView.of("misiones").tab("diarias", "DIARIAS").tab("semanales", "SEMANALES")
                    .selected(week ? "semanales" : "diarias")
                    .header((week ? "Nuevas misiones en " : "Se renuevan en ") + until(next) + ".")
                    .empty("Sin misiones.");
            String kind = week ? "s" : "d";
            for (Def d : defs) b.row(row(player, kind, d));
            if (claimable(player, kind, defs)) b.footer(PadView.Btn.of("RECLAMAR TODO", "todo:" + kind, PadView.GREEN));
            return b.build();
        }

        @Override
        public String action(ServerPlayer player, String tab, String action, String text) {
            boolean week = tab.equals("semanales");
            List<Def> defs = week ? weekly(player.getUUID()) : daily(player.getUUID());
            String kind = week ? "s" : "d";
            if (action.startsWith("todo:")) {
                claimAll(player, kind, defs);
            } else if (action.startsWith("reclamar:" + kind + ":")) {
                Def d = find(defs, action.substring(("reclamar:" + kind + ":").length()));
                if (d != null && claim(player, kind, d)) TFPadNet.notice(player, "Cobraste " + TFEconomy.format(d.coins) + ": " + d.name + ".");
            }
            return null;
        }
    }

    static final class Hunts implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String tab) {
            List<Def> defs = hunts();
            long next = (period() + 1) * 12L * 3600 * 1000;
            ZonedDateTime at = java.time.Instant.ofEpochMilli(next).atZone(ZoneId.systemDefault());
            PadView.Builder b = PadView.of("cazas").header("Las mismas para todo el servidor. Nuevas cazas en " + until(at) + ".")
                    .empty("Sin cazas.");
            for (Def d : defs) b.row(row(player, "c", d));
            if (claimable(player, "c", defs)) b.footer(PadView.Btn.of("RECLAMAR TODO", "todo:c", PadView.GREEN));
            return b.build();
        }

        @Override
        public String action(ServerPlayer player, String tab, String action, String text) {
            List<Def> defs = hunts();
            if (action.equals("todo:c")) {
                claimAll(player, "c", defs);
            } else if (action.startsWith("reclamar:c:")) {
                Def d = find(defs, action.substring("reclamar:c:".length()));
                if (d != null && claim(player, "c", d)) TFPadNet.notice(player, "Cobraste " + TFEconomy.format(d.coins) + ": " + d.name + ".");
            }
            return null;
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Datos: misiones.json → jugadores.<uuid>.{d|s|c} = {"n": día/semana/periodo, "<id>": [progreso, cobrada]}
    // ---------------------------------------------------------------------------------------------------------------

    static final class Store extends JsonStore {
        Store() {
            super("misiones.json");
        }

        @Override
        void loaded(MinecraftServer server) {
            loadConfig();
        }

        /** El hueco de un tipo, puesto a cero si es de otro día/semana/periodo. */
        JsonObject slot(UUID uuid, String kind) {
            JsonObject p = player(uuid);
            long now = kind.equals("d") ? day() : kind.equals("s") ? week() : period();
            JsonObject s = obj(p, kind);
            if (num(s, "n") != now) {
                s = new JsonObject();
                s.addProperty("n", now);
                p.add(kind, s);
                changed();
            }
            return s;
        }

        long[] state(JsonObject slot, String id) {
            JsonElement e = slot.get(id);
            if (e == null || !e.isJsonArray() || e.getAsJsonArray().size() < 2) return new long[] {0, 0};
            JsonArray a = e.getAsJsonArray();
            return new long[] {a.get(0).getAsLong(), a.get(1).getAsLong()};
        }

        void put(JsonObject slot, String id, long[] st) {
            JsonArray a = new JsonArray();
            a.add(st[0]);
            a.add(st[1]);
            slot.add(id, a);
            changed();
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Configuración
    // ---------------------------------------------------------------------------------------------------------------

    private static Path configFile() {
        return net.tierrasfantasticas.tfclient.util.TFConfigDir.file("misiones.json", "tfclient-misiones.json");
    }

    /** La config tal cual (para el pad de administrador). */
    static JsonObject readConfig() {
        JsonObject o = TFJson.read(configFile());
        return o != null ? o : defaults();
    }

    /** Guarda la config y la vuelve a cargar (lo que cambie el admin vale al momento). */
    static void writeConfig(JsonObject o) {
        TFJson.write(configFile(), o);
        loadConfig();
    }

    static void loadConfig() {
        Path file = configFile();
        JsonObject o = TFJson.read(file);
        if (o == null) {
            o = defaults();
            if (!Files.exists(file)) TFJson.write(file, o);
        } else {
            // 1.3.29: listas nuevas y economía nueva (rotan por temporadas); la de antes se guarda y se cambia
            o = net.tierrasfantasticas.tfclient.util.TFConfigDir.upgrade(file, o, "version", 2, defaults(), "misiones.json");
        }
        DAILY.clear();
        HUNT.clear();
        weeklyTimes = (int) Math.max(1, TFJson.num(o, "semanalesPorCantidad", 5));
        weeklyPay = (int) Math.max(1, TFJson.num(o, "semanalesPorPago", 4));
        read(o, "diarias", DAILY);
        read(o, "cazas", HUNT);
        TFClient.LOGGER.info("TF Pad: {} misiones y {} cazas", DAILY.size(), HUNT.size());
    }

    private static void read(JsonObject o, String key, List<Def> out) {
        if (!o.has(key) || !o.get(key).isJsonArray()) return;
        for (JsonElement e : o.getAsJsonArray(key)) {
            try {
                JsonObject m = e.getAsJsonObject();
                String target = TFJson.str(m, "objetivo", "*");
                if (target.startsWith("#")) new ResourceLocation(target.substring(1)); // etiqueta mal escrita: se avisa y se salta
                out.add(new Def(TFJson.str(m, "id", "m" + out.size()), TFJson.str(m, "nombre", "?"), TFJson.str(m, "descripcion", ""),
                        TFJson.str(m, "tipo", "romper"), TFJson.str(m, "objetivo", "*"), (int) Math.max(1, TFJson.num(m, "cantidad", 1)),
                        Math.max(0, TFJson.num(m, "monedas", 0)), TFJson.str(m, "icono", "minecraft:paper")));
            } catch (Exception ex) {
                TFClient.LOGGER.warn("TF Pad: una misión de la configuración no se pudo leer: {}", ex.getMessage());
            }
        }
    }

    /** Lo de por defecto: src/main/resources/tfclient-misiones-default.json (lo genera tools/gen_economia.py). */
    private static JsonObject defaults() {
        try (java.io.InputStream in = PadMissions.class.getResourceAsStream("/tfclient-misiones-default.json")) {
            if (in == null) return new JsonObject();
            return com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (Exception e) {
            TFClient.LOGGER.error("TF Pad: no se pudieron leer las misiones por defecto", e);
            return new JsonObject();
        }
    }

    /** Para Jugadores y Ranking. */
    public static Map<String, Long> summary(UUID uuid) {
        Map<String, Long> m = new HashMap<>();
        m.put("misiones", PadStats.get(uuid, PadStats.MISSIONS));
        m.put("cazas", PadStats.get(uuid, PadStats.HUNTS));
        return m;
    }
}
