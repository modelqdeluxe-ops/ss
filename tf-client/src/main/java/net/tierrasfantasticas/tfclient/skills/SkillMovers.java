package net.tierrasfantasticas.tfclient.skills;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
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
import net.tierrasfantasticas.tfclient.skills.SkillDefs.Cond;
import net.tierrasfantasticas.tfclient.skills.SkillDefs.Mech;
import net.tierrasfantasticas.tfclient.skills.SkillDefs.MobDef;
import net.tierrasfantasticas.tfclient.skills.SkillRuntime.Ctx;
import net.tierrasfantasticas.tfclient.skills.SkillRuntime.Tgt;
import net.tierrasfantasticas.tfclient.skills.SkillRuntime.Who;

/**
 * Lo que se mueve solo: proyectiles (projectile), misiles que persiguen (missile), tótems quietos (totem) y orbitales
 * que dan vueltas a alguien (orbital). Cada uno con sus skills al salir (onStart), en cada paso (onTick), al tocar
 * (onHit), al chocar con un bloque (onHitBlock) y al acabar (onEnd), y con un efecto que lo acompaña si es de tipo MOB.
 * Las opciones de MythicMobs que usan los packs: pegado al suelo (hugSurface + heightFromSurface: las ondas que van por
 * el suelo), salir desde el objetivo (targetIsOrigin), a quién puede tocar (hitConditions, hitTargetOnly), cada cuánto
 * puede volver a tocar al mismo (immuneDelay), cuándo pararse (stopConditions), y los orbitales con nombre de aura
 * (se quitan con auraremove y se miran con hasaura).
 */
final class SkillMovers {
    private static final List<Mover> MOVERS = new ArrayList<>();
    private static final int MAX = 1500;

    private SkillMovers() {}

