package net.tierrasfantasticas.tfclient.skills;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.skills.SkillDefs.ClassDef;
import net.tierrasfantasticas.tfclient.skills.SkillDefs.Cond;
import net.tierrasfantasticas.tfclient.skills.SkillDefs.Mech;
import net.tierrasfantasticas.tfclient.skills.SkillDefs.Meta;
import net.tierrasfantasticas.tfclient.skills.SkillMobs.Host;
import net.tierrasfantasticas.tfclient.skills.SkillRuntime.Ctx;
import net.tierrasfantasticas.tfclient.skills.SkillRuntime.Tgt;

/**
 * El cerebro táctico de los jefes («inteligencia» de la dificultad, de 0 a 1). Los ataques siguen siendo los del pack
 * (sus animaciones, golpes, condiciones y cooldowns, tal cual); lo que cambia es QUIÉN decide cuál sale y cuándo. En el
 * pack, cada ataque es una línea «skill{s=X} @target ~onTimer:20» y sale el primero de la lista que cumple sus
 * condiciones (o uno al azar, con randomskill). Con el cerebro, esas líneas no se lanzan solas: cada pocos ticks se
 * prueban todas sin hacer nada (SkillRuntime.usable) y sale la que mejor puntúa para lo que está pasando:
 *
 * <ul>
 *   <li>la distancia (el ataque cuyo alcance encaja mejor con donde está el jugador);</li>
 *   <li>castigos al estilo Souls: si el jugador se cura (come o bebe), un ataque que acorta distancia o a distancia; si
 *       se cubre con escudo, agarres y golpes en área; si dispara o se aleja, los que cierran distancia; si está detrás,
 *       los que dan detrás (coletazos, giros);</li>
 *   <li>en grupo, los de área cuando hay varios cerca;</li>
 *   <li>no repetir el mismo ataque y aprender: los que le entran al jugador salen más; los que esquiva, menos;</li>
 *   <li>un poco de azar (menos cuanto más listo) y, a veces, «se lo piensa» un instante antes de atacar.</li>
 * </ul>
 *
 * Además piensa más rápido cuanto más listo es (de cada 20 ticks a cada 4) y por debajo del 50 % y del 25 % de vida
 * piensa aún más rápido (fases). Si un ataque no pone gcd (las cabezas de la Hidra), puede encadenar otro distinto en el
 * mismo momento. Fuera de los ataques, da a la entidad un plan de movimiento ({@link #plan}): a qué distancia ponerse
 * (la del ataque que tendrá listo) y si rodear al jugador mientras espera. Con inteligencia 0 no hace nada: el jefe se
 * comporta exactamente como el pack. Lo simula tools/bosses/sim_ai.py (--smart).
 */
public final class SkillBrain {
    private SkillBrain() {}

    // ------------------------------------------------------------------------------------------- qué sabe de cada ataque

    /** Un ataque del pack y lo que se sabe de él (de leer sus mecánicas al empezar) y de cómo le va en esta pelea. */
    static final class Attack {
        final String name;
        /** La línea del mob que lo lanza; o, si sale de una randomskill, esa línea (para sus objetivos y condiciones). */
        final Mech line;
        final boolean fromPool;
        /** Distancia al objetivo a la que sale (de sus condiciones: distance, targetwithin, targetnotwithin). */
        double min = 0, max = 40;
        boolean gap, ranged, aoe, grab, rear;
        /** Hasta qué tick (desde que empieza, sin tempo) hace daño o mueve algo: lo de después es recuperación. */
        int activeEnd;
        /** Su gcd (lo que dura entero) y si se puede cortar su recuperación para encadenar otro (no: agarres, cargas). */
        int gcd;
        boolean noCancel;
        int uses, hits, missStreak;
        long lastUse = Long.MIN_VALUE / 4;

        Attack(String name, Mech line, boolean fromPool) {
            this.name = name;
            this.line = line;
            this.fromPool = fromPool;
        }

        /** Distancia ideal (algo más cerca que el centro de su alcance: los packs miden el alcance con holgura). */
        double sweet() {
            double hi = Math.min(max, 24);
            return min + Math.max(0, hi - min) * 0.35;
        }
    }

