/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
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
import net.tierrasfantasticas.tfclient.vacio.AfterShaders;
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
 *
 * <p>1.3.89: la grieta madre ({@link RiftEntity#MOTHER}; el dueño: «una más imponente que la que tenemos, para poner
 * criaturas más poderosas»): su propia malla ({@code bosses/grieta3_madre.json}: estrado de 18 lados, 11 garras de hasta
 * 27 bloques, cristal ×1,5, 24 esquirlas, desgarro de 30 y círculo de 19,5) y en carmesí (aquí la luz de cada cara; en
 * los shaders, material +4 y modo 0,25/1). La grieta del Void (la fija del centro de la isla) lleva encima su nombre.
 * Con paquete de shaders (Oculus), la grieta se dibuja después de la pasada final del paquete ({@link AfterShaders}).
 *
 * <p>1.3.91 (el dueño: «quita los pilares y la base; deja la grieta cerrada con sus cristales y, cuando rompa el
 * cristal, que se abra y salga el jefe; que desate su poder y se distorsione el espacio-tiempo hasta matarlo, y se
 * cierre»): ya no hay estrado ni garras; solo el cristal y sus esquirlas, flotando delante de una grieta cerrada (una
 * rendija de luz, {@link #SEAM}). Al despertarla el cristal se agrieta (brilla por dentro), tiembla y gira cada vez más
 * rápido y la rendija se entreabre; al empezar la pelea el cristal estalla en trozos que salen despedidos (y caen) y la
 * grieta se abre de golpe (con rebote) y se queda abierta, respirando, mientras las esquirlas giran deprisa más lejos.
 * Al morir el jefe se cierra cada vez más rápido, las esquirlas caen en ella, fogonazo al juntarse, y el cristal se
 * rehace despacio. La distorsión de la imagen (lente, remolino, ondas, temblor) la hace {@link RiftWarp}.
 */
@OnlyIn(Dist.CLIENT)
public class RiftRenderer extends EntityRenderer<RiftEntity> {
    private static final ResourceLocation NONE = new ResourceLocation("minecraft", "textures/misc/white.png");
    /** Lo mismo que grieta3.py (LIGHT, ROCK...) y que los shaders. */
    private static final Vector3f LIGHT = new Vector3f(0.35F, 0.85F, 0.25F).normalize();
    /** Colores [tipo]: la normal (morado) y la madre (carmesí), los mismos que grieta3.py COLORS. */
    private static final float[][] ROCK = {{0.075F, 0.042F, 0.11F}, {0.085F, 0.030F, 0.060F}},
            CRYSTAL = {{0.22F, 0.07F, 0.42F}, {0.42F, 0.05F, 0.20F}}, SHARD = {{0.30F, 0.10F, 0.55F}, {0.55F, 0.08F, 0.28F}},
            TEAR_LIGHT = {{0.85F, 0.28F, 1.0F}, {1.0F, 0.22F, 0.45F}}, RIM = {{0.55F, 0.30F, 0.95F}, {0.95F, 0.28F, 0.50F}};
    private static final String[] FILES = {"/assets/tfclient/bosses/grieta3.json", "/assets/tfclient/bosses/grieta3_madre.json"};
    private static final int VERT = 10;
    private static final Mesh[] meshes = new Mesh[2];
    private static final boolean[] meshFailed = new boolean[2];

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
        /** Los trozos del cristal al estallar: dirección (x, y, z), velocidad, giro, tamaño. */
        final List<float[]> burst = new ArrayList<>();
        float tearCy = 11.5F, quadW = 17F, quadH = 27F, ringR = 13.5F, ringY = 0.04F;
    }

    private static Mesh mesh(int kind) {
        if (meshes[kind] != null || meshFailed[kind]) return meshes[kind];
        try (InputStream in = RiftRenderer.class.getResourceAsStream(FILES[kind])) {
            if (in == null) throw new IllegalStateException("no está " + FILES[kind]);
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
            if (o.has("burst")) {
                for (com.google.gson.JsonElement s : o.getAsJsonArray("burst")) {
                    JsonArray a = s.getAsJsonArray();
                    float[] f = new float[a.size()];
                    for (int i = 0; i < f.length; i++) f[i] = a.get(i).getAsFloat();
                    m.burst.add(f);
                }
            }
            JsonObject t = o.getAsJsonObject("tear");
            m.tearCy = t.get("cy").getAsFloat();
            m.quadW = t.get("quad_w").getAsFloat();
            m.quadH = t.get("quad_h").getAsFloat();
            JsonObject r = o.getAsJsonObject("ring");
            m.ringR = r.get("r").getAsFloat();
            m.ringY = r.get("y").getAsFloat();
            meshes[kind] = m;
        } catch (Exception ex) {
            meshFailed[kind] = true;
            TFClient.LOGGER.error("TF Jefes: no se pudo leer la grieta ({})", FILES[kind], ex);
        }
        return meshes[kind];
    }

    // ------------------------------------------------------------------------------------------- dibujo

    /**
     * Cómo se ve ahora: luz, apertura del desgarro, el cristal (tamaño, grietas encendidas, temblor), las esquirlas y los
     * trozos del cristal que salen volando al estallar.
     */
    private static final class Look {
        float glow = 1F, open = SEAM, crystal = 1F, crystalSpin = 0.6F, crack, shake, shardSpeed = 1F, shardOut = 1F, shards = 1F;
        /** Trozos del cristal al estallar: segundos desde que estalló (−1: no). */
        float burst = -1F;
    }

    /** Lo abierta que está la rendija cerrada (la grieta lista: una línea de luz). */
    private static final float SEAM = 0.05F;

    private static float smooth(float a, float b, float x) {
        float t = Mth.clamp((x - a) / (b - a), 0F, 1F);
        return t * t * (3 - 2 * t);
    }

    /**
     * 1.3.91 (el dueño: «quita los pilares y la base; deja la grieta con sus cristales, cerrada, y cuando rompa el cristal
     * se abra la grieta y salga el boss; que desate su poder hasta matar al boss y se cierre»):
     * <ul>
     *   <li>Lista: el desgarro es una rendija de luz cerrada; el cristal flota y gira; las esquirlas orbitan.</li>
     *   <li>Despertando (70 ticks): el cristal se agrieta (sus vetas se encienden), tiembla y se hincha; las esquirlas
     *   se le acercan y aceleran; la rendija empieza a abrirse.</li>
     *   <li>Pelea: el cristal estalla en trozos que salen volando, el desgarro se abre de golpe (con un rebote) y se
     *   queda abierto, respirando, con las esquirlas girando alrededor, mientras dure el jefe.</li>
     *   <li>Recarga (al morir el jefe): el desgarro se cierra en 30 ticks, cada vez más rápido, con un fogonazo, y las
     *   esquirlas caen en él; después el cristal se rehace (de 40 a 140 ticks) y vuelven las esquirlas, apagado hasta
     *   que vuelve a estar lista.</li>
     * </ul>
     */
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
        float flicker = 0.85F + 0.15F * Mth.sin(t * 0.37F) * Mth.sin(t * 0.13F + 1.7F);
        switch (st) {
            case RiftEntity.WAKING -> {
                float k = Mth.clamp(1F - (e.left() - partial) / RiftEntity.WAKE_TICKS, 0F, 1F);
                l.glow = 1F + 1.0F * k + 0.3F * k * Mth.sin(t * 1.7F);
                l.open = SEAM + 0.3F * k * k * (0.8F + 0.2F * Mth.sin(t * 2.3F));
                l.crystal = 1F + 0.1F * k;
                l.crystalSpin = 0.6F + 7F * k;
                l.crack = k;
                l.shake = k * k * 0.16F;
                l.shardSpeed = 1F + 4F * k;
                l.shardOut = 1F - 0.4F * k;
            }
            case RiftEntity.FIGHT -> {
                l.crystal = 0F;
                l.burst = since / 20F;
                // se abre de golpe (rebote) y se queda abierta, respirando
                float o = since < 8F ? 0.35F + 1.15F * smooth(0F, 8F, since) : 1.5F - 0.3F * smooth(8F, 30F, since);
                l.open = o + 0.07F * Mth.sin(t * 0.21F) * smooth(20F, 40F, since);
                l.glow = 2.2F - 0.6F * smooth(4F, 40F, since) + 0.15F * Mth.sin(t * 0.6F);
                l.shardSpeed = 2.6F;
                l.shardOut = 1F + 0.7F * smooth(0F, 30F, since);
                l.shards = smooth(10F, 40F, since);
            }
            case RiftEntity.COOLDOWN -> {
                boolean fromFight = e.prevState == RiftEntity.FIGHT;
                float s2 = fromFight ? since : 1000F;
                // se cierra cada vez más rápido y da un fogonazo al juntarse
                float c = smooth(0F, 30F, s2);
                l.open = Math.max(0.03F, 1.2F * (1F - c * c));
                l.glow = 0.35F + 1.6F * (1F - smooth(0F, 34F, s2)) + 2.5F * Math.max(0F, 1F - Math.abs(s2 - 30F) / 5F);
                // las esquirlas caen en él y luego vuelven con el cristal, que se rehace
                l.shardOut = s2 < 30F ? 1.7F * (1F - c) + 0.05F : 1F;
                l.shards = s2 < 30F ? 1F - c : smooth(100F, 160F, s2);
                l.shardSpeed = s2 < 30F ? 4F : 0.5F;
                l.crystal = smooth(40F, 140F, s2);
                l.crystalSpin = 0.2F;
            }
            default -> {
                l.open = SEAM * flicker;
                // lista otra vez tras la recarga: se enciende en 40 ticks
                if (e.prevState == RiftEntity.COOLDOWN && since < 40F) l.glow = 0.35F + 0.65F * smooth(0F, 40F, since);
            }
        }
        return l;
    }

    @Override
    public void render(RiftEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        int kind = e.kind() == RiftEntity.MOTHER ? 1 : 0;
        Mesh m = mesh(kind);
        Look l = look(e, partial);
        // Con paquete de shaders (Oculus) la grieta se guarda y se dibuja después de su pasada final (AfterShaders;
        // dibujada ahora no dejaría nada); en su pasada de sombras, nada
        boolean late = AfterShaders.active();
        if (m != null && RiftShaders.ready() && !(late && AfterShaders.shadowPass())) {
            try {
                draw(e, m, kind, l, partial, pose, late ? RiftLate.buffers() : buffers, light);
            } catch (Throwable t) {
                TFClient.LOGGER.error("TF Jefes: error al dibujar la grieta", t);
                meshFailed[kind] = true;
                meshes[kind] = null;
            }
        }
        SkillDefs.ClassDef cls = SkillDefs.bosses().get(e.boss());
        SkillDefs.BossInfo bi = cls == null ? null : cls.bossInfo;
        // (1.3.93b, el dueño: «quita el texto encima de la grieta»: la del Void va sin cartel)
        if (e.state() != RiftEntity.FIGHT && bi != null && !e.fixed()) {
            label(e, bi, m == null ? 23F : m.tearCy + m.quadH * 0.426F, pose, buffers);
        }
    }

    private void draw(RiftEntity e, Mesh m, int kind, Look l, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        float t = e.clientAge + partial;
        Vec3 camW = this.entityRenderDispatcher.camera.getPosition();
        double ex = Mth.lerp(partial, e.xo, e.getX()), ey = Mth.lerp(partial, e.yo, e.getY()), ez = Mth.lerp(partial, e.zo, e.getZ());
        // La cámara en las coordenadas de la grieta (para el brillo del borde y el desgarro que te mira)
        Vector3f cam = new Vector3f((float) (camW.x - ex), (float) (camW.y - ey), (float) (camW.z - ez));
        Matrix4f mat = pose.last().pose();
        Matrix3f nmat = pose.last().normal();
        VertexConsumer vc = buffers.getBuffer(BossRenderTypes.RIFT_OBSIDIAN);
        // El cristal: flota, gira (y tiembla, se agrieta y se hincha al despertar)
        Part cr = m.parts.get("crystal");
        if (cr != null && l.crystal > 0.01F) {
            float sx = l.shake > 0 ? Mth.sin(t * 2.9F) * l.shake * 3F : 0F, sz = l.shake > 0 ? Mth.cos(t * 3.3F) * l.shake * 3F : 0F;
            Matrix4f x = new Matrix4f().translate(cr.px + sx, cr.py + 0.3F * Mth.sin(t * 0.065F), cr.pz + sz)
                    .rotateY(t * 0.03F * l.crystalSpin).rotateZ(l.shake * Mth.sin(t * 1.1F)).scale(l.crystal);
            Look lc = l;
            if (l.crack > 0F) {
                lc = new Look();
                lc.glow = l.glow * (1F + 2.5F * l.crack * (0.7F + 0.3F * Mth.sin(t * 1.9F)));
            }
            emit(vc, cr, x, mat, nmat, cam, lc, light, kind, m.tearCy);
        }
        // Las esquirlas que orbitan
        Part sh = m.parts.get("shard");
        if (sh != null && l.shards > 0.01F) {
            for (float[] s : m.shards) {
                float a = s[3] + t * 0.05F * s[2] * l.shardSpeed;
                float r = s[0] * l.shardOut;
                Matrix4f x = new Matrix4f().translate(Mth.cos(a) * r, s[1] + Mth.sin(a * 2 + s[3]) * s[4], Mth.sin(a) * r)
                        .rotateY(a * 3F).scale(s[5] * l.shards);
                emit(vc, sh, x, mat, nmat, cam, l, light, kind, m.tearCy);
            }
        }
        // Los trozos del cristal al estallar: salen del cristal hacia fuera, girando, caen un poco y se apagan en 1,6 s
        if (sh != null && cr != null && l.burst >= 0F && l.burst < 1.6F) {
            float b = l.burst;
            float fade = 1F - smooth(0.9F, 1.6F, b);
            float k = (float) (1 - Math.exp(-b * 2.2)) / 2.2F;
            Look lb = new Look();
            lb.glow = 1.2F + 2F * (1F - smooth(0F, 0.6F, b));
            for (float[] f : m.burst) {
                float d = f[3] * k;
                Matrix4f x = new Matrix4f().translate(cr.px + f[0] * d, cr.py + f[1] * d - 2.5F * b * b, cr.pz + f[2] * d)
                        .rotateXYZ(f[4] * b, f[4] * b * 0.7F, f[4] * b * 0.4F).scale(f[5] * fade);
                emit(vc, sh, x, mat, nmat, cam, lb, light, kind, m.tearCy);
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
        // (modo en g: 0 desgarro, 64 el de la madre; 191 círculo, 255 el de la madre)
        quad(fx, mat, -sxx * hw, cy - hh, -szz * hw, sxx * hw, cy - hh, szz * hw, sxx * hw, cy + hh, szz * hw, -sxx * hw, cy + hh, -szz * hw,
                cr0, kind == 1 ? 64 : 0, cb, ca);
        float rr = m.ringR, ry = m.ringY;
        // (el círculo: más encendido con la grieta abierta)
        int ringA = (int) (Mth.clamp((0.35F + 0.5F * Math.min(1F, l.open)) * l.glow / 2F, 0F, 1F) * 255);
        quad(fx, mat, -rr, ry, -rr, rr, ry, -rr, rr, ry, rr, -rr, ry, rr, cr0, kind == 1 ? 255 : 191, 255, ringA);
    }

    private static void quad(VertexConsumer vc, Matrix4f mat, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2,
                             float z2, float x3, float y3, float z3, int r, int g, int b, int a) {
        vc.vertex(mat, x0, y0, z0).color(r, g, b, a).uv(0F, 0F).endVertex();
        vc.vertex(mat, x1, y1, z1).color(r, g, b, a).uv(1F, 0F).endVertex();
        vc.vertex(mat, x2, y2, z2).color(r, g, b, a).uv(1F, 1F).endVertex();
        vc.vertex(mat, x3, y3, z3).color(r, g, b, a).uv(0F, 1F).endVertex();
    }

    /** Los triángulos de una parte, con su transformación (x) y la luz de cada vértice. */
    private static void emit(VertexConsumer vc, Part p, Matrix4f x, Matrix4f mat, Matrix3f nmat, Vector3f cam, Look l, int light,
                             int kind, float tearCy) {
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
            light(pos, n, matId, cam, l.glow, col, kind, tearCy);
            int vein = (int) Mth.clamp(v[i + 9] * l.glow * 100F, 0F, 255F);
            vc.vertex(mat, pos.x, pos.y, pos.z).color(col[0], col[1], col[2], 1F).uv(v[i + 6], v[i + 7]).overlayCoords(matId + 4 * kind, vein)
                    .uv2(light).normal(nmat, n.x, n.y, n.z).endVertex();
        }
    }

    /** La luz de un vértice (la misma cuenta que grieta3.py light): base × (ambiente + difusa) + desgarro + borde. */
    static void light(Vector3f pos, Vector3f n, int mat, Vector3f cam, float glow, float[] out, int kind, float tearCy) {
        float[] base = mat == 0 || mat == 3 ? ROCK[kind] : mat == 1 ? CRYSTAL[kind] : SHARD[kind];
        float dif = Math.max(0F, n.dot(LIGHT));
        float k0 = 0.5F + 0.9F * dif;
        float tx = -pos.x, ty = tearCy - pos.y, tz = -pos.z;
        float d2 = tx * tx + ty * ty + tz * tz;
        float dl = (float) Math.sqrt(d2 + 1e-9F);
        float k = Math.max(0F, (n.x * tx + n.y * ty + n.z * tz) / dl) * glow * 80F / (80F + d2) * (mat == 0 || mat == 3 ? 0.55F : 0.9F);
        float vx = cam.x - pos.x, vy = cam.y - pos.y, vz = cam.z - pos.z;
        float vl = (float) Math.sqrt(vx * vx + vy * vy + vz * vz) + 1e-9F;
        float fr = 1F - Math.abs((n.x * vx + n.y * vy + n.z * vz) / vl);
        fr = fr * fr * fr * (mat == 0 || mat == 3 ? 0.35F : 0.7F);
        for (int c = 0; c < 3; c++) out[c] = Mth.clamp(base[c] * k0 + TEAR_LIGHT[kind][c] * k + RIM[kind][c] * fr, 0F, 1F);
    }

    /**
     * El cartel de encima: jefe, dificultad y estado (y, en la grieta del Void, su nombre; si el staff no le ha puesto
     * criaturas, lo dice).
     */
    private void label(RiftEntity e, SkillDefs.BossInfo bi, float height, PoseStack pose, MultiBufferSource buffers) {
        Minecraft mc = Minecraft.getInstance();
        double far = 48 * e.scale();
        if (mc.player == null || mc.player.distanceToSqr(e) > far * far) return;
        List<Component> lines = new ArrayList<>();
        int st = e.state();
        boolean mother = e.kind() == RiftEntity.MOTHER;
        if (e.fixed()) {
            lines.add(Component.literal(mother ? "✦ GRIETA MADRE ✦" : "✦ GRIETA DEL VOID ✦")
                    .withStyle(s -> s.withColor(TextColor.fromRgb(mother ? 0xFF4A7A : 0xC77DFF)).withBold(true)));
        }
        if (bi == null) {
            lines.add(Component.literal("Sin criaturas").withStyle(s -> s.withColor(TextColor.fromRgb(0xD8D8E0))));
            lines.add(Component.literal("El staff las pone en /tf boss → VOID").withStyle(s -> s.withColor(TextColor.fromRgb(0x9AA0AE))));
        } else {
            lines.add(Component.literal(bi.name.toUpperCase(java.util.Locale.ROOT)).withStyle(s -> s.withColor(TextColor.fromRgb(bi.color)).withBold(true)));
            lines.add(Component.literal(e.label()).withStyle(s -> s.withColor(TextColor.fromRgb(e.labelColor()))));
            String state = switch (st) {
                case RiftEntity.READY -> "Golpea el cristal para despertarlo";
                case RiftEntity.WAKING -> "¡Despierta!";
                case RiftEntity.FIGHT -> "Incursión en curso";
                default -> e.left() < 0 ? "Apagada: el staff encenderá la siguiente" : "Vuelve en " + Rifts.time(e.left());
            };
            lines.add(Component.literal(state).withStyle(s -> s.withColor(TextColor.fromRgb(st == RiftEntity.WAKING ? 0xFF6A6A : 0xD8D8E0))));
        }
        Font font = mc.font;
        pose.pushPose();
        // Encima del desgarro (la normal llega a ~21,5 bloques; la madre a ~32)
        pose.translate(0, height, 0);
        pose.mulPose(this.entityRenderDispatcher.cameraOrientation());
        float k = 0.05F * (mother ? 1.4F : 1F);
        pose.scale(-k, -k, k);
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
