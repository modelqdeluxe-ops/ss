/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.vfx;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Pose de un {@link VfxModel} en un instante, con las mismas reglas que Blockbench (y que tools/vfx_bb.py):
 * cada hueso = traslación (pivote − pivote del padre + posición animada) · giro ZYX (reposo + animación) · escala.
 * La interpolación copia la del editor: escalón, lineal y catmullrom uniforme con los vecinos de cada tramo.
 */
public final class VfxPose {
    private static final float EPS = 1F / 1200F;
    private static final float DEG = (float) (Math.PI / 180.0);

    private VfxPose() {}

    /** Tiempo dentro de la animación, o NaN si ya terminó y no cuenta (una vez). */
    public static float animTime(VfxModel.Anim anim, float t) {
        if (anim.loop == 2 && anim.length > 0) return t % anim.length;
        if (t > anim.length + EPS) return anim.loop == 1 ? anim.length : Float.NaN;
        return Math.max(0F, t);
    }

    /**
     * Rellena out[i] con la matriz del hueso i en el espacio del modelo (píxeles). headPitch (grados) inclina los
     * huesos de cabeza (h_) como hace ModelEngine con la mirada; 0 = fijo.
     */
    public static void compute(VfxModel model, VfxModel.Anim anim, float seconds, float headPitch, Matrix4f[] out) {
        int n = model.bones.length;
        float[] extra = new float[n * 9];
        for (int i = 0; i < n; i++) {
            extra[i * 9 + 6] = 1F;
            extra[i * 9 + 7] = 1F;
            extra[i * 9 + 8] = 1F;
        }
        float at = anim == null ? Float.NaN : animTime(anim, seconds);
        if (!Float.isNaN(at)) {
            float[] v = new float[3];
            for (VfxModel.Track tr : anim.tracks) {
                if (tr.bone < 0 || tr.bone >= n) continue;
                int base = tr.bone * 9;
                if (tr.rot != null && sample(tr.rot, at, v)) System.arraycopy(v, 0, extra, base, 3);
                if (tr.pos != null && sample(tr.pos, at, v)) System.arraycopy(v, 0, extra, base + 3, 3);
                if (tr.scale != null && sample(tr.scale, at, v)) System.arraycopy(v, 0, extra, base + 6, 3);
            }
        }
        // Los efectos de las clases: cada hueso h_ cabecea por su cuenta en el espacio de su padre (así se probaron sus
        // packs; los jefes van con las reglas de ModelEngine 4 en build)
        for (int i = 0; i < n; i++) {
            VfxModel.Bone b = model.bones[i];
            Matrix4f m = place(model, extra, i, out);
            if (b.head && headPitch != 0F) m.rotateX(-headPitch * DEG);
            finish(b, extra, i, m);
        }
    }

    /** El hueso «i» puesto en su sitio (la matriz de su padre y su traslación), sin girar aún. */
    private static Matrix4f place(VfxModel model, float[] extra, int i, Matrix4f[] out) {
        VfxModel.Bone b = model.bones[i];
        int base = i * 9;
        float px = 0, py = 0, pz = 0;
        if (b.parent >= 0) {
            float[] pp = model.bones[b.parent].pivot;
            px = pp[0];
            py = pp[1];
            pz = pp[2];
        }
        Matrix4f m = out[i];
        if (b.parent >= 0) m.set(out[b.parent]);
        else m.identity();
        m.translate(b.pivot[0] - px + extra[base + 3], b.pivot[1] - py + extra[base + 4], b.pivot[2] - pz + extra[base + 5]);
        return m;
    }

    /** Su giro (reposo + animación, ZYX como Blockbench) y su escala. */
    private static void finish(VfxModel.Bone b, float[] extra, int i, Matrix4f m) {
        int base = i * 9;
        m.rotateZYX((b.rot[2] + extra[base + 2]) * DEG, (b.rot[1] + extra[base + 1]) * DEG, (b.rot[0] + extra[base]) * DEG);
        m.scale(extra[base + 6], extra[base + 7], extra[base + 8]);
    }

