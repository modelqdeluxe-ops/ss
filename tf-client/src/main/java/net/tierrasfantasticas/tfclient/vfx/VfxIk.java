/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.vfx;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Cinemática inversa de ModelEngine 4 en un modelo concreto (uno por mob dibujado): las cadenas de Blockbench (un
 * objeto nulo con «IK Target»: la cadena llega a donde lo pone su animación) y las que crea ikchain{root;tip;pole} (llegan
 * a lo que diga iktarget). Como ModelEngine: una cadena de Blockbench solo manda mientras una animación que suena mueve
 * su nulo (o con ik{alwaysactive}); weight mezcla con la animación; blendticks es lo que tarda en entrar y salir;
 * lockrotation deja la punta con su giro animado; el polo hace que el medio de la cadena se doble hacia él; los huesos
 * ik[minx;maxx...]_ (o ik{minx...}) no giran más de eso (grados de Blockbench).
 *
 * <p>Se resuelve con CCD (cada articulación, de la punta a la raíz, gira lo justo para acercar la punta al objetivo),
 * unas pocas vueltas por fotograma, en el espacio del modelo.
 */
public final class VfxIk {
    private static final class Chain {
        final int[] bones;
        final int tip;
        final int nullBone;
        int pole;
        boolean lock;
        boolean enabled = true;
        boolean always;
        float weight = 1F;
        int blendTicks = 5;
        float blend;
        // iktarget
        boolean hasTarget;
        int targetEntity = -1;
        Vector3f targetPos;
        float targetY;
        final Vector3f lastTarget = new Vector3f();

        Chain(int[] bones, int nullBone, int pole, boolean lock) {
            this.bones = bones;
            this.tip = bones[bones.length - 1];
            this.nullBone = nullBone;
            this.pole = pole;
            this.lock = lock;
        }
    }

    private final VfxModel model;
    private final int n;
    private final List<Chain> chains = new ArrayList<>();
    /** Límites de cada articulación (minx, maxx, miny, maxy, minz, maxz en grados; NaN = libre), o null. */
    private final float[][] limits;
    private float last = Float.NaN;

    public VfxIk(VfxModel m) {
        this.model = m;
        this.n = m.bones.length;
        this.limits = new float[n][];
        for (VfxModel.IkChain c : m.ik) {
            if (c.bones().length >= 2) chains.add(new Chain(c.bones(), c.nullBone(), c.pole(), c.lockRotation()));
        }
        for (int i = 0; i < n; i++) {
            VfxModel.Bone b = m.bones[i];
            if (!"ik".equals(b.beh)) continue;
            float[] l = new float[6];
            String[] keys = {"minx", "maxx", "miny", "maxy", "minz", "maxz"};
            for (int k = 0; k < 6; k++) l[k] = b.param(keys[k], Float.NaN);
            limits[i] = l;
        }
    }

    public boolean active() {
        return !chains.isEmpty();
    }

    private boolean hit(int b, String part, boolean exact) {
        if (part == null || part.isEmpty()) return true;
        String id = model.bones[b].id.toLowerCase(Locale.ROOT), want = part.toLowerCase(Locale.ROOT);
        return exact ? id.equals(want) || id.endsWith("_" + want) && id.indexOf('_') == id.length() - want.length() - 1
                : id.contains(want);
    }

    /** ikchain{root;tip;pole}: una cadena nueva de root a tip (tip debe colgar de root). false si no se puede. */
    public boolean addChain(String root, String tip, String pole) {
        int r = model.bone(root), t = model.bone(tip);
        if (r < 0 || t < 0) return false;
        List<Integer> path = new ArrayList<>();
        for (int b = t; b >= 0; b = model.bones[b].parent) {
            path.add(0, b);
            if (b == r) break;
        }
        if (path.get(0) != r || path.size() < 2) return false;
        removeChain(tip);
        int[] bs = path.stream().mapToInt(Integer::intValue).toArray();
        chains.add(new Chain(bs, -1, pole == null || pole.isEmpty() ? -1 : model.bone(pole), false));
        return true;
    }

