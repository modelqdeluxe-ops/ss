package net.tierrasfantasticas.tfclient.jobs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.util.TFJson;

/** config/tfclient/oficios.json: oficios, acciones que pagan, misiones, niveles y recompensas. Se crea la primera vez. */
public final class TFJobsConfig {
    public enum Repeat { DAILY, ALWAYS, NEVER }

    /** Qué cuenta: un id, una etiqueta (#...), * o, al matar, hostil / animal. */
    public record Target(String raw) {
        public boolean any() {
            return raw.equals("*");
        }

        public boolean block(BlockState state) {
            if (any()) return true;
            if (raw.startsWith("#")) return state.is(TagKey.create(Registries.BLOCK, new ResourceLocation(raw.substring(1))));
            return raw.equals(String.valueOf(ForgeRegistries.BLOCKS.getKey(state.getBlock())));
        }

        public boolean item(ItemStack stack) {
            if (any()) return true;
            if (raw.startsWith("#")) return stack.is(TagKey.create(Registries.ITEM, new ResourceLocation(raw.substring(1))));
            return raw.equals(String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem())));
        }

        public boolean entity(Entity entity) {
            if (any()) return true;
            if (raw.equals("hostil")) return entity instanceof Enemy;
            if (raw.equals("animal")) return entity instanceof Animal;
            if (raw.startsWith("#")) return entity.getType().is(TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(raw.substring(1))));
            return raw.equals(String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType())));
        }
    }

    public record Reward(double coins, double xp, List<String> items, List<String> commands) {
        static Reward parse(JsonObject o) {
            return new Reward(TFJson.dec(o, "monedas", 0), TFJson.dec(o, "xp", 0), strings(o, "objetos"), strings(o, "comandos"));
        }

        public boolean isEmpty() {
            return coins <= 0 && xp <= 0 && items.isEmpty() && commands.isEmpty();
        }
    }

    public record Action(String type, Target target, double xp, double coins) {}

    public record Mission(String id, String name, String description, String icon, String type, Target target, int amount,
                          int level, Repeat repeat, Reward reward) {}

    public record Job(String id, String name, String description, String icon, String background, int color,
                      List<Action> actions, List<Mission> missions) {
        public Mission mission(String id) {
            for (Mission m : missions) if (m.id().equals(id)) return m;
            return null;
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Misiones que rotan: las «diaria» de cada oficio son plantillas; cada día a cada jugador le tocan dailyMissions de
    // las de su nivel (TFRotation: temporadas de 90 días sin repetir ninguna igual y cada vez algo más difíciles).
    // Las «siempre» y «nunca» se quedan fijas.
    // ---------------------------------------------------------------------------------------------------------------

    /** Las misiones de un oficio hoy para un jugador: primero las diarias que le tocan, luego las fijas. */
    public static List<Mission> missionsFor(Job job, java.util.UUID uuid, TFJobsData.JobProgress jp) {
        long day = net.tierrasfantasticas.tfclient.util.TFRotation.day();
        if (jp.rotDay != day) {
            List<Mission> open = new ArrayList<>();
            for (Mission m : job.missions()) if (m.repeat() == Repeat.DAILY && m.level() <= jp.level) open.add(m);
            jp.rotIds.clear();
            for (var p : net.tierrasfantasticas.tfclient.util.TFRotation.pick(open.size(), dailyMissions, day,
                    net.tierrasfantasticas.tfclient.util.TFRotation.SEASON_DAYS, job.id().hashCode(), uuid.hashCode())) {
                jp.rotIds.add(open.get(p.index()).id() + "@" + p.factor());
            }
            jp.rotDay = day;
            TFJobs.data().changed();
        }
        List<Mission> out = new ArrayList<>();
        for (String r : jp.rotIds) {
            int at = r.indexOf('@');
            Mission t = at > 0 ? job.mission(r.substring(0, at)) : null;
            if (t == null) continue;
            double f;
            try {
                f = Double.parseDouble(r.substring(at + 1));
            } catch (NumberFormatException e) {
                f = 1;
            }
            out.add(rotated(t, f, day));
        }
        for (Mission m : job.missions()) if (m.repeat() != Repeat.DAILY) out.add(m);
        return out;
    }

    /** Una misión de hoy por su id (de missionsFor), o null. */
    public static Mission missionFor(Job job, java.util.UUID uuid, TFJobsData.JobProgress jp, String id) {
        for (Mission m : missionsFor(job, uuid, jp)) if (m.id().equals(id)) return m;
        return null;
    }

    /** La misión de la plantilla para el día: id «h&lt;día&gt;_&lt;plantilla&gt;», cantidad y premio por el factor. */
    private static Mission rotated(Mission t, double f, long day) {
        Reward r = t.reward();
        Reward scaled = new Reward(net.tierrasfantasticas.tfclient.util.TFRotation.coins(r.coins(), f), Math.round(r.xp() * f), r.items(),
                r.commands());
        return new Mission("h" + day + "_" + t.id(), t.name(), t.description(), t.icon(), t.type(), t.target(),
                net.tierrasfantasticas.tfclient.util.TFRotation.amount(t.amount(), f), t.level(), Repeat.DAILY, scaled);
    }

    /** El día de una misión que rota («h&lt;día&gt;_…»), o -1 si es fija. */
    static long rotatedDay(String id) {
        if (!id.startsWith("h")) return -1;
        int u = id.indexOf('_');
        if (u < 2) return -1;
        try {
            return Long.parseLong(id.substring(1, u));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static final List<String> BACKGROUNDS = List.of("farmer", "miner", "wood_cutter", "digger", "fisherman", "hunter",
            "alchemist", "blacksmith", "builder", "enchanter");
    static final List<String> TYPES = List.of("romper", "cosechar", "colocar", "matar", "pescar", "fabricar", "fundir", "preparar",
            "encantar", "reparar", "criar");

    // Ajustes generales
    public static boolean enabled = true;
    public static int maxJobs = 1;
    public static int switchWaitMinutes = 5;
    public static int paySeconds = 15;
    public static boolean placedDontCount = true;
    public static boolean spawnersCount = false;
    public static int maxLevel = 50;
    public static double xpBase = 100;
    public static double xpFactor = 1.15;
    public static double coinBonusPerLevel = 0.03;
    public static int announceEvery = 10;
    /** Cuántas misiones diarias da cada oficio al día (salen de sus misiones «diaria», que rotan). */
    public static int dailyMissions = 3;
    public static double levelCoins = 150;
    public static double levelCoinsPerLevel = 50;
    public static List<String> levelCommands = List.of();
    public static Map<Integer, Reward> milestones = new TreeMap<>();
    public static Map<String, Job> jobs = new LinkedHashMap<>();

    private TFJobsConfig() {}

    public static Path file() {
        return net.tierrasfantasticas.tfclient.util.TFConfigDir.file("oficios.json", "tfclient-jobs.json");
    }

    /** Experiencia que hace falta para pasar del nivel dado al siguiente. */
    public static long xpFor(int level) {
        return Math.max(1, Math.round(xpBase * Math.pow(xpFactor, Math.max(0, level - 1))));
    }

    /** Monedas por subir a este nivel (sin contar los hitos). */
    public static long levelUpCoins(int level) {
        return Math.round(levelCoins + levelCoinsPerLevel * Math.max(0, level - 2));
    }

    public static Job job(String id) {
        return id == null ? null : jobs.get(id);
    }

    /** Lee la configuración (y la crea con los oficios por defecto la primera vez). Devuelve los avisos. */
    public static List<String> load() {
        List<String> warnings = new ArrayList<>();
        Path file = file();
        JsonObject json = null;
        if (!Files.exists(file)) {
            json = defaults();
            if (json != null) TFJson.write(file, json);
            TFClient.LOGGER.info("TF Oficios: creado {} con los oficios por defecto", file);
        } else {
            json = TFJson.read(file);
            if (json == null) {
                warnings.add(file.getFileName() + " no es un JSON válido: se mantienen los oficios de antes");
                return warnings;
            }
            // 1.3.29: economía nueva (pagos más bajos, misiones que rotan): la de antes se guarda y se cambia
            json = net.tierrasfantasticas.tfclient.util.TFConfigDir.upgrade(file, json, "versionEconomia", 2, defaults(), "oficios.json");
        }
        if (json == null) return warnings;
        enabled = TFJson.bool(json, "activado", true);
        maxJobs = (int) Math.max(1, TFJson.num(json, "maxOficios", 1));
        switchWaitMinutes = (int) Math.max(0, TFJson.num(json, "esperaCambioMinutos", 5));
        paySeconds = (int) Math.max(1, Math.min(600, TFJson.num(json, "pagoCadaSegundos", 15)));
        placedDontCount = TFJson.bool(json, "bloquesColocadosNoCuentan", true);
        spawnersCount = TFJson.bool(json, "generadoresCuentan", false);
        JsonObject levels = TFJson.obj(json, "niveles");
        maxLevel = (int) Math.max(1, TFJson.num(levels, "maximo", 50));
        xpBase = Math.max(1, TFJson.dec(levels, "xpBase", 100));
        xpFactor = Math.max(1, TFJson.dec(levels, "multiplicador", 1.15));
        coinBonusPerLevel = Math.max(0, TFJson.dec(levels, "bonusMonedasPorNivel", 0.03));
        announceEvery = (int) Math.max(0, TFJson.num(levels, "anunciarCada", 10));
        dailyMissions = (int) Math.max(1, Math.min(8, TFJson.num(json, "misionesDiariasPorOficio", 3)));
        JsonObject levelReward = TFJson.obj(levels, "recompensa");
        levelCoins = TFJson.dec(levelReward, "monedas", 150);
        levelCoinsPerLevel = TFJson.dec(levelReward, "monedasPorNivel", 50);
        levelCommands = strings(levelReward, "comandos");
        Map<Integer, Reward> stones = new TreeMap<>();
        JsonObject hitos = TFJson.obj(levels, "hitos");
        for (String key : hitos.keySet()) {
            try {
                stones.put(Integer.parseInt(key.trim()), Reward.parse(hitos.getAsJsonObject(key)));
            } catch (RuntimeException e) {
                warnings.add("niveles.hitos: «" + key + "» no es un nivel");
            }
        }
        milestones = stones;

        Map<String, Job> parsed = new LinkedHashMap<>();
        JsonArray list = json.has("oficios") && json.get("oficios").isJsonArray() ? json.getAsJsonArray("oficios") : new JsonArray();
        for (JsonElement el : list) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            String id = TFJson.str(o, "id", "").toLowerCase(Locale.ROOT);
            if (!id.matches("[a-z0-9_]{1,32}")) {
                warnings.add("oficio sin id válido (letras, números y _): " + TFJson.str(o, "nombre", "?"));
                continue;
            }
            String background = TFJson.str(o, "fondo", "farmer");
            if (!BACKGROUNDS.contains(background)) {
                warnings.add(id + ": fondo «" + background + "» no existe, uso farmer");
                background = "farmer";
            }
            List<Action> actions = new ArrayList<>();
            for (JsonElement a : array(o, "acciones")) {
                JsonObject ao = a.getAsJsonObject();
                String type = TFJson.str(ao, "tipo", "");
                if (!TYPES.contains(type)) {
                    warnings.add(id + ": acción de tipo desconocido «" + type + "»");
                    continue;
                }
                actions.add(new Action(type, new Target(TFJson.str(ao, "objetivo", "*")), TFJson.dec(ao, "xp", 0), TFJson.dec(ao, "monedas", 0)));
            }
            List<Mission> missions = new ArrayList<>();
            for (JsonElement m : array(o, "misiones")) {
                JsonObject mo = m.getAsJsonObject();
                String mid = TFJson.str(mo, "id", "");
                String type = TFJson.str(mo, "tipo", "");
                if (!mid.matches("[a-z0-9_]{1,32}") || !TYPES.contains(type)) {
                    warnings.add(id + ": misión «" + mid + "» con id o tipo no válido");
                    continue;
                }
                Repeat repeat = switch (TFJson.str(mo, "repetir", "diaria")) {
                    case "siempre" -> Repeat.ALWAYS;
                    case "nunca" -> Repeat.NEVER;
                    default -> Repeat.DAILY;
                };
                missions.add(new Mission(mid, TFJson.str(mo, "nombre", mid), TFJson.str(mo, "descripcion", ""),
                        TFJson.str(mo, "icono", "minecraft:paper"), type, new Target(TFJson.str(mo, "objetivo", "*")),
                        (int) Math.max(1, TFJson.num(mo, "cantidad", 1)), (int) Math.max(1, TFJson.num(mo, "nivel", 1)), repeat,
                        Reward.parse(TFJson.obj(mo, "recompensa"))));
            }
            int color = 0xE3B74C;
            try {
                color = Integer.parseInt(TFJson.str(o, "color", "#e3b74c").replace("#", ""), 16);
            } catch (NumberFormatException e) {
                warnings.add(id + ": color no válido");
            }
            parsed.put(id, new Job(id, TFJson.str(o, "nombre", id), TFJson.str(o, "descripcion", ""), TFJson.str(o, "icono", background),
                    background, color, actions, missions));
        }
        jobs = parsed;
        TFClient.LOGGER.info("TF Oficios: {} oficios cargados{}", jobs.size(), warnings.isEmpty() ? "" : " (" + warnings.size() + " avisos)");
        warnings.forEach(w -> TFClient.LOGGER.warn("TF Oficios: {}", w));
        return warnings;
    }

    private static JsonObject defaults() {
        try (InputStream in = TFJobsConfig.class.getResourceAsStream("/tfclient-jobs-default.json")) {
            if (in == null) return null;
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            TFClient.LOGGER.error("TF Oficios: no se pudo leer la configuración por defecto", e);
            return null;
        }
    }

    private static JsonArray array(JsonObject o, String key) {
        JsonArray out = new JsonArray();
        if (o.has(key) && o.get(key).isJsonArray()) {
            for (JsonElement el : o.getAsJsonArray(key)) if (el.isJsonObject()) out.add(el);
        }
        return out;
    }

    static List<String> strings(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        if (o != null && o.has(key) && o.get(key).isJsonArray()) {
            for (JsonElement el : o.getAsJsonArray(key)) if (el.isJsonPrimitive()) out.add(el.getAsString());
        }
        return out;
    }
}
