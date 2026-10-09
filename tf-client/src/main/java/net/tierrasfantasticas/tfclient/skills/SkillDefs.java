package net.tierrasfantasticas.tfclient.skills;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * Las clases de skills tal como las deja tools/skills/build_skills.py en el jar (assets/tfclient/skills/): el índice
 * (catalog.json) y el programa de cada clase (classes/&lt;id&gt;.json). Se leen del propio jar, así que sirven igual en el
 * servidor y en el cliente.
 *
 * <p>El programa es el de MythicMobs ya separado: cada skill de la clase entra por una skill de MythicMobs («entry»),
 * que es una lista de mecánicas con su objetivo, disparador, condiciones y probabilidad. Los «mobs» son los efectos que
 * se invocan (soportes con un modelo en la cabeza o modelos de ModelEngine) y sus propias mecánicas.
 */
public final class SkillDefs {
    private static Map<String, ClassDef> classes;

    private SkillDefs() {}

    /** Una condición: mecánica de condición, sus argumentos, si va negada y qué hace (true, false, cancel, power 2...). */
    public record Cond(String m, Map<String, String> a, boolean not, String action) {}

    /** Una línea del programa: mecánica{a} @objetivo{ta} ~disparador:valor ?condiciones probabilidad. */
    public static final class Mech {
        public String m;
        public Map<String, String> a = Map.of();
        public String t;
        public Map<String, String> ta = Map.of();
        public String tr;
        public String trv;
        public List<Cond> c = List.of();
        public double chance = 1.0;
        /** Lleva variables (&lt;caster.var.x&gt;, &lt;skill.damage&gt;...) que se sustituyen al ejecutar. */
        public boolean dyn;

        Mech copyWith(Map<String, String> a, Map<String, String> ta) {
            Mech o = new Mech();
            o.m = m;
            o.a = a;
            o.t = t;
            o.ta = ta;
            o.tr = tr;
            o.trv = trv;
            o.c = c;
            o.chance = chance;
            return o;
        }

        public String arg(String def, String... keys) {
            for (String k : keys) {
                String v = a.get(k);
                if (v != null) return v;
            }
            return def;
        }

        public String targetArg(String def, String... keys) {
            for (String k : keys) {
                String v = ta.get(k);
                if (v != null) return v;
            }
            return def;
        }
    }

    /** Una skill de MythicMobs (metaskill): su cooldown, sus condiciones y sus mecánicas. */
    public static final class Meta {
        public String name;
        public double cooldown;
        public List<Cond> conditions = List.of();
        public List<Cond> targetConditions = List.of();
        public List<Cond> triggerConditions = List.of();
        public List<Mech> mechs = List.of();
    }

    /** Un efecto que se invoca (en MythicMobs era un mob): soporte con modelo en la cabeza o modelo de ModelEngine. */
    public static final class MobDef {
        public String name;
        public String type;
        public boolean small;
        public boolean marker;
        public String headModel;
        public double health;
        public List<Mech> mechs = List.of();
    }

    /** Cómo se dispara sola una pasiva de MMOCore: TIMER cada «timer» segundos, ATTACK, DAMAGED... */
    public record Passive(String type, double timer) {}

    public record SkillDef(String id, String name, List<String> lore, double cooldown, double mana, String entry,
                           String icon, Passive passive, boolean hidden, Map<String, Double> mods) {
        /** Se lanza con su tecla (no es pasiva ni interna). */
        public boolean active() {
            return passive == null && !hidden;
        }
    }

    public static final class ClassDef {
        public String id;
        public String name;
        public List<String> lore = List.of();
        public List<SkillDef> skills = List.of();
        public final Map<String, Meta> tree = new HashMap<>();
        public final Map<String, MobDef> mobs = new HashMap<>();
        /** Icono de cada skill (textura de 32×32). */
        public final Map<String, String> icons = new HashMap<>();
        /** Modelos de ítem que salen en los efectos (el cliente los carga al arrancar). */
        public final java.util.Set<String> itemModels = new java.util.HashSet<>();

        public Meta meta(String name) {
            return name == null ? null : tree.get(name.toLowerCase(Locale.ROOT));
        }

        public MobDef mob(String name) {
            return name == null ? null : mobs.get(name.toLowerCase(Locale.ROOT));
        }

