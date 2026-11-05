/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.mixin.luna;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.tierrasfantasticas.tfclient.luna.client.BloodMoonClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * (1.3.175; 1.3.178) Con la Luna de Sangre, la luna de Minecraft no se dibuja aquí (textura vacía): la dibuja
 * {@code BloodMoonClient}, la llena de la textura de Minecraft teñida de roja, en su sitio y de su tamaño.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererLunaMixin {
    @ModifyArg(method = "renderSky", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(ILnet/minecraft/resources/ResourceLocation;)V"), index = 1)
    private ResourceLocation tfclient$bloodMoonTexture(ResourceLocation texture) {
        if (BloodMoonClient.sky(0F) > 0.05F && "minecraft".equals(texture.getNamespace())
                && "textures/environment/moon_phases.png".equals(texture.getPath())) {
            // (1.3.178) la de Minecraft no se dibuja aquí: BloodMoonClient dibuja la llena de Minecraft, roja, donde toca
            // (y en la escena, la que nace)
            return BloodMoonClient.NO_MOON;
        }
        return texture;
    }
}
