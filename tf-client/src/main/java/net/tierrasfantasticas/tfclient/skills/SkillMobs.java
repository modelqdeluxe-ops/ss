package net.tierrasfantasticas.tfclient.skills;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.skills.SkillDefs.ClassDef;
import net.tierrasfantasticas.tfclient.skills.SkillDefs.Mech;
import net.tierrasfantasticas.tfclient.skills.SkillDefs.MobDef;
import net.tierrasfantasticas.tfclient.skills.SkillRuntime.Ctx;
import net.tierrasfantasticas.tfclient.skills.SkillRuntime.Tgt;
import net.tierrasfantasticas.tfclient.skills.SkillRuntime.Who;

/**
 * Los mobs de verdad de los jefes (el jefe y sus esbirros) con el motor de skills: sus mecánicas de MythicMobs saltan
 * con sus disparadores (~onSpawn, ~onTimer:N, ~onDamaged, ~onDeath, ~onSignal:X, ~onEnterCombat) y sus umbrales de
 * vida («~onDamaged &lt;50%»: las fases), y los scripts «mm:» de las animaciones de su modelo se lanzan a su tiempo.
 * La entidad (bosses/BossEntity) es la que anda, recibe golpes y muere; aquí solo se decide qué hace con sus skills.
 */
public final class SkillMobs {
    private SkillMobs() {}

    /** Lo que el motor necesita de un mob de un jefe (lo implementa la entidad). */
    public interface Host {
        Mob mob();

        ClassDef cls();

        MobDef def();

        /** El jefe del grupo (él mismo si es el jefe): de él salen la dificultad y a quién no se pega. */
        Host boss();

        /** Multiplicador del daño de la dificultad (para los daños fijos del pack: «damage{a=15}»). */
        double damageMul();

        /** Ritmo de ataque de la dificultad: 1 = como el pack; 1,5 = los enfriamientos duran 1,5 veces menos. */
        double rhythm();

        /** Vida máxima / vida del pack: lo que va con números fijos de vida (el escudo del Esqueleto) escala con esto. */
        double healthScale();

        LivingEntity target();

        void setAi(boolean on);

        void setSpeedMul(double m);

        void setLocked(boolean locked);

        /** bodyrotation{mh;mb;rde;rdu}: cuánto gira la cabeza sola y cómo la sigue el cuerpo. */
        void setBodyRotation(float maxHead, float maxBody, int delay, int duration);

        /** setrotation{yaw}: gira el cuerpo (relativo) o lo pone mirando a ese giro. */
        void turn(float yaw, boolean relative);

        /** look: mira hacia ese sitio (immediately = de golpe). */
        void face(Vec3 at, boolean immediately);

        /** La barra de jefe cambia de color o estilo (barSet del pack). */
        void barStyle(String color, String style);

        /** barcreate (se ve) y barremove (se quita) del pack sobre la barra del jefe. */
        default void barVisible(boolean on) {
        }

        /** resetai: vuelve a los objetivos de IA de su tipo de Minecraft ({@link SkillAI#vanillaGoals}). */
        default void resetAi() {
            setAi(true);
        }

        /**
         * runaigoalselector{goal} / runaitargetselector{target}: uno más al final de su IA (AIGoalSelectors o
         * AITargetSelectors); «clear» quita todos los de antes (y, en los de objetivo, a quien perseguía).
         */
        default void addAiGoal(String goal, boolean target) {
        }

        /** Daño que se hace a sí mismo (el del Esqueleto atacado por la espalda con el escudo puesto). */
        void selfDamage(double amount, boolean ignoreResistance);

        String faction();

        /** Ticks desde que apareció. */
        int age();

        /** ¿Ya ha muerto (aunque siga en el mundo haciendo su animación de muerte)? */
        boolean dying();

        /**
         * Hacia dónde mira la entidad (no el modelo): el «yaw» con el que MythicMobs mide fieldOfView, @forward y
         * hacia dónde salen sus proyectiles. Un mob que persigue mira a su objetivo aunque el modelo gire más despacio.
         */
        default float lookYaw() {
            return mob().yBodyRot;
        }

        /** Inteligencia de la dificultad (0 a 1): cuánto decide su cerebro táctico ({@link SkillBrain}); 0 = el pack tal cual. */
        default double smart() {
            return 0;
        }

        /** Lo rápido que ataca (1 = como el pack): sus retardos se dividen por esto y sus animaciones van así de rápidas. */
        default double tempo() {
            return 1;
        }

        /** Ticks mínimos de entrada de sus animaciones ahora (al encadenar un ataque con otro, para que no salte). */
        default int minLerp() {
            return 0;
        }

        /** Cómo se mueve y gira ahora (velocidad, bloqueo, bodyrotation): para dejarlo igual si se corta un ataque. */
        default double[] motion() {
            return null;
        }

        default void restoreMotion(double[] m) {
        }

        /** Durante «ticks», sus animaciones nuevas entran suaves (minLerp): al encadenar un ataque. */
        default void blend(int ticks) {
        }

        // --- Lo que piden las mecánicas de MythicMobs de los jefes (threat, setinvulnerable, goto, setmaxhealth...)

