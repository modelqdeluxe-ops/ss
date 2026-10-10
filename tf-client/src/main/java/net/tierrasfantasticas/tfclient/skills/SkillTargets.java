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
            case "self", "caster", "s", "c", "boss", "mob" -> List.of(Tgt.of(ctx.caster));
            case "owner" -> who(ctx.caster.owner() != null ? ctx.caster.owner() : ctx.caster);
            case "parent", "summoner" -> who(ctx.caster.owner());
            case "trigger" -> who(ctx.trigger != null ? ctx.trigger : ctx.caster);
            case "target", "t", "targetedentity", "targeted" -> target(ctx);
            // @TargetedTarget: el objetivo heredado (skill{s=X} @EntitiesNearOrigin → dentro de X, cada uno de ellos)
            case "targetedtarget", "tt" -> {
                List<Tgt> inh = new ArrayList<>();
                if (ctx.targets != null) for (Tgt x : ctx.targets) if (x.who != null && !x.who.same(ctx.caster)) inh.add(x);
                yield inh.isEmpty() ? target(ctx) : inh;
            }
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
            case "mobsinradius", "mir" -> entities(ctx, ctx.caster.pos(), radius(m), false, true, m);
            case "playersinradius", "pir", "playersinworld", "world", "pinr" -> entities(ctx, ctx.caster.pos(), radius(m), true, false, m);
            case "nearestplayer", "np" -> nearest(entities(ctx, ctx.caster.pos(), radius(m), true, false, m), ctx.caster.pos());
            case "ring" -> ring(m, ctx, yOff);
            case "circle" -> ring(m, ctx, yOff);
            case "randomlocationsnearcaster", "rlnc", "randomlocationsnearorigin", "rlno" -> random(m, ctx, yOff, t.endsWith("origin") || t.equals("rlno"));
            case "modelpart", "mp" -> SkillModels.modelPart(m, ctx, yOff);
            case "location", "l" -> location(m, ctx);
            case "spawnlocation" -> List.of(Tgt.at(ctx.caster.pos()));
            case "ownerlocation" -> {
                Who o = ctx.caster.owner() != null ? ctx.caster.owner() : ctx.caster;
                yield List.of(Tgt.at(o.pos().add(0, yOff, 0)));
            }
            case "children", "child", "summons" -> children(ctx);
            case "mountedmodel", "mounted" -> {
                SkillActor a = ctx.caster.entity == null ? null : SkillModels.mountedBy(ctx.caster.entity);
                yield a == null ? List.of() : List.of(Tgt.of(a.who));
            }
            case "mobsnearorigin", "mno" -> typed(entities(ctx, origin(ctx), radius(m), false, true, m), m);
            case "entitiesinworld", "eiw", "livingentitiesinworld" -> entities(ctx, ctx.caster.pos(), 128, false, false, m);
            case "entitiesinringnearorigin", "eirno" -> ringNear(ctx, origin(ctx), m);
            case "entitiesincone", "eic", "livingentitiesincone" -> cone(ctx, m);
            case "entitiesinline", "eil", "livinginline", "lil", "entl", "livingentitiesinline" -> line(ctx, m);
            case "targetblock", "tb" -> targetBlock(ctx, yOff);
            case "variablelocation", "varlocation", "vl" -> varLocation(m, ctx, yOff);
            case "randomlocationsneartargetentities", "rlnte", "randomlocationsneartargets", "rlnt" -> randomNearTargets(m, ctx, yOff);
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

    /** Lo que ha invocado quien lanza (sus efectos y esbirros). */
    private static List<Tgt> children(Ctx ctx) {
        List<Tgt> out = new ArrayList<>();
        for (SkillActor a : SkillRuntime.ACTORS) {
            if (a.alive && a.owner != null && a.owner.same(ctx.caster) && a.follow == null) out.add(Tgt.of(a.who));
        }
        return out;
    }

    /** types=a,b: solo los efectos o mobs de esos tipos del pack. */
    private static List<Tgt> typed(List<Tgt> in, Mech m) {
        String types = m.targetArg(null, "types", "type", "t", "mobtypes");
        if (types == null) return in;
        List<Tgt> out = new ArrayList<>();
        for (Tgt t : in) {
            if (t.who == null || t.who.actor == null) continue;
            for (String ty : types.split(",")) {
                if (ty.trim().equalsIgnoreCase(t.who.actor.name())) {
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

    /** @TargetBlock: el bloque al que mira el jugador (hasta 32). */
    private static List<Tgt> targetBlock(Ctx ctx, double yOff) {
        if (ctx.caster.entity instanceof ServerPlayer p) {
            Vec3 eye = p.getEyePosition();
            Vec3 end = eye.add(p.getViewVector(1F).scale(32));
            var hit = p.level().clip(new net.minecraft.world.level.ClipContext(eye, end, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, p));
            Vec3 at = hit.getLocation();
            return List.of(Tgt.at(at.add(0, yOff, 0)));
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
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (!e.isAlive() || e.isSpectator() || e instanceof ArmorStand) continue;
            if (ctx.caster.entity == e && !self) continue;
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
                String type = a.def != null && a.def.type != null ? a.def.type.toLowerCase(Locale.ROOT) : "";
                // Un display (block/item/text_display) no es un mob vivo: solo sale si se busca por su tipo de mob
                if ((livingOnly || !byType) && type.contains("display")) continue;
                if (!armorStands && type.equals("armor_stand")) continue;
                if (a.pos.distanceToSqr(c) <= r * r) out.add(Tgt.of(a.who));
            }
        }
        return out;
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
