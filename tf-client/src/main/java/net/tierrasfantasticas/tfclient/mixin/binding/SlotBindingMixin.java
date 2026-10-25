/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.mixin.binding;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.tierrasfantasticas.tfclient.items.TFBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (1.3.141, el dueño: «a uno le robaron el kit de la crate de un cofre, no es posible que lo puedan tomar») Un objeto
 * vinculado a otro jugador no se puede coger de ningún hueco (cofre, barril, shulker, el inventario…): ni con clic, ni
 * con mayúsculas, ni con las teclas de número, ni tirándolo. Minecraft lo pregunta con Slot.mayPickup en cada caso.
 */
@Mixin(Slot.class)
public abstract class SlotBindingMixin {
    @Inject(method = "mayPickup(Lnet/minecraft/world/entity/player/Player;)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void tfclient$notTheirs(Player player, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (TFBinding.foreign(((Slot) (Object) this).getItem(), player)) cir.setReturnValue(false);
        } catch (Throwable ignored) {
            // nunca romper un inventario por esto
        }
    }
}
