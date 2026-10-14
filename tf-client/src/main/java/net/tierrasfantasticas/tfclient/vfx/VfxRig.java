package net.tierrasfantasticas.tfclient.vfx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Lo que ModelEngine 4 hace con un modelo concreto encima de sus animaciones: las cabezas (headconfig, lockmodelhead,
 * headtarget y la velocidad h[speed]_), las colas, segmentos y física (segment, jiggleimpulse; {@link VfxPhysics}) y la
 * cinemática inversa (ik, iktarget, ikchain; {@link VfxIk}). Lo usan igual el cliente (SkillClient, para dibujarlo) y el
 * servidor (SkillModels, para que @modelpart y las cajas ob_ vean el modelo doblado como se ve): los mismos mensajes
 * («props» del actor) y las mismas cuentas en los dos lados. No depende del cliente.
 */
public final class VfxRig {
    /** Una entidad por su id: {x, y, z, alto} en el mundo, o null si no está. */
    public interface Entities extends IntFunction<float[]> {}

    public final VfxModel model;
    private float[] headLimits;
    private float headSpeed = -1F;
    private boolean lockYaw, lockPitch;
    private int targetId = -1;
    private Vector3f targetPos;
    private float targetY;
    private float smoothYaw, smoothPitch, lastHeadT = Float.NaN;
    private VfxPhysics physics;
    private VfxIk ik;

    public VfxRig(VfxModel model) {
        this.model = model;
        physics = new VfxPhysics(model);
        ik = new VfxIk(model);
    }

    /** ¿Hay que calcularlo cada tick aunque nadie mire (lo que tiene memoria: física, IK que entra y sale, cabezas lentas)? */
    public boolean stateful() {
        return physics.active() || ik.active() || headSpeed > 0F || model.headSpeed() > 0F;
    }

    /** Los límites de las cabezas de este modelo ahora (los de sus etiquetas o los que puso headconfig). */
    public float[] limits() {
        return headLimits != null ? headLimits : model.headLimits();
    }

    /**
     * Un mensaje del servidor (SkillMeg / SkillModels.segment): headcfg, headlock, headtgt, seg, jig, ik, iktgt e ikchain,
     * con los mismos textos que se mandan al cliente. Los demás no son de aquí.
     */
    public void message(String key, String a, String b, String c) {
        a = a == null ? "" : a;
        b = b == null ? "" : b;
        c = c == null ? "" : c;
        switch (key) {
            case "headcfg" -> headConfig(a, b, !"0".equals(c));
            case "headlock" -> {
                if (!a.isEmpty()) lockPitch = "1".equals(a);
                if (!b.isEmpty()) lockYaw = "1".equals(b);
            }
            case "headtgt" -> {
                if ("clear".equals(c)) {
                    targetId = -1;
                    targetPos = null;
                } else {
                    String[] p = b.split("\\|");
                    targetId = a.isEmpty() ? -1 : (int) parse(a, -1F);
                    targetPos = new Vector3f(parse(p[0], 0F), parse(p.length > 1 ? p[1] : "", 0F), parse(p.length > 2 ? p[2] : "", 0F));
                    targetY = p.length > 3 ? parse(p[3], 0F) : 0F;
                }
            }
            case "seg" -> physics.configure(a.isEmpty() ? null : a, cfg(b), !"0".equals(c));
            case "jig" -> {
                String[] p = b.split("\\|");
                if (p.length < 7) return;
                physics.impulse(a, !"0".equals(c), new Vector3f(parse(p[1], 0F), parse(p[2], 0F), parse(p[3], 0F)), parse(p[0], 0F),
                        new Vector3f(parse(p[4], 0F), parse(p[5], 0F), parse(p[6], 0F)));
            }
            case "ik" -> {
                Map<String, Float> v = cfg(b);
                ik.configure(a, v, !"0".equals(c));
                physics.configureJig(a, v, !"0".equals(c));
            }
            case "iktgt" -> {
                boolean clear = c.startsWith("1");
                boolean exact = c.length() < 2 || c.charAt(1) == '1';
                if (clear) {
                    ik.target(a, exact, -1, null, 0F, true);
                } else {
                    String[] p = b.split("\\|");
                    if (p.length < 5) return;
                    ik.target(a, exact, (int) parse(p[0], -1F), new Vector3f(parse(p[1], 0F), parse(p[2], 0F), parse(p[3], 0F)),
                            parse(p[4], 0F), false);
                }
            }
            case "ikchain" -> {
                String[] p = b.split("\\|", -1);
                if ("1".equals(c)) ik.removeChain(p[0]);
                else ik.addChain(a, p[0], p.length > 1 ? p[1] : "");
            }
            default -> { }
        }
    }

