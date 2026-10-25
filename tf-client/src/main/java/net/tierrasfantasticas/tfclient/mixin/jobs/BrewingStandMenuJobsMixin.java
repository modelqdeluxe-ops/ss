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
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.item.ItemStack;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.jobs.TFJobs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (1.3.139) Las pociones sacadas del soporte con mayúsculas + clic, para los oficios y las misiones: el evento de Forge
 * llega entonces con el montón ya vacío (objeto «aire»). Se mira qué había en el hueco antes y cuánto salió; mientras
 * tanto {@link TFJobs#quickBrew} hace que el evento no cuente (para no contarlo dos veces).
 */
@Mixin(BrewingStandMenu.class)
public abstract class BrewingStandMenuJobsMixin {
    @Unique
    private ItemStack tfclient$before = ItemStack.EMPTY;

    @Inject(method = "quickMoveStack(Lnet/minecraft/world/entity/player/Player;I)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"), require = 0)
    private void tfclient$head(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        tfclient$before = ItemStack.EMPTY;
        if (index < 0 || index > 2 || !(player instanceof ServerPlayer)) return;
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        if (index >= menu.slots.size()) return;
        tfclient$before = menu.slots.get(index).getItem().copy();
        TFJobs.quickBrew = !tfclient$before.isEmpty();
    }

    @Inject(method = "quickMoveStack(Lnet/minecraft/world/entity/player/Player;I)Lnet/minecraft/world/item/ItemStack;",
            at = @At("RETURN"), require = 0)
    private void tfclient$tail(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack before = tfclient$before;
        tfclient$before = ItemStack.EMPTY;
        TFJobs.quickBrew = false;
        if (before.isEmpty() || !(player instanceof ServerPlayer sp)) return;
        try {
            ItemStack now = ((AbstractContainerMenu) (Object) this).slots.get(index).getItem();
            int moved = before.getCount() - (ItemStack.isSameItemSameTags(before, now) ? now.getCount() : 0);
            if (moved > 0) TFJobs.made(sp, "preparar", before, moved);
        } catch (Throwable t) {
            TFClient.LOGGER.error("TF Oficios: fallo al contar las pociones", t);
        }
    }
}
