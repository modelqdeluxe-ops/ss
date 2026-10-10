package net.tierrasfantasticas.tfclient.skills;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.tierrasfantasticas.tfclient.skills.SkillDefs.Mech;
import net.tierrasfantasticas.tfclient.skills.SkillRuntime.Ctx;
import net.tierrasfantasticas.tfclient.skills.SkillRuntime.Tgt;

/** Empujes, saltos, embestidas, teletransportes y aturdimientos (con las mismas escalas que MythicMobs). */
final class SkillMotion {
    private SkillMotion() {}

    private static void push(Entity e, Vec3 motion) {
        double max = 6.0;
        motion = new Vec3(clamp(motion.x, max), clamp(motion.y, max), clamp(motion.z, max));
        e.setDeltaMovement(motion);
        e.hurtMarked = true;
        if (motion.y > 0 && e instanceof ServerPlayer) SkillServer.noFall(e.getUUID(), 100);
        e.fallDistance = 0;
    }

    private static double clamp(double v, double m) {
        return Math.max(-m, Math.min(m, v));
    }

    /** throw{velocity;velocityY}: lanza al objetivo lejos de quien lanza (las velocidades van entre 10, como allí). */
    static void throwAway(Mech m, Ctx ctx, List<Tgt> targets) {
        double v = SkillRuntime.num(m.arg("1", "velocity", "v"), 1) / 10.0;
        double vy = SkillRuntime.num(m.arg("1", "velocityy", "vy"), 1) / 10.0;
        Vec3 from = SkillTargets.origin(ctx);
        if (ctx.caster.entity != null || ctx.caster.actor != null) from = ctx.caster.pos();
        for (Tgt t : targets) {
            Entity e = t.entity();
            if (e == null || !movable(ctx, e)) continue;
            Vec3 away = new Vec3(e.getX() - from.x, 0, e.getZ() - from.z);
            away = away.lengthSqr() < 1e-6 ? Vec3.ZERO : away.normalize().scale(v);
            push(e, new Vec3(away.x, vy, away.z));
        }
    }

    static void pull(Mech m, Ctx ctx, List<Tgt> targets) {
        double v = SkillRuntime.num(m.arg("1", "velocity", "v"), 1) / 10.0;
        // to=true: hacia el origen de la skill (el centro del remolino), no hacia quien lanza
        Vec3 to = SkillRuntime.bool(m.arg("false", "toorigin", "to")) ? SkillTargets.origin(ctx) : ctx.caster.pos();
        for (Tgt t : targets) {
            Entity e = t.entity();
            if (e == null || !movable(ctx, e)) continue;
            Vec3 d = to.subtract(e.position());
            double dist = d.length();
            if (dist < 0.5) continue;
            Vec3 motion = d.normalize().scale(v * Math.max(1, Math.min(4, dist / 2)));
            push(e, new Vec3(motion.x, Math.max(0.1, motion.y + 0.15), motion.z));
        }
    }

    /** leap{velocity}: quien lanza sale disparado hacia el objetivo (100 = un bloque por tick). */
    static void leap(Mech m, Ctx ctx, List<Tgt> targets) {
        double v = SkillRuntime.num(m.arg("100", "velocity", "v"), 100) / 100.0;
        Entity self = ctx.caster.entity;
        for (Tgt t : targets) {
            Vec3 d = t.pos().subtract(ctx.caster.pos());
            if (d.lengthSqr() < 1e-6) continue;
            Vec3 motion = d.normalize().scale(Math.min(4, v));
            if (self != null) push(self, motion);
            else if (ctx.caster.actor != null) moveActor(ctx.caster.actor, motion);
            break;
        }
    }

    /** lunge{velocity;velocityY}: embestida hacia el objetivo. */
    static void lunge(Mech m, Ctx ctx, List<Tgt> targets) {
        double v = SkillRuntime.num(m.arg("1", "velocity", "v"), 1);
        double vy = SkillRuntime.num(m.arg("0", "velocityy", "vy"), 0);
        Entity self = ctx.caster.entity;
        for (Tgt t : targets) {
            Vec3 d = t.pos().subtract(ctx.caster.pos());
            d = new Vec3(d.x, 0, d.z);
            if (d.lengthSqr() < 1e-6) d = SkillRuntime.dir(ctx.caster.yaw(), 0F);
            Vec3 motion = d.normalize().scale(Math.min(4, v)).add(0, vy, 0);
            if (self != null) push(self, motion);
            else if (ctx.caster.actor != null) moveActor(ctx.caster.actor, motion);
            break;
        }
    }

    static void jump(Mech m, Ctx ctx, List<Tgt> targets) {
        double v = SkillRuntime.num(m.arg("1", "velocity", "v"), 1);
        for (Tgt t : targets) {
            Entity e = t.entity();
            if (e != null) push(e, new Vec3(e.getDeltaMovement().x, v, e.getDeltaMovement().z));
        }
    }

