package net.tierrasfantasticas.tfclient.skills;

import com.mojang.math.Axis;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.skills.SkillDefs.Mech;
import net.tierrasfantasticas.tfclient.skills.SkillRuntime.Ctx;
import net.tierrasfantasticas.tfclient.skills.SkillRuntime.Tgt;
import net.tierrasfantasticas.tfclient.vfx.VfxModel;
import net.tierrasfantasticas.tfclient.vfx.VfxPose;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Los modelos de ModelEngine: ponerlos en un efecto o en un jugador (model), sus animaciones (state), cambiar o
 * esconder piezas (changepart, partvisibility) y teñirlos (tint). En un jugador el modelo va en un actor pegado a él
 * que lo tapa (como el disfraz de ModelEngine). El servidor también sabe dónde está cada hueso (@modelpart).
 */
final class SkillModels {
    private static final Map<UUID, SkillActor> DISGUISES = new HashMap<>();
    private static final Map<String, Optional<VfxModel>> MODELS = new HashMap<>();

    private SkillModels() {}

    /** El actor que lleva el modelo de ese objetivo: el propio actor o el disfraz del jugador (null si no tiene). */
    private static SkillActor holder(Tgt t, boolean create, Ctx ctx) {
        if (t.who == null) return null;
        if (t.who.actor != null) return t.who.actor;
        Entity e = t.who.entity;
        if (e == null) return null;
        SkillActor a = DISGUISES.get(e.getUUID());
        if (a != null && !a.alive) {
            DISGUISES.remove(e.getUUID());
            a = null;
        }
        if (a == null && create) {
            float yaw = e instanceof LivingEntity le ? le.yBodyRot : e.getYRot();
            a = new SkillActor(ctx.cls, null, (net.minecraft.server.level.ServerLevel) e.level(), e.position(), yaw, 0F, ctx.caster);
            a.follow = e;
            a.hideHost = true;
            DISGUISES.put(e.getUUID(), a);
            SkillRuntime.ACTORS.add(a);
        }
        return a;
    }

    static String modelId(Mech m) {
        return m.arg(null, "mid", "m", "modelid", "model");
    }

    static void model(Mech m, Ctx ctx, List<Tgt> targets) {
        String mid = modelId(m);
        boolean remove = SkillRuntime.bool(m.arg("false", "remove", "r"));
        for (Tgt t : targets) {
            if (remove) {
                SkillActor a = holder(t, false, ctx);
                if (a == null) continue;
                if (a.follow != null) {
                    a.remove();
                    DISGUISES.remove(a.follow.getUUID());
                } else {
                    a.me = null;
                    a.prop("me", "", null, null);
                }
                continue;
            }
            if (mid == null) continue;
            boolean isNew = t.who != null && t.who.actor == null && t.who.entity != null && holder(t, false, ctx) == null;
            SkillActor a = holder(t, true, ctx);
            if (a == null) continue;
            a.me = mid;
            a.anim = null;
            float sc = (float) SkillRuntime.num(m.arg("1", "scale", "s", "size"), 1);
            if (sc > 0 && sc != a.scale) {
                a.scale = sc;
                if (!isNew) a.prop("scale", String.valueOf(sc), null, null);
            }
            if (isNew) {
                SkillNet.near(a.level, a.pos, a.spawnMessage());
            } else {
                a.prop("me", mid, null, null);
            }
        }
    }

    static void state(Mech m, Ctx ctx, List<Tgt> targets) {
        String s = m.arg(null, "state", "s", "animation", "a");
        if (s == null) return;
        boolean remove = SkillRuntime.bool(m.arg("false", "remove", "r"));
        float speed = (float) SkillRuntime.num(m.arg("1", "speed", "sp"), 1);
        for (Tgt t : targets) {
            SkillActor a = holder(t, false, ctx);
            if (a == null || a.me == null) continue;
            if (remove) {
                if (s.equalsIgnoreCase(a.anim)) a.anim = null;
                a.prop("stop", s, null, null);
            } else {
                a.anim = s;
                a.animStart = a.age;
                a.animSpeed = speed;
                a.prop("state", s, String.valueOf(speed), null);
            }
        }
    }

    /** setmodelscale{scale}: el modelo más grande o más pequeño. */
    static void scale(Mech m, Ctx ctx, List<Tgt> targets) {
        float sc = (float) SkillRuntime.num(m.arg("1", "scale", "s", "amount", "a"), 1);
        if (sc <= 0) return;
        for (Tgt t : targets) {
            SkillActor a = holder(t, false, ctx);
            if (a == null) continue;
            a.scale = sc;
            a.prop("scale", String.valueOf(sc), null, null);
        }
    }

    /**
     * mountmodel @owner (desde el efecto): el jugador se monta en el modelo (el caballo del Invocador). El modelo va
     * con él (anda el jugador) y al jugador se le dibuja subido encima.
     */
    static void mount(Mech m, Ctx ctx, List<Tgt> targets) {
        SkillActor a = ctx.caster.actor;
        if (a == null) return;
        for (Tgt t : targets) {
            Entity e = t.entity();
            if (e == null) continue;
            a.rider = e;
            a.follow = e;
            a.hideHost = false;
            a.prop("mount", String.valueOf(e.getId()), "1.0", null);
            break;
        }
    }

