package net.tierrasfantasticas.tfclient.bosses;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.tierrasfantasticas.tfclient.skills.SkillDefs;
import net.tierrasfantasticas.tfclient.util.TFConfigDir;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * La dificultad que tiene elegida cada jefe en /tf boss (config/tfclient/jefes.json): con la que salen las grietas nuevas
 * y la prueba. Cada grieta guarda la suya (se cambia desde la pestaña GRIETAS).
 */
public final class BossConfig {
    private static final Map<String, Difficulty> CHOSEN = new LinkedHashMap<>();

    private BossConfig() {}

    private static Path file() {
        return TFConfigDir.file("jefes.json", null);
    }

    public static void load() {
        CHOSEN.clear();
        JsonObject o = TFJson.read(file());
        JsonObject j = o == null ? null : TFJson.obj(o, "jefes");
        for (String id : SkillDefs.bosses().keySet()) {
            JsonObject d = j == null ? null : TFJson.obj(j, id);
            CHOSEN.put(id, d == null ? Difficulty.NORMAL : Difficulty.fromJson(d));
        }
        save();
    }

    public static Difficulty get(String boss) {
        return CHOSEN.getOrDefault(boss, Difficulty.NORMAL);
    }

    public static void set(String boss, Difficulty d) {
        if (!SkillDefs.bosses().containsKey(boss)) return;
        CHOSEN.put(boss, d.clamped());
        save();
    }

    public static Map<String, Difficulty> all() {
        return CHOSEN;
    }

    private static void save() {
        JsonObject root = new JsonObject();
        root.addProperty("_info", "Dificultad elegida de cada jefe en /tf boss (vida, daño, velocidad y ritmo de ataque son "
                + "multiplicadores; armadura en puntos; porJugador = vida extra por cada jugador más; recargaMin = minutos "
                + "que tarda la grieta; monedas = para cada jugador que le haga daño)");
        JsonObject j = new JsonObject();
        CHOSEN.forEach((k, v) -> j.add(k, v.toJson()));
        root.add("jefes", j);
        TFJson.write(file(), root);
    }
}
