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
    /** Una condición. «or»/«and» son las compuestas de MythicMobs («(A false || B) true»): sus partes, cada una con su
     * true/false. */
    public record Cond(String m, Map<String, String> a, boolean not, String action, List<Cond> parts) {
        public Cond(String m, Map<String, String> a, boolean not, String action) {
            this(m, a, not, action, List.of());
        }
    }

    /** Una línea del programa: mecánica{a} @objetivo{ta} ~disparador:valor ?condiciones probabilidad. */
    public static final class Mech {
        public String m;
        public Map<String, String> a = Map.of();
        public String t;
        public Map<String, String> ta = Map.of();
        public String tr;
        public String trv;
        public List<Cond> c = List.of();
        /** Condiciones del objetivo en línea (@EIR{conditions=[...]}), de lo que toca un proyectil (hitConditions) y de
         * cuándo se para (stopConditions). */
        public List<Cond> tc = List.of();
        public List<Cond> hc = List.of();
        public List<Cond> sc = List.of();
        /** bounceConditions de chain y chainmissile: a quién puede saltar. */
        public List<Cond> bc = List.of();
        public double chance = 1.0;
        /** Umbral de vida del disparador de un mob («~onDamaged &lt;50%», «~onTimer:20 &gt;75%»): las fases de los jefes. */
        public String hp;
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
            o.tc = tc;
            o.hc = hc;
            o.sc = sc;
            o.bc = bc;
            o.chance = chance;
            o.hp = hp;
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
        /** El objeto con el que nace un item_display (DisplayOptions.Item del pack, ya como modelo de ítem). */
        public String displayItem;
        public double health;
        public List<Mech> mechs = List.of();
        /** Display (text_display, item_display...): escala, desplazamiento y si mira a la cámara. */
        public float scaleX = 1F, scaleY = 1F, scaleZ = 1F;
        public float transX, transY, transZ;
        public String billboard;
        /** Un mob «de verdad» del pack (lobo, caballo, zombi...): en el juego anda, sigue a su dueño y ataca (esbirros). */
        public boolean living;
        public double speed = 0.25;
        /** La velocidad del tipo de Minecraft (IRON_GOLEM 0,25...): setspeed{s} la multiplica (jefes). */
        public double typeSpeed = 0.25;
        public double followRange = 32;
        // ----- Jefes (bosses/<jefe>.json)
        /** En el juego es un mob de verdad (se le pega, tiene IA): el jefe y sus esbirros. Si no, un efecto (actor). */
        public boolean entity;
        public String display;
        public double damage;
        public double armor;
        public boolean invincible;
        public boolean collidable = true;
        public String faction;
        /** Drops del pack («objeto cantidad probabilidad»): solo los suelta la fauna (los jefes no sueltan nada). */
        public List<String> drops = List.of();
        /** Modelo de ModelEngine que se pone al aparecer (para el tamaño, la vista previa y las cajas de golpe). */
        public String model;
        /** Daño que recibe por tipo (FIRE 0, FALL 0...): el multiplicador de MythicMobs. */
        public final Map<String, Double> damageModifiers = new HashMap<>();
        /**
         * IA de MythicMobs ya puesta sobre la de su tipo de Minecraft ({@link SkillAI}): qué hace (AIGoalSelectors) y a
         * quién busca (AITargetSelectors). Con «clear» solo (la Hidra), ni se mueve ni mira sola.
         */
        public List<String> goals = List.of();
        public List<String> aiTargets = List.of();
        /** Options: NoAI (sin IA desde que aparece: ni se mueve ni cae, como en Minecraft). */
        public boolean noAi;
        /** Options: NoGravity (no cae: la Tormenta del Wither flota). */
        public boolean noGravity;
        /** Options: LockPitch (la cabeza no mira arriba ni abajo). */
        public boolean lockPitch;
        /** Options: KnockbackResistance (0 a 1), o la de su tipo de Minecraft. */
        public double knockbackResistance = 1;
        /** Se quema al sol como su tipo (zombi, esqueleto, ahogado, fantasma) si el pack no pone PreventSunburn. */
        public boolean sunburn;
        /** Barra de jefe de las opciones del mob (BossBar: Enabled): color y estilo. */
        public String barColor;
        public String barStyle;
        public double barRange = 48;
        /** Hearing: Enabled: oye las vibraciones como un Warden (su ~onHear). */
        public boolean hearing;

        public boolean isDisplay() {
            return type != null && type.endsWith("_DISPLAY");
        }
    }

    /** Cómo se dispara sola una pasiva de MMOCore: TIMER cada «timer» ticks (como MythicLib), DAMAGED... */
    public record Passive(String type, double timer) {}

    /** name y desc: en español (los del pack si no hay traducción). */
    public record SkillDef(String id, String name, List<String> lore, double cooldown, double mana, String entry,
                           String icon, Passive passive, boolean hidden, Map<String, Double> mods, String desc) {
        /** Se lanza con su tecla (no es pasiva ni interna). */
        public boolean active() {
            return passive == null && !hidden;
        }
    }

    public static final class ClassDef {
        public String id;
        /** Nombre y papel en español (Bruja Tejedora · Hechicera, caos y maldiciones). */
        public String name;
        public String role = "";
        public List<String> lore = List.of();
        public List<SkillDef> skills = List.of();
        public final Map<String, Meta> tree = new HashMap<>();
        public final Map<String, MobDef> mobs = new HashMap<>();
        /** Icono de cada skill (textura de 32×32). */
        public final Map<String, String> icons = new HashMap<>();
        /** El set de objetos de la clase (armas y armadura, en skills/class_sets.json). */
        public String set;
        /** Objetos de MythicMobs que la clase lleva en la mano → objeto del mod (el Sacred Gear del Dragón Rojo...). */
        public final Map<String, String> handItems = new HashMap<>();
        /** Modelos de ítem que salen en los efectos (el cliente los carga al arrancar). */
        public final java.util.Set<String> itemModels = new java.util.HashSet<>();
        /** Letras de la fuente de la clase (tfclient:skills_&lt;id&gt;): impactos y pantallas que se dibujan como texto. */
        public String glyphs = "";
        /** Skills que el pack llama sin definirlas (en MythicMobs esas líneas no hacen nada). */
        public final java.util.Set<String> missing = new java.util.HashSet<>();
        /** Variables con las que empieza quien tiene la clase (si aún no las tiene): el modo de los esbirros. */
        public final Map<String, String> startVars = new HashMap<>();
        /** Es un jefe (assets/tfclient/bosses/), no una clase de jugador. */
        public boolean boss;
        public BossInfo bossInfo;

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

        /** El ataque básico de la clase (su combo): elegido, se queda y cada clic lo repite. null = no tiene. */
        public String basic;

        /** El sitio del ataque básico en la barra (-1 = no tiene). */
        public int basicSlot() {
            List<SkillDef> a = actives();
            for (int i = 0; i < a.size(); i++) if (a.get(i).id().equals(basic)) return i;
            return -1;
        }

        /** Clase de arco: la skill elegida sale al soltar la flecha con un arco (las demás, con el clic izquierdo). */
        public boolean bow() {
            return BOW_CLASSES.contains(id);
        }
    }

    /** Lo de un jefe que no es de MythicMobs: textos, valores base (Normal, un jugador), modelo y lo que suelta. */
    public static final class BossInfo {
        public String mob;
        public String name;
        public String title = "";
        public String desc = "";
        public int color = 0xFFFFFF;
        public double health;
        public double damage;
        /** La vida que traía el pack (para escalar lo que va por números fijos de vida: el escudo del Esqueleto). */
        public double packHealth;
        public String model;
        public float hitW;
        public float hitH;
        public List<String> attacks = List.of();
        /** Huesos que no salen en la vista previa de /tf boss (estelas, piezas de la escena). */
        public List<String> previewHide = List.of();
        /**
         * (1.3.88, orden del dueño: «que sus ataques no me eleven al cielo, debo mantenerme en el suelo») «suelo» en
         * tools/bosses/es.json: lo que lanza al jugador (throw, velocity, leap...) le da como mucho un saltito, nada si ya
         * está en el aire, y sus explosiones no le suben. El Contemplador.
         */
        public boolean ground;
        /**
         * (1.3.89, el dueño: «Ignevar es pendejo») «gira_al_atacar» en tools/bosses/es.json: un jefe que se gira con sus
         * propias skills y que el pack deja quieto mientras ataca (Ignevar echa el fuego 7 s sin girarse): con
         * inteligencia, se gira despacio hacia su objetivo mientras dura el ataque (BossEntity.turnBody).
         */
        public boolean turnWhileAttacking;
        /** Objetos que suelta (dropitem): id de MythicMobs → modelo de ítem y nombre. */
        public final Map<String, Drop> drops = new LinkedHashMap<>();
        /**
         * Su relevo: los mobs que saca al morir y siguen la pelea (el Mamut del Druida Boreal), en minúsculas → su
         * nombre en español. La grieta no se gana hasta que cae el último.
         */
        public final Map<String, String> successors = new HashMap<>();
        /**
         * (1.3.96) «fauna»: no es un jefe de grieta sino las criaturas de un bioma (las del Void, EndMobsVol1). Sus mobs
         * son todos esbirros sin dueño (sin la barra del jefe principal; la suya del pack, BossBar, sí); los saca
         * vacio/VoidFauna con las reglas de aparición del pack ({@link #spawns}). No salen en /tf boss ni en las grietas.
         */
        public boolean fauna;
        /** (1.3.96, fauna) Las reglas de su randomspawn de MythicMobs. */
        public List<Spawn> spawns = List.of();
        /** (1.3.96, fauna) Sus mobs (en minúsculas) → su nombre en español. */
        public final Map<String, String> faunaMobs = new LinkedHashMap<>();
    }

    /**
     * (1.3.96) Una regla de aparición de MythicMobs (RandomSpawns): el mob, su probabilidad cada vez que se mira, su
     * prioridad, su recarga en segundos, dónde (LAND...) y «mobsInRadius{a=&lt;N;r=R}»: no si ya hay más de maxNear de
     * ese mob a menos de radius bloques.
     */
    public record Spawn(String name, String mob, double chance, int priority, int cooldown, String position, double radius, int maxNear) {}

    /** Un objeto que suelta un jefe: su modelo (y el de cubrirse, si es escudo), nombre, tipo y sus skills del pack
     * (~onSwing al golpear con él, ~onTimer mientras se lleva). */
    /** Un objeto de un pack de jefe. type MATERIAL (1.3.96): de Oraxen, lo que suelta la fauna (se apila; rarity: su color). */
    public record Drop(String id, String material, String model, String blockingModel, String name, String type, List<Mech> mechs, String rarity) {}

    /** Las clases de arqueros (sus packs disparaban con el arco de la clase): Arquero, Arquero Despertado y Null. */
    private static final java.util.Set<String> BOW_CLASSES = java.util.Set.of("arquero", "arquero_despertado", "null");

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
        if (id == null) return null;
        ClassDef c = all().get(id);
        return c != null ? c : bosses().get(id);
    }

    private static Map<String, ClassDef> bosses;

    /** Los jefes que trae el mod (assets/tfclient/bosses/), en el orden del índice. */
    public static synchronized Map<String, ClassDef> bosses() {
        if (bosses == null) {
            Map<String, ClassDef> out = new LinkedHashMap<>();
            JsonElement cat = readJson("/assets/tfclient/bosses/catalog.json");
            if (cat != null && cat.isJsonArray()) {
                for (JsonElement e : cat.getAsJsonArray()) {
                    String id = e.getAsJsonObject().get("id").getAsString();
                    try {
                        JsonElement json = readJson("/assets/tfclient/bosses/" + id + ".json");
                        if (json != null) out.put(id, parse(json.getAsJsonObject()));
                    } catch (Exception ex) {
                        TFClient.LOGGER.error("TF Jefes: no se pudo leer el jefe {}", id, ex);
                    }
                }
            }
            bosses = Collections.unmodifiableMap(out);
            TFClient.LOGGER.info("TF Jefes: {} jefes cargados", bosses.size());
        }
        return bosses;
    }

    private static Map<String, ClassDef> fauna;

    /**
     * (1.3.96) La fauna (assets/tfclient/bosses/fauna.json): packs de criaturas que no son jefes de grieta (las del bioma
     * del Void). Van con los mismos datos que los jefes, pero aparte: no salen en /tf boss ni en las grietas.
     */
    public static synchronized Map<String, ClassDef> fauna() {
        if (fauna == null) {
            Map<String, ClassDef> out = new LinkedHashMap<>();
            JsonElement cat = readJson("/assets/tfclient/bosses/fauna.json");
            if (cat != null && cat.isJsonArray()) {
                for (JsonElement e : cat.getAsJsonArray()) {
                    String id = e.getAsJsonObject().get("id").getAsString();
                    try {
                        JsonElement json = readJson("/assets/tfclient/bosses/" + id + ".json");
                        if (json != null) out.put(id, parse(json.getAsJsonObject()));
                    } catch (Exception ex) {
                        TFClient.LOGGER.error("TF Jefes: no se pudo leer la fauna {}", id, ex);
                    }
                }
            }
            fauna = Collections.unmodifiableMap(out);
            TFClient.LOGGER.info("TF Jefes: {} packs de fauna cargados", fauna.size());
        }
        return fauna;
    }

    /** El jefe o la fauna con ese id (lo que puede ser una entidad del motor de jefes). */
    public static ClassDef bossClass(String id) {
        ClassDef c = bosses().get(id);
        return c != null ? c : fauna().get(id);
    }

    /** Carpeta de los modelos de ModelEngine de ese id («hidra.lr_hydra» → bosses/models/, los demás skills/models/). */
    public static String modelDir(String id) {
        // (la grieta y, desde la 1.3.88, el caballero de luz de The Void «vacio.caballero» están con los de los jefes)
        return id != null && (id.startsWith("grieta.") || id.startsWith("vacio.")) || isBoss(id) ? "bosses/models/" : "skills/models/";
    }

    /** ¿Es de un jefe ese id de clase o de modelo («hidra» o «hidra.lr_hydra»)? */
    public static boolean isBoss(String id) {
        if (id == null) return false;
        int dot = id.indexOf('.');
        String c = dot > 0 ? id.substring(0, dot) : id;
        return bosses().containsKey(c) || fauna().containsKey(c);
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
        c.name = str(cls, "name_es", str(cls, "name", c.id));
        c.role = str(cls, "role_es", "");
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
            String name = str(s, "name_es", str(s, "name", s.get("id").getAsString()));
            skills.add(new SkillDef(s.get("id").getAsString(), name, strings(s.get("lore")),
                    num(s.get("cooldown"), 0), num(s.get("mana"), 0), str(s, "entry", null), str(s, "icon", null), passive,
                    s.has("hidden") && s.get("hidden").getAsBoolean(), Map.copyOf(mods), str(s, "desc_es", "")));
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
        c.set = str(o, "set", null);
        c.basic = str(o, "basic", null);
        c.glyphs = str(o, "glyphs", "");
        for (String x : strings(o.get("missing"))) c.missing.add(x.toLowerCase(Locale.ROOT));
        if (o.has("boss") && o.get("boss").isJsonObject()) {
            JsonObject b = o.getAsJsonObject("boss");
            BossInfo bi = new BossInfo();
            bi.mob = str(b, "mob", "");
            bi.name = str(b, "name", c.id);
            bi.title = str(b, "title", "");
            bi.desc = str(b, "desc", "");
            try {
                bi.color = Integer.parseInt(str(b, "color", "#ffffff").replace("#", ""), 16);
            } catch (NumberFormatException ignored) {
                // blanco
            }
            bi.health = num(b.get("health"), 100);
            bi.damage = num(b.get("damage"), 5);
            bi.packHealth = num(b.get("pack_health"), bi.health);
            bi.model = str(b, "model", null);
            if (b.has("hitbox") && b.get("hitbox").isJsonArray()) {
                bi.hitW = (float) num(b.getAsJsonArray("hitbox").get(0), 1);
                bi.hitH = (float) num(b.getAsJsonArray("hitbox").get(1), 2);
            }
            bi.attacks = strings(b.get("attacks"));
            if (b.has("successors") && b.get("successors").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : b.getAsJsonObject("successors").entrySet()) {
                    bi.successors.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue().getAsString());
                }
            }
            bi.previewHide = strings(b.get("preview_hide"));
            bi.fauna = b.has("fauna") && b.get("fauna").getAsBoolean();
            if (b.has("spawns") && b.get("spawns").isJsonArray()) {
                List<Spawn> sp = new ArrayList<>();
                for (JsonElement e : b.getAsJsonArray("spawns")) {
                    JsonObject so = e.getAsJsonObject();
                    sp.add(new Spawn(str(so, "name", ""), str(so, "mob", ""), num(so.get("chance"), 1), (int) num(so.get("priority"), 1),
                            (int) num(so.get("cooldown"), 0), str(so, "position", "LAND"), num(so.get("radius"), 0), (int) num(so.get("max_near"), 0)));
                }
                bi.spawns = List.copyOf(sp);
            }
            if (b.has("fauna_mobs") && b.get("fauna_mobs").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : b.getAsJsonObject("fauna_mobs").entrySet()) {
                    bi.faunaMobs.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue().getAsString());
                }
            }
            bi.ground = b.has("suelo") && b.get("suelo").getAsBoolean();
            bi.turnWhileAttacking = b.has("gira_al_atacar") && b.get("gira_al_atacar").getAsBoolean();
            if (o.has("drops") && o.get("drops").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("drops").entrySet()) {
                    JsonObject d = e.getValue().getAsJsonObject();
                    bi.drops.put(e.getKey().toLowerCase(Locale.ROOT), new Drop(e.getKey(), str(d, "material", "paper"),
                            str(d, "model", null), str(d, "blocking", null), str(d, "name_es", str(d, "name", e.getKey())),
                            str(d, "type", ""), mechs(d.get("mechs")), str(d, "rarity", "EPIC")));
                }
            }
            c.boss = true;
            c.bossInfo = bi;
            c.name = bi.name;
        }
        if (o.has("start_vars") && o.get("start_vars").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("start_vars").entrySet()) {
                c.startVars.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue().getAsString());
            }
        }
        if (o.has("hand_items") && o.get("hand_items").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("hand_items").entrySet()) {
                c.handItems.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue().getAsString());
            }
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
            mob.displayItem = str(m, "display_item", null);
            mob.health = num(m.get("health"), 0);
            mob.mechs = mechs(m.get("mechs"));
            if (m.has("display_options") && m.get("display_options").isJsonObject()) {
                JsonObject d = m.getAsJsonObject("display_options");
                float[] sc = floats(str(d, "Scale", "1,1,1"), 1F);
                mob.scaleX = sc[0];
                mob.scaleY = sc[1];
                mob.scaleZ = sc[2];
                float[] tr = floats(str(d, "Translation", "0,0,0"), 0F);
                mob.transX = tr[0];
                mob.transY = tr[1];
                mob.transZ = tr[2];
                mob.billboard = str(d, "Billboard", null);
            }
            mob.living = LIVING.contains(mob.type);
            mob.speed = num(opts.get("MovementSpeed"), mob.speed);
            mob.followRange = num(opts.get("FollowRange"), mob.followRange);
            if (c.boss) {
                // Los mobs de un jefe: el de verdad (entity) o el efecto; su velocidad base (la del tipo de Minecraft,
                // que es con la que cuenta setspeed) y la de las opciones si la pone
                mob.entity = "entity".equals(str(m, "kind", "actor"));
                mob.living = false; // sus esbirros no son los de las clases (llevan su propia IA de mob de verdad)
                double typeSpeed = num(m.get("speed"), 0.25);
                mob.typeSpeed = typeSpeed;
                mob.speed = opt(opts, "MovementSpeed") != null ? num(opt(opts, "MovementSpeed"), typeSpeed) : typeSpeed;
                mob.display = str(m, "display", null);
                mob.damage = num(m.get("damage"), 0);
                mob.armor = num(m.get("armor"), 0);
                mob.invincible = bool(opt(opts, "Invincible"));
                mob.collidable = opt(opts, "Collidable") == null || bool(opt(opts, "Collidable"));
                mob.faction = str(m, "faction", null);
                if (m.has("drops") && m.get("drops").isJsonArray()) mob.drops = strings(m.get("drops"));
                mob.model = str(m, "model", null);
                if (m.has("damage_modifiers") && m.get("damage_modifiers").isJsonArray()) {
                    for (JsonElement dm : m.getAsJsonArray("damage_modifiers")) {
                        String[] p = dm.getAsString().trim().split("\\s+");
                        if (p.length >= 2) mob.damageModifiers.put(p[0].toUpperCase(Locale.ROOT), Math.max(0, num(new com.google.gson.JsonPrimitive(p[1]), 1)));
                    }
                }
                mob.goals = SkillAI.apply(SkillAI.vanillaGoals(mob.type), m.has("ai_goals") ? strings(m.get("ai_goals")) : null, false);
                mob.aiTargets = SkillAI.apply(SkillAI.vanillaTargets(mob.type), m.has("ai_targets") ? strings(m.get("ai_targets")) : null, true);
                mob.noAi = bool(opt(opts, "NoAI"));
                mob.noGravity = bool(opt(opts, "NoGravity"));
                mob.sunburn = SUNBURN.contains(mob.type) && !bool(opt(opts, "PreventSunburn"));
                mob.lockPitch = bool(opt(opts, "LockPitch"));
                mob.knockbackResistance = opt(opts, "KnockbackResistance") != null
                        ? Math.max(0, Math.min(1, num(opt(opts, "KnockbackResistance"), 0))) : SkillAI.knockbackResistance(mob.type);
                // Un esqueleto sin el arco que le da Minecraft (PreventRandomEquipment) pega cuerpo a cuerpo
                // (AbstractSkeleton.reassessWeaponGoal)
                if (bool(opt(opts, "PreventRandomEquipment"))) mob.goals = SkillAI.withoutBow(mob.goals);
                if (m.has("bossbar") && m.get("bossbar").isJsonObject()) {
                    JsonObject bb = m.getAsJsonObject("bossbar");
                    if (bool(bb.get("Enabled"))) {
                        mob.barColor = str(bb, "Color", "RED");
                        mob.barStyle = str(bb, "Style", "SOLID");
                        mob.barRange = num(bb.get("Range"), 48);
                    }
                }
                mob.hearing = m.has("hearing") && m.get("hearing").isJsonObject() && bool(m.getAsJsonObject("hearing").get("Enabled"));
            }
            if (mob.headModel != null) c.itemModels.add(mob.headModel);
            if (mob.displayItem != null) c.itemModels.add(mob.displayItem);
            c.mobs.put(e.getKey().toLowerCase(Locale.ROOT), mob);
        }
        return c;
    }

    /** Los tipos de Minecraft que arden al sol (Mob.isSunBurnTick: zombis, esqueletos, ahogados, fantasmas). */
    private static final java.util.Set<String> SUNBURN = java.util.Set.of("ZOMBIE", "ZOMBIE_VILLAGER", "DROWNED", "SKELETON",
            "STRAY", "PHANTOM");

    /** Tipos de MythicMobs que son un mob con IA (los esbirros); los soportes y los displays son solo efectos. */
    private static final java.util.Set<String> LIVING = java.util.Set.of("WOLF", "HORSE", "ZOMBIE", "SKELETON", "HUSK",
            "VEX", "ALLAY", "CAT", "FOX", "IRON_GOLEM", "SPIDER", "PHANTOM", "BLAZE", "PIG", "POLAR_BEAR", "RAVAGER",
            "DROWNED", "WITHER_SKELETON", "STRAY", "SKELETON_HORSE", "ZOMBIE_HORSE", "BEE", "PARROT", "GOAT");

    private static float[] floats(String s, float def) {
        float[] out = {def, def, def};
        String[] p = s.split(",");
        for (int i = 0; i < Math.min(3, p.length); i++) {
            try {
                out[i] = Float.parseFloat(p[i].trim());
            } catch (NumberFormatException ignored) {
                // se queda el de por defecto
            }
        }
        if (p.length == 1) out[1] = out[2] = out[0];
        return out;
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
            m.tc = conds(o.get("tc"));
            m.hc = conds(o.get("hc"));
            m.sc = conds(o.get("sc"));
            m.bc = conds(o.get("bc"));
            m.chance = num(o.get("ch"), 1.0);
            m.hp = o.has("hp") && !o.get("hp").isJsonNull() ? o.get("hp").getAsString() : null;
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
            out.add(new Cond(m, args(o.get("a")), not, str(o, "v", "true").toLowerCase(Locale.ROOT).trim(), conds(o.get("parts"))));
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

    /** Una opción de MythicMobs sin mirar mayúsculas («noAI», «NoAi» y «NoAI» son la misma). */
    private static JsonElement opt(JsonObject opts, String key) {
        if (opts.has(key)) return opts.get(key);
        for (Map.Entry<String, JsonElement> e : opts.entrySet()) if (e.getKey().equalsIgnoreCase(key)) return e.getValue();
        return null;
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
