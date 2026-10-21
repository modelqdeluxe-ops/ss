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
 * el resplandor más bello» y luego «sin shaders, sin bloom, sin colores vivos… no las texturas de Minecraft así»). La
 * escena no se dibuja directamente en la pantalla sino en una textura propia (con su profundidad); luego:
 * <ol>
 *   <li>el cielo de día (crate_sky) al fondo, antes del 3D (se ve por el hueco de las copas); el agua, con su shader
 *       (crate_water, desde SanctuaryScene);</li>
 *   <li>el resplandor (crate_bloom): se baja a un cuarto, se queda lo que brilla y se desenfoca dos veces (estrecho y
 *       ancho); y la imagen entera baja y desenfocada, para la profundidad de campo;</li>
 *   <li>la imagen final (crate_post) en la pantalla: algo de profundidad de campo (la crate nítida, lo de muy lejos
 *       un poco suave) + resplandor + rayos que salen del hueco del sol + colores vivos + viñeta.</li>
 * </ol>
 * Si los shaders no cargan, la cinemática dibuja como antes, directa a la pantalla.
 */
public final class ScenePost {
    static ShaderInstance sky, bloom, post, water;
    /** (1.3.106) Los de los efectos de luz (SceneFx): lo que suma con textura, los rayos y lo translúcido. */
    static ShaderInstance fxGlow, fxRays, fxSoft;
    /** Desde qué brillo florece (el agua turquesa y las luces sí; la crate y la piedra, no). */
    static final float THRESHOLD = 0.78F;
    private TextureTarget scene, a, b, c, d;

    public static boolean ready() {
        return sky != null && bloom != null && post != null;
    }

    /** El shader del agua (crate_water), o null si no cargó (entonces el agua la dibuja el juego). */
    static ShaderInstance water() {
        return water;
    }

    /** Prepara las texturas para el tamaño de la ventana (las rehace si cambia). */
    public void ensure(int w, int h) {
        if (scene != null && scene.width == w && scene.height == h) return;
        close();
        scene = new TextureTarget(w, h, true, Minecraft.ON_OSX);
        a = new TextureTarget(Math.max(1, w / 4), Math.max(1, h / 4), false, Minecraft.ON_OSX);
        b = new TextureTarget(Math.max(1, w / 4), Math.max(1, h / 4), false, Minecraft.ON_OSX);
        c = new TextureTarget(Math.max(1, w / 4), Math.max(1, h / 4), false, Minecraft.ON_OSX);
        d = new TextureTarget(Math.max(1, w / 4), Math.max(1, h / 4), false, Minecraft.ON_OSX);
        for (RenderTarget t : new RenderTarget[] {scene, a, b, c, d}) {
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

    /** El cielo de fondo (sin profundidad): ejes de la cámara, tangente del medio campo y el sol. */
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
     * El resplandor, la profundidad de campo y la imagen final en la pantalla. {@code sunU, sunV}: el haz en la imagen
     * (0..1, desde abajo a la izquierda; puede estar fuera); {@code sunVis}: cuánto se ven sus rayos; {@code focus}: a
     * qué distancia está lo nítido (la crate); {@code near, far}: los planos de la proyección; {@code time}, en ticks (los
     * rayos de sol se mueven despacio).
     */
    public void finish(float sunU, float sunV, float sunVis, float bloomStrength, float rayStrength, float exposure, float focus,
                       float near, float far, float time) {
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
        // 2b) la imagen entera, baja y desenfocada (el fondo de la profundidad de campo)
        pass(scene, c, 2, 1.0F / scene.width, 1.0F / scene.height, 0, 0);
        pass(c, d, 1, 0, 0, tw * 1.5F, 0);
        pass(d, c, 1, 0, 0, 0, th * 1.5F);
        pass(c, d, 1, 0, 0, tw * 2.5F, 0);
        pass(d, c, 1, 0, 0, 0, th * 2.5F);
        // 3) la imagen final, en la pantalla
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        ShaderInstance sh = post;
        sh.setSampler("SceneSampler", scene.getColorTextureId());
        sh.setSampler("BloomSampler", a.getColorTextureId());
        sh.setSampler("BlurSampler", c.getColorTextureId());
        sh.setSampler("DepthSampler", scene.getDepthTextureId());
        sh.safeGetUniform("Clip").set(near, far);
        sh.safeGetUniform("Focus").set(focus, 16.0F);
        sh.safeGetUniform("SunPos").set(sunU, sunV);
        sh.safeGetUniform("SunVis").set(sunVis);
        sh.safeGetUniform("BloomStrength").set(bloomStrength);
        sh.safeGetUniform("RayStrength").set(rayStrength);
        sh.safeGetUniform("Exposure").set(exposure);
        sh.safeGetUniform("Time").set(time);
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
        sh.safeGetUniform("Threshold").set(THRESHOLD);
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
        for (RenderTarget t : new RenderTarget[] {scene, a, b, c, d}) {
            if (t != null) t.destroyBuffers();
        }
        scene = a = b = c = d = null;
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
                event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation("tfclient", "crate_water"),
                        DefaultVertexFormat.POSITION_COLOR_TEX), s -> water = s);
                event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation("tfclient", "crate_fx_glow"),
                        DefaultVertexFormat.POSITION_COLOR_TEX), s -> fxGlow = s);
                event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation("tfclient", "crate_fx_rays"),
                        DefaultVertexFormat.POSITION_COLOR), s -> fxRays = s);
                event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation("tfclient", "crate_fx_soft"),
                        DefaultVertexFormat.POSITION_COLOR), s -> fxSoft = s);
            } catch (IOException e) {
                FSCrates.LOGGER.error("[TF Crates] No se pudieron cargar los shaders de la cinemática (va sin resplandor)", e);
            }
        }
    }
}
