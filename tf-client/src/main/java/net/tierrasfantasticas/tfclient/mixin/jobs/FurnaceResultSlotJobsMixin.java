/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.mixin.jobs;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.ItemStack;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.jobs.TFJobs;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (1.3.139, el dueño: «las misiones no reconocen nada») Lo fundido (horno, ahumador, alto horno), para los oficios y las misiones: lo que se saca del
 * hueco del resultado (removeCount) y de qué objeto. Antes contaba el evento de Forge, que con mayúsculas + clic llega con
 * el montón ya vacío (objeto «aire»): «fundir hierro» no contaba nada.
 */
@Mixin(FurnaceResultSlot.class)
public abstract class FurnaceResultSlotJobsMixin {
    @Shadow
    @Final
    private Player player;
    @Shadow
    private int removeCount;
    /** Lo último que salió con mayúsculas + clic (luego el montón llega vacío). */
    @Unique
    private ItemStack tfclient$last = ItemStack.EMPTY;

    @Inject(method = "onQuickCraft(Lnet/minecraft/world/item/ItemStack;I)V", at = @At("HEAD"), require = 0)
    private void tfclient$remember(ItemStack stack, int amount, CallbackInfo ci) {
        if (!stack.isEmpty()) tfclient$last = stack.copy();
    }

    @Inject(method = "checkTakeAchievements(Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), require = 0)
    private void tfclient$crafted(ItemStack stack, CallbackInfo ci) {
        try {
            ItemStack item = stack.isEmpty() ? tfclient$last : stack;
            if (removeCount > 0 && !item.isEmpty() && player instanceof ServerPlayer sp) TFJobs.made(sp, "fundir", item, removeCount);
        } catch (Throwable t) {
            TFClient.LOGGER.error("TF Oficios: fallo al contar lo fundido", t);
        }
    }
}
