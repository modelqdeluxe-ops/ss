package net.tierrasfantasticas.tfclient.skills;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.skills.SkillDefs.Cond;
import net.tierrasfantasticas.tfclient.skills.SkillDefs.Mech;
import net.tierrasfantasticas.tfclient.skills.SkillDefs.MobDef;
import net.tierrasfantasticas.tfclient.skills.SkillRuntime.Ctx;
import net.tierrasfantasticas.tfclient.skills.SkillRuntime.Tgt;
import net.tierrasfantasticas.tfclient.skills.SkillRuntime.Who;

/**
 * Lo que se mueve solo: proyectiles (projectile), misiles que persiguen (missile), tótems quietos (totem) y orbitales
 * que dan vueltas a alguien (orbital). Cada uno con sus skills al salir (onStart), en cada paso (onTick), al tocar
 * (onHit), al chocar con un bloque (onHitBlock) y al acabar (onEnd), y con un efecto que lo acompaña si es de tipo MOB.
 * Las opciones de MythicMobs que usan los packs: pegado al suelo (hugSurface + heightFromSurface: las ondas que van por
 * el suelo), salir desde el objetivo (targetIsOrigin), a quién puede tocar (hitConditions, hitTargetOnly), cada cuánto
 * puede volver a tocar al mismo (immuneDelay), cuándo pararse (stopConditions), y los orbitales con nombre de aura
 * (se quitan con auraremove y se miran con hasaura).
 */
final class SkillMovers {
    private static final List<Mover> MOVERS = new ArrayList<>();
    private static final int MAX = 1500;

    private SkillMovers() {}

    static final class Mover {
        final Ctx ctx;
        final int kind; // 0 proyectil, 1 misil, 2 tótem, 3 orbital
        final ServerLevel level;
        Vec3 pos;
        Vec3 dir;
        double step;
        int interval;
        double maxRange;
        int maxTicks;
        double hr;
        double vr;
        boolean hitPlayers;
        boolean hitNonPlayers;
        boolean stopAtEntity;
        boolean stopAtBlock;
        int charges;
        int hits;
        /** Prueba en el juego: tocó a un jugador. */
        boolean hitPlayer;
        double gravity;
        String onTick;
        String onHit;
        String onEnd;
        String onHitBlock;
        SkillActor bullet;
        /** vfx{} de ModelEngine pegados a este proyectil (van con él y se quitan con él). */
        final List<SkillActor> vfx = new java.util.ArrayList<>();
        double bulletY;
        double bulletForward;
        int deathDelay;
        /** Cuándo tocó a cada uno y cada cuántos ticks puede volver a tocar al mismo (immuneDelay). */
        final Map<Object, Long> hit = new HashMap<>();
        int rehit;
        List<Cond> hitConds = List.of();
        List<Cond> stopConds = List.of();
        LivingEntity onlyTarget;
        boolean hug;
        double hugHeight;
        double climb;
        /** Misil: el ser al que persigue (a «homeY» de sus pies) o el sitio fijo al que va. */
        LivingEntity homing;
        double homeY;
        Vec3 homeAt;
        double inertia;
        /** ac: la velocidad se multiplica por esto cada paso (el fuego de los lanzallamas frena). */
        double accel = 1.0;
        /** bounce: rebota en el suelo (bv: lo que conserva de velocidad). */
        boolean bounce;
        double bounceVel = 0.5;
        int age;
        double travelled;
        /** Dónde salió: el alcance (maxRange) se mide en línea recta desde aquí, como MythicMobs (maxDistanceSquared). */
        Vec3 start;
        boolean alive = true;
        // orbital
        Who center;
        double radius;
        double angle;
        double angleStep;
        double oy;
        double rx;
        double ry;
        double rz;
        double avx;
        double avy;
        double avz;
        SkillAuras.Aura aura;
        /** shoot/shootfireball: en MythicMobs es un proyectil de Minecraft (sus disparadores son ~onShoot y ~onBowHit). */
        boolean vanilla;
        /** El mob de jefe que lo lanza escucha ~onProjectileHit/~onBowHit: mira a quién toca aunque no tenga onHit. */
        boolean hitTrigger;
        /** chainmissile: los saltos que le quedan (null = un misil normal). */
        Chain chain;

        Mover(Ctx ctx, int kind, ServerLevel level) {
            this.ctx = ctx;
            this.kind = kind;
            this.level = level;
        }
    }

    /** Lo de un chainmissile: saltos que quedan, a cuánto, si vuelve a quien lo lanzó y a quién ya ha tocado. */
    static final class Chain {
        int left;
        double radius;
        boolean back;
        boolean returning;
        List<Cond> conds = List.of();
        final java.util.Set<java.util.UUID> visited = new java.util.HashSet<>();
    }

    // ------------------------------------------------------------------------------------------- lanzar

    static void projectile(Mech m, Ctx ctx, List<Tgt> targets, boolean missile) {
        projectile(m, ctx, targets, missile, false, null);
    }

    /**
     * chainmissile{bounces=2;bounceradius=5;returntocaster=false;bounceconditions} (y todo lo de missile): un misil que,
     * al tocar a su objetivo, salta al más cercano a «bounceradius» que no haya tocado (y cumpla bounceConditions),
     * «bounces» veces; luego vuelve a quien lo lanzó (returnToCaster) o se acaba. Su onHit sale en cada uno.
     */
    static void chainMissile(Mech m, Ctx ctx, List<Tgt> targets) {
        int bounces = (int) Math.max(0, Math.min(64, SkillRuntime.num(m.arg("2", "bounces", "b"), 2)));
        double radius = SkillRuntime.num(m.arg("5", "bounceradius", "bouncerange", "radius", "range", "r"), 5);
        boolean back = SkillRuntime.bool(m.arg("false", "returntocaster", "return", "rtc"));
        projectile(m, ctx, targets, true, false, p -> {
            Chain c = new Chain();
            c.left = bounces;
            c.radius = radius;
            c.back = back;
            c.conds = m.bc;
            p.chain = c;
        });
    }

