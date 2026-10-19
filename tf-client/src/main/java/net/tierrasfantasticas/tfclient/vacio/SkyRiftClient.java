package net.tierrasfantasticas.tfclient.vacio;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

/**
 * La grieta del cielo en el cliente (1.3.93, {@link SkyRift}): la dibuja (shader sky_rift, en un cuadro lejano que mira
 * a la cámara, en la dirección de la grieta), la hace sonar y temblar, y da a RiftWarp la distorsión leve de cuando la
 * miras. Sin paquete de shaders se dibuja justo después del cielo (los bloques, las nubes y todo lo demás quedan
 * delante); con paquete, después de su pasada final, solo donde se ve el cielo ({@link AfterShaders}).
 *
 * <ul>
 *   <li>Preludio (6 s): un retumbar hondo (el del warden al salir), latidos cada vez más rápidos, un lamento (el del
 *   guardián anciano) y un rugido lejano; la cámara tiembla un poco, cada vez más.</li>
 *   <li>Se rompe: las grietas corren por el cielo desde el centro (1,2 s, con cristal que se quiebra), fogonazo con
 *   trueno y explosión y un golpe de temblor, y el desgarro se abre al Void (hasta los 3,4 s).</li>
 *   <li>Abierta: las grietas laten y las recorren rayos; dentro, el Void.</li>
 *   <li>Se sella (cayó un jefe del Void): el desgarro se cierra en 2,5 s, con un fogonazo; queda la cicatriz.</li>
 *   <li>Se vuelve a abrir (otro sonido: el del Wither al nacer): la cicatriz se rompe otra vez.</li>
 *   <li>El final: se cierra del todo, gran fogonazo, y desaparece.</li>
 * </ul>
 * Vista previa del shader sin juego: tools/void/grieta_cielo_preview.py.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT)
public final class SkyRiftClient {
    /** El shader (lo registra VoidClient). */
    static ShaderInstance shader;
    private static int state = SkyRift.OFF, fx = -1;
    private static boolean here;
    private static Vec3 pos;
    private static long fxMs;
    private static int sounds;
    private static boolean failed;
    /** A qué distancia se dibuja el cuadro (con las esquinas, a menos de 100: dentro del plano lejano incluso con 2 chunks de distancia de renderizado, 128). */
    private static final double DRAW = 60;
    /** Para la prueba en el juego: fotogramas dibujados, animaciones recibidas y el temblor más fuerte. */
    static int frames;
    private static int fxCount;
    private static float maxShake;

    private SkyRiftClient() {}

    static void receive(VoidNet.Sky m) {
        state = m.state();
        here = m.here();
        pos = new Vec3(m.x(), m.y(), m.z());
        if (m.fx() != SkyRift.FX_SYNC) {
            fx = m.fx();
            fxMs = System.currentTimeMillis();
            sounds = 0;
            fxCount++;
        } else {
            fx = -1;
        }
    }

    private static float smooth(float a, float b, float x) {
        float t = Mth.clamp((x - a) / (b - a), 0F, 1F);
        return t * t * (3 - 2 * t);
    }

    /** Segundos desde el último cambio (o mucho, si no hay animación en marcha). */
    private static float since() {
        return fx < 0 ? 1e4F : (System.currentTimeMillis() - fxMs) / 1000F;
    }

    /** Cómo se ve ahora: grietas, abierto, fogonazo, opacidad; null si no se ve. */
    private static float[] look() {
        float t = since();
        switch (fx) {
            case SkyRift.FX_BURST -> {
                if (t < 6F) {
                    float flash = t < 1.2F ? 0.15F * smooth(0.6F, 1.2F, t) : (float) Math.exp(-(t - 1.2F) * 2.2F);
                    return new float[] {smooth(0F, 1.2F, t), smooth(1.2F, 3.4F, t), flash, 1F};
                }
            }
            case SkyRift.FX_REOPEN -> {
                if (t < 5F) {
                    float flash = t < 0.8F ? 0F : (float) Math.exp(-(t - 0.8F) * 2.5F) * 0.8F;
                    return new float[] {0.3F + 0.7F * smooth(0F, 0.8F, t), smooth(0.8F, 2.6F, t), flash, 1F};
                }
            }
            case SkyRift.FX_CLOSE -> {
                if (t < 4F) {
                    float flash = (float) Math.exp(-Math.abs(t - 2.3F) * 6F) * 0.8F;
                    return new float[] {1F - 0.7F * smooth(0.5F, 2.5F, t), 1F - smooth(0F, 2.2F, t), flash, 1F - 0.25F * smooth(2.3F, 3.5F, t)};
                }
            }
            case SkyRift.FX_END -> {
                if (t < 5F) {
                    float flash = (float) Math.exp(-Math.abs(t - 2.5F) * 3.5F) * 1.4F;
                    return new float[] {1F - smooth(0.5F, 3F, t), 1F - smooth(0F, 2.5F, t), flash, 1F - smooth(2.6F, 4.5F, t)};
                }
                return null;
            }
            default -> {
            }
        }
        return switch (state) {
            case SkyRift.OPEN -> new float[] {1F, 1F, 0F, 1F};
            case SkyRift.CLOSED -> new float[] {0.3F, 0F, 0F, 0.75F};
            default -> null;
        };
    }

    /** ¿Se ve aquí? (mundo de cielo de Overworld y con grieta) */
    private static boolean visibleHere() {
        Minecraft mc = Minecraft.getInstance();
        return here && pos != null && mc.level != null && !failed && shader != null
                && mc.level.effects().skyType() == net.minecraft.client.renderer.DimensionSpecialEffects.SkyType.NORMAL;
    }

    /** El temblor de la cámara (RiftWarp lo suma): leve en el preludio, un golpe al romperse. */
    public static float shake() {
        if (!here || fx < 0) return 0F;
        float t = since();
        float k = switch (fx) {
            case SkyRift.FX_PRELUDE -> t < 6.5F ? 0.06F + 0.3F * smooth(0F, 6F, t) : 0F;
            case SkyRift.FX_BURST -> t < 1.2F ? 0.36F : t < 5F ? 1.5F * (float) Math.exp(-(t - 1.2F) * 1.3F) : 0F;
            case SkyRift.FX_REOPEN -> t > 0.8F && t < 4F ? 1.0F * (float) Math.exp(-(t - 0.8F) * 1.5F) : 0F;
            case SkyRift.FX_CLOSE, SkyRift.FX_END -> t > 2F && t < 4.5F ? 0.6F * (float) Math.exp(-Math.abs(t - 2.4F) * 2F) : 0F;
            default -> 0F;
        };
        maxShake = Math.max(maxShake, k);
        return k;
    }

    /** Para la prueba en el juego (BossTestClient): la animación en marcha (cuál y cuántas van) y sus segundos. */
    public static int testFx() {
        return fx;
    }

    public static int testFxCount() {
        return fxCount;
    }

    public static float testSince() {
        return since();
    }

    private static boolean once(int bit, boolean when) {
        if (!when || (sounds & 1 << bit) != 0) return false;
        sounds |= 1 << bit;
        return true;
    }

    private static void play(SoundEvent s, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, volume));
    }

    /** Los sonidos de lo que está pasando (solo en los mundos que la ven). */
    private static void sounds() {
        if (!here || fx < 0) return;
        float t = since();
        switch (fx) {
            case SkyRift.FX_PRELUDE -> {
                if (once(0, t >= 0F)) play(SoundEvents.WARDEN_EMERGE, 0.55F, 0.9F);
                float[] beats = {0.3F, 1.6F, 2.7F, 3.6F, 4.3F, 4.9F, 5.4F, 5.8F};
                for (int i = 0; i < beats.length; i++) if (once(1 + i, t >= beats[i])) play(SoundEvents.WARDEN_HEARTBEAT, 0.6F + 0.03F * i, 1F);
                if (once(10, t >= 2.0F)) play(SoundEvents.ELDER_GUARDIAN_CURSE, 0.5F, 0.55F);
                if (once(11, t >= 4.6F)) play(SoundEvents.ENDER_DRAGON_GROWL, 0.5F, 0.5F);
            }
            case SkyRift.FX_BURST -> {
                if (once(0, t >= 0F)) {
                    play(SoundEvents.GLASS_BREAK, 0.5F, 1F);
                    play(SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.5F, 0.8F);
                }
                if (once(1, t >= 0.6F)) play(SoundEvents.GLASS_BREAK, 0.7F, 0.9F);
                if (once(2, t >= 1.2F)) {
                    play(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6F, 1F);
                    play(SoundEvents.GENERIC_EXPLODE, 0.5F, 0.8F);
                    play(SoundEvents.END_PORTAL_SPAWN, 0.6F, 1F);
                }
                if (once(3, t >= 2.0F)) play(SoundEvents.PORTAL_TRIGGER, 0.5F, 0.6F);
                if (once(4, t >= 3.0F)) play(SoundEvents.ENDER_DRAGON_GROWL, 0.6F, 0.6F);
            }
            case SkyRift.FX_REOPEN -> {
                if (once(0, t >= 0F)) play(SoundEvents.WITHER_SPAWN, 0.8F, 0.55F);
                if (once(1, t >= 0.8F)) {
                    play(SoundEvents.GLASS_BREAK, 0.6F, 1F);
                    play(SoundEvents.END_PORTAL_SPAWN, 0.8F, 0.9F);
                }
            }
            case SkyRift.FX_CLOSE -> {
                if (once(0, t >= 0F)) play(SoundEvents.BEACON_DEACTIVATE, 0.5F, 1F);
                if (once(1, t >= 2.3F)) {
                    play(SoundEvents.CONDUIT_DEACTIVATE, 0.7F, 1F);
                    play(SoundEvents.GLASS_BREAK, 1.2F, 0.5F);
                }
            }
            case SkyRift.FX_END -> {
                if (once(0, t >= 0F)) play(SoundEvents.BEACON_DEACTIVATE, 0.6F, 1F);
                if (once(1, t >= 2.5F)) {
                    play(SoundEvents.LIGHTNING_BOLT_THUNDER, 1.2F, 0.8F);
                    play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1F, 1F);
                }
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------------------------------- dibujo

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) sounds();
    }

    /** Al salir del servidor se olvida (el siguiente manda la suya). */
    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        state = SkyRift.OFF;
        fx = -1;
        here = false;
        pos = null;
    }

    /** Sin paquete de shaders: justo después del cielo (lo demás queda delante). */
    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        if (AfterShaders.active()) return;
        render(event.getPoseStack().last().pose(), event.getProjectionMatrix(), false);
    }

    /** Con paquete de shaders (AfterShaders): solo donde la escena dejó ver el cielo. */
    static void renderAfterShaders(Matrix4f view, Matrix4f proj) {
        render(view, proj, true);
    }

    /** El cuadro: centro, eje «derecha» y eje «arriba» (relativos a la cámara), o null. */
    private static Vec3[] quad() {
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 rel = pos.subtract(cam);
        double dist = Math.max(1, rel.length());
        Vec3 dir = rel.scale(1 / dist);
        // su tamaño, el de verdad (160 × 224 bloques), pero nunca menor que unos 10° de ancho (se ve desde lejos)
        double ang = Mth.clamp(80 / dist, 0.09, 0.62);
        double hw = DRAW * Math.tan(ang), hh = hw * 1.4;
        // de pie (su largo hacia arriba); mirándola desde debajo, hacia el norte
        double k = smooth(0.9F, 0.99F, (float) Math.abs(dir.y));
        Vec3 ref = new Vec3(0, 1 - k, -k).normalize();
        Vec3 right = dir.cross(ref);
        if (right.lengthSqr() < 1e-6) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = right.cross(dir).normalize();
        return new Vec3[] {dir.scale(DRAW), right.scale(hw), up.scale(hh)};
    }

    private static void render(Matrix4f view, Matrix4f proj, boolean atFar) {
        if (!visibleHere()) return;
        float[] lk = look();
        if (lk == null || lk[3] <= 0.001F) return;
        try {
            Vec3[] q = quad();
            Vec3 c = q[0], r = q[1], u = q[2];
            ShaderInstance sh = shader;
            sh.safeGetUniform("STime").set((System.currentTimeMillis() % 3_600_000L) / 1000F);
            sh.safeGetUniform("Crack").set(lk[0]);
            sh.safeGetUniform("Open").set(lk[1]);
            sh.safeGetUniform("Flash").set(lk[2]);
            sh.safeGetUniform("Alpha").set(lk[3]);
            sh.safeGetUniform("AtFar").set(atFar ? 1F : 0F);
            PoseStack mv = RenderSystem.getModelViewStack();
            mv.pushPose();
            mv.setIdentity();
            mv.mulPoseMatrix(view);
            RenderSystem.applyModelViewMatrix();
            RenderSystem.backupProjectionMatrix();
            RenderSystem.setProjectionMatrix(new Matrix4f(proj), VertexSorting.DISTANCE_TO_ORIGIN);
            RenderSystem.setShader(() -> sh);
            RenderSystem.setShaderTexture(0, VoidSky.PANORAMA);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                    com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            if (atFar) {
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
            } else {
                RenderSystem.disableDepthTest();
            }
            BufferBuilder b = Tesselator.getInstance().getBuilder();
            b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            vertex(b, c, r, u, -1, -1, 0, 0);
            vertex(b, c, r, u, 1, -1, 1, 0);
            vertex(b, c, r, u, 1, 1, 1, 1);
            vertex(b, c, r, u, -1, 1, 0, 1);
            BufferUploader.drawWithShader(b.end());
            RenderSystem.restoreProjectionMatrix();
            mv.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            frames++;
        } catch (Throwable t) {
            failed = true;
            TFClient.LOGGER.error("TF Void: no se pudo dibujar la grieta del cielo: se apaga", t);
        }
    }

    private static void vertex(BufferBuilder b, Vec3 c, Vec3 r, Vec3 u, float sx, float sy, float tu, float tv) {
        b.vertex(c.x + r.x * sx + u.x * sy, c.y + r.y * sx + u.y * sy, c.z + r.z * sx + u.z * sy).uv(tu, tv).endVertex();
    }

    /**
     * La distorsión leve de cuando la miras (RiftWarp): su centro en la pantalla (uv), su radio (alto de la pantalla
     * = 1, en esas unidades) y la fuerza (0..1, más al romperse); null si no se ve.
     */
    public static float[] warp(Matrix4f view, Matrix4f proj, float aspect) {
        if (!visibleHere()) return null;
        float[] lk = look();
        if (lk == null || lk[3] <= 0.01F || lk[1] <= 0.01F && lk[2] <= 0.01F) return null;
        Vec3[] q = quad();
        float[] s0 = project(view, proj, q[0]);
        float[] s1 = project(view, proj, q[0].add(q[1].scale(0.6)));
        if (s0 == null || s1 == null) return null;
        if (s0[0] < -0.2F || s0[0] > 1.2F || s0[1] < -0.2F || s0[1] > 1.2F) return null;
        float rad = (float) Math.hypot((s1[0] - s0[0]) * aspect, s1[1] - s0[1]);
        return new float[] {s0[0], s0[1], Mth.clamp(rad, 0.05F, 0.3F), lk[1] * lk[3], lk[2]};
    }

    private static float[] project(Matrix4f view, Matrix4f proj, Vec3 rel) {
        Vector4f v = new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1F);
        view.transform(v);
        proj.transform(v);
        if (v.w <= 0.05F) return null;
        return new float[] {v.x / v.w * 0.5F + 0.5F, v.y / v.w * 0.5F + 0.5F};
    }

    /** Para la prueba en el juego: lo que ve el cliente. */
    public static String describe() {
        String[] names = {"no ha salido", "abierta", "sellada", "cerrada para siempre"};
        return names[Math.max(0, Math.min(3, state))] + (here ? ", se ve en este mundo" : ", no se ve en este mundo")
                + (pos == null ? "" : String.format(" (en %.0f %.0f %.0f)", pos.x, pos.y, pos.z)) + ", " + frames
                + " fotogramas dibujada, " + fxCount + " animaciones, temblor máximo " + String.format("%.2f", maxShake)
                + (failed ? ", FALLÓ AL DIBUJARSE" : "") + (shader == null ? ", SIN SHADER" : "");
    }
}