        /** ¿Tiene la IA puesta? (condición hasai) */
        default boolean ai() {
            return true;
        }

        /**
         * threat{amount;mode} @quien: cambia lo que le odia (ADD, SUBTRACT, MULTIPLY, DIVIDE, SET, FORCE = pasa a ser
         * el primero de la tabla, RESET = a 0). Solo cuentan los jugadores (los objetivos de los jefes).
         */
        default void threat(LivingEntity who, String mode, double amount) {
        }

        /** clearthreat: olvida la tabla de amenaza (y su objetivo, que vuelve a elegir). */
        default void clearThreat() {
        }

        /** Su tabla de amenaza, de quien más odia a quien menos (los vivos a su alcance): @ThreatTable. */
        default List<LivingEntity> threatTable() {
            return List.of();
        }

        /** setinvulnerable{v} del pack: no recibe golpes (no es el setInvulnerable de Minecraft). */
        default void setPackInvulnerable(boolean on) {
        }

        /** goto{speed} @sitio: anda hasta allí (deja de perseguir hasta llegar o en ~10 s). */
        default void goTo(Vec3 at, double speed) {
        }

        /** setmaxhealth{amount;set}: vida máxima nueva (ya en su escala); con «set», también la vida. */
        default void setMaxHealth(double max, boolean alsoHealth) {
        }

        /** Dónde apareció (@SpawnLocation, distanceFromSpawn): la grieta de un jefe. */
        default Vec3 spawnPos() {
            return mob().position();
        }

        /** bindhitbox: el mob «bound» va en la caja ob_ «part» y recibe los golpes que le den a esa caja. */
        default void bindHitbox(String part, Host bound) {
        }

        /** hitboxconfig{p;pass}: cuánto daño deja pasar la caja de golpe de ese hueso (1 = todo, 0 = nada). */
        default void hitboxPass(String part, double pass) {
        }

        /** suicide: muere ya (con su ~onDeath y su animación). */
        default void suicide() {
            mob().kill();
        }

        // --- ModelEngine en la entidad (model{}, scale{}, move, syncyaw)

        /** move{x;y;z;speed;global}: anda este tick en esa dirección (x a su izquierda, z delante; con su escalón). */
        default void move(double side, double up, double front, double speed, boolean global) {
        }

        /** syncyaw: el giro «to» (yaw, body, head o all) pasa a valer el «from» (yaw, body o head). */
        default void syncYaw(String to, String from) {
        }

        /** model{hitbox}: su caja es la del hueso «hitbox» del modelo (true) o la de su tipo de Minecraft (false). */
        default void setModelHitbox(boolean on) {
        }

        /** model{hitboxscale} y scale{hitbox=true}: la escala de su caja de golpe. */
        default void setHitboxScale(float scale) {
        }

        /** model{showhitbox}: false = no se le puede apuntar ni pegar con la mano. */
        default void setShowHitbox(boolean on) {
        }

        /** model{syncbody}: el cuerpo sigue a la cabeza (lo normal) o se queda como está. */
        default void setSyncBody(boolean on) {
        }
    }

    /**
     * El golpe que dispara ~onDamaged, antes de que entre (como el EntityDamageEvent de MythicMobs): cancelevent lo
     * anula y modifydamage lo cambia. Solo cuenta lo que hagan sus skills en el momento (lo que vaya con delay ya llega
     * tarde, igual que en MythicMobs).
     */
    public static final class Hit {
        /** Daño que va a entrar (en la escala del jefe, ya con sus modificadores de daño). */
        public double amount;
        public boolean cancelled;
        /** Vida de verdad / vida del pack: las cifras fijas de modifydamage (ADD, SET) van en la del pack. */
        final double scale;

        Hit(double amount, double scale) {
            this.amount = amount;
            this.scale = scale;
        }
    }

    /** Lo que hacen las entidades (el paquete de jefes): esbirros, objetos que caen, asientos y temblores de cámara. */
    public interface World {
        /** Un esbirro de verdad del pack (summon de un mob con IA): null si no se pudo. */
        Host spawnMinion(Host owner, MobDef def, ServerLevel level, Vec3 at, float yaw);

        /** mountmodel: el jugador se queda en el asiento «seat» del modelo de «host» (agarrado). */
        void seat(Host host, String seat, LivingEntity rider);

        /** dismountall: suelta a quien esté en ese asiento (null = en todos). */
        void unseat(Host host, String seat);

        /** Quién va en ese asiento (null = en cualquiera). */
        List<LivingEntity> seated(Host host, String seat);
    }

    public static World world;

    /** La marca de los mobs de Minecraft que saca un jefe (summon{type=ZOMBIE}): no sueltan nada al morir (BossEvents). */
    public static final String SUMMONED = "tfclient_jefe";

