package net.tierrasfantasticas.tfclient.skills;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.skills.SkillDefs.Mech;
import net.tierrasfantasticas.tfclient.skills.SkillDefs.MobDef;
import net.tierrasfantasticas.tfclient.skills.SkillRuntime.Ctx;
import net.tierrasfantasticas.tfclient.skills.SkillRuntime.Tgt;
import net.tierrasfantasticas.tfclient.skills.SkillRuntime.Who;

/**
 * Lo que se mueve solo: proyectiles (projectile), misiles que persiguen (missile), tótems quietos (totem) y orbitales
 * que dan vueltas a alguien (orbital). Cada uno con sus skills al salir (onStart), en cada paso (onTick), al tocar
 * (onHit) y al acabar (onEnd), y con un efecto que lo acompaña si es de tipo MOB.
 */
final class SkillMovers {
    private static final List<Mover> MOVERS = new ArrayList<>();
    private static final int MAX = 1500;

    private SkillMovers() {}

    private static final class Mover {
        final Ctx ctx;
        final int kind; // 0 proyectil, 1 misil, 2 tótem, 3 orbital
        final ServerLevel level;
        Vec3 pos;
        Vec3 dir;
        double step;
        int interval;
        double maxRange;
        int maxTicks;
        double hr;
        double vr;
        boolean hitPlayers;
        boolean hitNonPlayers;
        boolean stopAtEntity;
        boolean stopAtBlock;
        int charges;
        int hits;
        double gravity;
        String onTick;
        String onHit;
        String onEnd;
        SkillActor bullet;
        final Set<Object> hit = new HashSet<>();
        LivingEntity homing;
        double inertia;
        int age;
        double travelled;
        boolean alive = true;
        // orbital
        Who center;
        double radius;
        double angle;
        double angleStep;
        double oy;
        double tilt;

        Mover(Ctx ctx, int kind, ServerLevel level) {
            this.ctx = ctx;
            this.kind = kind;
            this.level = level;
        }
    }

    // ------------------------------------------------------------------------------------------- lanzar

    static void projectile(Mech m, Ctx ctx, List<Tgt> targets, boolean missile) {
        for (Tgt t : targets) {
            if (MOVERS.size() >= MAX) return;
            Mover p = new Mover(ctx.copy(), missile ? 1 : 0, ctx.caster.level());
            common(p, m);
            boolean fromOrigin = SkillRuntime.bool(m.arg("false", "fromorigin", "fo"));
            Vec3 base = fromOrigin ? SkillTargets.origin(ctx) : ctx.caster.pos();
            double syo = SkillRuntime.num(m.arg("1", "startyoffset", "syo"), 1);
            double sfo = SkillRuntime.num(m.arg("1", "startforwardoffset", "sfo"), 1);
            double sso = SkillRuntime.num(m.arg("0", "startsideoffset", "sso"), 0);
            double tyo = SkillRuntime.num(m.arg("1", "targetyoffset", "tyo"), 1);
            Vec3 target = t.pos().add(0, tyo, 0);
            Vec3 start0 = base.add(0, syo, 0);
            Vec3 dir = target.subtract(start0);
            if (dir.lengthSqr() < 1e-6) dir = ctx.caster.forward();
            dir = dir.normalize();
            double ho = SkillRuntime.num(m.arg("0", "horizontaloffset", "ho"), 0);
            double vo = SkillRuntime.num(m.arg("0", "verticaloffset", "vo"), 0);
            if (ho != 0 || vo != 0) {
                dir = SkillRuntime.dir(SkillRuntime.yawOf(dir) + (float) ho, SkillRuntime.pitchOf(dir) - (float) vo);
            }
            Vec3 right = SkillRuntime.dir(SkillRuntime.yawOf(dir) + 90F, 0F);
            Vec3 flat = new Vec3(dir.x, 0, dir.z);
            flat = flat.lengthSqr() < 1e-6 ? Vec3.ZERO : flat.normalize();
            p.pos = start0.add(flat.scale(sfo)).add(right.scale(sso));
            p.dir = dir;
            double v = SkillRuntime.num(m.arg("5", "velocity", "v"), 5);
            p.step = v / 20.0 * p.interval;
            p.maxRange = SkillRuntime.num(m.arg("40", "maxrange", "mr"), 40);
            p.maxTicks = (int) SkillRuntime.num(m.arg("400", "maxduration", "md", "duration", "d"), 400);
            p.gravity = SkillRuntime.num(m.arg("0", "gravity", "g"), 0) / 20.0;
            if (missile) {
                p.homing = t.living();
                p.inertia = Math.max(0.1, SkillRuntime.num(m.arg("1.5", "inertia", "in"), 1.5));
                if (p.homing == null && ctx.aim instanceof LivingEntity le) p.homing = le;
            }
            p.ctx.dir = dir;
            start(p, m);
        }
    }

