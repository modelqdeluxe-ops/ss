package net.tierrasfantasticas.tfclient.skills;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.skills.SkillDefs.Mech;
import net.tierrasfantasticas.tfclient.skills.SkillRuntime.Ctx;
import net.tierrasfantasticas.tfclient.skills.SkillRuntime.Tgt;
import net.tierrasfantasticas.tfclient.skills.SkillRuntime.Who;

/**
 * Las auras de MythicMobs: una marca con nombre sobre alguien que dura un tiempo, se acumula (stacks) y lanza skills
 * al empezar, cada cierto tiempo y al acabar. Los packs las usan como estados (modo dragón, marca del viento...) y las
 * miran con hasaura / hasaurastacks.
 */
final class SkillAuras {
    private static final List<Aura> AURAS = new ArrayList<>();

    private SkillAuras() {}

    static final class Aura {
        final String name;
        final Who on;
        Ctx ctx;
        int left;
        int duration;
        int interval;
        int stacks;
        int maxStacks;
        String onTick;
        String onEnd;
        int age;
        boolean alive = true;
        /** onDamaged / onAttack: multiplica el daño que recibe / que hace quien la lleva. */
        double takenMult = 1.0;
        double dealtMult = 1.0;
        /** De qué es: aura, ondamaged, onattack, onswing, ondeath. */
        String kind = "aura";
        /** cancelEvent: ese golpe (o el golpe al aire) no pasa. */
        boolean cancel;
        /** Tipos de daño a los que afecta (damageMods / modDamageType; vacío = a todos) y multiplicador por tipo. */
        final java.util.Map<String, Double> types = new java.util.HashMap<>();
        /** La skill de su evento (al recibir el golpe, al pegar, al dar un golpe al aire, al morir). */
        String onEvent;
        /** Cuántas veces puede saltar antes de acabarse (0 = sin límite). */
        int charges;
        /** El orbital que la lleva (orbital{auraName=X}): se acaban juntos. */
        SkillMovers.Mover mover;

        Aura(String name, Who on) {
            this.name = name;
            this.on = on;
        }
    }

    static void aura(Mech m, Ctx ctx, List<Tgt> targets) {
        String name = m.arg(null, "auraname", "aura", "name", "n", "buffname", "b");
        if (name == null) name = "aura";
        String key = name.toLowerCase(Locale.ROOT);
        int duration = (int) SkillRuntime.num(m.arg("200", "duration", "d", "ticks", "t", "time"), 200);
        int interval = (int) Math.max(1, SkillRuntime.num(m.arg("1", "interval", "i"), 1));
        int maxStacks = (int) Math.max(1, SkillRuntime.num(m.arg("1", "maxstacks", "ms", "stacks"), 1));
        boolean refresh = SkillRuntime.bool(m.arg("true", "refreshduration", "rd"));
        String onStart = m.arg(null, "onstart", "os", "onstartskill");
        String onTick = m.arg(null, "ontick", "ot", "ontickskill");
        String onEnd = m.arg(null, "onend", "oe", "onendskill");
        for (Tgt t : targets) {
            if (t.who == null || !t.who.alive()) continue;
            SkillRuntime.WhoState st = SkillRuntime.state(t.who);
            Aura a = st.auras.get(key);
            if (a != null && a.alive) {
                a.stacks = Math.min(a.maxStacks, a.stacks + 1);
                if (refresh) a.left = duration;
                continue;
            }
            a = new Aura(key, t.who);
            a.ctx = ctx.copy();
            a.ctx.targets = List.of(Tgt.of(t.who));
            a.left = duration;
            a.duration = duration;
            a.interval = interval;
            a.maxStacks = maxStacks;
            a.stacks = 1;
            a.onTick = onTick;
            a.onEnd = onEnd;
            if (m.m.equals("ondamaged")) a.takenMult = SkillRuntime.num(m.arg("1", "multiplier", "m"), 1);
            if (m.m.equals("onattack")) a.dealtMult = SkillRuntime.num(m.arg("1", "multiplier", "m"), 1);
            a.kind = m.m;
            a.cancel = SkillRuntime.bool(m.arg("false", "cancelevent", "ce", "canceldamage"));
            a.charges = (int) SkillRuntime.num(m.arg("0", "charges", "c"), 0);
            a.onEvent = switch (m.m) {
                case "ondamaged" -> m.arg(null, "ondamagedskill", "onhit", "oh", "od", "ondamaged");
                case "onattack" -> m.arg(null, "onattackskill", "onhit", "oh", "onattack");
                case "onswing" -> m.arg(null, "onswing", "onswingskill", "os2");
                case "ondeath" -> m.arg(null, "ondeath", "od", "ondeathskill");
                case "onshoot" -> m.arg(null, "onshoot", "onshootskill", "oh");
                default -> null;
            };
            // damageMods=FALL 0 / moddamagetype=ENTITY_ATTACK,PROJECTILE: a qué golpes afecta (y con qué multiplicador)
            String mods = m.arg(null, "damagemods", "damagemodifiers", "moddamagetype", "damagetypes");
            if (mods != null) {
                for (String part : mods.split("[,;]")) {
                    String[] kv = part.trim().split("\\s+");
                    if (kv[0].isEmpty()) continue;
                    a.types.put(kv[0].toUpperCase(Locale.ROOT), kv.length > 1 ? SkillRuntime.num(kv[1], 1) : -1.0);
                }
            }
            st.auras.put(key, a);
            AURAS.add(a);
            if (onStart != null) SkillRuntime.runMeta(ctx.cls.meta(onStart), auraCtx(a));
        }
    }

