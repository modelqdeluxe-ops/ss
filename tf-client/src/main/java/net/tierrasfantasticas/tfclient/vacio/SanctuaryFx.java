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
    /** Los Pozos de Luz: hasta dónde se dibujan, lo alta que es su columna y el radio de su burbuja. */
    private static final double WELL_FAR = 260;
    private static final float WELL_BEAM = 40F, WELL_BUBBLE = 4.2F;
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
                && VoidIsland.get() != null;
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
        boolean sanctuary = cam.distanceTo(center) <= FAR;
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
            Matrix4f inv = new Matrix4f(view).invert();
            if (sanctuary) sanctuary(view, proj, cam, center, radius, time, inv);
            // (1.3.96) los Pozos de Luz y el velo del Portal del Alba
            mv.setIdentity();
            mv.mulPoseMatrix(view);
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(new Matrix4f(proj), VertexSorting.DISTANCE_TO_ORIGIN);
            heart.safeGetUniform("STime").set(time);
            wells(view, proj, cam, time);
            altar(view, proj, cam, time);
            portal(cam);
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

    /** El Velo del Alba y el Corazón con su columna. */
    private static void sanctuary(Matrix4f view, Matrix4f proj, Vec3 cam, Vec3 center, float radius, float time, Matrix4f inv) {
        PoseStack mv = RenderSystem.getModelViewStack();
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
    }

    /**
     * Los Pozos de Luz (1.3.97, el dueño: «solo una especie de burbuja como la del santuario que brille»): una burbuja de
     * luz como el Velo, de radio {@link #WELL_BUBBLE} y con el centro en su suelo (la mitad de abajo la tapa el suelo), y
     * una columna fina que se ve de lejos para encontrarlos.
     */
    private static void wells(Matrix4f view, Matrix4f proj, Vec3 cam, float time) {
        if (sphere == null) sphere = buildSphere();
        for (net.minecraft.core.BlockPos b : VoidIsland.shards()) {
            Vec3 at = LightWells.bubbleCenter(b);
            Vec3 w = at.subtract(cam);
            double d2 = w.lengthSqr();
            if (d2 > WELL_FAR * WELL_FAR) continue;
            bubble(view, proj, w, WELL_BUBBLE, d2 < WELL_BUBBLE * WELL_BUBBLE, 0.9F, 0F, time);
            Vec3 flat = new Vec3(w.x, 0, w.z);
            Vec3 side = flat.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0).cross(flat).normalize();
            RenderSystem.setShader(() -> heart);
            heart.safeGetUniform("Violet").set(0F);
            heart.safeGetUniform("Mode").set(1F);
            heart.safeGetUniform("Alpha").set(0.45F);
            quad(w.add(0, WELL_BEAM / 2 + 1, 0), side.scale(0.4), new Vec3(0, WELL_BEAM / 2, 0));
        }
    }

    /**
     * El aura de la armadura del Vacío en su altar (1.3.97, el dueño: «ponle un efecto o algo alrededor»): una burbuja
     * violeta alrededor del soporte y un círculo de runas violeta en el suelo.
     */
    private static void altar(Matrix4f view, Matrix4f proj, Vec3 cam, float time) {
        net.minecraft.core.BlockPos a = VoidIsland.altarPos();
        if (a == null) return;
        // (1.3.115, el dueño: «esa burbuja le queda asimétrica en todo») centrada en el soporte de verdad (ahora de pie sobre
        // su altar, VoidArmor.pedestal), no en la altura guardada del altar; sin el círculo del suelo (quedaría debajo)
        Vec3 feet = new Vec3(a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
        net.minecraft.client.multiplayer.ClientLevel lvl = net.minecraft.client.Minecraft.getInstance().level;
        if (lvl != null) {
            double best = 9.0;
            for (net.minecraft.world.entity.decoration.ArmorStand st : lvl.getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class,
                    new net.minecraft.world.phys.AABB(a).inflate(3))) {
                double d = st.position().distanceToSqr(feet.x, st.getY(), feet.z);
                if (d < best) {
                    best = d;
                    feet = st.position();
                }
            }
        }
        Vec3 c = feet.add(0, 1.0, 0).subtract(cam);
        if (c.lengthSqr() > 96 * 96) return;
        if (sphere == null) sphere = buildSphere();
        bubble(view, proj, c, 1.55F, c.lengthSqr() < 1.55 * 1.55, 0.75F, 1F, time);
    }

    /** Una burbuja con el shader del Velo (Violet 1: la paleta violeta del Vacío en vez de la de oro). */
    private static void bubble(Matrix4f view, Matrix4f proj, Vec3 c, float radius, boolean inside, float alpha, float violet, float time) {
        veil.safeGetUniform("STime").set(time);
        veil.safeGetUniform("Center").set((float) c.x, (float) c.y, (float) c.z);
        veil.safeGetUniform("Radius").set(radius);
        veil.safeGetUniform("Ripple").set(0F, 1F, 0F, -1F);
        veil.safeGetUniform("Inside").set(inside ? 1F : 0F);
        veil.safeGetUniform("Ground").set(0F);
        veil.safeGetUniform("Alpha").set(alpha);
        veil.safeGetUniform("Violet").set(violet);
        sphere.bind();
        sphere.drawWithShader(new Matrix4f(view), new Matrix4f(proj), veil);
        VertexBuffer.unbind();
        veil.safeGetUniform("Violet").set(0F);
    }

    /** (1.3.96) El velo del Portal del Alba: un remolino dorado en el hueco del arco (5 × 8). */
    private static void portal(Vec3 cam) {
        net.minecraft.core.BlockPos p = VoidIsland.portalPos();
        if (p == null) return;
        Vec3 c = new Vec3(p.getX() + 0.5, p.getY() + 4, p.getZ() + 0.5).subtract(cam);
        if (c.lengthSqr() > 160 * 160) return;
        boolean alongX = VoidIsland.portalFacing() % 2 == 0;
        heart.safeGetUniform("Mode").set(3F);
        heart.safeGetUniform("Alpha").set(1F);
        quad(c, alongX ? new Vec3(2.5, 0, 0) : new Vec3(0, 0, 2.5), new Vec3(0, 4, 0));
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