    /** El cerebro de un mob de jefe. */
    static final class Brain {
        final List<Attack> attacks = new ArrayList<>();
        /** Líneas del mob que decide el cerebro (no se lanzan solas por su ~onTimer). */
        final Set<Mech> handled = Collections.newSetFromMap(new IdentityHashMap<>());
        final Deque<String> recent = new ArrayDeque<>();
        long nextDecision;
        long hesitateUntil = -1;
        boolean hesitated;
        boolean urgent;
        boolean waiting;
        Attack current;
        long currentAt;
        boolean currentHit;
        double preferred = 3;
        int strafe = 1;
        long strafeUntil;
        long lastRangedHit = Long.MIN_VALUE / 4;
        /** El uso del ataque en curso (para cortarlo al encadenar) y cómo estaba el jefe antes de empezarlo. */
        long currentCast;
        double[] motion;
        String[] defaults;
        float animMark = Float.NaN;
        boolean chainTried;
        /** El ataque que tiene pensado hacer (rota el repertorio) y hasta cuándo; los que puede hacer ahora (su fase). */
        Attack planned;
        long plannedUntil;
        long planCheck;
        List<Attack> can = List.of();
        int chains;

        boolean handles(Mech m) {
            return handled.contains(m);
        }
    }

    /** Mecánicas que hacen algo a otros (si una skill no lleva ninguna, no es un ataque: cambios de postura, efectos). */
    private static final Set<String> OFFENSE = Set.of("damage", "basedamage", "hit", "percentdamage", "throw", "projectile",
            "missile", "shoot", "totem", "summon", "lunge", "propel", "leap", "mountmodel", "stun", "potion", "ignite", "pull",
            "velocity");
    private static final Set<String> AREA = Set.of("pir", "playersinradius", "eir", "entitiesinradius", "livinginradius",
            "leir", "livingentitiesinradius", "ring", "playersinring", "rlnc", "randomlocationsnearcaster", "air");

    /** El cerebro de un mob (null si no tiene ataques que decidir). */
    static Brain build(Host h) {
        ClassDef cls = h.cls();
        Brain b = new Brain();
        for (Mech m : h.def().mechs) {
            if (!"ontimer".equals(m.tr) || SkillRuntime.num(m.trv, 20) < 10) continue;
            boolean any = false;
            if (m.m.equals("randomskill")) {
                for (String part : m.arg("", "skills", "s").split(",")) {
                    String n = part.trim().split("\\s+")[0];
                    Meta meta = n.isEmpty() ? null : cls.meta(n);
                    if (meta != null && offensive(cls, meta, new HashSet<>(), 0)) {
                        b.attacks.add(analyze(cls, meta, new Attack(meta.name, m, true)));
                        any = true;
                    }
                }
            } else if (m.m.equals("skill") || m.m.equals("metaskill") || m.m.equals("cast")) {
                // Los de @self son cambios de postura del propio jefe (poner el escudo, la espada de almas): siguen solos
                if (m.t == null || m.t.equals("self") || m.t.equals("caster")) continue;
                String n = m.arg(null, "s", "skill", "skills");
                Meta meta = n == null ? null : cls.meta(n.split(",")[0].trim());
                if (meta != null && offensive(cls, meta, new HashSet<>(), 0)) {
                    b.attacks.add(analyze(cls, meta, new Attack(meta.name, m, false)));
                    any = true;
                }
            }
            if (any) b.handled.add(m);
        }
        return b.attacks.isEmpty() ? null : b;
    }

    /** ¿Hace algo a otros (directamente o en lo que llama)? */
    private static boolean offensive(ClassDef cls, Meta meta, Set<String> seen, int depth) {
        if (meta == null || depth > 4 || !seen.add(meta.name)) return false;
        for (Mech m : meta.mechs) {
            if (OFFENSE.contains(m.m) && !(m.m.equals("potion") || m.m.equals("velocity"))) return true;
            // Una carga (setspeed muy alto: la del escudo del Esqueleto) también es un ataque
            if (m.m.equals("setspeed") && SkillRuntime.num(m.arg("1", "speed", "s"), 1) >= 2) return true;
            if ((m.m.equals("potion") || m.m.equals("velocity")) && m.t != null && !m.t.equals("self")) return true;
            for (Meta sub : called(cls, m)) if (offensive(cls, sub, seen, depth + 1)) return true;
        }
        return false;
    }

