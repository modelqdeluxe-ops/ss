/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.crates.client.scene;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL11;

/**
 * (1.3.106) Los efectos de luz de la cinemática (halos, hadas, luciérnagas, haz, polvo, runas, pilar, premios; los rayos;
 * los pétalos), dibujados como el agua: sus vértices en un BufferBuilder propio, subidos a un VertexBuffer propio y
 * pintados con shaders propios (crate_fx_glow, crate_fx_rays, crate_fx_soft). Antes iban por el lote del juego
 * (renderBuffers().bufferSource() con sus RenderType) y en el juego del dueño no salía ninguno, ni el de serie ni los de
 * la caja; el agua, que ya iba así, sí. Si un shader no carga, ese efecto vuelve al lote del juego, como antes.
 */
public final class SceneFx {
    public enum Kind {
        /** Lo que suma luz, con la textura del brillo suave. */
        GLOW,
        /** Lo que suma luz sin textura (los rayos del premio). */
        RAYS,
        /** Lo translúcido (los pétalos). */
        SOFT
    }

    private static final BufferBuilder[] BUILDERS = new BufferBuilder[Kind.values().length];
    private static final VertexBuffer[] BUFFERS = new VertexBuffer[Kind.values().length];
    /** Cuántas veces se pintó cada uno por el camino propio y cuántas por el del juego (la prueba en el juego). */
    static final int[] OWN = new int[Kind.values().length], GAME = new int[Kind.values().length];

    private SceneFx() {}

    private static ShaderInstance shader(Kind k) {
        return switch (k) {
            case GLOW -> ScenePost.fxGlow;
            case RAYS -> ScenePost.fxRays;
            case SOFT -> ScenePost.fxSoft;
        };
    }

    private static RenderType type(Kind k) {
        return switch (k) {
            case GLOW -> SceneRenderTypes.GLOW_ADD;
            case RAYS -> SceneRenderTypes.RAYS;
            case SOFT -> SceneRenderTypes.SOFT;
        };
    }

    /** Dónde escribir los vértices de un efecto (los mismos que con su RenderType: los de CinematicFx no cambian). */
    public static VertexConsumer begin(Kind k, MultiBufferSource buf) {
        if (shader(k) == null) return buf.getBuffer(type(k));
        int i = k.ordinal();
        if (BUILDERS[i] == null) BUILDERS[i] = new BufferBuilder(k == Kind.GLOW ? 65536 : 8192);
        BufferBuilder b = BUILDERS[i];
        if (b.building()) b.discard();
        b.begin(VertexFormat.Mode.QUADS, k == Kind.GLOW ? DefaultVertexFormat.POSITION_COLOR_TEX : DefaultVertexFormat.POSITION_COLOR);
        return b;
    }

    /**
     * Pinta lo escrito desde begin, con la cámara y la proyección de RenderSystem (las de la escena), probando la
     * profundidad sin escribirla, como sus RenderType.
     */
    public static void end(Kind k, MultiBufferSource.BufferSource buf) {
        ShaderInstance sh = shader(k);
        int i = k.ordinal();
        BufferBuilder b = BUILDERS[i];
        if (sh == null || b == null || !b.building()) {
            buf.endBatch(type(k));
            GAME[i]++;
            return;
        }
        BufferBuilder.RenderedBuffer rb = b.endOrDiscardIfEmpty();
        if (rb == null) return;
        if (BUFFERS[i] == null) BUFFERS[i] = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        VertexBuffer vb = BUFFERS[i];
        RenderSystem.enableBlend();
        if (k == Kind.SOFT) {
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        } else {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        }
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        if (k == Kind.GLOW) {
            Minecraft.getInstance().getTextureManager().getTexture(SceneRenderTypes.GLOW).setFilter(true, false);
            RenderSystem.setShaderTexture(0, SceneRenderTypes.GLOW);
        }
        vb.bind();
        vb.upload(rb);
        vb.drawWithShader(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), sh);
        VertexBuffer.unbind();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        OWN[i]++;
    }

    /** Para la prueba: «propio GLOW 300, RAYS 300, SOFT 300 / del juego 0, 0, 0». */
    public static String stats() {
        return "propio " + OWN[0] + "/" + OWN[1] + "/" + OWN[2] + ", del juego " + GAME[0] + "/" + GAME[1] + "/" + GAME[2];
    }
}
