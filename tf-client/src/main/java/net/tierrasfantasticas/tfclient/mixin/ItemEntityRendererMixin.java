/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.mixin;

import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** (1.3.117) Los objetos tirados en el Void con shaders, con luz (VoidItemLight). */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
    @ModifyVariable(method = "render(Lnet/minecraft/world/entity/item/ItemEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int tfclient$voidItemLight(int light) {
        return net.tierrasfantasticas.tfclient.vacio.VoidItemLight.lift(light);
    }
}
