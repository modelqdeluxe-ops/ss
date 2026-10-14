package net.tierrasfantasticas.tfclient.skills;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Un efecto invocado por una skill (en MythicMobs era un mob: un soporte invisible con un modelo en la cabeza que va
 * cambiando de fotograma, o un modelo de ModelEngine). Aquí no es una entidad del mundo: el servidor lo lleva (dónde
 * está, qué lleva, sus temporizadores) y los jugadores cercanos lo dibujan. Así no hay entidades que se queden
 * sueltas, que empujen ni que guarde el mundo.
 */
public final class SkillActor {
    private static int nextId = 1;

    public final int id;
    final SkillDefs.ClassDef cls;
    final SkillDefs.MobDef def;
    final ServerLevel level;
    final SkillRuntime.Who who;
    Vec3 pos;
    float yaw;
    float pitch;
    /** Quien lo invocó (para @owner y @parent) y el jugador al que se le cuenta el daño. */
    final SkillRuntime.Who owner;
    int age;
    boolean alive = true;
    boolean moved;
    int removeAt = -1;
    String head;
    /** Lo que lleva en la mano derecha (modelo de ítem), como los soportes de algunos efectos. */
    String hand;
    String me;
    /** Entidad a la que va pegado (el modelo puesto sobre un jugador) y si la tapa. */
    Entity follow;
    boolean hideHost;
    /** Lo lleva un proyectil (se mueve con él). */
    boolean carried;
    final List<SkillDefs.Mech> timers = new ArrayList<>();
    /** Animación de su modelo de ModelEngine (null = la de reposo), desde qué edad y a qué velocidad. */
    String anim;
    int animStart;
    float animSpeed = 1F;
    /** Para no durar para siempre si nadie lo quita. */
    static final int MAX_AGE = 20 * 60;
    static final int MAX_AGE_CARRIED = 20 * 60 * 90;
    /** setname: el nombre con el que lo nombran sus skills (&lt;caster.name&gt;). */
    String customName;
    /** Esbirros (mobs con IA en el pack): a quién ataca, si tiene la IA puesta (setai) y su velocidad (setspeed). */
    net.minecraft.world.entity.LivingEntity target;
    boolean ai = true;
    double speedMul = 1.0;
    boolean walking;
    /** El jugador montado en él (mountmodel). */
    Entity rider;
    /** Texto de un text_display (settextdisplay): letras de la fuente de la clase o texto normal. */
    String text;
    float scale = 1F;
    /** spin: grados por tick que gira sobre sí mismo y hasta qué edad. */
    float spin;
    int spinUntil;
    // ----- Jefes
    /** El jefe del que viene (su dificultad); null = de una clase de jugador. */
    SkillMobs.Host host;
    /** Animaciones a capas como ModelEngine (los modelos de los jefes); null = una sola animación (las clases). */
    net.tierrasfantasticas.tfclient.vfx.VfxLayers layers;
    /** lockmodel en un efecto: no gira. */
    boolean locked;
    /** Un soporte (no marcador) de un jefe: cae y se le puede lanzar (la cabeza cortada de la Hidra, la grieta). */
    boolean physics;
    Vec3 vel = Vec3.ZERO;
    boolean onGround;
    /** Lo último que se le cambió de cada cosa (para enseñárselo igual a quien llega tarde). */
    final java.util.Map<String, SkillNet.Prop> sticky = new java.util.LinkedHashMap<>();
    /**
     * En los jefes, qué huesos están escondidos ahora, como los deja el cliente con «vis» (para quien llega tarde). No
     * vale guardar el último «vis» de cada hueso: uno con child=true sobre el padre tapa a los de sus hijos, y el orden
     * importa (el Esqueleto esconde sus esquirlas al aparecer y las enseña al forjar la espada de almas).
     */
    private boolean[] hiddenNow;
    private String hiddenModel;
    /**
     * Cabezas, colas/física e IK de ModelEngine también en el servidor (las mismas cuentas que el cliente: VfxRig), para
     * que @modelpart y las cajas ob_ vean el modelo doblado como se ve. Recibe los mismos mensajes que el cliente.
     */
    private net.tierrasfantasticas.tfclient.vfx.VfxRig rig;
    private String rigModel;
    private static final java.util.Set<String> RIG_KEYS = java.util.Set.of("seg", "headcfg", "headlock", "headtgt", "jig", "ik",
            "iktgt", "ikchain");

    SkillActor(SkillDefs.ClassDef cls, SkillDefs.MobDef def, ServerLevel level, Vec3 pos, float yaw, float pitch,
               SkillRuntime.Who owner) {
        this.id = nextId++;
        if (nextId > 2_000_000_000) nextId = 1;
        this.cls = cls;
        this.def = def;
        this.level = level;
        this.pos = pos;
        this.yaw = yaw;
        this.pitch = pitch;
        this.owner = owner;
        this.head = def == null ? null : def.headModel;
        this.who = SkillRuntime.Who.of(this);
        if (cls != null && cls.boss) {
            layers = new net.tierrasfantasticas.tfclient.vfx.VfxLayers();
            physics = def != null && "ARMOR_STAND".equals(def.type) && !def.marker;
        }
    }

