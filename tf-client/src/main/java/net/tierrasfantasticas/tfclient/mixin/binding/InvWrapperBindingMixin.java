/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.mixin.binding;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import net.tierrasfantasticas.tfclient.items.TFBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (1.3.141) Las tolvas, tuberías y demás máquinas sacan las cosas de los cofres por estos envoltorios de Forge: un objeto
 * vinculado no sale (así nadie se lo lleva de un cofre ajeno con una tolva debajo).
 */
@Mixin(value = {InvWrapper.class, SidedInvWrapper.class}, remap = false)
public abstract class InvWrapperBindingMixin {
    @Inject(method = "extractItem(IIZ)Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"), cancellable = true, require = 0)
    private void tfclient$notBound(int slot, int amount, boolean simulate, CallbackInfoReturnable<ItemStack> cir) {
        try {
            if (TFBinding.bound(((IItemHandler) (Object) this).getStackInSlot(slot))) cir.setReturnValue(ItemStack.EMPTY);
        } catch (Throwable ignored) {
            // como Forge
        }
    }
}