        /** Las skills que van en la barra (con tecla), en orden. */
        public List<SkillDef> actives() {
            List<SkillDef> out = new ArrayList<>();
            for (SkillDef s : skills) if (s.active()) out.add(s);
            return out;
        }

        public SkillDef skill(String id) {
            for (SkillDef s : skills) if (s.id().equals(id)) return s;
            return null;
        }
    }

    // ------------------------------------------------------------------------------------------- carga

    /** Todas las clases que trae el mod, en el orden del índice. */
    public static synchronized Map<String, ClassDef> all() {
        if (classes == null) {
            Map<String, ClassDef> out = new LinkedHashMap<>();
            JsonElement cat = readJson("/assets/tfclient/skills/catalog.json");
            if (cat != null && cat.isJsonArray()) {
                for (JsonElement e : cat.getAsJsonArray()) {
                    String id = e.getAsJsonObject().get("id").getAsString();
                    try {
                        JsonElement json = readJson("/assets/tfclient/skills/classes/" + id + ".json");
                        if (json != null) out.put(id, parse(json.getAsJsonObject()));
                    } catch (Exception ex) {
                        TFClient.LOGGER.error("TF Skills: no se pudo leer la clase {}", id, ex);
                    }
                }
            }
            classes = Collections.unmodifiableMap(out);
            TFClient.LOGGER.info("TF Skills: {} clases cargadas", classes.size());
        }
        return classes;
    }

    public static ClassDef get(String id) {
        return id == null ? null : all().get(id);
    }

    private static JsonElement readJson(String path) {
        try (InputStream in = SkillDefs.class.getResourceAsStream(path)) {
            if (in == null) return null;
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            TFClient.LOGGER.error("TF Skills: no se pudo leer {}", path, e);
            return null;
        }
    }

