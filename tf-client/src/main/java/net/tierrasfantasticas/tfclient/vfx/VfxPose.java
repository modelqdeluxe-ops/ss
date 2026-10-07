package net.tierrasfantasticas.tfclient.vfx;

import org.joml.Matrix4f;

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
        for (int i = 0; i < n; i++) {
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
            if (b.head && headPitch != 0F) m.rotateX(-headPitch * DEG);
            m.rotateZYX((b.rot[2] + extra[base + 2]) * DEG, (b.rot[1] + extra[base + 1]) * DEG, (b.rot[0] + extra[base]) * DEG);
            m.scale(extra[base + 6], extra[base + 7], extra[base + 8]);
        }
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
