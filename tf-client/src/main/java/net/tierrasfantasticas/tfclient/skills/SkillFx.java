package net.tierrasfantasticas.tfclient.skills;

import java.util.List;
import java.util.Locale;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.tierrasfantasticas.tfclient.skills.SkillDefs.Mech;
import net.tierrasfantasticas.tfclient.skills.SkillRuntime.Ctx;
import net.tierrasfantasticas.tfclient.skills.SkillRuntime.Tgt;

/** Partículas y sonidos: el servidor dice dónde y cuántas; cada jugador cercano las saca (SkillClient). */
final class SkillFx {
    private SkillFx() {}

    /** Punto de una partícula: el objetivo, subido «y» y movido delante/al lado según hacia dónde mira quien lanza. */
    private static Vec3 point(Mech m, Ctx ctx, Tgt t) {
        Vec3 p = t.pos();
        double y = SkillRuntime.num(m.arg("0", "y", "yoffset"), 0);
        double fwd = SkillRuntime.num(m.arg("0", "forwardoffset", "fo", "startforwardoffset"), 0);
        double side = SkillRuntime.num(m.arg("0", "sideoffset", "so"), 0);
        if (fwd != 0 || side != 0) {
            float yaw = ctx.caster.yaw();
            p = p.add(SkillRuntime.dir(yaw, 0F).scale(fwd)).add(SkillRuntime.dir(yaw + 90F, 0F).scale(side));
        }
        return p.add(0, y, 0);
    }

    private static String particle(Mech m) {
        return m.arg("reddust", "particle", "p", "part").toLowerCase(Locale.ROOT);
    }

    /** Color, bloque o colores de la partícula (lo que necesita para dibujarse). */
    private static String extra(Mech m) {
        String p = particle(m);
        if (p.contains("block") || p.contains("falling_dust") || p.equals("blockcrack") || p.equals("block_dust")) {
            return m.arg("stone", "material", "m", "block", "b").toLowerCase(Locale.ROOT);
        }
        if (p.contains("transition")) {
            return m.arg("#ffffff", "color1", "color", "c") + ">" + m.arg("#ffffff", "color2", "tocolor");
        }
        if (p.equals("item")) return m.arg("stone", "material", "m", "item").toLowerCase(Locale.ROOT);
        return m.arg("", "color", "c");
    }

    private static float size(Mech m) {
        return (float) SkillRuntime.num(m.arg("1", "size"), 1);
    }

    static void particles(Mech m, Ctx ctx, List<Tgt> targets) {
        int n = (int) Math.min(400, SkillRuntime.num(m.arg("10", "amount", "a", "count"), 10));
        float hs = (float) SkillRuntime.num(m.arg("0", "hspread", "hs", "xspread", "spread"), 0);
        float vs = (float) SkillRuntime.num(m.arg("0", "vspread", "vs", "yspread"), 0);
        float speed = (float) SkillRuntime.num(m.arg("0", "speed", "s"), 0);
        String id = particle(m), extra = extra(m);
        float size = size(m);
        for (Tgt t : targets) {
            Vec3 p = point(m, ctx, t);
            send(ctx.caster.level(), p, new SkillNet.Fx(0, id, p.x, p.y, p.z, n, hs, vs, speed, 0, 0, extra, size, 0, 0, 0));
        }
    }

    static void ring(Mech m, Ctx ctx, List<Tgt> targets) {
        int n = (int) Math.min(64, SkillRuntime.num(m.arg("1", "amount", "a"), 1));
        float r = (float) SkillRuntime.num(m.arg("5", "radius", "r"), 5);
        int points = (int) Math.min(256, SkillRuntime.num(m.arg("8", "points", "p"), 8));
        float hs = (float) SkillRuntime.num(m.arg("0", "hspread", "hs"), 0);
        float vs = (float) SkillRuntime.num(m.arg("0", "vspread", "vs"), 0);
        float speed = (float) SkillRuntime.num(m.arg("0", "speed", "s"), 0);
        String id = particle(m), extra = extra(m);
        for (Tgt t : targets) {
            Vec3 p = point(m, ctx, t);
            send(ctx.caster.level(), p, new SkillNet.Fx(2, id, p.x, p.y, p.z, n, hs, vs, speed, r, points, extra, size(m), 0, 0, 0));
        }
    }

    static void sphere(Mech m, Ctx ctx, List<Tgt> targets) {
        int n = (int) Math.min(400, SkillRuntime.num(m.arg("10", "amount", "a"), 10));
        float r = (float) SkillRuntime.num(m.arg("1", "radius", "r"), 1);
        float speed = (float) SkillRuntime.num(m.arg("0", "speed", "s"), 0);
        String id = particle(m), extra = extra(m);
        for (Tgt t : targets) {
            Vec3 p = point(m, ctx, t);
            send(ctx.caster.level(), p, new SkillNet.Fx(1, id, p.x, p.y, p.z, n, 0, 0, speed, r, 0, extra, size(m), 0, 0, 0));
        }
    }