    static void propel(Mech m, Ctx ctx, List<Tgt> targets) {
        double v = SkillRuntime.num(m.arg("1", "velocity", "v"), 1) / 10.0;
        Entity self = ctx.caster.entity;
        if (self == null) return;
        for (Tgt t : targets) {
            Vec3 d = t.pos().subtract(self.position());
            if (d.lengthSqr() < 1e-6) continue;
            push(self, self.getDeltaMovement().add(d.normalize().scale(v)));
            break;
        }
    }

    /** velocity{mode=set|add|multiply;x;y;z}. */
    static void velocity(Mech m, Ctx ctx, List<Tgt> targets) {
        String mode = m.arg("set", "mode", "m").toLowerCase(Locale.ROOT);
        double x = SkillRuntime.num(m.arg("0", "velocityx", "vx", "x"), 0);
        double y = SkillRuntime.num(m.arg("0", "velocityy", "vy", "y"), 0);
        double z = SkillRuntime.num(m.arg("0", "velocityz", "vz", "z"), 0);
        boolean relative = SkillRuntime.bool(m.arg("false", "relative", "r"));
        for (Tgt t : targets) {
            Entity e = t.entity();
            if (e == null || !movable(ctx, e)) continue;
            Vec3 add = new Vec3(x, y, z);
            if (relative) {
                Vec3 f = SkillRuntime.dir(e.getYRot(), 0F), r = SkillRuntime.dir(e.getYRot() + 90F, 0F);
                add = f.scale(z).add(r.scale(x)).add(0, y, 0);
            }
            Vec3 now = e.getDeltaMovement();
            Vec3 out = switch (mode) {
                case "add" -> now.add(add);
                case "multiply", "mult" -> new Vec3(now.x * x, now.y * y, now.z * z);
                case "remove" -> Vec3.ZERO;
                default -> add;
            };
            push(e, out);
        }
    }

    /** teleport: quien lanza va al objetivo (con un poco de desvío si lo pide), sin quedar dentro de un bloque. */
    static void teleport(Mech m, Ctx ctx, List<Tgt> targets) {
        double sh = SkillRuntime.num(m.arg("0", "spreadh", "sh"), 0);
        double sv = SkillRuntime.num(m.arg("0", "spreadv", "sv"), 0);
        for (Tgt t : targets) {
            if (t.who != null && t.who.same(ctx.caster)) continue;
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            Vec3 to = t.pos().add((rnd.nextDouble() * 2 - 1) * sh, rnd.nextDouble() * sv, (rnd.nextDouble() * 2 - 1) * sh);
            if (ctx.caster.actor != null) {
                ctx.caster.actor.moveTo(to, ctx.caster.actor.yaw, ctx.caster.actor.pitch);
                return;
            }
            Entity e = ctx.caster.entity;
            if (e == null) return;
            ServerLevel level = (ServerLevel) e.level();
            to = safe(level, to, e);
            if (to == null) return;
            if (e instanceof ServerPlayer p) {
                p.teleportTo(level, to.x, to.y, to.z, p.getYRot(), p.getXRot());
                SkillServer.noFall(p.getUUID(), 40);
            } else {
                e.teleportTo(to.x, to.y, to.z);
            }
            e.fallDistance = 0;
            return;
        }
    }

    /** Un sitio libre cerca (sube hasta 3 bloques si está dentro de algo), o null. */
    private static Vec3 safe(ServerLevel level, Vec3 to, Entity e) {
        if (!level.isLoaded(BlockPos.containing(to))) return null;
        for (int up = 0; up <= 3; up++) {
            Vec3 p = to.add(0, up, 0);
            if (level.noCollision(e, e.getBoundingBox().move(p.subtract(e.position())))) return p;
        }
        return null;
    }

    static void look(Mech m, Ctx ctx, List<Tgt> targets) {
        if (targets.isEmpty()) return;
        Vec3 d = targets.get(0).pos().subtract(ctx.caster.eye());
        if (d.lengthSqr() < 1e-6) return;
        if (ctx.caster.actor != null) {
            SkillActor a = ctx.caster.actor;
            a.moveTo(a.pos, SkillRuntime.yawOf(d), a.pitch); // un soporte gira entero; la cabeza no se inclina
        }
    }