    static void totem(Mech m, Ctx ctx, List<Tgt> targets) {
        for (Tgt t : targets) {
            if (MOVERS.size() >= MAX) return;
            Mover p = new Mover(ctx.copy(), 2, ctx.caster.level());
            common(p, m);
            p.pos = t.pos().add(0, SkillRuntime.num(m.arg("0", "yoffset", "y"), 0), 0);
            p.dir = ctx.caster.forward();
            p.step = 0;
            p.maxRange = Double.MAX_VALUE;
            p.maxTicks = (int) SkillRuntime.num(m.arg("200", "maxduration", "md", "duration", "d"), 200);
            p.ctx.dir = p.dir;
            start(p, m);
        }
    }

    static void orbital(Mech m, Ctx ctx, List<Tgt> targets) {
        for (Tgt t : targets) {
            if (MOVERS.size() >= MAX || t.who == null) continue;
            Mover p = new Mover(ctx.copy(), 3, ctx.caster.level());
            common(p, m);
            p.center = t.who;
            p.radius = SkillRuntime.num(m.arg("4", "radius", "r"), 4);
            int points = (int) Math.max(1, SkillRuntime.num(m.arg("32", "points", "p"), 32));
            p.angleStep = Math.PI * 2 / points;
            p.oy = SkillRuntime.num(m.arg("0", "offsety", "oy", "yoffset"), 0);
            p.tilt = Math.toRadians(SkillRuntime.num(m.arg("0", "rotationx", "rx"), 0));
            p.maxTicks = (int) SkillRuntime.num(m.arg("100", "duration", "d", "maxduration", "md"), 100);
            p.maxRange = Double.MAX_VALUE;
            p.pos = orbitPos(p);
            p.dir = Vec3.ZERO;
            start(p, m);
        }
    }

    /** shoot{type=ARROW}: aquí sin flecha de verdad, como un proyectil rápido. */
    static void shoot(Mech m, Ctx ctx, List<Tgt> targets) {
        projectile(m, ctx, targets, false);
    }

    private static void common(Mover p, Mech m) {
        p.interval = (int) Math.max(1, SkillRuntime.num(m.arg("1", "interval", "i"), 1));
        p.hr = SkillRuntime.num(m.arg("1.25", "horizontalradius", "hitradius", "hr", "radius_h"), 1.25);
        p.vr = SkillRuntime.num(m.arg(String.valueOf(p.hr), "verticalradius", "vr"), p.hr);
        p.hitPlayers = SkillRuntime.bool(m.arg("true", "hitplayers", "hp"));
        p.hitNonPlayers = SkillRuntime.bool(m.arg("false", "hitnonplayers", "hnp"));
        p.stopAtEntity = SkillRuntime.bool(m.arg(p.kind == 2 || p.kind == 3 ? "false" : "true", "stopatentity", "se"));
        p.stopAtBlock = SkillRuntime.bool(m.arg(p.kind == 2 || p.kind == 3 ? "false" : "true", "stopatblock", "sb"));
        p.charges = (int) SkillRuntime.num(m.arg("0", "charges", "c", "maxcharges"), 0);
        p.onTick = m.arg(null, "ontick", "ot", "ontickskill");
        p.onHit = m.arg(null, "onhit", "oh", "onhitskill");
        p.onEnd = m.arg(null, "onend", "oe", "onendskill");
    }

    private static void start(Mover p, Mech m) {
        String type = m.arg("", "bullettype", "bt", "type").toUpperCase(Locale.ROOT);
        String mob = m.arg(null, "mob", "bulletmob", "bm", "mobtype", "mm");
        if (mob != null && (type.equals("MOB") || type.isEmpty())) {
            MobDef def = p.ctx.cls.mob(mob);
            if (def != null) {
                float yaw = p.dir.lengthSqr() > 0 ? SkillRuntime.yawOf(p.dir) : p.ctx.caster.yaw();
                float pitch = p.dir.lengthSqr() > 0 ? SkillRuntime.pitchOf(p.dir) : 0F;
                p.bullet = SkillRuntime.spawn(p.ctx, def, p.pos, yaw, p.kind == 2 || p.kind == 3 ? 0F : pitch);
                if (p.bullet != null) p.bullet.carried = true;
            }
        }
        String onStart = m.arg(null, "onstart", "os", "onstartskill");
        if (onStart != null) run(p, onStart, List.of(Tgt.at(p.pos)), null);
        MOVERS.add(p);
    }

    private static Vec3 orbitPos(Mover p) {
        Vec3 c = p.center.pos();
        double x = Math.cos(p.angle) * p.radius, z = Math.sin(p.angle) * p.radius;
        double y = p.oy + z * Math.sin(p.tilt);
        z = z * Math.cos(p.tilt);
        return c.add(x, y, z);
    }

