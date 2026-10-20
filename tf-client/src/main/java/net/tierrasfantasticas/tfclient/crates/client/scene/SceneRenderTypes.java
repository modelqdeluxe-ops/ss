package net.tierrasfantasticas.tfclient.crates.client.scene;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Las capas de render del santuario de las crates (hereda de RenderType solo para usar sus piezas, que son protected).
 * Todas van a la imagen principal: la cinemática se dibuja dentro de una pantalla, y con gráficos «Fabulosos»
 * RenderType.translucent() y lightning() irían a capas del mundo que nadie compone después.
 */
final class SceneRenderTypes extends RenderType {
    private SceneRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort,
                             Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }

    static final ResourceLocation GLOW = new ResourceLocation("fscrates", "textures/gui/glow.png");

    /** Luz que suma (haces de sol, destellos, runas, el pilar de luz), con la textura del brillo suave. */
    static final RenderType GLOW_ADD = create("tfclient_crate_glow", DefaultVertexFormat.POSITION_COLOR_TEX, VertexFormat.Mode.QUADS,
            65536, false, false, CompositeState.builder().setShaderState(POSITION_COLOR_TEX_SHADER)
                    .setTextureState(new TextureStateShard(GLOW, true, false)).setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setLightmapState(NO_LIGHTMAP).createCompositeState(false));

    /** Lo mismo sin textura (rayos con forma propia). */
    static final RenderType RAYS = create("tfclient_crate_rays", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 16384,
            false, false, CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setLightmapState(NO_LIGHTMAP).createCompositeState(false));

    /** Cosas pequeñas con color y transparencia (pétalos, mariposas, pájaros, espuma): sin escribir profundidad. */
    static final RenderType SOFT = create("tfclient_crate_soft", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 16384,
            false, false, CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setLightmapState(NO_LIGHTMAP).createCompositeState(false));

    /** El agua (la del estanque y los chorros): como translucent() pero en la imagen principal. */
    static final RenderType WATER = create("tfclient_crate_water", DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 262144, true,
            true, CompositeState.builder().setLightmapState(LIGHTMAP).setShaderState(RENDERTYPE_TRANSLUCENT_SHADER)
                    .setTextureState(BLOCK_SHEET_MIPPED).setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                    .createCompositeState(true));
}