    /** stun{duration}: no se puede mover ni saltar (ni pegar fuerte) un rato. */
    static void stun(Mech m, Ctx ctx, List<Tgt> targets) {
        int d = (int) SkillRuntime.num(m.arg("40", "duration", "d", "ticks", "t"), 40);
        for (Tgt t : targets) {
            LivingEntity le = t.living();
            if (le == null) continue;
            boolean self = le == ctx.caster.entity || le == ctx.caster.player();
            if (!self && !SkillRuntime.canHurt(ctx, le)) continue;
            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, d, 9, false, false, false));
            le.addEffect(new MobEffectInstance(MobEffects.JUMP, d, 200, false, false, false));
            if (!self) le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, d, 4, false, false, false));
            le.setDeltaMovement(0, Math.min(0, le.getDeltaMovement().y), 0);
            le.hurtMarked = true;
        }
    }

    /**
     * directionalvelocity{yaw;pitch;velocity;mode}: empuja a los objetivos hacia un lado concreto (el giro de quien
     * lanza ± algo). mode=MINIMUM: solo si ahora van más despacio que eso.
     */
    static void directional(Mech m, Ctx ctx, List<Tgt> targets) {
        float yaw = (float) SkillRuntime.num(m.arg(String.valueOf(ctx.caster.yaw()), "yaw", "y"), ctx.caster.yaw());
        float pitch = (float) SkillRuntime.num(m.arg("0", "pitch", "p"), 0);
        double v = SkillRuntime.num(m.arg("1", "velocity", "v"), 1);
        String mode = m.arg("SET", "mode", "m").toUpperCase(Locale.ROOT);
        Vec3 add = SkillRuntime.dir(yaw, pitch).scale(v);
        for (Tgt t : targets) {
            Entity e = t.entity();
            if (e == null || !movable(ctx, e)) continue;
            Vec3 now = e.getDeltaMovement();
            Vec3 out = switch (mode) {
                case "ADD" -> now.add(add);
                case "MINIMUM", "MIN" -> now.lengthSqr() >= add.lengthSqr() ? now : add;
                default -> add;
            };
            push(e, out);
        }
    }

    /** recoil{r;pitch}: la cámara del jugador da un tirón (la del arma al disparar). */
    static void recoil(Mech m, Ctx ctx, List<Tgt> targets) {
        float pitch = (float) SkillRuntime.num(m.arg("-2", "pitch", "p"), -2);
        for (Tgt t : targets) {
            if (t.entity() instanceof ServerPlayer p) {
                SkillNet.toPlayer(p, new SkillNet.Fx(6, "", 0, 0, 0, 0, pitch, 0, 0, 0, 0, null, 0, 0, 0, 0));
            }
        }
    }

    /**
     * chain{bounces;bounceRadius;bounceDelay;hitPlayers;hitNonPlayers;onBounce}: salta del primer objetivo al más
     * cercano que no haya tocado aún, cada «bounceDelay» ticks, lanzando onBounce en cada uno (el rayo en cadena).
     */
    static void chain(Mech m, Ctx ctx, List<Tgt> targets) {
        int bounces = (int) Math.max(1, Math.min(32, SkillRuntime.num(m.arg("3", "bounces", "b"), 3)));
        double radius = SkillRuntime.num(m.arg("5", "bounceradius", "br", "radius", "r"), 5);
        int delay = (int) Math.max(0, SkillRuntime.num(m.arg("2", "bouncedelay", "bd"), 2));
        boolean players = SkillRuntime.bool(m.arg("true", "hitplayers", "hp"));
        boolean others = SkillRuntime.bool(m.arg("true", "hitnonplayers", "hnp"));
        SkillDefs.Meta onBounce = ctx.cls.meta(m.arg(null, "onbounce", "ob", "onbounceskill"));
        LivingEntity first = null;
        for (Tgt t : targets) if (t.living() != null && SkillRuntime.canHurt(ctx, t.living())) {
            first = t.living();
            break;
        }
        if (first == null || onBounce == null) return;
        java.util.Set<LivingEntity> done = new java.util.HashSet<>();
        bounce(ctx, onBounce, first, done, bounces, radius, delay, players, others, null);
    }

    private static void bounce(Ctx ctx, SkillDefs.Meta skill, LivingEntity at, java.util.Set<LivingEntity> done, int left,
                               double radius, int delay, boolean players, boolean others, LivingEntity from) {
        if (at == null || !at.isAlive() || left <= 0) return;
        done.add(at);
        Ctx c = ctx.copy();
        c.targets = List.of(Tgt.of(SkillRuntime.Who.of(at)));
        c.origin = from != null ? from.position() : ctx.caster.pos();
        c.trigger = SkillRuntime.Who.of(at);
        SkillRuntime.runMeta(skill, c);
        SkillRuntime.later(Math.max(1, delay), () -> {
            LivingEntity next = null;
            double best = radius * radius;
            for (LivingEntity e : at.level().getEntitiesOfClass(LivingEntity.class, at.getBoundingBox().inflate(radius))) {
                if (done.contains(e) || !e.isAlive() || !SkillRuntime.canHurt(ctx, e)) continue;
                if (e instanceof net.minecraft.world.entity.player.Player ? !players : !others) continue;
                double d = e.distanceToSqr(at);
                if (d < best) {
                    best = d;
                    next = e;
                }
            }
            bounce(ctx, skill, next, done, left - 1, radius, delay, players, others, at);
        });
    }

    /** No se empuja a quien la skill no puede tocar (aliados, mascotas, jugadores sin PvP), salvo a uno mismo. */
    private static boolean movable(Ctx ctx, Entity e) {
        if (e == ctx.caster.entity || e == ctx.caster.player()) return true;
        return SkillRuntime.canHurt(ctx, e);
    }

    private static void moveActor(SkillActor a, Vec3 motion) {
        a.moveTo(a.pos.add(motion), a.yaw, a.pitch);
    }
}
