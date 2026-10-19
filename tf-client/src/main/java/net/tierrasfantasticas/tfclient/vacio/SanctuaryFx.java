package net.tierrasfantasticas.tfclient.vacio;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * El santuario del Void, en el cliente (1.3.96; el dueño: «una isla santuario con el caballero sacro, encerrada en una
 * especie de burbuja brillante que contiene la luz y nos mantiene a salvo de esa corrosión»):
 *
 * <ul>
 *   <li>El Velo del Alba: la burbuja ({@link VoidIsland#bubbleCenter()}, radio {@link VoidIsland#bubbleRadius()}), con
 *   el shader {@code velo_alba} (película tornasolada, auroras, chispas, borde que brilla de lado, cinturón de oro en el
 *   suelo). Al cruzarla, una onda se abre desde ese punto.</li>
 *   <li>El Corazón del Alba ({@link VoidIsland#heartPos()}), sobre el templo: late (shader {@code corazon_alba}) y suelta
 *   una columna de luz al cielo para encontrar el santuario desde la isla grande. Motas de luz alrededor de él y dentro
 *   del Velo.</li>
 * </ul>
 * Sin paquete de shaders se dibuja después de las partículas; con paquete, después de su pasada final ({@link AfterShaders}),
 * con la profundidad de la escena: la isla lo tapa igual. Aditivo y sin escribir profundidad.
 * Vista previa sin el juego: tools/void/santuario_preview.py.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT)
public final class SanctuaryFx {
    static ShaderInstance veil, heart;
    /** La malla de la esfera (radio 1; el shader la pone en su sitio): 96 × 48. */
    private static final int SEG_LON = 96, SEG_LAT = 48;
    private static final float HEART_HALF = 5F, BEAM_W = 2.4F, BEAM_H = 160F;
    /** Más lejos que esto, no se dibuja. */
    private static final double FAR = 420;
    private static VertexBuffer sphere;
    private static boolean failed, wasInside, knowInside;
    private static Vec3 rippleDir = new Vec3(0, 1, 0);
    private static long rippleAt = -1;
    /** Para la prueba en el juego: fotogramas dibujados. */
    static int frames;

    private SanctuaryFx() {}

    private static boolean here(Minecraft mc) {
        return !failed && veil != null && heart != null && mc.level != null && TFVoid.isVoid(mc.level)
                && VoidIsland.bubbleRadius() > 0;
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || AfterShaders.active()) return;
        render(event.getPoseStack().last().pose(), event.getProjectionMatrix(), event.getCamera().getPosition());
    }

    /** Con paquete de shaders (AfterShaders, con su cámara). */
    static void renderAfterShaders(Matrix4f view, Matrix4f proj) {
        render(view, proj, Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
    }

    private static void render(Matrix4f view, Matrix4f proj, Vec3 cam) {
        Minecraft mc = Minecraft.getInstance();
        if (!here(mc)) return;
        Vec3 center = VoidIsland.bubbleCenter();
        float radius = VoidIsland.bubbleRadius();
        if (cam.distanceTo(center) > FAR) return;
        PoseStack mv = RenderSystem.getModelViewStack();
        try {
            RenderSystem.backupProjectionMatrix();
            mv.pushPose();
            mv.setIdentity();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.enableBlend();
            RenderSystem.depthMask(false);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.disableCull();
            float time = (System.currentTimeMillis() % 3_600_000L) / 1000F;
            // el Velo
            if (sphere == null) sphere = buildSphere();
            Vec3 c = center.subtract(cam);
            veil.safeGetUniform("STime").set(time);
            veil.safeGetUniform("Center").set((float) c.x, (float) c.y, (float) c.z);
            veil.safeGetUniform("Radius").set(radius);
            float since = rippleAt < 0 ? -1F : (System.currentTimeMillis() - rippleAt) / 1000F;
            if (since > 4F) since = -1F;
            veil.safeGetUniform("Ripple").set((float) rippleDir.x, (float) rippleDir.y, (float) rippleDir.z, since);
            veil.safeGetUniform("Inside").set(cam.distanceTo(center) < radius ? 1F : 0F);
            veil.safeGetUniform("Ground").set((float) ((VoidIsland.arrivePos().y - center.y) / radius));
            veil.safeGetUniform("Alpha").set(1F);
            sphere.bind();
            sphere.drawWithShader(new Matrix4f(view), new Matrix4f(proj), veil);
            VertexBuffer.unbind();
            // el Corazón (mira a la cámara) y su columna (gira solo alrededor de la vertical)
            mv.mulPoseMatrix(view);
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(new Matrix4f(proj), VertexSorting.DISTANCE_TO_ORIGIN);
            Vec3 h = VoidIsland.heartPos().subtract(cam);
            Matrix4f inv = new Matrix4f(view).invert();
            Vector3f r = inv.transformDirection(new Vector3f(1, 0, 0)).mul(HEART_HALF);
            Vector3f u = inv.transformDirection(new Vector3f(0, 1, 0)).mul(HEART_HALF);
            heart.safeGetUniform("STime").set(time);
            heart.safeGetUniform("Alpha").set(1F);
            heart.safeGetUniform("Mode").set(0F);
            quad(h, new Vec3(r.x(), r.y(), r.z()), new Vec3(u.x(), u.y(), u.z()));
            Vec3 flat = new Vec3(h.x, 0, h.z);
            Vec3 side = flat.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0).cross(flat).normalize();
            heart.safeGetUniform("Mode").set(1F);
            quad(h.add(0, BEAM_H / 2, 0), side.scale(BEAM_W), new Vec3(0, BEAM_H / 2, 0));
            frames++;
        } catch (Throwable t) {
            failed = true;
            TFClient.LOGGER.error("TF Void: no se pudo dibujar el Velo del Alba: se apaga", t);
        } finally {
            mv.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
    }

    private static void quad(Vec3 c, Vec3 r, Vec3 u) {
        RenderSystem.setShader(() -> heart);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.vertex(c.x - r.x - u.x, c.y - r.y - u.y, c.z - r.z - u.z).uv(0, 0).endVertex();
        b.vertex(c.x + r.x - u.x, c.y + r.y - u.y, c.z + r.z - u.z).uv(1, 0).endVertex();
        b.vertex(c.x + r.x + u.x, c.y + r.y + u.y, c.z + r.z + u.z).uv(1, 1).endVertex();
        b.vertex(c.x - r.x + u.x, c.y - r.y + u.y, c.z - r.z + u.z).uv(0, 1).endVertex();
        BufferUploader.drawWithShader(b.end());
    }

    /** La esfera de radio 1 (= santuario_preview.sphere). */
    private static VertexBuffer buildSphere() {
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        for (int j = 0; j < SEG_LAT; j++) {
            double t0 = Math.PI * j / SEG_LAT, t1 = Math.PI * (j + 1) / SEG_LAT;
            for (int i = 0; i < SEG_LON; i++) {
                double p0 = 2 * Math.PI * i / SEG_LON, p1 = 2 * Math.PI * (i + 1) / SEG_LON;
                point(b, t0, p0);
                point(b, t0, p1);
                point(b, t1, p1);
                point(b, t1, p0);
            }
        }
        VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vb.bind();
        vb.upload(b.end());
        VertexBuffer.unbind();
        return vb;
    }

    private static void point(BufferBuilder b, double t, double p) {
        b.vertex(Math.sin(t) * Math.cos(p), Math.cos(t), Math.sin(t) * Math.sin(p)).endVertex();
    }

    /** La onda al cruzar el Velo y las motas de luz (alrededor del Corazón y dentro del Velo, cerca de ti). */
    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !TFVoid.isVoid(mc.level) || VoidIsland.bubbleRadius() <= 0 || mc.isPaused()) {
            knowInside = false;
            return;
        }
        Vec3 center = VoidIsland.bubbleCenter();
        Vec3 p = mc.player.position().add(0, 1, 0);
        boolean inside = VoidIsland.inBubble(p.x, p.y, p.z);
        if (knowInside && inside != wasInside) {
            Vec3 d = p.subtract(center);
            if (d.lengthSqr() > 1e-4) rippleDir = d.normalize();
            rippleAt = System.currentTimeMillis();
        }
        wasInside = inside;
        knowInside = true;
        RandomSource rnd = mc.level.random;
        long tick = mc.level.getGameTime();
        Vec3 hp = VoidIsland.heartPos();
        if (tick % 3 == 0 && p.distanceToSqr(hp) < 96 * 96) {
            Vec3 dir = new Vec3(rnd.nextGaussian(), rnd.nextGaussian(), rnd.nextGaussian()).normalize();
            Vec3 at = hp.add(dir.scale(1.2));
            mc.level.addParticle(ParticleTypes.END_ROD, at.x, at.y, at.z, dir.x * 0.04, dir.y * 0.04, dir.z * 0.04);
        }
        if (inside && tick % 2 == 0) {
            float radius = VoidIsland.bubbleRadius();
            for (int k = 0; k < 4; k++) {
                Vec3 at = p.add((rnd.nextDouble() - 0.5) * 24, (rnd.nextDouble() - 0.3) * 10, (rnd.nextDouble() - 0.5) * 24);
                if (at.distanceTo(center) > radius - 1 || !mc.level.getBlockState(BlockPos.containing(at)).isAir()) continue;
                mc.level.addParticle(ParticleTypes.END_ROD, at.x, at.y, at.z, 0, 0.008 + rnd.nextDouble() * 0.01, 0);
                break;
            }
        }
    }
}
