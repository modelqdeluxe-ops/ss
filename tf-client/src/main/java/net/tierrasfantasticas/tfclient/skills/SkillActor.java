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
    }

    void moveTo(Vec3 p, float yaw, float pitch) {
        if (p.distanceToSqr(pos) > 1e-6 || Math.abs(yaw - this.yaw) > 0.01F || Math.abs(pitch - this.pitch) > 0.01F) moved = true;
        this.pos = p;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    SkillNet.Spawn spawnMessage() {
        return new SkillNet.Spawn(id, pos.x, pos.y, pos.z, yaw, pitch, head, me, def != null && def.small,
                follow == null ? -1 : follow.getId(), hideHost, 0F);
    }

    void prop(String key, String a, String b, String c) {
        SkillNet.near(level, pos, new SkillNet.Prop(id, key, a, b, c));
    }

    void remove() {
        if (!alive) return;
        alive = false;
        prop("remove", null, null, null);
    }

    String name() {
        return def == null ? "" : def.name;
    }
}
