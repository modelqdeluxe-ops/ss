package net.tierrasfantasticas.tfclient.skills;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.tierrasfantasticas.tfclient.skills.SkillDefs.Mech;
import net.tierrasfantasticas.tfclient.skills.SkillRuntime.Ctx;
import net.tierrasfantasticas.tfclient.skills.SkillRuntime.Tgt;
import net.tierrasfantasticas.tfclient.skills.SkillRuntime.Who;

/**
 * Las variables de MythicMobs/MythicLib dentro de los argumentos: &lt;skill.damage&gt; y &lt;modifier.x&gt; (valores de
 * la skill de clase), &lt;caster.var.x&gt; / &lt;target.var.x&gt; / &lt;skill.var.x&gt;, &lt;caster.l.x&gt;, &lt;caster.hp&gt;,
 * &lt;random.float.AtoB&gt;... y las cuentas sencillas que quedan («20 * 3»).
 */
final class SkillVars {
    private static final Pattern TAG = Pattern.compile("<([^<>]+)>");

    private SkillVars() {}

    static Mech resolve(Mech m, Ctx ctx) {
        Tgt first = ctx.targets != null && !ctx.targets.isEmpty() ? ctx.targets.get(0) : null;
        Map<String, String> a = map(m.a, ctx, first);
        // setname: «<target.name>» es el de cada objetivo de la línea (se resuelve en la mecánica)
        if (m.m.equals("setname") && a != m.a) {
            a = new HashMap<>(a);
            for (String k : new String[] {"name", "n"}) if (m.a.containsKey(k)) a.put(k, m.a.get(k));
        }
        return m.copyWith(a, map(m.ta, ctx, first));
    }

    private static Map<String, String> map(Map<String, String> in, Ctx ctx, Tgt target) {
        if (in.isEmpty()) return in;
        Map<String, String> out = new HashMap<>(in.size());
        for (Map.Entry<String, String> e : in.entrySet()) {
            String v = e.getValue();
            out.put(e.getKey(), v.indexOf('<') >= 0 ? text(v, ctx, target) : v);
        }
        return out;
    }

    /** Sustituye las variables de un texto (y si queda una cuenta, la resuelve). */
    static String text(String s, Ctx ctx, Tgt target) {
        Matcher mt = TAG.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (mt.find()) mt.appendReplacement(sb, Matcher.quoteReplacement(value(mt.group(1), ctx, target)));
        mt.appendTail(sb);
        String out = sb.toString();
        if (out.matches(".*\\d\\s*[*/+\\-]\\s*[\\d(].*") && out.matches("[\\d\\s.+\\-*/()]+")) {
            Double r = eval(out);
            if (r != null) return SkillRuntime.fmt(r);
        }
        return out;
    }

