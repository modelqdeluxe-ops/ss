package net.tierrasfantasticas.tfclient.vacio;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.bosses.RiftLate;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/**
 * Lo que dibujamos con shaders propios (la grieta: rift_obsidian y rift_fx; el cielo del Void: void_sky) cuando hay un
 * paquete de shaders puesto (Oculus, el Iris de Forge).
 *
 * <p>1.3.89, en la prueba con Oculus 1.8 + Complementary: la grieta no se veía (ni la normal ni la madre) y el agujero
 * negro tampoco. Oculus, mientras dibuja el mundo, apaga la escritura de color y profundidad de todo shader que no es
 * suyo ({@code MixinShaderInstance.iris$lockDepthColorState}): lo que se dibuja con él no deja nada. Pero Oculus usa la
 * profundidad del framebuffer principal de Minecraft y, al acabar el mundo (su pasada final, al salir de
 * {@code LevelRenderer.renderLevel}), deja ahí la imagen final con la profundidad de la escena. Así que, con paquete,
 * la grieta se guarda ({@link RiftLate}) y se dibuja justo después de esa pasada (mixin
 * {@code GameRendererLateMixin}, antes de la mano y la interfaz): detrás de los bloques, los jefes y la mano, con la
 * misma cámara. En el Void, antes, nuestro cielo solo donde la escena dejó la profundidad en 1 (donde se ve el cielo
 * del paquete): el agujero negro vuelve, y la isla, la luz y la niebla las pone el paquete (su End,
 * {@link ShaderPacks}).
 *
 * <p>1.3.96 (el dueño, con captura y Photon v1.3b: «se ve horrible con shaders»: la grieta del cielo con barras blancas
 * en abanico): Photon pone mezclas por búfer ({@code blend.gbuffers_basic.colortex0 = ONE ONE_MINUS_SRC_ALPHA ...}) y
 * Oculus las aplica con {@code glBlendFuncSeparatei}, que la caché de {@code GlStateManager} no ve; al acabar, la caché
 * cree que la mezcla ya es la de Minecraft y no la vuelve a poner. Lo nuestro (color sin premultiplicar, mezcla
 * {@code SRC_ALPHA}) se sumaba entero: donde el alfa era pequeño, blanco. Ahora, antes de dibujar, {@link #syncBlend}
 * vuelve a poner la mezcla con OpenGL directo para que la caché diga la verdad.
 */
@OnlyIn(Dist.CLIENT)
public final class AfterShaders {
    private static boolean frame, failed;
    private static final Matrix4f VIEW = new Matrix4f(), PROJ = new Matrix4f();
    /** Para la prueba en el juego: fotogramas dibujados así. */
    static int frames;

    private AfterShaders() {}

    /**
     * Antes de dibujar el mundo (con la cámara ya girada): ¿hay paquete? y la cámara de este fotograma (siempre: también
     * la usa la distorsión de las grietas, {@link net.tierrasfantasticas.tfclient.bosses.RiftWarp}).
     */
    public static void begin(PoseStack pose) {
        VIEW.set(pose.last().pose());
        PROJ.set(RenderSystem.getProjectionMatrix());
        frame = !failed && ShaderPacks.inUse();
    }

    /** La rotación de la cámara de este fotograma (mundo relativo a la cámara → vista). */
    public static Matrix4f view() {
        return VIEW;
    }

    /** La proyección con la que se dibujó el mundo en este fotograma. */
    public static Matrix4f projection() {
        return PROJ;
    }

    /** ¿Lo nuestro se deja para después del paquete en este fotograma? */
    public static boolean active() {
        return frame;
    }

    /** ¿Es la pasada de sombras del paquete? (ahí la grieta no se dibuja: no tiene sombra) */
    public static boolean shadowPass() {
        return ShaderPacks.shadowPass();
    }

    /**
     * La mezcla de OpenGL igual que la de la caché de {@code GlStateManager} (ver arriba): sin mezcla, {@code SRC_ALPHA,
     * ONE_MINUS_SRC_ALPHA, ONE, ZERO} y suma. {@code glEnable}/{@code glDisable} y {@code glBlendFuncSeparate} sin índice
     * valen para todos los búferes de dibujo (también los que el paquete dejó con su mezcla propia).
     */
    static void syncBlend() {
        RenderSystem.disableBlend();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ZERO);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
        RenderSystem.disableBlend();
    }

    /** Después de la pasada final del paquete: el cielo del Void, el santuario, la grieta del cielo y las grietas. */
    public static void end(float partial) {
        if (!frame) return;
        frame = false;
        Minecraft mc = Minecraft.getInstance();
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.setIdentity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(PROJ), VertexSorting.DISTANCE_TO_ORIGIN);
        try {
            syncBlend();
            if (mc.level != null && TFVoid.isVoid(mc.level)) {
                VoidSky.renderAfterShaders(VIEW, PROJ, partial);
                // (1.3.96: el Velo del Alba y el Corazón del Alba, con la profundidad de la escena)
                SanctuaryFx.renderAfterShaders(VIEW, PROJ);
            }
            // (la grieta del cielo de los mundos Overworld, 1.3.93: detrás de todo, solo donde se ve el cielo)
            SkyRiftClient.renderAfterShaders(VIEW, PROJ);
            RiftLate.flush();
            frames++;
        } catch (Throwable t) {
            failed = true;
            TFClient.LOGGER.error("TF Void: no se pudo dibujar la grieta y el cielo después del paquete de shaders", t);
        } finally {
            RenderSystem.restoreProjectionMatrix();
            mv.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        }
    }
}