    /** Las skills que lanza una línea (skill, randomskill, onHit/onTick/onEnd de proyectiles, tótems y auras). */
    private static List<Meta> called(ClassDef cls, Mech m) {
        List<Meta> out = new ArrayList<>();
        for (String k : new String[] {"s", "skill", "skills", "oh", "onhit", "ot", "ontick", "oe", "onend", "os", "onstart"}) {
            String v = m.a.get(k);
            if (v == null) continue;
            if (!(k.equals("s") || k.equals("skill") || k.equals("skills")) || m.m.equals("skill") || m.m.equals("metaskill")
                    || m.m.equals("randomskill") || m.m.equals("cast") || m.m.equals("skillsequence")) {
                for (String part : v.split(",")) {
                    Meta sub = cls.meta(part.trim().split("\\s+")[0]);
                    if (sub != null) out.add(sub);
                }
            }
        }
        return out;
    }

    /** Lee sus mecánicas (y las de lo que llama): alcance y qué tipo de ataque es. */
    private static Attack analyze(ClassDef cls, Meta meta, Attack a) {
        for (Cond c : meta.targetConditions) range(a, c);
        for (Cond c : meta.conditions) range(a, c);
        for (Cond c : meta.targetConditions) {
            if (c.m().equals("fieldofview") || c.m().equals("fov") || c.m().equals("infieldofview")) {
                double r = SkillRuntime.num(c.a().getOrDefault("rotation", c.a().getOrDefault("r", "0")), 0);
                r = ((r % 360) + 360) % 360;
                if (r >= 110 && r <= 250) a.rear = true;
            }
        }
        String n = a.name.toLowerCase(Locale.ROOT);
        if (n.contains("tail") || n.contains("behind") || n.contains("turn")) a.rear |= n.contains("tail") || n.contains("leap_turn");
        if (n.contains("grab") || n.contains("enclose") || n.contains("absorb") || n.contains("cage")) a.grab = true;
        tags(cls, meta, a, new HashSet<>(), 0, false);
        a.activeEnd = activeEnd(cls, meta, 0, new HashSet<>(), 0);
        for (Mech m : meta.mechs) {
            if (m.m.equals("gcd") || m.m.equals("setgcd") || m.m.equals("globalcooldown")) {
                a.gcd = Math.max(a.gcd, (int) SkillRuntime.num(m.arg("20", "ticks", "t", "duration", "d", "_"), 20));
            }
            // Una variable sin duración es un estado del jefe (la carga del Esqueleto): no se corta a medias
            if ((m.m.equals("setvar") || m.m.equals("setvariable")) && m.arg(null, "duration", "d") == null) a.noCancel = true;
        }
        if (a.grab) a.noCancel = true;
        // Sin proyectiles pero de lejos y sin acercarse: un golpe a distancia (latigazo, onda)
        if (!a.ranged && !a.gap && a.min >= 6 && a.max >= 12) a.ranged = true;
        return a;
    }

    /**
     * Qué tipo de ataque es, mirando sus mecánicas y las de lo que llama. «area»: la skill se lanzó con un objetivo de
     * área (@PIR, @EIR...), así que su daño sin objetivo propio cae en área. (Una skill de temblor de cámara lanzada con
     * @PIR no cuenta: no hace daño.)
     */
    private static void tags(ClassDef cls, Meta meta, Attack a, Set<String> seen, int depth, boolean area) {
        if (meta == null || depth > 4 || !seen.add(meta.name)) return;
        for (Mech m : meta.mechs) {
            boolean here = m.t == null ? area : AREA.contains(m.t) && radius(m) >= 4 && radius(m) <= 16;
            switch (m.m) {
                case "lunge", "propel", "leap" -> a.gap = true;
                case "projectile", "missile", "shoot" -> {
                    // Los proyectiles que solo dejan una marca para lanzarse a ella (oe=summon marker) son de acercarse
                    if (m.a.containsKey("oe") || m.a.containsKey("onend")) a.gap = true;
                    else a.ranged = true;
                }
                case "mountmodel", "stun", "lock" -> a.grab = true;
                case "setspeed" -> {
                    if (SkillRuntime.num(m.arg("1", "speed", "s"), 1) >= 2) a.gap = true;
                }
                case "summon" -> a.aoe |= depth > 0 || here;
                case "damage", "basedamage", "hit", "throw", "potion", "percentdamage" -> a.aoe |= here;
                default -> { }
            }
            if (m.m.equals("totem") && SkillRuntime.num(m.arg("1.25", "hr", "horizontalradius"), 1.25) >= 3) a.aoe = true;
            boolean callArea = m.m.equals("skill") || m.m.equals("metaskill") || m.m.equals("randomskill") ? here : false;
            for (Meta sub : called(cls, m)) tags(cls, sub, a, seen, depth + 1, callArea);
        }
    }

