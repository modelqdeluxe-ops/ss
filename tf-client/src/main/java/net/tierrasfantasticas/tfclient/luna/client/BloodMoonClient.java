/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.luna.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * Lo que se ve y se oye de la Luna de Sangre (el servidor manda si aquí hay luna: {@code luna.BloodMoon.State}).
 * <ul>
 *   <li>El cielo, las nubes y la niebla se tiñen de rojo ({@code mixin.luna.ClientLevelLunaMixin} y la niebla aquí);
 *       la luna de Minecraft pasa a la roja ({@code mixin.luna.LevelRendererLunaMixin}) y encima va una luna roja
 *       grande con su resplandor, donde está la de verdad (con paquete de shaders, después de su pasada final y solo
 *       donde se ve el cielo, {@code vacio.AfterShaders}).</li>
 *   <li>Una viñeta roja que late en los bordes de la pantalla, ceniza roja que flota y la música en «Discos», baja
 *       ({@code luna_sangre.musica}, en bucle; la de Minecraft se calla).</li>
 *   <li>Los ojos rojos de los mobs ({@link BloodEyesLayer}): se encienden poco a poco al acabar la escena.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT)
public final class BloodMoonClient {
    static final ResourceLocation MOON = tf("textures/environment/luna_sangre.png");
    static final ResourceLocation HALO = tf("textures/environment/luna_sangre_halo.png");
    public static final ResourceLocation PHASES = tf("textures/environment/luna_sangre_fases.png");
    static final ResourceLocation VIGNETTE = tf("textures/gui/luna_vineta.png");
    static final SoundEvent MUSIC = SoundEvent.createVariableRangeEvent(tf("luna_sangre.musica"));
    static final SoundEvent SCENE = SoundEvent.createVariableRangeEvent(tf("luna_sangre.escena"));
    /** Lo baja que va la música (el dueño: «una musiquita baja… tenue»). */
    static final float MUSIC_VOLUME = 0.42F;

    private static boolean active;
    private static int nightsLeft = -1;
    private static float sky, skyO, eyes, eyesO;
    private static int age;
    private static Music music;
    /** Para la prueba en el juego. */
    public static int moonFrames, moonLate, vignetteFrames;

    private BloodMoonClient() {}

    static ResourceLocation tf(String path) {
        return new ResourceLocation(TFClient.MOD_ID, path);
    }

    /** Del servidor: si aquí hay luna, si toca la escena, cuántas noches faltan y cuánto lleva. */
    public static void state(boolean on, boolean scene, int nights, int ageTicks) {
        nightsLeft = nights;
        if (on && !active) age = ageTicks;
        active = on;
        if (on && scene) BloodMoonScene.open();
        if (!on) BloodMoonScene.close();
    }

    public static boolean active() {
        return active;
    }

    public static int nightsLeft() {
        return nightsLeft;
    }

    /** Lo rojo que está el cielo (0..1). */
    public static float sky(float partial) {
        return Mth.lerp(partial, skyO, sky);
    }

    /** Lo encendidos que están los ojos de los mobs del mundo (0..1). */
    public static float eyes(float partial) {
        return Mth.lerp(partial, eyesO, eyes);
    }

    public static boolean musicActive() {
        return music != null && !music.isStopped();
    }

