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
            return actor != null ? actor.owner : null;
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
    }

    static WhoState state(Who w) {
        WhoState s = STATES.computeIfAbsent(w.key(), k -> new WhoState());
        s.touched = clock;
        return s;
    }

    static WhoState stateIfAny(Who w) {
        return STATES.get(w.key());
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
                    wait = (int) Math.max(0, num(m.arg("0", "_", "ticks", "t", "d"), 0));
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

    private record Task(long at, Runnable run) {}

    static void later(int ticks, Runnable r) {
        if (ticks <= 0) {
            r.run();
            return;
        }
        if (TASKS.size() > 20000) return;
        TASKS.add(new Task(clock + ticks, r));
    }

    // ------------------------------------------------------------------------------------------- entrada

    /**
     * Lanza la skill de MythicMobs «entry» de una skill de clase como lo haría MMOCore: quien lanza es el jugador,
     * @target es lo que tiene delante (hasta 32 bloques) y, sin objetivo, la mecánica va a él mismo.
     */
    static boolean cast(ServerPlayer player, ClassDef cls, SkillDefs.SkillDef skill) {
        Meta meta = cls.meta(skill.entry());
        if (meta == null) return false;
        Ctx ctx = new Ctx(cls, Who.of(player));
        if (!cls.startVars.isEmpty()) {
            Map<String, String> vars = state(ctx.caster).vars;
            cls.startVars.forEach(vars::putIfAbsent);
        }
        ctx.mods = skill.mods();
        ctx.origin = player.position();
        ctx.aim = aimed(player, 32);
        ctx.targets = null;
        ctx.vars = new HashMap<>();
        return runMeta(meta, ctx);
    }

    /** Ejecuta una skill de MythicMobs (con su cooldown y sus condiciones). false si no se lanzó. */
    static boolean runMeta(Meta meta, Ctx ctx) {
        if (meta == null || ctx.depth > 40 || SCRIPTS.size() > MAX_SCRIPTS) return false;
        if (meta.cooldown > 0) {
            WhoState st = state(ctx.caster);
            Long until = st.cooldowns.get(meta.name);
            if (until != null && until > clock) return false;
            st.cooldowns.put(meta.name, clock + Math.round(meta.cooldown * 20));
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
        Script s = new Script(run, meta.mechs);
        if (!s.run()) SCRIPTS.add(s);
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
        runMeta(ctx.cls.meta(name.trim()), ctx.copy());
    }

    // ------------------------------------------------------------------------------------------- mecánicas

    /** Ejecuta una línea: probabilidad, condiciones en línea, objetivos, delay/repeat de la línea y la mecánica. */
    static void exec(Mech raw, Ctx ctx) {
        Mech m = raw.dyn ? SkillVars.resolve(raw, ctx) : raw;
        if (m.chance < 1.0 && ThreadLocalRandom.current().nextDouble() >= m.chance) return;
        for (Cond c : m.c) {
            boolean r = SkillConds.test(c, ctx.caster, ctx, null);
            if (c.not() == r) return;
        }
        int delay = (int) num(m.a.get("delay"), 0);
        int repeat = (int) num(m.arg("0", "repeat", "r_"), 0);
        int every = (int) Math.max(1, num(m.arg("1", "repeatinterval", "repeati", "ri"), 1));
        Runnable once = () -> {
            if (ctx.caster.actor != null && !ctx.caster.alive() && !m.m.equals("remove")) return;
            Ctx use = ctx;
            // origin=@Objetivo{...}: la línea (y lo que llame) sale desde ahí
            String o = m.a.get("origin");
            if (o != null && o.trim().startsWith("@")) {
                List<Tgt> r = SkillTargets.fromString(o, ctx);
                if (!r.isEmpty()) {
                    use = ctx.copy();
                    use.depth = ctx.depth;
                    use.origin = r.get(0).pos();
                }
            }
            apply(m, use, targets(m, use));
        };
        if (delay <= 0 && repeat <= 0) {
            once.run();
            return;
        }
        later(delay, once);
        for (int k = 1; k <= Math.min(repeat, 400); k++) later(delay + k * every, once);
    }

    static void apply(Mech m, Ctx ctx, List<Tgt> targets) {
        switch (m.m) {
            case "skill", "metaskill", "cast", "s" -> callSkill(m, ctx, targets);
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
                for (Tgt t : targets) if (t.who != null && t.who.actor != null) t.who.actor.remove();
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
            case "percentdamage" -> percentDamage(m, ctx, targets);
            case "heal" -> heal(m, targets, false);
            case "healpercent" -> heal(m, targets, true);
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
            case "look" -> SkillMotion.look(m, ctx, targets);
            case "stun", "prison", "freeze" -> SkillMotion.stun(m, ctx, targets);
            case "mount", "mounttarget", "dismount" -> { /* los actores no se montan */ }
            case "setblock", "setblocktype" -> setBlock(m, ctx, targets);
            case "setvariable", "setvar", "variableset" -> setVariable(m, ctx, targets);
            case "variableadd", "varadd" -> addVariable(m, ctx, targets);
            case "variableunset", "unsetvariable" -> unsetVariable(m, ctx, targets);
            case "message", "msg", "actionmessage" -> message(m, targets);
            case "variablesubtract", "varsubtract" -> {
                Mech neg = m.copyWith(new HashMap<>(m.a), m.ta);
                neg.a.put("amount", fmt(-num(unquote(m.arg("1", "amount", "a", "value", "val")), 1)));
                addVariable(neg, ctx, targets);
            }
            case "setvarloc", "setvariablelocation" -> setVarLoc(m, ctx, targets);
            // Estado de cada quién
            case "addtag" -> {
                String tag = m.arg(null, "tag", "t");
                for (Tgt t : targets) if (tag != null && t.who != null) state(t.who).tags.add(tag.toLowerCase(Locale.ROOT));
            }
            case "removetag" -> {
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
                int ticks = (int) num(m.arg("20", "ticks", "t", "duration", "d", "_"), 20);
                for (Tgt t : targets) if (t.who != null) state(t.who).gcdUntil = clock + ticks;
            }
            case "setskillcooldown" -> {
                Meta other = ctx.cls.meta(m.arg(null, "skill", "s"));
                double sec = num(m.arg("0", "seconds", "s2", "ticks"), 0);
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
                for (Tgt t : targets) if (sig != null && t.who != null && t.who.actor != null) t.who.actor.signal(sig, ctx.caster);
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
                for (Tgt t : targets) if (t.who != null && t.who.actor != null) t.who.actor.ai = ai;
            }
            case "setspeed" -> {
                double sp = num(m.arg("1", "speed", "s", "amount", "a"), 1);
                for (Tgt t : targets) if (t.who != null && t.who.actor != null) t.who.actor.speedMul = sp;
            }
            case "settextdisplay" -> {
                String text = unquote(m.arg("", "text", "t"));
                for (Tgt t : targets) if (t.who != null && t.who.actor != null) t.who.actor.setText(text);
            }
            case "setmodelscale" -> SkillModels.scale(m, ctx, targets);
            case "mountmodel" -> SkillModels.mount(m, ctx, targets);
            case "dismountmodel", "dismountall" -> SkillModels.dismount(ctx, targets);
            case "setnodamageticks" -> {
                int ticks = (int) num(m.arg("0", "ticks", "t", "_"), 0);
                for (Tgt t : targets) if (t.living() != null) t.living().invulnerableTime = Math.max(0, ticks);
            }
            case "directionalvelocity" -> SkillMotion.directional(m, ctx, targets);
            case "spin" -> {
                // spin{velocity=grados por tick;duration=ticks (0 = siempre)}: el efecto gira sobre sí mismo
                float v = (float) num(m.arg("10", "velocity", "v"), 10);
                int d = (int) num(m.arg("0", "duration", "d"), 0);
                for (Tgt t : targets) {
                    if (t.who == null || t.who.actor == null) continue;
                    t.who.actor.spin = v;
                    t.who.actor.spinUntil = d > 0 ? t.who.actor.age + d : Integer.MAX_VALUE;
                }
            }
            case "recoil" -> SkillMotion.recoil(m, ctx, targets);
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
            case "cancelevent", "animatearmorstand", "bodyrotation", "brightness", "lockmodel",
                    "bodyclamp", "enchant", "setgravity", "threat", "runaitargetselector", "runaigoalselector",
                    "modifyglobalscore", "setglobalscore", "feed", "playanimation", "swing", "remapmodel", "glow",
                    "setcollidable", "setinvulnerable", "setrotation", "bindhitbox", "clearthreat", "cullconfig",
                    "posearmorstand", "segment", "removehelditem" -> { /* nada (o no se puede hacer sin entidades de verdad) */ }
            default -> warn("mecánica " + m.m);
        }
    }

    private static void callSkill(Mech m, Ctx ctx, List<Tgt> targets) {
        String names = m.arg(null, "s", "skill", "skills", "$skill", "spell");
        if (names == null) return;
        for (String n : names.split(",")) callNamed(n.trim().split(" ")[0], ctx, targets);
    }

    /** skill{s=X}: ejecuta X con los objetivos de la línea (o los heredados) y su propio tiempo. */
    static void callNamed(String name, Ctx ctx, List<Tgt> targets) {
        Meta meta = ctx.cls.meta(name);
        if (meta == null) return;
        Ctx c = ctx.copy();
        c.targets = targets;
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

    private static void summon(Mech m, Ctx ctx, List<Tgt> targets) {
        String type = m.arg(null, "type", "t", "mob", "m");
        MobDef def = ctx.cls.mob(type);
        if (def == null) {
            warn("mob " + type);
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

    /** Saca un actor y le pasa sus mecánicas ~onSpawn; las ~onTimer quedan para cada tick. */
    static SkillActor spawn(Ctx ctx, MobDef def, Vec3 at, float yaw, float pitch) {
        if (ACTORS.size() >= MAX_ACTORS) return null;
        ServerLevel level = ctx.caster.level();
        SkillActor a = new SkillActor(ctx.cls, def, level, at, yaw, pitch, ctx.caster);
        ACTORS.add(a);
        SkillNet.near(level, at, a.spawnMessage());
        Ctx mine = ctx.as(a.who);
        mine.targets = null;
        List<Mech> onSpawn = new ArrayList<>();
        for (Mech mm : def.mechs) {
            String tr = mm.tr == null ? "onspawn" : mm.tr;
            if (tr.equals("ontimer")) a.timers.add(mm);
            else if (tr.equals("onspawn") || tr.equals("onready")) onSpawn.add(mm);
        }
        Script s = new Script(mine, onSpawn);
        if (!s.run()) SCRIPTS.add(s);
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

    private static void damage(Mech m, Ctx ctx, List<Tgt> targets, boolean base) {
        ServerPlayer owner = ctx.caster.player();
        double amount = num(m.arg("1", "amount", "a", "damage"), 1);
        if (base) {
            double attack = owner != null ? owner.getAttributeValue(Attributes.ATTACK_DAMAGE) : 1;
            amount = attack * num(m.arg("1", "multiplier", "m"), 1);
        }
        boolean noKnock = bool(m.arg("false", "preventknockback", "pkb"));
        boolean ignoreArmor = bool(m.arg("false", "ignorearmor", "ia"));
        for (Tgt t : targets) {
            LivingEntity le = t.living();
            if (le == null || !canHurt(ctx, le)) continue;
            hurt(ctx, le, amount * ctx.power * t.power * SkillServer.damageScale(), noKnock, ignoreArmor);
        }
    }

    private static void percentDamage(Mech m, Ctx ctx, List<Tgt> targets) {
        double pct = num(m.arg("0.1", "percent", "p"), 0.1);
        for (Tgt t : targets) {
            LivingEntity le = t.living();
            if (le == null || !canHurt(ctx, le)) continue;
            hurt(ctx, le, le.getMaxHealth() * pct * SkillServer.damageScale(), false, true);
        }
    }

    static void hurt(Ctx ctx, LivingEntity le, double amount, boolean noKnock, boolean ignoreArmor) {
        if (amount <= 0) return;
        ServerPlayer owner = ctx.caster.player();
        Vec3 motion = le.getDeltaMovement();
        le.invulnerableTime = 0;
        inDamage = true;
        try {
            if (owner != null) {
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
    }

    private static void heal(Mech m, List<Tgt> targets, boolean percent) {
        double a = num(m.arg("1", "amount", "a", "multiplier", "m"), 1);
        for (Tgt t : targets) {
            LivingEntity le = t.living();
            if (le == null) continue;
            le.heal((float) (percent ? le.getMaxHealth() * a : a));
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
        // En MythicMobs «level» es el nivel (1 = I); en Minecraft, el amplificador empieza en 0
        int level = (int) Math.max(0, num(m.arg("1", "level", "lvl", "l"), 1) - 1);
        boolean particles = bool(m.arg("true", "hasparticles", "p", "particles"));
        boolean icon = bool(m.arg("true", "hasicon", "i", "icon"));
        for (Tgt t : targets) {
            LivingEntity le = t.living();
            if (le == null) continue;
            if (harmful(effect) && !canHurt(ctx, le) && !isCasterSide(ctx, le)) continue;
            le.addEffect(new MobEffectInstance(effect, duration, Math.min(level, 127), false, particles, icon));
        }
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
        if (var.toLowerCase(Locale.ROOT).startsWith("target.")) {
            for (Tgt t : targets) varScope(var, ctx, t).put(varName(var), val);
        } else {
            varScope(var, ctx, null).put(varName(var), val);
        }
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

    private static void message(Mech m, List<Tgt> targets) {
        String msg = unquote(m.arg("", "message", "msg", "m"));
        if (msg.isBlank()) return;
        String clean = msg.replaceAll("<[^>]+>", "").replaceAll("[&§][0-9a-fk-orA-FK-OR]", "");
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
            if (ctx.targets != null && !ctx.targets.isEmpty()) return ctx.targets;
            return List.of(Tgt.of(ctx.caster));
        }
        return SkillTargets.resolve(m, ctx);
    }

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
        Iterator<SkillActor> it = ACTORS.iterator();
        List<SkillActor> list = new ArrayList<>(ACTORS);
        for (SkillActor a : list) {
            if (!a.alive) continue;
            a.age++;
            if (a.spin != 0F && a.age <= a.spinUntil && a.follow == null) a.moveTo(a.pos, a.yaw + a.spin, a.pitch);
            if (a.follow != null) {
                if (!a.follow.isAlive() || a.follow.isRemoved()) {
                    a.remove();
                    continue;
                }
                float yaw = a.follow instanceof LivingEntity le ? le.yBodyRot : a.follow.getYRot();
                a.moveTo(a.follow.position(), yaw, 0F);
            } else if (a.def != null && a.def.living) {
                try {
                    SkillMinions.tick(a);
                } catch (Throwable e) {
                    TFClient.LOGGER.debug("TF Skills: error en un esbirro", e);
                }
            }
            if (!a.timers.isEmpty()) {
                for (Mech mm : a.timers) {
                    int every = (int) Math.max(1, num(mm.trv, 20));
                    if (a.age % every == 0) {
                        Ctx c = new Ctx(a.cls, a.who);
                        c.origin = a.pos;
                        c.trigger = a.owner;
                        c.aim = a.target;
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
            if (a.age > SkillActor.MAX_AGE && a.follow == null) a.remove();
            if (a.moved) {
                a.moved = false;
                moved.computeIfAbsent(a.level, k -> new ArrayList<>()).add(a);
            }
        }
        while (it.hasNext()) if (!it.next().alive) it.remove();
        moved.forEach(SkillNet::sendMoves);
        // Estados de quien ya no existe
        if (clock % 200 == 0) STATES.values().removeIf(s -> clock - s.touched > 6000 && s.auras.isEmpty());
    }

    /** Lo que estaba en marcha se para (al parar el servidor o recargar). */
    static void clear() {
        SCRIPTS.clear();
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
