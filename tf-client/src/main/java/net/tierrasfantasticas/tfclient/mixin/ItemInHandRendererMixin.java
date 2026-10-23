/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.tierrasfantasticas.tfclient.trail.TrailClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Estelas de arma (1.3.67): cada objeto en la mano pasa por renderItem (el de la primera persona y el de los brazos de
 * jugadores y mobs); ahí se apunta dónde están la punta y la base de la hoja. Las de primera persona se dibujan al
 * acabar las manos, con su misma cámara.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"))
    private void tfclient$trailCapture(LivingEntity entity, ItemStack stack, ItemDisplayContext ctx, boolean left, PoseStack pose,
                                       MultiBufferSource buffers, int light, CallbackInfo ci) {
        TrailClient.capture(entity, stack, ctx, left, pose);
    }

    @Inject(method = "renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/player/LocalPlayer;I)V",
            at = @At("RETURN"))
    private void tfclient$trailFirstPerson(float partial, PoseStack pose, MultiBufferSource.BufferSource buffers, LocalPlayer player, int light,
                                           CallbackInfo ci) {
        TrailClient.renderFirstPerson(buffers);
    }
}
