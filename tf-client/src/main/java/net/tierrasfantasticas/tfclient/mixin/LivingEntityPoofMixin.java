/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.tierrasfantasticas.tfclient.vfx.VfxClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Efectos de kill: la nube de humo con la que Minecraft quita un mob muerto (evento 60, un segundo después de morir)
 * no sale mientras esa víctima hace el efecto (saldría a mitad del efecto en el sitio de la muerte). VfxClient la
 * saca al final si el cuerpo sigue a la vista.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityPoofMixin {
    @Inject(method = "handleEntityEvent(B)V", at = @At("HEAD"), cancellable = true)
    private void tfclient$killFxPoof(byte id, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (id == 60 && self.level().isClientSide() && VfxClient.hidesDeathPoof(self)) ci.cancel();
    }
}
