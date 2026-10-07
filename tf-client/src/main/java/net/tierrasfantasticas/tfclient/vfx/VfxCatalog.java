package net.tierrasfantasticas.tfclient.vfx;

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
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * Catálogo de VFX (assets/tfclient/vfx/catalog.json, lo genera tools/build_vfx.py): efectos de kill sueltos y
 * paquetes de skills. Se lee del propio mod, así el servidor y los jugadores tienen siempre el mismo.
 */
public final class VfxCatalog {
    public record Kill(String id, String name, String fx) {}

    /**
     * Una skill pasiva: se lanza sola con su disparador (golpe, golpe crítico, golpe corriendo, golpe agachado,
     * daño recibido o en combate) si no está en cooldown. stages: una o varias fases (combo: cada lanzamiento usa la
     * siguiente). Cada fase tiene su efecto visual y sus acciones en el servidor (daño, empujes, pociones...).
     */
    public record Skill(String id, String name, String trigger, int cooldown, double chance, double health, List<Stage> stages) {}

    public record Stage(String fx, String fxAt, List<JsonObject> actions) {}

    public record Pack(String id, String name, List<Skill> skills) {}

    private static Map<String, Kill> kills;
    private static Map<String, Pack> packs;

    private VfxCatalog() {}

    public static synchronized Map<String, Kill> kills() {
        if (kills == null) load();
        return kills;
    }

    public static synchronized Map<String, Pack> packs() {
        if (packs == null) load();
        return packs;
    }

    public static Kill kill(String id) {
        return id == null ? null : kills().get(id);
    }

    public static Pack pack(String id) {
        return id == null ? null : packs().get(id);
    }

    private static void load() {
        kills = new LinkedHashMap<>();
        packs = new LinkedHashMap<>();
        try (InputStream in = VfxCatalog.class.getResourceAsStream("/assets/tfclient/vfx/catalog.json")) {
            if (in == null) {
                TFClient.LOGGER.error("TF VFX: falta vfx/catalog.json en el mod");
                return;
            }
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement e : o.getAsJsonArray("kills")) {
                JsonObject k = e.getAsJsonObject();
                kills.put(k.get("id").getAsString(), new Kill(k.get("id").getAsString(), k.get("name").getAsString(), k.get("fx").getAsString()));
            }
            for (JsonElement e : o.getAsJsonArray("packs")) {
                JsonObject p = e.getAsJsonObject();
                List<Skill> skills = new ArrayList<>();
                for (JsonElement se : p.getAsJsonArray("skills")) {
                    JsonObject s = se.getAsJsonObject();
                    List<Stage> stages = new ArrayList<>();
                    for (JsonElement st : s.getAsJsonArray("stages")) {
                        JsonObject so = st.getAsJsonObject();
                        List<JsonObject> actions = new ArrayList<>();
                        if (so.has("actions")) for (JsonElement a : so.getAsJsonArray("actions")) actions.add(a.getAsJsonObject());
                        stages.add(new Stage(so.has("fx") ? so.get("fx").getAsString() : null,
                                so.has("fxAt") ? so.get("fxAt").getAsString() : "caster", Collections.unmodifiableList(actions)));
                    }
                    skills.add(new Skill(s.get("id").getAsString(), s.get("name").getAsString(), s.get("trigger").getAsString(),
                            Math.max(1, (int) Math.round(s.get("cooldown").getAsDouble() * 20)),
                            s.has("chance") ? s.get("chance").getAsDouble() : 1.0,
                            s.has("vida") ? s.get("vida").getAsDouble() : 1.0, Collections.unmodifiableList(stages)));
                }
                packs.put(p.get("id").getAsString(), new Pack(p.get("id").getAsString(), p.get("name").getAsString(),
                        Collections.unmodifiableList(skills)));
            }
            TFClient.LOGGER.info("TF VFX: {} efectos de kill y {} paquetes de skills", kills.size(), packs.size());
        } catch (Exception e) {
            TFClient.LOGGER.error("TF VFX: no se pudo leer vfx/catalog.json", e);
        }
    }

    static JsonArray array(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonArray() ? o.getAsJsonArray(key) : new JsonArray();
    }
}