    /**
     * Las matrices de los huesos con lo que añaden las animaciones: extra[i*9 ..] = giro (3), posición (3), escala (3).
     * headYaw y headPitch (grados): hacia dónde mira la cabeza respecto al cuerpo (ver el otro build).
     */
    public static void build(VfxModel model, float[] extra, float headYaw, float headPitch, Matrix4f[] out) {
        build(model, extra, headYaw, headPitch, out, model.headLimits());
    }

    /**
     * Los modelos de los jefes, con las cabezas de ModelEngine 4 (wiki «Bone Behaviors»; igual que BetterModel, que lo
     * copia): el giro de la mirada es <b>global</b> (en los ejes del modelo, sobre el pivote del hueso) y <b>no se
     * acumula</b>: una cabeza dentro de otra cabeza no gira el doble (hi_chest e hi_head de la Gárgola: todo el torso
     * mira una vez). Un hijo sin etiqueta de un h_ va donde lo lleve su cabeza pero <b>no gira con ella</b> («los hijos
     * necesitan su propio h_»); con hi_ todos sus hijos son cabeza (el conversor los marca). Cada cabeza tiene sus
     * límites: un hi[yaw=30]_pecho con un h[yaw=90]_cabeza dentro gira 30° el pecho y la cabeza 60° más.
     *
     * <p>limits[i*4 ..]: lo que puede girar la cabeza i (mín. y máx. de yaw, a la derecha positivo; mín. y máx. de pitch,
     * abajo positivo), como h[yaw;pitch;minyaw...]_ y headconfig de ModelEngine; NaN = sin límite.
     */
    public static void build(VfxModel model, float[] extra, float headYaw, float headPitch, Matrix4f[] out, float[] limits) {
        build(model, extra, headYaw, headPitch, out, limits, null);
    }

    /**
     * Igual, con giros puestos a mano en los ejes del modelo (animatemodel{rm=GLOBAL}): global[i*3 ..] en grados (NaN =
     * ninguno). El hueso gira sobre su pivote en los ejes del modelo y lo que cuelga de él va con él.
     */
    public static void build(VfxModel model, float[] extra, float headYaw, float headPitch, Matrix4f[] out, float[] limits,
                             float[] global) {
        int n = model.bones.length;
        if (headYaw == 0F && headPitch == 0F && global == null) {
            for (int i = 0; i < n; i++) finish(model.bones[i], extra, i, place(model, extra, i, out));
            return;
        }
        // q0: el giro de cada hueso en el modelo sin ninguna cabeza; g: el de su mirada (null = no es cabeza)
        Quaternionf[] q0 = new Quaternionf[n];
        Quaternionf[] g = new Quaternionf[n];
        for (int i = 0; i < n; i++) {
            VfxModel.Bone b = model.bones[i];
            int base = i * 9;
            Matrix4f m = place(model, extra, i, out);
            if (b.head && (headYaw != 0F || headPitch != 0F)) {
                float yaw = headYaw, pitch = headPitch;
                if (limits != null) {
                    yaw = clamp(yaw, limits[i * 4], limits[i * 4 + 1]);
                    pitch = clamp(pitch, limits[i * 4 + 2], limits[i * 4 + 3]);
                }
                g[i] = new Quaternionf().rotationY(-yaw * DEG).rotateX(-pitch * DEG);
            }
            Quaternionf q0p = b.parent >= 0 ? q0[b.parent] : null;
            Quaternionf gp = b.parent >= 0 ? g[b.parent] : null;
            if (g[i] != null || gp != null) {
                // Lo que hay que girar aquí (en los ejes del padre) para que el hueso quede con su mirada encima de su giro
                // sin cabezas: q0p⁻¹ · gp⁻¹ · g · q0p (si padre e hijo miran igual, nada)
                Quaternionf c = gp == null ? new Quaternionf() : new Quaternionf(gp).conjugate();
                if (g[i] != null) c.mul(g[i]);
                if (q0p != null) c = new Quaternionf(q0p).conjugate().mul(c).mul(q0p);
                if (Math.abs(c.w) < 0.999999F) m.rotate(c);
            }
            Quaternionf local = new Quaternionf().rotationZYX((b.rot[2] + extra[base + 2]) * DEG, (b.rot[1] + extra[base + 1]) * DEG,
                    (b.rot[0] + extra[base]) * DEG);
            Quaternionf q = q0p == null ? new Quaternionf() : new Quaternionf(q0p);
            if (global != null && !Float.isNaN(global[i * 3])) {
                // En los ejes del modelo sobre su pivote: el giro, visto desde los ejes de su padre (sin cabezas)
                Quaternionf r = new Quaternionf().rotationZYX(global[i * 3 + 2] * DEG, global[i * 3 + 1] * DEG, global[i * 3] * DEG);
                Quaternionf c = q0p == null ? r : new Quaternionf(q0p).conjugate().mul(r).mul(q0p);
                m.rotate(c);
                q.mul(c);
            }
            q0[i] = q.mul(local);
            m.rotate(local);
            m.scale(extra[base + 6], extra[base + 7], extra[base + 8]);
        }
    }