    private static void projectile(Mech m, Ctx ctx, List<Tgt> targets, boolean missile, boolean vanilla,
                                   java.util.function.Consumer<Mover> setup) {
        // projectile{type=METEOR}: cae desde «hfs» bloques por encima del objetivo (la sangre del Grimorio del Vampiro)
        boolean meteor = !missile && m.arg("NORMAL", "type").equalsIgnoreCase("METEOR");
        for (Tgt t : targets) {
            if (MOVERS.size() >= MAX) return;
            Mover p = new Mover(ctx.copy(), missile ? 1 : 0, ctx.caster.level());
            p.vanilla = vanilla;
            if (setup != null) setup.accept(p);
            SkillMobs.Host h = host(p);
            p.hitTrigger = h != null && SkillMobs.listens(h, vanilla ? "onbowhit" : "onprojectilehit");
            common(p, m);
            boolean fromOrigin = SkillRuntime.bool(m.arg("false", "fromorigin", "fo"));
            boolean targetIsOrigin = SkillRuntime.bool(m.arg("false", "targetisorigin", "tio"));
            Vec3 base = targetIsOrigin ? t.pos() : fromOrigin ? SkillTargets.origin(ctx) : ctx.caster.pos();
            double syo = SkillRuntime.num(m.arg("1", "startyoffset", "syo"), 1);
            double sfo = SkillRuntime.num(m.arg("1", "startforwardoffset", "startfoffset", "forwardoffset", "sfo"), 1);
            double side = SkillRuntime.num(m.arg("0", "sideoffset", "soffset", "so"), 0);
            double sso = SkillRuntime.num(m.arg(null, "startsideoffset", "ssoffset", "sso"), side);
            double eso = SkillRuntime.num(m.arg(null, "endsideoffset", "endoffset", "esoffset", "eso"), side);
            double tyo = SkillRuntime.num(m.arg("0", "targetyoffset", "targety", "tyo"), 0);
            double tso = SkillRuntime.num(m.arg("0", "targetsideoffset", "tso"), 0);
            // Salida: a «syo» de los pies, «sfo» por delante y «sso» al lado según hacia dónde mira quien lanza
            float cy = ctx.caster.yaw();
            Vec3 start = base.add(0, syo, 0).add(SkillRuntime.dir(cy, 0F).scale(sfo)).add(SkillRuntime.dir(cy + 90F, 0F).scale(sso));
            // Adónde apunta (como MythicMobs): un proyectil, a media altura de un ser (o al sitio) + tyo; un misil, a los
            // pies (luego persigue a la altura de los ojos, o a tyo)
            // En un jefe, el misil también sale hacia el centro del objetivo: a los pies y con mucha inercia (la esfera de
            // Astwood, inertia=100, casi no gira) se clavaba en el suelo antes de llegar
            // tyo («dónde, sobre el objetivo») solo cuando el objetivo es un ser, como MythicMobs: a un sitio va derecho (el
            // área del Archivista, tyo=64 a sitios al azar, salía al cielo y estallaba en el aire)
            boolean onBeing = t.entity() != null || t.who != null && t.who.actor != null;
            Vec3 aimAt = t.pos().add(0, missile && ctx.host == null ? 0 : (onBeing ? tyo : 0) + halfHeight(t), 0);
            if (eso != 0) aimAt = aimAt.add(SkillRuntime.dir(cy + 90F, 0F).scale(eso));
            if (tso != 0) aimAt = aimAt.add(SkillRuntime.dir(SkillRuntime.yawOf(aimAt.subtract(start)) + 90F, 0F).scale(tso));
            Vec3 dir = targetIsOrigin ? ctx.caster.forward() : aimAt.subtract(start);
            // startingDirection (sdir) de un misil: sale hacia ese sitio y luego gira hacia su objetivo (la Tormenta del
            // Wither escupe desde cada boca hacia delante); en un proyectil no hace nada, como en MythicMobs
            String sdir = missile ? m.a.get("sdir") != null ? m.a.get("sdir") : m.a.getOrDefault("startingdirection", m.a.get("startdir")) : null;
            if (sdir != null && sdir.trim().startsWith("@")) {
                List<Tgt> sd = SkillTargets.fromString(sdir, ctx);
                if (!sd.isEmpty() && sd.get(0).pos().distanceToSqr(start) > 1e-4) dir = sd.get(0).pos().subtract(start);
            }
            if (dir.lengthSqr() < 1e-6) dir = ctx.caster.forward();
            dir = dir.normalize();
            double ho = SkillRuntime.num(m.arg("0", "horizontaloffset", "ho"), 0);
            double vo = SkillRuntime.num(m.arg("0", "verticaloffset", "vo"), 0);
            // como MythicMobs: «ho» gira la dirección (grados) y «vo» se suma a su Y (el granizo de Glacia cae casi
            // en vertical con vo=-5)
            if (ho != 0) dir = SkillRuntime.dir(SkillRuntime.yawOf(dir) + (float) ho, SkillRuntime.pitchOf(dir));
            if (vo != 0) {
                Vec3 d2 = dir.add(0, vo, 0);
                if (d2.lengthSqr() > 1e-6) dir = d2.normalize();
            }
            Vec3 flat = new Vec3(dir.x, 0, dir.z);
            flat = flat.lengthSqr() < 1e-6 ? Vec3.ZERO : flat.normalize();
            p.pos = start;
            p.start = start;
            p.dir = dir;
            double v = SkillRuntime.num(m.arg("5", "velocity", "v"), 5);
            p.step = v / 20.0 * p.interval;
            p.maxRange = SkillRuntime.num(m.arg("40", "maxrange", "mr"), 40);
            p.maxTicks = (int) SkillRuntime.num(m.arg("400", "maxduration", "md", "duration", "d"), 400);
            p.gravity = SkillRuntime.num(m.arg("0", "gravity", "g"), 0) / 20.0;
            p.hug = SkillRuntime.bool(m.arg("false", "hugsurface", "hs"));
            p.hugHeight = SkillRuntime.num(m.arg("0.5", "heightfromsurface", "hfs"), 0.5);
            p.climb = SkillRuntime.num(m.arg("1", "maxclimbheight", "mch"), 1);
            if (meteor) {
                // Sale encima del objetivo y cae con la gravedad (o, sin ella, con la velocidad) hasta el suelo
                p.pos = aimAt.add(0, p.hugHeight, 0);
                p.dir = new Vec3(0, -1, 0);
                p.step = 0;
                p.gravity = (p.gravity > 0 ? p.gravity : v / 20.0);
                p.hug = false;
            } else if (p.hug) {
                // Pegado al suelo: va en horizontal a «hfs» del suelo
                p.dir = flat.lengthSqr() < 1e-6 ? SkillRuntime.dir(ctx.caster.yaw(), 0F) : flat;
                Vec3 g = SkillMinions.ground(p.level, p.pos, p.pos.y);
                p.pos = new Vec3(p.pos.x, g.y + p.hugHeight, p.pos.z);
            }
            if (SkillRuntime.bool(m.arg("false", "hittargetonly", "hto"))) p.onlyTarget = t.living();
            if (missile) {
                // Persigue al ser (a la altura de sus ojos, o a tyo de sus pies) o va al sitio (+ tyo)
                p.homing = t.living();
                if (p.homing != null) p.homeY = tyo != 0 ? tyo : ctx.host != null ? p.homing.getBbHeight() * 0.55 : p.homing.getEyeHeight();
                else p.homeAt = t.pos().add(0, tyo, 0);
                p.inertia = Math.max(0.1, SkillRuntime.num(m.arg("1.5", "inertia", "in"), 1.5));
                // En un jefe, que la inercia no le impida llegar: como mucho 12 (la de 100 del pack apenas corregía ni 1°
                // por paso y un jugador andando ya no estaba donde iba)
                if (ctx.host != null) p.inertia = Math.min(p.inertia, 12);
            }
            p.accel = SkillRuntime.num(m.arg("1", "acceleration", "accel", "ac"), 1);
            p.bounce = SkillRuntime.bool(m.arg("false", "bounce"));
            p.bounceVel = SkillRuntime.num(m.arg("0.5", "bouncevelocity", "bv"), 0.5);
            p.ctx.dir = p.dir;
            start(p, m);
        }
    }