    public void removeChain(String tip) {
        int t = model.bone(tip);
        chains.removeIf(c -> c.tip == t && c.nullBone < 0);
    }

    /** ik{p;enabled;alwaysactive;lockrotation;weight;blendticks;limits;minx..maxz}: lo que no venga se queda igual. */
    public void configure(String part, Map<String, Float> v, boolean exact) {
        for (Chain c : chains) {
            int ctl = c.nullBone >= 0 ? c.nullBone : c.tip;
            if (!hit(ctl, part, exact)) continue;
            Float f;
            if ((f = v.get("enabled")) != null) c.enabled = f != 0F;
            if ((f = v.get("always")) != null) c.always = f != 0F;
            if ((f = v.get("lock")) != null) c.lock = f != 0F;
            if ((f = v.get("weight")) != null) c.weight = Math.max(0F, Math.min(1F, f));
            if ((f = v.get("blend")) != null) c.blendTicks = Math.max(0, f.intValue());
        }
        String[] keys = {"minx", "maxx", "miny", "maxy", "minz", "maxz"};
        for (int b = 0; b < n; b++) {
            if (!hit(b, part, exact) || part == null || part.isEmpty()) continue;
            Float lim = v.get("limits");
            if (lim != null && lim == 0F) {
                limits[b] = null;
                continue;
            }
            for (int k = 0; k < 6; k++) {
                Float f = v.get(keys[k]);
                if (f == null) continue;
                if (limits[b] == null) {
                    limits[b] = new float[6];
                    java.util.Arrays.fill(limits[b], Float.NaN);
                }
                limits[b][k] = f;
            }
        }
    }

    /** iktarget{p;y;follow;clear}: la cadena (su nulo o su punta) llega a esa entidad o a ese sitio (mundo). */
    public void target(String part, boolean exact, int entity, Vector3f pos, float y, boolean clear) {
        for (Chain c : chains) {
            int ctl = c.nullBone >= 0 ? c.nullBone : c.tip;
            if (!hit(ctl, part, exact)) continue;
            c.hasTarget = !clear;
            c.targetEntity = entity;
            c.targetPos = pos;
            c.targetY = y;
        }
    }

    /**
     * Resuelve las cadenas sobre la pose (mats, espacio del modelo; se cambian). animMoves: ¿una animación que suena
     * mueve ese hueso? entityPos: dónde está (mundo) una entidad por su id, o null. world: del modelo al mundo.
     */
    public void apply(Matrix4f[] mats, Matrix4f world, float ticks, IntPredicate animMoves, IntFunction<Vector3f> entityPos) {
        if (chains.isEmpty()) return;
        float dt = Float.isNaN(last) ? 0F : Math.max(0F, Math.min(5F, ticks - last));
        last = ticks;
        Matrix4f worldInv = new Matrix4f(world).invert();
        for (Chain c : chains) {
            if (!c.enabled) {
                c.blend = 0F;
                continue;
            }
            Vector3f target = null;
            if (c.hasTarget) {
                Vector3f wp = c.targetEntity >= 0 ? entityPos.apply(c.targetEntity) : null;
                if (wp == null && c.targetPos != null) wp = new Vector3f(c.targetPos);
                if (wp != null) target = worldInv.transformPosition(wp.add(0, c.targetY, 0));
            } else if (c.nullBone >= 0 && (c.always || animMoves.test(c.nullBone))) {
                target = mats[c.nullBone].getTranslation(new Vector3f());
            }
            float want = target != null ? 1F : 0F;
            float step = c.blendTicks <= 0 ? 1F : dt / c.blendTicks;
            if (c.blend < want) c.blend = Math.min(want, c.blend + step);
            else c.blend = Math.max(want, c.blend - step);
            if (target != null) c.lastTarget.set(target);
            float w = c.weight * c.blend;
            if (w <= 0.001F) continue;
            solve(c, mats, c.lastTarget, w);
        }
    }