    static void remove(Mech m, List<Tgt> targets) {
        String name = m.arg(null, "auraname", "aura", "name", "n", "buffname", "b");
        if (name == null) return;
        String key = name.toLowerCase(Locale.ROOT);
        int stacks = (int) SkillRuntime.num(m.arg("0", "stacks", "s"), 0);
        for (Tgt t : targets) {
            if (t.who == null) continue;
            SkillRuntime.WhoState st = SkillRuntime.stateIfAny(t.who);
            Aura a = st == null ? null : st.auras.get(key);
            if (a == null || !a.alive) continue;
            if (stacks > 0 && a.stacks > stacks) {
                a.stacks -= stacks;
            } else {
                finish(a, st);
            }
        }
    }

    /** El aura que va con un orbital (orbital{auraName}): hasaura la ve y auraremove para el orbital. */
    static Aura forMover(SkillMovers.Mover p, String name, Who on, Ctx ctx, int duration) {
        String key = name.toLowerCase(Locale.ROOT);
        SkillRuntime.WhoState st = SkillRuntime.state(on);
        Aura a = new Aura(key, on);
        a.ctx = ctx.copy();
        a.ctx.targets = List.of(Tgt.of(on));
        a.left = duration;
        a.duration = duration;
        a.interval = 1;
        a.maxStacks = 1;
        a.stacks = 1;
        a.mover = p;
        st.auras.put(key, a);
        AURAS.add(a);
        return a;
    }

    static void moverEnded(Aura a) {
        if (a.alive) finish(a, SkillRuntime.stateIfAny(a.on));
    }

    // ------------------------------------------------------------------------------------------- eventos

    /** Las auras de evento de alguien (de un tipo), vivas. */
    private static List<Aura> of(net.minecraft.world.entity.Entity e, String kind) {
        SkillRuntime.WhoState st = SkillRuntime.stateIfAny(Who.of(e));
        if (st == null || st.auras.isEmpty()) return List.of();
        List<Aura> out = new ArrayList<>();
        for (Aura a : st.auras.values()) if (a.alive && a.kind.equals(kind)) out.add(a);
        return out;
    }

    /** ¿Afecta el aura a este tipo de daño? (sin lista, a todos). */
    private static boolean affects(Aura a, String type) {
        if (a.types.isEmpty()) return true;
        return a.types.containsKey(type);
    }

    /** Le van a hacer daño a «e» (de tipo «type», de «attacker»): sus auras onDamaged. true = se cancela el golpe. */
    static boolean damaged(net.minecraft.world.entity.LivingEntity e, String type, net.minecraft.world.entity.Entity attacker) {
        boolean cancel = false;
        for (Aura a : of(e, "ondamaged")) {
            if (!affects(a, type)) continue;
            Double mult = a.types.get(type);
            if (mult != null && mult == 0) cancel = true;
            if (a.cancel) cancel = true;
            fire(a, attacker);
        }
        return cancel;
    }

    /** «e» va a pegar a «victim»: sus auras onAttack. true = se cancela el golpe. */
    static boolean attacking(net.minecraft.world.entity.Entity e, net.minecraft.world.entity.LivingEntity victim) {
        boolean cancel = false;
        for (Aura a : of(e, "onattack")) {
            if (a.cancel) cancel = true;
            fire(a, victim);
        }
        return cancel;
    }

