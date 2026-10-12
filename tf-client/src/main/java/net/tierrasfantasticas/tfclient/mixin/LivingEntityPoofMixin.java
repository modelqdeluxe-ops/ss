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