    private void solve(Chain c, Matrix4f[] mats, Vector3f target, float w) {
        int root = c.bones[0];
        // La pose animada de lo que cuelga de la raíz (para rehacer los hijos y mezclar)
        Matrix4f[] h = new Matrix4f[n];
        boolean[] sub = new boolean[n];
        sub[root] = true;
        for (int i = root; i < n; i++) {
            int p = model.bones[i].parent;
            if (i != root && (p < 0 || !sub[p])) continue;
            sub[i] = true;
            h[i] = new Matrix4f(mats[i]);
        }
        Matrix4f[] s = new Matrix4f[n];
        for (int i = 0; i < n; i++) if (sub[i]) s[i] = new Matrix4f(mats[i]);
        Vector3f tmp = new Vector3f();
        for (int it = 0; it < 16; it++) {
            for (int k = c.bones.length - 2; k >= 0; k--) {
                int j = c.bones[k];
                Vector3f p = s[j].getTranslation(new Vector3f());
                Vector3f e = s[c.tip].getTranslation(new Vector3f());
                Vector3f toE = new Vector3f(e).sub(p), toT = new Vector3f(target).sub(p);
                if (toE.lengthSquared() < 1e-8F || toT.lengthSquared() < 1e-8F) continue;
                Quaternionf rot = s[j].getUnnormalizedRotation(new Quaternionf()).normalize();
                Quaternionf q;
                float lenE = toE.length(), lenT = toT.length();
                Vector3f ne = new Vector3f(toE).div(lenE), nt = new Vector3f(toT).div(lenT);
                if (new Vector3f(ne).cross(nt).lengthSquared() < 1e-8F && ne.dot(nt) > 0F && lenT < lenE - 1e-3F) {
                    // Recta y con el objetivo en su misma línea (más cerca): el CCD no sabe hacia dónde doblar; se dobla un
                    // poco por su bisagra (el eje X del hueso, como las rodillas y los codos de Blockbench)
                    Vector3f hinge = rot.transform(new Vector3f(1, 0, 0));
                    q = new Quaternionf().rotationAxis(0.15F, hinge);
                } else {
                    q = new Quaternionf().rotationTo(ne, nt);
                }
                Quaternionf nr = limit(j, q.mul(rot), s, mats);
                turn(j, p, nr, s, sub);
            }
            if (s[c.tip].getTranslation(tmp).distanceSquared(target) < 0.0025F) break;
        }
        // Polo: el medio de la cadena se dobla hacia él (gira toda la cadena sobre la línea raíz → punta)
        if (c.pole >= 0 && c.bones.length >= 3) {
            Vector3f p0 = s[root].getTranslation(new Vector3f());
            Vector3f e = s[c.tip].getTranslation(new Vector3f());
            Vector3f axis = new Vector3f(e).sub(p0);
            if (axis.lengthSquared() > 1e-8F) {
                axis.normalize();
                Vector3f mid = s[c.bones[c.bones.length / 2]].getTranslation(new Vector3f()).sub(p0);
                Vector3f pole = mats[c.pole].getTranslation(new Vector3f()).sub(p0);
                mid.sub(new Vector3f(axis).mul(mid.dot(axis)));
                pole.sub(new Vector3f(axis).mul(pole.dot(axis)));
                if (mid.lengthSquared() > 1e-8F && pole.lengthSquared() > 1e-8F) {
                    Quaternionf q = new Quaternionf().rotationTo(mid.normalize(), pole.normalize());
                    Quaternionf rot = s[root].getUnnormalizedRotation(new Quaternionf()).normalize();
                    turn(root, s[root].getTranslation(new Vector3f()), q.mul(rot), s, sub);
                }
            }
        }
        // lockrotation: la punta con su giro animado
        if (c.lock) {
            turn(c.tip, s[c.tip].getTranslation(new Vector3f()), h[c.tip].getUnnormalizedRotation(new Quaternionf()).normalize(), s, sub);
        }
        // Mezcla con la animación (weight × entrada/salida): giro local de cada hueso de la cadena
        if (w >= 0.999F) {
            for (int i = 0; i < n; i++) if (sub[i]) mats[i].set(s[i]);
            return;
        }
        boolean[] inChain = new boolean[n];
        for (int b : c.bones) inChain[b] = true;
        for (int i = root; i < n; i++) {
            if (!sub[i]) continue;
            int p = model.bones[i].parent;
            Matrix4f parentNow = i == root ? (p >= 0 ? mats[p] : new Matrix4f()) : mats[p];
            Matrix4f parentAnim = i == root ? parentNow : h[p];
            Matrix4f localAnim = new Matrix4f(parentAnim).invert().mul(h[i]);
            if (inChain[i]) {
                Matrix4f parentSolved = i == root ? parentNow : s[p];
                Matrix4f localSolved = new Matrix4f(parentSolved).invert().mul(s[i]);
                Quaternionf qa = localAnim.getUnnormalizedRotation(new Quaternionf()).normalize();
                Quaternionf qs = localSolved.getUnnormalizedRotation(new Quaternionf()).normalize();
                Vector3f t = localAnim.getTranslation(new Vector3f());
                Vector3f sc = localAnim.getScale(new Vector3f());
                Matrix4f local = new Matrix4f().translate(t).rotate(qa.slerp(qs, w)).scale(sc);
                mats[i].set(parentNow).mul(local);
            } else {
                mats[i].set(parentNow).mul(localAnim);
            }
        }
    }

