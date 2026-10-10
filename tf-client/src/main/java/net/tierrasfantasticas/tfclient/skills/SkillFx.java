package net.tierrasfantasticas.tfclient.skills;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.tierrasfantasticas.tfclient.skills.SkillDefs.Mech;
import net.tierrasfantasticas.tfclient.skills.SkillRuntime.Ctx;
import net.tierrasfantasticas.tfclient.skills.SkillRuntime.Tgt;
import net.tierrasfantasticas.tfclient.skills.SkillRuntime.Who;

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

    /** hide{duration} / showentity: quien lanza no se ve (para todos, con lo que lleva) un rato, o se vuelve a ver. */
    static void hide(Mech m, Ctx ctx, List<Tgt> targets, boolean hide) {
        int ticks = hide ? (int) Math.max(1, SkillRuntime.num(m.arg("40", "duration", "d", "ticks", "t"), 40)) : 0;
        for (Tgt t : targets) {
            if (t.entity() == null) continue;
            Vec3 p = t.pos();
            send(ctx.caster.level(), p, new SkillNet.Fx(5, String.valueOf(t.entity().getId()), p.x, p.y, p.z, ticks, 0, 0, 0, 0, 0,
                    null, 0, 0, 0, 0));
        }
    }

    /**
     * sendtitle{title;subtitle;d;fi;fo}: título en la pantalla del jugador. Las letras que son imágenes del pack (la
     * pantalla de la Bruja) van con la fuente de la clase.
     */
    static void title(Mech m, Ctx ctx, List<Tgt> targets) {
        String title = SkillRuntime.unquote(m.arg("", "title", "t"));
        String sub = SkillRuntime.unquote(m.arg("", "subtitle", "st", "s"));
        int stay = (int) SkillRuntime.num(m.arg("40", "duration", "d", "stay"), 40);
        int fadeIn = (int) SkillRuntime.num(m.arg("0", "fadein", "fi"), 0);
        int fadeOut = (int) SkillRuntime.num(m.arg("0", "fadeout", "fo"), 0);
        for (Tgt t : targets) {
            if (!(t.entity() instanceof net.minecraft.server.level.ServerPlayer p)) continue;
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(styled(sub, ctx)));
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(styled(title, ctx)));
        }
    }

    /** Un texto de un pack: sus letras-imagen con la fuente de la clase; el resto, normal (sin los &códigos). */
    static net.minecraft.network.chat.Component styled(String text, Ctx ctx) {
        String clean = text.replaceAll("[&§][0-9a-fk-orA-FK-OR]", "");
        String glyphs = ctx.cls.glyphs;
        net.minecraft.network.chat.MutableComponent out = net.minecraft.network.chat.Component.empty();
        net.minecraft.network.chat.Style font = net.minecraft.network.chat.Style.EMPTY.withFont(
                new net.minecraft.resources.ResourceLocation(net.tierrasfantasticas.tfclient.TFClient.MOD_ID, "skills_" + ctx.cls.id))
                .withColor(net.minecraft.ChatFormatting.WHITE);
        StringBuilder plain = new StringBuilder();
        clean.codePoints().forEach(cp -> {
            String ch = new String(Character.toChars(cp));
            if (!glyphs.isEmpty() && glyphs.contains(ch)) {
                if (plain.length() > 0) {
                    out.append(net.minecraft.network.chat.Component.literal(plain.toString()));
                    plain.setLength(0);
                }
                out.append(net.minecraft.network.chat.Component.literal(ch).withStyle(font));
            } else {
                plain.append(ch);
            }
        });
        if (plain.length() > 0) out.append(net.minecraft.network.chat.Component.literal(plain.toString()));
        return out;
    }

    /**
     * slash{w;h;a;roll;rot;d;p;fo;y;op}: un tajo dibujado: un arco de elipse (ancho w, alto h, «a» grados) delante del
     * objetivo, girado «roll» sobre la dirección a la que mira quien lanza, que se va dibujando en «d» ticks; en cada
     * punto se lanza la skill «op» (sus partículas).
     */
    static void slash(Mech m, Ctx ctx, List<Tgt> targets) {
        SkillDefs.Meta onPoint = ctx.cls.meta(m.arg(null, "onpoint", "op", "onpointskill"));
        SkillDefs.Meta onHit = ctx.cls.meta(m.arg(null, "onhit", "oh", "onhitskill"));
        double hr = Math.max(0, SkillRuntime.num(m.arg("1", "radius", "r", "hitradius", "hr"), 1));
        double w = SkillRuntime.num(m.arg("3", "width", "w"), 3) / 2;
        double h = SkillRuntime.num(m.arg(String.valueOf(w * 2), "height", "h"), w * 2) / 2;
        double arc = Math.toRadians(SkillRuntime.num(m.arg("180", "angle", "a", "arc"), 180));
        double roll = SkillRuntime.num(m.arg("0", "roll", "rl"), 0);
        String rot = m.arg(null, "rot", "rotation");
        if (rot != null) {
            String[] r = rot.split(",");
            if (r.length >= 3) roll += SkillRuntime.num(r[2], 0);
        }
        int dur = (int) Math.max(0, SkillRuntime.num(m.arg("0", "duration", "d"), 0));
        int points = (int) Math.max(2, Math.min(240, SkillRuntime.num(m.arg("30", "points", "p"), 30)));
        double fo = SkillRuntime.num(m.arg("0", "forwardoffset", "fo"), 0);
        double yo = SkillRuntime.num(m.arg("0", "yoffset", "y"), 0);
        if (onPoint == null && onHit == null) return;
        float yaw = ctx.caster.yaw();
        Vec3 fwd = SkillRuntime.dir(yaw, 0F), right = SkillRuntime.dir(yaw + 90F, 0F), up = new Vec3(0, 1, 0);
        double rr = Math.toRadians(roll);
        // el plano del tajo: horizontal (right/fwd) girado «roll» alrededor de fwd
        Vec3 axisA = right.scale(Math.cos(rr)).add(up.scale(Math.sin(rr)));
        for (Tgt t : targets) {
            Vec3 c = t.pos().add(fwd.scale(fo)).add(0, yo, 0);
            // cada tajo le da una sola vez a cada uno (onHit a los que pasan a «r» bloques de un punto)
            Set<UUID> hit = new HashSet<>();
            for (int i = 0; i < points; i++) {
                double k = (double) i / (points - 1);
                double ang = -arc / 2 + arc * k;
                // de un lado al otro por delante (la elipse va hacia delante: «h» es lo que entra)
                Vec3 p = c.add(axisA.scale(Math.sin(ang) * w)).add(fwd.scale(Math.cos(ang) * h - h * 0.5));
                int when = dur <= 0 ? 0 : (int) Math.round(k * dur);
                SkillRuntime.later(when, () -> {
                    if (onPoint != null) point(ctx, onPoint, p);
                    if (onHit != null) slashHit(ctx, onHit, p, hr, hit);
                });
            }
        }
    }

    /**
     * polygon{p;skip;scale;yaw;db;y;os;oe}: un polígono (o estrella: une cada punto con el que está «skip» más allá)
     * en el suelo, de «scale» bloques; en las esquinas la skill «os» y por los lados, cada «db» bloques, la «oe».
     */
    static void polygon(Mech m, Ctx ctx, List<Tgt> targets) {
        int pts = (int) Math.max(1, Math.min(64, SkillRuntime.num(m.arg("5", "points", "p"), 5)));
        int skip = (int) Math.max(1, SkillRuntime.num(m.arg("1", "skip", "s"), 1));
        double scale = SkillRuntime.num(m.arg("3", "scale", "radius", "r"), 3);
        double yaw0 = Math.toRadians(SkillRuntime.num(m.arg("0", "yaw", "rotation"), 0));
        double db = Math.max(0.1, SkillRuntime.num(m.arg("0.5", "distancebetween", "db"), 0.5));
        double y = SkillRuntime.num(m.arg("0", "yoffset", "y"), 0);
        SkillDefs.Meta corner = ctx.cls.meta(m.arg(null, "onstart", "os", "onvertex"));
        SkillDefs.Meta edge = ctx.cls.meta(m.arg(null, "onend", "oe", "onpoint", "op"));
        if (corner == null && edge == null) return;
        for (Tgt t : targets) {
            Vec3 c = t.pos().add(0, y, 0);
            Vec3[] v = new Vec3[pts];
            for (int i = 0; i < pts; i++) {
                double a = yaw0 + Math.PI * 2 * i / pts;
                v[i] = c.add(Math.cos(a) * scale, 0, Math.sin(a) * scale);
                if (corner != null) point(ctx, corner, v[i]);
            }
            if (edge == null || skip % pts == 0) continue;
            for (int i = 0; i < pts; i++) {
                Vec3 a = v[i], b = v[(i + skip) % pts];
                int n = (int) Math.min(200, Math.ceil(a.distanceTo(b) / db));
                for (int k = 1; k < n; k++) point(ctx, edge, a.lerp(b, (double) k / n));
            }
        }
    }

    private static void slashHit(Ctx ctx, SkillDefs.Meta skill, Vec3 p, double r, Set<UUID> hit) {
        ServerLevel level = ctx.caster.level();
        if (level == null) return;
        AABB box = new AABB(p, p).inflate(r);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (!e.isAlive() || e.isSpectator() || e instanceof ArmorStand) continue;
            if (e == ctx.caster.entity || e == ctx.caster.player() || hit.contains(e.getUUID())) continue;
            if (!SkillRuntime.canHurt(ctx, e)) continue;
            hit.add(e.getUUID());
            Ctx c = ctx.copy();
            c.origin = p;
            c.targets = List.of(Tgt.of(Who.of(e)));
            SkillRuntime.runMeta(skill, c);
        }
    }

    private static void point(Ctx ctx, SkillDefs.Meta skill, Vec3 p) {
        Ctx c = ctx.copy();
        c.origin = p;
        c.targets = List.of(Tgt.at(p));
        SkillRuntime.runMeta(skill, c);
    }

    private static void send(ServerLevel level, Vec3 at, SkillNet.Fx fx) {
        SkillNet.near(level, at, fx);
    }
}