    /**
     * Para la prueba de los jefes (bosses/BossTest): el modelo que lleva y las animaciones que tiene puestas ahora en el
     * servidor (la de por defecto y las de state{}); null si no lleva modelo.
     */
    /**
     * Prueba en el juego (BossTest): los efectos sueltos (sin entidad a la que sigan) que llevan vivos más de «minAge»
     * ticks: nombre del mob, edad y su texto (las llamas de texto del Esqueleto deberían irse a los 20 ticks).
     */
    public static List<String> oldActors(int minAge) {
        List<String> out = new ArrayList<>();
        for (SkillActor a : SkillRuntime.ACTORS) {
            if (!a.alive || a.follow != null || a.carried || a.age < minAge) continue;
            Entity oe = a.owner == null ? null : a.owner.entity;
            String by = a.owner == null ? "sin dueño" : oe != null ? oe.getType().toShortString() + " " + oe.getUUID().toString().substring(0, 8)
                    + (oe.isRemoved() ? " QUITADO" : "") : a.owner.actor != null ? "efecto " + a.owner.actor.name() + (a.owner.actor.alive ? "" : " (ya no está)") : "?";
            out.add((a.def != null ? a.def.name : a.me) + " " + a.age + "t" + (a.text != null ? " «" + a.text + "»" : "") + " [de " + by + (a.origin != null ? ", sale de " + a.origin + " (ahora " + SkillRuntime.clock + ")" : "") + "]");
        }
        return out;
    }

    public static List<String> animNames(Host h) {
        SkillActor a = SkillModels.disguise(h.mob().getUUID());
        if (a == null || a.me == null) return null;
        List<String> out = new ArrayList<>();
        out.add("modelo:" + a.me);
        if (a.layers != null) {
            if (a.layers.baseName() != null) out.add(a.layers.baseName());
            for (net.tierrasfantasticas.tfclient.vfx.VfxLayers.Layer l : a.layers.active()) out.add(l.name);
        } else if (a.anim != null) {
            out.add(a.anim);
        }
        return out;
    }

    /** La muerte épica (BossEntity.epicTick): el modelo, a «factor» de la escala que tenía al morir (se deshace). */
    public static void deathScale(Host h, float factor) {
        SkillActor a = SkillModels.disguise(h.mob().getUUID());
        if (a == null || a.me == null) return;
        if (a.deathScale <= 0) a.deathScale = a.scale;
        a.scale = Math.max(0.01F, a.deathScale * factor);
        a.prop("scale", String.valueOf(a.scale), null, null);
    }

    /** La música de la pelea (client/TFBossMusic): el jefe «boss» la pide a ese jugador (on) o deja de pedírsela. */
    public static void music(ServerPlayer p, UUID boss, boolean on) {
        if (p.hasDisconnected()) return;
        SkillNet.toPlayer(p, new SkillNet.Screen("music", boss.toString(), "", on ? 1 : 0));
    }

    /** En el cliente: ¿el modelo de esa entidad tiene algún hueso con glowbone? (lo pone SkillClient). */
    public static volatile java.util.function.Predicate<Entity> clientGlow = e -> false;
    /** En el cliente: el fuego de model{renderfire;forcefire}: 1 = siempre, -1 = nunca, 0 = el de Minecraft (lo pone SkillClient). */
    public static volatile java.util.function.ToIntFunction<Entity> clientFire = e -> 0;

    /** El hueso (su id) de la caja de golpe «index» del modelo del mob tal como está ahora, o null. */
    /** ¿Es «part» una caja ob_ de su modelo (una de sus BossPart)? (bindhitbox solo engancha a esas, como ModelEngine) */
    static boolean isHitboxBone(Host h, String part) {
        net.tierrasfantasticas.tfclient.vfx.VfxModel m = SkillModels.modelOf(SkillModels.disguise(h.mob().getUUID()));
        if (m == null || part == null) return false;
        int b = m.bone(part);
        if (b < 0) return false;
        for (int i : net.tierrasfantasticas.tfclient.vfx.VfxHitboxes.bones(m)) if (i == b) return true;
        return false;
    }

    public static String partBone(Host h, int index) {
        net.tierrasfantasticas.tfclient.vfx.VfxModel m = SkillModels.modelOf(SkillModels.disguise(h.mob().getUUID()));
        if (m == null) return null;
        int[] hb = net.tierrasfantasticas.tfclient.vfx.VfxHitboxes.bones(m);
        return index >= 0 && index < hb.length ? m.bones[hb[index]].id : null;
    }

    /** Lo de cada mob de jefe vivo: sus temporizadores y por dónde van los scripts de su modelo. */
    private static final class State {
        final Host host;
        final List<Mech> timers = new ArrayList<>();
        boolean spawned;
        boolean combat;
        int lastFx;
        boolean diedDone;
        /** Su cerebro táctico (null si no tiene ataques que decidir) y si ya se ha mirado. */
        SkillBrain.Brain brain;
        boolean brainBuilt;
        /** Último tick con objetivo (para ~onEnterCombat y ~onDropCombat). */
        int lastCombat;
        /** Sigue «en combate» para ~onDropCombat (se va tras 10 s sin objetivo, como entra una vez por pelea). */
        boolean inFight;
        /** Su objetivo del tick anterior (~onChangeTarget). */
        UUID lastTarget;
        /** Hearing (~onHear): la vibración que le está llegando (null = ninguna), cuándo llega y hasta cuándo no oye otra. */
        Heard heard;
        int heardAt;
        int deafUntil;
        /** Jugadores que ha matado (condición playerkills). */
        int playerKills;
        /** La caja (hueso ob_/b_) que le golpearon la última vez (condición hitbox/lasthitbox de ModelEngine). */
        String lastHitbox;

