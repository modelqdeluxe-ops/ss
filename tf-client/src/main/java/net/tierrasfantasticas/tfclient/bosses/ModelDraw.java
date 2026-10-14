package net.tierrasfantasticas.tfclient.bosses;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tierrasfantasticas.tfclient.vfx.VfxModel;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Dibuja un modelo de ModelEngine ya posado (la grieta en el mundo y los jefes en la vista previa de /tf boss): cada
 * cara con su textura (las animadas, en su fotograma), las emisivas a plena luz, las demás con la luz que se le dé, y
 * las marcadas «tint» del color del jefe.
 */
@OnlyIn(Dist.CLIENT)
final class ModelDraw {
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();

    private ModelDraw() {}

    /**
     * pose: ya en el sitio del modelo (sus pies), mirando a donde mira; mats: pose de cada hueso (píxeles); hidden:
     * huesos que no se ven (con sus hijos), o null; tint: color de las texturas «tint» (0xRRGGBB).
     */
    static void draw(VfxModel m, Matrix4f[] mats, boolean[] hidden, PoseStack pose, MultiBufferSource buffers, int light,
                     int tint, int age, float alpha) {
        Matrix4f base = new Matrix4f(pose.last().pose()).scale(1F / 16F);
        Matrix3f normalBase = new Matrix3f(pose.last().normal());
        Map<ResourceLocation, List<int[]>> byTex = new HashMap<>();
        for (int bi = 0; bi < m.bones.length; bi++) {
            if (hiddenUp(m, bi, hidden) || m.bones[bi].hidden) continue;
            VfxModel.Bone b = m.bones[bi];
            for (int q = 0; q < b.quadCount; q++) {
                int ti = (int) b.quads[q * VfxModel.QUAD];
                if (ti < 0 || ti >= m.tex.length) continue;
                byTex.computeIfAbsent(TEXTURES.computeIfAbsent(m.tex[ti].path(), ResourceLocation::new), k -> new ArrayList<>())
                        .add(new int[]{bi, q, ti});
            }
        }
        float tr = ((tint >> 16) & 255) / 255F, tg = ((tint >> 8) & 255) / 255F, tb = (tint & 255) / 255F;
        Matrix4f full = new Matrix4f();
        Matrix3f normal = new Matrix3f();
        Vector3f n = new Vector3f();
        Vector4f v = new Vector4f();
        for (Map.Entry<ResourceLocation, List<int[]>> e : byTex.entrySet()) {
            VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentCull(e.getKey()));
            int last = -1;
            for (int[] it : e.getValue()) {
                int bi = it[0], q = it[1], ti = it[2];
                VfxModel.Bone b = m.bones[bi];
                if (bi != last) {
                    full.set(base).mul(mats[bi]);
                    normal.set(normalBase).mul(new Matrix3f(mats[bi]));
                    last = bi;
                }
                int o = q * VfxModel.QUAD;
                VfxModel.Tex t = m.tex[ti];
                float frames = 1, frame = 0;
                if (t.frames() > 1) {
                    frames = t.frames();
                    frame = (age / t.frameTime()) % t.frames();
                }
                n.set(b.quads[o + 1], b.quads[o + 2], b.quads[o + 3]).mul(normal).normalize();
                if (!Float.isFinite(n.x)) n.set(0, 1, 0);
                int lit = t.emissive() || ((int) b.quads[o + 24] & 1) != 0 ? LightTexture.FULL_BRIGHT : light;
                float cr = t.tint() ? tr : 1F, cg = t.tint() ? tg : 1F, cb = t.tint() ? tb : 1F;
                float[] c = VfxModel.uvCenter(b.quads, o);
                for (int k = 0; k < 4; k++) {
                    int p = o + 4 + k * 5;
                    v.set(b.quads[p], b.quads[p + 1], b.quads[p + 2], 1F);
                    full.transform(v);
                    float tu = VfxModel.insetUv(b.quads[p + 3], c[0]);
                    float tv = (VfxModel.insetUv(b.quads[p + 4], c[1]) + frame) / frames;
                    vc.vertex(v.x, v.y, v.z).color(cr, cg, cb, alpha).uv(tu, tv).overlayCoords(OverlayTexture.NO_OVERLAY)
                            .uv2(lit).normal(n.x, n.y, n.z).endVertex();
                }
            }
        }
    }

    private static boolean hiddenUp(VfxModel m, int b, boolean[] hidden) {
        if (hidden == null) return false;
        for (int p = b; p >= 0; p = m.bones[p].parent) if (hidden[p]) return true;
        return false;
    }

    static void clearCache() {
        TEXTURES.clear();
    }
}