    public static boolean musicPlaying() {
        return music != null && !music.isStopped() && Minecraft.getInstance().getSoundManager().isActive(music);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        active = false;
        sky = skyO = eyes = eyesO = 0;
        BloodMoonScene.close();
        if (music != null) music.leaving = true;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        skyO = sky;
        eyesO = eyes;
        boolean scene = BloodMoonScene.running();
        float skyWant = !active ? 0F : scene ? BloodMoonScene.skyAmount() : 1F;
        sky = scene ? skyWant : approach(sky, skyWant, 1F / 60F);
        float eyesWant = active && !scene ? 1F : 0F;
        eyes = approach(eyes, eyesWant, eyesWant > eyes ? 1F / 50F : 1F / 30F);
        if (active) age++;
        if (mc.level == null || mc.isPaused()) return;
        music = keep(mc, music, active && !scene);
        // ceniza roja que flota alrededor
        if (sky > 0.4F && mc.player != null && mc.options.particles().get() != net.minecraft.client.ParticleStatus.MINIMAL) {
            ClientLevel level = mc.level;
            for (int i = 0; i < 2; i++) {
                double x = mc.player.getX() + (level.random.nextDouble() - 0.5) * 24;
                double y = mc.player.getY() + level.random.nextDouble() * 10 - 2;
                double z = mc.player.getZ() + (level.random.nextDouble() - 0.5) * 24;
                level.addParticle(ParticleTypes.CRIMSON_SPORE, x, y, z, 0, 0, 0);
            }
        }
    }

    private static float approach(float v, float to, float step) {
        return v < to ? Math.min(to, v + step) : Math.max(to, v - step);
    }

    // ------------------------------------------------------------------------------------------------- cielo

    /** El color del cielo (lo llama el mixin de ClientLevel). */
    public static Vec3 tintSky(Vec3 c, float partial) {
        float k = sky(partial) * 0.85F;
        if (k <= 0.001F) return c;
        return new Vec3(Mth.lerp(k, c.x, 0.40), Mth.lerp(k, c.y, 0.025), Mth.lerp(k, c.z, 0.035));
    }

    /** El color de las nubes. */
    public static Vec3 tintClouds(Vec3 c, float partial) {
        float k = sky(partial) * 0.7F;
        if (k <= 0.001F) return c;
        return new Vec3(Mth.lerp(k, c.x, 0.42), Mth.lerp(k, c.y, 0.10), Mth.lerp(k, c.z, 0.10));
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float k = sky((float) event.getPartialTick()) * 0.75F;
        if (k <= 0.001F || event.getCamera().getFluidInCamera() != FogType.NONE) return;
        event.setRed(Mth.lerp(k, event.getRed(), 0.30F));
        event.setGreen(Mth.lerp(k, event.getGreen(), 0.02F));
        event.setBlue(Mth.lerp(k, event.getBlue(), 0.03F));
    }

    /**
     * Hacia dónde está la luna (el «momento del día» de Minecraft): la de verdad, o durante la escena la que va naciendo
     * por el este.
     */
    static float moonTimeOfDay(float partial) {
        Minecraft mc = Minecraft.getInstance();
        float real = mc.level == null ? 0.26F : mc.level.getTimeOfDay(partial);
        return BloodMoonScene.running() ? BloodMoonScene.moonTimeOfDay(real, partial) : real;
    }