    private static String value(String tag, Ctx ctx, Tgt target) {
        String t = tag.trim();
        String low = t.toLowerCase(Locale.ROOT);
        // Números al azar
        Matcher r = Pattern.compile("random\\.float\\.(-?[\\d.]+)to(-?[\\d.]+)").matcher(low);
        if (r.matches()) {
            double a = Double.parseDouble(r.group(1)), b = Double.parseDouble(r.group(2));
            return SkillRuntime.fmt(round(a + ThreadLocalRandom.current().nextDouble() * (b - a)));
        }
        Matcher ri = Pattern.compile("random\\.(?:int\\.)?(-?\\d+)to(-?\\d+)").matcher(low);
        if (ri.matches()) {
            int a = Integer.parseInt(ri.group(1)), b = Integer.parseInt(ri.group(2));
            return String.valueOf(a + ThreadLocalRandom.current().nextInt(Math.max(1, b - a + 1)));
        }
        // Valores de la skill de clase
        for (String prefix : new String[] {"modifier.", "spell.mod.", "skill.mod.", "mod."}) {
            if (low.startsWith(prefix)) return SkillRuntime.fmt(ctx.mods.getOrDefault(low.substring(prefix.length()), 0.0));
        }
        if (low.startsWith("skill.var.")) return orZero(ctx.vars == null ? null : ctx.vars.get(low.substring(10)));
        if (low.equals("skill.power")) return SkillRuntime.fmt(ctx.power);
        if (low.equals("skill.targets")) return String.valueOf(ctx.targets == null ? 1 : ctx.targets.size());
        if (low.startsWith("skill.")) {
            String k = low.substring(6);
            if (ctx.mods.containsKey(k)) return SkillRuntime.fmt(ctx.mods.get(k));
            return orZero(ctx.vars == null ? null : ctx.vars.get(k));
        }
        if (low.startsWith("stat.")) return SkillRuntime.fmt(stat(ctx.caster.player(), low.substring(5)));
        if (low.startsWith("global.var.")) return orZero(SkillRuntime.varScope("global.x", ctx, null).get(low.substring(11)));
        // De quién: caster, target, trigger
        Who who;
        String rest;
        if (low.startsWith("caster.")) {
            who = ctx.caster;
            rest = low.substring(7);
        } else if (low.startsWith("target.")) {
            who = target != null ? target.who : null;
            rest = low.substring(7);
            if (who == null && target != null) return location(target.at, rest);
        } else if (low.startsWith("trigger.")) {
            who = ctx.trigger;
            rest = low.substring(8);
        } else {
            return "<" + tag + ">"; // no es una variable (un color «<#FFAC00>» en un nombre...): se queda como está
        }
        if (who == null) return "0";
        if (rest.startsWith("owner.")) {
            Who o = who.owner();
            if (o == null) return "0";
            who = o;
            rest = rest.substring(6);
        }
        if (rest.startsWith("var.")) {
            SkillRuntime.WhoState st = SkillRuntime.stateIfAny(who);
            return orZero(st == null ? null : st.vars.get(rest.substring(4)));
        }
        if (rest.startsWith("l.")) {
            String k = rest.substring(2);
            if (k.startsWith("yaw")) return SkillRuntime.fmt(who.yaw());
            if (k.startsWith("pitch")) return SkillRuntime.fmt(who.pitch());
            return location(who.pos(), k);
        }
        LivingEntity le = who.entity instanceof LivingEntity l ? l : null;
        return switch (rest) {
            case "name" -> who.entity != null ? who.entity.getName().getString() : who.actor.displayName();
            case "uuid" -> who.entity != null ? who.entity.getUUID().toString() : String.valueOf(who.actor.id);
            case "hp" -> SkillRuntime.fmt(le == null ? 0 : le.getHealth());
            case "mhp" -> SkillRuntime.fmt(le == null ? 0 : le.getMaxHealth());
            case "php" -> SkillRuntime.fmt(le == null ? 0 : le.getHealth() / Math.max(1, le.getMaxHealth()));
            case "damage" -> SkillRuntime.fmt(le == null ? 0 : le.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)
                    ? le.getAttributeValue(Attributes.ATTACK_DAMAGE) : 0);
            case "level" -> "1";
            default -> "0";
        };
    }

    private static String location(Vec3 p, String k) {
        if (p == null) return "0";
        if (k.startsWith("x")) return SkillRuntime.fmt(round(p.x));
        if (k.startsWith("y")) return SkillRuntime.fmt(round(p.y));
        if (k.startsWith("z")) return SkillRuntime.fmt(round(p.z));
        return "0";
    }

    private static double stat(ServerPlayer p, String stat) {
        if (p == null) return 0;
        return switch (stat) {
            case "attack_damage", "damage" -> p.getAttributeValue(Attributes.ATTACK_DAMAGE);
            case "max_health", "health" -> p.getMaxHealth();
            case "movement_speed" -> p.getAttributeValue(Attributes.MOVEMENT_SPEED);
            case "attack_speed" -> p.getAttributeValue(Attributes.ATTACK_SPEED);
            case "armor" -> p.getAttributeValue(Attributes.ARMOR);
            default -> 0; // skill_damage, crit... (estadísticas de MMO que aquí no hay: +0 %)
        };
    }

    private static String orZero(String s) {
        return s == null ? "0" : s;
    }

    private static double round(double d) {
        return Math.round(d * 1000.0) / 1000.0;
    }

    // ------------------------------------------------------------------------------------------- cuentas

    /** Resuelve una cuenta con + - * / y paréntesis. null si no es una cuenta. */
    static Double eval(String s) {
        try {
            Parser p = new Parser(s.replace(" ", ""));
            double v = p.expr();
            return p.pos == p.s.length() ? v : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static final class Parser {
        final String s;
        int pos;

        Parser(String s) {
            this.s = s;
        }

        double expr() {
            double v = term();
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == '+') {
                    pos++;
                    v += term();
                } else if (c == '-') {
                    pos++;
                    v -= term();
                } else {
                    break;
                }
            }
            return v;
        }

        double term() {
            double v = factor();
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == '*') {
                    pos++;
                    v *= factor();
                } else if (c == '/') {
                    pos++;
                    double d = factor();
                    v = d == 0 ? 0 : v / d;
                } else {
                    break;
                }
            }
            return v;
        }

        double factor() {
            if (pos < s.length() && s.charAt(pos) == '-') {
                pos++;
                return -factor();
            }
            if (pos < s.length() && s.charAt(pos) == '(') {
                pos++;
                double v = expr();
                if (pos < s.length() && s.charAt(pos) == ')') pos++;
                return v;
            }
            int start = pos;
            while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) pos++;
            if (start == pos) throw new IllegalStateException("no es un número");
            return Double.parseDouble(s.substring(start, pos));
        }
    }
}
