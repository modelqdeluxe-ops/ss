package net.tierrasfantasticas.tfclient.skills;

import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.tierrasfantasticas.tfclient.skills.SkillDefs.Cond;
import net.tierrasfantasticas.tfclient.skills.SkillRuntime.Ctx;
import net.tierrasfantasticas.tfclient.skills.SkillRuntime.Who;

/** Las condiciones de MythicMobs que usan los packs. Las que no existen aquí se dan por cumplidas. */
final class SkillConds {
    private SkillConds() {}

    /** ¿Se cumple la condición para «who» (o el punto «at» si el objetivo es un sitio)? */
    static boolean test(Cond c, Who who, Ctx ctx, Vec3 at) {
        Map<String, String> a = c.a();
        Entity e = who != null ? who.entity : null;
        LivingEntity le = e instanceof LivingEntity l ? l : null;
        return switch (c.m()) {
            case "mythicmobtype", "mmt", "mobtype" -> {
                String types = arg(a, "", "types", "type", "t", "mobtypes", "m");
                String name = who != null && who.actor != null ? who.actor.name() : "";
                boolean hit = false;
                for (String t : types.split(",")) if (t.trim().equalsIgnoreCase(name)) hit = true;
                yield hit;
            }
            case "owner", "isowner" -> who != null && ctx.caster.owner() != null && ctx.caster.owner().same(who)
                    || who != null && who.entity != null && who.entity == ctx.caster.player() && ctx.caster.actor != null;
            case "isparent", "parent" -> who != null && ctx.caster.owner() != null && ctx.caster.owner().same(who);
            case "hasaura", "hasbuff", "hasaurastacks", "aurastacks" -> {
                String name = arg(a, null, "auraname", "aura", "name", "n", "buffname", "b");
                if (who == null || name == null) yield false;
                SkillRuntime.WhoState st = SkillRuntime.stateIfAny(who);
                SkillAuras.Aura aura = st == null ? null : st.auras.get(name.toLowerCase(Locale.ROOT));
                int stacks = aura == null || !aura.alive ? 0 : aura.stacks;
                String range = arg(a, null, "stacks", "s", "amount", "a");
                yield range == null ? stacks > 0 : inRange(stacks, range);
            }
            case "crouching", "sneaking", "issneaking" -> e != null && e.isCrouching();
            case "sprinting", "issprinting" -> e != null && e.isSprinting();
            case "onground", "grounded" -> e != null ? e.onGround() : true;
            case "inblock", "blocktype", "inside", "standingon" -> {
                Vec3 p = at != null ? at : who != null ? who.pos() : ctx.caster.pos();
                BlockPos pos = BlockPos.containing(p);
                if (c.m().equals("standingon")) pos = pos.below();
                String types = arg(a, "", "types", "type", "t", "material", "m").toLowerCase(Locale.ROOT);
                String id = BuiltInRegistries.BLOCK.getKey(ctx.caster.level().getBlockState(pos).getBlock()).getPath();
                boolean hit = false;
                for (String t : types.split(",")) {
                    String tt = t.trim().replace("minecraft:", "");
                    if (tt.equals(id)) hit = true;
                }
                yield hit;
            }
            case "variableisset", "varisset", "varset" -> {
                String var = arg(a, null, "var", "variable", "name", "key", "k");
                yield var != null && SkillRuntime.varScope(var, ctx, who == null ? null : SkillRuntime.Tgt.of(who))
                        .containsKey(SkillRuntime.varName(var));
            }
            case "variableequals", "varequals", "variableeq", "vareq" -> {
                String var = arg(a, null, "var", "variable", "name", "key", "k");
                if (var == null) yield false;
                String v = SkillRuntime.varScope(var, ctx, who == null ? null : SkillRuntime.Tgt.of(who)).get(SkillRuntime.varName(var));
                String want = SkillRuntime.unquote(arg(a, "", "value", "val", "v"));
                yield v != null && (v.equalsIgnoreCase(want) || numEq(v, want));
            }
            case "variableinrange", "varinrange", "varrange" -> {
                String var = arg(a, null, "var", "variable", "name", "key", "k");
                if (var == null) yield false;
                String v = SkillRuntime.varScope(var, ctx, who == null ? null : SkillRuntime.Tgt.of(who)).get(SkillRuntime.varName(var));
                yield v != null && inRange(SkillRuntime.num(v, 0), arg(a, ">0", "value", "val", "v", "range"));
            }
            case "itemissimilar", "holding" -> e instanceof net.minecraft.world.entity.player.Player p
                    && SkillItems.holds(p, ctx.cls, arg(a, "", "i", "item", "material", "m"));
            case "offgcd", "gcd", "incombat", "hasai", "hastarget", "wearing", "haspermission",
                    "lineofsight", "los", "world", "biome", "dimension", "moonphase", "stance", "hasowner" -> true;
            case "targetwithin", "targetinlineofsight", "targetnotwithin" -> {
                Entity t = ctx.aim;
                double d = SkillRuntime.num(arg(a, "16", "distance", "d"), 16);
                boolean within = t != null && t.isAlive() && t.position().distanceTo(ctx.caster.pos()) <= d;
                yield c.m().equals("targetnotwithin") != within;
            }
            case "distance", "distancefromorigin" -> {
                Vec3 p = at != null ? at : who != null ? who.pos() : ctx.caster.pos();
                Vec3 from = c.m().equals("distance") ? ctx.caster.pos() : SkillTargets.origin(ctx);
                yield inRange(p.distanceTo(from), arg(a, ">0", "distance", "d"));
            }
            case "health", "hp" -> le != null && inRange(le.getHealth(), arg(a, ">0", "health", "h", "amount", "a"));
            case "healthpercent", "hppercent", "hpp" -> le != null && inRange(le.getHealth() / Math.max(1, le.getMaxHealth()),
                    arg(a, ">0", "percent", "p", "health", "h"));
            case "entitytype", "type", "entitytypes" -> {
                if (e == null) yield false;
                String id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
                boolean hit = false;
                for (String t : arg(a, "", "types", "type", "t").split(",")) if (t.trim().equalsIgnoreCase(id)) hit = true;
                yield hit;
            }
            case "isplayer", "player" -> e instanceof Player;
            case "ismonster", "monster" -> e instanceof Enemy;
            case "isliving", "living" -> le != null;
            case "isburning", "burning", "onfire" -> e != null && e.isOnFire();
            case "inwater", "isinwater" -> e != null && e.isInWater();
            case "haspotioneffect", "haspotion" -> {
                var effect = SkillRuntime.potionType(arg(a, null, "type", "t"));
                yield le != null && effect != null && le.hasEffect(effect);
            }
            case "isskill", "chance" -> java.util.concurrent.ThreadLocalRandom.current().nextDouble()
                    < SkillRuntime.num(arg(a, "1", "chance", "c"), 1);
            case "altitude", "height" -> {
                Vec3 p = at != null ? at : who != null ? who.pos() : ctx.caster.pos();
                yield inRange(p.y, arg(a, ">0", "height", "h", "a"));
            }
            case "dead", "isdead" -> who != null && !who.alive();
            default -> {
                SkillRuntime.warn("condición " + c.m());
                yield true;
            }
        } != c.not();
    }

