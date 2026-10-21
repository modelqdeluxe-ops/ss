package net.tierrasfantasticas.tfclient.skills;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
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
            case "self", "caster", "s", "c", "boss", "mob" -> List.of(Tgt.of(ctx.caster));
            case "owner" -> who(ctx.caster.owner() != null ? ctx.caster.owner() : ctx.caster);
            case "parent", "summoner" -> who(ctx.caster.owner());
            case "trigger" -> who(ctx.trigger != null ? ctx.trigger : ctx.caster);
            case "target", "t", "targetedentity", "targeted" -> target(ctx);
            // @ThreatTable (@TT en MythicMobs): en un jefe, todos los de su tabla de amenaza. @RandomThreatTarget (@RTT):
            // uno de ellos al azar; @RandomThreatTargetLocation (@RTTL): dónde está
            case "threattable", "threattableplayers", "ttp", "randomthreattarget", "rtt", "randomthreattargetlocation", "rttl" ->
                    threat(ctx, t, yOff);
            case "triggerlocation", "triggerloc" -> {
                Who tr = ctx.trigger != null ? ctx.trigger : ctx.caster;
                yield List.of(Tgt.at(tr.pos().add(0, yOff, 0)));
            }
            case "playersonserver", "server", "everyone" -> {
                List<Tgt> all = new ArrayList<>();
                for (var p : ctx.caster.level().getServer().getPlayerList().getPlayers()) {
                    if (p.level() == ctx.caster.level() && !p.isSpectator()) all.add(Tgt.of(Who.of(p)));
                }
                yield all;
            }
            // En un jefe, @TT es su tabla de amenaza (MythicMobs); en las skills de clase, el objetivo heredado
            case "tt" -> ctx.host != null && ctx.caster.entity != null && SkillMobs.host(ctx.caster.entity) != null
                    ? threat(ctx, "threattable", yOff) : inherited(ctx);
            // @TargetedTarget: el objetivo heredado (skill{s=X} @EntitiesNearOrigin → dentro de X, cada uno de ellos), tal
            // cual, también el lanzador si es a él a quien se pasó (MythicMobs: skill{s=X} @self → dentro, él mismo; así
            // asigna el panel de NPC una ruta al caminante, NPCS.md §3)
            case "targetedtarget" -> ctx.targets != null && !ctx.targets.isEmpty() ? new ArrayList<>(ctx.targets) : target(ctx);
            case "origin", "o", "source" -> List.of(Tgt.at(offset(m, ctx, origin(ctx)).add(0, yOff, 0)));
            case "selflocation", "casterlocation", "sl", "bosslocation", "moblocation" ->
                    List.of(Tgt.at(offset(m, ctx, ctx.caster.pos()).add(0, yOff, 0)));
            case "selfeyelocation", "eyelocation", "casterseyelocation", "boss_eye", "sel", "se" -> {
                double fo = SkillRuntime.num(m.targetArg("0", "fo", "forwardoffset", "f"), 0);
                Vec3 eye = ctx.caster.eye().add(0, yOff, 0);
                yield List.of(Tgt.at(fo == 0 ? eye : eye.add(ctx.caster.forward().scale(fo))));
            }
            case "targetlocation", "tl", "targetloc", "targetedlocation", "targetedloc" -> targetLocation(m, ctx, yOff);
            case "forward", "f" -> {
                // ofowner=true: delante del dueño (los efectos que se quedan pegados al jugador: alas, cargas...)
                Who src = SkillRuntime.bool(m.targetArg("false", "ofowner", "oo")) && ctx.caster.owner() != null
                        ? ctx.caster.owner() : ctx.caster;
                yield List.of(Tgt.at(forward(m, src, src.pos(), src.yaw(), src.pitch(), yOff)));
            }
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
            // @MobsInRadius{r;types} (@MIR, @Mobs): con tipos, los mobs y efectos del pack de esos tipos (también los suyos)
            case "mobsinradius", "mir", "mobs" -> m.targetArg(null, "types", "type", "t", "mobtypes") != null
                    ? typed(entities(ctx, ctx.caster.pos(), radius(m), false, false, m), m)
                    : entities(ctx, ctx.caster.pos(), radius(m), false, true, m);
            case "playersinradius", "pir", "playersinworld", "world", "pinr" -> entities(ctx, ctx.caster.pos(), radius(m), true, false, m);
            case "nearestplayer", "np" -> nearest(entities(ctx, ctx.caster.pos(), radius(m), true, false, m), ctx.caster.pos());
            case "ring" -> ring(m, ctx, yOff);
            case "circle" -> ring(m, ctx, yOff);
            case "randomlocationsnearcaster", "rlnc", "randomlocationsnearorigin", "rlno" -> random(m, ctx, yOff, t.endsWith("origin") || t.equals("rlno"));
            case "modelpart", "mp" -> SkillModels.modelPart(m, ctx, yOff);
            case "location", "l" -> location(m, ctx);
            // @pin{name} (MythicMobs, /mm pin): el sitio con ese nombre, si está en este mundo (los NPC de escenas que
            // andan entre sus puntos; los pone el panel de NPC: SkillSaved)
            case "pin" -> {
                Vec3 p = SkillSaved.pinIn(m.targetArg(null, "name", "n", "pin", "p"), ctx.caster.level());
                yield p == null ? List.of() : List.of(Tgt.at(p.add(0, yOff, 0)));
            }
            case "spawnlocation", "casterspawnlocation", "spawn" -> {
                // Donde apareció (un jefe: su grieta); un efecto, donde está
                SkillMobs.Host h = ctx.caster.entity == null ? null : SkillMobs.host(ctx.caster.entity);
                yield List.of(Tgt.at((h != null ? h.spawnPos() : ctx.caster.pos()).add(0, yOff, 0)));
            }
            case "ownerlocation" -> {
                Who o = ctx.caster.owner() != null ? ctx.caster.owner() : ctx.caster;
                yield List.of(Tgt.at(o.pos().add(0, yOff, 0)));
            }
            case "children", "child", "summons" -> children(ctx, m.targetArg("", "target", "t", "type").toLowerCase(Locale.ROOT));
            // @FloorOfTarget: el suelo bajo @target; @FloorOfTargets: bajo cada objetivo heredado
            case "flooroftarget", "fot" -> {
                List<Tgt> tg = target(ctx);
                if (tg.isEmpty()) yield List.of();
                Vec3 p = tg.get(0).pos();
                yield List.of(Tgt.at(SkillMinions.ground(ctx.caster.level(), p).add(0, yOff, 0)));
            }
            case "flooroftargets", "floorsoftarget", "floorsoftargets" -> {
                List<Tgt> tg = ctx.targets != null && !ctx.targets.isEmpty() ? ctx.targets : target(ctx);
                List<Tgt> floors = new ArrayList<>();
                for (Tgt x : tg) floors.add(Tgt.at(SkillMinions.ground(ctx.caster.level(), x.pos()).add(0, yOff, 0)));
                yield floors;
            }
            // @PlayersInRing{min;max}: jugadores entre min y max bloques (el muro de madera de Astwood los empuja)
            case "playersinring", "pring" -> {
                double min = SkillRuntime.num(m.targetArg("0", "min", "minradius", "minr"), 0);
                double max = SkillRuntime.num(m.targetArg("5", "max", "maxradius", "maxr", "r"), 5);
                List<Tgt> ring = new ArrayList<>();
                Vec3 c = ctx.caster.pos();
                for (Tgt x : entities(ctx, c, max, true, false, m)) {
                    double d = Math.sqrt(x.pos().distanceToSqr(c));
                    if (d >= min && d <= max) ring.add(x);
                }
                yield ring;
            }
            // @ModelPassengers{p}: quien va en el asiento del modelo (el jugador agarrado)
            case "modelpassengers", "mpassengers" -> {
                SkillMobs.Host h = ctx.caster.entity == null ? null : SkillMobs.host(ctx.caster.entity);
                List<Tgt> riders = new ArrayList<>();
                if (h != null && SkillMobs.world != null) {
                    for (LivingEntity e : SkillMobs.world.seated(h, m.targetArg(null, "p", "pbone", "seat", "part"))) riders.add(Tgt.of(Who.of(e)));
                }
                yield riders;
            }
            // Objetos en el suelo: aquí no se mueven (solo apartaba los que caían dentro del cofre)
            case "itemsinradius", "iir" -> List.of();
            case "mountedmodel", "mounted" -> {
                SkillActor a = ctx.caster.entity == null ? null : SkillModels.mountedBy(ctx.caster.entity);
                yield a == null ? List.of() : List.of(Tgt.of(a.who));
            }
            case "mobsnearorigin", "mno" -> typed(entities(ctx, origin(ctx), radius(m), false, true, m), m);
            case "entitiesinworld", "eiw", "livingentitiesinworld" -> entities(ctx, ctx.caster.pos(), 128, false, false, m);
            case "entitiesinringnearorigin", "eirno", "erno" -> ringNear(ctx, origin(ctx), m);
            case "entitiesincone", "eic", "livingentitiesincone", "livingincone", "leic" -> cone(ctx, m);
            // (1.3.82) packs nuevos
            case "ringaroundorigin", "ringorigin", "rao" -> ringAround(m, ctx, origin(ctx), yOff);
            case "randomringpoint" -> randomRingPoint(m, ctx, yOff);
            case "trackedlocation" -> {
                SkillRuntime.WhoState st = SkillRuntime.stateIfAny(ctx.caster);
                yield st == null || st.tracked == null ? List.of() : List.of(Tgt.at(st.tracked.add(0, yOff, 0)));
            }
            case "siblings", "sibling", "brothers", "sisters" -> siblings(ctx);
            case "playersinringnearorigin" -> {
                double min = SkillRuntime.num(m.targetArg("0", "min", "minradius", "minr"), 0);
                double max = SkillRuntime.num(m.targetArg("5", "max", "maxradius", "maxr", "r"), 5);
                List<Tgt> ring = new ArrayList<>();
                Vec3 c = origin(ctx);
                for (Tgt x : entities(ctx, c, max, true, false, m)) {
                    double d = Math.sqrt(x.pos().distanceToSqr(c));
                    if (d >= min && d <= max) ring.add(x);
                }
                yield ring;
            }
            case "playerlocationsinradius", "plir" -> {
                List<Tgt> locs = new ArrayList<>();
                for (Tgt x : entities(ctx, ctx.caster.pos(), radius(m), true, false, m)) locs.add(Tgt.at(x.pos().add(0, yOff, 0)));
                yield locs;
            }
            // @NotLivingNearOrigin (@NLNO): lo que no es un ser vivo cerca del origen: aquí, los efectos del pack
            case "notlivingnearorigin", "nonlivingnearorigin", "nlno" -> {
                Vec3 c = origin(ctx);
                double r = radius(m);
                List<Tgt> out2 = new ArrayList<>();
                for (SkillActor a : SkillRuntime.ACTORS) {
                    if (!a.alive || a.level != ctx.caster.level() || a.follow != null || ctx.caster.actor == a) continue;
                    if (a.pos.distanceToSqr(c) <= r * r) out2.add(Tgt.of(a.who));
                }
                yield out2;
            }
            case "locationsoftargets", "locationoftarget", "lot" -> {
                List<Tgt> locs = new ArrayList<>();
                if (ctx.targets != null) for (Tgt x : ctx.targets) locs.add(Tgt.at(x.pos().add(0, yOff, 0)));
                yield locs;
            }
            case "entitiesinline", "eil", "livinginline", "lil", "entl", "livingentitiesinline" -> line(ctx, m);
            case "targetblock", "tb" -> targetBlock(ctx, yOff);
            case "variablelocation", "varlocation", "vl" -> varLocation(m, ctx, yOff);
            case "randomlocationsneartargetentities", "rlnte", "randomlocationsneartargets", "rlnt", "randomlocationsneartarget",
                    "randomlocationsneartargetlocations", "rlntl" -> randomNearTargets(m, ctx, yOff);
            case "cone" -> conePoints(m, ctx);
            case "sphere" -> spherePoints(m, ctx);
            case "rectangle", "cube", "cuboid" -> rectangle(m, ctx);
            case "blocksinradius", "bir" -> {
                // Alrededor de cada objetivo heredado (nearorigin: del origen); sin ninguno, de quien disparó la skill
                if (SkillRuntime.bool(m.targetArg("false", "nearorigin", "no"))) yield blocks(m, ctx, List.of(origin(ctx)));
                List<Vec3> centers = new ArrayList<>();
                if (ctx.targets != null) for (Tgt x : ctx.targets) centers.add(x.pos());
                if (centers.isEmpty()) centers.add((ctx.trigger != null ? ctx.trigger : ctx.caster).pos());
                yield blocks(m, ctx, centers);
            }
            case "blocksnearorigin", "bno" -> blocks(m, ctx, List.of(origin(ctx)));
            default -> {
                SkillRuntime.warn("objetivo @" + t);
                yield List.of(Tgt.of(ctx.caster));
            }
        };
        // Condiciones del objetivo en línea (conditions=[ - isLiving true ...])
        if (!m.tc.isEmpty() && !out.isEmpty()) {
            List<Tgt> keep = new ArrayList<>();
            for (Tgt x : out) if (passes(m.tc, x, ctx)) keep.add(x);
            out = keep;
        }
        // sort= (NEAREST, FURTHEST, RANDOM) y limit=
        String sort = m.targetArg("", "sort", "sortby").toUpperCase(Locale.ROOT);
        int limit = (int) SkillRuntime.num(m.targetArg("0", "limit"), 0);
        if (out.size() > 1 && (!sort.isEmpty() || limit > 0 && out.size() > limit)) {
            Vec3 c = sort.contains("ORIGIN") ? origin(ctx) : ctx.caster.pos();
            List<Tgt> sorted = new ArrayList<>(out);
            switch (sort) {
                case "RANDOM" -> java.util.Collections.shuffle(sorted);
                case "FURTHEST", "FARTHEST" -> sorted.sort(Comparator.comparingDouble((Tgt x) -> x.pos().distanceToSqr(c)).reversed());
                case "NONE" -> { }
                default -> sorted.sort(Comparator.comparingDouble(x -> x.pos().distanceToSqr(c)));
            }
            out = limit > 0 && sorted.size() > limit ? sorted.subList(0, limit) : sorted;
        }
        return out;
    }

    /** ¿Pasa un objetivo las condiciones en línea? (cada una con su true/false; si no pasa una, fuera). */
    static boolean passes(List<SkillDefs.Cond> conds, Tgt t, Ctx ctx) {
        for (SkillDefs.Cond c : conds) {
            boolean r = SkillConds.test(c, t.who, ctx, t.who == null ? t.pos() : null);
            String act = c.action();
            boolean ok = act.equals("false") || act.equals("cancel") ? !r : r;
            if (!ok) return false;
        }
        return true;
    }

    /**
     * Lo que ha invocado quien lanza (sus efectos y esbirros). target=markers / armorstands: solo los de ese tipo de mob
     * (los marcadores hacia los que salta la Gárgola, la cabeza cortada de la Hidra).
     */
    private static List<Tgt> children(Ctx ctx, String kind) {
        List<Tgt> out = new ArrayList<>();
        for (SkillActor a : SkillRuntime.ACTORS) {
            if (!a.alive || a.owner == null || !a.owner.same(ctx.caster) || a.follow != null) continue;
            String type = a.def == null || a.def.type == null ? "" : a.def.type;
            if (kind.startsWith("marker") && !type.equals("MARKER")) continue;
            if (kind.startsWith("armorstand") && !type.equals("ARMOR_STAND")) continue;
            out.add(Tgt.of(a.who));
        }
        // Los esbirros de verdad de un jefe
        SkillMobs.Host me = ctx.caster.entity == null ? null : SkillMobs.host(ctx.caster.entity);
        if (me != null && me.boss() == me && (kind.isEmpty() || kind.startsWith("mob") || kind.startsWith("entit"))) {
            for (LivingEntity e : ctx.caster.level().getEntitiesOfClass(LivingEntity.class, ctx.caster.entity.getBoundingBox().inflate(64))) {
                SkillMobs.Host h = SkillMobs.host(e);
                if (h != null && h != me && h.boss() == me && e.isAlive()) out.add(Tgt.of(Who.of(e)));
            }
        }
        return out;
    }

    /** types=a,b: solo los efectos o mobs de esos tipos del pack. */
    private static List<Tgt> typed(List<Tgt> in, Mech m) {
        String types = m.targetArg(null, "types", "type", "t", "mobtypes");
        if (types == null) return in;
        List<Tgt> out = new ArrayList<>();
        for (Tgt t : in) {
            if (t.who == null) continue;
            // un efecto del pack (su mob) o un mob de verdad de un jefe (su mob del pack)
            SkillMobs.Host h = t.who.entity == null ? null : SkillMobs.host(t.who.entity);
            String name = t.who.actor != null ? t.who.actor.name() : h != null ? h.def().name : null;
            if (name == null) continue;
            for (String ty : types.split(",")) {
                if (ty.trim().equalsIgnoreCase(name)) {
                    out.add(t);
                    break;
                }
            }
        }
        return out;
    }

    private static List<Tgt> ringNear(Ctx ctx, Vec3 c, Mech m) {
        double min = SkillRuntime.num(m.targetArg("0", "min", "minradius", "minr"), 0);
        double max = SkillRuntime.num(m.targetArg("5", "max", "maxradius", "maxr", "r"), 5);
        List<Tgt> out = new ArrayList<>();
        for (Tgt t : entities(ctx, c, max, false, false, m)) {
            double d = Math.sqrt(t.pos().distanceToSqr(c));
            if (d >= min) out.add(t);
        }
        return out;
    }

    /** @EntitiesInCone{range;angle;usePitch}: delante de quien lanza, dentro del ángulo. */
    private static List<Tgt> cone(Ctx ctx, Mech m) {
        double range = SkillRuntime.num(m.targetArg("5", "range", "r"), 5);
        double angle = SkillRuntime.num(m.targetArg("90", "angle", "a"), 90);
        boolean pitch = SkillRuntime.bool(m.targetArg("false", "usepitch", "up"));
        Vec3 c = ctx.caster.pos();
        Vec3 look = SkillRuntime.dir(ctx.caster.yaw(), pitch ? ctx.caster.pitch() : 0F);
        double cos = Math.cos(Math.toRadians(angle / 2));
        List<Tgt> out = new ArrayList<>();
        for (Tgt t : entities(ctx, c, range, false, false, m)) {
            Vec3 to = t.pos().subtract(c);
            if (!pitch) to = new Vec3(to.x, 0, to.z);
            if (to.lengthSqr() < 0.25 || to.normalize().dot(look) >= cos) out.add(t);
        }
        return out;
    }

    /**
     * @EntitiesInLine{r}: las entidades en la línea que va de quien lanza al sitio de la skill (el objetivo heredado,
     * el origen o, si no, 8 bloques delante).
     */
    private static List<Tgt> line(Ctx ctx, Mech m) {
        double r = SkillRuntime.num(m.targetArg("1", "r", "radius"), 1);
        Vec3 from = ctx.caster.pos();
        Vec3 to = null;
        if (ctx.targets != null) {
            for (Tgt t : ctx.targets) if (t.who == null || !t.who.same(ctx.caster)) {
                to = t.pos();
                break;
            }
        }
        if (to == null && ctx.origin != null && ctx.origin.distanceToSqr(from) > 1) to = ctx.origin;
        if (to == null) to = from.add(SkillRuntime.dir(ctx.caster.yaw(), 0F).scale(8));
        Vec3 d = to.subtract(from);
        double len = d.length();
        Vec3 mid = from.add(d.scale(0.5));
        List<Tgt> out = new ArrayList<>();
        for (Tgt t : entities(ctx, mid, len / 2 + r + 1, false, false, m)) {
            Vec3 p = t.pos().add(0, 0.9, 0);
            double k = len < 1e-6 ? 0 : Math.max(0, Math.min(1, p.subtract(from).dot(d) / (len * len)));
            Vec3 near = from.add(d.scale(k));
            if (p.distanceTo(near.add(0, 0.9, 0)) <= r + 0.6) out.add(t);
        }
        return out;
    }

    /**
     * @TargetBlock: el bloque al que mira el jugador (hasta 64), como en MythicMobs: la esquina del bloque (y = la del
     * bloque sólido, uno por debajo del suelo que se pisa). Los packs lo cuentan así: «tyo=1.5» desde ahí cae al suelo.
     */
    private static List<Tgt> targetBlock(Ctx ctx, double yOff) {
        if (ctx.caster.entity instanceof ServerPlayer p) {
            Vec3 eye = p.getEyePosition();
            Vec3 end = eye.add(p.getViewVector(1F).scale(64));
            var hit = p.level().clip(new net.minecraft.world.level.ClipContext(eye, end, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, p));
            BlockPos b = hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : BlockPos.containing(end);
            return List.of(Tgt.at(new Vec3(b.getX(), b.getY() + yOff, b.getZ())));
        }
        return List.of(Tgt.at(ctx.caster.pos().add(ctx.caster.forward().scale(8)).add(0, yOff, 0)));
    }

    /** @VariableLocation{var=caster.x}: el sitio que guardó setvarloc. */
    private static List<Tgt> varLocation(Mech m, Ctx ctx, double yOff) {
        String var = m.targetArg(null, "var", "variable", "v");
        if (var == null) return List.of();
        String v = SkillRuntime.varScope(var, ctx, null).get(SkillRuntime.varName(var));
        if (v == null) return List.of();
        String[] p = v.split(",");
        if (p.length < 3) return List.of();
        return List.of(Tgt.at(new Vec3(SkillRuntime.num(p[0], 0), SkillRuntime.num(p[1], 0) + yOff, SkillRuntime.num(p[2], 0))));
    }

    /** @RandomLocationsNearTargetEntities{a;r;minr}: sitios al azar cerca de los objetivos (o de @target). */
    private static List<Tgt> randomNearTargets(Mech m, Ctx ctx, double yOff) {
        double r = SkillRuntime.num(m.targetArg("5", "radius", "r"), 5);
        double minR = SkillRuntime.num(m.targetArg("0", "minradius", "minr"), 0);
        int amount = (int) Math.max(1, Math.min(64, SkillRuntime.num(m.targetArg("1", "amount", "a"), 1)));
        List<Vec3> centers = new ArrayList<>();
        if (ctx.targets != null) for (Tgt t : ctx.targets) if (t.who != null && !t.who.same(ctx.caster)) centers.add(t.pos());
        if (centers.isEmpty() && ctx.aim != null && ctx.aim.isAlive()) centers.add(ctx.aim.position());
        if (centers.isEmpty()) centers.add(ctx.caster.pos().add(ctx.caster.forward().scale(6)));
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        List<Tgt> out = new ArrayList<>();
        for (Vec3 c : centers) {
            for (int i = 0; i < amount; i++) {
                double a = rnd.nextDouble() * Math.PI * 2, d = minR + Math.sqrt(rnd.nextDouble()) * Math.max(0, r - minR);
                out.add(Tgt.at(c.add(Math.cos(a) * d, yOff, Math.sin(a) * d)));
            }
        }
        return out;
    }

    /** Tope de puntos de los objetivos de formas (@Cone, @Sphere, @Rectangle) y de bloques (@BIR, @BNO). */
    private static final int MAX_POINTS = 4096;

    /**
     * @Cone{angle=90;range=16;points;slices;minpoints=1;rotation;yoffset;exact} (wiki de MythicMobs): puntos dentro de un
     * cono delante de quien lanza, siempre en horizontal (no se inclina). Va en «slices» capas a lo largo (floor(range)
     * si no lo dice); cada capa lleva su parte de los «points» (angle·range·0,1 si no lo dice) según lo que ocupa, y al
     * menos «minpoints»; al azar dentro de su capa o, con exact, repartidos por el borde de cada capa. rotation gira el
     * cono (grados, a la derecha).
     */
    private static List<Tgt> conePoints(Mech m, Ctx ctx) {
        double angle = Math.max(0, Math.min(360, SkillRuntime.num(m.targetArg("90", "angle", "a"), 90)));
        double range = Math.max(0.1, SkillRuntime.num(m.targetArg("16", "range", "r"), 16));
        int points = (int) Math.max(0, SkillRuntime.num(m.targetArg(null, "points", "p"), angle * range * 0.1));
        int slices = (int) Math.max(1, SkillRuntime.num(m.targetArg(null, "slices", "s"), Math.floor(range)));
        int minPoints = (int) Math.max(0, SkillRuntime.num(m.targetArg("1", "minpoints", "mp"), 1));
        float yaw = ctx.caster.yaw() + (float) SkillRuntime.num(m.targetArg("0", "rotation", "rot"), 0);
        boolean exact = SkillRuntime.bool(m.targetArg("false", "exact", "e"));
        Vec3 c = ctx.caster.pos().add(0, SkillRuntime.num(m.targetArg("0", "yoffset", "yo", "y"), 0), 0);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        List<Tgt> out = new ArrayList<>();
        for (int i = 1; i <= slices && out.size() < MAX_POINTS; i++) {
            // Lo que ocupa la capa i de un sector circular: (2i − 1) / slices²
            int n = Math.max(minPoints, (int) Math.round(points * (2.0 * i - 1) / ((double) slices * slices)));
            double r0 = range * (i - 1) / slices, r1 = range * i / slices;
            for (int k = 0; k < n && out.size() < MAX_POINTS; k++) {
                double a, d;
                if (exact) {
                    a = n == 1 ? 0 : -angle / 2 + angle * k / (n - 1);
                    d = r1;
                } else {
                    a = (rnd.nextDouble() - 0.5) * angle;
                    d = Math.sqrt(r0 * r0 + rnd.nextDouble() * (r1 * r1 - r0 * r0));
                }
                out.add(Tgt.at(c.add(SkillRuntime.dir(yaw + (float) a, 0F).scale(d))));
            }
        }
        return out;
    }

    /**
     * @Sphere{radius=5;points=10;yoffset;exact}: puntos en la superficie de una esfera alrededor de quien lanza; al azar o,
     * con exact («una esfera perfecta»), repartidos por igual (espiral de Fibonacci).
     */
    private static List<Tgt> spherePoints(Mech m, Ctx ctx) {
        double r = Math.max(0, SkillRuntime.num(m.targetArg("5", "radius", "r"), 5));
        int points = (int) Math.max(0, Math.min(MAX_POINTS, SkillRuntime.num(m.targetArg("10", "points", "p"), 10)));
        boolean exact = SkillRuntime.bool(m.targetArg("false", "exact", "e"));
        Vec3 c = ctx.caster.pos().add(0, SkillRuntime.num(m.targetArg("0", "yoffset", "yo", "y"), 0), 0);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double golden = Math.PI * (3 - Math.sqrt(5));
        List<Tgt> out = new ArrayList<>();
        for (int i = 0; i < points; i++) {
            double y = exact ? 1 - 2 * (i + 0.5) / points : rnd.nextDouble() * 2 - 1;
            double phi = exact ? i * golden : rnd.nextDouble() * Math.PI * 2;
            double h = Math.sqrt(Math.max(0, 1 - y * y));
            out.add(Tgt.at(c.add(Math.cos(phi) * h * r, y * r, Math.sin(phi) * h * r)));
        }
        return out;
    }

    /**
     * @Rectangle{x;y;z;xoffset;yoffset;zoffset;points=10;filled;outline;rotation=0,0,0;fromorigin} (@cube, @cuboid): una
     * caja de x·y·z bloques centrada en quien lanza (o en el origen) más sus offsets, con «points» puntos por arista: sus
     * caras (lo normal), solo sus aristas (outline) o llena (filled). rotation: grados en x, y, z sobre su centro.
     */
    private static List<Tgt> rectangle(Mech m, Ctx ctx) {
        double sx = SkillRuntime.num(m.targetArg("1", "x"), 1), sy = SkillRuntime.num(m.targetArg("1", "y"), 1),
                sz = SkillRuntime.num(m.targetArg("1", "z"), 1);
        Vec3 c = SkillRuntime.bool(m.targetArg("false", "fromorigin", "origin")) ? origin(ctx) : ctx.caster.pos();
        c = c.add(SkillRuntime.num(m.targetArg("0", "xoffset"), 0), SkillRuntime.num(m.targetArg("0", "yoffset"), 0),
                SkillRuntime.num(m.targetArg("0", "zoffset"), 0));
        int n = (int) Math.max(1, Math.min(64, SkillRuntime.num(m.targetArg("10", "points", "p", "density", "d"), 10)));
        boolean filled = SkillRuntime.bool(m.targetArg("false", "filled", "fill", "f"));
        boolean outline = SkillRuntime.bool(m.targetArg("false", "outline", "edge", "onlyedge", "e", "onlyoutline", "o"));
        String[] rot = m.targetArg("0,0,0", "rotation", "r").split(",");
        float rx = (float) Math.toRadians(SkillRuntime.num(rot[0], 0));
        float ry = (float) Math.toRadians(rot.length > 1 ? SkillRuntime.num(rot[1], 0) : 0);
        float rz = (float) Math.toRadians(rot.length > 2 ? SkillRuntime.num(rot[2], 0) : 0);
        List<Tgt> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                for (int k = 0; k < n; k++) {
                    int edges = (i == 0 || i == n - 1 ? 1 : 0) + (j == 0 || j == n - 1 ? 1 : 0) + (k == 0 || k == n - 1 ? 1 : 0);
                    if (n > 1 && !filled && edges < (outline ? 2 : 1)) continue;
                    double u = n == 1 ? 0.5 : (double) i / (n - 1), v = n == 1 ? 0.5 : (double) j / (n - 1),
                            w = n == 1 ? 0.5 : (double) k / (n - 1);
                    Vec3 p = new Vec3((u - 0.5) * sx, (v - 0.5) * sy, (w - 0.5) * sz);
                    if (rx != 0) p = p.xRot(rx);
                    if (ry != 0) p = p.yRot(ry);
                    if (rz != 0) p = p.zRot(rz);
                    out.add(Tgt.at(c.add(p)));
                    if (out.size() >= MAX_POINTS) return out;
                }
            }
        }
        return out;
    }

    /**
     * @BlocksInRadius / @BlocksNearOrigin{radius=2;radiusy;shape=SPHERE|CUBE;noise=0;noair=true;onlyair=false}: los bloques
     * alrededor de esos centros (su esquina, como las demás posiciones de bloque de MythicMobs); sin aire si no dice otra
     * cosa, o solo aire; noise: la parte que se salta al azar (0,5 = más o menos la mitad). Solo los chunks cargados (no
     * carga ninguno) y como mucho 16 bloques de radio.
     */
    private static List<Tgt> blocks(Mech m, Ctx ctx, List<Vec3> centers) {
        double r = Math.max(0, Math.min(16, SkillRuntime.num(m.targetArg("2", "radius", "r"), 2)));
        double ry = Math.max(0, Math.min(16, SkillRuntime.num(m.targetArg(null, "radiusy", "ry", "yradius", "yr"), r)));
        boolean cube = m.targetArg("SPHERE", "shape", "s").toUpperCase(Locale.ROOT).equals("CUBE");
        double noise = SkillRuntime.num(m.targetArg("0", "noise", "n"), 0);
        boolean onlyAir = SkillRuntime.bool(m.targetArg("false", "onlyair", "oa"));
        boolean noAir = SkillRuntime.bool(m.targetArg("true", "noair", "na"));
        ServerLevel level = ctx.caster.level();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        List<Tgt> out = new ArrayList<>();
        int ir = (int) Math.ceil(r), iry = (int) Math.ceil(ry);
        for (Vec3 center : centers) {
            BlockPos cb = BlockPos.containing(center);
            for (int dx = -ir; dx <= ir; dx++) {
                for (int dy = -iry; dy <= iry; dy++) {
                    for (int dz = -ir; dz <= ir; dz++) {
                        boolean in = cube ? Math.abs(dx) <= r && Math.abs(dy) <= ry && Math.abs(dz) <= r
                                : sq(dx, r) + sq(dy, ry) + sq(dz, r) <= 1.0 + 1e-9;
                        if (!in) continue;
                        BlockPos b = cb.offset(dx, dy, dz);
                        if (!seen.add(b) || !level.isLoaded(b)) continue;
                        boolean air = level.getBlockState(b).isAir();
                        if (onlyAir ? !air : noAir && air) continue;
                        if (noise > 0 && rnd.nextDouble() < noise) continue;
                        out.add(Tgt.at(Vec3.atLowerCornerOf(b)));
                        if (out.size() >= MAX_POINTS) return out;
                    }
                }
            }
        }
        return out;
    }

    /** (d / r)², con r = 0 como «solo el centro». */
    private static double sq(int d, double r) {
        if (r <= 0) return d == 0 ? 0 : 2;
        double v = d / r;
        return v * v;
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
            for (Tgt t : ctx.targets) if (t.who != null && !t.who.same(ctx.caster)) return List.of(t);
        }
        return List.of();
    }

    /** @TargetedTarget: el objetivo heredado (o @target si no hay). */
    private static List<Tgt> inherited(Ctx ctx) {
        List<Tgt> inh = new ArrayList<>();
        if (ctx.targets != null) for (Tgt x : ctx.targets) if (x.who != null && !x.who.same(ctx.caster)) inh.add(x);
        return inh.isEmpty() ? target(ctx) : inh;
    }

    /** @ThreatTable, @RandomThreatTarget y @RandomThreatTargetLocation de un mob de jefe (otro lanzador: su @target). */
    private static List<Tgt> threat(Ctx ctx, String t, double yOff) {
        SkillMobs.Host h = ctx.caster.entity == null ? null : SkillMobs.host(ctx.caster.entity);
        List<Tgt> all = new ArrayList<>();
        if (h != null) for (LivingEntity e : h.threatTable()) all.add(Tgt.of(Who.of(e)));
        else all.addAll(target(ctx));
        if (all.isEmpty() || t.equals("threattable") || t.equals("threattableplayers") || t.equals("ttp")) return all;
        Tgt one = all.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(all.size()));
        if (t.equals("rttl") || t.equals("randomthreattargetlocation")) return List.of(Tgt.at(one.pos().add(0, yOff, 0)));
        return List.of(one);
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

    /** Un objetivo escrito como texto («@SelfEyeLocation{fo=-0.5;yo=-0.2}»): sus sitios (para origin=, setvarloc). */
    static List<Tgt> fromString(String text, Ctx ctx) {
        java.util.regex.Matcher mt = java.util.regex.Pattern.compile("^@([\\w]+)(?:\\{(.*)})?$").matcher(text.trim());
        if (!mt.matches()) return List.of();
        Mech fake = new Mech();
        fake.m = "origin";
        fake.t = mt.group(1).toLowerCase(Locale.ROOT);
        java.util.Map<String, String> ta = new java.util.HashMap<>();
        if (mt.group(2) != null) {
            for (String part : mt.group(2).split(";")) {
                int eq = part.indexOf('=');
                if (eq > 0) ta.put(part.substring(0, eq).trim().toLowerCase(Locale.ROOT), part.substring(eq + 1).trim());
            }
        }
        fake.ta = ta;
        return resolve(fake, ctx);
    }

    /**
     * @forward{f=distancia;y;sideoffset;lockpitch;uel}: delante de quien lanza, en la dirección a la que mira (uel:
     * desde los ojos).
     */
    static Vec3 forward(Mech m, Who src, Vec3 base, float yaw, float pitch, double yOff) {
        double f = SkillRuntime.num(m.targetArg("5", "f", "forward", "amount", "a"), 5);
        double side = SkillRuntime.num(m.targetArg("0", "sideoffset", "so", "side", "s"), 0);
        if (SkillRuntime.bool(m.targetArg("false", "uel", "useeyelocation"))) base = src.eye();
        boolean lock = SkillRuntime.bool(m.targetArg("false", "lockpitch", "lp")) || src.actor != null;
        Vec3 d = SkillRuntime.dir(yaw, lock ? 0F : pitch);
        Vec3 right = SkillRuntime.dir(yaw + 90F, 0F);
        return base.add(d.scale(f)).add(right.scale(side)).add(0, yOff, 0);
    }

    /** forwardoffset/sideoffset de un sitio, según hacia dónde mira quien lanza (los elixires de la Bruja salen detrás). */
    private static Vec3 offset(Mech m, Ctx ctx, Vec3 at) {
        double fo = SkillRuntime.num(m.targetArg("0", "forwardoffset", "fo"), 0);
        double so = SkillRuntime.num(m.targetArg("0", "sideoffset", "so"), 0);
        if (fo == 0 && so == 0) return at;
        float yaw = ctx.caster.yaw();
        return at.add(SkillRuntime.dir(yaw, 0F).scale(fo)).add(SkillRuntime.dir(yaw + 90F, 0F).scale(so));
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
        boolean self = SkillRuntime.bool(m.targetArg("false", "targetself", "ts"));
        String myFaction = ignore.contains("samefaction") ? faction(ctx.caster) : null;
        // En un jefe, sus propios esbirros y efectos no son objetivo (salvo que el pack los busque por su tipo): la esfera
        // de energía de Astwood (@EIR) salía hacia sus retoños y sus muros y caía al suelo junto a él
        boolean typed = m != null && (m.targetArg(null, "types", "type", "t", "mobtypes") != null || looksForType(m.tc));
        SkillMobs.Host mine = ctx.host != null && !typed ? ctx.host.boss() : null;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (!e.isAlive() || e.isSpectator() || e instanceof ArmorStand) continue;
            if (ctx.caster.entity == e && !self) continue;
            if (mine != null && e != ctx.caster.entity && SkillMobs.host(e) != null && SkillMobs.host(e).boss() == mine) continue;
            if (myFaction != null && myFaction.equalsIgnoreCase(faction(Who.of(e)))) continue;
            if (playersOnly && !(e instanceof Player)) continue;
            if (mobsOnly && e instanceof Player) continue;
            if (e.position().distanceToSqr(c) > r * r && e.getBoundingBox().getCenter().distanceToSqr(c) > r * r) continue;
            if (!ignore.isEmpty() && ignore.contains("players") && e instanceof Player) continue;
            out.add(Tgt.of(Who.of(e)));
        }
        // Los efectos invocados también están ahí (en MythicMobs eran mobs): los packs los buscan para quitarlos o
        // moverlos (con ?mythicmobtype)
        // (la bala de un proyectil no, salvo que se busque por tipo: en el pack el misil le da al de al lado, no a sí mismo)
        if (!playersOnly) {
            boolean byType = m != null && (m.targetArg(null, "types", "type", "t", "mobtypes") != null || looksForType(m.tc));
            // livingOnly: sin los displays (los soportes sí son mobs vivos); targetArmorStands=false: sin soportes
            boolean livingOnly = SkillRuntime.bool(m.targetArg("false", "livingonly"));
            boolean armorStands = SkillRuntime.bool(m.targetArg("true", "targetarmorstands", "tas"));
            for (SkillActor a : SkillRuntime.ACTORS) {
                if (!a.alive || a.level != level || a.follow != null || ctx.caster.actor == a) continue;
                if (a.carried && !byType) continue;
                if (mine != null && a.host != null && a.host.boss() == mine) continue;
                String type = a.def != null && a.def.type != null ? a.def.type.toLowerCase(Locale.ROOT) : "";
                // Un display (block/item/text_display) no es un mob vivo: solo sale si se busca por su tipo de mob
                if ((livingOnly || !byType) && type.contains("display")) continue;
                if (!armorStands && type.equals("armor_stand")) continue;
                if (a.pos.distanceToSqr(c) <= r * r) out.add(Tgt.of(a.who));
            }
        }
        return out;
    }

    /** La facción de alguien (Faction: NATURE de Astwood y sus retoños; o la que le puso setfaction). */
    static String faction(Who w) {
        if (w == null) return null;
        SkillRuntime.WhoState st = SkillRuntime.stateIfAny(w);
        if (st != null && st.faction != null) return st.faction;
        if (w.actor != null && w.actor.def != null) return w.actor.def.faction;
        SkillMobs.Host h = w.entity == null ? null : SkillMobs.host(w.entity);
        return h != null ? h.faction() : null;
    }

    /** ¿Busca la línea un efecto del pack por su tipo (?mythicmobtype{t=X} true, también dentro de una compuesta)? */
    static boolean looksForType(List<SkillDefs.Cond> conds) {
        for (SkillDefs.Cond c : conds) {
            if ((c.m().equals("mythicmobtype") || c.m().equals("mmt") || c.m().equals("mobtype")) && !"false".equals(c.action())) return true;
            if (looksForType(c.parts())) return true;
        }
        return false;
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

    /**
     * @RingAroundOrigin{radius;points;rotation;xrotation;yrotation;zrotation} (@RAO): un anillo de sitios alrededor del
     * origen, girado esos grados en x, y, z (como Vec3.xRot/yRot/zRot).
     */
    private static List<Tgt> ringAround(Mech m, Ctx ctx, Vec3 c, double yOff) {
        double r = SkillRuntime.num(m.targetArg("5", "radius", "r"), 5);
        int points = (int) Math.max(1, Math.min(256, SkillRuntime.num(m.targetArg("8", "points", "p"), 8)));
        float rot = (float) SkillRuntime.num(m.targetArg("0", "rotation", "rot"), 0);
        float rx = (float) Math.toRadians(SkillRuntime.num(m.targetArg("0", "xrotation", "xrot", "rx"), 0));
        float ry = (float) Math.toRadians(SkillRuntime.num(m.targetArg("0", "yrotation", "yrot", "ry"), 0));
        float rz = (float) Math.toRadians(SkillRuntime.num(m.targetArg("0", "zrotation", "zrot", "rz"), 0));
        List<Tgt> out = new ArrayList<>();
        for (int i = 0; i < points; i++) {
            Vec3 d = SkillRuntime.dir(rot + 360F * i / points, 0F).scale(r);
            if (rx != 0) d = d.xRot(rx);
            if (ry != 0) d = d.yRot(ry);
            if (rz != 0) d = d.zRot(rz);
            out.add(Tgt.at(c.add(d).add(0, yOff, 0)));
        }
        return out;
    }

    /**
     * @RandomRingPoint{radius=5;points=10;amount=1;rotationx/y/z;offsetx/y/z}: «amount» de los «points» puntos de un
     * anillo alrededor de quien lanza, al azar (la Tormenta del Wither saca de ahí los bloques que absorbe).
     */
    private static List<Tgt> randomRingPoint(Mech m, Ctx ctx, double yOff) {
        double r = SkillRuntime.num(m.targetArg("5", "radius", "r"), 5);
        int points = (int) Math.max(1, Math.min(256, SkillRuntime.num(m.targetArg("10", "points", "p"), 10)));
        int amount = (int) Math.max(1, Math.min(points, SkillRuntime.num(m.targetArg("1", "amount", "a"), 1)));
        float rx = (float) Math.toRadians(SkillRuntime.num(m.targetArg("0", "rotationx", "rotx", "rx"), 0));
        float ry = (float) Math.toRadians(SkillRuntime.num(m.targetArg("0", "rotationy", "roty", "ry"), 0));
        float rz = (float) Math.toRadians(SkillRuntime.num(m.targetArg("0", "rotationz", "rotz", "rz"), 0));
        Vec3 off = new Vec3(SkillRuntime.num(m.targetArg("0", "offsetx", "offx", "ox"), 0),
                SkillRuntime.num(m.targetArg("0", "offsety", "offy", "oy"), 0), SkillRuntime.num(m.targetArg("0", "offsetz", "offz", "oz"), 0));
        Vec3 c = ctx.caster.pos();
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < points; i++) idx.add(i);
        java.util.Collections.shuffle(idx, new java.util.Random(java.util.concurrent.ThreadLocalRandom.current().nextLong()));
        List<Tgt> out = new ArrayList<>();
        for (int k = 0; k < amount; k++) {
            Vec3 d = SkillRuntime.dir(360F * idx.get(k) / points, 0F).scale(r);
            if (rx != 0) d = d.xRot(rx);
            if (ry != 0) d = d.yRot(ry);
            if (rz != 0) d = d.zRot(rz);
            out.add(Tgt.at(c.add(d).add(off).add(0, yOff, 0)));
        }
        return out;
    }

    /** @Siblings: los que tienen el mismo padre que quien lanza (los otros efectos de su dueño, los otros esbirros de su jefe). */
    private static List<Tgt> siblings(Ctx ctx) {
        List<Tgt> out = new ArrayList<>();
        SkillRuntime.Who parent = ctx.caster.owner();
        if (parent != null) {
            for (SkillActor a : SkillRuntime.ACTORS) {
                if (a.alive && a.owner != null && a.owner.same(parent) && ctx.caster.actor != a && a.follow == null) out.add(Tgt.of(a.who));
            }
        }
        SkillMobs.Host me = ctx.caster.entity == null ? null : SkillMobs.host(ctx.caster.entity);
        if (me != null && me.boss() != me) {
            for (LivingEntity e : ctx.caster.level().getEntitiesOfClass(LivingEntity.class, ctx.caster.entity.getBoundingBox().inflate(64))) {
                SkillMobs.Host h = SkillMobs.host(e);
                if (h != null && h != me && h.boss() == me.boss() && h.boss() != h && e.isAlive()) out.add(Tgt.of(Who.of(e)));
            }
        }
        return out;
    }

    private static List<Tgt> random(Mech m, Ctx ctx, double yOff, boolean fromOrigin) {
        double r = SkillRuntime.num(m.targetArg("5", "radius", "r", "maxradius", "maxr"), 5);
        double minR = Math.min(r, SkillRuntime.num(m.targetArg("0", "minradius", "minr"), 0));
        int amount = (int) Math.max(1, Math.min(64, SkillRuntime.num(m.targetArg("1", "amount", "a"), 1)));
        Vec3 c = fromOrigin ? origin(ctx) : ctx.caster.pos();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        List<Tgt> out = new ArrayList<>();
        for (int i = 0; i < amount; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = minR + Math.sqrt(rnd.nextDouble()) * (r - minR);
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
