/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.mixin.binding;

import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.tierrasfantasticas.tfclient.items.TFBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (1.3.141) Las tolvas (y las vagonetas con tolva) no recogen del suelo un objeto vinculado: si alguien rompe el cofre
 * donde estaba, se queda en el suelo (sin desaparecer) hasta que lo coja su dueño.
 */
@Mixin(HopperBlockEntity.class)
public abstract class HopperBindingMixin {
    @Inject(method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/entity/item/ItemEntity;)Z", at = @At("HEAD"),
            cancellable = true, require = 0)
    private static void tfclient$notBound(Container container, ItemEntity entity, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (TFBinding.bound(entity.getItem())) cir.setReturnValue(false);
        } catch (Throwable ignored) {
            // como Minecraft
        }
    }
}