        State(Host host) {
            this.host = host;
        }
    }

    /** Una vibración que ha oído (~onHear): quién la hizo, dónde y a qué distancia. */
    private record Heard(Entity source, Vec3 at, double distance, String type) {}

    private static final Map<UUID, State> STATES = new HashMap<>();

    private static State state(Host h) {
        return STATES.computeIfAbsent(h.mob().getUUID(), k -> {
            State s = new State(h);
            for (Mech m : h.def().mechs) if ("ontimer".equals(m.tr)) s.timers.add(m);
            return s;
        });
    }

    /** La ejecución de una skill del mob: él lanza, su objetivo es @target y su jefe pone la dificultad. */
    static Ctx ctx(Host h) {
        Ctx c = new Ctx(h.cls(), Who.of(h.mob()));
        c.origin = h.mob().position();
        c.aim = h.target();
        c.host = h.boss();
        c.cast = -1L - h.mob().getId(); // la inmunidad a golpes va por mob
        c.vars = new HashMap<>();
        return c;
    }

    // ------------------------------------------------------------------------------------------- disparadores

    /**
     * Primer tick del mob en el mundo: sus ~onSpawn, ~onSpawnOrLoad (los jefes no se guardan: nunca «cargan»), ~onReady
     * (la primera vez que sale de un generador: el jefe, de su grieta; un esbirro invocado, no) y ~onCombat (el
     * disparador de las líneas sin ninguno, que en MythicMobs salta al aparecer, al pegar, al recibir y al morir).
     */
    public static void spawned(Host h) {
        State s = state(h);
        if (s.spawned) return;
        s.spawned = true;
        run(h, "onspawn", null, null, null);
        run(h, "onspawnorload", null, null, null);
        if (h.boss() == h) run(h, "onready", null, null, null);
        run(h, "oncombat", null, null, null);
    }

    /** Cada tick del mob (en el servidor). */
    public static void tick(Host h) {
        if (h.mob().isRemoved()) return; // ya olvidado (removed): no volver a nacer
        State s = state(h);
        if (!s.spawned) spawned(h);
        if (h.dying()) {
            scripts(h, s);
            return;
        }
        int age = h.age();
        LivingEntity target = h.target();
        boolean combat = target != null && target.isAlive();
        // ~onEnterCombat una vez por pelea: si pierde el objetivo un momento (la correa de la grieta, un cambio de
        // objetivo) no vuelve a «entrar» (la Abominación volvía a gritar cada vez, quieta delante del jugador)
        if (combat && !s.combat && (s.lastCombat == 0 || age - s.lastCombat > 200)) run(h, "onentercombat", target, null, null);
        if (combat) {
            s.lastCombat = age;
            s.inFight = true;
        } else if (s.inFight && age - s.lastCombat > 200) {
            // ~onDropCombat: con la misma regla que la entrada (10 s sin nadie a quien atacar = se acabó la pelea)
            s.inFight = false;
            run(h, "ondropcombat", null, null, null);
        }
        // ~onChangeTarget: pasa de un objetivo a otro (con @trigger = el nuevo)
        UUID now = combat ? target.getUUID() : null;
        if (now != null && s.lastTarget != null && !now.equals(s.lastTarget)) run(h, "onchangetarget", target, null, null);
        if (now != null) s.lastTarget = now;
        s.combat = combat;
        if (!s.brainBuilt) {
            s.brainBuilt = true;
            try {
                s.brain = SkillBrain.build(h);
            } catch (Throwable t) {
                TFClient.LOGGER.warn("TF Jefes: sin cerebro para {}", h.def().name, t);
            }
        }
        // Con cerebro, sus líneas de ataque no salen solas: las elige él (ver SkillBrain)
        boolean brain = s.brain != null && h.smart() > 0;
        for (Mech m : s.timers) {
            if (brain && s.brain.handles(m)) continue;
            int every = (int) Math.max(1, SkillRuntime.num(m.trv, 20));
            if (age % every != 0 || !hpOk(h, m.hp)) continue;
            Ctx c = ctx(h);
            SkillRuntime.setCurrent(c);
            try {
                SkillRuntime.exec(m, c);
            } catch (SkillRuntime.CancelSkill ignored) {
                // la skill se paró sola
            } catch (Throwable t) {
                TFClient.LOGGER.debug("TF Jefes: error en un temporizador de {}", h.def().name, t);
            } finally {
                SkillRuntime.setCurrent(null);
            }
        }
        if (brain) {
            try {
                SkillBrain.tick(h, s.brain);
            } catch (Throwable t) {
                TFClient.LOGGER.debug("TF Jefes: error en el cerebro de {}", h.def().name, t);
            }
        }
        // rootmotion de ModelEngine: anda lo que su animación mueve el hueso raíz
        SkillModels.rootStep(h);
        // ~onHear: la vibración que oyó le llega ahora (tarda un tick por bloque, como al Warden)
        if (s.heard != null && age >= s.heardAt) {
            Heard v = s.heard;
            s.heard = null;
            Map<String, String> vars = new HashMap<>();
            vars.put("volume", SkillRuntime.fmt(Math.max(1, Math.min(15, v.distance()))));
            vars.put("sound-type", v.type());
            run(h, "onhear", v.source(), null, v.at(), vars, null);
        }
        scripts(h, s);
    }

