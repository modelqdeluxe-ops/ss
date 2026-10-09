package net.tierrasfantasticas.tfclient.skills;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.tierrasfantasticas.tfclient.skills.SkillDefs.Mech;
import net.tierrasfantasticas.tfclient.skills.SkillRuntime.Ctx;
import net.tierrasfantasticas.tfclient.skills.SkillRuntime.Tgt;
import net.tierrasfantasticas.tfclient.skills.SkillRuntime.Who;

/** Los @objetivos de MythicMobs que usan los packs. */
final class SkillTargets {
    private SkillTargets() {}

    static List<Tgt> resolve(Mech m, Ctx ctx) {
        String t = m.t;
        double yOff = SkillRuntime.num(m.targetArg("0", "y", "yoffset", "yo"), 0);
        List<Tgt> out = switch (t) {
            case "self", "caster", "s", "boss", "mob" -> List.of(Tgt.of(ctx.caster));
            case "owner" -> who(ctx.caster.owner() != null ? ctx.caster.owner() : ctx.caster);
            case "parent", "summoner" -> who(ctx.caster.owner());
            case "trigger" -> who(ctx.trigger != null ? ctx.trigger : ctx.caster);
            case "target", "t", "targetedtarget", "tt", "targetedentity" -> target(ctx);
            case "origin", "o", "source" -> List.of(Tgt.at(origin(ctx).add(0, yOff, 0)));
            case "selflocation", "casterlocation", "sl", "bosslocation", "moblocation" ->
                    List.of(Tgt.at(ctx.caster.pos().add(0, yOff, 0)));
            case "selfeyelocation", "eyelocation", "casterseyelocation", "boss_eye", "sel" ->
                    List.of(Tgt.at(ctx.caster.eye().add(0, yOff, 0)));
            case "targetlocation", "tl", "targetloc", "targetedlocation", "targetedloc" -> targetLocation(m, ctx, yOff);
            case "forward", "f" -> List.of(Tgt.at(forward(m, ctx, ctx.caster.pos(), ctx.caster.yaw(), ctx.caster.pitch(), yOff)));
            case "projectileforward", "pf" -> {
                Vec3 d = ctx.dir != null ? ctx.dir : ctx.caster.forward();
                double f = SkillRuntime.num(m.targetArg("1", "f", "forward"), 1);
                yield List.of(Tgt.at(origin(ctx).add(d.normalize().scale(f)).add(0, yOff, 0)));
            }
            case "entitiesnearorigin", "eno", "livingentitiesnearorigin", "leno" ->
                    entities(ctx, origin(ctx), radius(m), false, false, m);
            case "playersnearorigin", "pno" -> entities(ctx, origin(ctx), radius(m), true, false, m);
            case "entitiesinradius", "eir", "livingentitiesinradius", "leir", "livinginradius", "allinradius", "air",
                    "entitiesinring", "eirr" -> entities(ctx, ctx.caster.pos(), radius(m), false, false, m);
            case "mobsinradius", "mir" -> entities(ctx, ctx.caster.pos(), radius(m), false, true, m);
            case "playersinradius", "pir", "playersinworld", "world", "pinr" -> entities(ctx, ctx.caster.pos(), radius(m), true, false, m);
            case "nearestplayer", "np" -> nearest(entities(ctx, ctx.caster.pos(), radius(m), true, false, m), ctx.caster.pos());
            case "ring" -> ring(m, ctx, yOff);
            case "circle" -> ring(m, ctx, yOff);
            case "randomlocationsnearcaster", "rlnc", "randomlocationsnearorigin", "rlno" -> random(m, ctx, yOff, t.endsWith("origin") || t.equals("rlno"));
            case "modelpart", "mp" -> SkillModels.modelPart(m, ctx, yOff);
            case "location", "l" -> location(m, ctx);
            case "spawnlocation" -> List.of(Tgt.at(ctx.caster.pos()));
            default -> {
                SkillRuntime.warn("objetivo @" + t);
                yield List.of(Tgt.of(ctx.caster));
            }
        };
        // limit= y sort=: los más cercanos primero
        int limit = (int) SkillRuntime.num(m.targetArg("0", "limit"), 0);
        if (limit > 0 && out.size() > limit) {
            Vec3 c = ctx.caster.pos();
            List<Tgt> sorted = new ArrayList<>(out);
            sorted.sort(Comparator.comparingDouble(x -> x.pos().distanceToSqr(c)));
            out = sorted.subList(0, limit);
        }
        return out;
    }

    private static List<Tgt> who(Who w) {
        return w == null ? List.of() : List.of(Tgt.of(w));
    }

    static Vec3 origin(Ctx ctx) {
        return ctx.origin != null ? ctx.origin : ctx.caster.pos();
    }

    /** @target: la entidad a la que apuntaba el jugador; en un actor, la de su dueño; si no, la heredada. */
    private static List<Tgt> target(Ctx ctx) {
        if (ctx.aim != null && ctx.aim.isAlive()) return List.of(Tgt.of(Who.of(ctx.aim)));
        if (ctx.targets != null) {
            for (Tgt t : ctx.targets) if (t.who != null && t.who.entity != null && !t.who.same(ctx.caster)) return List.of(t);
        }
        return List.of();
    }