    /** Mecánicas que hacen el ataque (después de la última, el jefe solo se recupera). */
    private static final Set<String> ACTIVE = Set.of("damage", "basedamage", "hit", "percentdamage", "throw", "projectile",
            "missile", "shoot", "totem", "summon", "lunge", "propel", "leap", "pull", "mountmodel", "potion", "velocity");

    /** Tick (desde «start») en que acaba lo último que hace el ataque, siguiendo sus delay y las skills que llama. */
    private static int activeEnd(ClassDef cls, Meta meta, int start, Set<String> seen, int depth) {
        if (meta == null || depth > 4 || !seen.add(meta.name)) return start;
        int t = start, end = start;
        for (Mech m : meta.mechs) {
            if (m.m.equals("delay")) {
                t += (int) Math.max(0, SkillRuntime.num(m.arg("0", "_", "ticks", "t", "d"), 0));
                continue;
            }
            int at = t + (int) SkillRuntime.num(m.a.get("delay"), 0);
            int rep = (int) SkillRuntime.num(m.arg("0", "repeat", "r_"), 0);
            int every = (int) Math.max(1, SkillRuntime.num(m.arg("1", "repeatinterval", "repeati", "ri"), 1));
            int last = at + rep * every;
            if (ACTIVE.contains(m.m) && !(m.t != null && m.t.equals("self") && (m.m.equals("potion") || m.m.equals("velocity")))) {
                int extra = m.m.equals("totem") ? (int) SkillRuntime.num(m.arg("2", "maxduration", "md"), 2)
                        : m.m.equals("projectile") || m.m.equals("missile") ? 10 : 2;
                end = Math.max(end, last + extra);
            }
            if (m.m.equals("skill") || m.m.equals("metaskill") || m.m.equals("randomskill") || m.m.equals("skillsequence")) {
                for (Meta sub : called(cls, m)) end = Math.max(end, activeEnd(cls, sub, last, new HashSet<>(seen), depth + 1));
            }
        }
        return end;
    }

    private static double radius(Mech m) {
        return SkillRuntime.num(m.ta.getOrDefault("r", m.ta.getOrDefault("radius", "5")), 5);
    }

    /** distance{d=4to16}, targetwithin{d=10} (o «false»: más allá), targetnotwithin{d=7}. */
    private static void range(Attack a, Cond c) {
        boolean want = !"false".equals(c.action()) && !c.not();
        switch (c.m()) {
            case "distance" -> {
                double[] r = spec(c.a().getOrDefault("distance", c.a().getOrDefault("d", "")));
                if (r != null && want) {
                    a.min = Math.max(a.min, r[0]);
                    a.max = Math.min(a.max, r[1]);
                }
            }
            case "targetwithin" -> {
                double d = SkillRuntime.num(c.a().getOrDefault("distance", c.a().getOrDefault("d", "16")), 16);
                if (want) a.max = Math.min(a.max, d);
                else a.min = Math.max(a.min, d);
            }
            case "targetnotwithin" -> {
                double d = SkillRuntime.num(c.a().getOrDefault("distance", c.a().getOrDefault("d", "16")), 16);
                if (want) a.min = Math.max(a.min, d);
                else a.max = Math.min(a.max, d);
            }
            default -> { }
        }
    }

