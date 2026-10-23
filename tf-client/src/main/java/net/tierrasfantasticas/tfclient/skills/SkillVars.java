/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
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
        // De dentro afuera, como MythicMobs: «<global.var.skin<caster.uuid>|scene_guards>» (los NPC de escenas) resuelve
        // primero <caster.uuid> y luego la variable con ese nombre
        String out = s;
        for (int pass = 0; pass < 4; pass++) {
            Matcher mt = TAG.matcher(out);
            StringBuilder sb = new StringBuilder();
            boolean changed = false;
            while (mt.find()) {
                String v = value(mt.group(1), ctx, target);
                changed |= !v.equals(mt.group());
                mt.appendReplacement(sb, Matcher.quoteReplacement(v));
            }
            mt.appendTail(sb);
            out = sb.toString();
            if (!changed || out.indexOf('<') < 0) break;
        }
        if (out.matches(".*\\d\\s*[*/+\\-]\\s*[\\d(].*") && out.matches("[\\d\\s.+\\-*/()]+")) {
            Double r = eval(out);
            if (r != null) return SkillRuntime.fmt(r);
        }
        return out;
    }

    private static String value(String tag, Ctx ctx, Tgt target) {
        String t = tag.trim();
        // «<caster.var.x|otro>»: lo que vale si la variable no está puesta (MythicMobs; sin él, 0)
        String dflt = null;
        int bar = t.indexOf('|');
        if (bar > 0 && t.indexOf('<') < 0) {
            dflt = t.substring(bar + 1);
            t = t.substring(0, bar).trim();
        }
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
        if (low.startsWith("skill.var.")) return or(ctx.vars == null ? null : ctx.vars.get(low.substring(10)), dflt);
        if (low.equals("skill.power")) return SkillRuntime.fmt(ctx.power);
        if (low.equals("skill.targets")) return String.valueOf(ctx.targets == null ? 1 : ctx.targets.size());
        if (low.startsWith("skill.")) {
            String k = low.substring(6);
            String param = ctx.params.get(k);
            if (param != null) return param.indexOf('<') >= 0 ? text(param, ctx, target) : param;
            if (ctx.mods.containsKey(k)) return SkillRuntime.fmt(ctx.mods.get(k));
            return orZero(ctx.vars == null ? null : ctx.vars.get(k));
        }
        // «<damage.modifier>» (el Pícaro, al revés en el pack): es <modifier.damage>
        if (low.equals("damage.modifier")) return SkillRuntime.fmt(ctx.mods.getOrDefault("damage", 0.0));
        if (low.startsWith("stat.")) return SkillRuntime.fmt(stat(ctx.caster.player(), low.substring(5)));
        if (low.startsWith("global.var.")) return or(SkillRuntime.varScope("global.x", ctx, null).get(low.substring(11)), dflt);
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
            return or(st == null ? null : st.vars.get(rest.substring(4)), dflt);
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
            case "damage" -> SkillRuntime.fmt(attackDamage(le));
            case "level" -> "1";
            default -> "0";
        };
    }

    /**
     * El daño de ataque de alguien (&lt;caster.damage&gt;). Las clases ya no dan su arma, así que un jugador pega como
     * mínimo con el arma de la clase (lo de una espada de diamante, 7); con una mejor en la mano, lo de esa.
     */
    static final double CLASS_WEAPON_DAMAGE = 7.0;

    private static double attackDamage(LivingEntity le) {
        if (le == null || !le.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)) return 0;
        double v = le.getAttributeValue(Attributes.ATTACK_DAMAGE);
        return le instanceof net.minecraft.world.entity.player.Player ? Math.max(CLASS_WEAPON_DAMAGE, v) : v;
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
            case "attack_damage", "damage" -> attackDamage(p);
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

    /** Una variable: lo que vale, o lo de después del «|» si no está puesta (o 0). */
    private static String or(String s, String dflt) {
        return s != null ? s : dflt != null ? dflt : "0";
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

    /**
     * Las cuentas de variablemath (la librería Crunch de MythicMobs): + - * / % ^ (potencia, de derecha a izquierda),
     * comparaciones (= != &lt; &gt; &lt;= &gt;=, que dan 1 o 0), &amp; | ! (y, o, no), paréntesis, «x» (lo que vale la
     * variable ahora), pi, e y las funciones sin, cos, tan, asin, acos, atan, sinh, cosh, tanh, sqrt, cbrt, ln, log (en
     * base 10), abs, round, ceil, floor, sign, rand (al azar hasta el número), min y max. null si no se entiende.
     */
    static Double math(String expr, double x) {
        try {
            MathParser p = new MathParser(expr.replace(" ", "").toLowerCase(java.util.Locale.ROOT), x);
            double v = p.or();
            return p.pos == p.s.length() ? v : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static final class MathParser {
        final String s;
        final double x;
        int pos;

        MathParser(String s, double x) {
            this.s = s;
            this.x = x;
        }

        boolean eat(String op) {
            if (s.startsWith(op, pos)) {
                pos += op.length();
                return true;
            }
            return false;
        }

        double or() {
            double v = and();
            while (pos < s.length() && s.charAt(pos) == '|') {
                pos += s.startsWith("||", pos) ? 2 : 1;
                double r = and();
                v = v != 0 || r != 0 ? 1 : 0;
            }
            return v;
        }

        double and() {
            double v = cmp();
            while (pos < s.length() && s.charAt(pos) == '&') {
                pos += s.startsWith("&&", pos) ? 2 : 1;
                double r = cmp();
                v = v != 0 && r != 0 ? 1 : 0;
            }
            return v;
        }

        double cmp() {
            double v = add();
            if (eat("==") || eat("=")) return v == add() ? 1 : 0;
            if (eat("!=")) return v != add() ? 1 : 0;
            if (eat("<=")) return v <= add() ? 1 : 0;
            if (eat(">=")) return v >= add() ? 1 : 0;
            if (eat("<")) return v < add() ? 1 : 0;
            if (eat(">")) return v > add() ? 1 : 0;
            return v;
        }

        double add() {
            double v = mul();
            while (pos < s.length()) {
                if (eat("+")) v += mul();
                else if (eat("-")) v -= mul();
                else break;
            }
            return v;
        }

        double mul() {
            double v = unary();
            while (pos < s.length()) {
                if (eat("*")) {
                    v *= unary();
                } else if (eat("/")) {
                    double d = unary();
                    v = d == 0 ? 0 : v / d;
                } else if (eat("%")) {
                    double d = unary();
                    v = d == 0 ? 0 : v % d;
                } else {
                    break;
                }
            }
            return v;
        }

        double unary() {
            if (eat("-")) return -unary();
            if (eat("+")) return unary();
            if (pos < s.length() && s.charAt(pos) == '!' && !s.startsWith("!=", pos)) {
                pos++;
                return unary() == 0 ? 1 : 0;
            }
            return pow();
        }

        double pow() {
            double v = atom();
            if (eat("^")) return Math.pow(v, unary());
            return v;
        }

        double atom() {
            if (eat("(")) {
                double v = or();
                eat(")");
                return v;
            }
            int start = pos;
            if (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) {
                while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) pos++;
                if (pos < s.length() && s.charAt(pos) == 'e' && pos + 1 < s.length()
                        && (Character.isDigit(s.charAt(pos + 1)) || s.charAt(pos + 1) == '-')) {
                    pos += 2;
                    while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
                }
                return Double.parseDouble(s.substring(start, pos));
            }
            // (nombres con cifras después de la primera letra: «atan2»; 1.3.88, el giro en vuelo de Ignevar)
            while (pos < s.length() && (Character.isLetter(s.charAt(pos)) || pos > start && Character.isDigit(s.charAt(pos)))) pos++;
            String id = s.substring(start, pos);
            if (id.isEmpty()) throw new IllegalStateException("no es un número");
            if (!eat("(")) {
                return switch (id) {
                    case "x" -> x;
                    case "pi" -> Math.PI;
                    case "e" -> Math.E;
                    case "true" -> 1;
                    case "false" -> 0;
                    default -> throw new IllegalStateException("no sé qué es " + id);
                };
            }
            java.util.List<Double> args = new java.util.ArrayList<>();
            if (!eat(")")) {
                do {
                    args.add(or());
                } while (eat(","));
                eat(")");
            }
            double a = args.isEmpty() ? 0 : args.get(0);
            return switch (id) {
                case "sin" -> Math.sin(a);
                case "cos" -> Math.cos(a);
                case "tan" -> Math.tan(a);
                case "asin" -> Math.asin(a);
                case "acos" -> Math.acos(a);
                case "atan" -> Math.atan(a);
                case "atan2" -> Math.atan2(a, args.size() > 1 ? args.get(1) : 1);
                case "pow" -> Math.pow(a, args.size() > 1 ? args.get(1) : 1);
                case "exp" -> Math.exp(a);
                case "hypot" -> Math.hypot(a, args.size() > 1 ? args.get(1) : 0);
                case "sinh" -> Math.sinh(a);
                case "cosh" -> Math.cosh(a);
                case "tanh" -> Math.tanh(a);
                case "sqrt" -> Math.sqrt(a);
                case "cbrt" -> Math.cbrt(a);
                case "ln" -> Math.log(a);
                case "log" -> Math.log10(a);
                case "abs" -> Math.abs(a);
                case "round" -> Math.round(a);
                case "ceil" -> Math.ceil(a);
                case "floor" -> Math.floor(a);
                case "sign", "signum" -> Math.signum(a);
                case "rand", "random" -> java.util.concurrent.ThreadLocalRandom.current().nextDouble() * (args.isEmpty() ? 1 : a);
                case "min" -> args.stream().mapToDouble(Double::doubleValue).min().orElse(0);
                case "max" -> args.stream().mapToDouble(Double::doubleValue).max().orElse(0);
                default -> throw new IllegalStateException("no sé qué es " + id + "()");
            };
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
