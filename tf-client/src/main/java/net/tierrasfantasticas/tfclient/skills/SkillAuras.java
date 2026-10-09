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
                if (a.onTick != null && a.age % a.interval == 0) SkillRuntime.runMeta(a.ctx.cls.meta(a.onTick), auraCtx(a));
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
        if (a.onEnd != null) SkillRuntime.runMeta(a.ctx.cls.meta(a.onEnd), auraCtx(a));
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
