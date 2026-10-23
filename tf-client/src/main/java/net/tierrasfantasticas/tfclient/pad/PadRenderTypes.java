/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
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

    /**
     * (1.3.109) Lo translúcido del pad (brillos, sombras, velos, destellos): como RenderType.gui() pero sin escribir la
     * profundidad. Cada cosa del pad va un poco más adelante que la anterior (TFPadScreen.nextOrder); un brillo pintado
     * después del texto, si algo del juego del dueño lo pinta antes que el texto, lo tapaba aunque fuera transparente
     * (el brillo del icono de la fila de la TNT se comía «La zona», el título de encima, salvo la última letra).
     */
    static final RenderType SOFT = create("tfclient_pad_soft", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256, false, false,
            CompositeState.builder().setShaderState(RENDERTYPE_GUI_SHADER).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).createCompositeState(false));

    static RenderType font(ResourceLocation texture) {
        return create("tfclient_pad_font", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 4096, false, false,
                CompositeState.builder().setShaderState(RENDERTYPE_TEXT_SHADER).setTextureState(new TextureStateShard(texture, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setLightmapState(LIGHTMAP).createCompositeState(false));
    }
}