    private static String arg(Map<String, String> a, String def, String... keys) {
        for (String k : keys) {
            String v = a.get(k);
            if (v != null) return v;
        }
        return def;
    }

    private static boolean numEq(String a, String b) {
        try {
            return Double.parseDouble(a) == Double.parseDouble(b);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Rango de MythicMobs: «3», «1to5», «>0», «<2», «>=1», «<=4», «!=0». */
    static boolean inRange(double v, String range) {
        if (range == null) return true;
        String r = range.trim().replace(" ", "");
        try {
            if (r.startsWith(">=")) return v >= Double.parseDouble(r.substring(2));
            if (r.startsWith("<=")) return v <= Double.parseDouble(r.substring(2));
            if (r.startsWith("!=")) return v != Double.parseDouble(r.substring(2));
            if (r.startsWith(">")) return v > Double.parseDouble(r.substring(1));
            if (r.startsWith("<")) return v < Double.parseDouble(r.substring(1));
            if (r.startsWith("=")) return v == Double.parseDouble(r.substring(1));
            int to = r.indexOf("to");
            if (to > 0) return v >= Double.parseDouble(r.substring(0, to)) && v <= Double.parseDouble(r.substring(to + 2));
            int dash = r.indexOf('-', 1);
            if (dash > 0) return v >= Double.parseDouble(r.substring(0, dash)) && v <= Double.parseDouble(r.substring(dash + 1));
            return v == Double.parseDouble(r);
        } catch (NumberFormatException e) {
            return true;
        }
    }
}