    /** Media altura de un objetivo (MythicMobs apunta los proyectiles a la mitad de los ojos de un ser); un sitio, 0. */
    private static double halfHeight(Tgt t) {
        if (t.entity() != null) return t.entity().getEyeHeight() / 2.0;
        if (t.who != null && t.who.actor != null) return t.who.actor.def != null && t.who.actor.def.small ? 0.44 : 0.89;
        return 0;
    }

    static void totem(Mech m, Ctx ctx, List<Tgt> targets) {
        for (Tgt t : targets) {
            if (MOVERS.size() >= MAX) return;
            Mover p = new Mover(ctx.copy(), 2, ctx.caster.level());
            common(p, m);
            // como MythicMobs: un bloque por encima del sitio si no dice otra cosa
            p.pos = t.pos().add(0, SkillRuntime.num(m.arg("1", "yoffset", "yo"), 1), 0);
            p.dir = ctx.caster.forward();
            p.step = 0;
            p.maxRange = Double.MAX_VALUE;
            p.maxTicks = (int) SkillRuntime.num(m.arg("200", "maxduration", "md", "duration", "d"), 200);
            // Los tajos cuerpo a cuerpo (tótems de un momento) de un jugador llegan medio bloque más: en el pack
            // alcanzaban ~2 bloques y un jugador pega a los mobs hasta a 3 (fallaban aunque le dieras al mob)
            if (p.maxTicks <= 10 && p.hr > 0 && p.hr <= 2 && ctx.caster.entity instanceof net.minecraft.server.level.ServerPlayer) p.hr += MELEE_REACH;
            p.ctx.dir = p.dir;
            start(p, m);
        }
    }

    /** Lo que llegan de más los tajos cuerpo a cuerpo de un jugador (ver totem). */
    static final double MELEE_REACH = 0.5;

