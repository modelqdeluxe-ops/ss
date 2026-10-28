/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.mixin;

import net.minecraft.world.item.ItemStack;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.items.TFEnchanting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (1.3.150) Las piezas de los sets van a la mesa de encantamientos aunque ya estén encantadas (ver TFEnchanting). */
@Mixin(ItemStack.class)
public abstract class EnchantTableMixin {
    @Inject(method = "isEnchantable()Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void tfclient$tableAccepts(CallbackInfoReturnable<Boolean> cir) {
        try {
            Boolean ok = TFEnchanting.tableAccepts((ItemStack) (Object) this);
            if (ok != null) cir.setReturnValue(ok);
        } catch (Throwable t) {
            TFClient.LOGGER.error("TF Encantar: fallo mirando si se puede encantar", t);
        }
    }
}