    /** Un soporte que cae: gravedad y rozamiento como un soporte de armadura, y se para en el suelo. */
    void physicsTick() {
        if (onGround && vel.lengthSqr() < 1e-6) return;
        vel = new Vec3(vel.x * 0.98, (vel.y - 0.08) * 0.98, vel.z * 0.98);
        Vec3 next = pos.add(vel);
        Vec3 ground = SkillMinions.ground(level, next, pos.y);
        if (next.y <= ground.y) {
            next = new Vec3(next.x, ground.y, next.z);
            vel = new Vec3(vel.x * 0.5, 0, vel.z * 0.5);
            if (vel.lengthSqr() < 1e-4) vel = Vec3.ZERO;
            onGround = true;
        } else {
            onGround = false;
        }
        moveTo(next, yaw, pitch);
    }

    void moveTo(Vec3 p, float yaw, float pitch) {
        if (p.distanceToSqr(pos) > 1e-6 || Math.abs(yaw - this.yaw) > 0.01F || Math.abs(pitch - this.pitch) > 0.01F) moved = true;
        this.pos = p;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    SkillNet.Spawn spawnMessage() {
        return new SkillNet.Spawn(id, pos.x, pos.y, pos.z, yaw, pitch, head, me, def != null && def.small,
                follow == null ? -1 : follow.getId(), hideHost, 0F, hand, anim, cls == null ? "" : cls.id,
                def == null ? "" : def.name, text, scale);
    }

    void setText(String t) {
        text = t;
        prop("text", t, null, null);
    }

    /** signal{s=X}: lanza sus mecánicas ~onSignal:X (quien la manda queda como trigger). */
    void signal(String sig, SkillRuntime.Who from) {
        if (!alive || def == null) return;
        for (SkillDefs.Mech mm : def.mechs) {
            if (!"onsignal".equals(mm.tr) || mm.trv == null || !mm.trv.equalsIgnoreCase(sig)) continue;
            SkillRuntime.Ctx c = new SkillRuntime.Ctx(cls, who);
            c.origin = pos;
            c.trigger = from;
            c.aim = target;
            SkillRuntime.exec(mm, c);
        }
    }

    /** Las mecánicas ~onDespawn (al quitarlo): sus skills de «me voy» (restar esbirros, animación de irse). */
    private void despawnTriggers() {
        if (def == null) return;
        for (SkillDefs.Mech mm : def.mechs) {
            if (!"ondespawn".equals(mm.tr) && !"onremove".equals(mm.tr)) continue;
            SkillRuntime.Ctx c = new SkillRuntime.Ctx(cls, who);
            c.origin = pos;
            c.trigger = owner;
            try {
                SkillRuntime.exec(mm, c);
            } catch (RuntimeException ignored) {
                // nada que hacer: ya se va
            }
        }
    }

    void prop(String key, String a, String b, String c) {
        SkillNet.Prop p = new SkillNet.Prop(id, key, a, b, c);
        SkillNet.near(level, pos, p);
        if (layers != null && me != null && RIG_KEYS.contains(key)) {
            net.tierrasfantasticas.tfclient.vfx.VfxModel m = SkillModels.load(me);
            if (m != null) rig(m).message(key, a, b, c);
        }
        // Lo que se queda (para quien llega tarde): por clave y, en las de un hueso, por hueso
        switch (key) {
            case "me", "tint", "scale", "head", "hand", "text", "dstate", "lock", "remap", "item" -> {
                if (key.equals("me")) {
                    sticky.keySet().removeIf(k -> k.startsWith("vis:") || k.startsWith("part:") || k.startsWith("bright:")
                            || k.startsWith("seg:") || PER_MODEL.stream().anyMatch(k::startsWith));
                    hiddenNow = null;
                }
                sticky.put(key + (key.equals("dstate") ? ":" + a : ""), p);
            }
            case "vis" -> {
                if (layers != null) trackVis(a, Boolean.parseBoolean(b), !"false".equals(c));
                else sticky.put(key + ":" + a, p);
            }
            case "part", "bright", "seg", "headcfg", "bitem", "btag", "btagv", "ench", "bill", "bglow", "ik", "iktgt" -> sticky.put(key + ":" + a, p);
            case "ikchain" -> sticky.put(key + ":" + (b == null ? "" : b.split("\\|")[0]), p);
            case "headlock", "headtgt", "skin", "dtint", "only", "vfxvis" -> sticky.put(key, p);
            case "vfxtf" -> sticky.put(key + ":" + a, p);
            // Correas: las puestas se repiten; al quitarlas, se olvidan (leashself sin destino quita todas las de ese hueso)
            case "leash" -> {
                if ("1".equals(c)) sticky.put(key + ":" + a, p);
                else sticky.remove(key + ":" + a);
            }
            case "leashself" -> {
                if ("1".equals(c)) sticky.put(key + ":" + a + ">" + b, p);
                else sticky.keySet().removeIf(k -> k.startsWith(key + ":" + a + ">"));
            }
            default -> { }
        }
    }

    /** Lo que se guarda por modelo (ModelEngine) y se olvida al cambiarlo. */
    private static final java.util.List<String> PER_MODEL = java.util.List.of("headcfg:", "headlock", "headtgt", "bitem:", "btag:",
            "btagv:", "ench:", "bill:", "bglow:", "skin", "dtint", "ik:", "iktgt:", "ikchain:", "leash:", "leashself:");

    /** Como SkillClient.setVisible: el hueso (y, con children, los que cuelgan de él). */
    private void trackVis(String bone, boolean show, boolean children) {
        net.tierrasfantasticas.tfclient.vfx.VfxModel m = me == null ? null : SkillModels.load(me);
        if (m == null) return;
        if (hiddenNow == null || !me.equals(hiddenModel) || hiddenNow.length != m.bones.length) {
            hiddenNow = new boolean[m.bones.length];
            for (int i = 0; i < hiddenNow.length; i++) hiddenNow[i] = m.bones[i].hidden;
            hiddenModel = me;
        }
        int b = m.bone(bone);
        if (b < 0) return;
        hiddenNow[b] = !show;
        if (!children) return;
        for (int i = 0; i < m.bones.length; i++) {
            for (int p = m.bones[i].parent; p >= 0; p = m.bones[p].parent) {
                if (p == b) {
                    hiddenNow[i] = !show;
                    break;
                }
            }
        }
    }

    /** El VfxRig de su modelo de ahora (uno nuevo al cambiar de modelo, como el cliente). */
    net.tierrasfantasticas.tfclient.vfx.VfxRig rig(net.tierrasfantasticas.tfclient.vfx.VfxModel m) {
        if (rig == null || rig.model != m || !java.util.Objects.equals(me, rigModel)) {
            rig = new net.tierrasfantasticas.tfclient.vfx.VfxRig(m);
            rigModel = me;
        }
        return rig;
    }

    /** Los huesos escondidos ahora en un jefe (true = escondido), o null si no ha escondido ninguno de este modelo. */
    boolean[] hiddenNow(net.tierrasfantasticas.tfclient.vfx.VfxModel m) {
        return hiddenNow != null && me != null && me.equals(hiddenModel) && hiddenNow.length == m.bones.length ? hiddenNow : null;
    }

    /** A un jugador que llega (o que empieza a ver al jefe): el actor tal como está ahora. */
    void replay(net.minecraft.server.level.ServerPlayer player) {
        SkillNet.toPlayer(player, spawnMessage());
        for (SkillNet.Prop p : sticky.values()) SkillNet.toPlayer(player, p);
        if (hiddenNow != null && me != null && me.equals(hiddenModel)) {
            net.tierrasfantasticas.tfclient.vfx.VfxModel m = SkillModels.load(me);
            if (m != null && m.bones.length == hiddenNow.length) {
                for (int i = 0; i < hiddenNow.length; i++) {
                    if (hiddenNow[i] != m.bones[i].hidden) {
                        SkillNet.toPlayer(player, new SkillNet.Prop(id, "vis", m.bones[i].id, String.valueOf(!hiddenNow[i]), "false"));
                    }
                }
            }
        }
        if (layers != null) {
            for (net.tierrasfantasticas.tfclient.vfx.VfxLayers.Layer l : layers.active()) {
                if (l.stopAt >= 0) continue;
                SkillNet.toPlayer(player, new SkillNet.Prop(id, "lstate", l.name, l.speed + "|" + l.lerpIn + "|" + l.lerpOut + "|"
                        + l.priority + "|1", String.valueOf((int) (age - l.start))));
                if (l.loopMode >= 0 || l.overrideMode != null) {
                    // modifystate{loop;ov} que tuviera
                    SkillNet.toPlayer(player, new SkillNet.Prop(id, "lmod", l.name, l.priority + "|NaN|-1|-1|" + l.loopMode + "|"
                            + (l.overrideMode == null ? "" : l.overrideMode ? "1" : "0"), null));
                }
            }
            if (layers.dead()) SkillNet.toPlayer(player, new SkillNet.Prop(id, "death", "", "", ""));
        }
    }

    private boolean leaving;

    void remove() {
        if (!alive || leaving) return;
        leaving = true;
        despawnTriggers();
        alive = false;
        prop("remove", null, null, null);
        SkillModels.unmounted(this);
    }

    /** El tipo de mob del pack (para ?mythicmobtype). */
    String name() {
        return def == null ? "" : def.name;
    }

    /** Como lo llaman sus skills: el que le puso setname o el tipo. */
    String displayName() {
        return customName != null ? customName : name();
    }
}