    static void orbital(Mech m, Ctx ctx, List<Tgt> targets) {
        String auraName = m.arg(null, "auraname", "aura", "n", "buffname");
        // mergeAll / mergeSameCaster: aplicarla otra vez se suma a la que hay (la pasiva que la pone cada medio
        // segundo con duration=20 la mantiene, no la vuelve a crear)
        boolean refresh = SkillRuntime.bool(m.arg("false", "refreshduration", "rd"))
                || SkillRuntime.bool(m.arg("false", "mergeall", "ma")) || SkillRuntime.bool(m.arg("false", "mergesamecaster", "ms"));
        for (Tgt t : targets) {
            if (MOVERS.size() >= MAX || t.who == null) continue;
            int duration = (int) SkillRuntime.num(m.arg("100", "duration", "d", "maxduration", "md", "ticks"), 100);
            // Con nombre de aura: si ya lo tiene, se renueva (rd) o se queda el que hay
            if (auraName != null) {
                SkillRuntime.WhoState st = SkillRuntime.stateIfAny(t.who);
                SkillAuras.Aura old = st == null ? null : st.auras.get(auraName.toLowerCase(Locale.ROOT));
                if (old != null && old.alive && old.mover != null && old.mover.alive) {
                    if (refresh) {
                        old.left = Math.max(old.left, duration);
                        old.mover.maxTicks = Math.max(old.mover.maxTicks, old.mover.age + duration);
                    }
                    continue;
                }
            }
            Mover p = new Mover(ctx.copy(), 3, ctx.caster.level());
            common(p, m);
            p.center = t.who;
            p.radius = SkillRuntime.num(m.arg("4", "radius", "r"), 4);
            int points = (int) Math.max(1, SkillRuntime.num(m.arg("32", "points", "p"), 32));
            p.angleStep = Math.PI * 2 / points * (SkillRuntime.bool(m.arg("false", "reversed", "rev")) ? -1 : 1);
            p.angle = p.angleStep * SkillRuntime.num(m.arg("0", "startingpoint", "sp"), 0);
            p.oy = SkillRuntime.num(m.arg("0", "offsety", "oy", "yoffset"), 0);
            p.rx = Math.toRadians(SkillRuntime.num(m.arg("0", "rotationx", "rx"), 0));
            p.ry = Math.toRadians(SkillRuntime.num(m.arg("0", "rotationy", "ry"), 0));
            p.rz = Math.toRadians(SkillRuntime.num(m.arg("0", "rotationz", "rz"), 0));
            p.avx = Math.toRadians(SkillRuntime.num(m.arg("0", "angularvelocityx", "avx"), 0));
            p.avy = Math.toRadians(SkillRuntime.num(m.arg("0", "angularvelocityy", "avy"), 0));
            p.avz = Math.toRadians(SkillRuntime.num(m.arg("0", "angularvelocityz", "avz"), 0));
            p.maxTicks = duration;
            p.maxRange = Double.MAX_VALUE;
            p.pos = orbitPos(p);
            p.dir = Vec3.ZERO;
            if (auraName != null) p.aura = SkillAuras.forMover(p, auraName, t.who, ctx, duration);
            start(p, m);
        }
    }

    /**
     * shoot{type=ARROW}: aquí sin flecha de verdad, como un proyectil rápido; pero cuenta como el disparo de Minecraft que
     * es en MythicMobs: el mob que dispara lanza su ~onShoot (una vez por disparo) y, al dar a alguien, su ~onBowHit.
     */
    static void shoot(Mech m, Ctx ctx, List<Tgt> targets) {
        projectile(m, ctx, targets, false, true, null);
        SkillMobs.Host h = ctx.caster.entity == null ? null : SkillMobs.host(ctx.caster.entity);
        if (h != null && !targets.isEmpty()) SkillMobs.shot(h, 1.0);
    }

    /** El mob de jefe que lanzó el proyectil (null si lo lanzó un jugador o un efecto). */
    private static SkillMobs.Host host(Mover p) {
        return p.ctx.caster.entity == null ? null : SkillMobs.host(p.ctx.caster.entity);
    }

    private static void common(Mover p, Mech m) {
        p.interval = (int) Math.max(1, SkillRuntime.num(m.arg("1", "interval", "i", "int"), 1));
        p.hr = SkillRuntime.num(m.arg("1.25", "horizontalradius", "hradius", "hitradius", "hr", "radius_h"), 1.25);
        // Como MythicMobs: el alto de la caja es 1,25 si no lo dice (en un orbital, el mismo que el ancho)
        p.vr = SkillRuntime.num(m.arg(null, "verticalradius", "vradius", "verticalhitradius", "vhr", "vr"), p.kind == 3 ? p.hr : 1.25);
        p.hitPlayers = SkillRuntime.bool(m.arg("true", "hitplayers", "hp"));
        // Los misiles de MythicMobs tocan a los mobs si no dice otra cosa; proyectiles, tótems y orbitales, no
        p.hitNonPlayers = SkillRuntime.bool(m.arg(p.kind == 1 ? "true" : "false", "hitnonplayers", "hnp"));
        p.stopAtEntity = SkillRuntime.bool(m.arg(p.kind == 2 || p.kind == 3 ? "false" : "true", "stopatentity", "se"));
        p.stopAtBlock = SkillRuntime.bool(m.arg(p.kind == 2 || p.kind == 3 ? "false" : "true", "stopatblock", "sb"));
        // Cargas: solo tótems y orbitales (cada tick que toca a alguien gasta una; en un proyectil «c» son condiciones)
        p.charges = p.kind >= 2 ? (int) SkillRuntime.num(m.arg("0", "charges", "ch", "c", "maxcharges"), 0) : 0;
        p.onTick = m.arg(null, "ontick", "ot", "ontickskill");
        p.onHit = m.arg(null, "onhit", "oh", "onhitskill");
        p.onEnd = m.arg(null, "onend", "oe", "onendskill");
        p.onHitBlock = m.arg(null, "onhitblock", "ohb", "onhitblockskill");
        // immuneDelay va en milisegundos (2000 si no lo dice) y MythicMobs deja volver a tocar al mismo al doble de eso
        double immune = SkillRuntime.num(m.arg("2000", "immunedelay", "immune", "id"), 2000);
        p.rehit = (int) Math.max(1, Math.ceil(immune * 2 / 50.0));
        p.deathDelay = (int) SkillRuntime.num(m.arg("2", "deathdelay", "death", "dd"), 2);
        p.bulletForward = SkillRuntime.num(m.arg("0", "bulletforwardoffset", "bfo"), 0);
        p.hitConds = m.hc;
        p.stopConds = m.sc;
    }

