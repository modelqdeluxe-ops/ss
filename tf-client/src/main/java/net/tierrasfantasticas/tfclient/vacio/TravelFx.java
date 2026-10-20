package net.tierrasfantasticas.tfclient.vacio;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.bosses.RiftWarp;

/**
 * El viaje por el agujero de gusano (1.3.91; el dueño: «la animación de teletransporte, más bonita: una escena como si
 * viajara por un agujero de gusano con la realidad alterada, con imágenes del Overworld, el Nether y el End; y de
 * regreso lo mismo pero End, Nether y Overworld; algo súper épico»). Dura lo que el viaje en el servidor
 * ({@link VoidTravel#LEAVE_TICKS}, 11,5 s desde la 1.3.96; antes 7,5) y un poco más:
 * <ol>
 *   <li>0-1 s: el mundo se deforma en el centro de la pantalla y la cámara tiembla ({@link RiftWarp#travel}).</li>
 *   <li>1-2,2 s: la realidad se rasga: en el centro se abre un desgarro de borde vivo que llena la pantalla.</li>
 *   <li>El túnel, cada vez más rápido: a la ida el Overworld (cielo con nubes, campos, bosques, cerezos y ríos), la
 *   puerta morada del Nether (3,4 s), el Nether (magma con venas de lava), la puerta turquesa del End (4,9 s), el End
 *   (nebulosa violeta con corrientes claras y estrellas), la puerta del Void (6,3 s) y la antesala del Void (sus
 *   nebulosas con hebras de oro) con el agujero negro al fondo, que crece y al llegar se cruza; a la vuelta, antesala,
 *   End, Nether y Overworld. Al cruzar cada puerta, fogonazo de su color. (1.3.92, el dueño: «hazlo limpio, se ven
 *   cuadrados; quiero una textura completa, limpia, no fragmentos; y después del End una antesala al Void»: sin
 *   losetas de bloques ni cubos; ver el shader.)</li>
 *   <li>Al llegar (el servidor ya te ha llevado y el mundo nuevo ya se ha cargado: mientras se ve «Cargando el
 *   terreno...», se sigue viajando): la luz del fondo llena la pantalla y se va, y el mundo nuevo aparece ondulando
 *   hasta calmarse.</li>
 * </ol>
 * Lo dibuja el shader wormhole sobre toda la pantalla (encima de la interfaz y de la pantalla de carga al cambiar de
 * mundo) al acabar cada fotograma (desde la 1.3.96, cada tramo es el panorama de su dimensión: {@link TunnelPanoramas});
 * su sonido (una pista continua, {@link #sounds}) suena solo para ti. Vista previa sin juego: tools/void/tunel_preview.py
 * (mismos tiempos).
 *
 * <p>Una llegada sin viaje (el rescate al caer al vacío del Void) es solo la luz que se va.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT)
public final class TravelFx {
    /** El shader del túnel (lo registra {@link VoidClient}). */
    static ShaderInstance shader;
    /** Segundos desde que empieza: el desgarro se abre (de OPEN0 a OPEN1) y las tres puertas. = tunel_preview. */
    static final float OPEN0 = 1.0F, OPEN1 = 2.2F, GATE1 = 4.4F, GATE2 = 6.8F, GATE3 = 9.2F;
    private static final float[] GATES = {GATE1, GATE2, GATE3};
    /** Segundos desde que llegas: el túnel sigue, la luz llena la pantalla (EXIT1) y se va (EXIT2); el mundo se calma. */
    private static final float EXIT0 = 0.4F, EXIT1 = 1.5F, EXIT2 = 2.4F, CALM = 3.4F;
    /** Si no llega la llegada (se cortó algo), el túnel se acaba solo. */
    private static final float GIVE_UP = 25F;
    /** La llegada sin viaje: la luz se va en este tiempo. */
    private static final float FADE = 2.5F;

    private static boolean trip, back, fade, failed;
    private static long startNanos;
    private static float arrived = -1F, serverArrived = -1F;
    private static int tripId, sounds, tripFrames;
    /** Este fotograma: segundos desde que empezó, cuánto se abre el desgarro, camino recorrido y salida (0..2). */
    private static float now, open, travel, exit;
    private static String lastTrip = "ningún viaje";

    private TravelFx() {}

    static void start(int ph, int t) {
        Minecraft mc = Minecraft.getInstance();
        long nanos = System.nanoTime();
        switch (ph) {
            case VoidNet.FX_LEAVE, VoidNet.FX_LEAVE_BACK -> {
                trip = true;
                fade = false;
                back = ph == VoidNet.FX_LEAVE_BACK;
                startNanos = nanos;
                arrived = serverArrived = -1F;
                sounds = 0;
                tripFrames = 0;
                tripId++;
                // El diálogo se cierra para que se vea
                if (mc.screen instanceof KnightScreen) mc.setScreen(null);
            }
            case VoidNet.FX_ARRIVE -> {
                if (trip && arrived < 0F) {
                    arrived = serverArrived = (nanos - startNanos) / 1e9F;
                } else if (!trip) {
                    fade = true;
                    startNanos = nanos;
                }
            }
            default -> stop();
        }
    }

    private static void stop() {
        if (trip) {
            float s = (System.nanoTime() - startNanos) / 1e9F;
            lastTrip = String.format("%s: %d fotogramas en %.1f s (%.0f por segundo), llegada a los %.1f s, mundo cargado a los %.1f s",
                    back ? "vuelta" : "ida", tripFrames, s, tripFrames / Math.max(0.1F, s), serverArrived, arrived);
        }
        trip = false;
        fade = false;
        RiftWarp.travel(0F, 0F);
    }

    /** ¿Está viajando (o se está yendo la luz)? (para la prueba en el juego) */
    public static boolean active() {
        return trip || fade;
    }

    /** Segundos desde que empezó el viaje, o −1 si no hay viaje (la prueba saca capturas a sus tiempos). */
    public static float clock() {
        return trip ? (System.nanoTime() - startNanos) / 1e9F : -1F;
    }

    /** A los cuántos segundos del viaje llegó (−1: aún no). */
    public static float arrivedAt() {
        return trip ? arrived : -1F;
    }

    /** ¿Es la vuelta (End → Nether → Overworld)? */
    public static boolean back() {
        return back;
    }

    /** Cuenta los viajes (cada uno empieza sus capturas de cero). */
    public static int tripId() {
        return tripId;
    }

    /** Cómo fue el último viaje (fotogramas por segundo). */
    public static String lastTrip() {
        return lastTrip;
    }

    /** = tunel_preview.travel: el camino recorrido; la velocidad va de 1,4 a 4 entre 1 y 4,5 s. */
    static float travel(float t) {
        float a = 1F, b = 4.5F, s;
        if (t < a) {
            s = 0F;
        } else if (t <= b) {
            float y = (t - a) / (b - a);
            s = (b - a) * (y * y * y - y * y * y * y / 2F);
        } else {
            s = (b - a) / 2F + (t - b);
        }
        return 1.4F * t + 2.6F * s;
    }

    private static float smooth(float a, float b, float x) {
        float t = Mth.clamp((x - a) / (b - a), 0F, 1F);
        return t * t * (3 - 2 * t);
    }

    /** Antes de cada fotograma: dónde va el viaje, la deformación del mundo y los sonidos. */
    private static void update() {
        if (!trip && !fade) return;
        now = (System.nanoTime() - startNanos) / 1e9F;
        if (fade) {
            open = 1F;
            travel = travel(now + 8F);
            exit = 1F + now / FADE;
            if (now > FADE) stop();
            return;
        }
        if (arrived < 0F && now > GIVE_UP) arrived = now;
        // (con el mundo nuevo aún cargando, «Cargando el terreno...», se sigue viajando: la luz se abre cuando ya está; en
        // la prueba con shaders tardaba más que la salida y se veía la pantalla de carga)
        if (arrived >= 0F && loading() && now < GIVE_UP + 15F) arrived = now;
        open = smooth(OPEN0, OPEN1, now);
        travel = travel(now);
        float since = arrived < 0F ? -1F : now - arrived;
        exit = since < EXIT0 ? 0F : smooth(EXIT0, EXIT1, since) + smooth(EXIT1, EXIT2, since);
        // el mundo se deforma antes de rasgarse; al llegar, el nuevo aparece ondulando (y gira al revés) hasta calmarse
        float warp = smooth(0.1F, 1.3F, now) * (1F - smooth(OPEN1, OPEN1 + 0.3F, now));
        float calm = since < EXIT1 ? 0F : 0.9F * (1F - smooth(EXIT1 + 0.1F, CALM, since));
        if (calm > 0F) RiftWarp.travel(calm, -1.6F * calm);
        else RiftWarp.travel(warp, 2.2F * warp);
        sounds(since);
        if (since > CALM) stop();
    }

    /** ¿Aún se está cargando el mundo al que llegas? */
    private static boolean loading() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null || mc.player == null || mc.screen instanceof ReceivingLevelScreen || mc.screen instanceof ProgressScreen;
    }

    /**
     * (1.3.96) Una pista propia para todo el viaje (tools/void/sonidos_viaje.py, a estos mismos tiempos), sin golpes: el
     * dueño quiere «un sonido fino corrido». Antes sonaban varios de Minecraft uno tras otro (el portal, el cristal que se
     * rompe, una explosión, el ghast, el dragón...). Al llegar, la luz del Alba.
     */
    private static final SoundEvent S_IDA = sound("viaje.ida"), S_VUELTA = sound("viaje.vuelta"), S_LLEGADA = sound("viaje.llegada");

    private static SoundEvent sound(String id) {
        return SoundEvent.createVariableRangeEvent(new net.minecraft.resources.ResourceLocation(TFClient.MOD_ID, id));
    }

    private static void sounds(float since) {
        if (once(0, now >= 0F && now < 1.5F)) play(back ? S_VUELTA : S_IDA, 1F, 1F);
        if (once(1, since >= EXIT0)) play(S_LLEGADA, 1F, 0.9F);
    }

    private static boolean once(int bit, boolean when) {
        if (!when || (sounds & 1 << bit) != 0) return false;
        sounds |= 1 << bit;
        return true;
    }

    private static void play(SoundEvent s, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, volume));
    }

    /**
     * Al empezar el fotograma, el viaje; al acabarlo (encima de todo: el mundo, la interfaz y las pantallas), el túnel.
     * (Antes que las capturas de la prueba, que van después: BossTestClient.)
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRender(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            update();
            return;
        }
        if ((!trip && !fade) || exit >= 1.9F || open <= 0.001F || shader == null || failed) return;
        try {
            draw();
            if (trip) tripFrames++;
        } catch (Throwable t) {
            failed = true;
            TFClient.LOGGER.error("TF Void: no se pudo dibujar el agujero de gusano: se apaga", t);
        }
    }

    private static void draw() {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        main.bindWrite(true);
        ShaderInstance sh = shader;
        sh.safeGetUniform("WTime").set(now);
        sh.safeGetUniform("Travel").set(travel);
        sh.safeGetUniform("Gates").set(travel(GATE1), travel(GATE2), travel(GATE3));
        sh.safeGetUniform("Open").set(open);
        sh.safeGetUniform("Exit").set(exit);
        sh.safeGetUniform("Back").set(back ? 1F : 0F);
        sh.safeGetUniform("ScreenSize").set((float) main.width, (float) main.height);
        sh.safeGetUniform("HasOverworld").set(TunnelPanoramas.overworldReady() ? 1F : 0F);
        RenderSystem.setShader(() -> sh);
        RenderSystem.setShaderTexture(1, VoidSky.PANORAMA);
        // (1.3.96) las dimensiones de verdad: sus panoramas
        RenderSystem.setShaderTexture(2, TunnelPanoramas.overworld());
        RenderSystem.setShaderTexture(3, TunnelPanoramas.NETHER);
        RenderSystem.setShaderTexture(4, TunnelPanoramas.END);
        RenderSystem.setShaderTexture(5, TunnelPanoramas.VOID);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        b.vertex(-1, -1, 0).endVertex();
        b.vertex(1, -1, 0).endVertex();
        b.vertex(1, 1, 0).endVertex();
        b.vertex(-1, 1, 0).endVertex();
        BufferUploader.drawWithShader(b.end());
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }
}