    private static double[] spec(String s) {
        s = s.trim();
        try {
            if (s.contains("to")) {
                String[] p = s.split("to", 2);
                return new double[] {Double.parseDouble(p[0]), Double.parseDouble(p[1])};
            }
            if (s.startsWith("<=")) return new double[] {0, Double.parseDouble(s.substring(2))};
            if (s.startsWith("<")) return new double[] {0, Double.parseDouble(s.substring(1))};
            if (s.startsWith(">=")) return new double[] {Double.parseDouble(s.substring(2)), 40};
            if (s.startsWith(">")) return new double[] {Double.parseDouble(s.substring(1)), 40};
        } catch (NumberFormatException ignored) {
            // sin alcance
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------- qué pasa ahora

    /** Lo que ve el jefe de su objetivo y de la pelea en este momento. */
    record Situation(double dist, boolean behind, boolean healing, boolean blocking, boolean shooting, boolean far,
                     int crowd, boolean lowHp) {}

    /** ¿Se está curando (comiendo o bebiendo)? */
    public static boolean healing(LivingEntity e) {
        if (!e.isUsingItem()) return false;
        UseAnim u = e.getUseItem().getUseAnimation();
        return u == UseAnim.EAT || u == UseAnim.DRINK;
    }

    /** ¿Está tensando un arco, una ballesta o apuntando un tridente? */
    public static boolean aiming(LivingEntity e) {
        if (!e.isUsingItem()) return false;
        UseAnim u = e.getUseItem().getUseAnimation();
        return u == UseAnim.BOW || u == UseAnim.CROSSBOW || u == UseAnim.SPEAR;
    }

    static Situation situation(Host h, LivingEntity t, Brain b) {
        LivingEntity me = h.mob();
        double dist = t.position().distanceTo(me.position());
        Vec3 d = t.position().subtract(me.position());
        float to = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        boolean behind = Math.abs(Mth.wrapDegrees(to - me.yBodyRot)) > 110F;
        int crowd = 0;
        if (me.level() instanceof ServerLevel sl) {
            for (ServerPlayer p : sl.players()) {
                if (!p.isSpectator() && !p.isCreative() && p.isAlive() && p.distanceToSqr(me) <= 7 * 7 + me.getBbWidth() * me.getBbWidth()) crowd++;
            }
        }
        boolean shooting = aiming(t) || h.age() - b.lastRangedHit < 60;
        return new Situation(dist, behind, healing(t), t.isBlocking(), shooting, dist > 10, crowd,
                t.getHealth() / Math.max(1F, t.getMaxHealth()) < 0.3F);
    }

    // ------------------------------------------------------------------------------------------- decidir

    /** Ticks entre decisiones: 20 con inteligencia 0 y 4 con 1; más rápido por debajo del 50 % y del 25 % de vida. */
    static int interval(Host h) {
        double smart = h.smart();
        double t = 20 - 16 * smart;
        float hp = h.mob().getHealth() / Math.max(1F, h.mob().getMaxHealth());
        if (hp < 0.5F) t *= 0.85;
        if (hp < 0.25F) t *= 0.8;
        return (int) Math.max(3, Math.round(t / Math.max(0.5, h.rhythm())));
    }

    /** Cada tick del mob (en el servidor), si tiene cerebro y su inteligencia es mayor que 0. */
    static void tick(Host h, Brain b) {
        long now = h.age();
        // El ataque anterior: ¿le entró al jugador?
        if (b.current != null && now - b.currentAt > 70) {
            b.current.missStreak = b.currentHit ? 0 : b.current.missStreak + 1;
            b.current = null;
        }
        LivingEntity target = h.target();
        if (target == null || !target.isAlive() || h.dying()) return;
        double smart = h.smart();
        Situation s = situation(h, target, b);
        // Castigar curas: en cuanto pueda, sin pensárselo
        if (s.healing() && smart >= 0.5) b.urgent = true;
        if (busy(h)) {
            // Combos: pasada la parte peligrosa del ataque, a veces corta la recuperación y encadena otro (Souls)
            Attack cur = b.current;
            if (smart >= 0.6 && cur != null && !cur.noCancel && cur.gcd > 0 && !b.chainTried
                    && now - b.currentAt >= cur.activeEnd / Math.max(0.5, h.tempo()) + 3 && gcdLeft(h) > 6) {
                b.chainTried = true;
                if (ThreadLocalRandom.current().nextDouble() < 0.3 + 0.55 * smart) chain(h, b, s, smart, now);
            }
            return;
        }
        if (!b.urgent && now < b.nextDecision) return;
        if (!b.urgent && now < b.hesitateUntil) return;
        b.nextDecision = now + interval(h);
        Ctx base = SkillMobs.ctx(h);
        List<Attack> ok = usable(h, b, base);
        b.waiting = ok.isEmpty();
        if (ok.isEmpty()) {
            b.urgent = false;
            return;
        }
        // A veces se lo piensa un momento (como los jefes de los Souls, que no siempre atacan en cuanto pueden)
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        // (menos cuanto más listo: con 100 % no se para nunca)
        if (!b.urgent && !b.hesitated && smart >= 0.5 && rnd.nextDouble() < 0.24 * (1 - smart)) {
            b.hesitated = true;
            b.hesitateUntil = now + 3 + rnd.nextInt(10);
            return;
        }
        b.hesitated = false;
        b.urgent = false;
        Set<Attack> done = new HashSet<>();
        for (int k = 0; k < 3 && !ok.isEmpty(); k++) {
            Attack best = null;
            double bestScore = -1e9;
            for (Attack a : ok) {
                if (done.contains(a)) continue;
                double sc = score(h, b, a, s, smart, rnd);
                if (sc > bestScore) {
                    bestScore = sc;
                    best = a;
                }
            }
            if (best == null) break;
            fire(h, b, best, now);
            done.add(best);
            // Si ese ataque no deja al jefe ocupado (sin gcd: las cabezas de la Hidra), puede salir otro a la vez
            if (busy(h) || rnd.nextDouble() > 0.35 + 0.4 * smart) break;
            ok = usable(h, b, SkillMobs.ctx(h));
            ok.removeAll(done);
        }
    }

    /** Los ataques que saldrían ahora (sus condiciones, las de su línea y su cooldown), sin lanzar nada. */
    private static List<Attack> usable(Host h, Brain b, Ctx base) {
        List<Attack> out = new ArrayList<>();
        Map<Mech, List<Tgt>> lineTargets = new IdentityHashMap<>();
        for (Attack a : b.attacks) {
            Mech line = a.line;
            if (line.hp != null && !SkillMobs.hpOk(h, line.hp)) continue;
            boolean lineOk = true;
            for (Cond c : line.c) {
                if (!SkillConds.test(c, base.caster, base, null)) {
                    lineOk = false;
                    break;
                }
            }
            if (!lineOk) continue;
            List<Tgt> targets = lineTargets.computeIfAbsent(line, l -> SkillRuntime.targets(l, base));
            if (targets.isEmpty()) continue;
            Ctx c = base.copy();
            c.targets = targets;
            if (SkillRuntime.usable(h.cls().meta(a.name), c)) out.add(a);
        }
        return out;
    }

    private static double score(Host h, Brain b, Attack a, Situation s, double smart, ThreadLocalRandom rnd) {
        double sc = 1.0;
        // Que el alcance encaje con la distancia
        double span = Math.max(2, Math.min(a.max, 24) - a.min);
        sc += smart * 1.5 * (1 - Math.min(1, Math.abs(s.dist() - a.sweet()) / span));
        // Castigos
        if (s.healing()) sc += smart * ((a.gap ? 4 : 0) + (a.ranged ? 3 : 0));
        if (s.shooting()) sc += smart * ((a.gap ? 2.2 : 0) + (a.ranged ? 1 : 0));
        if (s.far()) sc += smart * ((a.gap ? 1.4 : 0) + (a.ranged ? 1.4 : 0));
        if (s.blocking()) sc += smart * ((a.grab ? 3 : 0) + (a.aoe ? 1.2 : 0));
        if (s.behind()) sc += smart * (a.rear ? 3 : 0);
        if (s.crowd() >= 2) sc += smart * (a.aoe ? 1 + s.crowd() * 0.7 : 0);
        if (s.lowHp()) sc += smart * (a.gap ? 1 : 0.4);
        // Lo que tenía pensado hacer (rota el repertorio: no siempre los mismos dos)
        if (a == b.planned) sc += smart * 1.2;
        // No repetir
        int i = 0;
        for (String r : b.recent) {
            if (r.equals(a.name)) sc -= smart * (i == 0 ? 2.2 : i == 1 ? 0.9 : 0.3);
            i++;
        }
        // Aprender: los que le entran salen más; los que esquiva, menos
        if (a.uses >= 2) sc += smart * (1.2 * a.hits / (double) a.uses - 0.35 * Math.min(4, a.missStreak));
        // Algo de azar (menos cuanto más listo)
        sc += rnd.nextDouble() * (0.35 + 2.6 * (1 - smart));
        return sc;
    }

    private static long castSeq = Long.MIN_VALUE / 2;

    private static void fire(Host h, Brain b, Attack a, long now) {
        Ctx c = SkillMobs.ctx(h);
        // Cada ataque es un uso propio (para poder cortar lo que le queda al encadenar el siguiente)
        c.cast = ++castSeq;
        b.currentCast = c.cast;
        b.chainTried = false;
        b.motion = h.motion();
        b.defaults = SkillModels.defaults(h.mob().getUUID());
        b.animMark = SkillModels.mark(h.mob().getUUID());
        if (a == b.planned) b.plannedUntil = 0;
        SkillRuntime.setCurrent(c);
        try {
            if (a.fromPool) SkillRuntime.callNamed(a.name, c, SkillRuntime.targets(a.line, c));
            else SkillRuntime.exec(a.line, c);
        } catch (SkillRuntime.CancelSkill ignored) {
            // la skill se paró sola
        } catch (Throwable t) {
            TFClient.LOGGER.debug("TF Jefes: error en el ataque {} de {}", a.name, h.def().name, t);
        } finally {
            SkillRuntime.setCurrent(null);
        }
        if (b.current != null && b.current != a) b.current.missStreak = b.currentHit ? 0 : b.current.missStreak + 1;
        a.uses++;
        a.lastUse = now;
        b.current = a;
        b.currentAt = now;
        b.currentHit = false;
        b.recent.addFirst(a.name);
        while (b.recent.size() > 6) b.recent.removeLast();
    }

    /** Ticks que le quedan de gcd. */
    private static long gcdLeft(Host h) {
        SkillRuntime.WhoState st = SkillRuntime.stateIfAny(SkillRuntime.Who.of(h.mob()));
        return st == null ? 0 : st.gcdUntil - SkillRuntime.clock;
    }

    /**
     * Encadenar: si hay otro ataque que saldría ahora (sin contar el gcd del que está haciendo), corta lo que le queda
     * al actual (su recuperación: delays pendientes), deja al jefe como estaba antes de empezarlo (velocidad, giro,
     * animaciones por defecto) y lanza el siguiente entrando suave.
     */
    private static void chain(Host h, Brain b, Situation s, double smart, long now) {
        SkillRuntime.WhoState st = SkillRuntime.stateIfAny(SkillRuntime.Who.of(h.mob()));
        if (st == null) return;
        long saved = st.gcdUntil;
        st.gcdUntil = SkillRuntime.clock;
        List<Attack> ok = usable(h, b, SkillMobs.ctx(h));
        ok.remove(b.current);
        if (ok.isEmpty()) {
            st.gcdUntil = saved;
            return;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Attack best = null;
        double bestScore = -1e9;
        for (Attack a : ok) {
            double sc = score(h, b, a, s, smart, rnd);
            if (sc > bestScore) {
                bestScore = sc;
                best = a;
            }
        }
        SkillRuntime.cancelCast(b.currentCast);
        h.restoreMotion(b.motion);
        SkillModels.restoreDefaults(h.mob().getUUID(), b.defaults);
        SkillModels.cutSince(h.mob().getUUID(), b.animMark, 5);
        h.blend(6);
        b.chains++;
        fire(h, b, best, now);
    }

    /** Un golpe del jefe ha dado a un jugador: cuenta para el ataque que está haciendo. */
    static void landed(Brain b, long now) {
        if (b.current == null || b.currentHit || now - b.currentAt > 70) return;
        b.currentHit = true;
        b.current.hits++;
    }

    /** ¿Está en mitad de un ataque (su gcd)? */
    static boolean busy(Host h) {
        SkillRuntime.WhoState st = SkillRuntime.stateIfAny(SkillRuntime.Who.of(h.mob()));
        return st != null && st.gcdUntil > SkillRuntime.clock;
    }

    /**
     * ¿Está atacando? Su gcd o, en los packs que no ponen gcd (la Abominación y Astwood marcan sus ataques con
     * variables), mientras dura lo que hace el ataque que eligió.
     */
    static boolean attacking(Host h, Brain b) {
        if (busy(h)) return true;
        return b != null && b.current != null && h.age() - b.currentAt < b.current.activeEnd / Math.max(0.5, h.tempo());
    }

    // ------------------------------------------------------------------------------------------- moverse

    /** Condiciones que no dicen si un ataque es de ahora (fase, postura), sino si sale en este instante. */
    private static final Set<String> MOMENT = Set.of("distance", "targetwithin", "targetnotwithin", "playerwithin",
            "playernotwithin", "offgcd", "gcd", "fieldofview", "fov", "infieldofview", "lineofsight", "los", "ydiff");

    /** ¿Es un ataque de ahora (su umbral de vida y las condiciones de fase o postura del pack)? */
    private static boolean possible(Host h, Attack a, Ctx base) {
        if (a.line.hp != null && !SkillMobs.hpOk(h, a.line.hp)) return false;
        return SkillRuntime.possible(h.cls().meta(a.name), base, MOMENT);
    }

    /**
     * El plan de movimiento: {distancia a la que quiere estar, rodear (-1, 0 o 1), distancia mínima (si se le pega más,
     * se aparta), distancia máxima}; null = perseguir sin más. Piensa en un ataque de los que puede hacer ahora (de su
     * fase y su postura) que tendrá listo pronto y que hace más que no usa (así rota todo su repertorio: se aleja para
     * el lanzallamas, se acerca para el agarre) y se pone a su distancia; rodea mientras espera, como los jefes de los
     * Souls. Con poca inteligencia solo no se pega más de lo que le dejan sus ataques.
     */
    static double[] plan(Host h, Brain b) {
        double smart = h.smart();
        if (h.target() == null) return null;
        long now = h.age();
        if (now >= b.planCheck) {
            b.planCheck = now + 10;
            Ctx base = SkillMobs.ctx(h);
            List<Attack> can = new ArrayList<>();
            for (Attack a : b.attacks) if (possible(h, a, base)) can.add(a);
            // (en mitad de un ataque, sus variables de «atacando» bloquean todo un momento: se queda con lo de antes)
            if (!can.isEmpty()) b.can = can;
            if (b.planned == null || now >= b.plannedUntil || !b.can.contains(b.planned)) {
                SkillRuntime.WhoState st = SkillRuntime.stateIfAny(SkillRuntime.Who.of(h.mob()));
                List<Attack> soon = new ArrayList<>();
                for (Attack a : b.can) {
                    Long until = st == null ? null : st.cooldowns.get(a.name);
                    if (until == null || until - SkillRuntime.clock < 40) soon.add(a);
                }
                if (soon.isEmpty()) soon = new ArrayList<>(b.can);
                Collections.shuffle(soon);
                Attack pick = null;
                int bestAge = -1;
                for (Attack a : soon) {
                    int idx = 0, age = 99;
                    for (String r : b.recent) {
                        if (r.equals(a.name)) {
                            age = idx;
                            break;
                        }
                        idx++;
                    }
                    if (age > bestAge) {
                        bestAge = age;
                        pick = a;
                    }
                }
                b.planned = pick;
                b.plannedUntil = now + 60 + ThreadLocalRandom.current().nextInt(60);
            }
        }
        Attack p = b.planned;
        if (p == null) return null;
        if (smart < 0.4) {
            // Poco listo: persigue sin más, pero sin pegarse más de lo que le dejan sus ataques (la Abominación: todos
            // piden 3 bloques o más; pegado a ella no le salía nada)
            double near = 99;
            for (Attack a : b.can) near = Math.min(near, a.min);
            if (near < 1 || near > 12) return null;
            return new double[] {near + 0.5, 0, b.waiting ? near : 0, 40};
        }
        b.preferred = Math.max(1.5, Math.min(p.sweet(), 14));
        int strafe = 0;
        if (smart >= 0.6 && b.waiting && !busy(h)) {
            if (now >= b.strafeUntil) {
                b.strafe = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
                b.strafeUntil = now + 40 + ThreadLocalRandom.current().nextInt(50);
            }
            strafe = b.strafe;
        }
        // La distancia mínima solo cuenta si ahora no le sale nada (si algo le sale, lo hará; si no, se aparta para lo
        // que tiene pensado: la Abominación, con el jugador pegado, retrocede en vez de quedarse gritando)
        return new double[] {b.preferred, strafe, b.waiting ? Math.min(p.min, 12) : 0, Math.min(p.max, 40)};
    }

    /** ¿Debe girarse hacia su objetivo mientras ataca (antes de que el modelo se bloquee)? Grados por tick, 0 = no. */
    static float tracking(Host h) {
        double smart = h.smart();
        return smart < 0.4 || !attacking(h, brainOf(h)) ? 0F : (float) (2 + 8 * smart);
    }

    private static Brain brainOf(Host h) {
        return SkillMobs.brain(h);
    }

    /** Un jugador le ha dado con un proyectil: el cerebro sabe que le están disparando. */
    static void shotBy(Brain b, long now) {
        b.lastRangedHit = now;
    }
}