    private static float clamp(float v, float lo, float hi) {
        if (!Float.isNaN(lo) && v < lo) v = lo;
        if (!Float.isNaN(hi) && v > hi) v = hi;
        return v;
    }

    /** Valor de una pista (fotogramas aplanados de 5 en 5) en el instante t. false si la pista está vacía. */
    public static boolean sample(float[] k, float t, float[] out) {
        int count = k.length / 5;
        if (count == 0) return false;
        int before = -1, after = -1;
        for (int i = 0; i < count; i++) {
            float kt = k[i * 5];
            if (kt < t) {
                if (before < 0 || kt > k[before * 5]) before = i;
            } else if (after < 0 || kt < k[after * 5]) {
                after = i;
            }
        }
        if (before >= 0 && Math.abs(k[before * 5] - t) < EPS) return copy(k, before, out);
        if (after >= 0 && Math.abs(k[after * 5] - t) < EPS) return copy(k, after, out);
        if (before >= 0 && k[before * 5 + 4] == 2F) return copy(k, before, out);
        if (before >= 0 && after < 0) return copy(k, before, out);
        if (after >= 0 && before < 0) return copy(k, after, out);
        float bt = k[before * 5], ft = k[after * 5];
        float alpha = (t - bt) / (ft - bt);
        boolean catmull = k[before * 5 + 4] == 1F || k[after * 5 + 4] == 1F;
        // Vecinos para catmullrom: en el orden de los fotogramas (como hace Blockbench)
        int bp = before - 1 >= 0 ? before - 1 : before;
        int ap = before + 2 < count ? before + 2 : after;
        for (int j = 1; j <= 3; j++) {
            float p1 = k[before * 5 + j], p2 = k[after * 5 + j];
            out[j - 1] = catmull ? catmullRom(alpha, k[bp * 5 + j], p1, p2, k[ap * 5 + j]) : p1 + (p2 - p1) * alpha;
        }
        return true;
    }

    private static boolean copy(float[] k, int i, float[] out) {
        out[0] = k[i * 5 + 1];
        out[1] = k[i * 5 + 2];
        out[2] = k[i * 5 + 3];
        return true;
    }

    private static float catmullRom(float t, float p0, float p1, float p2, float p3) {
        float v0 = (p2 - p0) * 0.5F;
        float v1 = (p3 - p1) * 0.5F;
        float t2 = t * t;
        float t3 = t * t2;
        return (2 * p1 - 2 * p2 + v0 + v1) * t3 + (-3 * p1 + 3 * p2 - 2 * v0 - v1) * t2 + v0 * t + p1;
    }
}
