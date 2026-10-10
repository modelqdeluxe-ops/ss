package net.tierrasfantasticas.tfclient.skills;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Los esbirros: los mobs «de verdad» de los packs (lobos, caballos... con un modelo de ModelEngine encima) que en
 * MythicMobs andaban solos. Aquí son actores (no entidades), así que se les da una IA sencilla como la de un lobo
 * domesticado: siguen a su dueño, persiguen a su objetivo (el que les ponen sus skills con settarget, o el enemigo
 * más cercano si el pack no les pone ninguno) y van por el suelo. Sus ataques son sus propias skills (~onTimer), que
 * miran a ese objetivo con @target y ?targetwithin.
 */
final class SkillMinions {
    private SkillMinions() {}

    /** Distancia a la que se quedan del objetivo y del dueño. */
    private static final double REACH = 1.8;
    private static final double OWNER_NEAR = 3.5;
    private static final double OWNER_FAR = 28.0;

    static void tick(SkillActor a) {
        if (a.def == null || !a.def.living || a.follow != null || !a.alive) return;
        // Objetivo: el que le pusieron, mientras siga vivo y cerca
        if (a.target != null && (!a.target.isAlive() || a.target.isRemoved() || a.target.level() != a.level
                || a.target.position().distanceToSqr(a.pos) > a.def.followRange * a.def.followRange)) {
            a.target = null;
        }
        if (a.target == null && !setsOwnTarget(a) && a.age % 10 == 0) a.target = nearestEnemy(a);
        Entity owner = a.owner != null ? a.owner.entity : null;
        Vec3 goal = null;
        double stop = REACH;
        if (a.target != null) {
            goal = a.target.position();
        } else if (owner != null && owner.level() == a.level) {
            double d = owner.position().distanceTo(a.pos);
            if (d > OWNER_FAR) {
                a.moveTo(ground(a.level, owner.position()), a.yaw, 0F);
                walking(a, false);
                return;
            }
            if (d > OWNER_NEAR) {
                goal = owner.position();
                stop = OWNER_NEAR - 1;
            }
        }
        boolean frozen = !a.ai || hasAura(a, "noai");
        if (goal == null || frozen) {
            // quieto: que no se quede flotando si el suelo cambió
            Vec3 g = ground(a.level, a.pos);
            float yaw = a.target != null ? SkillRuntime.yawOf(a.target.position().subtract(a.pos)) : a.yaw;
            a.moveTo(g, frozen ? a.yaw : yaw, 0F);
            walking(a, false);
            return;
        }
        Vec3 to = goal.subtract(a.pos);
        Vec3 flat = new Vec3(to.x, 0, to.z);
        double dist = flat.length();
        float yaw = dist > 1e-3 ? SkillRuntime.yawOf(flat) : a.yaw;
        if (dist <= stop) {
            a.moveTo(ground(a.level, a.pos), yaw, 0F);
            walking(a, false);
            return;
        }
        // Un mob con MovementSpeed 0.3 anda unos 0.22 bloques por tick
        double step = Math.min(dist - stop + 0.05, Math.min(1.2, a.def.speed * 0.72 * a.speedMul));
        Vec3 next = a.pos.add(flat.normalize().scale(step));
        a.moveTo(ground(a.level, next, a.pos.y), yaw, 0F);
        walking(a, true);
    }

    /** ¿Sus skills ya eligen a quién atacar (settarget)? Entonces no se le pone uno solo. */
    private static boolean setsOwnTarget(SkillActor a) {
        for (SkillDefs.Mech m : a.def.mechs) if (m.m.equals("settarget") && m.t != null) return true;
        return false;
    }

    private static LivingEntity nearestEnemy(SkillActor a) {
        Vec3 c = a.pos;
        double r = Math.min(24, a.def.followRange);
        LivingEntity best = null;
        double bestD = r * r;
        SkillRuntime.Ctx ctx = new SkillRuntime.Ctx(a.cls, a.who);
        for (LivingEntity e : a.level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(c, c).inflate(r))) {
            if (!(e instanceof Enemy) || e instanceof ArmorStand || !e.isAlive()) continue;
            if (!SkillRuntime.canHurt(ctx, e)) continue;
            double d = e.position().distanceToSqr(c);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    private static boolean hasAura(SkillActor a, String name) {
        SkillRuntime.WhoState st = SkillRuntime.stateIfAny(a.who);
        if (st == null) return false;
        SkillAuras.Aura au = st.auras.get(name);
        return au != null && au.alive;
    }

    private static void walking(SkillActor a, boolean now) {
        if (a.walking == now) return;
        a.walking = now;
        a.prop("walk", now ? "1" : "0", null, null);
    }

    static Vec3 ground(ServerLevel level, Vec3 p) {
        return ground(level, p, p.y);
    }

    /**
     * El suelo bajo un punto: sube como mucho un bloque (escalones) y baja lo que haga falta (hasta 6 bloques; si no
     * hay nada, cae poco a poco).
     */
    static Vec3 ground(ServerLevel level, Vec3 p, double fromY) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double top = fromY + 1.1;
        for (int dy = 0; dy <= 8; dy++) {
            int by = (int) Math.floor(top) - dy;
            pos.set(Math.floor(p.x), by, Math.floor(p.z));
            if (!level.isLoaded(pos)) return new Vec3(p.x, fromY, p.z);
            BlockState st = level.getBlockState(pos);
            VoxelShape shape = st.getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;
            double y = by + shape.max(net.minecraft.core.Direction.Axis.Y);
            if (y <= top + 1e-3) return new Vec3(p.x, y, p.z);
        }
        return new Vec3(p.x, fromY - 0.5, p.z);
    }
}