    static ClassDef parse(JsonObject o) {
        ClassDef c = new ClassDef();
        c.id = o.get("id").getAsString();
        JsonObject cls = o.has("class") && o.get("class").isJsonObject() ? o.getAsJsonObject("class") : new JsonObject();
        c.name = str(cls, "name", c.id);
        c.lore = strings(cls.get("lore"));
        List<SkillDef> skills = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("skills")) {
            JsonObject s = e.getAsJsonObject();
            Passive passive = null;
            if (s.has("passive") && s.get("passive").isJsonObject()) {
                JsonObject p = s.getAsJsonObject("passive");
                passive = new Passive(str(p, "type", "TIMER").toUpperCase(Locale.ROOT), num(p.get("timer"), 1));
            }
            Map<String, Double> mods = new HashMap<>();
            if (s.has("mods") && s.get("mods").isJsonObject()) {
                for (Map.Entry<String, JsonElement> me : s.getAsJsonObject("mods").entrySet()) {
                    mods.put(me.getKey().toLowerCase(Locale.ROOT), num(me.getValue(), 0));
                }
            }
            skills.add(new SkillDef(s.get("id").getAsString(), str(s, "name", s.get("id").getAsString()), strings(s.get("lore")),
                    num(s.get("cooldown"), 0), num(s.get("mana"), 0), str(s, "entry", null), str(s, "icon", null), passive,
                    s.has("hidden") && s.get("hidden").getAsBoolean(), Map.copyOf(mods)));
        }
        c.skills = List.copyOf(skills);
        JsonObject tree = o.getAsJsonObject("tree");
        for (Map.Entry<String, JsonElement> e : tree.entrySet()) {
            JsonObject t = e.getValue().getAsJsonObject();
            Meta meta = new Meta();
            meta.name = e.getKey();
            meta.cooldown = num(t.get("cooldown"), 0);
            meta.conditions = conds(t.get("conditions"));
            meta.targetConditions = conds(t.get("target_conditions"));
            meta.triggerConditions = conds(t.get("trigger_conditions"));
            meta.mechs = mechs(t.get("mechs"));
            c.tree.put(e.getKey().toLowerCase(Locale.ROOT), meta);
        }
        if (o.has("icons") && o.get("icons").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("icons").entrySet()) c.icons.put(e.getKey(), e.getValue().getAsString());
        }
        if (o.has("item_models") && o.get("item_models").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("item_models").entrySet()) c.itemModels.add(e.getValue().getAsString());
        }
        JsonObject mobs = o.getAsJsonObject("mobs");
        for (Map.Entry<String, JsonElement> e : mobs.entrySet()) {
            JsonObject m = e.getValue().getAsJsonObject();
            MobDef mob = new MobDef();
            mob.name = e.getKey();
            mob.type = str(m, "type", "ARMOR_STAND").toUpperCase(Locale.ROOT);
            JsonObject opts = m.has("options") && m.get("options").isJsonObject() ? m.getAsJsonObject("options") : new JsonObject();
            mob.small = bool(opts.get("Small"));
            mob.marker = bool(opts.get("Marker"));
            mob.headModel = str(m, "head_model", null);
            mob.health = num(m.get("health"), 0);
            mob.mechs = mechs(m.get("mechs"));
            if (mob.headModel != null) c.itemModels.add(mob.headModel);
            c.mobs.put(e.getKey().toLowerCase(Locale.ROOT), mob);
        }
        return c;
    }

    private static List<Mech> mechs(JsonElement el) {
        if (el == null || !el.isJsonArray()) return List.of();
        List<Mech> out = new ArrayList<>();
        for (JsonElement e : el.getAsJsonArray()) {
            JsonObject o = e.getAsJsonObject();
            Mech m = new Mech();
            m.m = o.get("m").getAsString().toLowerCase(Locale.ROOT);
            m.a = args(o.get("a"));
            m.t = o.has("t") && !o.get("t").isJsonNull() ? o.get("t").getAsString().toLowerCase(Locale.ROOT) : null;
            m.ta = args(o.get("ta"));
            m.tr = o.has("tr") && !o.get("tr").isJsonNull() ? o.get("tr").getAsString().toLowerCase(Locale.ROOT) : null;
            m.trv = o.has("trv") && !o.get("trv").isJsonNull() ? o.get("trv").getAsString() : null;
            if (o.has("c") && o.get("c").isJsonArray()) {
                List<Cond> cs = new ArrayList<>();
                for (JsonElement ce : o.getAsJsonArray("c")) {
                    JsonObject co = ce.getAsJsonObject();
                    cs.add(new Cond(co.get("m").getAsString().toLowerCase(Locale.ROOT), args(co.get("a")),
                            co.has("not") && co.get("not").getAsBoolean(), "true"));
                }
                m.c = cs;
            }
            m.chance = num(o.get("ch"), 1.0);
            for (String v : m.a.values()) if (v.indexOf('<') >= 0) m.dyn = true;
            for (String v : m.ta.values()) if (v.indexOf('<') >= 0) m.dyn = true;
            out.add(m);
        }
        return out;
    }

    private static List<Cond> conds(JsonElement el) {
        if (el == null || !el.isJsonArray()) return List.of();
        List<Cond> out = new ArrayList<>();
        for (JsonElement e : el.getAsJsonArray()) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            String m = o.get("m").getAsString().toLowerCase(Locale.ROOT);
            boolean not = m.startsWith("!");
            if (not) m = m.substring(1);
            out.add(new Cond(m, args(o.get("a")), not, str(o, "v", "true").toLowerCase(Locale.ROOT).trim()));
        }
        return out;
    }

    /** Argumentos con la clave en minúsculas (MythicMobs no distingue mayúsculas). */
    private static Map<String, String> args(JsonElement el) {
        if (el == null || !el.isJsonObject()) return Map.of();
        Map<String, String> out = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
            if (e.getValue().isJsonNull()) continue;
            out.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue().isJsonPrimitive() ? e.getValue().getAsString()
                    : e.getValue().toString());
        }
        return out;
    }

    private static List<String> strings(JsonElement el) {
        if (el == null || !el.isJsonArray()) return List.of();
        List<String> out = new ArrayList<>();
        JsonArray arr = el.getAsJsonArray();
        for (JsonElement e : arr) if (e.isJsonPrimitive() && !e.getAsString().isBlank()) out.add(e.getAsString());
        return List.copyOf(out);
    }

    private static String str(JsonObject o, String key, String def) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : def;
    }

    private static boolean bool(JsonElement e) {
        return e != null && e.isJsonPrimitive() && (e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean()
                : "true".equalsIgnoreCase(e.getAsString()));
    }

    static double num(JsonElement e, double def) {
        if (e == null || e.isJsonNull() || !e.isJsonPrimitive()) return def;
        try {
            return Double.parseDouble(e.getAsString().trim());
        } catch (NumberFormatException ex) {
            return def;
        }
    }
}
