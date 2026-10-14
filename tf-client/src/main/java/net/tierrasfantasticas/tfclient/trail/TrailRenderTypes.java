package net.tierrasfantasticas.tfclient.trail;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Las capas de render de las estelas (hereda de RenderType solo para usar sus piezas, que son protected): de color
 * (posición y color) o con textura (humo, destellos), con luz que suma (brillo) o translúcidas; sin caras ocultas,
 * sin escribir profundidad (no tapan) y probando la profundidad (las tapan los bloques).
 */
final class TrailRenderTypes extends RenderType {
    private TrailRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort,
                             Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }

    private static RenderType color(String name, TransparencyStateShard transparency) {
        return create(name, DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 8192, false, true,
                CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setTransparencyState(transparency).setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE).setDepthTestState(LEQUAL_DEPTH_TEST).setLightmapState(NO_LIGHTMAP)
                        .createCompositeState(false));
    }

    static final RenderType GLOW = color("tfclient_trail_glow", LIGHTNING_TRANSPARENCY);
    static final RenderType TRANSLUCENT = color("tfclient_trail_translucent", TRANSLUCENT_TRANSPARENCY);

    private static final Map<String, RenderType> TEXTURED = new HashMap<>();

    static RenderType textured(ResourceLocation texture, boolean additive) {
        return TEXTURED.computeIfAbsent(texture + (additive ? "+" : "~"), k -> create("tfclient_trail_tex", DefaultVertexFormat.POSITION_COLOR_TEX,
                VertexFormat.Mode.QUADS, 8192, false, true,
                CompositeState.builder().setShaderState(POSITION_COLOR_TEX_SHADER).setTextureState(new TextureStateShard(texture, true, false))
                        .setTransparencyState(additive ? LIGHTNING_TRANSPARENCY : TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE).setDepthTestState(LEQUAL_DEPTH_TEST).setLightmapState(NO_LIGHTMAP)
                        .createCompositeState(false)));
    }
}
