package net.tierrasfantasticas.tfclient.skills;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.skills.SkillDefs.ClassDef;
import net.tierrasfantasticas.tfclient.skills.SkillDefs.Cond;
import net.tierrasfantasticas.tfclient.skills.SkillDefs.Mech;
import net.tierrasfantasticas.tfclient.skills.SkillDefs.Meta;
import net.tierrasfantasticas.tfclient.skills.SkillDefs.MobDef;

/**
 * El intérprete de las skills (el subconjunto de MythicMobs que usan los packs): ejecuta las mecánicas línea a línea
 * con sus esperas (delay), objetivos, condiciones y probabilidades, invoca los efectos (actores), lanza proyectiles,
 * auras y tótems, y hace el daño. Todo en el servidor, tick a tick; los jugadores solo ven el resultado.
 *
 * <p>Reglas de MythicMobs que se respetan: una skill llamada con skill{} sigue su propio tiempo (sus delay no paran a
 * quien la llama); sin @objetivo, la mecánica usa los objetivos heredados; las condiciones de la skill (Conditions) son
 * de quien lanza, las TargetConditions filtran los objetivos y sus acciones (true, false, cancel, castinstead,
 * orElseCast, power) hacen lo mismo que allí. Lo que no existe aquí (IA de mobs, comandos) no hace nada.
 */
public final class SkillRuntime {
    private SkillRuntime() {}

    static long clock;
    private static final List<Script> SCRIPTS = new ArrayList<>();
    private static final List<Task> TASKS = new ArrayList<>();
    static final List<SkillActor> ACTORS = new ArrayList<>();
    private static final Map<Object, WhoState> STATES = new HashMap<>();
    private static final Map<BlockKey, BlockState> PLACED = new HashMap<>();
    private static final Set<String> WARNED = new HashSet<>();
    /** Nadie puede tener más de esto en marcha a la vez (por si un pack se llama a sí mismo sin fin). */
    private static final int MAX_SCRIPTS = 4000;
    private static final int MAX_ACTORS = 2000;
    static boolean inDamage;

    // ------------------------------------------------------------------------------------------- quién

    /** Quien ejecuta una skill o es objetivo: una entidad del mundo o un actor (efecto). */
    public static final class Who {
        final Entity entity;
        final SkillActor actor;

        private Who(Entity entity, SkillActor actor) {
            this.entity = entity;
            this.actor = actor;
        }

        public static Who of(Entity e) {
            return e == null ? null : new Who(e, null);
        }

        static Who of(SkillActor a) {
            return new Who(null, a);
        }

        Vec3 pos() {
            return entity != null ? entity.position() : actor.pos;
        }

        Vec3 eye() {
            return entity != null ? entity.getEyePosition() : actor.pos.add(0, actor.def != null && actor.def.small ? 0.7 : 1.6, 0);
        }

        float yaw() {
            if (entity instanceof SkillMobs.Host h) return h.lookYaw();
            if (entity instanceof LivingEntity le && !(entity instanceof Player)) return le.yBodyRot;
            return entity != null ? entity.getYRot() : actor.yaw;
        }

        float pitch() {
            return entity != null ? entity.getXRot() : actor.pitch;
        }

        ServerLevel level() {
            return entity != null ? (ServerLevel) entity.level() : actor.level;
        }

        boolean alive() {
            return entity != null ? entity.isAlive() && !entity.isRemoved() : actor.alive;
        }

        /** Quien invocó al actor (para @owner, @parent). Una entidad no tiene. */
        Who owner() {
            if (actor != null) return actor.owner;
            // Un mob de jefe que se invocó (esbirro, cabeza de bindhitbox): su padre es el jefe que lo sacó, como el
            // «parent» de MythicMobs (@parent, signal @parent)
            SkillMobs.Host h = entity == null ? null : SkillMobs.host(entity);
            return h != null && h.boss() != h ? Who.of(h.boss().mob()) : null;
        }

        /** El jugador al que se le cuenta lo que hace (daño, muertes). */
        ServerPlayer player() {
            Who w = this;
            for (int i = 0; i < 16 && w != null; i++) {
                if (w.entity instanceof ServerPlayer p) return p;
                w = w.owner();
            }
            return null;
        }

        Object key() {
            return entity != null ? entity.getUUID() : (Object) actor.id;
        }

        boolean same(Who o) {
            return o != null && key().equals(o.key());
        }

        Vec3 forward() {
            return dir(yaw(), pitch());
        }
    }

    /** Lo que se guarda de cada quién: auras, variables y cooldowns de sus skills de MythicMobs. */
    static final class WhoState {
        final Map<String, SkillAuras.Aura> auras = new HashMap<>();
        final Map<String, String> vars = new HashMap<>();
        final Map<String, Long> cooldowns = new HashMap<>();
        /** Etiquetas (addtag/hastag), postura (setstance/stance), facción (setfaction/faction) y el gcd (setgcd/offgcd). */
        final Set<String> tags = new HashSet<>();
        String stance;
        String faction;
        long gcdUntil;
        long touched;
        /** tracklocation: el sitio guardado (@TrackedLocation, distanceFromTrackedLocation). */
        Vec3 tracked;
        /** La última señal que recibió (condición lastsignal). */
        String lastSignal;
        /** Puntuaciones de un mob (setmobscore, settargetscore, modifytargetscore): en memoria, no en el marcador. */
        final Map<String, Double> scores = new HashMap<>();
    }

    static WhoState state(Who w) {
        WhoState s = STATES.computeIfAbsent(w.key(), k -> new WhoState());
        s.touched = clock;
        return s;
    }

    static WhoState stateIfAny(Who w) {
        return STATES.get(w.key());
    }

