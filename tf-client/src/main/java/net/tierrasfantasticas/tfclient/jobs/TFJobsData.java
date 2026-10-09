package net.tierrasfantasticas.tfclient.jobs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * Progreso de cada jugador en los oficios: &lt;mundo&gt;/tfclient/oficios.json. Al dejar un oficio no se pierde nada:
 * el nivel, la experiencia y las misiones se quedan guardados por si vuelve.
 */
public final class TFJobsData {
    /** Estado de una misión: progreso, si está hecha y el día en que se cobró (-1 si no). */
    public static final class MissionState {
        public int progress;
        public boolean done;
        public long claimedDay = -1;
    }

    public static final class JobProgress {
        public int level = 1;
        public double xp;
        public final Map<String, MissionState> missions = new HashMap<>();
        /** Las misiones diarias que le tocaron hoy (día de TFRotation) en este oficio: "plantilla@factor". */
        public long rotDay = -1;
        public final List<String> rotIds = new ArrayList<>();

        public MissionState mission(String id) {
            return missions.computeIfAbsent(id, k -> new MissionState());
        }
    }

    public static final class PlayerJobs {
        public final List<String> active = new ArrayList<>();
        public long lastJoin;
        public final Map<String, JobProgress> jobs = new LinkedHashMap<>();

        public JobProgress job(String id) {
            return jobs.computeIfAbsent(id, k -> new JobProgress());
        }
    }

    private static TFJobsData instance;

    private final Path file;
    private final Map<UUID, PlayerJobs> players = new HashMap<>();
    private boolean dirty;

    private TFJobsData(Path file) {
        this.file = file;
        JsonObject json = TFJson.read(file);
        if (json == null) return;
        for (String key : json.keySet()) {
            try {
                players.put(UUID.fromString(key), parse(json.getAsJsonObject(key)));
            } catch (RuntimeException ignored) {
                // jugador con datos rotos: empieza de cero
            }
        }
    }

    public static TFJobsData get(MinecraftServer server) {
        if (instance == null) instance = new TFJobsData(TFJson.worldFile(server, "oficios.json"));
        return instance;
    }

    public static void unload() {
        if (instance != null) instance.save();
        instance = null;
    }

    public static long today() {
        return LocalDate.now().toEpochDay();
    }

    public PlayerJobs player(UUID uuid) {
        return players.computeIfAbsent(uuid, k -> new PlayerJobs());
    }

    public void changed() {
        dirty = true;
    }

    public void save() {
        if (!dirty) return;
        dirty = false;
        JsonObject json = new JsonObject();
        players.forEach((uuid, p) -> json.add(uuid.toString(), write(p)));
        TFJson.write(file, json);
    }

    private static PlayerJobs parse(JsonObject o) {
        PlayerJobs p = new PlayerJobs();
        if (o.has("activos")) for (JsonElement el : o.getAsJsonArray("activos")) p.active.add(el.getAsString());
        p.lastJoin = TFJson.num(o, "ultimoIngreso", 0);
        JsonObject jobs = TFJson.obj(o, "oficios");
        for (String id : jobs.keySet()) {
            JsonObject jo = jobs.getAsJsonObject(id);
            JobProgress jp = p.job(id);
            jp.level = (int) Math.max(1, TFJson.num(jo, "nivel", 1));
            jp.xp = Math.max(0, TFJson.dec(jo, "xp", 0));
            jp.rotDay = TFJson.num(jo, "hoy", -1);
            if (jo.has("hoyMisiones") && jo.get("hoyMisiones").isJsonArray()) {
                for (JsonElement el : jo.getAsJsonArray("hoyMisiones")) jp.rotIds.add(el.getAsString());
            }
            JsonObject missions = TFJson.obj(jo, "misiones");
            for (String mid : missions.keySet()) {
                JsonObject mo = missions.getAsJsonObject(mid);
                MissionState ms = jp.mission(mid);
                ms.progress = (int) TFJson.num(mo, "progreso", 0);
                ms.done = TFJson.bool(mo, "hecha", false);
                ms.claimedDay = TFJson.num(mo, "cobrada", -1);
            }
        }
        return p;
    }

    private static JsonObject write(PlayerJobs p) {
        JsonObject o = new JsonObject();
        JsonArray active = new JsonArray();
        p.active.forEach(active::add);
        o.add("activos", active);
        o.addProperty("ultimoIngreso", p.lastJoin);
        JsonObject jobs = new JsonObject();
        p.jobs.forEach((id, jp) -> {
            JsonObject jo = new JsonObject();
            jo.addProperty("nivel", jp.level);
            jo.addProperty("xp", Math.round(jp.xp * 100) / 100.0);
            if (jp.rotDay >= 0) {
                jo.addProperty("hoy", jp.rotDay);
                JsonArray rot = new JsonArray();
                jp.rotIds.forEach(rot::add);
                jo.add("hoyMisiones", rot);
            }
            JsonObject missions = new JsonObject();
            long today = net.tierrasfantasticas.tfclient.util.TFRotation.day();
            jp.missions.forEach((mid, ms) -> {
                if (ms.progress == 0 && !ms.done && ms.claimedDay < 0) return;
                if (TFJobsConfig.rotatedDay(mid) >= 0 && TFJobsConfig.rotatedDay(mid) < today) return; // las de otros días ya no valen
                JsonObject mo = new JsonObject();
                mo.addProperty("progreso", ms.progress);
                mo.addProperty("hecha", ms.done);
                mo.addProperty("cobrada", ms.claimedDay);
                missions.add(mid, mo);
            });
            jo.add("misiones", missions);
            jobs.add(id, jo);
        });
        o.add("oficios", jobs);
        return o;
    }
}