    /** Dio un golpe (clic izquierdo): sus auras onSwing. */
    static void swing(net.minecraft.server.level.ServerPlayer player) {
        for (Aura a : of(player, "onswing")) fire(a, null);
    }

    /** Murió alguien con un aura onDeath: su skill (en el sitio donde cayó). */
    static void died(net.minecraft.world.entity.LivingEntity e) {
        for (Aura a : of(e, "ondeath")) {
            if (a.onEvent != null) {
                Ctx c = a.ctx.copy();
                c.targets = List.of(Tgt.at(e.position()));
                c.origin = e.position();
                c.trigger = Who.of(e);
                SkillRuntime.runMeta(c.cls.meta(a.onEvent), c);
            }
            finish(a, SkillRuntime.stateIfAny(Who.of(e)));
        }
    }

    private static void fire(Aura a, net.minecraft.world.entity.Entity other) {
        if (a.onEvent != null) {
            Ctx c = auraCtx(a);
            if (other != null) {
                c.trigger = Who.of(other);
                if (a.kind.equals("onattack")) c.targets = List.of(Tgt.of(Who.of(other)));
            }
            try {
                SkillRuntime.runMeta(a.ctx.cls.meta(a.onEvent), c);
            } catch (SkillRuntime.CancelSkill ignored) {
                // la skill se paró sola
            }
        }
        if (a.charges > 0 && --a.charges <= 0) finish(a, SkillRuntime.stateIfAny(a.on));
    }

    /** Las skills del aura las lanza quien la puso, con el que la lleva como objetivo. */
    private static Ctx auraCtx(Aura a) {
        Ctx c = a.ctx.copy();
        c.targets = List.of(Tgt.of(a.on));
        c.origin = a.on.pos();
        return c;
    }

    static void tick() {
        if (AURAS.isEmpty()) return;
        for (Aura a : new ArrayList<>(AURAS)) {
            if (!a.alive) continue;
            a.age++;
            a.left--;
            try {
                if (!a.on.alive()) {
                    SkillRuntime.WhoState st = SkillRuntime.stateIfAny(a.on);
                    finish(a, st);
                    continue;
                }
                if (a.onTick != null && a.age % a.interval == 0) {
                    try {
                        SkillRuntime.runMeta(a.ctx.cls.meta(a.onTick), auraCtx(a));
                    } catch (SkillRuntime.CancelSkill ignored) {
                        // esa vuelta de la skill se paró sola
                    }
                }
                if (a.left <= 0) finish(a, SkillRuntime.stateIfAny(a.on));
            } catch (Throwable t) {
                a.alive = false;
                TFClient.LOGGER.debug("TF Skills: error en un aura", t);
            }
        }
        AURAS.removeIf(a -> !a.alive);
    }

    private static void finish(Aura a, SkillRuntime.WhoState st) {
        if (!a.alive) return;
        a.alive = false;
        if (st != null && st.auras.get(a.name) == a) st.auras.remove(a.name);
        if (a.mover != null && a.mover.alive) SkillMovers.end(a.mover);
        if (a.onEnd != null) {
            try {
                SkillRuntime.runMeta(a.ctx.cls.meta(a.onEnd), auraCtx(a));
            } catch (SkillRuntime.CancelSkill ignored) {
                // nada
            }
        }
    }

    /** Multiplicador del daño que recibe (taken) o hace (!taken) una entidad por sus auras onDamaged / onAttack. */
    static double multiplier(net.minecraft.world.entity.Entity e, boolean taken) {
        SkillRuntime.WhoState st = SkillRuntime.stateIfAny(Who.of(e));
        if (st == null || st.auras.isEmpty()) return 1.0;
        double m = 1.0;
        for (Aura a : st.auras.values()) if (a.alive) m *= taken ? a.takenMult : a.dealtMult;
        return m;
    }

    static void forget(UUID player) {
        for (Aura a : AURAS) {
            if (a.on.entity != null && a.on.entity.getUUID().equals(player)) a.alive = false;
            ServerPlayer p = a.ctx.caster.player();
            if (p != null && p.getUUID().equals(player)) a.alive = false;
        }
    }

    static void clear() {
        AURAS.clear();
    }
}