    /** Los scripts «mm:» de las animaciones del modelo que han tocado desde el último tick. */
    private static void scripts(Host h, State s) {
        SkillActor a = SkillModels.disguise(h.mob().getUUID());
        if (a == null || a.layers == null || a.me == null) return;
        net.tierrasfantasticas.tfclient.vfx.VfxModel model = SkillModels.modelOf(a);
        if (model == null) return;
        int now = a.age;
        if (now <= s.lastFx) {
            s.lastFx = now;
            return;
        }
        List<String> due = new ArrayList<>();
        a.layers.scripts(s.lastFx, now, due::add);
        a.layers.baseScripts(model, s.lastFx, now, due::add);
        s.lastFx = now;
        for (String skill : due) {
            Ctx c = ctx(h);
            SkillRuntime.runMeta(h.cls().meta(skill), c);
        }
    }

    /**
     * Le van a hacer daño (aún no ha entrado, como el EntityDamageEvent de MythicMobs): sus ~onDamaged, con quien le
     * pegó como @trigger, «damage-amount» en la escala del pack y la vida de antes del golpe para sus umbrales
     * («~onDamaged &lt;50%»). Devuelve el golpe: cancelevent lo anula y modifydamage lo cambia.
     */
    public static Hit damaged(Host h, Entity attacker, String cause, double amount) {
        double scale = Math.max(0.01, h.healthScale());
        Hit hit = new Hit(amount, scale);
        if (h.dying()) return hit;
        State st = STATES.get(h.mob().getUUID());
        if (st != null && st.brain != null && "PROJECTILE".equals(cause)) SkillBrain.shotBy(st.brain, h.age());
        Map<String, String> vars = new HashMap<>();
        vars.put("damage-amount", SkillRuntime.fmt(amount / scale));
        vars.put("damage-cause", cause);
        run(h, "ondamaged", attacker, cause, vars, hit);
        // ~onCombat también salta al recibir el golpe (con el mismo golpe: puede anularlo o cambiarlo)
        if (!h.dying()) run(h, "oncombat", attacker, cause, null, vars, hit);
        return hit;
    }

    /**
     * Va a pegar a «victim» con el golpe de Minecraft (MeleeAttackGoal: el EntityDamageByEntityEvent de MythicMobs, antes
     * de que entre): sus ~onAttack y ~onCombat, con @trigger = a quien pega y «damage-amount» en la
     * escala del pack (sin la dificultad). Devuelve el golpe: cancelevent lo anula y modifydamage lo cambia (sus cifras
     * fijas, con la dificultad).
     */
    public static Hit attack(Host h, LivingEntity victim, double amount) {
        double scale = Math.max(0.01, h.damageMul());
        Hit hit = new Hit(amount, scale);
        if (h.dying()) return hit;
        Map<String, String> vars = new HashMap<>();
        vars.put("damage-amount", SkillRuntime.fmt(amount / scale));
        vars.put("damage-cause", "ENTITY_ATTACK");
        vars.put("damage-type", "ENTITY_ATTACK");
        run(h, "onattack", victim, "ENTITY_ATTACK", vars, hit);
        if (!h.dying()) run(h, "oncombat", victim, "ENTITY_ATTACK", null, vars, hit);
        return hit;
    }

    /** Para que un ~onSkillDamage que hace daño no se vuelva a disparar a sí mismo sin fin. */
    private static boolean inSkillDamage;

    /** Ha hecho daño con una mecánica (damage, un proyectil...): ~onSkillDamage, con @trigger = a quien ha dado. */
    static void skillDamage(Host h, LivingEntity victim) {
        if (h.dying() || inSkillDamage) return;
        inSkillDamage = true;
        try {
            run(h, "onskilldamage", victim, null, null);
        } finally {
            inSkillDamage = false;
        }
    }

    /** Lo mismo para ~onShoot (un ~onShoot que dispara no se dispara a sí mismo). */
    private static boolean inShoot;

    /**
     * Ha disparado un proyectil de Minecraft (shoot, shootfireball: en MythicMobs son flechas, bolas de fuego...):
     * ~onShoot, con @trigger = él mismo y «bow-tension» (lo tensa que iba el arco: 1, del todo).
     */
    static void shot(Host h, double tension) {
        if (h.dying() || inShoot) return;
        inShoot = true;
        try {
            run(h, "onshoot", h.mob(), null, null, Map.of("bow-tension", SkillRuntime.fmt(tension)), null);
        } finally {
            inShoot = false;
        }
    }

    /**
     * Su tipo de Minecraft va a disparar (la bola de fuego del ghast): ~onShoot antes de que salga, como el
     * ProjectileLaunchEvent que escucha MythicMobs; true si sus skills lo cancelan (cancelevent).
     */
    public static boolean shootCancelled(Host h, Entity target) {
        if (h.dying() || inShoot) return false;
        inShoot = true;
        Hit hit = new Hit(0, 1);
        try {
            run(h, "onshoot", target != null ? target : h.mob(), null, null, Map.of("bow-tension", "1"), hit);
        } finally {
            inShoot = false;
        }
        return hit.cancelled;
    }