    static final class Mover {
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
        String onHitBlock;
        SkillActor bullet;
        double bulletY;
        double bulletForward;
        int deathDelay;
        /** Cuándo tocó a cada uno (para immuneDelay: 0 = una sola vez). */
        final Map<Object, Long> hit = new HashMap<>();
        int immuneDelay;
        List<Cond> hitConds = List.of();
        List<Cond> stopConds = List.of();
        LivingEntity onlyTarget;
        boolean hug;
        double hugHeight;
        double climb;
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
        double rx;
        double ry;
        double rz;
        double avx;
        double avy;
        double avz;
        SkillAuras.Aura aura;

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
            boolean targetIsOrigin = SkillRuntime.bool(m.arg("false", "targetisorigin", "tio"));
            Vec3 base = targetIsOrigin ? t.pos() : fromOrigin ? SkillTargets.origin(ctx) : ctx.caster.pos();
            double syo = SkillRuntime.num(m.arg("1", "startyoffset", "syo"), 1);
            double sfo = SkillRuntime.num(m.arg("1", "startforwardoffset", "sfo"), 1);
            double sso = SkillRuntime.num(m.arg("0", "startsideoffset", "sso"), 0);
            double tyo = SkillRuntime.num(m.arg("1", "targetyoffset", "tyo"), 1);
            double tso = SkillRuntime.num(m.arg("0", "targetsideoffset", "tso"), 0);
            Vec3 target = targetIsOrigin ? t.pos().add(ctx.caster.forward().scale(0.01)) : t.pos();
            Vec3 start0 = base.add(0, syo, 0);
            Vec3 aimAt = target.add(0, tyo, 0);
            if (tso != 0) {
                Vec3 side = SkillRuntime.dir(SkillRuntime.yawOf(aimAt.subtract(start0)) + 90F, 0F);
                aimAt = aimAt.add(side.scale(tso));
            }
            Vec3 dir = targetIsOrigin ? ctx.caster.forward() : aimAt.subtract(start0);
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
            p.hug = SkillRuntime.bool(m.arg("false", "hugsurface", "hs"));
            p.hugHeight = SkillRuntime.num(m.arg("0.5", "heightfromsurface", "hfs"), 0.5);
            p.climb = SkillRuntime.num(m.arg("1", "maxclimbheight", "mch"), 1);
            if (p.hug) {
                // Pegado al suelo: va en horizontal a «hfs» del suelo
                p.dir = flat.lengthSqr() < 1e-6 ? SkillRuntime.dir(ctx.caster.yaw(), 0F) : flat;
                Vec3 g = SkillMinions.ground(p.level, p.pos, p.pos.y);
                p.pos = new Vec3(p.pos.x, g.y + p.hugHeight, p.pos.z);
            }
            if (SkillRuntime.bool(m.arg("false", "hittargetonly", "hto"))) p.onlyTarget = t.living();
            if (missile) {
                p.homing = t.living();
                p.inertia = Math.max(0.1, SkillRuntime.num(m.arg("1.5", "inertia", "in"), 1.5));
                if (p.homing == null && ctx.aim instanceof LivingEntity le) p.homing = le;
            }
            p.ctx.dir = p.dir;
            start(p, m);
        }
    }

    static void totem(Mech m, Ctx ctx, List<Tgt> targets) {
        for (Tgt t : targets) {
            if (MOVERS.size() >= MAX) return;
            Mover p = new Mover(ctx.copy(), 2, ctx.caster.level());
            common(p, m);
            p.pos = t.pos().add(0, SkillRuntime.num(m.arg("0", "yoffset", "y", "yo"), 0), 0);
            p.dir = ctx.caster.forward();
            p.step = 0;
            p.maxRange = Double.MAX_VALUE;
            p.maxTicks = (int) SkillRuntime.num(m.arg("200", "maxduration", "md", "duration", "d"), 200);
            p.ctx.dir = p.dir;
            start(p, m);
        }
    }

    static void orbital(Mech m, Ctx ctx, List<Tgt> targets) {
        String auraName = m.arg(null, "auraname", "aura", "n", "buffname");
        boolean refresh = SkillRuntime.bool(m.arg("false", "refreshduration", "rd"));
        for (Tgt t : targets) {
            if (MOVERS.size() >= MAX || t.who == null) continue;
            int duration = (int) SkillRuntime.num(m.arg("100", "duration", "d", "maxduration", "md", "ticks"), 100);
            // Con nombre de aura: si ya lo tiene, se renueva (rd) o se queda el que hay
            if (auraName != null) {
                SkillRuntime.WhoState st = SkillRuntime.stateIfAny(t.who);
                SkillAuras.Aura old = st == null ? null : st.auras.get(auraName.toLowerCase(Locale.ROOT));
                if (old != null && old.alive && old.mover != null && old.mover.alive) {
                    if (refresh) {
                        old.left = duration;
                        old.mover.maxTicks = old.mover.age + duration;
                    }
                    continue;
                }
            }
            Mover p = new Mover(ctx.copy(), 3, ctx.caster.level());
            common(p, m);
            p.center = t.who;
            p.radius = SkillRuntime.num(m.arg("4", "radius", "r"), 4);
            int points = (int) Math.max(1, SkillRuntime.num(m.arg("32", "points", "p"), 32));
            p.angleStep = Math.PI * 2 / points * (SkillRuntime.bool(m.arg("false", "reversed", "rev")) ? -1 : 1);
            p.angle = p.angleStep * SkillRuntime.num(m.arg("0", "startingpoint", "sp"), 0);
            p.oy = SkillRuntime.num(m.arg("0", "offsety", "oy", "yoffset"), 0);
            p.rx = Math.toRadians(SkillRuntime.num(m.arg("0", "rotationx", "rx"), 0));
            p.ry = Math.toRadians(SkillRuntime.num(m.arg("0", "rotationy", "ry"), 0));
            p.rz = Math.toRadians(SkillRuntime.num(m.arg("0", "rotationz", "rz"), 0));
            p.avx = Math.toRadians(SkillRuntime.num(m.arg("0", "angularvelocityx", "avx"), 0));
            p.avy = Math.toRadians(SkillRuntime.num(m.arg("0", "angularvelocityy", "avy"), 0));
            p.avz = Math.toRadians(SkillRuntime.num(m.arg("0", "angularvelocityz", "avz"), 0));
            p.maxTicks = duration;
            p.maxRange = Double.MAX_VALUE;
            p.pos = orbitPos(p);
            p.dir = Vec3.ZERO;
            if (auraName != null) p.aura = SkillAuras.forMover(p, auraName, t.who, ctx, duration);
            start(p, m);
        }
    }

    /** shoot{type=ARROW}: aquí sin flecha de verdad, como un proyectil rápido. */
    static void shoot(Mech m, Ctx ctx, List<Tgt> targets) {
        projectile(m, ctx, targets, false);
    }

    private static void common(Mover p, Mech m) {
        p.interval = (int) Math.max(1, SkillRuntime.num(m.arg("1", "interval", "i", "int"), 1));
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
        p.onHitBlock = m.arg(null, "onhitblock", "ohb", "onhitblockskill");
        p.immuneDelay = (int) SkillRuntime.num(m.arg("0", "immunedelay", "id"), 0);
        p.deathDelay = (int) SkillRuntime.num(m.arg("0", "deathdelay", "dd"), 0);
        p.bulletY = SkillRuntime.num(m.arg("0", "bulletyoffset", "byo"), 0);
        p.bulletForward = SkillRuntime.num(m.arg("0", "bulletforwardoffset", "bfo"), 0);
        p.hitConds = m.hc;
        p.stopConds = m.sc;
    }

    private static void start(Mover p, Mech m) {
        String type = m.arg("", "bullettype", "bt", "type").toUpperCase(Locale.ROOT);
        String mob = m.arg(null, "mob", "bulletmob", "bm", "mobtype", "mm");
        if (mob != null && (type.equals("MOB") || type.isEmpty())) {
            MobDef def = p.ctx.cls.mob(mob);
            if (def != null) {
                float yaw = p.dir.lengthSqr() > 0 ? SkillRuntime.yawOf(p.dir) : p.ctx.caster.yaw();
                float pitch = p.dir.lengthSqr() > 0 ? SkillRuntime.pitchOf(p.dir) : 0F;
                p.bullet = SkillRuntime.spawn(p.ctx, def, bulletPos(p), yaw, p.kind == 2 || p.kind == 3 ? 0F : pitch);
                if (p.bullet != null) p.bullet.carried = true;
            }
        }
        String onStart = m.arg(null, "onstart", "os", "onstartskill");
        if (onStart != null) run(p, onStart, List.of(Tgt.at(p.pos)), null);
        MOVERS.add(p);
    }

    private static Vec3 bulletPos(Mover p) {
        Vec3 at = p.pos.add(0, p.bulletY, 0);
        if (p.bulletForward != 0 && p.dir.lengthSqr() > 1e-6) at = at.add(p.dir.normalize().scale(p.bulletForward));
        return at;
    }

    private static Vec3 orbitPos(Mover p) {
        Vec3 c = p.center.pos();
        double t = p.age;
        Vec3 o = new Vec3(Math.cos(p.angle) * p.radius, 0, Math.sin(p.angle) * p.radius);
        o = o.xRot((float) (p.rx + p.avx * t)).yRot((float) (p.ry + p.avy * t)).zRot((float) (p.rz + p.avz * t));
        return c.add(o).add(0, p.oy, 0);
    }

    // ------------------------------------------------------------------------------------------- desde sus skills

    /** modifyprojectile{trait=VELOCITY|RADIUS|GRAVITY;action=SET|ADD|MULTIPLY;value}: cambia el proyectil que la lanzó. */
    static void modify(Mech m, Ctx ctx) {
        Mover p = ctx.mover;
        if (p == null || !p.alive) return;
        String trait = m.arg("VELOCITY", "trait", "t").toUpperCase(Locale.ROOT);
        String action = m.arg("SET", "action", "a").toUpperCase(Locale.ROOT);
        double v = SkillRuntime.num(m.arg("1", "value", "v"), 1);
        switch (trait) {
            // la velocidad va en bloques por segundo (como v= del proyectil); multiplicar, tal cual
            case "VELOCITY", "SPEED" -> p.step = apply(p.step, action, action.startsWith("MULT") ? v : v / 20.0 * p.interval);
            case "RADIUS", "HITRADIUS" -> {
                p.hr = apply(p.hr, action, v);
                p.vr = apply(p.vr, action, v);
            }
            case "GRAVITY" -> p.gravity = apply(p.gravity, action, action.equals("MULTIPLY") ? v : v / 20.0);
            default -> { }
        }
    }

    private static double apply(double now, String action, double v) {
        return switch (action) {
            case "ADD" -> now + v;
            case "MULTIPLY", "MULT" -> now * v;
            default -> v;
        };
    }

    static void endCurrent(Ctx ctx) {
        if (ctx.mover != null) end(ctx.mover);
    }

    /** setprojectiledirection @objetivo: el proyectil que la lanzó gira hacia allí. */
    static void setDirection(Ctx ctx, List<Tgt> targets) {
        Mover p = ctx.mover;
        if (p == null || !p.alive || targets.isEmpty()) return;
        Vec3 d = targets.get(0).pos().subtract(p.pos);
        if (d.lengthSqr() > 1e-6) {
            p.dir = d.normalize();
            p.ctx.dir = p.dir;
        }
    }

    // ------------------------------------------------------------------------------------------- cada tick

    static void tick() {
        if (MOVERS.isEmpty()) return;
        List<Mover> now = new ArrayList<>(MOVERS);
        for (Mover p : now) {
            if (!p.alive) continue;
            try {
                step(p);
            } catch (SkillRuntime.CancelSkill c) {
                // una skill del proyectil se paró sola
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
        if (p.aura != null && !p.aura.alive) {
            end(p);
            return;
        }
        if (!p.stopConds.isEmpty()) {
            Tgt self = Tgt.of(p.ctx.caster);
            for (Cond c : p.stopConds) {
                boolean r = SkillConds.test(c, self.who, p.ctx, p.pos);
                boolean want = !(c.action().equals("false") || c.action().equals("cancel"));
                if (r == want) {
                    end(p);
                    return;
                }
            }
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
                if (p.gravity != 0 && !p.hug) p.dir = p.dir.add(0, -p.gravity * p.interval, 0).normalize();
                Vec3 to = p.pos.add(p.dir.scale(p.step));
                if (p.hug) {
                    // Pegado al suelo: sube escalones de hasta «maxclimbheight» y baja lo que haga falta
                    Vec3 g = SkillMinions.ground(p.level, to, p.pos.y - p.hugHeight + Math.max(0, p.climb - 1));
                    double groundNow = p.pos.y - p.hugHeight;
                    if (g.y - groundNow > p.climb + 1e-3 && p.stopAtBlock) {
                        hitBlock(p);
                        return;
                    }
                    to = new Vec3(to.x, g.y + p.hugHeight, to.z);
                } else if (p.stopAtBlock) {
                    BlockHitResult hit = p.level.clip(new ClipContext(p.pos, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                            (net.minecraft.world.entity.Entity) null));
                    if (hit.getType() == HitResult.Type.BLOCK) {
                        p.pos = hit.getLocation();
                        moveBullet(p);
                        hitBlock(p);
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

    private static void hitBlock(Mover p) {
        if (p.onHitBlock != null) run(p, p.onHitBlock, List.of(Tgt.at(p.pos)), null);
        end(p);
    }

    private static void moveBullet(Mover p) {
        if (p.bullet == null || !p.bullet.alive) return;
        float yaw = p.dir.lengthSqr() > 1e-6 ? SkillRuntime.yawOf(p.dir) : p.bullet.yaw;
        float pitch = p.kind == 0 || p.kind == 1 ? (p.dir.lengthSqr() > 1e-6 ? SkillRuntime.pitchOf(p.dir) : p.bullet.pitch) : 0F;
        p.bullet.moveTo(bulletPos(p), yaw, pitch);
    }

    private static void hits(Mover p, Vec3 from) {
        if (p.onHit == null && !p.stopAtEntity) return;
        AABB box = new AABB(from, p.pos).inflate(p.hr, p.vr, p.hr);
        List<LivingEntity> found = new ArrayList<>();
        long now = SkillRuntime.clock;
        for (LivingEntity e : p.level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (!e.isAlive() || e.isSpectator() || e instanceof ArmorStand) continue;
            if (e == p.ctx.caster.entity || e == p.ctx.caster.player()) continue;
            if (p.onlyTarget != null && e != p.onlyTarget) continue;
            Long last = p.hit.get(e.getUUID());
            if (last != null && (p.immuneDelay <= 0 || now - last < p.immuneDelay)) continue;
            if (e instanceof Player ? !p.hitPlayers : !p.hitNonPlayers) continue;
            if (!SkillRuntime.canHurt(p.ctx, e)) continue;
            if (!p.hitConds.isEmpty() && !SkillTargets.passes(p.hitConds, Tgt.of(Who.of(e)), p.ctx)) continue;
            found.add(e);
        }
        found.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(from)));
        // Efectos del pack que el proyectil busca a propósito (hitConditions=[ - mythicmobtype{t=X} true ]): el
        // Nigromante tira un muñeco donde cae el disparo y otro proyectil va a por él
        if (wantsActors(p)) {
            for (SkillActor a : new ArrayList<>(SkillRuntime.ACTORS)) {
                if (!a.alive || a.level != p.level || a.carried || a.follow != null || p.hit.containsKey(a.id)) continue;
                if (!box.contains(a.pos) && !box.inflate(0.3).contains(a.pos.add(0, 0.5, 0))) continue;
                if (!SkillTargets.passes(p.hitConds, Tgt.of(a.who), p.ctx)) continue;
                p.hit.put(a.id, now);
                p.hits++;
                if (p.onHit != null) run(p, p.onHit, List.of(Tgt.of(a.who)), null);
                if (!p.alive) return;
                if (p.stopAtEntity || (p.charges > 0 && p.hits >= p.charges)) {
                    end(p);
                    return;
                }
            }
        }
        for (LivingEntity e : found) {
            p.hit.put(e.getUUID(), now);
            p.hits++;
            if (p.onHit != null) run(p, p.onHit, List.of(Tgt.of(Who.of(e))), e);
            if (!p.alive) return;
            if (p.stopAtEntity || (p.charges > 0 && p.hits >= p.charges)) {
                end(p);
                return;
            }
        }
    }

    private static boolean wantsActors(Mover p) {
        for (Cond c : p.hitConds) {
            if ((c.m().equals("mythicmobtype") || c.m().equals("mmt") || c.m().equals("mobtype")) && c.action().equals("true")) return true;
        }
        return false;
    }

    private static void run(Mover p, String skill, List<Tgt> targets, LivingEntity trigger) {
        Ctx c = p.ctx.copy();
        c.origin = p.pos;
        c.targets = targets;
        c.dir = p.dir;
        c.mover = p;
        if (trigger != null) c.trigger = Who.of(trigger);
        SkillRuntime.runMeta(c.cls.meta(skill), c);
    }

    static void end(Mover p) {
        if (!p.alive) return;
        p.alive = false;
        if (p.onEnd != null) {
            try {
                run(p, p.onEnd, List.of(Tgt.at(p.pos)), null);
            } catch (SkillRuntime.CancelSkill ignored) {
                // nada
            }
        }
        if (p.bullet != null) {
            SkillActor b = p.bullet;
            if (p.deathDelay > 0) SkillRuntime.later(p.deathDelay, b::remove);
            else b.remove();
        }
        if (p.aura != null) SkillAuras.moverEnded(p.aura);
    }

    static void clear() {
        MOVERS.clear();
    }

    /** ¿Está en el aire un sitio? (para quien lo necesite). */
    static boolean air(ServerLevel level, Vec3 p) {
        return level.getBlockState(BlockPos.containing(p)).isAir();
    }
}