    /** Partículas que dan vueltas alrededor del objetivo durante «ticks»: un punto del anillo cada intervalo. */
    static void orbital(Mech m, Ctx ctx, List<Tgt> targets) {
        float r = (float) SkillRuntime.num(m.arg("1", "radius", "r"), 1);
        int points = (int) Math.max(1, SkillRuntime.num(m.arg("16", "points", "p"), 16));
        int ticks = (int) Math.min(400, SkillRuntime.num(m.arg("20", "ticks", "t", "duration", "d"), 20));
        int interval = (int) Math.max(1, SkillRuntime.num(m.arg("1", "interval", "i"), 1));
        double oy = SkillRuntime.num(m.arg("0", "offsety", "oy", "y"), 0);
        int n = (int) Math.min(32, SkillRuntime.num(m.arg("1", "amount", "a"), 1));
        String id = particle(m), extra = extra(m);
        for (Tgt t : targets) {
            for (int k = 0; k * interval < ticks; k++) {
                final int step = k;
                SkillRuntime.later(k * interval, () -> {
                    Vec3 c = t.pos();
                    double a = Math.PI * 2 * step / points;
                    Vec3 p = c.add(Math.cos(a) * r, oy, Math.sin(a) * r);
                    send(ctx.caster.level(), p, new SkillNet.Fx(0, id, p.x, p.y, p.z, n, 0, 0, 0, 0, 0, extra, size(m), 0, 0, 0));
                });
            }
        }
    }

    /** Línea de partículas desde quien lanza (o el origen) hasta el objetivo. */
    static void line(Mech m, Ctx ctx, List<Tgt> targets) {
        boolean fromOrigin = SkillRuntime.bool(m.arg("false", "fromorigin", "fo"));
        Vec3 from = (fromOrigin ? SkillTargets.origin(ctx) : ctx.caster.pos()).add(0, SkillRuntime.num(m.arg("1", "startyoffset", "syo"), 1), 0);
        float spacing = (float) Math.max(0.1, SkillRuntime.num(m.arg("0.25", "distancebetween", "db"), 0.25));
        int n = (int) Math.min(16, SkillRuntime.num(m.arg("1", "amount", "a"), 1));
        String id = particle(m), extra = extra(m);
        for (Tgt t : targets) {
            Vec3 to = t.pos().add(0, SkillRuntime.num(m.arg("1", "targetyoffset", "tyo"), 1), 0);
            send(ctx.caster.level(), from, new SkillNet.Fx(3, id, from.x, from.y, from.z, n, 0, 0, 0, spacing, 0, extra, size(m),
                    to.x, to.y, to.z));
        }
    }

    static void sound(Mech m, Ctx ctx, List<Tgt> targets) {
        String s = m.arg(null, "sound", "s");
        if (s == null || s.isBlank()) return;
        float vol = (float) SkillRuntime.num(m.arg("1", "volume", "v"), 1);
        float pitch = (float) SkillRuntime.num(m.arg("1", "pitch", "p"), 1);
        for (Tgt t : targets) send(ctx.caster.level(), t.pos(), SkillNet.Fx.sound(s, t.pos(), vol, pitch));
    }

    static void lightning(Mech m, Ctx ctx, List<Tgt> targets) {
        for (Tgt t : targets) {
            Vec3 p = t.pos();
            // Solo el efecto (sin fuego ni daño del rayo de verdad)
            net.minecraft.world.entity.LightningBolt bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(ctx.caster.level());
            if (bolt == null) continue;
            bolt.moveTo(p);
            bolt.setVisualOnly(true);
            ctx.caster.level().addFreshEntity(bolt);
        }
    }

    /** blockmask: el suelo se ve de otro bloque un rato (solo para la vista, sin tocar el mundo). */
    static void blockmask(Mech m, Ctx ctx, List<Tgt> targets) {
        String block = m.arg("stone", "material", "m", "block", "b").toLowerCase(Locale.ROOT);
        float r = (float) SkillRuntime.num(m.arg("3", "radius", "r"), 3);
        int ticks = (int) SkillRuntime.num(m.arg("40", "duration", "d"), 40);
        for (Tgt t : targets) {
            Vec3 p = t.pos();
            send(ctx.caster.level(), p, new SkillNet.Fx(4, block, p.x, p.y, p.z, ticks, 0, 0, 0, r, 0, null, 1, 0, 0, 0));
        }
    }

    private static void send(ServerLevel level, Vec3 at, SkillNet.Fx fx) {
        SkillNet.near(level, at, fx);
    }
}