    private static void start(Mover p, Mech m) {
        String type = m.arg("", "bullettype", "bullet", "bt").toUpperCase(Locale.ROOT);
        String mob = m.arg(null, "mob", "bulletmob", "bm", "mobtype", "mm");
        float yaw = p.dir.lengthSqr() > 0 ? SkillRuntime.yawOf(p.dir) : p.ctx.caster.yaw();
        float pitch = p.dir.lengthSqr() > 0 && p.kind < 2 ? SkillRuntime.pitchOf(p.dir) : 0F;
        if (mob != null && (type.equals("MOB") || type.isEmpty())) {
            // Bala de tipo MOB: como MythicMobs, el mob va «bulletyoffset» por DEBAJO del proyectil (1,35 si no lo dice;
            // los packs suben sus modelos o el proyectil para compensarlo)
            p.bulletY = -SkillRuntime.num(m.arg("1.35", "bulletyoffset", "byoffset", "byo"), 1.35);
            MobDef def = p.ctx.cls.mob(mob);
            if (def != null) {
                p.bullet = SkillRuntime.spawn(p.ctx, def, bulletPos(p), yaw, pitch);
                if (p.bullet != null) p.bullet.carried = true;
            }
        } else if (type.equals("DISPLAY") && m.a.get("bulletitem") != null) {
            // Bala de tipo display (la esfera de energía de Astwood): el modelo de ítem que la pone el pack
            p.bullet = SkillRuntime.spawnModel(p.ctx, null, null, 1F, bulletPos(p), yaw, pitch);
            if (p.bullet != null) {
                p.bullet.carried = true;
                String sc = m.arg("1", "bulletscale", "bs").split(",")[0];
                p.bullet.prop("item", m.a.get("bulletitem"), String.valueOf(SkillRuntime.num(sc, 1)), null);
            }
        } else if (m.a.get("bulletitem") != null && ITEM_BULLETS.containsKey(type)) {
            // ITEM/MYTHICITEM (un objeto suelto), TRACKING/REALTRACKING (un soporte con el objeto en la cabeza, girado
            // hacia donde va), BLOCK/SMALLBLOCK (el bloque) y ARROW (la flecha): el modelo que les puso el conversor
            String[] how = ITEM_BULLETS.get(type);
            p.bullet = SkillRuntime.spawnModel(p.ctx, null, null, 1F, bulletPos(p), yaw, pitch);
            if (p.bullet != null) {
                p.bullet.carried = true;
                p.bullet.prop("item", m.a.get("bulletitem"), how[1], how[0]);
            }
        } else if (type.equals("ME") || type.equals("MEG") || type.equals("MODELENGINE")) {
            // Bala de ModelEngine: el modelo tal cual, justo donde va el proyectil
            String model = m.arg(null, "bulletmodel", "model");
            if (model != null) {
                String mid = model.contains(".") ? model : p.ctx.cls.id + "." + model.toLowerCase(Locale.ROOT);
                float scale = (float) SkillRuntime.num(m.arg("1", "bulletscale"), 1);
                p.bullet = SkillRuntime.spawnModel(p.ctx, mid, m.arg(null, "bulletstate", "state"), scale, bulletPos(p), yaw, pitch);
                if (p.bullet != null) p.bullet.carried = true;
            }
        }
        String onStart = m.arg(null, "onstart", "os", "onstartskill");
        if (onStart != null) run(p, onStart, List.of(Tgt.at(p.pos)), null);
        MOVERS.add(p);
    }

    /** Tipo de bala de MythicMobs → {cómo se dibuja (SkillClient: head, ground o fixed), escala}. */
    private static final java.util.Map<String, String[]> ITEM_BULLETS = java.util.Map.ofEntries(
            java.util.Map.entry("ITEM", new String[] {"ground", "1"}), java.util.Map.entry("MYTHICITEM", new String[] {"ground", "1"}),
            java.util.Map.entry("TRACKING", new String[] {"head", "1"}), java.util.Map.entry("ARMOR_STAND", new String[] {"head", "1"}),
            java.util.Map.entry("ARMORSTAND", new String[] {"head", "1"}), java.util.Map.entry("PSTAND", new String[] {"head", "1"}),
            java.util.Map.entry("REALTRACKING", new String[] {"head", "1"}), java.util.Map.entry("RTRACKING", new String[] {"head", "1"}),
            java.util.Map.entry("REAL_ARMOR_STAND", new String[] {"head", "1"}), java.util.Map.entry("REALARMORSTAND", new String[] {"head", "1"}),
            java.util.Map.entry("STAND", new String[] {"head", "1"}),
            // el bloque entero (como el bloque que cae de Minecraft: FIXED lo dibuja a la mitad) y el pequeño
            java.util.Map.entry("BLOCK", new String[] {"fixed", "2"}), java.util.Map.entry("SMALLBLOCK", new String[] {"fixed", "0.9"}),
            java.util.Map.entry("SMALL_BLOCK", new String[] {"fixed", "0.9"}), java.util.Map.entry("ARROW", new String[] {"fixed", "1"}));

    private static Vec3 bulletPos(Mover p) {
        Vec3 at = p.pos.add(0, p.bulletY, 0);
        if (p.bulletForward != 0 && p.dir.lengthSqr() > 1e-6) at = at.add(p.dir.normalize().scale(p.bulletForward));
        return at;
    }

    private static Vec3 orbitPos(Mover p) {
        Vec3 c = p.center.pos();
        double t = p.age;
        Vec3 o = new Vec3(Math.cos(p.angle) * p.radius, 0, Math.sin(p.angle) * p.radius);
        o = o.xRot((float) (p.rx + p.avx * t)).yRot((float) (p.ry + p.avy * t)).zRot((float) (p.rz + p.avz * t));
        return c.add(o).add(0, p.oy, 0);
    }