    /**
     * Gira el hueso «j» sobre su pivote «p» hasta «rot» (giro en el modelo) y lleva con él, tal cual, todo lo que
     * cuelga de él (con lo que ya giró el CCD: si se rehicieran con su giro animado, se perdería y no converge).
     */
    private void turn(int j, Vector3f p, Quaternionf rot, Matrix4f[] s, boolean[] sub) {
        Matrix4f old = new Matrix4f(s[j]);
        Vector3f sc = s[j].getScale(new Vector3f());
        s[j].identity().translate(p).rotate(rot).scale(sc);
        Matrix4f d = new Matrix4f(s[j]).mul(old.invert());
        boolean[] moved = new boolean[n];
        moved[j] = true;
        for (int i = j + 1; i < n; i++) {
            int pa = model.bones[i].parent;
            if (pa < 0 || !moved[pa] || !sub[i]) continue;
            moved[i] = true;
            s[i].set(new Matrix4f(d).mul(s[i]));
        }
    }

    /** Límite de la articulación (sus grados de Blockbench, giro local respecto a su padre). */
    private Quaternionf limit(int j, Quaternionf world, Matrix4f[] s, Matrix4f[] mats) {
        float[] l = limits[j];
        if (l == null) return world;
        int p = model.bones[j].parent;
        Matrix4f pm = p < 0 ? null : s[p] != null ? s[p] : mats[p];
        Quaternionf parent = pm != null ? pm.getUnnormalizedRotation(new Quaternionf()).normalize() : new Quaternionf();
        Quaternionf local = new Quaternionf(parent).conjugate().mul(world);
        Vector3f e = new Matrix3f().set(local).getEulerAnglesZYX(new Vector3f());
        float x = clamp((float) Math.toDegrees(e.x), l[0], l[1]);
        float y = clamp((float) Math.toDegrees(e.y), l[2], l[3]);
        float z = clamp((float) Math.toDegrees(e.z), l[4], l[5]);
        Quaternionf lim = new Quaternionf().rotationZYX((float) Math.toRadians(z), (float) Math.toRadians(y), (float) Math.toRadians(x));
        return parent.mul(lim);
    }

    private static float clamp(float v, float lo, float hi) {
        if (!Float.isNaN(lo) && v < lo) v = lo;
        if (!Float.isNaN(hi) && v > hi) v = hi;
        return v;
    }
}