    /** Ese proyectil de Minecraft ha dado a alguien: ~onBowHit, con @trigger = a quien ha dado. */
    static void bowHit(Host h, Entity victim) {
        if (!h.dying()) run(h, "onbowhit", victim, null, null);
    }

    /** Uno de sus proyectiles de MythicMobs (projectile, missile) ha dado a alguien: ~onProjectileHit (@trigger = él). */
    static void projectileHit(Host h, Entity victim, Vec3 at) {
        if (!h.dying()) run(h, "onprojectilehit", victim, null, at, null, null);
    }

    /** ...o ha chocado con un bloque: ~onProjectileLand (@origin = donde ha caído). */
    static void projectileLand(Host h, Vec3 at) {
        if (!h.dying()) run(h, "onprojectileland", null, null, at, null, null);
    }

    /** Se va del mundo sin morir (se cierra su grieta, se descarga): ~onDespawn. */
    public static void despawned(Host h) {
        if (!h.dying()) run(h, "ondespawn", null, null, null);
    }

    /** Se ha bajado (o le han bajado) de lo que montaba: ~onDismounted, con @trigger = su montura. */
    public static void dismounted(Host h, Entity vehicle) {
        if (!h.dying()) run(h, "ondismounted", vehicle, null, null);
    }

    /**
     * Una vibración de Minecraft (pasos, golpes, bloques, proyectiles...; game event del tag «vibrations») en «at»: la
     * oyen los mobs con Hearing: Enabled a 16 bloques, como el Warden (al que copia MythicMobs): no oyen a quien va
     * agachado si el evento es de los que se callan así, ni lo amortiguado (lana), ni a sí mismos; la vibración tarda un
     * tick por bloque en llegar, mientras viaja no oyen otra y, al oírla, dos segundos de calma (los del Warden).
     */
    public static void heard(ServerLevel level, Vec3 at, String type, Entity source) {
        if (STATES.isEmpty()) return;
        for (State s : STATES.values()) {
            Host h = s.host;
            Mob mob = h.mob();
            if (!h.def().hearing || mob.level() != level || h.dying() || mob == source) continue;
            if (s.heard != null || h.age() < s.deafUntil) continue;
            double d = mob.position().distanceTo(at);
            if (d > 16) continue;
            s.heard = new Heard(source, at, d, type);
            s.heardAt = h.age() + (int) Math.floor(d);
            s.deafUntil = s.heardAt + 40;
        }
    }

    /** ¿Tiene el mob alguna línea con ese disparador? */
    static boolean listens(Host h, String trigger) {
        for (Mech m : h.def().mechs) if (trigger.equals(m.tr == null ? "oncombat" : m.tr)) return true;
        return false;
    }

    /** ¿Algún mob de jefe oye? (para no mirar cada vibración del mundo si nadie las oye). */
    public static boolean anyHearing() {
        for (State s : STATES.values()) if (s.host.def().hearing) return true;
        return false;
    }

    /** Ha matado a alguien con un golpe o una skill: ~onKill (a cualquiera) y ~onPlayerKill (a un jugador), @trigger = él. */
    public static void killed(Host h, LivingEntity victim) {
        if (h.dying()) return;
        if (victim instanceof net.minecraft.world.entity.player.Player) state(h).playerKills++;
        run(h, "onkill", victim, null, null);
        if (victim instanceof net.minecraft.world.entity.player.Player) run(h, "onplayerkill", victim, null, null);
    }

    /** Le han dado en la caja «index» de su modelo (BossEntity.hurtPart): para la condición hitbox/lasthitbox. */
    public static void hitPart(Host h, int index) {
        state(h).lastHitbox = partBone(h, index);
    }

    /** La caja que le golpearon la última vez (sin prefijo de comportamiento: «ob_rightleg_box» → «rightleg_box»). */
    static String lastHitbox(Host h) {
        State s = STATES.get(h.mob().getUUID());
        if (s == null || s.lastHitbox == null) return null;
        return s.lastHitbox.replaceFirst("^(?i)(ob|b)_", "");
    }

    /** Cuántos jugadores ha matado (condición playerkills). */
    static int playerKills(Host h) {
        State s = STATES.get(h.mob().getUUID());
        return s == null ? 0 : s.playerKills;
    }

    /** Un jugador le hace clic derecho: ~onInteract (@trigger = el jugador). */
    public static void interacted(Host h, Entity who) {
        if (h.dying()) return;
        run(h, "oninteract", who, null, null);
    }

    /** Ha muerto: sus ~onDeath (siguen aunque la entidad ya no esté viva: la animación de muerte, el cofre...). */
    public static void died(Host h, Entity killer) {
        State s = state(h);
        if (s.diedDone) return;
        s.diedDone = true;
        SkillActor a = SkillModels.disguise(h.mob().getUUID());
        if (a != null && a.layers != null && a.me != null) {
            net.tierrasfantasticas.tfclient.vfx.VfxModel model = SkillModels.modelOf(a);
            if (model != null) {
                a.layers.death(model, a.age);
                a.prop("death", "", null, null);
            }
        }
        run(h, "ondeath", killer, null, null);
        run(h, "oncombat", killer, null, null);
    }