    // ------------------------------------------------------------------------------------------- cada tick

    static void tick() {
        if (MOVERS.isEmpty()) return;
        List<Mover> now = new ArrayList<>(MOVERS);
        for (Mover p : now) {
            if (!p.alive) continue;
            try {
                step(p);
            } catch (Throwable t) {
                p.alive = false;
                TFClient.LOGGER.debug("TF Skills: error en un proyectil", t);
            }
        }
        MOVERS.removeIf(p -> !p.alive);
    }

    private static void step(Mover p) {
        p.age++;
        if (p.ctx.caster.actor != null && !p.ctx.caster.alive() && p.kind == 3) {
            end(p);
            return;
        }
        if (p.age % p.interval != 0) {
            if (p.age >= p.maxTicks) end(p);
            return;
        }
        Vec3 from = p.pos;
        switch (p.kind) {
            case 0, 1 -> {
                if (p.kind == 1 && p.homing != null && p.homing.isAlive()) {
                    Vec3 to = p.homing.getBoundingBox().getCenter().subtract(p.pos);
                    if (to.lengthSqr() > 1e-6) p.dir = p.dir.scale(p.inertia).add(to.normalize()).normalize();
                }
                if (p.gravity != 0) p.dir = p.dir.add(0, -p.gravity * p.interval, 0).normalize();
                Vec3 to = p.pos.add(p.dir.scale(p.step));
                if (p.stopAtBlock) {
                    BlockHitResult hit = p.level.clip(new ClipContext(p.pos, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                            (net.minecraft.world.entity.Entity) null));
                    if (hit.getType() == HitResult.Type.BLOCK) {
                        p.pos = hit.getLocation();
                        moveBullet(p);
                        end(p);
                        return;
                    }
                }
                p.travelled += p.step;
                p.pos = to;
                p.ctx.dir = p.dir;
            }
            case 3 -> {
                if (!p.center.alive()) {
                    end(p);
                    return;
                }
                p.angle += p.angleStep;
                Vec3 np = orbitPos(p);
                p.dir = np.subtract(p.pos);
                p.pos = np;
            }
            default -> { /* tótem: quieto */ }
        }
        moveBullet(p);
        if (p.onTick != null) run(p, p.onTick, List.of(Tgt.at(p.pos)), null);
        if (!p.alive) return;
        hits(p, from);
        if (!p.alive) return;
        if (p.travelled >= p.maxRange || p.age >= p.maxTicks) end(p);
    }

    private static void moveBullet(Mover p) {
        if (p.bullet == null || !p.bullet.alive) return;
        float yaw = p.dir.lengthSqr() > 1e-6 ? SkillRuntime.yawOf(p.dir) : p.bullet.yaw;
        float pitch = p.kind == 0 || p.kind == 1 ? (p.dir.lengthSqr() > 1e-6 ? SkillRuntime.pitchOf(p.dir) : p.bullet.pitch) : 0F;
        p.bullet.moveTo(p.pos, yaw, pitch);
    }

    private static void hits(Mover p, Vec3 from) {
        if (p.onHit == null && !p.stopAtEntity) return;
        AABB box = new AABB(from, p.pos).inflate(p.hr, p.vr, p.hr);
        List<LivingEntity> found = new ArrayList<>();
        for (LivingEntity e : p.level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (!e.isAlive() || e.isSpectator() || e instanceof ArmorStand) continue;
            if (e == p.ctx.caster.entity || e == p.ctx.caster.player()) continue;
            if (p.hit.contains(e.getUUID())) continue;
            if (e instanceof Player ? !p.hitPlayers : !p.hitNonPlayers) continue;
            if (!SkillRuntime.canHurt(p.ctx, e)) continue;
            found.add(e);
        }
        found.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(from)));
        for (LivingEntity e : found) {
            p.hit.add(e.getUUID());
            p.hits++;
            if (p.onHit != null) run(p, p.onHit, List.of(Tgt.of(Who.of(e))), e);
            if (p.stopAtEntity || (p.charges > 0 && p.hits >= p.charges)) {
                end(p);
                return;
            }
        }
    }

    private static void run(Mover p, String skill, List<Tgt> targets, LivingEntity trigger) {
        Ctx c = p.ctx.copy();
        c.origin = p.pos;
        c.targets = targets;
        c.dir = p.dir;
        if (trigger != null) c.trigger = Who.of(trigger);
        SkillRuntime.runMeta(c.cls.meta(skill), c);
    }

    private static void end(Mover p) {
        if (!p.alive) return;
        p.alive = false;
        if (p.onEnd != null) run(p, p.onEnd, List.of(Tgt.at(p.pos)), null);
        if (p.bullet != null) p.bullet.remove();
    }

    static void clear() {
        MOVERS.clear();
    }
}
