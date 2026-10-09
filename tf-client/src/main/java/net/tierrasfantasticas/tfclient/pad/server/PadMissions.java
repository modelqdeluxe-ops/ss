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

/**
 * Misiones (3 diarias y 3 semanales por jugador) y Cazas (4 objetivos para todo el servidor que cambian cada 12 h).
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

    private static List<Def> pick(List<Def> pool, long seed, int n) {
        List<Def> copy = new ArrayList<>(pool);
        java.util.Collections.shuffle(copy, new Random(seed));
        return copy.subList(0, Math.min(n, copy.size()));
    }

    static List<Def> daily(UUID uuid) {
        return pick(DAILY, day() * 1_000_003L ^ uuid.getMostSignificantBits(), 3);
    }

    static List<Def> weekly(UUID uuid) {
        List<Def> base = pick(DAILY, week() * 7_919L ^ uuid.getLeastSignificantBits(), 3);
        List<Def> out = new ArrayList<>();
        for (Def d : base) {
            out.add(new Def(d.id, d.name, d.desc.replaceFirst("\\b" + d.amount + "\\b", Integer.toString(d.amount * weeklyTimes)),
                    d.type, d.target, d.amount * weeklyTimes, d.coins * weeklyPay, d.icon));
        }
        return out;
    }

    static List<Def> hunts() {
        return pick(HUNT, period() * 104_729L, 4);
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
                player.displayClientMessage(Component.literal(what + " ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                        .append(Component.literal(d.name + " — reclama tus " + TFEconomy.format(d.coins) + " en el pad (C)")
                                .withStyle(ChatFormatting.YELLOW)), true);
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
        lines.add(d.desc + "  ·  " + Math.min(st[0], d.amount) + "/" + d.amount);
        return new PadView.Row(new ItemStack(d.item()), d.name, claimed ? 0x7E8CA8 : 0x18265C, lines,
                Math.min(1F, st[0] / (float) d.amount), "+" + TFEconomy.number(d.coins), btn, null);
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

    static void loadConfig() {
        Path file = configFile();
        JsonObject o = TFJson.read(file);
        if (o == null) {
            o = defaults();
            if (!Files.exists(file)) TFJson.write(file, o);
        }
        DAILY.clear();
        HUNT.clear();
        weeklyTimes = (int) Math.max(1, TFJson.num(o, "semanalesPorCantidad", 5));
        weeklyPay = (int) Math.max(1, TFJson.num(o, "semanalesPorPago", 6));
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

    private static JsonObject defaults() {
        JsonObject o = new JsonObject();
        o.addProperty("_ayuda", "tipo: romper, cosechar, colocar, matar, pescar, fabricar, fundir, preparar, criar, encantar. "
                + "objetivo: un id (minecraft:zombie), una etiqueta (#minecraft:logs), * o, al matar, hostil / animal. "
                + "Cada día se eligen 3 diarias por jugador; las semanales son 3 diarias con cantidad x semanalesPorCantidad y pago x semanalesPorPago. "
                + "Cada 12 h se eligen 4 cazas para todo el servidor.");
        o.addProperty("semanalesPorCantidad", 5);
        o.addProperty("semanalesPorPago", 6);
        JsonArray d = new JsonArray();
        def(d, "piedra", "Pico incansable", "Rompe 128 piedras", "romper", "#minecraft:base_stone_overworld", 128, 60, "minecraft:stone_pickaxe");
        def(d, "carbon", "Minero de carbón", "Saca 24 menas de carbón", "romper", "#minecraft:coal_ores", 24, 80, "minecraft:coal");
        def(d, "hierro", "Vena de hierro", "Saca 12 menas de hierro", "romper", "#minecraft:iron_ores", 12, 120, "minecraft:raw_iron");
        def(d, "cobre", "Brillo de cobre", "Saca 16 menas de cobre", "romper", "#minecraft:copper_ores", 16, 80, "minecraft:raw_copper");
        def(d, "troncos", "Leñador", "Tala 48 troncos", "romper", "#minecraft:logs", 48, 70, "minecraft:oak_log");
        def(d, "trigo", "Cosecha de trigo", "Cosecha 32 trigos maduros", "cosechar", "minecraft:wheat", 32, 60, "minecraft:wheat");
        def(d, "zanahorias", "Huerto naranja", "Cosecha 32 zanahorias maduras", "cosechar", "minecraft:carrots", 32, 60, "minecraft:carrot");
        def(d, "patatas", "Patatas al punto", "Cosecha 32 patatas maduras", "cosechar", "minecraft:potatoes", 32, 60, "minecraft:potato");
        def(d, "zombis", "Noche de zombis", "Derrota 15 zombis", "matar", "minecraft:zombie", 15, 90, "minecraft:rotten_flesh");
        def(d, "esqueletos", "Huesos fuera", "Derrota 12 esqueletos", "matar", "minecraft:skeleton", 12, 90, "minecraft:bone");
        def(d, "aranas", "Telarañas", "Derrota 10 arañas", "matar", "minecraft:spider", 10, 80, "minecraft:string");
        def(d, "creepers", "Sin explosiones", "Derrota 8 creepers", "matar", "minecraft:creeper", 8, 110, "minecraft:gunpowder");
        def(d, "monstruos", "Guardián de la noche", "Derrota 30 monstruos", "matar", "hostil", 30, 120, "minecraft:iron_sword");
        def(d, "pesca", "Día de pesca", "Pesca 10 veces", "pescar", "*", 10, 90, "minecraft:fishing_rod");
        def(d, "lingotes", "Fundición", "Funde 16 lingotes de hierro", "fundir", "minecraft:iron_ingot", 16, 90, "minecraft:iron_ingot");
        def(d, "cocina", "Cocinero", "Cocina o funde 32 cosas", "fundir", "*", 32, 70, "minecraft:furnace");
        def(d, "artesano", "Artesano", "Fabrica 64 objetos", "fabricar", "*", 64, 60, "minecraft:crafting_table");
        def(d, "constructor", "Constructor", "Coloca 200 bloques", "colocar", "*", 200, 70, "minecraft:bricks");
        def(d, "granja", "Ganadero", "Cría 6 animales", "criar", "animal", 6, 70, "minecraft:wheat_seeds");
        def(d, "pociones", "Alquimista", "Prepara 3 pociones", "preparar", "*", 3, 100, "minecraft:brewing_stand");
        o.add("diarias", d);
        JsonArray c = new JsonArray();
        def(c, "c_zombi", "Plaga de zombis", "Derrota 40 zombis", "matar", "minecraft:zombie", 40, 250, "minecraft:zombie_head");
        def(c, "c_esqueleto", "Arqueros de hueso", "Derrota 30 esqueletos", "matar", "minecraft:skeleton", 30, 250, "minecraft:skeleton_skull");
        def(c, "c_creeper", "Silencio verde", "Derrota 20 creepers", "matar", "minecraft:creeper", 20, 300, "minecraft:creeper_head");
        def(c, "c_arana", "Nido de arañas", "Derrota 25 arañas", "matar", "minecraft:spider", 25, 220, "minecraft:spider_eye");
        def(c, "c_enderman", "Ojos del End", "Derrota 10 endermans", "matar", "minecraft:enderman", 10, 400, "minecraft:ender_pearl");
        def(c, "c_bruja", "Caza de brujas", "Derrota 6 brujas", "matar", "minecraft:witch", 6, 350, "minecraft:glass_bottle");
        def(c, "c_slime", "Pegajoso", "Derrota 20 slimes", "matar", "minecraft:slime", 20, 250, "minecraft:slime_ball");
        def(c, "c_ahogado", "Bajo el agua", "Derrota 15 ahogados", "matar", "minecraft:drowned", 15, 300, "minecraft:trident");
        def(c, "c_saqueador", "Patrulla", "Derrota 10 saqueadores", "matar", "minecraft:pillager", 10, 350, "minecraft:crossbow");
        def(c, "c_blaze", "Fuego del Nether", "Derrota 12 blazes", "matar", "minecraft:blaze", 12, 450, "minecraft:blaze_rod");
        def(c, "c_wither", "Esqueletos oscuros", "Derrota 8 esqueletos wither", "matar", "minecraft:wither_skeleton", 8, 500, "minecraft:wither_skeleton_skull");
        def(c, "c_fantasma", "Cielo nocturno", "Derrota 8 phantoms", "matar", "minecraft:phantom", 8, 350, "minecraft:phantom_membrane");
        def(c, "c_magma", "Magma", "Derrota 15 cubos de magma", "matar", "minecraft:magma_cube", 15, 350, "minecraft:magma_cream");
        def(c, "c_monstruos", "Gran cacería", "Derrota 80 monstruos", "matar", "hostil", 80, 400, "minecraft:diamond_sword");
        o.add("cazas", c);
        return o;
    }

    private static void def(JsonArray a, String id, String name, String desc, String type, String target, int amount, long coins, String icon) {
        JsonObject m = new JsonObject();
        m.addProperty("id", id);
        m.addProperty("nombre", name);
        m.addProperty("descripcion", desc);
        m.addProperty("tipo", type);
        m.addProperty("objetivo", target);
        m.addProperty("cantidad", amount);
        m.addProperty("monedas", coins);
        m.addProperty("icono", icon);
        a.add(m);
    }

    /** Para Jugadores y Ranking. */
    public static Map<String, Long> summary(UUID uuid) {
        Map<String, Long> m = new HashMap<>();
        m.put("misiones", PadStats.get(uuid, PadStats.MISSIONS));
        m.put("cazas", PadStats.get(uuid, PadStats.HUNTS));
        return m;
    }
}