    static void dismount(Ctx ctx, List<Tgt> targets) {
        for (Tgt t : targets) {
            Entity e = t.entity();
            if (e == null) continue;
            SkillActor a = mountedBy(e);
            if (a == null) continue;
            a.rider = null;
            a.follow = null;
            a.prop("mount", "", null, null);
        }
    }

    /** El efecto en el que va montado alguien (null si no). */
    static SkillActor mountedBy(Entity e) {
        for (SkillActor a : SkillRuntime.ACTORS) if (a.alive && a.rider == e) return a;
        return null;
    }

    static void unmounted(SkillActor a) {
        a.rider = null;
    }

    static void changePart(Mech m, Ctx ctx, List<Tgt> targets) {
        String part = m.arg(null, "partid", "pid", "part", "p");
        String newModel = m.arg(null, "newmodelid", "nmid", "nm", "newmodel");
        String newPart = m.arg(part, "newpartid", "npid", "np", "newpart");
        if (part == null || newModel == null) return;
        // El otro modelo, con el mismo nombre que en el pack
        String nm = newModel.contains(".") ? newModel : ctx.cls.id + "." + newModel.toLowerCase(Locale.ROOT);
        for (Tgt t : targets) {
            SkillActor a = holder(t, false, ctx);
            if (a != null) a.prop("part", part, nm, newPart);
        }
    }

    static void partVis(Mech m, Ctx ctx, List<Tgt> targets) {
        String part = m.arg(null, "partid", "pid", "part", "p");
        if (part == null) return;
        boolean visible = SkillRuntime.bool(m.arg("true", "visible", "v", "visibility"));
        boolean child = SkillRuntime.bool(m.arg("true", "child", "c", "children"));
        for (Tgt t : targets) {
            SkillActor a = holder(t, false, ctx);
            if (a != null) a.prop("vis", part, String.valueOf(visible), String.valueOf(child));
        }
    }

    static void tint(Mech m, Ctx ctx, List<Tgt> targets) {
        String color = m.arg("#ffffff", "color", "c");
        for (Tgt t : targets) {
            SkillActor a = holder(t, false, ctx);
            if (a != null) a.prop("tint", color, null, null);
        }
    }

    // ------------------------------------------------------------------------------------------- huesos

    static VfxModel load(String id) {
        if (id == null) return null;
        return MODELS.computeIfAbsent(id, k -> {
            try (InputStream in = SkillModels.class.getResourceAsStream("/assets/tfclient/skills/models/" + k + ".json")) {
                return in == null ? Optional.empty() : Optional.of(VfxModel.read(in));
            } catch (Exception e) {
                TFClient.LOGGER.warn("TF Skills: no se pudo leer el modelo {}", k, e);
                return Optional.empty();
            }
        }).orElse(null);
    }

    /** @modelpart{pid=hueso}: dónde está ahora ese hueso del modelo de quien lanza. */
    static List<Tgt> modelPart(Mech m, Ctx ctx, double yOff) {
        String part = m.targetArg(null, "pid", "p", "partid", "part");
        SkillActor a = holder(Tgt.of(ctx.caster), false, ctx);
        if (a == null || a.me == null || part == null) return List.of(Tgt.at(ctx.caster.pos().add(0, yOff, 0)));
        VfxModel model = load(a.me);
        if (model == null) return List.of(Tgt.at(ctx.caster.pos().add(0, yOff, 0)));
        int b = model.bone(part);
        if (b < 0) return List.of(); // como ModelEngine: sin ese hueso no hay sitio
        Matrix4f[] mats = new Matrix4f[model.bones.length];
        for (int i = 0; i < mats.length; i++) mats[i] = new Matrix4f();
        VfxModel.Anim anim = a.anim == null ? model.anims.get("idle") : model.anims.get(a.anim);
        VfxPose.compute(model, anim, (a.age - a.animStart) / 20F * a.animSpeed, 0F, mats);
        Matrix4f w = new Matrix4f().translate((float) a.pos.x, (float) a.pos.y, (float) a.pos.z)
                .rotate(Axis.YP.rotationDegrees(180F - a.yaw)).scale(1F / 16F).mul(mats[b]);
        Vector4f p = w.transform(new Vector4f(0, 0, 0, 1));
        return List.of(Tgt.at(new Vec3(p.x, p.y + yOff, p.z)));
    }

    static void forget(UUID player) {
        SkillActor a = DISGUISES.remove(player);
        if (a != null) a.remove();
    }

    static void clear() {
        DISGUISES.clear();
    }

    /** El disfraz de un jugador (para mandárselo a quien llega), o null. */
    static SkillActor disguise(UUID player) {
        SkillActor a = DISGUISES.get(player);
        return a != null && a.alive ? a : null;
    }
}
