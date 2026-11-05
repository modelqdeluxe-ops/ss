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
 * (1.3.175) Con la Luna de Sangre, la luna del cielo de Minecraft es la roja (llena, sus 8 fases iguales;
 * {@code tools/luna/texturas_luna.py}). Los paquetes de shaders que dibujan la luna de Minecraft también la ven roja.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererLunaMixin {
    @ModifyArg(method = "renderSky", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(ILnet/minecraft/resources/ResourceLocation;)V"), index = 1)
    private ResourceLocation tfclient$bloodMoonTexture(ResourceLocation texture) {
        if (BloodMoonClient.sky(0F) > 0.05F && "minecraft".equals(texture.getNamespace())
                && "textures/environment/moon_phases.png".equals(texture.getPath())) {
            return BloodMoonClient.PHASES;
        }
        return texture;
    }
}
