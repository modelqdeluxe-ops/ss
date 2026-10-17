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
}