    /** @targetlocation: donde está @target o, sin él, el punto al que mira (como MythicLib con las skills de jugador). */
    private static List<Tgt> targetLocation(Mech m, Ctx ctx, double yOff) {
        List<Tgt> t = target(ctx);
        if (!t.isEmpty()) return List.of(Tgt.at(t.get(0).pos().add(0, yOff, 0)));
        if (ctx.targets != null) {
            for (Tgt x : ctx.targets) if (x.at != null) return List.of(Tgt.at(x.at.add(0, yOff, 0)));
        }
        Who c = ctx.caster;
        if (c.entity instanceof ServerPlayer p) {
            Vec3 eye = p.getEyePosition();
            Vec3 end = eye.add(p.getViewVector(1F).scale(24));
            var hit = p.level().clip(new net.minecraft.world.level.ClipContext(eye, end, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, p));
            Vec3 at = hit.getLocation();
            return List.of(Tgt.at(new Vec3(at.x, Math.floor(at.y + 0.01), at.z).add(0, yOff, 0)));
        }
        return List.of(Tgt.at(c.pos().add(c.forward().scale(8)).add(0, yOff, 0)));
    }

    /** @forward{f=distancia;y;sideoffset;lockpitch}: delante de quien lanza, en la dirección a la que mira. */
    static Vec3 forward(Mech m, Ctx ctx, Vec3 base, float yaw, float pitch, double yOff) {
        double f = SkillRuntime.num(m.targetArg("5", "f", "forward", "amount", "a"), 5);
        double side = SkillRuntime.num(m.targetArg("0", "sideoffset", "so", "side", "s"), 0);
        boolean lock = SkillRuntime.bool(m.targetArg("false", "lockpitch", "lp")) || ctx.caster.actor != null;
        Vec3 d = SkillRuntime.dir(yaw, lock ? 0F : pitch);
        Vec3 right = SkillRuntime.dir(yaw + 90F, 0F);
        Vec3 from = base;
        if (!lock && ctx.caster.entity != null) from = base.add(0, ctx.caster.entity.getEyeHeight() * 0.0, 0);
        return from.add(d.scale(f)).add(right.scale(side)).add(0, yOff, 0);
    }

    private static double radius(Mech m) {
        return Math.min(64, SkillRuntime.num(m.targetArg("5", "r", "radius"), 5));
    }

    /** Entidades vivas en el radio (sin quien lanza; los actores no son entidades). */
    static List<Tgt> entities(Ctx ctx, Vec3 c, double r, boolean playersOnly, boolean mobsOnly, Mech m) {
        ServerLevel level = ctx.caster.level();
        AABB box = new AABB(c.x - r, c.y - r, c.z - r, c.x + r, c.y + r, c.z + r);
        List<Tgt> out = new ArrayList<>();
        String ignore = m.targetArg("", "ignore").toLowerCase(Locale.ROOT);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (!e.isAlive() || e.isSpectator() || e instanceof ArmorStand) continue;
            if (ctx.caster.entity == e) continue;
            if (playersOnly && !(e instanceof Player)) continue;
            if (mobsOnly && e instanceof Player) continue;
            if (e.position().distanceToSqr(c) > r * r && e.getBoundingBox().getCenter().distanceToSqr(c) > r * r) continue;
            if (!ignore.isEmpty() && ignore.contains("players") && e instanceof Player) continue;
            out.add(Tgt.of(Who.of(e)));
        }
        // Los efectos invocados también están ahí (en MythicMobs eran mobs): los packs los buscan para quitarlos o
        // moverlos (con ?mythicmobtype)
        if (!playersOnly) {
            for (SkillActor a : SkillRuntime.ACTORS) {
                if (!a.alive || a.level != level || a.follow != null || ctx.caster.actor == a) continue;
                if (a.pos.distanceToSqr(c) <= r * r) out.add(Tgt.of(a.who));
            }
        }
        return out;
    }

    private static List<Tgt> nearest(List<Tgt> list, Vec3 c) {
        return list.stream().min(Comparator.comparingDouble(t -> t.pos().distanceToSqr(c))).map(List::of).orElse(List.of());
    }

    private static List<Tgt> ring(Mech m, Ctx ctx, double yOff) {
        double r = SkillRuntime.num(m.targetArg("5", "radius", "r"), 5);
        int points = (int) Math.max(1, Math.min(128, SkillRuntime.num(m.targetArg("8", "points", "p"), 8)));
        Vec3 c = ctx.caster.pos();
        float yaw = ctx.caster.yaw();
        List<Tgt> out = new ArrayList<>();
        for (int i = 0; i < points; i++) {
            Vec3 d = SkillRuntime.dir(yaw + 360F * i / points, 0F);
            out.add(Tgt.at(c.add(d.scale(r)).add(0, yOff, 0)));
        }
        return out;
    }

    private static List<Tgt> random(Mech m, Ctx ctx, double yOff, boolean fromOrigin) {
        double r = SkillRuntime.num(m.targetArg("5", "radius", "r"), 5);
        int amount = (int) Math.max(1, Math.min(64, SkillRuntime.num(m.targetArg("1", "amount", "a"), 1)));
        Vec3 c = fromOrigin ? origin(ctx) : ctx.caster.pos();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        List<Tgt> out = new ArrayList<>();
        for (int i = 0; i < amount; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = Math.sqrt(rnd.nextDouble()) * r;
            out.add(Tgt.at(c.add(Math.cos(a) * d, yOff, Math.sin(a) * d)));
        }
        return out;
    }

    private static List<Tgt> location(Mech m, Ctx ctx) {
        String c = m.targetArg(null, "c", "coords", "coordinates");
        if (c == null) return List.of(Tgt.at(ctx.caster.pos()));
        String[] p = c.split(",");
        if (p.length < 3) return List.of(Tgt.at(ctx.caster.pos()));
        return List.of(Tgt.at(new Vec3(SkillRuntime.num(p[0], 0), SkillRuntime.num(p[1], 0), SkillRuntime.num(p[2], 0))));
    }

    static boolean isEntity(Tgt t) {
        Entity e = t.entity();
        return e != null;
    }
}