    /** Sin paquete de shaders: justo después del cielo (los bloques van delante). */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY || net.tierrasfantasticas.tfclient.vacio.AfterShaders.active()) return;
        if (drawMoon(event.getPoseStack().last().pose(), event.getPartialTick(), false)) moonFrames++;
    }

    /** Con paquete de shaders: después de su pasada final, solo donde se ve el cielo (profundidad 1). */
    public static void renderAfterShaders(Matrix4f view, float partial) {
        if (drawMoon(view, partial, true)) {
            moonFrames++;
            moonLate++;
        }
    }

    private static boolean drawMoon(Matrix4f view, float partial, boolean late) {
        float k = sky(partial);
        Minecraft mc = Minecraft.getInstance();
        if (k <= 0.01F || mc.level == null) return false;
        float rain = 1F - mc.level.getRainLevel(partial) * 0.6F;
        PoseStack pose = new PoseStack();
        pose.mulPoseMatrix(view);
        pose.mulPose(Axis.YP.rotationDegrees(-90F));
        pose.mulPose(Axis.XP.rotationDegrees(moonTimeOfDay(partial) * 360F));
        Matrix4f m = pose.last().pose();
        float pulse = 0.9F + 0.1F * Mth.sin((age + partial) * 0.05F);
        try {
            if (late) {
                // todo a profundidad 1: solo pasa donde la escena no dejó nada (el cielo)
                GL11.glDepthRange(1.0, 1.0);
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
            } else {
                RenderSystem.disableDepthTest();
            }
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            // el resplandor (suma luz)
            RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
            quad(m, HALO, 48F, k * rain * 0.85F * pulse);
            // la luna (tapa lo de detrás)
            RenderSystem.defaultBlendFunc();
            quad(m, MOON, 13F, Math.min(1F, k * 1.4F) * rain);
        } finally {
            if (late) GL11.glDepthRange(0.0, 1.0);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        }
        return true;
    }

    private static void quad(Matrix4f m, ResourceLocation tex, float s, float alpha) {
        if (alpha <= 0.002F) return;
        RenderSystem.setShaderTexture(0, tex);
        int a = Mth.clamp((int) (alpha * 255), 0, 255);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        b.vertex(m, -s, -100F, s).uv(0F, 0F).color(255, 255, 255, a).endVertex();
        b.vertex(m, s, -100F, s).uv(1F, 0F).color(255, 255, 255, a).endVertex();
        b.vertex(m, s, -100F, -s).uv(1F, 1F).color(255, 255, 255, a).endVertex();
        b.vertex(m, -s, -100F, -s).uv(0F, 1F).color(255, 255, 255, a).endVertex();
        BufferUploader.drawWithShader(b.end());
    }

    // ------------------------------------------------------------------------------------------------- pantalla

    /** La viñeta roja que late (debajo de la interfaz). */
    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Pre event) {
        float k = sky(event.getPartialTick());
        Minecraft mc = Minecraft.getInstance();
        if (k <= 0.01F || mc.options.hideGui || BloodMoonScene.running()) return;
        float pulse = 0.82F + 0.18F * Mth.sin((age + event.getPartialTick()) * 0.06F);
        int w = event.getWindow().getGuiScaledWidth(), h = event.getWindow().getGuiScaledHeight();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1F, 1F, 1F, Mth.clamp(k * 0.6F * pulse, 0F, 1F));
        event.getGuiGraphics().blit(VIGNETTE, 0, 0, 0, 0F, 0F, w, h, w, h);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.disableBlend();
        vignetteFrames++;
    }

    // ------------------------------------------------------------------------------------------------- música

    private static Music keep(Minecraft mc, Music m, boolean want) {
        if (m != null && !m.isStopped() && m.age > 40 && !mc.getSoundManager().isActive(m)) m = null;
        if (m != null && !m.isStopped()) m.age++;
        if (want) {
            if (m == null || m.isStopped()) {
                m = new Music();
                mc.getSoundManager().play(m);
                mc.getMusicManager().stopPlaying();
            } else {
                m.leaving = false;
            }
        } else if (m != null && !m.isStopped()) {
            m.leaving = true;
        }
        return m;
    }

    /** La música de la noche: en bucle, sin posición, entra en 5 s y se va en 3. */
    static final class Music extends AbstractTickableSoundInstance {
        private static final int FADE_IN = 100, FADE_OUT = 60;
        boolean leaving;
        int age;
        private float fade;

        Music() {
            super(MUSIC, SoundSource.RECORDS, SoundInstance.createUnseededRandom());
            this.looping = true;
            this.delay = 0;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.volume = 0.001F;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            fade = leaving ? fade - 1F / FADE_OUT : Math.min(1F, fade + 1F / FADE_IN);
            if (fade <= 0F) {
                stop();
                return;
            }
            this.volume = Math.max(0.001F, fade * fade * MUSIC_VOLUME);
        }
    }

    /** Para los ojos: si esta entidad está en la escena, su luz; si no, la del mundo. */
    public static float eyesOf(Entity e, float partial) {
        float scene = BloodMoonScene.eyesOf(e);
        return scene >= 0F ? scene : eyes(partial);
    }
}
