package net.tierrasfantasticas.tfclient.pad;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Capas de dibujo propias del pad (hereda de RenderType solo para usar sus piezas, que son protected). La fuente pixel:
 * como el texto de Minecraft, pero sin reordenar al subir: así los rótulos seguidos van en una sola llamada.
 */
final class PadRenderTypes extends RenderType {
    private PadRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort, Runnable setup,
                           Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }

    static RenderType font(ResourceLocation texture) {
        return create("tfclient_pad_font", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 4096, false, false,
                CompositeState.builder().setShaderState(RENDERTYPE_TEXT_SHADER).setTextureState(new TextureStateShard(texture, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setLightmapState(LIGHTMAP).createCompositeState(false));
    }
}
