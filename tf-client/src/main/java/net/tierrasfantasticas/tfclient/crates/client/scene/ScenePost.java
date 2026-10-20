package net.tierrasfantasticas.tfclient.crates.client.scene;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.crates.FSCrates;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * El «shader» de la cinemática de las crates (1.3.99; el dueño: «mejora el shader, pon un shader bonito de la escena,
 * el resplandor más bello»). La escena no se dibuja directamente en la pantalla sino en una textura propia; luego:
 * <ol>
 *   <li>el cielo del atardecer (crate_sky) al fondo, antes del 3D;</li>
 *   <li>el resplandor (crate_bloom): se baja a un cuarto, se queda lo que brilla y se desenfoca dos veces (estrecho y
 *       ancho);</li>
 *   <li>la imagen final (crate_post) en la pantalla: escena + resplandor + brillo soñador + rayos de sol que salen del
 *       sol + colores del atardecer + viñeta.</li>
 * </ol>
 * Si los shaders no cargan, la cinemática dibuja como antes, directa a la pantalla.
 */
public final class ScenePost {
    static ShaderInstance sky, bloom, post;
    private TextureTarget scene, a, b;

    public static boolean ready() {
        return sky != null && bloom != null && post != null;
    }

    /** Prepara las texturas para el tamaño de la ventana (las rehace si cambia). */
    public void ensure(int w, int h) {
        if (scene != null && scene.width == w && scene.height == h) return;
        close();
        scene = new TextureTarget(w, h, true, Minecraft.ON_OSX);
        a = new TextureTarget(Math.max(1, w / 4), Math.max(1, h / 4), false, Minecraft.ON_OSX);
        b = new TextureTarget(Math.max(1, w / 4), Math.max(1, h / 4), false, Minecraft.ON_OSX);
        for (RenderTarget t : new RenderTarget[] {scene, a, b}) {
            t.setFilterMode(GL11.GL_LINEAR);
            t.setClearColor(0, 0, 0, 1);
        }
    }

    /** Empieza a dibujar la escena en su textura (borrada). */
    public void begin() {
        scene.bindWrite(true);
        RenderSystem.clearColor(0, 0, 0, 1);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    /** Vuelve a la textura de la escena (por si algo dibujó en otra). */
    public void rebind() {
        scene.bindWrite(true);
    }

    /** El cielo del atardecer de fondo (sin profundidad): ejes de la cámara, tangente del medio campo y el sol. */
    public void sky(Vector3f right, Vector3f up, Vector3f fwd, float tanX, float tanY, Vector3f sun, float time) {
        ShaderInstance sh = sky;
        sh.safeGetUniform("CamRight").set(right.x, right.y, right.z);
        sh.safeGetUniform("CamUp").set(up.x, up.y, up.z);
        sh.safeGetUniform("CamFwd").set(fwd.x, fwd.y, fwd.z);
        sh.safeGetUniform("TanHalf").set(tanX, tanY);
        sh.safeGetUniform("SunDir").set(sun.x, sun.y, sun.z);
        sh.safeGetUniform("Time").set(time);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        quad(sh);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    /**
     * El resplandor y la imagen final en la pantalla. {@code sunU, sunV}: el sol en la imagen (0..1, desde abajo a la
     * izquierda; puede estar fuera); {@code sunVis}: cuánto se ven sus rayos.
     */
    public void finish(float sunU, float sunV, float sunVis, float bloomStrength, float rayStrength, float exposure) {
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        float tw = 1.0F / a.width, th = 1.0F / a.height;
        // 1) se baja a un cuarto y se queda lo que brilla
        pass(scene, a, 0, 1.0F / scene.width, 1.0F / scene.height, 0, 0);
        // 2) desenfoque estrecho y otro ancho
        pass(a, b, 1, 0, 0, tw * 1.5F, 0);
        pass(b, a, 1, 0, 0, 0, th * 1.5F);
        pass(a, b, 1, 0, 0, tw * 3.5F, 0);
        pass(b, a, 1, 0, 0, 0, th * 3.5F);
        // 3) la imagen final, en la pantalla
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        ShaderInstance sh = post;
        sh.setSampler("SceneSampler", scene.getColorTextureId());
        sh.setSampler("BloomSampler", a.getColorTextureId());
        sh.safeGetUniform("SunPos").set(sunU, sunV);
        sh.safeGetUniform("SunVis").set(sunVis);
        sh.safeGetUniform("BloomStrength").set(bloomStrength);
        sh.safeGetUniform("RayStrength").set(rayStrength);
        sh.safeGetUniform("Exposure").set(exposure);
        quad(sh);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private void pass(RenderTarget in, RenderTarget out, int mode, float inTexelX, float inTexelY, float dirX, float dirY) {
        out.bindWrite(true);
        ShaderInstance sh = bloom;
        sh.setSampler("InSampler", in.getColorTextureId());
        sh.safeGetUniform("InTexel").set(inTexelX, inTexelY);
        sh.safeGetUniform("Dir").set(dirX, dirY);
        sh.safeGetUniform("Mode").set((float)mode);
        sh.safeGetUniform("Threshold").set(0.70F);
        quad(sh);
    }

    private static void quad(ShaderInstance sh) {
        RenderSystem.setShader(() -> sh);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        bb.vertex(-1, -1, 0).endVertex();
        bb.vertex(1, -1, 0).endVertex();
        bb.vertex(1, 1, 0).endVertex();
        bb.vertex(-1, 1, 0).endVertex();
        BufferUploader.drawWithShader(bb.end());
    }

    public void close() {
        for (RenderTarget t : new RenderTarget[] {scene, a, b}) {
            if (t != null) t.destroyBuffers();
        }
        scene = a = b = null;
    }

    /** Los registra Forge al cargar los recursos. */
    @Mod.EventBusSubscriber(modid = "tfclient", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void onShaders(RegisterShadersEvent event) {
            try {
                event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation("tfclient", "crate_sky"),
                        DefaultVertexFormat.POSITION), s -> sky = s);
                event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation("tfclient", "crate_bloom"),
                        DefaultVertexFormat.POSITION), s -> bloom = s);
                event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation("tfclient", "crate_post"),
                        DefaultVertexFormat.POSITION), s -> post = s);
            } catch (IOException e) {
                FSCrates.LOGGER.error("[TF Crates] No se pudieron cargar los shaders de la cinemática (va sin resplandor)", e);
            }
        }
    }
}