    /** Para la prueba en el juego: las variables del pack y el gcd de una entidad («movement=forward ... gcd=12»). */
    public static String debugVars(Entity e) {
        WhoState st = stateIfAny(Who.of(e));
        if (st == null) return "sin estado";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> v : new java.util.TreeMap<>(st.vars).entrySet()) {
            if (sb.length() > 360) break;
            String val = v.getValue() == null ? "" : v.getValue();
            sb.append(v.getKey()).append('=').append(val.length() > 18 ? val.substring(0, 18) : val).append(' ');
        }
        return sb.append("gcd=").append(Math.max(0, st.gcdUntil - clock)).toString();
    }

    // ------------------------------------------------------------------------------------------- objetivos

    /** Un objetivo: una entidad, un actor o un punto. power: multiplicador (condición «power»). */
    static final class Tgt {
        final Who who;
        final Vec3 at;
        double power = 1.0;

        Tgt(Who who, Vec3 at) {
            this.who = who;
            this.at = at;
        }

        static Tgt of(Who w) {
            return new Tgt(w, null);
        }

        static Tgt at(Vec3 p) {
            return new Tgt(null, p);
        }

        Vec3 pos() {
            return who != null ? who.pos() : at;
        }

        Entity entity() {
            return who != null ? who.entity : null;
        }

        LivingEntity living() {
            return who != null && who.entity instanceof LivingEntity le ? le : null;
        }
    }

    /** Datos de una ejecución (el SkillMetadata de MythicMobs). */
    static final class Ctx {
        final ClassDef cls;
        final Who caster;
        Who trigger;
        Vec3 origin;
        List<Tgt> targets;
        /** La entidad a la que apuntaba el jugador al lanzar (@target). */
        Entity aim;
        double power = 1.0;
        /** Dirección del proyectil que la lanzó (para @projectileforward). */
        Vec3 dir;
        int depth;
        /** Variables de la ejecución (skill.*). */
        Map<String, String> vars;
        /** Valores de la skill de clase que la lanzó (daño, duración...): &lt;skill.damage&gt;, &lt;modifier.damage&gt;. */
        Map<String, Double> mods = Map.of();
        /** El proyectil que la lanzó (para modifyprojectile, endprojectile, setprojectiledirection). */
        SkillMovers.Mover mover;
        /** Parámetros con los que la llamaron (skill{s=X;damage=4}): &lt;skill.damage&gt; dentro de X. */
        Map<String, String> params = Map.of();
        /** La lanzó una pasiva por tiempo (TIMER): sus partículas van muy rebajadas (ver SkillFx). */
        boolean ambient;
        /** De qué uso de una skill viene (cada clic, cada pasiva): la inmunidad a golpes de skill es de cada uso. */
        long cast;
        /** El jefe del que viene (su dificultad: daño, ritmo de ataque; a quién no pega); null = skills de jugador. */
        SkillMobs.Host host;
        /** El tipo de daño que lo disparó (~onDamaged): ENTITY_ATTACK, PROJECTILE... */
        String damageCause;
        /** El golpe que lo disparó, aún sin entrar (~onDamaged): cancelevent y modifydamage lo cambian. */
        SkillMobs.Hit hit;
        /** La skill con nombre de la que viene (para la prueba de jefes: qué ataques hacen daño). */
        String skill;

        Ctx(ClassDef cls, Who caster) {
            this.cls = cls;
            this.caster = caster;
        }

        Ctx copy() {
            Ctx c = new Ctx(cls, caster);
            c.trigger = trigger;
            c.origin = origin;
            c.targets = targets;
            c.aim = aim;
            c.power = power;
            c.dir = dir;
            c.depth = depth + 1;
            c.vars = vars;
            c.mods = mods;
            c.mover = mover;
            c.params = params;
            c.ambient = ambient;
            c.cast = cast;
            c.host = host;
            c.damageCause = damageCause;
            c.hit = hit;
            c.skill = skill;
            return c;
        }

        /** La misma ejecución, pero la lanza otro (el actor invocado, el objetivo de sudoskill). */
        Ctx as(Who who) {
            Ctx c = new Ctx(cls, who);
            c.trigger = caster;
            c.origin = who.pos();
            c.targets = null;
            c.aim = aim;
            c.power = power;
            c.depth = depth + 1;
            c.mods = mods;
            c.cast = cast;
            c.host = host;
            c.skill = skill;
            // Un mob de jefe que lanza (el esbirro, el brote): su propio jefe
            SkillMobs.Host own = who.entity == null ? null : SkillMobs.host(who.entity);
            if (own != null) c.host = own.boss();
            return c;
        }
    }

    /** Una lista de mecánicas en marcha (se para en cada delay). */
    private static final class Script {
        final Ctx ctx;
        final List<Mech> mechs;
        int pc;
        int wait;

        Script(Ctx ctx, List<Mech> mechs) {
            this.ctx = ctx;
            this.mechs = mechs;
        }

        /** Ejecuta hasta el siguiente delay. true = ha terminado. */
        boolean run() {
            while (pc < mechs.size()) {
                if (wait > 0) return false;
                if (!ctx.caster.alive() && ctx.caster.actor != null) return true;
                Mech m = mechs.get(pc++);
                if (m.m.equals("delay")) {
                    // «delay "<modifier.duration> * 20"»: con variables y cuentas
                    String d = unquote(m.arg("0", "_", "ticks", "t", "d"));
                    if (d.indexOf('<') >= 0) d = SkillVars.text(d, ctx, null);
                    wait = (int) Math.max(0, Math.round(num(d, 0) / tempo(ctx)));
                    continue;
                }
                try {
                    exec(m, ctx);
                } catch (CancelSkill c) {
                    return true;
                } catch (Throwable t) {
                    warn("error en " + m.m + ": " + t);
                    TFClient.LOGGER.debug("TF Skills: error en la mecánica {}", m.m, t);
                }
            }
            return true;
        }
    }

    /** cancelskill: para la skill que se está ejecutando (lo que queda de su lista no se hace). */
    static final class CancelSkill extends RuntimeException {
        CancelSkill() {
            super(null, null, false, false);
        }
    }

    /** Una tarea programada (delay=, repeat=); cast: de qué uso de una skill es (para poder cancelarla: los combos). */
    private record Task(long at, long cast, Runnable run) {}

    private static boolean tasksFull;

    static void later(int ticks, Runnable r) {
        later(ticks, 0L, r);
    }

    static void later(int ticks, long cast, Runnable r) {
        if (ticks <= 0) {
            r.run();
            return;
        }
        if (TASKS.size() > 20000) {
            if (!tasksFull) TFClient.LOGGER.warn("TF Skills: demasiadas tareas pendientes (20000); se descartan las nuevas");
            tasksFull = true;
            return;
        }
        tasksFull = false;
        TASKS.add(new Task(clock + ticks, cast, r));
    }

    /**
     * Cancela lo que le queda a un uso de una skill (sus líneas con delay y sus listas esperando un delay): el cerebro de
     * los jefes lo usa para encadenar un ataque con el siguiente cortando la recuperación del anterior (combos).
     */
    static void cancelCast(long cast) {
        if (cast == 0L) return;
        TASKS.removeIf(t -> t.cast() == cast);
        SCRIPTS.removeIf(s -> s.ctx.cast == cast);
    }

    /** Lo rápido que ataca el jefe (1 = como el pack): los retardos se dividen y sus animaciones se aceleran igual. */
    static double tempo(Ctx ctx) {
        return ctx.host == null ? 1.0 : Math.max(0.5, Math.min(2.0, ctx.host.tempo()));
    }

    // ------------------------------------------------------------------------------------------- entrada

    /**
     * Lanza la skill de MythicMobs «entry» de una skill de clase como lo haría MMOCore: quien lanza es el jugador,
     * @target es lo que tiene delante (hasta 32 bloques) y, sin objetivo, la mecánica va a él mismo.
     */
    static boolean cast(ServerPlayer player, ClassDef cls, SkillDefs.SkillDef skill) {
        return cast(player, cls, skill, false);
    }

    /** ambient: la lanza una pasiva por tiempo (sus partículas, muy rebajadas). */
    static boolean cast(ServerPlayer player, ClassDef cls, SkillDefs.SkillDef skill, boolean ambient) {
        Meta meta = cls.meta(skill.entry());
        if (meta == null) return false;
        Ctx ctx = new Ctx(cls, Who.of(player));
        ctx.ambient = ambient;
        ctx.cast = ++castSeq;
        if (!cls.startVars.isEmpty()) {
            Map<String, String> vars = state(ctx.caster).vars;
            cls.startVars.forEach(vars::putIfAbsent);
        }
        ctx.mods = skill.mods();
        ctx.origin = player.position();
        ctx.aim = aimed(player, 32);
        ctx.targets = null;
        ctx.vars = new HashMap<>();
        long before = applied;
        int scripts = SCRIPTS.size(), tasks = TASKS.size();
        if (!runMeta(meta, ctx)) return false;
        // Si sus condiciones la pararon por dentro (ya lanzando otra, sin la carga que pide...) y no hizo nada ni dejó
        // nada pendiente, no ha salido: no gasta el cooldown y el clic es un golpe normal
        return applied != before || SCRIPTS.size() > scripts || TASKS.size() > tasks;
    }

    /** Mecánicas que han hecho algo (todas menos las que solo llaman a otra skill): para saber si una skill salió. */
    private static long applied;

    /** Ejecuta una skill de MythicMobs (con su cooldown y sus condiciones). false si no se lanzó. */
    static boolean runMeta(Meta meta, Ctx ctx) {
        if (meta == null || ctx.depth > 40 || SCRIPTS.size() > MAX_SCRIPTS) return false;
        if (meta.cooldown > 0) {
            WhoState st = stateIfAny(ctx.caster);
            Long until = st == null ? null : st.cooldowns.get(meta.name);
            if (until != null && until > clock) return false;
        }
        Ctx run = ctx;
        // Conditions: de quien lanza
        for (Cond c : meta.conditions) {
            boolean r = SkillConds.test(c, run.caster, run, null);
            switch (outcome(c, r, run)) {
                case STOP:
                    return false;
                case REPLACED:
                    return true;
                default:
            }
        }
        // TriggerConditions: de quien la disparó
        if (!meta.triggerConditions.isEmpty() && run.trigger != null) {
            for (Cond c : meta.triggerConditions) {
                boolean r = SkillConds.test(c, run.trigger, run, null);
                if (outcome(c, r, run) != Outcome.GO) return false;
            }
        }
        // TargetConditions: se quedan los objetivos que las cumplen
        if (!meta.targetConditions.isEmpty()) {
            List<Tgt> in = run.targets != null ? run.targets : List.of(Tgt.of(run.caster));
            List<Tgt> out = new ArrayList<>();
            boolean replaced = false;
            for (Tgt t : in) {
                Tgt copy = new Tgt(t.who, t.at);
                copy.power = t.power;
                boolean keep = true;
                for (Cond c : meta.targetConditions) {
                    boolean r = SkillConds.test(c, t.who, run, t.at);
                    String act = c.action();
                    if (act.startsWith("power")) {
                        if (r) copy.power *= num(act.substring(5).trim(), 1);
                        continue;
                    }
                    if (act.startsWith("cast ")) {
                        if (r) {
                            Ctx other = run.copy();
                            other.targets = List.of(copy);
                            runNamed(act.substring(5).trim(), other);
                        }
                        continue;
                    }
                    // castinstead / orElseCast: ese objetivo se va a la otra skill; los demás siguen
                    boolean alt = act.startsWith("castinstead");
                    if (alt || act.startsWith("orelsecast")) {
                        if (r == alt) {
                            Ctx other = run.copy();
                            other.targets = List.of(copy);
                            runNamed(act.substring((alt ? "castinstead" : "orelsecast").length()).trim(), other);
                            replaced = true;
                            keep = false;
                            break;
                        }
                        continue;
                    }
                    if (!passes(act, r)) {
                        keep = false;
                        break;
                    }
                }
                if (keep) out.add(copy);
            }
            if (out.isEmpty()) return replaced;
            run = run.copy();
            run.depth = ctx.depth;
            run.targets = out;
        }
        // El cooldown solo cuenta si la skill sale de verdad (como MythicMobs): antes se ponía aunque sus condiciones
        // la pararan, y los ataques de los jefes que se miran cada segundo (distancia, ángulo...) se quedaban en
        // recarga sin haber salido: casi siempre quedaba uno solo
        if (meta.cooldown > 0) state(ctx.caster).cooldowns.put(meta.name, clock + Math.round(meta.cooldown * 20 / rhythm(ctx)));
        Script s = new Script(run, meta.mechs);
        if (!s.run()) SCRIPTS.add(s);
        return true;
    }

    /**
     * ¿Saldría ahora esta skill? Lo mismo que mira runMeta (cooldown, condiciones de quien lanza y de los objetivos)
     * sin hacer nada: el cerebro de los jefes (SkillBrain) prueba así cada ataque antes de elegir. Una condición con
     * castinstead/orelseCast que se dispararía cuenta como que sale (haría algo).
     */
    static boolean usable(Meta meta, Ctx ctx) {
        if (meta == null) return false;
        if (meta.cooldown > 0) {
            WhoState st = stateIfAny(ctx.caster);
            Long until = st == null ? null : st.cooldowns.get(meta.name);
            if (until != null && until > clock) return false;
        }
        for (Cond c : meta.conditions) {
            String act = c.action();
            if (act.startsWith("cast ") || act.startsWith("power")) continue;
            boolean r = SkillConds.test(c, ctx.caster, ctx, null);
            if (act.startsWith("castinstead")) {
                if (r) return true;
                continue;
            }
            if (act.startsWith("orelsecast")) {
                if (!r) return true;
                continue;
            }
            if (!passes(act, r)) return false;
        }
        if (meta.targetConditions.isEmpty()) return true;
        List<Tgt> in = ctx.targets != null ? ctx.targets : List.of(Tgt.of(ctx.caster));
        for (Tgt t : in) {
            boolean keep = true;
            for (Cond c : meta.targetConditions) {
                String act = c.action();
                if (act.startsWith("power") || act.startsWith("cast ")) continue;
                boolean r = SkillConds.test(c, t.who, ctx, t.at);
                if (act.startsWith("castinstead") || act.startsWith("orelsecast")) {
                    if (r == act.startsWith("castinstead")) return true;
                    continue;
                }
                if (!passes(act, r)) {
                    keep = false;
                    break;
                }
            }
            if (keep) return true;
        }
        return false;
    }

    /**
     * ¿Podría salir pronto? Como {@link #usable}, pero sin su cooldown, sin las condiciones de «skip» (distancias, gcd)
     * y sin mirar a los objetivos: lo que dice si es de esta fase o de esta postura (la Abominación, por debajo del 50 %;
     * el Esqueleto, con el escudo arriba o abajo).
     */
    static boolean possible(Meta meta, Ctx ctx, Set<String> skip) {
        if (meta == null) return false;
        for (Cond c : meta.conditions) {
            if (skip.contains(c.m())) continue;
            String act = c.action();
            if (act.startsWith("cast ") || act.startsWith("power")) continue;
            boolean r = SkillConds.test(c, ctx.caster, ctx, null);
            if (act.startsWith("castinstead")) {
                if (r) return true;
                continue;
            }
            if (act.startsWith("orelsecast")) {
                if (!r) return true;
                continue;
            }
            if (!passes(act, r)) return false;
        }
        return true;
    }

    private enum Outcome { GO, STOP, REPLACED }

    /** Qué hace una condición de la skill según su resultado (true, false, cancel, castinstead X, orElseCast X, power N). */
    private static Outcome outcome(Cond c, boolean r, Ctx ctx) {
        String act = c.action();
        if (act.startsWith("cast ")) {
            // «cast X»: si se cumple, lanza también X; la skill sigue igual
            if (r) runNamed(act.substring(5).trim(), ctx);
            return Outcome.GO;
        }
        if (act.startsWith("castinstead")) {
            if (!r) return Outcome.GO;
            runNamed(act.substring("castinstead".length()).trim(), ctx);
            return Outcome.REPLACED;
        }
        if (act.startsWith("orelsecast")) {
            if (r) return Outcome.GO;
            runNamed(act.substring("orelsecast".length()).trim(), ctx);
            return Outcome.REPLACED;
        }
        if (act.startsWith("power")) {
            if (r) ctx.power *= num(act.substring(5).trim(), 1);
            return Outcome.GO;
        }
        return passes(act, r) ? Outcome.GO : Outcome.STOP;
    }

    private static boolean passes(String act, boolean r) {
        if (act.equals("false")) return !r;
        if (act.equals("cancel")) return !r;
        return r; // true, required, vacío
    }

    static void runNamed(String name, Ctx ctx) {
        if (name == null || name.isBlank()) return;
        Ctx c = ctx.copy();
        c.skill = name.trim();
        runMeta(ctx.cls.meta(name.trim()), c);
    }

    // ------------------------------------------------------------------------------------------- mecánicas

    /** Ejecuta una línea: probabilidad, condiciones en línea, objetivos, delay/repeat de la línea y la mecánica. */
    static void exec(Mech raw, Ctx ctx) {
        Mech m = raw.dyn ? SkillVars.resolve(raw, ctx) : raw;
        if (m.chance < 1.0 && ThreadLocalRandom.current().nextDouble() >= m.chance) return;
        // Modificador de vida de la línea («- skill{s=X} @self <75%»), también dentro de una skill: la de quien lanza
        if (m.hp != null) {
            SkillMobs.Host self = ctx.caster.entity == null ? null : SkillMobs.host(ctx.caster.entity);
            if (self != null && !SkillMobs.hpOk(self, m.hp)) return;
        }
        // Condiciones en línea («?hasaura{...}», «?!hasaura{...}»): test ya tiene en cuenta el «!» (antes se volvía a
        // negar aquí y todas las «?!» del juego salían al revés: el Asesino, la Bruja, el Caballero de la Muerte...)
        for (Cond c : m.c) {
            if (!SkillConds.test(c, ctx.caster, ctx, null)) return;
        }
        // Cooldown de la propia línea (settarget{cooldown=5}, effect:sound{cooldown=1}...): de cada quién, en segundos
        double lineCd = num(raw.a.getOrDefault("cooldown", raw.a.get("cd")), 0);
        if (lineCd > 0) {
            WhoState st = state(ctx.caster);
            String key = "\u0001línea" + System.identityHashCode(raw);
            Long until = st.cooldowns.get(key);
            if (until != null && until > clock) return;
            st.cooldowns.put(key, clock + Math.round(lineCd * 20));
        }
        // En los jefes, con su tempo (la animación va igual de rápida: los golpes siguen cayendo donde se ven)
        double tempo = tempo(ctx);
        int delay = (int) Math.round(num(m.a.get("delay"), 0) / tempo);
        int repeat = (int) num(m.arg("0", "repeat", "r_"), 0);
        int every = (int) Math.max(1, Math.round(num(m.arg("1", "repeatinterval", "repeati", "ri"), 1) / tempo));
        // Los marcadores (<caster.var.x>, <random...>) se leen cada vez que la línea sale, como MythicMobs: con delay,
        // al salir; con repeat, en cada repetición (1.3.88: el despegue de Ignevar, velocity{y=<caster.var.ramping_current>;
        // repeat=40}, subía con la velocidad que tenía al empezar, 0: nunca volaba, ni se giraba ni caía en picado)
        boolean[] fresh = {delay <= 0};
        Runnable once = () -> {
            if (ctx.caster.actor != null && !ctx.caster.alive() && !m.m.equals("remove")) return;
            Mech cur = fresh[0] || !raw.dyn ? m : SkillVars.resolve(raw, ctx);
            fresh[0] = false;
            Ctx use = ctx;
            // origin=@Objetivo{...}: la línea (y lo que llame) sale desde ahí
            String o = cur.a.get("origin");
            if (o != null && o.trim().startsWith("@")) {
                List<Tgt> r = SkillTargets.fromString(o, ctx);
                if (!r.isEmpty()) {
                    use = ctx.copy();
                    use.depth = ctx.depth;
                    use.origin = r.get(0).pos();
                }
            }
            apply(cur, use, targets(cur, use));
        };
        if (delay <= 0 && repeat <= 0) {
            once.run();
            return;
        }
        // Cada repetición programa la siguiente (antes se programaban todas de golpe: los efectos con repeat=999 dejaban
        // miles de tareas en cola aunque ya no existieran, y al llegar al tope las skills con delay dejaban de salir).
        // Como en MythicMobs, si el efecto que la lanza ya no existe, se acaba.
        int[] left = {Math.min(repeat, MAX_REPEAT)};
        Runnable[] step = new Runnable[1];
        step[0] = () -> {
            if (ctx.caster.actor != null && !ctx.caster.alive() && !m.m.equals("remove")) return;
            once.run();
            if (left[0]-- > 0) later(every, ctx.cast, step[0]);
        };
        later(delay, ctx.cast, step[0]);
    }

    /** Repeticiones como mucho de una línea (repeat=999 de los efectos que duran lo que viven). */
    private static final int MAX_REPEAT = 2400;

    static void apply(Mech m, Ctx ctx, List<Tgt> targets) {
        if (!CALLS.contains(m.m)) applied++;
        switch (m.m) {
            case "skill", "metaskill", "cast", "s" -> callSkill(m, ctx, targets);
            // vskill (VariableSkill): como skill, con marcadores en el nombre (ya resueltos al ejecutar la línea)
            case "vskill", "variableskill", "metavariableskill" -> callSkill(m, ctx, targets);
            case "randomskill" -> randomSkill(m, ctx, targets);
            case "skillsequence" -> {
                for (String n : m.arg("", "skills", "s").split(",")) callNamed(n.trim(), ctx, targets);
            }
            case "sudoskill", "sudo" -> {
                String name = m.arg(null, "s", "skill");
                for (Tgt t : targets) {
                    if (t.who == null) continue;
                    Ctx c = ctx.as(t.who);
                    c.targets = null;
                    runMeta(ctx.cls.meta(name), c);
                }
            }
            case "effect:particles", "particles", "particle", "e:p", "effect:p", "effect:particle" -> SkillFx.particles(m, ctx, targets);
            case "effect:particlering", "particlering", "e:pr", "effect:pr" -> SkillFx.ring(m, ctx, targets);
            case "effect:particlesphere", "particlesphere", "e:ps", "effect:ps" -> SkillFx.sphere(m, ctx, targets);
            case "effect:particleorbital", "particleorbital", "e:po", "effect:po" -> SkillFx.orbital(m, ctx, targets);
            case "effect:particleline", "particleline", "e:pl", "effect:pl" -> SkillFx.line(m, ctx, targets);
            case "effect:particlebox", "particlebox", "effect:pb" -> SkillFx.sphere(m, ctx, targets);
            case "effect:sound", "sound", "e:s", "effect:s" -> SkillFx.sound(m, ctx, targets);
            case "effect:lightning", "lightning" -> SkillFx.lightning(m, ctx, targets);
            case "effect:blockmask", "blockmask", "effect:blockwave", "blockwave" -> SkillFx.blockmask(m, ctx, targets);
            case "summon" -> summon(m, ctx, targets);
            case "remove" -> {
                for (Tgt t : targets) {
                    if (t.who != null && t.who.actor != null) t.who.actor.remove();
                    SkillMobs.Host h = t.who == null || t.who.entity == null ? null : SkillMobs.host(t.who.entity);
                    if (h != null && h.boss() != h) h.mob().discard();
                }
            }
            case "equip" -> equip(m, ctx, targets);
            case "model" -> SkillModels.model(m, ctx, targets);
            case "state", "animation" -> SkillModels.state(m, ctx, targets);
            case "changepart" -> SkillModels.changePart(m, ctx, targets);
            case "partvisibility", "partvis" -> SkillModels.partVis(m, ctx, targets);
            case "tint" -> SkillModels.tint(m, ctx, targets);
            case "projectile", "p" -> SkillMovers.projectile(m, ctx, targets, false);
            case "missile" -> SkillMovers.projectile(m, ctx, targets, true);
            case "totem" -> SkillMovers.totem(m, ctx, targets);
            case "orbital" -> SkillMovers.orbital(m, ctx, targets);
            case "shoot", "shootfireball" -> SkillMovers.shoot(m, ctx, targets);
            case "aura", "buff", "debuff" -> SkillAuras.aura(m, ctx, targets);
            case "ondamaged", "onattack", "onshoot" -> SkillAuras.aura(m, ctx, targets);
            case "command", "consolecommand", "cmd" -> command(m, ctx, targets);
            case "auraremove", "removeaura", "removebuff" -> SkillAuras.remove(m, targets);
            case "damage", "d" -> damage(m, ctx, targets, false);
            case "basedamage" -> damage(m, ctx, targets, true);
            case "hit" -> {
                // hit{a=multiplicador}: un golpe con el daño de ataque de quien lanza (como basedamage)
                Mech b = m.copyWith(new HashMap<>(m.a), m.ta);
                b.a.put("multiplier", m.arg("1", "a", "amount", "multiplier", "m"));
                damage(b, ctx, targets, true);
            }
            case "percentdamage", "damagepercent" -> percentDamage(m, ctx, targets);
            case "heal" -> heal(m, targets, false, ctx.host != null && ctx.caster.entity != null ? ctx.host.healthScale() : 1.0);
            case "healpercent" -> heal(m, targets, true, 1.0);
            case "potion" -> potion(m, ctx, targets);
            case "potionclear" -> {
                for (Tgt t : targets) if (t.living() != null) t.living().removeAllEffects();
            }
            case "ignite" -> {
                int ticks = (int) num(m.arg("60", "ticks", "t", "duration", "d"), 60);
                for (Tgt t : targets) if (t.entity() != null && canHurt(ctx, t.entity())) t.entity().setRemainingFireTicks(ticks);
            }
            case "extinguish" -> {
                for (Tgt t : targets) if (t.entity() != null) t.entity().clearFire();
            }
            case "throw" -> SkillMotion.throwAway(m, ctx, targets);
            case "pull" -> SkillMotion.pull(m, ctx, targets);
            case "leap" -> SkillMotion.leap(m, ctx, targets);
            case "lunge" -> SkillMotion.lunge(m, ctx, targets);
            case "jump" -> SkillMotion.jump(m, ctx, targets);
            case "velocity" -> SkillMotion.velocity(m, ctx, targets);
            case "propel" -> SkillMotion.propel(m, ctx, targets);
            case "teleport", "tp", "teleportto", "tpt" -> SkillMotion.teleport(m, ctx, targets);
            case "stun", "prison" -> SkillMotion.stun(m, ctx, targets);
            case "freeze" -> SkillMotion.freeze(m, ctx, targets);
            case "mount", "mounttarget", "dismount" -> { /* los actores no se montan */ }
            case "setblock", "setblocktype" -> setBlock(m, ctx, targets);
            case "setvariable", "setvar", "variableset" -> setVariable(m, ctx, targets);
            case "variableadd", "varadd" -> addVariable(m, ctx, targets);
            case "variableunset", "unsetvariable", "unsetvar" -> unsetVariable(m, ctx, targets);
            case "message", "msg", "actionmessage" -> message(m, targets);
            case "variablesubtract", "varsubtract" -> {
                Mech neg = m.copyWith(new HashMap<>(m.a), m.ta);
                neg.a.put("amount", fmt(-num(unquote(m.arg("1", "amount", "a", "value", "val")), 1)));
                addVariable(neg, ctx, targets);
            }
            case "setvarloc", "setvariablelocation" -> setVarLoc(m, ctx, targets);
            // Estado de cada quién
            case "addtag", "tagadd" -> {
                String tag = m.arg(null, "tag", "t");
                for (Tgt t : targets) if (tag != null && t.who != null) state(t.who).tags.add(tag.toLowerCase(Locale.ROOT));
            }
            case "removetag", "tagremove" -> {
                String tag = m.arg(null, "tag", "t");
                for (Tgt t : targets) if (tag != null && t.who != null) state(t.who).tags.remove(tag.toLowerCase(Locale.ROOT));
            }
            case "setstance" -> {
                String st = m.arg(null, "stance", "s");
                for (Tgt t : targets) if (t.who != null) state(t.who).stance = st;
            }
            case "setfaction" -> {
                String f = m.arg(null, "faction", "f");
                for (Tgt t : targets) if (t.who != null) state(t.who).faction = f;
            }
            case "gcd", "setgcd", "globalcooldown" -> {
                // El gcd es de quien lanza (en MythicMobs no lleva objetivo): «gcd{ticks=60}» dentro de un ataque lanzado
                // con @target se ponía al jugador y el jefe encadenaba ataques encima unos de otros
                // Sin dividir por el ritmo de la dificultad: el gcd es lo que dura el ataque (su animación); acortarlo hacía
                // que el siguiente cortara al anterior a medias. El ritmo acorta los cooldowns y lo que piensa el cerebro
                int ticks = (int) Math.round(num(m.arg("20", "ticks", "t", "duration", "d", "_"), 20) / tempo(ctx));
                state(ctx.caster).gcdUntil = clock + ticks;
            }
            case "setskillcooldown", "setskillcd" -> {
                Meta other = ctx.cls.meta(m.arg(null, "skill", "s"));
                double sec = num(m.arg("0", "seconds", "s2", "ticks"), 0) / rhythm(ctx);
                for (Tgt t : targets) {
                    if (other == null || t.who == null) continue;
                    if (sec <= 0) state(t.who).cooldowns.remove(other.name);
                    else state(t.who).cooldowns.put(other.name, clock + Math.round(sec * 20));
                }
            }
            case "setname" -> {
                // A un jugador nunca se le cambia el nombre: «setname{name=<target.name>} @owner» desde un esbirro es
                // para que el esbirro se llame como su dueño (sus skills buscan el aura «<caster.name>TARGET»)
                String raw = m.arg("", "name", "n");
                for (Tgt t : targets) {
                    String name = unquote(raw.indexOf('<') >= 0 ? SkillVars.text(raw, ctx, t) : raw);
                    if (t.who == null || name.isBlank()) continue;
                    if (t.who.actor != null) t.who.actor.customName = name;
                    else if (ctx.caster.actor != null) ctx.caster.actor.customName = name;
                }
            }
            case "cancelskill" -> throw new CancelSkill();
            case "signal" -> {
                String sig = m.arg(null, "signal", "s");
                for (Tgt t : targets) {
                    if (sig == null || t.who == null) continue;
                    state(t.who).lastSignal = sig;
                    if (t.who.actor != null) t.who.actor.signal(sig, ctx.caster);
                    SkillMobs.Host h = t.who.entity == null ? null : SkillMobs.host(t.who.entity);
                    if (h != null) SkillMobs.signal(h, sig, ctx.caster);
                }
            }
            case "settarget" -> {
                if (ctx.caster.actor != null) {
                    LivingEntity found = null;
                    if (m.t != null) {
                        // Entre los vivos que cumplen (el «limit=1» de @EIR cogería a veces un efecto, que no vale)
                        Mech all = m.copyWith(m.a, new HashMap<>(m.ta));
                        all.ta.remove("limit");
                        List<LivingEntity> alive = new ArrayList<>();
                        for (Tgt t : SkillTargets.resolve(all, ctx)) {
                            if (t.living() != null && canHurt(ctx, t.living())) alive.add(t.living());
                        }
                        if (!alive.isEmpty()) {
                            boolean random = m.targetArg("", "sort").equalsIgnoreCase("RANDOM");
                            found = alive.get(random ? ThreadLocalRandom.current().nextInt(alive.size()) : 0);
                        }
                    }
                    ctx.caster.actor.target = found;
                }
            }
            case "setai", "setnoai" -> {
                boolean ai = m.m.equals("setai") ? bool(m.arg("true", "ai", "a")) : !bool(m.arg("true", "ai", "a"));
                for (Tgt t : targets) {
                    if (t.who != null && t.who.actor != null) t.who.actor.ai = ai;
                    SkillMobs.Host h = t.who == null || t.who.entity == null ? null : SkillMobs.host(t.who.entity);
                    if (h != null) h.setAi(ai);
                }
            }
            case "setspeed" -> {
                double sp = num(m.arg("1", "speed", "s", "amount", "a"), 1);
                for (Tgt t : targets) {
                    if (t.who != null && t.who.actor != null) t.who.actor.speedMul = sp;
                    SkillMobs.Host h = t.who == null || t.who.entity == null ? null : SkillMobs.host(t.who.entity);
                    if (h != null) h.setSpeedMul(sp);
                }
            }
            case "settextdisplay" -> {
                String text = unquote(m.arg("", "text", "t"));
                for (Tgt t : targets) if (t.who != null && t.who.actor != null) t.who.actor.setText(text);
            }
            case "setmodelscale" -> SkillModels.scale(m, ctx, targets);
            case "mountmodel" -> {
                SkillMobs.Host h = ctx.caster.entity == null ? null : SkillMobs.host(ctx.caster.entity);
                if (h != null && SkillMobs.world != null) {
                    // Agarre de un jefe (mountmodel{p=seat} desde su tótem): el objetivo va al asiento de su modelo
                    String seat = m.arg("seat", "p", "pbone", "seat", "part");
                    for (Tgt t : targets) if (t.living() != null && canHurt(ctx, t.living())) SkillMobs.world.seat(h, seat, t.living());
                } else {
                    SkillModels.mount(m, ctx, targets);
                }
            }
            case "dismountmodel", "dismountall", "dismountallmodel" -> {
                boolean done = false;
                for (Tgt t : targets) {
                    SkillMobs.Host h = t.who == null || t.who.entity == null ? null : SkillMobs.host(t.who.entity);
                    if (h != null && SkillMobs.world != null) {
                        SkillMobs.world.unseat(h, m.arg(null, "p", "pbone", "seat", "part"));
                        done = true;
                    }
                }
                if (!done) SkillModels.dismount(ctx, targets);
            }
            case "setnodamageticks" -> {
                int ticks = (int) num(m.arg("0", "ticks", "t", "_"), 0);
                for (Tgt t : targets) if (t.living() != null) setImmunity(t.living(), ticks, ctx.cast);
            }
            case "directionalvelocity" -> SkillMotion.directional(m, ctx, targets);
            case "spin", "effect:spin" -> {
                // spin{velocity=grados por tick;duration=ticks (0 = siempre)}: el efecto gira sobre sí mismo; un mob, como
                // el aura de MythicMobs (SkillMore.spinEntity)
                float v = (float) num(m.arg("10", "velocity", "v"), 10);
                int d = (int) num(m.arg("0", "duration", "d"), 0);
                for (Tgt t : targets) {
                    if (t.who == null) continue;
                    if (t.who.actor == null) {
                        SkillMore.spinEntity(m, ctx, t);
                        continue;
                    }
                    t.who.actor.spin = v;
                    t.who.actor.spinUntil = d > 0 ? t.who.actor.age + d : Integer.MAX_VALUE;
                }
            }
            case "recoil" -> {
                if (ctx.host != null) SkillFx.shake(m, ctx, targets);
                else SkillMotion.recoil(m, ctx, targets);
            }
            case "chain" -> SkillMotion.chain(m, ctx, targets);
            case "hide" -> SkillFx.hide(m, ctx, targets, true);
            case "showentity", "show" -> SkillFx.hide(m, ctx, targets, false);
            case "sendtitle", "title" -> SkillFx.title(m, ctx, targets);
            case "slash" -> SkillFx.slash(m, ctx, targets);
            case "polygon" -> SkillFx.polygon(m, ctx, targets);
            case "fakelightning" -> SkillFx.lightning(m, ctx, targets);
            case "modifyprojectile" -> SkillMovers.modify(m, ctx);
            case "endprojectile", "terminateprojectile" -> SkillMovers.endCurrent(ctx);
            case "setprojectiledirection" -> SkillMovers.setDirection(ctx, targets);
            case "onswing", "ondeath" -> SkillAuras.aura(m, ctx, targets);
            case "bodyrotation", "bodyclamp" -> {
                for (Tgt t : targets) {
                    SkillMobs.Host h = t.who == null || t.who.entity == null ? null : SkillMobs.host(t.who.entity);
                    if (h != null) h.setBodyRotation((float) num(m.arg("30", "maxheadangle", "mh"), 30),
                            (float) num(m.arg("30", "maxbodyangle", "mb"), 30), (int) num(m.arg("0", "rotationdelay", "rde"), 0),
                            (int) num(m.arg("0", "rotationduration", "rdu"), 0));
                }
            }
            case "lockmodel" -> {
                boolean lock = bool(m.arg("true", "lock", "l"));
                for (Tgt t : targets) {
                    SkillMobs.Host h = t.who == null || t.who.entity == null ? null : SkillMobs.host(t.who.entity);
                    if (h != null) h.setLocked(lock);
                    else if (t.who != null && t.who.actor != null) t.who.actor.locked = lock;
                }
            }
            case "setrotation" -> {
                boolean rel = bool(m.arg("false", "relative", "r"));
                float yaw = (float) num(m.arg("0", "yaw", "y"), 0);
                for (Tgt t : targets) {
                    SkillMobs.Host h = t.who == null || t.who.entity == null ? null : SkillMobs.host(t.who.entity);
                    if (h != null) h.turn(yaw, rel);
                    else if (t.who != null && t.who.actor != null) {
                        SkillActor a = t.who.actor;
                        a.moveTo(a.pos, rel ? a.yaw + yaw : yaw, a.pitch);
                    }
                }
            }
            case "brightness" -> SkillModels.brightness(m, ctx, targets);
            case "defaultstate" -> SkillModels.defaultState(m, ctx, targets);
            case "remapmodel" -> SkillModels.remap(m, ctx, targets);
            case "displaytransformation" -> SkillModels.displayTransform(m, ctx, targets);
            case "barcreate", "createbar", "barset", "setbar" -> {
                for (Tgt t : targets) {
                    SkillMobs.Host h = t.who == null || t.who.entity == null ? null : SkillMobs.host(t.who.entity);
                    if (h == null) continue;
                    h.barStyle(m.arg(null, "color", "c"), m.arg(null, "style", "s"));
                    if (m.m.equals("barcreate") || m.m.equals("createbar")) h.barVisible(true);
                }
            }
            case "barremove", "removebar" -> {
                for (Tgt t : targets) {
                    SkillMobs.Host h = t.who == null || t.who.entity == null ? null : SkillMobs.host(t.who.entity);
                    if (h != null) h.barVisible(false);
                }
            }
            // MythicMobs que faltaba (JEFES.md §11.3): ver SkillMore
            case "consume" -> SkillMore.consume(m, ctx, targets);
            case "oxygen" -> SkillMore.oxygen(m, targets);
            case "shieldbreak", "disableshield" -> SkillMore.shieldBreak(m, targets);
            case "blackscreen", "effect:blackscreen" -> SkillMore.blackScreen(m, targets);
            case "bloodyscreen", "effect:bloodyscreen", "redscreen", "effect:redscreen" -> SkillMore.bloodyScreen(m, targets);
            case "sendtoast", "toastmessage", "toastmsg", "advmessage", "advancementmessage" -> SkillMore.sendToast(m, ctx, targets);
            case "skybox", "effect:skybox" -> SkillMore.skybox(m, targets);
            case "bossborder", "effect:bossborder" -> SkillMore.bossBorder(m, ctx, targets);
            case "enderbeam", "effect:enderbeam" -> SkillMore.enderBeam(m, ctx, targets);
            case "hologram", "summonhologram", "holo" -> SkillMore.hologram(m, ctx, targets);
            case "blockunmask", "effect:blockunmask" -> SkillMore.blockUnmask(m, ctx, targets);
            case "summonareaeffectcloud", "summoncloud" -> SkillMore.areaEffectCloud(m, ctx, targets);
            case "setgliding" -> SkillMore.setGliding(m, targets);
            case "setpose" -> SkillMore.setPose(m, targets);
            case "setmobcolor", "setcolor" -> SkillMore.setMobColor(m, targets);
            case "setpathfindingmalus", "setmalus", "malus" -> SkillMore.setPathfindingMalus(m, targets);
            case "swap", "tpswap" -> SkillMore.swap(ctx, targets);
            case "teleportin", "tpin", "tpdir", "tpi" -> SkillMore.teleportIn(m, ctx, targets);
            case "resetai", "resetaigoals" -> SkillMore.resetAi(ctx);
            case "randommessage", "randommsg", "rm", "rmsg" -> SkillMore.randomMessage(m, targets);
            case "variablemath", "mathvariable", "varmath", "mathvar" -> SkillMore.variableMath(m, ctx, targets);
            case "switch" -> SkillMore.switchCases(m, ctx, targets);
            case "chainmissile", "cmi" -> SkillMovers.chainMissile(m, ctx, targets);
            // 1.3.82 (packs nuevos)
            case "terminable", "stoppable", "cancelable", "exit", "terminatable" -> SkillAuras.terminable(m, ctx, targets);
            case "teleporty", "tpy" -> SkillMore.teleportY(m, ctx);
            case "matchrotation", "matchrot" -> SkillMore.matchRotation(m, ctx, targets);
            case "tracklocation", "settrackedlocation", "stl" -> SkillMore.trackLocation(ctx, targets);
            case "setdisplayentityitem" -> SkillMore.setDisplayItem(m, targets);
            case "speak", "speech" -> SkillMore.speak(m, ctx, targets);
            case "raytraceto" -> SkillMore.rayTraceTo(m, ctx, targets);
            case "playblockstepsound", "blockstepsound" -> SkillMore.blockStepSound(m, ctx, targets);
            case "weather" -> SkillMore.weather(m, ctx);
            case "setmobscore" -> SkillMore.score(m, ctx, targets, true, false);
            case "settargetscore" -> SkillMore.score(m, ctx, targets, false, false);
            case "modifytargetscore" -> SkillMore.score(m, ctx, targets, false, true);
            case "modifymobscore" -> SkillMore.score(m, ctx, List.of(), true, true);
            // MythicMobs dice de ella que aún no funciona («not yet functional... not intended to be used»)
            case "particleequation", "effect:particleequation", "peq", "effect:peq" -> { /* sin efecto, como en MythicMobs */ }
            case "sethealth" -> {
                double hp = num(m.arg("0", "amount", "a", "health", "h"), 0);
                for (Tgt t : targets) {
                    if (t.who != null && t.who.actor != null && hp <= 0) t.who.actor.remove();
                    else if (t.living() != null) {
                        if (hp <= 0) t.living().kill();
                        else t.living().setHealth((float) hp);
                    }
                }
            }
            // Los jefes no sueltan objetos (orden del dueño, 1.3.83): lo que se gana es lo de la grieta (STUB_OK de audit.py)
            case "dropitem" -> { /* sin efecto */ }
            case "raytrace" -> {
                // raytrace{entitySkill=X} @sitio: X con lo que haya en ese sitio (la sangre de la Hidra al recibir un
                // flechazo): aquí, X en el sitio
                String skill = m.arg(null, "entityskill", "es", "onhit");
                if (skill != null) callNamed(skill, ctx, targets);
            }
            case "look", "lock", "rotatetowards" -> {
                boolean now = bool(m.arg("false", "immediately", "i", "force", "f"));
                SkillMobs.Host h = ctx.caster.entity == null ? null : SkillMobs.host(ctx.caster.entity);
                if (h != null) {
                    if (!targets.isEmpty()) h.face(targets.get(0).pos(), now);
                } else {
                    SkillMotion.look(m, ctx, targets);
                }
            }
            // El golpe que disparó ~onDamaged (aún sin entrar): anularlo o cambiarlo. Fuera de un ~onDamaged no hay
            // evento que cambiar (~onAttack y ~onInteract de los packs, o una skill lanzada por tiempo): nada, como en
            // MythicMobs
            case "cancelevent" -> {
                if (ctx.hit != null) ctx.hit.cancelled = true;
            }
            case "modifydamage" -> modifyDamage(m, ctx);
            case "threat", "threatchange", "threatmod" -> {
                SkillMobs.Host h = ctx.caster.entity == null ? null : SkillMobs.host(ctx.caster.entity);
                String mode = m.arg("ADD", "mode", "m").toUpperCase(Locale.ROOT);
                double amount = num(m.arg("0", "amount", "a"), 0);
                if (h != null) for (Tgt t : targets) if (t.living() != null) h.threat(t.living(), mode, amount);
            }
            case "clearthreat" -> {
                SkillMobs.Host h = ctx.caster.entity == null ? null : SkillMobs.host(ctx.caster.entity);
                if (h != null) h.clearThreat();
            }
            case "rally" -> rally(m, ctx, targets);
            case "setinvulnerable", "invulnerable" -> {
                boolean on = bool(m.arg("true", "value", "v", "invulnerable", "i"));
                for (Tgt t : targets) {
                    SkillMobs.Host h = t.who == null || t.who.entity == null ? null : SkillMobs.host(t.who.entity);
                    if (h != null) h.setPackInvulnerable(on);
                }
            }
            case "glow" -> {
                int ticks = (int) num(m.arg("0", "duration", "d", "ticks", "t"), 0);
                for (Tgt t : targets) {
                    Entity e = t.who == null ? null : t.who.entity;
                    if (e == null || e instanceof Player) continue;
                    e.setGlowingTag(true);
                    if (ticks > 0) later(ticks, ctx.cast, () -> e.setGlowingTag(false));
                }
            }
            case "setgravity" -> {
                boolean g = bool(m.arg("true", "gravity", "g", "value", "v"));
                for (Tgt t : targets) if (t.who != null && t.who.entity != null && !(t.who.entity instanceof Player)) t.who.entity.setNoGravity(!g);
            }
            case "goto" -> {
                SkillMobs.Host h = ctx.caster.entity == null ? null : SkillMobs.host(ctx.caster.entity);
                double sp = num(m.arg("1", "speedmodifier", "sm", "speed", "s"), 1);
                if (h != null && !targets.isEmpty()) h.goTo(targets.get(0).pos(), sp);
            }
            case "suicide" -> {
                SkillMobs.Host h = ctx.caster.entity == null ? null : SkillMobs.host(ctx.caster.entity);
                if (h != null) h.suicide();
                else if (ctx.caster.actor != null) ctx.caster.actor.remove();
            }
            case "setmaxhealth" -> {
                // En los jefes, la cifra va en la escala del pack (como sus otras cifras de vida)
                String mode = m.arg("SET", "mode", "m").toUpperCase(Locale.ROOT);
                double amount = num(m.arg("20", "amount", "a"), 20);
                boolean set = bool(m.arg("false", "set", "s"));
                for (Tgt t : targets) {
                    SkillMobs.Host h = t.who == null || t.who.entity == null ? null : SkillMobs.host(t.who.entity);
                    if (h == null) continue;
                    double max = h.mob().getMaxHealth();
                    double v = amount * h.healthScale();
                    max = switch (mode) {
                        case "ADD" -> max + v;
                        case "MULTIPLY" -> max * amount;
                        default -> v;
                    };
                    h.setMaxHealth(Math.max(1, max), set);
                }
            }
            case "shield", "absorbshield", "shieldpercent" -> {
                // Corazones de absorción (como los de la manzana dorada) encima de la vida: los golpes se los comen antes
                boolean pct = m.m.equals("shieldpercent");
                double amount = num(m.arg(pct ? "0.1" : "1", "amount", "a", "multiplier", "m", "percent", "p"), pct ? 0.1 : 1);
                double cap = num(m.arg("0", "maxshield", "ms", "max"), 0);
                for (Tgt t : targets) {
                    LivingEntity le = t.living();
                    if (le == null) continue;
                    SkillMobs.Host h = SkillMobs.host(le);
                    double scale = h != null && ctx.host != null ? h.healthScale() : 1.0;
                    double add = pct ? le.getMaxHealth() * amount : amount * scale;
                    double max = cap > 0 ? (pct ? le.getMaxHealth() * cap : cap * scale) : Double.MAX_VALUE;
                    le.setAbsorptionAmount((float) Math.min(max, le.getAbsorptionAmount() + add));
                }
            }
            case "explosion", "explode" -> explosion(m, ctx, targets);
            case "effect:particletornado", "particletornado", "effect:tornado", "tornado" -> SkillFx.tornado(m, ctx, targets);
            case "effect:particlelinehelix", "particlelinehelix", "e:plh" -> SkillFx.lineHelix(m, ctx, targets);
            case "effect:particlelinering", "particlelinering", "e:plr" -> SkillFx.lineRing(m, ctx, targets);
            case "effect:atom", "atom" -> SkillFx.atom(m, ctx, targets);
            case "effect:smoke", "smoke", "effect:smokeswirl", "smokeswirl" -> SkillFx.simple("smoke", m, ctx, targets);
            case "effect:flames", "flames" -> SkillFx.simple("flames", m, ctx, targets);
            case "effect:ender", "ender" -> SkillFx.simple("ender", m, ctx, targets);
            case "effect:geyser", "geyser" -> SkillFx.simple("geyser", m, ctx, targets);
            case "effect:spring", "spring" -> SkillFx.simple("spring", m, ctx, targets);
            case "effect:itemspray", "itemspray" -> SkillFx.simple("itemspray", m, ctx, targets);
            case "effect:firework", "firework", "fireworks" -> SkillFx.simple("firework", m, ctx, targets);
            case "effect:stopsound", "stopsound" -> SkillFx.stopSound(m, ctx, targets);
            case "summonfallingblock", "sfb" -> fallingBlock(m, ctx, targets);
            case "shootpotion" -> shootPotion(m, ctx, targets);
            case "shootshulkerbullet", "shootshulker" -> shulkerBullet(m, ctx, targets);
            case "fakeexplosion", "effect:explosion", "e:explosion", "effect:explode", "e:explode" -> SkillFx.explosionFx(ctx, targets);
            case "forcepull" -> {
                // Trae a los objetivos junto a quien lanza (con algo de dispersión)
                double sp = num(m.arg("0", "spread", "s"), 0);
                double vs = num(m.arg("0", "vspread", "vs"), 0);
                Vec3 c = ctx.caster.pos();
                for (Tgt t : targets) {
                    LivingEntity le = t.living();
                    if (le == null || le == ctx.caster.entity) continue;
                    ThreadLocalRandom r = ThreadLocalRandom.current();
                    le.teleportTo(c.x + (r.nextDouble() * 2 - 1) * sp, c.y + r.nextDouble() * vs, c.z + (r.nextDouble() * 2 - 1) * sp);
                }
            }
            case "disengage" -> {
                // Salto hacia atrás, lejos de su objetivo
                Entity e = ctx.caster.entity;
                if (e != null) {
                    double v = num(m.arg("1", "velocity", "v"), 1);
                    double vy = num(m.arg("0.5", "velocityy", "vy"), 0.5);
                    Vec3 from = !targets.isEmpty() ? targets.get(0).pos() : ctx.aim != null ? ctx.aim.position() : null;
                    Vec3 away = from == null ? ctx.caster.forward().scale(-1) : e.position().subtract(from);
                    away = new Vec3(away.x, 0, away.z);
                    if (away.lengthSqr() < 1e-6) away = ctx.caster.forward().scale(-1);
                    away = away.normalize().scale(v);
                    e.setDeltaMovement(away.x, vy, away.z);
                    e.hurtMarked = true;
                }
            }
            // Sin efecto aquí, a propósito (tools/bosses/audit.py las conoce y dice cuándo importa):
            //  - del cuerpo de Minecraft que los jefes no usan (los soportes de armadura, el brazo, el hambre, los
            //    selectores de IA de Bukkit: su IA es la de BossEntity y su cerebro);
            //  - de ModelEngine que aquí ya se hace de otra forma (bindhitbox: las cajas ob_; renderinit, cullconfig) o
            //    aún no (segment: capas y colas que se doblan como una cuerda; enchant: el brillo de encantado);
            //  - marcadores globales de MythicMobs (no hay condición que los lea).
            case "segment", "seg" -> SkillModels.segment(m, ctx, targets);
            // ModelEngine 4: lo que se ve del modelo (lo hace el cliente; ver SkillMeg)
            case "modifystate", "modifyanimation", "modstate" -> SkillMeg.modifyState(m, ctx, targets);
            case "statetoggle", "togglestate" -> SkillMeg.stateToggle(m, ctx, targets);
            case "headconfig" -> SkillMeg.headConfig(m, ctx, targets);
            case "lockmodelhead", "lockhead" -> SkillMeg.lockHead(m, ctx, targets);
            case "headtarget" -> SkillMeg.headTarget(m, ctx, targets);
            case "jiggleimpulse" -> SkillMeg.jiggleImpulse(m, ctx, targets);
            case "hitboxconfig" -> SkillMeg.hitboxConfig(m, ctx, targets);
            case "setitemmodel" -> SkillMeg.setItemModel(m, ctx, targets);
            case "linkitembone" -> SkillMeg.linkItemBone(m, ctx, targets);
            case "setmodeltag" -> SkillMeg.setModelTag(m, ctx, targets);
            case "setmodeltagvisible" -> SkillMeg.setModelTagVisible(m, ctx, targets);
            case "enchant" -> SkillMeg.enchant(m, ctx, targets);
            case "billboard" -> SkillMeg.billboard(m, ctx, targets);
            case "glowbone" -> SkillMeg.glowBone(m, ctx, targets);
            case "cycleparts" -> SkillMeg.cycleParts(m, ctx, targets);
            case "modelplayerskin", "modelskin" -> SkillMeg.playerSkin(m, ctx, targets);
            case "vfx" -> SkillMeg.vfx(m, ctx, targets);
            case "vfxpos" -> SkillMeg.vfxTransform("pos", m, ctx, targets, false);
            case "vfxposreset" -> SkillMeg.vfxTransform("pos", m, ctx, targets, true);
            case "vfxrot" -> SkillMeg.vfxTransform("rot", m, ctx, targets, false);
            case "vfxrotreset" -> SkillMeg.vfxTransform("rot", m, ctx, targets, true);
            case "vfxscale", "vfxscl", "vfxsca" -> SkillMeg.vfxTransform("scale", m, ctx, targets, false);
            case "vfxscalereset" -> SkillMeg.vfxTransform("scale", m, ctx, targets, true);
            case "vfxchangemodel" -> SkillMeg.vfxChangeModel(m, ctx, targets);
            case "vfxsetvis" -> SkillMeg.vfxSet("vis", m, ctx, targets);
            case "vfxenchant" -> SkillMeg.vfxSet("ench", m, ctx, targets);
            case "vfxtint" -> SkillMeg.vfxSet("tint", m, ctx, targets);
            case "vfxbrightness" -> SkillMeg.vfxSet("bright", m, ctx, targets);
            case "ik", "inversekinematics" -> SkillMeg.ik(m, ctx, targets);
            case "iktarget" -> SkillMeg.ikTarget(m, ctx, targets);
            case "ikchain" -> SkillMeg.ikChain(m, ctx, targets);
            case "leash" -> SkillMeg.leash(m, ctx, targets);
            case "leashself" -> SkillMeg.leashSelf(m, ctx, targets);
            case "animatemodel" -> SkillMeg.animateModel(m, ctx, targets);
            case "rootmotion" -> SkillMeg.rootMotion(m, ctx, targets);
            case "changeparent" -> SkillMeg.changeParent(m, ctx, targets);
            case "submodel" -> SkillMeg.subModel(m, ctx, targets);
            case "swapentity" -> SkillMeg.swapEntity(m, ctx, targets);
            case "petrify" -> SkillMeg.petrify(m, ctx, targets);
            case "pairmodel" -> SkillMeg.pairModel(m, ctx, targets);
            case "modeldisguise" -> SkillMeg.modelDisguise(m, ctx, targets);
            case "modelundisguise" -> SkillMeg.modelUndisguise(m, ctx, targets);
            case "move" -> {
                // move{x;y;z;speed;global} de ModelEngine: este tick anda en esa dirección (con su escalón)
                double side = num(m.arg("0", "side", "x"), 0), up = num(m.arg("0", "up", "y"), 0), front = num(m.arg("0", "front", "z"), 0);
                double sp = num(m.arg("1", "speed"), 1);
                boolean global = bool(m.arg("false", "global", "g"));
                for (Tgt t : targets) {
                    SkillMobs.Host h = t.who == null || t.who.entity == null ? null : SkillMobs.host(t.who.entity);
                    if (h != null) h.move(side, up, front, sp, global);
                }
            }
            case "syncyaw" -> {
                // syncyaw{t=yaw|body|head|all;b=yaw|body|false}: un giro de la entidad pasa a ser otro (false = el de la cabeza)
                String to = m.arg("yaw", "target", "t", "to").toLowerCase(Locale.ROOT);
                String from = m.arg("head", "body", "b", "from", "src").toLowerCase(Locale.ROOT);
                for (Tgt t : targets) {
                    SkillMobs.Host h = t.who == null || t.who.entity == null ? null : SkillMobs.host(t.who.entity);
                    if (h != null) h.syncYaw(to, from.equals("false") ? "head" : from);
                }
            }
            case "scale", "meg_scale" -> SkillModels.scale(m, ctx, targets);
            case "meg_jump" -> SkillMotion.jump(m, ctx, targets);
            case "nofall" -> {
                for (Tgt t : targets) if (t.who != null && t.who.entity != null) t.who.entity.fallDistance = 0F;
            }
            case "runaigoalselector", "aigoal", "aigoals" -> {
                // Siempre al que lanza: un objetivo de IA más al final de los suyos (clear quita los de antes)
                SkillMobs.Host h = ctx.caster.entity == null ? null : SkillMobs.host(ctx.caster.entity);
                String g = m.arg(null, "goal", "aigoalselector", "g", "goalselector", "s", "string", "t", "target");
                if (h != null && g != null) h.addAiGoal(g, false);
            }
            case "runaitargetselector", "aitarget" -> {
                SkillMobs.Host h = ctx.caster.entity == null ? null : SkillMobs.host(ctx.caster.entity);
                String g = m.arg(null, "target", "aitargetselector", "s", "string", "targetselector", "goal", "g");
                if (h != null && g != null) h.addAiGoal(g, true);
            }
            case "bindhitbox" -> SkillMore.bindHitbox(m, ctx);
            case "animatearmorstand", "modifyglobalscore",
                    "setglobalscore", "feed", "playanimation", "swing", "setcollidable", "cullconfig",
                    "posearmorstand", "removehelditem", "renderinit" -> { /* nada */ }
            default -> warn("mecánica " + m.m);
        }
    }

    private static final Set<String> CALLS = Set.of("skill", "metaskill", "cast", "s", "randomskill", "skillsequence", "vskill",
            "variableskill", "metavariableskill");

    /** Opciones de la propia mecánica skill{} (lo demás son parámetros para la skill llamada). */
    private static final Set<String> CALL_KEYS = Set.of("s", "skill", "skills", "$skill", "spell", "delay", "repeat",
            "repeatinterval", "repeati", "ri", "r_", "origin", "cooldown", "sync", "forcesync", "targetisorigin", "tio", "_");

    private static void callSkill(Mech m, Ctx ctx, List<Tgt> targets) {
        String names = m.arg(null, "s", "skill", "skills", "$skill", "spell");
        if (names == null) return;
        Ctx use = ctx;
        Map<String, String> params = null;
        for (Map.Entry<String, String> e : m.a.entrySet()) {
            if (CALL_KEYS.contains(e.getKey())) continue;
            if (params == null) params = new HashMap<>(ctx.params);
            params.put(e.getKey(), unquote(e.getValue()));
        }
        if (params != null) {
            use = ctx.copy();
            use.depth = ctx.depth;
            use.params = params;
        }
        for (String n : names.split(",")) callNamed(n.trim().split(" ")[0], use, targets);
    }

    /** skill{s=X}: ejecuta X con los objetivos de la línea (o los heredados) y su propio tiempo. */
    static void callNamed(String name, Ctx ctx, List<Tgt> targets) {
        Meta meta = ctx.cls.meta(name);
        if (meta == null) return;
        Ctx c = ctx.copy();
        c.targets = targets;
        c.skill = name;
        runMeta(meta, c);
    }

    private static void randomSkill(Mech m, Ctx ctx, List<Tgt> targets) {
        String list = m.arg("", "skills", "s");
        List<String> names = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        double total = 0;
        for (String part : list.split(",")) {
            String[] p = part.trim().split("\\s+");
            if (p[0].isEmpty()) continue;
            double w = p.length > 1 ? Math.max(0, num(p[1], 1)) : 1;
            names.add(p[0]);
            weights.add(w);
            total += w;
        }
        if (names.isEmpty()) return;
        double r = ThreadLocalRandom.current().nextDouble() * total;
        for (int i = 0; i < names.size(); i++) {
            r -= weights.get(i);
            if (r <= 0 || i == names.size() - 1) {
                callNamed(names.get(i), ctx, targets);
                return;
            }
        }
    }

    // ------------------------------------------------------------------------------------------- actores

    /** Ritmo de ataque de la dificultad del jefe (1 = el del pack): los enfriamientos y gcd se dividen por esto. */
    static double rhythm(Ctx ctx) {
        return ctx.host == null ? 1.0 : Math.max(0.25, ctx.host.rhythm());
    }

    private static void summon(Mech m, Ctx ctx, List<Tgt> targets) {
        String type = m.arg(null, "type", "t", "mob", "m");
        MobDef def = ctx.cls.mob(type);
        if (def == null && type != null && ctx.host != null) {
            // Un mob de Minecraft (summon{type=WITHER_SKELETON}): MythicMobs lo saca tal cual, con su IA y su equipo
            net.minecraft.world.entity.EntityType<?> vt = net.minecraft.world.entity.EntityType.byString(
                    "minecraft:" + type.toLowerCase(Locale.ROOT)).orElse(null);
            if (vt != null && vt.getCategory() != net.minecraft.world.entity.MobCategory.MISC) {
                summonVanilla(m, ctx, targets, vt);
                return;
            }
        }
        if (def == null) {
            warn("mob " + type);
            return;
        }
        if (def.entity && SkillMobs.world != null) {
            // Un mob de verdad del jefe (esbirro, brote, retoño): una entidad con su IA y sus skills
            SkillMobs.Host owner = ctx.host;
            if (owner == null) return;
            int amount = (int) Math.max(1, Math.min(16, num(m.arg("1", "amount", "a"), 1)));
            double radius = num(m.arg("0", "radius", "r"), 0);
            for (Tgt t : targets) {
                for (int i = 0; i < amount; i++) {
                    Vec3 at = t.pos();
                    ThreadLocalRandom rnd = ThreadLocalRandom.current();
                    if (radius > 0) at = at.add((rnd.nextDouble() * 2 - 1) * radius, 0, (rnd.nextDouble() * 2 - 1) * radius);
                    at = SkillMinions.ground(ctx.caster.level(), at);
                    SkillMobs.world.spawnMinion(owner, def, ctx.caster.level(), at, ctx.caster.yaw());
                }
            }
            return;
        }
        int amount = (int) Math.max(1, Math.min(32, num(m.arg("1", "amount", "a"), 1)));
        double radius = num(m.arg("0", "radius", "r"), 0);
        double yRadius = num(m.arg("0", "yradius", "yr"), 0);
        boolean upOnly = bool(m.arg("false", "yradiusuponly", "yu"));
        boolean surface = bool(m.arg("false", "onsurface", "os"));
        Meta onSummon = ctx.cls.meta(m.arg(null, "onsummon", "onsummonskill"));
        for (Tgt t : targets) {
            Vec3 base = t.pos();
            for (int i = 0; i < amount; i++) {
                Vec3 at = base;
                ThreadLocalRandom rnd = ThreadLocalRandom.current();
                if (radius > 0) at = at.add((rnd.nextDouble() * 2 - 1) * radius, 0, (rnd.nextDouble() * 2 - 1) * radius);
                if (yRadius > 0) at = at.add(0, upOnly ? rnd.nextDouble() * yRadius : (rnd.nextDouble() * 2 - 1) * yRadius, 0);
                if (surface || def.living) at = SkillMinions.ground(ctx.caster.level(), at);
                // Con @self/@forward del lanzador, el efecto mira a donde mira él (como en MythicMobs)
                float yaw = ctx.caster.yaw();
                float pitch = 0F;
                SkillActor a = spawn(ctx, def, at, yaw, pitch);
                // onSummon: la lanza quien invoca, con lo invocado como objetivo
                if (a != null && onSummon != null) {
                    Ctx c = ctx.copy();
                    c.targets = List.of(Tgt.of(a.who));
                    c.aim = null;
                    runMeta(onSummon, c);
                }
            }
        }
    }

    /**
     * summon de un mob de Minecraft: como el spawnEntity de Bukkit que usa MythicMobs (con finalizeSpawn: el esqueleto
     * con su arco), en el suelo; como mucho 16 de una vez y 24 vivos cerca de quien los saca.
     */
    private static void summonVanilla(Mech m, Ctx ctx, List<Tgt> targets, net.minecraft.world.entity.EntityType<?> vt) {
        ServerLevel level = ctx.caster.level();
        int amount = (int) Math.max(1, Math.min(16, num(m.arg("1", "amount", "a"), 1)));
        double radius = num(m.arg("0", "radius", "r"), 0);
        Vec3 c = ctx.caster.pos();
        int near = level.getEntities(vt, new net.minecraft.world.phys.AABB(c, c).inflate(64), e -> e.isAlive()).size();
        for (Tgt t : targets) {
            for (int i = 0; i < amount && near < 24; i++, near++) {
                Vec3 at = t.pos();
                ThreadLocalRandom rnd = ThreadLocalRandom.current();
                if (radius > 0) at = at.add((rnd.nextDouble() * 2 - 1) * radius, 0, (rnd.nextDouble() * 2 - 1) * radius);
                at = SkillMinions.ground(level, at);
                net.minecraft.world.entity.Entity e = vt.create(level);
                if (e == null) return;
                e.moveTo(at.x, at.y, at.z, ctx.caster.yaw(), 0F);
                if (e instanceof net.minecraft.world.entity.Mob mob) {
                    mob.finalizeSpawn(level, level.getCurrentDifficultyAt(e.blockPosition()),
                            net.minecraft.world.entity.MobSpawnType.MOB_SUMMONED, null, null);
                }
                // No sueltan nada al morir (ni lo que llevan, ni experiencia): BossEvents.onDrops
                e.addTag(SkillMobs.SUMMONED);
                level.addFreshEntity(e);
            }
        }
    }

    /** Saca un actor y le pasa sus mecánicas ~onSpawn; las ~onTimer quedan para cada tick. */
    static SkillActor spawn(Ctx ctx, MobDef def, Vec3 at, float yaw, float pitch) {
        if (ACTORS.size() >= MAX_ACTORS) return null;
        ServerLevel level = ctx.caster.level();
        SkillActor a = new SkillActor(ctx.cls, def, level, at, yaw, pitch, ctx.caster);
        a.host = ctx.host;
        if (net.tierrasfantasticas.tfclient.bosses.BossTest.ON) a.origin = ctx.skill + " en el tick " + clock;
        ACTORS.add(a);
        SkillNet.near(level, at, a.spawnMessage());
        // Un item_display nace enseñando su objeto (DisplayOptions.Item)
        if (def.displayItem != null) a.prop("item", def.displayItem, "1", "display");
        Ctx mine = ctx.as(a.who);
        mine.targets = null;
        List<Mech> onSpawn = new ArrayList<>();
        // Sin disparador es ~onCombat (MythicMobs), que salta al aparecer; un efecto no pega ni recibe golpes, así que
        // para él es lo mismo que ~onSpawn (y ~onSpawnOrLoad, ~onReady: sale una vez y nunca se guarda)
        for (Mech mm : def.mechs) {
            String tr = mm.tr == null ? "oncombat" : mm.tr;
            if (tr.equals("ontimer")) a.timers.add(mm);
            else if (tr.equals("onspawn") || tr.equals("onready") || tr.equals("oncombat") || tr.equals("onspawnorload")) onSpawn.add(mm);
        }
        Script s = new Script(mine, onSpawn);
        if (!s.run()) SCRIPTS.add(s);
        return a;
    }

    /** Un efecto que es solo un modelo de ModelEngine (la bala bulletType=ME de un proyectil): sin mob ni mecánicas. */
    static SkillActor spawnModel(Ctx ctx, String mid, String state, float scale, Vec3 at, float yaw, float pitch) {
        if (ACTORS.size() >= MAX_ACTORS) return null;
        ServerLevel level = ctx.caster.level();
        SkillActor a = new SkillActor(ctx.cls, null, level, at, yaw, pitch, ctx.caster);
        a.host = ctx.host;
        a.me = mid;
        if (scale > 0) a.scale = scale;
        if (state != null && !state.isBlank()) {
            a.anim = state;
            a.animStart = a.age;
        }
        ACTORS.add(a);
        SkillNet.near(level, at, a.spawnMessage());
        return a;
    }

    private static void equip(Mech m, Ctx ctx, List<Tgt> targets) {
        String model = m.a.get("model");
        String item = m.arg("", "item", "i");
        String slot = m.arg("HEAD", "slot").toUpperCase(Locale.ROOT);
        boolean head = slot.equals("HEAD") || slot.equals("4") || slot.equals("HELMET");
        boolean hand = slot.equals("HAND") || slot.equals("0") || slot.equals("MAINHAND");
        for (Tgt t : targets) {
            if (t.who == null) continue;
            if (t.who.actor != null) {
                SkillActor a = t.who.actor;
                if (head) {
                    a.head = model;
                    a.prop("head", model, null, null);
                } else if (hand) {
                    a.hand = model;
                    a.prop("hand", model, null, null);
                }
            } else if (hand && t.who.entity instanceof ServerPlayer p) {
                SkillItems.swapHand(p, ctx.cls, item.split(":")[0]);
            }
        }
    }

    // ------------------------------------------------------------------------------------------- daño y efectos

    /** Si la skill puede hacer daño o efectos malos a esa entidad (nunca a quien la lanza, sus mascotas ni aliados). */
    static boolean canHurt(Ctx ctx, Entity e) {
        if (!(e instanceof LivingEntity le) || !le.isAlive() || le instanceof ArmorStand || le.isSpectator()) return false;
        if (ctx.host != null) {
            // Un jefe y lo suyo: a los jugadores (no en creativo) y a los demás mobs, nunca a los de su grupo
            if (SkillMobs.sameGroup(ctx.host, le)) return false;
            if (le instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
            return true;
        }
        ServerPlayer owner = ctx.caster.player();
        if (owner != null) {
            if (le == owner) return false;
            if (le instanceof OwnableEntity own && owner.getUUID().equals(own.getOwnerUUID())) return false;
            if (le.isAlliedTo(owner)) return false;
            if (le instanceof Player p) {
                if (p.isCreative() || !SkillServer.pvp() || !owner.getServer().isPvpAllowed() || !owner.canHarmPlayer(p)) return false;
            }
        } else if (ctx.caster.entity == le) {
            return false;
        }
        if (le instanceof AbstractVillager) return false;
        if (le instanceof OwnableEntity own && own.getOwnerUUID() != null) return false;
        return true;
    }

    static void damage(Mech m, Ctx ctx, List<Tgt> targets, boolean base) {
        ServerPlayer owner = ctx.caster.player();
        double amount = num(m.arg("1", "amount", "a", "damage"), 1);
        if (base) {
            double attack = owner != null ? owner.getAttributeValue(Attributes.ATTACK_DAMAGE) : 1;
            // Un mob de jefe: su daño de ataque (ya con la dificultad); un efecto suyo, el del jefe
            if (ctx.host != null) {
                LivingEntity src = ctx.caster.entity instanceof LivingEntity l && SkillMobs.host(l) != null ? l : ctx.host.mob();
                attack = src.getAttributeValue(Attributes.ATTACK_DAMAGE);
            }
            amount = attack * num(m.arg("1", "multiplier", "m"), 1);
        } else if (ctx.host != null) {
            amount *= ctx.host.damageMul(); // los daños fijos del pack, con la dificultad
        }
        boolean noKnock = bool(m.arg("false", "preventknockback", "pkb", "pk"));
        boolean ignoreArmor = bool(m.arg("false", "ignorearmor", "ia"));
        boolean noImmunity = bool(m.arg("false", "preventimmunity", "pi", "ignoreinvulnerability", "ii"));
        for (Tgt t : targets) {
            LivingEntity le = t.living();
            if (le == null) continue;
            // damage @self de un mob de jefe: se lo hace a sí mismo (el Esqueleto atacado por la espalda); el número va
            // en la vida del pack
            SkillMobs.Host self = ctx.host != null && le == ctx.caster.entity ? SkillMobs.host(le) : null;
            if (self != null) {
                double raw = num(m.arg("1", "amount", "a", "damage"), 1);
                self.selfDamage(raw * self.healthScale(), bool(m.arg("false", "ignoreresistance", "ir")));
                continue;
            }
            if (!canHurt(ctx, le)) continue;
            double scale = ctx.host != null ? 1.0 : SkillServer.damageScale();
            hurt(ctx, le, amount * ctx.power * t.power * scale, noKnock, ignoreArmor, noImmunity);
        }
    }

    /**
     * modifydamage{amount;action} en un ~onDamaged: cambia el golpe antes de que entre. action (o type): MULTIPLY (por
     * defecto), ADD, SUBTRACT, DIVIDE o SET; las cifras de ADD/SUBTRACT/SET van en la escala del pack, como
     * «damage-amount».
     */
    private static void modifyDamage(Mech m, Ctx ctx) {
        SkillMobs.Hit hit = ctx.hit;
        if (hit == null) return;
        double a = num(m.arg("1", "amount", "a", "damage", "d", "multiplier", "m"), 1);
        String act = m.arg("MULTIPLY", "action", "act", "type", "t", "mode").toUpperCase(Locale.ROOT);
        hit.amount = switch (act) {
            case "ADD" -> hit.amount + a * hit.scale;
            case "SUBTRACT", "SUB" -> hit.amount - a * hit.scale;
            case "DIVIDE" -> a == 0 ? hit.amount : hit.amount / a;
            case "SET" -> a * hit.scale;
            default -> hit.amount * a;
        };
        if (hit.amount < 0) hit.amount = 0;
    }

    /**
     * summonfallingblock{material;velocity;velocityy;spread}: un bloque que cae (lanzado hacia el objetivo con su
     * velocidad). Nunca se queda en el mundo ni suelta objeto (el servidor tiene construcciones y protecciones).
     */
    private static void fallingBlock(Mech m, Ctx ctx, List<Tgt> targets) {
        String mat = m.arg("stone", "material", "m", "block", "b").toLowerCase(Locale.ROOT);
        ResourceLocation rl = ResourceLocation.tryParse(mat.contains(":") ? mat : "minecraft:" + mat);
        Block block = rl == null ? null : ForgeRegistries.BLOCKS.getValue(rl);
        if (block == null || block == Blocks.AIR) block = Blocks.STONE;
        double v = num(m.arg("0", "velocity", "v"), 0), vy = num(m.arg("0", "velocityy", "vy"), 0);
        double spread = num(m.arg("0", "spread", "s"), 0);
        for (Tgt t : targets) {
            Vec3 at = t.pos();
            // (FallingBlockEntity.fall() quitaría el bloque del mundo en ese sitio: se crea vacío y se le pone el bloque por NBT)
            net.minecraft.world.entity.item.FallingBlockEntity fb = EntityTypeHolder.FALLING.create(ctx.caster.level());
            if (fb == null) continue;
            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            fb.setPos(at.x, at.y, at.z);
            fb.saveWithoutId(tag);
            tag.put("BlockState", net.minecraft.nbt.NbtUtils.writeBlockState(block.defaultBlockState()));
            tag.putBoolean("DropItem", false);
            tag.putBoolean("CancelDrop", true);
            tag.putInt("Time", 1);
            fb.load(tag);
            fb.disableDrop();
            fb.dropItem = false;
            Vec3 dir = at.subtract(ctx.caster.pos()).multiply(1, 0, 1);
            dir = dir.lengthSqr() > 1e-6 ? dir.normalize().scale(v) : Vec3.ZERO;
            ThreadLocalRandom r = ThreadLocalRandom.current();
            fb.setDeltaMovement(dir.x + (r.nextDouble() - 0.5) * spread, vy, dir.z + (r.nextDouble() - 0.5) * spread);
            ctx.caster.level().addFreshEntity(fb);
            // Por si cae en algo raro: se va solo a los 5 s
            later(100, () -> {
                if (fb.isAlive()) fb.discard();
            });
        }
    }

    private static final class EntityTypeHolder {
        static final net.minecraft.world.entity.EntityType<net.minecraft.world.entity.item.FallingBlockEntity> FALLING =
                net.minecraft.world.entity.EntityType.FALLING_BLOCK;
    }

    /** shootpotion{type;duration;level;velocity}: una poción arrojadiza hacia el objetivo. */
    private static void shootPotion(Mech m, Ctx ctx, List<Tgt> targets) {
        if (!(ctx.caster.entity instanceof LivingEntity shooter)) return;
        MobEffect effect = potionType(m.arg("SLOW", "type", "t"));
        int ticks = (int) num(m.arg("100", "duration", "d"), 100);
        int lvl = (int) num(m.arg("1", "level", "l", "lvl"), 1);
        float vel = (float) num(m.arg("1", "velocity", "v"), 1);
        for (Tgt t : targets) {
            net.minecraft.world.item.ItemStack st = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SPLASH_POTION);
            if (effect != null) {
                net.minecraft.world.item.alchemy.PotionUtils.setCustomEffects(st, List.of(new MobEffectInstance(effect, ticks,
                        Math.max(0, ctx.host != null ? lvl : lvl - 1))));
            }
            net.minecraft.world.entity.projectile.ThrownPotion pot = new net.minecraft.world.entity.projectile.ThrownPotion(ctx.caster.level(), shooter);
            pot.setItem(st);
            Vec3 d = t.pos().add(0, 1, 0).subtract(shooter.getEyePosition());
            pot.shoot(d.x, d.y + d.horizontalDistance() * 0.2, d.z, vel, 2F);
            ctx.caster.level().addFreshEntity(pot);
        }
    }

    /** shootshulkerbullet: la bala de shulker que persigue al objetivo. */
    private static void shulkerBullet(Mech m, Ctx ctx, List<Tgt> targets) {
        if (!(ctx.caster.entity instanceof LivingEntity shooter)) return;
        for (Tgt t : targets) {
            if (!(t.entity() instanceof LivingEntity le) || !canHurt(ctx, le)) continue;
            ctx.caster.level().addFreshEntity(new net.minecraft.world.entity.projectile.ShulkerBullet(ctx.caster.level(), shooter, le,
                    net.minecraft.core.Direction.Axis.Y));
        }
    }

    /** rally{types;radius;overwritetarget}: los de su grupo cerca van a por su objetivo (o por quien diga la línea). */
    private static void rally(Mech m, Ctx ctx, List<Tgt> targets) {
        SkillMobs.Host me = ctx.caster.entity == null ? null : SkillMobs.host(ctx.caster.entity);
        if (me == null) return;
        LivingEntity foe = null;
        for (Tgt t : targets) if (t.living() != null && t.living() != ctx.caster.entity) foe = t.living();
        if (foe == null) foe = me.target();
        if (foe == null || !canHurt(ctx, foe)) return;
        double r = num(m.arg("16", "radius", "r"), 16);
        boolean over = bool(m.arg("false", "overwritetarget", "ot"));
        String types = m.arg("", "types", "type", "t").toLowerCase(Locale.ROOT);
        for (LivingEntity e : ctx.caster.level().getEntitiesOfClass(LivingEntity.class, ctx.caster.entity.getBoundingBox().inflate(r))) {
            SkillMobs.Host h = SkillMobs.host(e);
            if (h == null || h == me || h.boss() != me.boss() || h.dying()) continue;
            if (!types.isEmpty() && !(("," + types.replace(" ", "") + ",").contains("," + h.def().name.toLowerCase(Locale.ROOT) + ","))) continue;
            if (!over && h.target() != null && h.target().isAlive()) continue;
            h.threat(foe, "FORCE", 0);
        }
    }

    /**
     * explosion{yield;fire}: una explosión de verdad para los golpes (yield 4 = una dinamita) que nunca rompe bloques
     * (el servidor tiene construcciones y protecciones) y solo daña a quien el lanzador puede dañar.
     */
    private static void explosion(Mech m, Ctx ctx, List<Tgt> targets) {
        double power = Math.max(0.5, num(m.arg("4", "yield", "y", "power", "p"), 4));
        boolean fire = bool(m.arg("false", "fire", "f"));
        double r = power * 2;
        SkillFx.explosionFx(ctx, targets);
        for (Tgt t : targets) {
            Vec3 c = t.pos();
            for (LivingEntity le : ctx.caster.level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r))) {
                if (!canHurt(ctx, le)) continue;
                double d = le.position().distanceTo(c);
                if (d > r) continue;
                double impact = 1 - d / r;
                // La cuenta de daño de Minecraft (sin tapar con bloques); los jefes, con su dificultad
                double dmg = (impact * impact + impact) / 2 * 7 * r + 1;
                if (ctx.host != null) dmg *= ctx.host.damageMul();
                hurt(ctx, le, dmg, false, false, false);
                Vec3 push = le.position().subtract(c);
                if (push.lengthSqr() > 1e-6) {
                    push = push.normalize().scale(impact);
                    // (1.3.88, orden del dueño) un jefe con «suelo» no sube al jugador con sus explosiones
                    boolean ground = le instanceof net.minecraft.server.level.ServerPlayer && SkillMotion.keepsGround(ctx);
                    le.push(push.x, ground ? 0 : push.y * 0.5 + impact * 0.3, push.z);
                    le.hurtMarked = true;
                }
                if (fire) le.setSecondsOnFire(4);
            }
        }
    }

    private static void percentDamage(Mech m, Ctx ctx, List<Tgt> targets) {
        double pct = num(m.arg("0.1", "percent", "p"), 0.1);
        boolean noImmunity = bool(m.arg("false", "preventimmunity", "pi"));
        // ch=true: del total de vida que le queda (no de la máxima)
        boolean current = bool(m.arg("false", "currenthealth", "current", "c", "ch"));
        for (Tgt t : targets) {
            LivingEntity le = t.living();
            if (le == null) continue;
            double base = current ? le.getHealth() : le.getMaxHealth();
            // Un jefe a sí mismo (damagepercent @self): su propia vida, como damage @self
            SkillMobs.Host self = SkillMobs.host(le);
            if (self != null && le == ctx.caster.entity) {
                self.selfDamage(base * pct, true);
                continue;
            }
            if (!canHurt(ctx, le)) continue;
            double scale = ctx.host != null ? ctx.host.damageMul() : SkillServer.damageScale();
            hurt(ctx, le, base * pct * scale, false, true, noImmunity);
        }
    }

    /** El último golpe que recibió alguien de un uso de skill (cuándo y cuánto): la inmunidad de MythicMobs. */
    private record SkillHit(long at, double amount) {}

    private record HitKey(UUID id, long cast) {}

    private static final Map<HitKey, SkillHit> SKILL_HITS = new HashMap<>();
    private static long castSeq;

    /** Un uso nuevo de una skill (para poder cortarlo luego con cancelCast). */
    static long newCast() {
        return ++castSeq;
    }

    /**
     * Ticks que alguien no recibe otro golpe igual o menor del mismo uso de una skill. Minecraft (y MythicMobs) usan 10,
     * pero entonces los combos que los packs anuncian con varios golpes seguidos (dos tajos a 4 ticks) solo harían el
     * primero; con 4 pegan todos y un rayo o un muro que toca cada tick sigue sin multiplicar su daño (como mucho 5
     * golpes por segundo).
     */
    static final int IMMUNITY = 4;

    /**
     * Cuánto le hace de verdad un golpe de skill. Como en MythicMobs (el daño de Minecraft): durante IMMUNITY ticks tras
     * un golpe, otro igual o menor no hace nada y uno mayor solo hace la diferencia; «preventimmunity» se lo salta (los
     * packs lo ponen donde quieren varios golpes seguidos). Cuenta solo dentro de un mismo uso de la skill (un rayo o un
     * muro que toca cada tick no multiplica su daño), no entre clics distintos ni con los golpes normales del jugador:
     * hacer clic rápido no hace que la skill deje de pegar.
     */
    static double immunity(UUID id, long cast, double amount, boolean noImmunity) {
        return immunity(id, cast, amount, noImmunity, IMMUNITY);
    }

    /** window: ticks de inmunidad (los golpes de los jefes, los 10 de Minecraft: un tótem que dura 7 ticks pega una vez). */
    static double immunity(UUID id, long cast, double amount, boolean noImmunity, int window) {
        HitKey key = new HitKey(id, cast);
        SkillHit last = noImmunity ? null : SKILL_HITS.get(key);
        if (last != null && clock - last.at() < window) {
            if (amount <= last.amount()) return 0;
            SKILL_HITS.put(key, new SkillHit(last.at(), amount));
            return amount - last.amount();
        }
        SKILL_HITS.put(key, new SkillHit(clock, amount));
        return amount;
    }

    /** setnodamageticks{ticks}: la inmunidad de Minecraft (20 = recién golpeado; 10 o menos, ya se le puede golpear). */
    static void setImmunity(LivingEntity le, int ticks, long cast) {
        le.invulnerableTime = Math.max(0, ticks);
        UUID id = le.getUUID();
        if (ticks <= 10) {
            SKILL_HITS.keySet().removeIf(k -> k.id().equals(id));
        } else {
            SkillHit last = SKILL_HITS.get(new HitKey(id, cast));
            SKILL_HITS.put(new HitKey(id, cast), new SkillHit(clock - IMMUNITY + (ticks - 10), last == null ? 0 : last.amount()));
        }
    }

    static void hurt(Ctx ctx, LivingEntity le, double amount, boolean noKnock, boolean ignoreArmor, boolean noImmunity) {
        if (amount <= 0) return;
        amount = immunity(le.getUUID(), ctx.cast, amount, noImmunity, ctx.host != null ? 10 : IMMUNITY);
        if (amount <= 0) return;
        ServerPlayer owner = ctx.caster.player();
        Vec3 motion = le.getDeltaMovement();
        le.invulnerableTime = 0;
        inDamage = true;
        boolean dealt = false;
        try {
            if (ctx.host != null) {
                // Golpe de un jefe: como el de un mob (con su nombre en el mensaje de muerte); sin armadura, magia
                LivingEntity src = ctx.caster.entity instanceof LivingEntity l && SkillMobs.host(l) != null ? l : ctx.host.mob();
                dealt = le.hurt(ignoreArmor ? le.damageSources().indirectMagic(src, src) : le.damageSources().mobAttack(src), (float) amount);
            } else if (owner != null) {
                le.hurt(ignoreArmor ? le.damageSources().indirectMagic(owner, owner) : le.damageSources().playerAttack(owner), (float) amount);
            } else {
                le.hurt(le.damageSources().magic(), (float) amount);
            }
        } finally {
            inDamage = false;
        }
        le.invulnerableTime = 0;
        if (noKnock) {
            le.setDeltaMovement(motion);
            le.hurtMarked = true;
        }
        // El cerebro del jefe aprende qué ataques le entran al jugador (y cuáles esquiva)
        if (ctx.host != null && le instanceof Player) SkillMobs.landed(ctx.host.boss());
        if (net.tierrasfantasticas.tfclient.bosses.BossTest.ON && ctx.host != null && le instanceof ServerPlayer) {
            String what = ctx.skill != null ? ctx.skill : ctx.caster.actor != null ? "mob:" + ctx.caster.actor.name() : "?";
            net.tierrasfantasticas.tfclient.bosses.BossTest.skillHit(what, dealt ? (float) amount : 0F);
        }
        // ~onSkillDamage del mob que lo ha hecho (él o un proyectil suyo; no un efecto invocado, que en MythicMobs es otro mob)
        if (dealt && ctx.caster.entity != null && SkillMobs.host(ctx.caster.entity) != null) SkillMobs.skillDamage(SkillMobs.host(ctx.caster.entity), le);
    }

    /** scale: la vida de los jefes va a la escala de su dificultad (un «heal{a=10}» del pack cura 10 de su vida del pack). */
    private static void heal(Mech m, List<Tgt> targets, boolean percent, double scale) {
        double a = num(m.arg("1", "amount", "a", "multiplier", "m"), 1);
        for (Tgt t : targets) {
            LivingEntity le = t.living();
            if (le == null) continue;
            le.heal((float) (percent ? le.getMaxHealth() * a : a * (SkillMobs.host(le) != null ? scale : 1.0)));
        }
    }

    private static final Map<String, MobEffect> POTIONS = new HashMap<>();

    static MobEffect potionType(String name) {
        if (name == null) return null;
        String n = name.toUpperCase(Locale.ROOT).replace("MINECRAFT:", "");
        if (POTIONS.isEmpty()) {
            POTIONS.put("SLOW", MobEffects.MOVEMENT_SLOWDOWN);
            POTIONS.put("SLOWNESS", MobEffects.MOVEMENT_SLOWDOWN);
            POTIONS.put("SPEED", MobEffects.MOVEMENT_SPEED);
            POTIONS.put("FAST_DIGGING", MobEffects.DIG_SPEED);
            POTIONS.put("HASTE", MobEffects.DIG_SPEED);
            POTIONS.put("SLOW_DIGGING", MobEffects.DIG_SLOWDOWN);
            POTIONS.put("MINING_FATIGUE", MobEffects.DIG_SLOWDOWN);
            POTIONS.put("INCREASE_DAMAGE", MobEffects.DAMAGE_BOOST);
            POTIONS.put("STRENGTH", MobEffects.DAMAGE_BOOST);
            POTIONS.put("HEAL", MobEffects.HEAL);
            POTIONS.put("INSTANT_HEALTH", MobEffects.HEAL);
            POTIONS.put("HARM", MobEffects.HARM);
            POTIONS.put("INSTANT_DAMAGE", MobEffects.HARM);
            POTIONS.put("JUMP", MobEffects.JUMP);
            POTIONS.put("JUMP_BOOST", MobEffects.JUMP);
            POTIONS.put("CONFUSION", MobEffects.CONFUSION);
            POTIONS.put("NAUSEA", MobEffects.CONFUSION);
            POTIONS.put("REGENERATION", MobEffects.REGENERATION);
            POTIONS.put("DAMAGE_RESISTANCE", MobEffects.DAMAGE_RESISTANCE);
            POTIONS.put("RESISTANCE", MobEffects.DAMAGE_RESISTANCE);
            POTIONS.put("FIRE_RESISTANCE", MobEffects.FIRE_RESISTANCE);
            POTIONS.put("WATER_BREATHING", MobEffects.WATER_BREATHING);
            POTIONS.put("INVISIBILITY", MobEffects.INVISIBILITY);
            POTIONS.put("BLINDNESS", MobEffects.BLINDNESS);
            POTIONS.put("NIGHT_VISION", MobEffects.NIGHT_VISION);
            POTIONS.put("HUNGER", MobEffects.HUNGER);
            POTIONS.put("WEAKNESS", MobEffects.WEAKNESS);
            POTIONS.put("POISON", MobEffects.POISON);
            POTIONS.put("WITHER", MobEffects.WITHER);
            POTIONS.put("HEALTH_BOOST", MobEffects.HEALTH_BOOST);
            POTIONS.put("ABSORPTION", MobEffects.ABSORPTION);
            POTIONS.put("SATURATION", MobEffects.SATURATION);
            POTIONS.put("GLOWING", MobEffects.GLOWING);
            POTIONS.put("LEVITATION", MobEffects.LEVITATION);
            POTIONS.put("LUCK", MobEffects.LUCK);
            POTIONS.put("UNLUCK", MobEffects.UNLUCK);
            POTIONS.put("SLOW_FALLING", MobEffects.SLOW_FALLING);
            POTIONS.put("CONDUIT_POWER", MobEffects.CONDUIT_POWER);
            POTIONS.put("DOLPHINS_GRACE", MobEffects.DOLPHINS_GRACE);
            POTIONS.put("DARKNESS", MobEffects.DARKNESS);
        }
        MobEffect e = POTIONS.get(n);
        if (e == null) {
            ResourceLocation rl = ResourceLocation.tryParse(name.toLowerCase(Locale.ROOT));
            e = rl == null ? null : ForgeRegistries.MOB_EFFECTS.getValue(rl);
        }
        return e;
    }

    private static boolean harmful(MobEffect e) {
        return e == MobEffects.MOVEMENT_SLOWDOWN || e == MobEffects.DIG_SLOWDOWN || e == MobEffects.HARM
                || e == MobEffects.CONFUSION || e == MobEffects.BLINDNESS || e == MobEffects.HUNGER || e == MobEffects.WEAKNESS
                || e == MobEffects.POISON || e == MobEffects.WITHER || e == MobEffects.LEVITATION || e == MobEffects.UNLUCK
                || e == MobEffects.DARKNESS || e == MobEffects.GLOWING;
    }

    private static void potion(Mech m, Ctx ctx, List<Tgt> targets) {
        MobEffect effect = potionType(m.arg(null, "type", "t"));
        if (effect == null) return;
        int duration = (int) num(m.arg("100", "duration", "d"), 100);
        // En las clases «level» va como nivel (1 = I). En los jefes, como en MythicMobs: es el amplificador (0 = I; el
        // «DAMAGE_RESISTANCE level=4» del Esqueleto de la Torre es Resistencia V: no le entra daño con el escudo)
        int level = ctx.host != null ? (int) Math.max(0, num(m.arg("0", "level", "lvl", "l"), 0))
                : (int) Math.max(0, num(m.arg("1", "level", "lvl", "l"), 1) - 1);
        boolean particles = bool(m.arg("true", "hasparticles", "p", "particles"));
        boolean icon = bool(m.arg("true", "hasicon", "i", "icon"));
        for (Tgt t : targets) {
            LivingEntity le = t.living();
            if (le == null) continue;
            if (harmful(effect) && !canHurt(ctx, le) && !isCasterSide(ctx, le)) continue;
            int d = duration;
            if (le instanceof Player && isCasterSide(ctx, le)) {
                d = selfEffect(effect, duration, level);
                if (d <= 0) continue;
            }
            // Hasta 255, como Bukkit: Minecraft manda el amplificador al cliente en un byte, así que de 128 en adelante el
            // cliente lo ve negativo (el «JUMP 155» de la jaula de Astwood es NO poder saltar). Hasta la 1.3.86 se cortaba
            // en 127: Salto 128, un supersalto de ~130 bloques («te manda a volar fuera del mapa»).
            le.addEffect(new MobEffectInstance(effect, d, Math.min(level, 255), false, particles, icon));
        }
    }

    /**
     * Lo que un jugador se pone a sí mismo con su skill: nada que le quite el control. Los packs se ponían lentitud
     * fuerte (quedarse clavado al cargar), levitación larga, saltos bloqueados, ceguera... Fuera; la levitación y la
     * lentitud suave, como mucho un momento. Devuelve la duración que queda (0 = no se pone).
     */
    private static int selfEffect(MobEffect effect, int duration, int amplifier) {
        if (effect == MobEffects.LEVITATION) return Math.min(duration, 20);
        if (effect == MobEffects.MOVEMENT_SLOWDOWN) return amplifier >= 2 ? 0 : Math.min(duration, 30);
        if (effect == MobEffects.JUMP && amplifier > 10) return 0; // «salto 128»: no poder saltar
        return harmful(effect) ? 0 : duration;
    }

    /** El que lanza o su dueño: las pociones malas que se pone él mismo (inmovilizarse al cargar) sí valen. */
    private static boolean isCasterSide(Ctx ctx, LivingEntity le) {
        ServerPlayer p = ctx.caster.player();
        return le == p || le == ctx.caster.entity;
    }

    /** La ejecución que corre ahora (para las pociones, que no reciben el contexto). */
    private static Ctx currentCtx;

    // ------------------------------------------------------------------------------------------- bloques y variables

    private record BlockKey(ServerLevel level, BlockPos pos) {}

    private static void setBlock(Mech m, Ctx ctx, List<Tgt> targets) {
        String type = m.arg("air", "type", "t", "material", "m", "block", "b").toLowerCase(Locale.ROOT);
        ResourceLocation rl = ResourceLocation.tryParse(type.contains(":") ? type : "minecraft:" + type);
        Block block = rl == null ? null : ForgeRegistries.BLOCKS.getValue(rl);
        if (block == null) return;
        for (Tgt t : targets) {
            Vec3 p = t.pos();
            ServerLevel level = t.who != null ? t.who.level() : ctx.caster.level();
            BlockPos pos = BlockPos.containing(p);
            BlockKey key = new BlockKey(level, pos);
            BlockState now = level.getBlockState(pos);
            if (block == Blocks.AIR) {
                // Solo se quita lo que puso una skill (nunca bloques del mundo)
                BlockState orig = PLACED.remove(key);
                if (orig != null) level.setBlock(pos, orig, 3);
                continue;
            }
            if (!now.isAir() && !PLACED.containsKey(key)) continue; // solo en huecos
            PLACED.putIfAbsent(key, now);
            level.setBlock(pos, block.defaultBlockState(), 3);
            // Por si el pack no lo quita: a los 30 s vuelve a estar como estaba
            later(600, () -> {
                BlockState orig = PLACED.remove(key);
                if (orig != null) level.setBlock(pos, orig, 3);
            });
        }
    }

    /** Deja el mundo como estaba (al parar el servidor). */
    static void restoreBlocks() {
        PLACED.forEach((k, orig) -> {
            try {
                k.level().setBlock(k.pos(), orig, 3);
            } catch (Exception ignored) {
                // mundo ya cerrado
            }
        });
        PLACED.clear();
    }

    /** Dónde se guarda una variable: caster.x, target.x, skill.x o global.x (sin prefijo, la de quien lanza). */
    static Map<String, String> varScope(String var, Ctx ctx, Tgt target) {
        String v = var.toLowerCase(Locale.ROOT);
        if (v.startsWith("skill.")) {
            if (ctx.vars == null) ctx.vars = new HashMap<>();
            return ctx.vars;
        }
        if (v.startsWith("target.") && target != null && target.who != null) return state(target.who).vars;
        if (v.startsWith("global.")) return GLOBAL_VARS;
        return state(ctx.caster).vars;
    }

    static String varName(String var) {
        String v = var.toLowerCase(Locale.ROOT);
        int dot = v.indexOf('.');
        if (dot > 0 && (v.startsWith("caster.") || v.startsWith("target.") || v.startsWith("skill.") || v.startsWith("global.")
                || v.startsWith("world."))) {
            return v.substring(dot + 1);
        }
        return v;
    }

    private static final Map<String, String> GLOBAL_VARS = new HashMap<>();

    private static void setVariable(Mech m, Ctx ctx, List<Tgt> targets) {
        String var = m.arg(null, "var", "variable", "name", "key", "k");
        if (var == null) return;
        String val = unquote(m.arg("", "value", "val", "v"));
        int duration = (int) num(m.arg("0", "duration", "d"), 0);
        if (var.toLowerCase(Locale.ROOT).startsWith("target.")) {
            for (Tgt t : targets) putVar(varScope(var, ctx, t), varName(var), val, duration);
        } else {
            // Las de un jefe van a su tempo: casi siempre marcan «estoy atacando» (la Abominación, Astwood) y, si el
            // ataque va más rápido, la marca tiene que acabar con él
            if (duration > 0 && ctx.host != null) duration = (int) Math.max(1, Math.round(duration / tempo(ctx)));
            putVar(varScope(var, ctx, null), varName(var), val, duration);
        }
    }

    private static long varSeq;

    /**
     * Pone una variable; con duración (setvar{...;duration=40}, en ticks) se borra sola al acabar, como en MythicMobs
     * (vuelve a valer «UNDEFINED»). Antes la duración no se miraba: la Hidra marcaba «saltando», «mordiendo» o
     * «lanzando fuego» para unos segundos y se quedaba marcada para siempre, sin volver a hacer esos ataques.
     */
    static void putVar(Map<String, String> scope, String name, String val, int duration) {
        scope.put(name, val);
        String key = "\u0001hasta:" + name;
        if (duration <= 0) {
            scope.remove(key);
            return;
        }
        String token = Long.toString(++varSeq);
        scope.put(key, token);
        later(duration, () -> {
            if (!token.equals(scope.get(key))) return; // la volvieron a poner después
            scope.remove(key);
            scope.remove(name);
        });
    }

    private static void addVariable(Mech m, Ctx ctx, List<Tgt> targets) {
        String var = m.arg(null, "var", "variable", "name", "key", "k");
        if (var == null) return;
        double add = num(unquote(m.arg("1", "amount", "a", "value", "val")), 1);
        Map<String, String> scope = varScope(var, ctx, targets.isEmpty() ? null : targets.get(0));
        String name = varName(var);
        double now = num(scope.get(name), 0);
        scope.put(name, fmt(now + add));
    }

    /**
     * setvarloc{var=caster.x;v=@forward{f=2}}: guarda un sitio (el del objetivo que se pone en «v», o el de la línea)
     * como «x,y,z» para usarlo luego con @variableLocation{var=caster.x}.
     */
    private static void setVarLoc(Mech m, Ctx ctx, List<Tgt> targets) {
        String var = m.arg(null, "var", "variable", "name", "key", "k");
        if (var == null) return;
        String v = unquote(m.arg("", "value", "val", "v", "location", "l")).trim();
        Vec3 at = null;
        if (v.startsWith("@")) {
            List<Tgt> r = SkillTargets.fromString(v, ctx);
            if (!r.isEmpty()) at = r.get(0).pos();
        } else if (v.split(",").length >= 3) {
            String[] p = v.split(",");
            at = new Vec3(num(p[0], 0), num(p[1], 0), num(p[2], 0));
        }
        if (at == null && !targets.isEmpty()) at = targets.get(0).pos();
        if (at == null) return;
        String val = fmt(Math.round(at.x * 1000) / 1000.0) + "," + fmt(Math.round(at.y * 1000) / 1000.0) + ","
                + fmt(Math.round(at.z * 1000) / 1000.0);
        varScope(var, ctx, targets.isEmpty() ? null : targets.get(0)).put(varName(var), val);
    }

    private static void unsetVariable(Mech m, Ctx ctx, List<Tgt> targets) {
        String var = m.arg(null, "var", "variable", "name", "key", "k");
        if (var == null) return;
        varScope(var, ctx, targets.isEmpty() ? null : targets.get(0)).remove(varName(var));
    }

    /**
     * Comandos de los packs: solo los de ModelEngine para ponerse o quitarse un modelo («meg disguise redsuit»,
     * «meg undisguise»), que aquí son la mecánica model. Los demás comandos no se ejecutan nunca.
     */
    private static void command(Mech m, Ctx ctx, List<Tgt> targets) {
        String c = unquote(m.arg("", "command", "cmd", "c")).trim().toLowerCase(Locale.ROOT);
        if (c.startsWith("/")) c = c.substring(1);
        String[] p = c.split("\\s+");
        if (p.length >= 2 && (p[0].equals("meg") || p[0].equals("modelengine"))) {
            Mech mm = new Mech();
            mm.m = "model";
            if (p[1].equals("disguise") && p.length >= 3) {
                String id = p[2].contains(".") ? p[2] : ctx.cls.id + "." + p[2];
                mm.a = Map.of("mid", id);
            } else if (p[1].equals("undisguise")) {
                mm.a = Map.of("remove", "true");
            } else {
                return;
            }
            SkillModels.model(mm, ctx, targets);
        }
    }

    static void message(Mech m, List<Tgt> targets) {
        String msg = unquote(m.arg("", "message", "msg", "m"));
        if (msg.isBlank()) return;
        // <&lb> <&rb> <&co> de MythicMobs son [ ] : (los demás <...> son colores)
        String clean = msg.replace("<&lb>", "[").replace("<&rb>", "]").replace("<&co>", ":")
                .replaceAll("<[^>]+>", "").replaceAll("[&§][0-9a-fk-orA-FK-OR]", "");
        for (Tgt t : targets) {
            if (t.entity() instanceof ServerPlayer p) {
                p.displayClientMessage(net.minecraft.network.chat.Component.literal(clean), true);
            }
        }
    }

    // ------------------------------------------------------------------------------------------- objetivos

    /** Los objetivos de una línea: los de su @objetivo o, sin él, los heredados (o quien lanza). */
    static List<Tgt> targets(Mech m, Ctx ctx) {
        if (m.t == null) {
            // En los jefes, las de ModelEngine sin objetivo son de su propio modelo (bodyrotation{mh=0} dentro de un ataque
            // lanzado con @target es para que el jefe deje de girar, no algo del jugador)
            if (ctx.host != null && OWN_MODEL.contains(m.m)) return List.of(Tgt.of(ctx.caster));
            if (ctx.targets != null && !ctx.targets.isEmpty()) return ctx.targets;
            return List.of(Tgt.of(ctx.caster));
        }
        return SkillTargets.resolve(m, ctx);
    }

    /** Mecánicas de ModelEngine que, sin objetivo, van al modelo de quien lanza. */
    private static final Set<String> OWN_MODEL = Set.of("bodyrotation", "bodyclamp", "model", "segment", "seg", "partvis",
            "partvisibility", "brightness", "renderinit", "defaultstate", "state", "animation", "lockmodel", "remapmodel",
            "changepart", "barset", "barcreate", "setmodelscale", "tint", "modifystate", "modifyanimation", "modstate",
            "statetoggle", "togglestate", "headconfig", "lockmodelhead", "lockhead", "hitboxconfig", "setitemmodel",
            "linkitembone", "setmodeltag", "setmodeltagvisible", "enchant", "billboard", "glowbone", "cycleparts", "scale",
            "meg_scale", "ik", "inversekinematics", "ikchain", "leashself", "animatemodel", "rootmotion", "changeparent", "submodel",
            "petrify", "syncyaw", "move");

    // ------------------------------------------------------------------------------------------- tick

    static void tick(MinecraftServer server) {
        clock++;
        // Tareas programadas (delay= y repeat= de una línea)
        if (!TASKS.isEmpty()) {
            List<Task> due = new ArrayList<>();
            Iterator<Task> it = TASKS.iterator();
            while (it.hasNext()) {
                Task t = it.next();
                if (t.at() <= clock) {
                    due.add(t);
                    it.remove();
                }
            }
            for (Task t : due) {
                try {
                    t.run().run();
                } catch (CancelSkill ignored) {
                    // la línea con delay paró su skill: ya no queda nada de ella aquí
                } catch (Throwable e) {
                    TFClient.LOGGER.debug("TF Skills: error en una tarea", e);
                }
            }
        }
        // Las listas que esperaban un delay
        if (!SCRIPTS.isEmpty()) {
            List<Script> now = new ArrayList<>(SCRIPTS);
            SCRIPTS.clear();
            for (Script s : now) {
                if (s.wait > 0) s.wait--;
                boolean done;
                currentCtx = s.ctx;
                try {
                    done = s.wait > 0 ? false : s.run();
                } catch (Throwable e) {
                    done = true;
                    TFClient.LOGGER.debug("TF Skills: error en una skill", e);
                } finally {
                    currentCtx = null;
                }
                if (!done) SCRIPTS.add(s);
            }
        }
        SkillMovers.tick();
        SkillAuras.tick();
        // Actores: temporizadores, pegados a su entidad, fin de vida
        Map<ServerLevel, List<SkillActor>> moved = new HashMap<>();
        List<SkillActor> list = new ArrayList<>(ACTORS);
        for (SkillActor a : list) {
            if (!a.alive) continue;
            a.age++;
            if (a.spin != 0F && a.age <= a.spinUntil && a.follow == null) a.moveTo(a.pos, a.yaw + a.spin, a.pitch);
            if (a.follow != null) {
                // El modelo de un mob de jefe sigue mientras la entidad esté en el mundo (muerto también: su animación
                // de muerte y lo que hacen sus skills al morir)
                boolean bossHost = SkillMobs.host(a.follow) != null;
                if (a.follow.isRemoved() || !bossHost && !a.follow.isAlive()) {
                    a.remove();
                    continue;
                }
                float yaw = a.follow instanceof LivingEntity le ? le.yBodyRot : a.follow.getYRot();
                a.moveTo(a.follow.position(), yaw, 0F);
                if (a.layers != null) {
                    Vec3 d = a.follow.getDeltaMovement();
                    a.layers.setWalking(d.x * d.x + d.z * d.z > 0.0009 && !(a.follow instanceof LivingEntity l2 && l2.isDeadOrDying()));
                }
            } else if (a.physics) {
                a.physicsTick();
            } else if (a.def != null && a.def.living) {
                try {
                    SkillMinions.tick(a);
                } catch (Throwable e) {
                    TFClient.LOGGER.debug("TF Skills: error en un esbirro", e);
                }
            }
            if (a.layers != null && a.me != null) {
                net.tierrasfantasticas.tfclient.vfx.VfxModel vm = SkillModels.modelOf(a);
                if (vm != null) {
                    a.layers.update(vm, a.age);
                    a.layers.prune(a.age);
                    // Lo que tiene memoria (colas y física que se arrastran, la IK que entra y sale, cabezas lentas) se
                    // calcula cada tick, como lo va viendo el cliente, aunque nadie le pregunte por un hueso
                    if (a.rig(vm).stateful()) SkillModels.pose(a, vm);
                }
            }
            if (!a.timers.isEmpty()) {
                // (sobre una copia: un temporizador puede cambiar el modelo del efecto y con él sus temporizadores)
                for (Mech mm : new ArrayList<>(a.timers)) {
                    int every = (int) Math.max(1, num(mm.trv, 20));
                    if (a.age % every == 0) {
                        Ctx c = new Ctx(a.cls, a.who);
                        c.origin = a.pos;
                        c.trigger = a.owner;
                        c.aim = a.target;
                        c.host = a.host;
                        currentCtx = c;
                        try {
                            exec(mm, c);
                        } catch (Throwable e) {
                            TFClient.LOGGER.debug("TF Skills: error en un temporizador", e);
                        } finally {
                            currentCtx = null;
                        }
                    }
                }
            }
            // Lo que nadie quita se va al minuto; la bala de un proyectil u órbita dura lo que su movimiento (el arma
            // que flota sobre el jugador es una órbita de 99999 ticks): solo un tope muy largo por si acaso
            if (a.follow == null && a.age > (a.carried ? SkillActor.MAX_AGE_CARRIED : SkillActor.MAX_AGE)) a.remove();
            if (a.moved) {
                a.moved = false;
                moved.computeIfAbsent(a.level, k -> new ArrayList<>()).add(a);
            }
        }
        // (el iterador se hacía antes del bucle y, si un efecto sacaba otro —Eldric—, saltaba ConcurrentModificationException
        // y el resto del tick no se hacía: ni se mandaban los movimientos ni se limpiaban los estados. Prueba en el juego)
        ACTORS.removeIf(a -> !a.alive);
        moved.forEach(SkillNet::sendMoves);
        // Estados de quien ya no existe
        if (clock % 200 == 0) {
            STATES.values().removeIf(s -> clock - s.touched > 6000 && s.auras.isEmpty());
            SKILL_HITS.values().removeIf(h -> clock - h.at() > 40);
        }
    }

    /** Lo que estaba en marcha se para (al parar el servidor o recargar). */
    static void clear() {
        SCRIPTS.clear();
        SKILL_HITS.clear();
        TASKS.clear();
        for (SkillActor a : ACTORS) a.alive = false;
        ACTORS.clear();
        SkillMovers.clear();
        SkillAuras.clear();
        STATES.clear();
        restoreBlocks();
    }

    /** Quita lo de un jugador que se va (sus efectos y lo que tiene pegado). */
    static void forget(UUID player) {
        for (SkillActor a : ACTORS) {
            ServerPlayer p = a.who.player();
            if (p != null && p.getUUID().equals(player)) a.remove();
        }
        SkillAuras.forget(player);
        STATES.remove(player);
    }

    // ------------------------------------------------------------------------------------------- ayudas

    /** La entidad viva a la que apunta el jugador (hasta «range» bloques), o null. */
    static LivingEntity aimed(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1F);
        Vec3 end = eye.add(look.scale(range));
        HitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        AABB box = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.5);
        EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player, eye, end, box,
                e -> e instanceof LivingEntity le && le.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand), range * range);
        if (hit != null && hit.getEntity() instanceof LivingEntity le) return le;
        // Sin acertar justo: el más cercano a la línea de mira (como el objetivo de MythicLib)
        LivingEntity best = null;
        double bestScore = 0.97;
        for (LivingEntity le : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range))) {
            if (le == player || !le.isAlive() || le instanceof ArmorStand || le.isSpectator()) continue;
            Vec3 to = le.getBoundingBox().getCenter().subtract(eye);
            double d = to.length();
            if (d > range || d < 0.5) continue;
            double dot = to.normalize().dot(look);
            if (dot > bestScore && player.hasLineOfSight(le)) {
                bestScore = dot;
                best = le;
            }
        }
        return best;
    }

    /** Dirección de un giro e inclinación (grados, como Minecraft). */
    static Vec3 dir(float yaw, float pitch) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        return new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
    }

    static float yawOf(Vec3 d) {
        return (float) Math.toDegrees(Math.atan2(-d.x, d.z));
    }

    static float pitchOf(Vec3 d) {
        double h = Math.sqrt(d.x * d.x + d.z * d.z);
        return (float) Math.toDegrees(-Math.atan2(d.y, h));
    }

    /**
     * Número de un argumento: «12», «1.5», «1to3» (al azar en el rango), «&lt;random.float.1to2&gt;» o «0.5s»
     * (segundos → ticks no: se deja el número). def si no se entiende.
     */
    static double num(String s, double def) {
        if (s == null) return def;
        s = unquote(s.trim());
        if (s.isEmpty()) return def;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ignored) {
            // sigue
        }
        Double expr = SkillVars.eval(s);
        if (expr != null) return expr;
        java.util.regex.Matcher r = java.util.regex.Pattern.compile("<random\\.(?:float|int)\\.(-?[\\d.]+)to(-?[\\d.]+)>").matcher(s);
        if (r.find()) {
            double a = Double.parseDouble(r.group(1)), b = Double.parseDouble(r.group(2));
            return a + ThreadLocalRandom.current().nextDouble() * (b - a);
        }
        java.util.regex.Matcher range = java.util.regex.Pattern.compile("^(-?[\\d.]+)to(-?[\\d.]+)$").matcher(s);
        if (range.find()) {
            double a = Double.parseDouble(range.group(1)), b = Double.parseDouble(range.group(2));
            return a + ThreadLocalRandom.current().nextDouble() * (b - a);
        }
        java.util.regex.Matcher lead = java.util.regex.Pattern.compile("^-?\\d+(\\.\\d+)?").matcher(s);
        if (lead.find()) return Double.parseDouble(lead.group());
        return def;
    }

    static boolean bool(String s) {
        return s != null && (s.equalsIgnoreCase("true") || s.equals("1") || s.equalsIgnoreCase("yes"));
    }

    static String unquote(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.length() >= 2 && (s.startsWith("\"") && s.endsWith("\"") || s.startsWith("'") && s.endsWith("'"))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    static void warn(String what) {
        if (WARNED.add(what)) TFClient.LOGGER.info("TF Skills: no soportado ({})", what);
    }

    static Ctx current() {
        return currentCtx;
    }

    static void setCurrent(Ctx c) {
        currentCtx = c;
    }

    static boolean isHostileTo(Ctx ctx, LivingEntity e) {
        return canHurt(ctx, e) && (e instanceof net.minecraft.world.entity.monster.Enemy || e instanceof Player
                || e instanceof Mob mob && mob.getTarget() != null);
    }
}
