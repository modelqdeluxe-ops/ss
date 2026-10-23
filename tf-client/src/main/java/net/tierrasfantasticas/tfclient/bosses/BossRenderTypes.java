/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.bosses;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/**
 * Las capas de render propias de los jefes (hereda de RenderType solo para usar sus piezas, que son protected).
 */
final class BossRenderTypes extends RenderType {
    private BossRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort,
                            Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }

    /**
     * Los rayos de la muerte épica: luz que suma, como RenderType.lightning() del dragón, pero probando la profundidad
     * sin escribirla y en la imagen principal. lightning() escribe profundidad y, con gráficos «Fabulosos», va a la capa
     * del clima: los rayos se veían a través de los bloques y tapaban lo que se dibujaba después.
     */
    static final RenderType RAYS = create("tfclient_boss_rays", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 8192,
            false, true, CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setLightmapState(NO_LIGHTMAP).createCompositeState(false));

    /**
     * La obsidiana de la grieta 3.0 (1.3.88): triángulos con su luz en el color, las vetas del shader rift_obsidian, el
     * mapa de luz y sin cara oculta (las caras de las garras se ven por dentro al pasar entre ellas).
     */
    static final RenderType RIFT_OBSIDIAN = create("tfclient_rift_obsidian", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLES,
            262144, true, false, CompositeState.builder().setShaderState(new ShaderStateShard(() -> RiftShaders.obsidian))
                    .setTransparencyState(NO_TRANSPARENCY).setCullState(NO_CULL).setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY).createCompositeState(true));

    /** El desgarro y el círculo de runas (shader rift_fx): alfa premultiplicado, sin escribir profundidad. */
    static final RenderType RIFT_FX = create("tfclient_rift_fx", DefaultVertexFormat.POSITION_COLOR_TEX, VertexFormat.Mode.QUADS, 4096,
            false, true, CompositeState.builder().setShaderState(new ShaderStateShard(() -> RiftShaders.fx))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE)
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setLightmapState(NO_LIGHTMAP).createCompositeState(false));
}