    /** headconfig: «minYaw|maxYaw|minPitch|maxPitch|speed» («» = no tocar) para esas cabezas (todas si vacío). */
    private void headConfig(String part, String cfg, boolean exact) {
        if (headLimits == null) headLimits = model.headLimits().clone();
        String[] p = cfg.split("\\|", -1);
        for (int b : match(model, part, exact, false)) {
            if (!model.bones[b].head) continue;
            for (int k = 0; k < 4; k++) if (k < p.length && !p[k].isEmpty()) headLimits[b * 4 + k] = parse(p[k], Float.NaN);
        }
        if (p.length > 4 && !p[4].isEmpty()) headSpeed = parse(p[4], 0F);
    }

    /**
     * Hacia dónde miran las cabezas ({yaw respecto al cuerpo, pitch}): la mirada de la entidad, o la de headtarget,
     * sin el eje bloqueado (lockmodelhead) y como mucho a su velocidad (grados por tick). now: ticks (con fracción).
     */
    public float[] look(float headYaw, float headPitch, float bodyYaw, Vector3f eye, Entities entities, float now) {
        Vector3f tp = null;
        if (targetId >= 0) {
            float[] e = entities.apply(targetId);
            if (e != null) tp = new Vector3f(e[0], e[1] + e[3] * 0.85F + targetY, e[2]);
        }
        if (tp == null) tp = targetPos;
        if (tp != null && eye != null) {
            Vector3f d = new Vector3f(tp).sub(eye);
            float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            headYaw = wrap(yaw - bodyYaw);
            headPitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        }
        if (lockYaw) headYaw = 0F;
        if (lockPitch) headPitch = 0F;
        float sp = headSpeed >= 0F ? headSpeed : model.headSpeed();
        if (sp > 0F && !Float.isNaN(lastHeadT)) {
            float dt = Math.max(0F, Math.min(5F, now - lastHeadT));
            smoothYaw += clamp(wrap(headYaw - smoothYaw), -sp * dt, sp * dt);
            smoothPitch += clamp(headPitch - smoothPitch, -sp * dt, sp * dt);
            headYaw = smoothYaw;
            headPitch = smoothPitch;
        } else {
            smoothYaw = headYaw;
            smoothPitch = headPitch;
        }
        lastHeadT = now;
        return new float[] {headYaw, headPitch};
    }

    /**
     * La IK y la física sobre la pose ya animada (mats, espacio del modelo; se cambian). world: del modelo (píxeles) al
     * mundo. animMoves: ¿una animación que suena mueve ese hueso?
     */
    public void bend(Matrix4f[] mats, Matrix4f world, float now, IntPredicate animMoves, Entities entities) {
        if (ik.active()) {
            ik.apply(mats, world, now, animMoves, id -> {
                float[] e = entities.apply(id);
                return e == null ? null : new Vector3f(e[0], e[1] + e[3] * 0.5F, e[2]);
            });
        }
        if (physics.active()) physics.apply(mats, world, now);
    }

    /** Los huesos con ese nombre (todos si vacío; con exact=false, los que lo llevan) y, con child, sus hijos. */
    public static int[] match(VfxModel model, String part, boolean exact, boolean child) {
        if (model == null) return new int[0];
        int n = model.bones.length;
        boolean[] hit = new boolean[n];
        String want = part == null ? "" : part.toLowerCase(Locale.ROOT);
        if (want.isEmpty()) {
            java.util.Arrays.fill(hit, true);
        } else if (exact) {
            int b = model.bone(part);
            if (b >= 0) hit[b] = true;
        } else {
            for (int i = 0; i < n; i++) hit[i] = model.bones[i].id.toLowerCase(Locale.ROOT).contains(want);
        }
        if (child && !want.isEmpty()) {
            for (int i = 0; i < n; i++) if (!hit[i] && model.bones[i].parent >= 0 && hit[model.bones[i].parent]) hit[i] = true;
        }
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < n; i++) if (hit[i]) out.add(i);
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    /** «clave=valor;...» de segment{} e ik{} (ver SkillModels.segment y SkillMeg.ik). */
    private static Map<String, Float> cfg(String s) {
        Map<String, Float> v = new HashMap<>();
        for (String kv : s.split(";")) {
            int eq = kv.indexOf('=');
            if (eq > 0) v.put(kv.substring(0, eq), parse(kv.substring(eq + 1), 0F));
        }
        return v;
    }

    private static float parse(String s, float def) {
        try {
            return Float.parseFloat(s);
        } catch (Exception e) {
            return def;
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float wrap(float a) {
        a = a % 360F;
        if (a >= 180F) a -= 360F;
        if (a < -180F) a += 360F;
        return a;
    }
}
