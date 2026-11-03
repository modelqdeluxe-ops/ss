/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.mixin.items;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.tierrasfantasticas.tfclient.items.TFCreativeGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (1.3.168) En creativo, el botón central sobre un hueco de cualquier inventario (cofre, barril, el propio…) copia el
 * montón entero (ClickType.CLONE). Con lo nuestro no hace nada: ni en el servidor (lo que cuenta) ni en el cliente (para
 * que no salga una copia fantasma en el cursor).
 */
@Mixin(AbstractContainerMenu.class)
public abstract class ContainerCloneGuardMixin {
    @Inject(method = "doClick", at = @At("HEAD"), cancellable = true, require = 0)
    private void tfclient$noClone(int slotId, int button, ClickType type, Player player, CallbackInfo ci) {
        try {
            if (type != ClickType.CLONE || slotId < 0 || player == null || !player.getAbilities().instabuild) return;
            AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
            if (slotId < menu.slots.size() && TFCreativeGuard.blocksClone(menu.slots.get(slotId).getItem())) ci.cancel();
        } catch (Throwable ignored) {
            // nunca romper un inventario por esto
        }
    }
}