    // ------------------------------------------------------------------------------------------- desde sus skills

    /** modifyprojectile{trait=VELOCITY|RADIUS|GRAVITY;action=SET|ADD|MULTIPLY;value}: cambia el proyectil que la lanzó. */
    static void modify(Mech m, Ctx ctx) {
        Mover p = ctx.mover;
        if (p == null || !p.alive) return;
        String trait = m.arg("VELOCITY", "trait", "t").toUpperCase(Locale.ROOT);
        String action = m.arg("SET", "action", "a").toUpperCase(Locale.ROOT);
        double v = SkillRuntime.num(m.arg("1", "value", "v"), 1);
        switch (trait) {
            // la velocidad va en bloques por segundo (como v= del proyectil); multiplicar, tal cual
            case "VELOCITY", "SPEED" -> p.step = apply(p.step, action, action.startsWith("MULT") ? v : v / 20.0 * p.interval);
            case "RADIUS", "HITRADIUS" -> {
                p.hr = apply(p.hr, action, v);
                p.vr = apply(p.vr, action, v);
            }
            case "GRAVITY" -> p.gravity = apply(p.gravity, action, action.equals("MULTIPLY") ? v : v / 20.0);
            default -> { }
        }
    }

    private static double apply(double now, String action, double v) {
        return switch (action) {
            case "ADD" -> now + v;
            case "MULTIPLY", "MULT" -> now * v;
            default -> v;
        };
    }

    static void endCurrent(Ctx ctx) {
        if (ctx.mover != null) end(ctx.mover);
    }

    /** setprojectiledirection @objetivo: el proyectil que la lanzó gira hacia allí. */
    static void setDirection(Ctx ctx, List<Tgt> targets) {
        Mover p = ctx.mover;
        if (p == null || !p.alive || targets.isEmpty()) return;
        Vec3 d = targets.get(0).pos().subtract(p.pos);
        if (d.lengthSqr() > 1e-6) {
            p.dir = d.normalize();
            p.ctx.dir = p.dir;
        }
    }

    // ------------------------------------------------------------------------------------------- cada tick

    static void tick() {
        if (MOVERS.isEmpty()) return;
        List<Mover> now = new ArrayList<>(MOVERS);
        for (Mover p : now) {
            if (!p.alive) continue;
            try {
                step(p);
            } catch (SkillRuntime.CancelSkill c) {
                // una skill del proyectil se paró sola
            } catch (Throwable t) {
                p.alive = false;
                TFClient.LOGGER.debug("TF Skills: error en un proyectil", t);
            }
        }
        MOVERS.removeIf(p -> !p.alive);
    }

