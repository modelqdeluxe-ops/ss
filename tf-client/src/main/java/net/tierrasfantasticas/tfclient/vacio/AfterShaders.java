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

    /** Después de la pasada final del paquete: el cielo del Void y las grietas. */
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
            if (mc.level != null && TFVoid.isVoid(mc.level)) VoidSky.renderAfterShaders(VIEW, PROJ, partial);
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
