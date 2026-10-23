/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.vacio;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.bosses.RiftSky;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * El cielo de The Void (1.3.88, el dueño: «quita ese sol, hazle un rework al cielo, que haya un agujero negro gigante
 * animado, como el cielo de Iterationt en el End; que me haga sentir pequeño»). Todo el cielo es un shader
 * (shaders/core/void_sky.fsh): trazado de rayos que se curvan con la gravedad alrededor de un agujero negro con su disco
 * de acreción, sobre el panorama de nebulosas (grieta_cielo.png) y estrellas que titilan. Se ve de frente al llegar al
 * santuario, detrás del caballero. Encima, las estrellas fugaces de {@link RiftSky}.
 * Calidad según los gráficos de Minecraft (Rápidos: menos pasos del rayo). Para verlo sin el juego:
 * {@code xvfb-run -a python3 tools/void/cielo_preview.py carpeta} (las mismas cuentas de HOLE y diskNormal).
 */
@OnlyIn(Dist.CLIENT)
public final class VoidSky {
    static final ResourceLocation PANORAMA = new ResourceLocation(TFClient.MOD_ID, "textures/environment/grieta_cielo.png");
    /**
     * Hacia dónde está el agujero: al sur, algo al este y subido 22° (1.3.89: el santuario está en la punta norte de la
     * isla y se llega mirando al sur, hacia la grieta del centro; el agujero queda detrás de ella).
     */
    static final Vector3f HOLE = new Vector3f(0.16F, 0.42F, 1.0F).normalize();
    static ShaderInstance shader;
    private static VertexBuffer cube;
    private static boolean warned;
    /** La furia que se ve ahora (va detrás de la de RiftWarp, suave) y el carmesí de la madre. */
    private static float rageShown, motherShown;
    private static long rageNanos;

    private VoidSky() {}

    /** La normal del disco: casi de canto (7° hacia ti) y algo girado (−16°), como en cielo_preview.py. */
    static Vector3f diskNormal() {
        Vector3f f = new Vector3f(HOLE);
        Vector3f up = new Vector3f(0, 1, 0);
        Vector3f n0 = new Vector3f(up).sub(new Vector3f(f).mul(up.dot(f))).normalize();
        double tilt = Math.toRadians(7), roll = Math.toRadians(-16);
        Vector3f n1 = new Vector3f(n0).mul((float) Math.cos(tilt)).sub(new Vector3f(f).mul((float) Math.sin(tilt))).normalize();
        Vector3f n = new Vector3f(n1).mul((float) Math.cos(roll))
                .add(new Vector3f(f).cross(n1).mul((float) Math.sin(roll)))
                .add(new Vector3f(f).mul(f.dot(n1) * (float) (1 - Math.cos(roll))));
        return n.normalize();
    }

    /** Dibuja el cielo entero (lo llama {@link VoidEffects#renderSky}). */
    static void render(PoseStack pose, Matrix4f projection, float partial) {
        if (!draw(pose.last().pose(), projection, partial, false)) return;
        // Las estrellas fugaces, encima
        RiftSky.drawMeteors(pose, partial);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
    }

    /**
     * Con paquete de shaders, después de su pasada final ({@link AfterShaders}): el mismo cielo, solo donde la escena
     * dejó la profundidad en 1 (donde se ve el cielo); los bloques, los jefes y la mano quedan delante. Sin las
     * estrellas fugaces (son cuadros a 88 bloques: con la profundidad de la escena saldrían delante de la isla lejana).
     */
    public static void renderAfterShaders(Matrix4f view, Matrix4f projection, float partial) {
        draw(view, projection, partial, true);
    }

    private static boolean draw(Matrix4f view, Matrix4f projection, float partial, boolean atFar) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        ShaderInstance sh = shader;
        if (sh == null) {
            if (!warned) {
                warned = true;
                TFClient.LOGGER.warn("TF Void: el shader del cielo no está cargado (se ve el vacío negro)");
            }
            return false;
        }
        if (cube == null) cube = buildCube();
        float time = ((mc.level.getGameTime() % 72000L) + partial) / 20F;
        GraphicsStatus g = mc.options.graphicsMode().get();
        float quality = g == GraphicsStatus.FAST ? 0.35F : g == GraphicsStatus.FANCY ? 0.75F : 1F;
        Vector3f n = diskNormal();
        sh.safeGetUniform("VoidTime").set(time);
        sh.safeGetUniform("HoleDir").set(HOLE.x, HOLE.y, HOLE.z);
        sh.safeGetUniform("DiskNormal").set(n.x, n.y, n.z);
        sh.safeGetUniform("Quality").set(quality);
        sh.safeGetUniform("AtFar").set(atFar ? 1F : 0F);
        // (1.3.91) una grieta activa cerca: el agujero negro se enfurece (RiftWarp). 1.3.92: entra y sale despacio (~1 s),
        // no de golpe (con la distorsión de la grieta, «a veces el agujero negro como que bugueaba»)
        long now = System.nanoTime();
        float dt = rageNanos == 0L ? 0F : Math.min(0.25F, (now - rageNanos) / 1e9F);
        rageNanos = now;
        float k = 1F - (float) Math.exp(-dt * 1.2F);
        rageShown += (net.tierrasfantasticas.tfclient.bosses.RiftWarp.rage() - rageShown) * k;
        motherShown += ((net.tierrasfantasticas.tfclient.bosses.RiftWarp.rageMother() ? 1F : 0F) - motherShown) * k;
        sh.safeGetUniform("Rage").set(rageShown);
        sh.safeGetUniform("RageMother").set(motherShown);
        RenderSystem.setShaderTexture(0, PANORAMA);
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        if (atFar) {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
        }
        cube.bind();
        cube.drawWithShader(view, projection, sh);
        VertexBuffer.unbind();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        return true;
    }

    private static VertexBuffer buildCube() {
        float s = 100F;
        float[][] c = {{-s, -s, -s}, {s, -s, -s}, {s, s, -s}, {-s, s, -s}, {-s, -s, s}, {s, -s, s}, {s, s, s}, {-s, s, s}};
        int[][] faces = {{0, 1, 2, 3}, {5, 4, 7, 6}, {4, 0, 3, 7}, {1, 5, 6, 2}, {3, 2, 6, 7}, {4, 5, 1, 0}};
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        for (int[] f : faces) for (int k : f) b.vertex(c[k][0], c[k][1], c[k][2]).endVertex();
        VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vb.bind();
        vb.upload(b.end());
        VertexBuffer.unbind();
        return vb;
    }

    /** Al recargar los recursos (o salir del mundo), el cubo se rehace. */
    static void reset() {
        if (cube != null) {
            cube.close();
            cube = null;
        }
    }
}