    /** signal{s=X} @este mob: sus ~onSignal:X. */
    static void signal(Host h, String sig, Who from) {
        for (Mech m : h.def().mechs) {
            if (!"onsignal".equals(m.tr) || m.trv == null || !m.trv.equalsIgnoreCase(sig)) continue;
            Ctx c = ctx(h);
            c.trigger = from;
            SkillRuntime.exec(m, c);
        }
    }

    private static void run(Host h, String trigger, Entity other, String cause, Map<String, String> vars) {
        run(h, trigger, other, cause, null, vars, null);
    }

    private static void run(Host h, String trigger, Entity other, String cause, Map<String, String> vars, Hit hit) {
        run(h, trigger, other, cause, null, vars, hit);
    }

    /** Las líneas del mob con ese disparador (sin ninguno = ~onCombat, como MythicMobs); «at»: su @origin (null = él). */
    private static void run(Host h, String trigger, Entity other, String cause, Vec3 at, Map<String, String> vars, Hit hit) {
        List<Mech> list = new ArrayList<>();
        for (Mech m : h.def().mechs) {
            String tr = m.tr == null ? "oncombat" : m.tr;
            if (tr.equals(trigger) && hpOk(h, m.hp)) list.add(m);
        }
        if (list.isEmpty()) return;
        Ctx c = ctx(h);
        if (at != null) c.origin = at;
        if (other != null) c.trigger = Who.of(other);
        c.damageCause = cause;
        c.hit = hit;
        if (vars != null) c.vars.putAll(vars);
        // Cada línea por separado (como MythicMobs: una línea con delay no frena a las demás)
        for (Mech m : list) {
            Ctx one = c.copy();
            one.depth = 0;
            SkillRuntime.setCurrent(one);
            try {
                SkillRuntime.exec(m, one);
            } catch (SkillRuntime.CancelSkill ignored) {
                // nada
            } catch (Throwable t) {
                TFClient.LOGGER.debug("TF Jefes: error en {} de {}", trigger, h.def().name, t);
            } finally {
                SkillRuntime.setCurrent(null);
            }
        }
    }

    /** Un golpe de este jefe (o de algo suyo) ha dado a un jugador: su cerebro lo apunta al ataque que hace. */
    static void landed(Host boss) {
        State st = boss == null ? null : STATES.get(boss.mob().getUUID());
        if (st != null && st.brain != null) SkillBrain.landed(st.brain, boss.age());
    }

    /** Plan de movimiento del cerebro: {distancia que busca, rodear -1/0/1}, o null (perseguir sin más). */
    public static double[] plan(Host h) {
        State st = STATES.get(h.mob().getUUID());
        if (st == null || st.brain == null || h.smart() <= 0) return null;
        return SkillBrain.plan(h, st.brain);
    }

    /** Grados por tick que se gira hacia su objetivo mientras ataca (0 = lo que diga su bodyrotation). */
    public static float tracking(Host h) {
        State st = STATES.get(h.mob().getUUID());
        if (st == null || st.brain == null || h.smart() <= 0) return 0F;
        return SkillBrain.tracking(h);
    }

    /** ¿Está en mitad de un ataque (su gcd)? */
    public static boolean busy(Host h) {
        return SkillBrain.busy(h);
    }

    /** ¿Está atacando (su gcd o lo que dura el ataque que eligió su cerebro)? */
    public static boolean attacking(Host h) {
        return SkillBrain.attacking(h, brain(h));
    }

    static SkillBrain.Brain brain(Host h) {
        State st = STATES.get(h.mob().getUUID());
        return st == null ? null : st.brain;
    }

    /** «&lt;50%», «&gt;75%»: ¿cumple la vida del mob el umbral de la línea? */
    static boolean hpOk(Host h, String hp) {
        if (hp == null) return true;
        Mob mob = h.mob();
        double pct = mob.getHealth() / Math.max(1F, mob.getMaxHealth()) * 100.0;
        String r = hp.replace("%", "").trim();
        try {
            if (r.startsWith("<=")) return pct <= Double.parseDouble(r.substring(2));
            if (r.startsWith(">=")) return pct >= Double.parseDouble(r.substring(2));
            if (r.startsWith("<")) return pct < Double.parseDouble(r.substring(1));
            if (r.startsWith(">")) return pct > Double.parseDouble(r.substring(1));
        } catch (NumberFormatException ignored) {
            // sin umbral
        }
        return true;
    }

    // ------------------------------------------------------------------------------------------- ayudas

    /** El mob de jefe de esa entidad, o null. */
    public static Host host(Entity e) {
        return e instanceof Host h ? h : null;
    }

    /** ¿Son del mismo jefe (no se pegan entre ellos)? */
    static boolean sameGroup(Host a, Entity other) {
        Host o = host(other);
        return a != null && o != null && o.boss() == a.boss();
    }