    private static void step(Mover p) {
        p.age++;
        if (p.ctx.caster.actor != null && !p.ctx.caster.alive() && p.kind == 3) {
            end(p);
            return;
        }
        if (p.aura != null && !p.aura.alive) {
            end(p);
            return;
        }
        if (p.age % p.interval != 0) {
            if (p.age >= p.maxTicks) end(p);
            return;
        }
        Vec3 from = p.pos;
        switch (p.kind) {
            case 0, 1 -> {
                if (p.kind == 1) {
                    // Misil: gira hacia el ser que persigue (si ya no está, se acaba, como en MythicMobs) o hacia su sitio
                    Vec3 home = p.homeAt;
                    if (p.homing != null) {
                        if (!p.homing.isAlive() || p.homing.isRemoved()) {
                            end(p);
                            return;
                        }
                        home = p.homing.position().add(0, p.homeY, 0);
                        // chainmissile de vuelta: se acaba al llegar a quien lo lanzó
                        if (p.chain != null && p.chain.returning && home.distanceToSqr(p.pos) < 1.0) {
                            end(p);
                            return;
                        }
                    }
                    Vec3 to = home == null ? Vec3.ZERO : home.subtract(p.pos);
                    if (to.lengthSqr() > 1e-6) p.dir = p.dir.scale(p.inertia).add(to.normalize()).normalize();
                }
                if (p.gravity != 0 && !p.hug) {
                    // como MythicMobs: la gravedad se resta a la velocidad (bloques por tick), que cambia de rumbo y de
                    // rapidez (un tiro hacia arriba sube, se para y cae)
                    Vec3 vel = p.dir.scale(p.step).add(0, -p.gravity * p.interval, 0);
                    double sp = vel.length();
                    if (sp > 1e-6) {
                        p.dir = vel.scale(1 / sp);
                        p.step = sp;
                    }
                }
                Vec3 to = p.pos.add(p.dir.scale(p.step));
                if (p.hug) {
                    // Pegado al suelo: sube escalones de hasta «maxclimbheight» y baja lo que haga falta
                    Vec3 g = SkillMinions.ground(p.level, to, p.pos.y - p.hugHeight + Math.max(0, p.climb - 1));
                    double groundNow = p.pos.y - p.hugHeight;
                    if (g.y - groundNow > p.climb + 1e-3 && p.stopAtBlock) {
                        hitBlock(p);
                        return;
                    }
                    to = new Vec3(to.x, g.y + p.hugHeight, to.z);
                } else if (p.stopAtBlock || p.bounce) {
                    BlockHitResult hit = p.level.clip(new ClipContext(p.pos, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                            (net.minecraft.world.entity.Entity) null));
                    if (hit.getType() == HitResult.Type.BLOCK) {
                        if (p.bounce) {
                            // Rebota: la dirección se refleja en la cara del bloque y pierde velocidad (el fuego por el suelo)
                            net.minecraft.core.Direction face = hit.getDirection();
                            Vec3 d = p.dir;
                            d = switch (face.getAxis()) {
                                case X -> new Vec3(-d.x, d.y, d.z);
                                case Y -> new Vec3(d.x, -d.y, d.z);
                                default -> new Vec3(d.x, d.y, -d.z);
                            };
                            p.dir = d.normalize();
                            p.step *= p.bounceVel;
                            to = hit.getLocation().add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.05));
                        } else {
                            p.pos = hit.getLocation();
                            moveBullet(p);
                            hitBlock(p);
                            return;
                        }
                    }
                }
                if (p.accel != 1.0) p.step *= p.accel;
                p.travelled += p.step;
                p.pos = to;
                p.ctx.dir = p.dir;
            }
            case 3 -> {
                if (!p.center.alive()) {
                    end(p);
                    return;
                }
                p.angle += p.angleStep;
                Vec3 np = orbitPos(p);
                p.dir = np.subtract(p.pos);
                p.pos = np;
            }
            default -> { /* tótem: quieto */ }
        }
        moveBullet(p);
        if (p.onTick != null) run(p, p.onTick, List.of(Tgt.at(p.pos)), null);
        if (!p.alive) return;
        hits(p, from);
        if (!p.alive) return;
        if (p.age >= p.maxTicks || (p.start != null ? p.pos.distanceToSqr(p.start) >= p.maxRange * p.maxRange : p.travelled >= p.maxRange)) end(p);
    }

    private static void hitBlock(Mover p) {
        // ~onProjectileLand del mob que lo lanzó (los suyos de MythicMobs; el disparo de Minecraft, no)
        SkillMobs.Host h = p.kind <= 1 && !p.vanilla ? host(p) : null;
        if (h != null) SkillMobs.projectileLand(h, p.pos);
        if (!p.alive) return;
        if (p.onHitBlock != null) run(p, p.onHitBlock, List.of(Tgt.at(p.pos)), null);
        end(p);
    }

    private static void moveBullet(Mover p) {
        for (SkillActor v : p.vfx) {
            if (!v.alive) continue;
            float yaw = p.dir.lengthSqr() > 1e-6 ? SkillRuntime.yawOf(p.dir) : v.yaw;
            float pitch = p.dir.lengthSqr() > 1e-6 ? SkillRuntime.pitchOf(p.dir) : v.pitch;
            v.moveTo(p.pos, yaw, pitch);
        }
        if (p.bullet == null || !p.bullet.alive) return;
        float yaw = p.dir.lengthSqr() > 1e-6 ? SkillRuntime.yawOf(p.dir) : p.bullet.yaw;
        float pitch = p.kind == 0 || p.kind == 1 ? (p.dir.lengthSqr() > 1e-6 ? SkillRuntime.pitchOf(p.dir) : p.bullet.pitch) : 0F;
        p.bullet.moveTo(bulletPos(p), yaw, pitch);
    }

    /**
     * Lo que toca en este paso. Como MythicMobs: todos los que toca a la vez van juntos a una sola vez de su onHit;
     * con stopAtEntity se para en el primero que cumpla sus stopConditions; los tótems y orbitales gastan una carga por
     * cada paso en el que tocan a alguien; al mismo no lo vuelve a tocar hasta pasado «rehit».
     */
    private static void hits(Mover p, Vec3 from) {
        if (p.onHit == null && !p.stopAtEntity && !p.hitTrigger) return;
        if (p.hr <= 0 || p.vr <= 0) return; // sin caja no toca a nadie
        if (p.chain != null && p.chain.returning) return; // de vuelta a quien lo lanzó no toca a nadie
        AABB box = new AABB(from, p.pos).inflate(p.hr, p.vr, p.hr);
        long now = SkillRuntime.clock;
        List<Tgt> hit = new ArrayList<>();
        LivingEntity first = null;
        boolean stop = false;
        // Efectos del pack que el proyectil busca a propósito (hitConditions=[ - mythicmobtype{t=X} true ]): el
        // Nigromante tira un muñeco donde cae el disparo y otro proyectil va a por él
        if (wantsActors(p)) {
            for (SkillActor a : new ArrayList<>(SkillRuntime.ACTORS)) {
                if (!a.alive || a.level != p.level || a.carried || a.follow != null || immune(p, a.id, now)) continue;
                if (!box.contains(a.pos) && !box.inflate(0.3).contains(a.pos.add(0, 0.5, 0))) continue;
                Tgt t = Tgt.of(a.who);
                if (!SkillTargets.passes(p.hitConds, t, p.ctx)) continue;
                p.hit.put(a.id, now);
                hit.add(t);
                if (p.stopAtEntity && stopsAt(p, t)) {
                    stop = true;
                    break;
                }
            }
        }
        if (!stop) {
            List<LivingEntity> found = new ArrayList<>();
            for (LivingEntity e : p.level.getEntitiesOfClass(LivingEntity.class, box)) {
                if (!e.isAlive() || e.isSpectator() || e instanceof ArmorStand) continue;
                if (e == p.ctx.caster.entity || e == p.ctx.caster.player()) continue;
                if (p.onlyTarget != null && e != p.onlyTarget) continue;
                if (immune(p, e.getUUID(), now)) continue;
                if (e instanceof Player ? !p.hitPlayers : !p.hitNonPlayers) continue;
                if (!SkillRuntime.canHurt(p.ctx, e)) continue;
                if (!p.hitConds.isEmpty() && !SkillTargets.passes(p.hitConds, Tgt.of(Who.of(e)), p.ctx)) continue;
                found.add(e);
            }
            found.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(from)));
            for (LivingEntity e : found) {
                Tgt t = Tgt.of(Who.of(e));
                if (e instanceof Player) p.hitPlayer = true;
                p.hit.put(e.getUUID(), now);
                hit.add(t);
                if (first == null) first = e;
                if (p.stopAtEntity && stopsAt(p, t)) {
                    stop = true;
                    break;
                }
            }
        }
        if (hit.isEmpty()) return;
        p.hits++;
        // ~onProjectileHit (o ~onBowHit, el disparo de Minecraft) del mob que lo lanzó: una vez por cada ser tocado
        SkillMobs.Host h = p.hitTrigger ? host(p) : null;
        if (h != null) {
            for (Tgt t : hit) {
                if (t.entity() == null) continue;
                if (p.vanilla) SkillMobs.bowHit(h, t.entity());
                else SkillMobs.projectileHit(h, t.entity(), p.pos);
            }
            if (!p.alive) return;
        }
        if (p.onHit != null) run(p, p.onHit, hit, first);
        if (!p.alive) return;
        if (stop && p.chain != null) {
            bounce(p, first);
            return;
        }
        if (stop || (p.charges > 0 && p.hits >= p.charges)) end(p);
    }

    /** chainmissile: tras tocar a «at», al siguiente (el más cercano que no ha tocado), de vuelta o se acaba. */
    private static void bounce(Mover p, LivingEntity at) {
        Chain c = p.chain;
        if (at != null) c.visited.add(at.getUUID());
        Vec3 from = at != null ? at.position() : p.pos;
        if (c.left > 0) {
            LivingEntity next = null;
            double best = c.radius * c.radius;
            for (LivingEntity e : p.level.getEntitiesOfClass(LivingEntity.class, new AABB(from, from).inflate(c.radius))) {
                if (!e.isAlive() || e.isSpectator() || e instanceof ArmorStand || c.visited.contains(e.getUUID())) continue;
                if (e == p.ctx.caster.entity || e == p.ctx.caster.player()) continue;
                if (e instanceof Player ? !p.hitPlayers : !p.hitNonPlayers) continue;
                if (!SkillRuntime.canHurt(p.ctx, e)) continue;
                if (!c.conds.isEmpty() && !SkillTargets.passes(c.conds, Tgt.of(Who.of(e)), p.ctx)) continue;
                double d = e.distanceToSqr(from);
                if (d < best) {
                    best = d;
                    next = e;
                }
            }
            if (next != null) {
                c.left--;
                p.homing = next;
                p.start = p.pos; // cada salto cuenta su alcance desde aquí
                p.homeAt = null;
                p.homeY = p.ctx.host != null ? next.getBbHeight() * 0.55 : next.getEyeHeight();
                if (p.onlyTarget != null) p.onlyTarget = next;
                return;
            }
        }
        if (c.back && !c.returning && p.ctx.caster.entity instanceof LivingEntity self && self.isAlive()) {
            c.returning = true;
            p.homing = self;
            p.start = p.pos;
            p.homeAt = null;
            p.homeY = self.getBbHeight() * 0.5;
            return;
        }
        end(p);
    }

    private static boolean immune(Mover p, Object key, long now) {
        Long last = p.hit.get(key);
        return last != null && now - last < p.rehit;
    }

    /** stopConditions: se para en ese objetivo solo si las cumple (sin condiciones, en el primero). */
    private static boolean stopsAt(Mover p, Tgt t) {
        return p.stopConds.isEmpty() || SkillTargets.passes(p.stopConds, t, p.ctx);
    }

    private static boolean wantsActors(Mover p) {
        return wantsActors(p.hitConds);
    }

    private static boolean wantsActors(List<Cond> conds) {
        for (Cond c : conds) {
            if ((c.m().equals("mythicmobtype") || c.m().equals("mmt") || c.m().equals("mobtype")) && c.action().equals("true")) return true;
            if (wantsActors(c.parts())) return true; // dentro de una compuesta («(entitytype false || mythicmobtype{t=X})»)
        }
        return false;
    }

    private static void run(Mover p, String skill, List<Tgt> targets, LivingEntity trigger) {
        Ctx c = p.ctx.copy();
        c.origin = p.pos;
        c.targets = targets;
        c.dir = p.dir;
        c.mover = p;
        if (trigger != null) c.trigger = Who.of(trigger);
        SkillRuntime.runMeta(c.cls.meta(skill), c);
    }

    static void end(Mover p) {
        if (!p.alive) return;
        p.alive = false;
        if (net.tierrasfantasticas.tfclient.bosses.BossTest.ON && p.kind <= 1 && host(p) != null) {
            // Prueba en el juego: cada proyectil o misil de un jefe (¿llega al jugador?)
            String to = p.homing != null ? p.homing.getName().getString() : "un sitio";
            net.tierrasfantasticas.tfclient.bosses.BossTest.projectile((p.kind == 1 ? "misil" : "proyectil") + " " + p.onHit
                    + " → " + to, p.hitPlayer, String.format("%.0f bloques, %d ticks%s", p.travelled, p.age,
                    p.age >= p.maxTicks ? ", se acabó el tiempo" : p.start != null && p.pos.distanceToSqr(p.start) >= p.maxRange * p.maxRange ? ", alcance máximo" : ""));
        }
        if (p.onEnd != null) {
            try {
                run(p, p.onEnd, List.of(Tgt.at(p.pos)), null);
            } catch (SkillRuntime.CancelSkill ignored) {
                // nada
            }
        }
        if (p.bullet != null) {
            SkillActor b = p.bullet;
            if (p.deathDelay > 0) SkillRuntime.later(p.deathDelay, b::remove);
            else b.remove();
        }
        for (SkillActor v : p.vfx) v.remove();
        p.vfx.clear();
        if (p.aura != null) SkillAuras.moverEnded(p.aura);
    }

    static void clear() {
        MOVERS.clear();
    }

    /** ¿Está en el aire un sitio? (para quien lo necesite). */
    static boolean air(ServerLevel level, Vec3 p) {
        return level.getBlockState(BlockPos.containing(p)).isAir();
    }
}
