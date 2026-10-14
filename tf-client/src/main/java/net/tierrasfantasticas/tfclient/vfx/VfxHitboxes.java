package net.tierrasfantasticas.tfclient.vfx;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Las cajas de golpe por piezas de ModelEngine (huesos ob_: cabezas, cuello y cola de la Hidra) en el mundo: cada una
 * se mueve con su hueso. Minecraft solo sabe de cajas alineadas con los ejes, así que cada caja girada se convierte en
 * la caja recta que la contiene. Igual en el servidor (los golpes) y en el cliente (a qué apunta el jugador).
 */
public final class VfxHitboxes {
    private VfxHitboxes() {}

    /** Los huesos con caja de golpe, en orden (el índice es el de la pieza de la entidad). */
    public static int[] bones(VfxModel m) {
        int n = 0;
        for (VfxModel.Bone b : m.bones) if (b.hitbox != null) n++;
        int[] out = new int[n];
        int k = 0;
        for (int i = 0; i < m.bones.length; i++) if (m.bones[i].hitbox != null) out[k++] = i;
        return out;
    }

    /**
     * Cajas en el mundo. mats: pose de los huesos (espacio del modelo); world: del modelo al mundo; hidden: huesos
     * escondidos (con partvis): sus cajas y las de sus hijos no cuentan (null = ninguno).
     */
    public static List<AABB> boxes(VfxModel m, Matrix4f[] mats, Matrix4f world, boolean[] hidden) {
        List<AABB> out = new ArrayList<>();
        Matrix4f full = new Matrix4f();
        Vector3f v = new Vector3f();
        for (int bi : bones(m)) {
            if (hidden != null && hiddenUp(m, bi, hidden)) {
                out.add(null);
                continue;
            }
            float[] hb = m.bones[bi].hitbox;
            full.set(world).mul(mats[bi]);
            if (m.bones[bi].aabb) {
                // b_ de ModelEngine: sigue al hueso pero no gira con él ni con el mob (caja recta del mundo)
                Vector3f c = full.transformPosition(new Vector3f());
                Vector3f sc = full.getScale(new Vector3f());
                out.add(new AABB(c.x + hb[0] * sc.x, c.y + hb[1] * sc.y, c.z + hb[2] * sc.z,
                        c.x + hb[3] * sc.x, c.y + hb[4] * sc.y, c.z + hb[5] * sc.z));
                continue;
            }
            double x0 = Double.MAX_VALUE, y0 = Double.MAX_VALUE, z0 = Double.MAX_VALUE;
            double x1 = -Double.MAX_VALUE, y1 = -Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
            for (int c = 0; c < 8; c++) {
                v.set((c & 1) == 0 ? hb[0] : hb[3], (c & 2) == 0 ? hb[1] : hb[4], (c & 4) == 0 ? hb[2] : hb[5]);
                full.transformPosition(v);
                x0 = Math.min(x0, v.x);
                y0 = Math.min(y0, v.y);
                z0 = Math.min(z0, v.z);
                x1 = Math.max(x1, v.x);
                y1 = Math.max(y1, v.y);
                z1 = Math.max(z1, v.z);
            }
            out.add(new AABB(x0, y0, z0, x1, y1, z1));
        }
        return out;
    }

    private static boolean hiddenUp(VfxModel m, int b, boolean[] hidden) {
        for (int p = b; p >= 0; p = m.bones[p].parent) if (hidden[p]) return true;
        return false;
    }

    /** Lo más lejos que llega el modelo en reposo desde su centro (bloques): para que no se deje de dibujar antes. */
    public static double reach(VfxModel m) {
        if (m.reachCache >= 0) return m.reachCache;
        Matrix4f[] r = m.rest();
        double max = 0;
        Vector3f v = new Vector3f();
        for (int i = 0; i < m.bones.length; i++) {
            VfxModel.Bone b = m.bones[i];
            for (int q = 0; q < b.quadCount; q++) {
                int o = q * VfxModel.QUAD + 4;
                for (int k = 0; k < 4; k++) {
                    v.set(b.quads[o + k * 5], b.quads[o + k * 5 + 1], b.quads[o + k * 5 + 2]);
                    r[i].transformPosition(v);
                    max = Math.max(max, Math.sqrt(v.x * v.x + v.z * v.z) / 16.0);
                    max = Math.max(max, Math.abs(v.y) / 16.0);
                }
            }
        }
        m.reachCache = max;
        return max;
    }
}