    /** Se ha ido del mundo (muerto del todo o descargado): se olvida. */
    public static void removed(Host h) {
        STATES.remove(h.mob().getUUID());
        SkillModels.forget(h.mob().getUUID());
        if (world != null) world.unseat(h, null);
        // Lo que invocó y sigue ahí (y lo que invocaron esos) se va con él. En MythicMobs el trono del Esqueleto
        // («Despawn: NEVER») se queda para siempre en el servidor del pack; aquí la grieta vuelve a despertar y cada pelea
        // ponía otro trono encima del anterior (la prueba lo veía vivo en la pelea siguiente, 1.3.87).
        java.util.Set<Object> gone = new java.util.HashSet<>();
        gone.add(h.mob());
        boolean more = true;
        while (more) {
            more = false;
            for (SkillActor a : new ArrayList<>(SkillRuntime.ACTORS)) {
                if (a.owner == null || gone.contains(a)) continue;
                // (también a través de un efecto que ya se fue: lo que invocó sigue siendo de este jefe)
                Entity oe = a.owner.entity;
                boolean mine = oe != null && (gone.contains(oe) || oe.isRemoved() && host(oe) != null)
                        || a.owner.actor != null && gone.contains(a.owner.actor);
                if (mine) {
                    gone.add(a);
                    if (a.alive) a.remove();
                    more = true;
                }
            }
        }
    }

    static void clear() {
        STATES.clear();
    }

    /** Para la vista previa y el tamaño: el modelo de ModelEngine de un mob de un jefe. */
    public static net.tierrasfantasticas.tfclient.vfx.VfxModel model(String id) {
        return SkillModels.load(id);
    }

    /** Las cajas de golpe por piezas (huesos ob_) del modelo del mob, en el mundo (null = no tiene modelo). */
    public static List<net.minecraft.world.phys.AABB> hitboxes(Host h) {
        SkillActor a = SkillModels.disguise(h.mob().getUUID());
        if (a == null || a.me == null) return null;
        net.tierrasfantasticas.tfclient.vfx.VfxModel m = SkillModels.modelOf(a);
        if (m == null || net.tierrasfantasticas.tfclient.vfx.VfxHitboxes.bones(m).length == 0) return null;
        return net.tierrasfantasticas.tfclient.vfx.VfxHitboxes.boxes(m, SkillModels.pose(a, m), SkillModels.world(a),
                SkillModels.hiddenBones(a, m));
    }

    /** Dónde está ahora un hueso del modelo del mob (el asiento del agarre), o null. */
    public static Vec3 bonePos(Host h, String bone) {
        SkillActor a = SkillModels.disguise(h.mob().getUUID());
        if (a == null || a.me == null || bone == null) return null;
        net.tierrasfantasticas.tfclient.vfx.VfxModel m = SkillModels.modelOf(a);
        if (m == null) return null;
        int b = m.bone(bone);
        if (b < 0) return null;
        org.joml.Matrix4f w = SkillModels.world(a).mul(SkillModels.pose(a, m)[b]);
        org.joml.Vector4f p = w.transform(new org.joml.Vector4f(0, 0, 0, 1));
        return new Vec3(p.x, p.y, p.z);
    }

    /** A un jugador que empieza a ver el mob: su modelo tal como está ahora (animaciones, piezas, brillo...). */
    public static void replay(Host h, ServerPlayer p) {
        SkillActor a = SkillModels.disguise(h.mob().getUUID());
        if (a != null && a.alive) a.replay(p);
    }

    /** Jugadores que deben ver la barra o los efectos de un mob (a «r» bloques). */
    static List<ServerPlayer> playersNear(Host h, double r) {
        List<ServerPlayer> out = new ArrayList<>();
        if (!(h.mob().level() instanceof ServerLevel level)) return out;
        for (ServerPlayer p : level.players()) if (p.distanceToSqr(h.mob()) <= r * r) out.add(p);
        return out;
    }

    private static long playerCasts = Long.MIN_VALUE / 2;

    /**
     * Una línea de las skills de un objeto de un jefe (la guja y la égida del Esqueleto) con el jugador que lo lleva como
     * lanzador: @target es lo que tiene delante a «reach» bloques. Son skills de jugador: el daño a otros jugadores
     * sigue las reglas de PvP de las skills.
     */
    public static void playerMech(ServerPlayer p, ClassDef cls, Mech m, double reach) {
        Ctx c = new Ctx(cls, Who.of(p));
        c.origin = p.position();
        c.aim = SkillRuntime.aimed(p, reach);
        c.vars = new HashMap<>();
        c.cast = ++playerCasts;
        SkillRuntime.setCurrent(c);
        try {
            SkillRuntime.exec(m, c);
        } catch (SkillRuntime.CancelSkill ignored) {
            // nada
        } catch (Throwable t) {
            TFClient.LOGGER.debug("TF Jefes: error en la skill de un objeto", t);
        } finally {
            SkillRuntime.setCurrent(null);
        }
    }

    /** Para quien lo use desde fuera: lanza una skill del jefe con este mob como lanzador (los objetos que suelta). */
    public static void cast(Host h, String skill, List<Entity> targets) {
        Ctx c = ctx(h);
        if (targets != null && !targets.isEmpty()) {
            List<Tgt> t = new ArrayList<>();
            for (Entity e : targets) t.add(Tgt.of(Who.of(e)));
            c.targets = t;
        }
        SkillRuntime.runMeta(h.cls().meta(skill), c);
    }
}
