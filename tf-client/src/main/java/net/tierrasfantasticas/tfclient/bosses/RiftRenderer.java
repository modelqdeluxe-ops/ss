package net.tierrasfantasticas.tfclient.bosses;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.skills.SkillDefs;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * La grieta 3.0 (1.3.88; el dueño, con capturas: «la textura está mal trabajada, se ve horrible, falta pintura, bordes
 * dentados, caras planas; quiero algo más imponente, más grande»). Ya no es un modelo con texturas:
 * <ul>
 *   <li>La obsidiana (estrado roto, siete garras de 6 a 18 bloques, el cristal que se golpea y 14 esquirlas que orbitan)
 *   es geometría facetada de tools/bosses/grieta3.py ({@code bosses/grieta3.json}); la luz de cada cara se calcula
 *   aquí por vértice ({@link #light}: de arriba, la morada del desgarro en las caras que lo miran y el brillo del borde
 *   según la cámara) y el shader rift_obsidian le pone las vetas que laten y los destellos.</li>
 *   <li>El desgarro (20 bloques de alto) es un cuadro que mira a la cámara dibujado por el shader rift_fx: borde suave
 *   (sin píxeles), el vacío girando dentro con estrellas y el ojo del vórtice, el borde caliente y el halo. En el suelo,
 *   el círculo de runas que gira (el mismo shader).</li>
 * </ul>
 * Estados: lista (todo en reposo: el cristal flota y gira, las esquirlas orbitan, el desgarro respira); despertando
 * (más luz y parpadeo, el desgarro se abre ×1,4, el cristal tiembla y gira rápido, las esquirlas se alejan y aceleran);
 * pelea (se encoge y desaparece en 14 ticks); recarga (el desgarro es una rendija, el cristal pequeño y apagado, sin
 * esquirlas) y, al estar lista otra vez, se abre en 40 ticks. Encima (si no hay pelea), el jefe, su dificultad y qué
 * hacer. Para verla sin el juego: {@code xvfb-run -a python3 tools/bosses/grieta3.py --preview carpeta}.
 */
@OnlyIn(Dist.CLIENT)
public class RiftRenderer extends EntityRenderer<RiftEntity> {
    private static final ResourceLocation NONE = new ResourceLocation("minecraft", "textures/misc/white.png");
    /** Lo mismo que grieta3.py (LIGHT, ROCK...) y que los shaders. */
    private static final Vector3f LIGHT = new Vector3f(0.35F, 0.85F, 0.25F).normalize();
    private static final float[] ROCK = {0.075F, 0.042F, 0.11F}, CRYSTAL = {0.22F, 0.07F, 0.42F}, SHARD = {0.30F, 0.10F, 0.55F},
            TEAR_LIGHT = {0.85F, 0.28F, 1.0F}, RIM = {0.55F, 0.30F, 0.95F};
    private static final int VERT = 10;
    private static Mesh mesh;
    private static boolean meshFailed;

    public RiftRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0F;
    }

    // ------------------------------------------------------------------------------------------- datos

    private static final class Part {
        float[] v;
        float px, py, pz;
    }

    private static final class Mesh {
        final Map<String, Part> parts = new LinkedHashMap<>();
        final List<float[]> shards = new ArrayList<>();
        float tearCy = 11.5F, quadW = 17F, quadH = 27F, ringR = 13.5F, ringY = 0.04F;
    }

    private static Mesh mesh() {
        if (mesh != null || meshFailed) return mesh;
        try (InputStream in = RiftRenderer.class.getResourceAsStream("/assets/tfclient/bosses/grieta3.json")) {
            if (in == null) throw new IllegalStateException("no está grieta3.json");
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            Mesh m = new Mesh();
            for (Map.Entry<String, com.google.gson.JsonElement> e : o.getAsJsonObject("parts").entrySet()) {
                JsonObject po = e.getValue().getAsJsonObject();
                Part p = new Part();
                JsonArray va = po.getAsJsonArray("v");
                p.v = new float[va.size()];
                for (int i = 0; i < p.v.length; i++) p.v[i] = va.get(i).getAsFloat();
                JsonArray pv = po.getAsJsonArray("pivot");
                p.px = pv.get(0).getAsFloat();
                p.py = pv.get(1).getAsFloat();
                p.pz = pv.get(2).getAsFloat();
                m.parts.put(e.getKey(), p);
            }
            for (com.google.gson.JsonElement s : o.getAsJsonArray("shards")) {
                JsonArray a = s.getAsJsonArray();
                float[] f = new float[a.size()];
                for (int i = 0; i < f.length; i++) f[i] = a.get(i).getAsFloat();
                m.shards.add(f);
            }
            JsonObject t = o.getAsJsonObject("tear");
            m.tearCy = t.get("cy").getAsFloat();
            m.quadW = t.get("quad_w").getAsFloat();
            m.quadH = t.get("quad_h").getAsFloat();
            JsonObject r = o.getAsJsonObject("ring");
            m.ringR = r.get("r").getAsFloat();
            m.ringY = r.get("y").getAsFloat();
            mesh = m;
        } catch (Exception ex) {
            meshFailed = true;
            TFClient.LOGGER.error("TF Jefes: no se pudo leer la grieta (grieta3.json)", ex);
        }
        return mesh;
    }

    // ------------------------------------------------------------------------------------------- dibujo

    /** Cómo se ve ahora: luz, apertura del desgarro, cristal, esquirlas y tamaño de todo. */
    private static final class Look {
        float glow = 1F, open = 1F, crystal = 1F, crystalSpin = 0.6F, shake, shardSpeed = 1F, shardOut = 1F, shards = 1F, size = 1F;
    }

    private static Look look(RiftEntity e, float partial) {
        Look l = new Look();
        int st = e.state();
        if (st != e.shownState) {
            e.stateSince = e.shownState < 0 ? -1000 : e.clientAge;
            e.prevState = e.shownState;
            e.shownState = st;
        }
        float since = e.clientAge - e.stateSince + partial;
        float t = e.clientAge + partial;
        switch (st) {
            case RiftEntity.WAKING -> {
                float k = Mth.clamp(1F - (e.left() - partial) / RiftEntity.WAKE_TICKS, 0F, 1F);
                l.glow = 1F + 0.9F * k + 0.25F * k * Mth.sin(t * 1.7F);
                l.open = 1F + 0.4F * k * k;
                l.crystalSpin = 0.6F + 6F * k;
                l.shake = k * 0.12F;
                l.shardSpeed = 1F + 3.5F * k;
                l.shardOut = 1F + 0.6F * k;
            }
            case RiftEntity.FIGHT -> {
                // (1.3.87, lo pidió el dueño) al salir el jefe, la grieta se encoge y desaparece; vuelve al morir él
                l.size = Math.max(0F, 1F - since / 14F);
                l.glow = 1.9F;
                l.open = 1.4F;
            }
            case RiftEntity.COOLDOWN -> {
                l.glow = 0.35F;
                l.open = 0.06F;
                l.crystal = 0.28F;
                l.crystalSpin = 0.15F;
                l.shards = 0F;
                l.size = e.prevState == RiftEntity.FIGHT ? Math.min(1F, since / 30F) : 1F;
            }
            default -> {
                // Lista otra vez tras la recarga: se abre en 40 ticks
                if (e.prevState == RiftEntity.COOLDOWN && since < 40F) {
                    float k = since / 40F;
                    k = k * k * (3 - 2 * k);
                    l.glow = 0.35F + 0.65F * k;
                    l.open = 0.06F + 0.94F * k;
                    l.crystal = 0.28F + 0.72F * k;
                    l.shards = k;
                }
            }
        }
        return l;
    }

    @Override
    public void render(RiftEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        Mesh m = mesh();
        Look l = look(e, partial);
        if (m != null && l.size > 0.001F && RiftShaders.ready()) {
            try {
                draw(e, m, l, partial, pose, buffers, light);
            } catch (Throwable t) {
                TFClient.LOGGER.error("TF Jefes: error al dibujar la grieta", t);
                meshFailed = true;
                mesh = null;
            }
        }
        SkillDefs.ClassDef cls = SkillDefs.bosses().get(e.boss());
        if (cls != null && cls.bossInfo != null && e.state() != RiftEntity.FIGHT) label(e, cls.bossInfo, pose, buffers);
    }

    private void draw(RiftEntity e, Mesh m, Look l, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        float t = e.clientAge + partial;
        Vec3 camW = this.entityRenderDispatcher.camera.getPosition();
        double ex = Mth.lerp(partial, e.xo, e.getX()), ey = Mth.lerp(partial, e.yo, e.getY()), ez = Mth.lerp(partial, e.zo, e.getZ());
        // La cámara en las coordenadas de la grieta (para el brillo del borde y el desgarro que te mira)
        Vector3f cam = new Vector3f((float) ((camW.x - ex) / l.size), (float) ((camW.y - ey) / l.size), (float) ((camW.z - ez) / l.size));
        pose.pushPose();
        if (l.size < 1F) pose.scale(l.size, l.size, l.size);
        Matrix4f mat = pose.last().pose();
        Matrix3f nmat = pose.last().normal();
        VertexConsumer vc = buffers.getBuffer(BossRenderTypes.RIFT_OBSIDIAN);
        Matrix4f id = new Matrix4f();
        emit(vc, m.parts.get("base"), id, mat, nmat, cam, l, light);
        emit(vc, m.parts.get("spires"), id, mat, nmat, cam, l, light);
        // El cristal: flota, gira (y tiembla al despertar)
        Part cr = m.parts.get("crystal");
        if (cr != null && l.crystal > 0.01F) {
            float sx = l.shake > 0 ? Mth.sin(t * 2.9F) * l.shake * 3F : 0F, sz = l.shake > 0 ? Mth.cos(t * 3.3F) * l.shake * 3F : 0F;
            Matrix4f x = new Matrix4f().translate(cr.px + sx, cr.py + 0.3F * Mth.sin(t * 0.065F), cr.pz + sz)
                    .rotateY(t * 0.03F * l.crystalSpin).rotateZ(l.shake * Mth.sin(t * 1.1F)).scale(l.crystal);
            emit(vc, cr, x, mat, nmat, cam, l, light);
        }
        // Las esquirlas que orbitan
        Part sh = m.parts.get("shard");
        if (sh != null && l.shards > 0.01F) {
            for (float[] s : m.shards) {
                float a = s[3] + t * 0.05F * s[2] * l.shardSpeed;
                float r = s[0] * l.shardOut;
                Matrix4f x = new Matrix4f().translate(Mth.cos(a) * r, s[1] + Mth.sin(a * 2 + s[3]) * s[4], Mth.sin(a) * r)
                        .rotateY(a * 3F).scale(s[5] * l.shards);
                emit(vc, sh, x, mat, nmat, cam, l, light);
            }
        }
        // El desgarro (mira a la cámara, girando solo en vertical) y el círculo del suelo
        VertexConsumer fx = buffers.getBuffer(BossRenderTypes.RIFT_FX);
        float seed = (Math.floorMod(e.getUUID().hashCode(), 97)) / 100F;
        float hx = cam.x, hz = cam.z, hl = (float) Math.sqrt(hx * hx + hz * hz);
        if (hl < 1e-3F) {
            hx = 0F;
            hz = 1F;
            hl = 1F;
        }
        float sxx = hz / hl, szz = -hx / hl;
        float hw = m.quadW / 2F, hh = m.quadH / 2F, cy = m.tearCy;
        float intensity = Mth.clamp(l.glow / 2F, 0F, 1F);
        int cr0 = (int) (seed * 255), ca = (int) (intensity * 255), cb = (int) (Mth.clamp(l.open / 1.4F, 0F, 1F) * 255);
        // (el desgarro lleva «abierto» 0..1,4 en b/255×1,4: el shader lo usa tal cual en 0..1; con 1,4 se ve más ancho)
        quad(fx, mat, -sxx * hw, cy - hh, -szz * hw, sxx * hw, cy - hh, szz * hw, sxx * hw, cy + hh, szz * hw, -sxx * hw, cy + hh, -szz * hw,
                cr0, 0, cb, ca);
        float rr = m.ringR, ry = m.ringY;
        quad(fx, mat, -rr, ry, -rr, rr, ry, -rr, rr, ry, rr, -rr, ry, rr, cr0, 255, 255, ca);
        pose.popPose();
    }

    private static void quad(VertexConsumer vc, Matrix4f mat, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2,
                             float z2, float x3, float y3, float z3, int r, int g, int b, int a) {
        vc.vertex(mat, x0, y0, z0).color(r, g, b, a).uv(0F, 0F).endVertex();
        vc.vertex(mat, x1, y1, z1).color(r, g, b, a).uv(1F, 0F).endVertex();
        vc.vertex(mat, x2, y2, z2).color(r, g, b, a).uv(1F, 1F).endVertex();
        vc.vertex(mat, x3, y3, z3).color(r, g, b, a).uv(0F, 1F).endVertex();
    }

    /** Los triángulos de una parte, con su transformación (x) y la luz de cada vértice. */
    private static void emit(VertexConsumer vc, Part p, Matrix4f x, Matrix4f mat, Matrix3f nmat, Vector3f cam, Look l, int light) {
        if (p == null) return;
        float[] v = p.v;
        Vector3f pos = new Vector3f(), n = new Vector3f();
        float[] col = new float[3];
        boolean moved = x.properties() != 4 || x.m30() != 0 || x.m31() != 0 || x.m32() != 0;
        for (int i = 0; i + VERT <= v.length; i += VERT) {
            pos.set(v[i], v[i + 1], v[i + 2]);
            n.set(v[i + 3], v[i + 4], v[i + 5]);
            if (moved) {
                x.transformPosition(pos);
                x.transformDirection(n).normalize();
            }
            int matId = (int) v[i + 8];
            light(pos, n, matId, cam, l.glow, col);
            int vein = (int) Mth.clamp(v[i + 9] * l.glow * 100F, 0F, 255F);
            vc.vertex(mat, pos.x, pos.y, pos.z).color(col[0], col[1], col[2], 1F).uv(v[i + 6], v[i + 7]).overlayCoords(matId, vein)
                    .uv2(light).normal(nmat, n.x, n.y, n.z).endVertex();
        }
    }

    /** La luz de un vértice (la misma cuenta que grieta3.py light): base × (ambiente + difusa) + desgarro + borde. */
    static void light(Vector3f pos, Vector3f n, int mat, Vector3f cam, float glow, float[] out) {
        float[] base = mat == 0 || mat == 3 ? ROCK : mat == 1 ? CRYSTAL : SHARD;
        float dif = Math.max(0F, n.dot(LIGHT));
        float k0 = 0.5F + 0.9F * dif;
        float tx = -pos.x, ty = 11.5F - pos.y, tz = -pos.z;
        float d2 = tx * tx + ty * ty + tz * tz;
        float dl = (float) Math.sqrt(d2 + 1e-9F);
        float k = Math.max(0F, (n.x * tx + n.y * ty + n.z * tz) / dl) * glow * 80F / (80F + d2) * (mat == 0 || mat == 3 ? 0.55F : 0.9F);
        float vx = cam.x - pos.x, vy = cam.y - pos.y, vz = cam.z - pos.z;
        float vl = (float) Math.sqrt(vx * vx + vy * vy + vz * vz) + 1e-9F;
        float fr = 1F - Math.abs((n.x * vx + n.y * vy + n.z * vz) / vl);
        fr = fr * fr * fr * (mat == 0 || mat == 3 ? 0.35F : 0.7F);
        for (int c = 0; c < 3; c++) out[c] = Mth.clamp(base[c] * k0 + TEAR_LIGHT[c] * k + RIM[c] * fr, 0F, 1F);
    }

    /** El cartel de encima: jefe, dificultad y estado. */
    private void label(RiftEntity e, SkillDefs.BossInfo bi, PoseStack pose, MultiBufferSource buffers) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.distanceToSqr(e) > 48 * 48) return;
        List<Component> lines = new ArrayList<>();
        int st = e.state();
        lines.add(Component.literal(bi.name.toUpperCase(java.util.Locale.ROOT)).withStyle(s -> s.withColor(TextColor.fromRgb(bi.color)).withBold(true)));
        lines.add(Component.literal(e.label()).withStyle(s -> s.withColor(TextColor.fromRgb(e.labelColor()))));
        String state = switch (st) {
            case RiftEntity.READY -> "Golpea el cristal para despertarlo";
            case RiftEntity.WAKING -> "¡Despierta!";
            case RiftEntity.FIGHT -> "Incursión en curso";
            default -> "Vuelve en " + Rifts.time(e.left());
        };
        lines.add(Component.literal(state).withStyle(s -> s.withColor(TextColor.fromRgb(st == RiftEntity.WAKING ? 0xFF6A6A : 0xD8D8E0))));
        Font font = mc.font;
        pose.pushPose();
        // Encima del desgarro (que llega a ~21,5 bloques)
        pose.translate(0, 23.0, 0);
        pose.mulPose(this.entityRenderDispatcher.cameraOrientation());
        pose.scale(-0.05F, -0.05F, 0.05F);
        Matrix4f mat = pose.last().pose();
        int bg = (int) (mc.options.getBackgroundOpacity(0.35F) * 255F) << 24;
        float y = -lines.size() * 11;
        for (Component c : lines) {
            float x = -font.width(c) / 2F;
            font.drawInBatch(c, x, y, 0xFFFFFFFF, false, mat, buffers, Font.DisplayMode.SEE_THROUGH, bg, LightTexture.FULL_BRIGHT);
            font.drawInBatch(c, x, y, 0xFFFFFFFF, false, mat, buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            y += 11;
        }
        pose.popPose();
    }

    @Override
    protected boolean shouldShowName(RiftEntity e) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(RiftEntity e) {
        return NONE;
    }
}
