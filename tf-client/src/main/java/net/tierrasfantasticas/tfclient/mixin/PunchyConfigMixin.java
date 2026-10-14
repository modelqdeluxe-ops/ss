package net.tierrasfantasticas.tfclient.mixin;

import net.minecraft.world.item.ItemStack;
import net.tierrasfantasticas.tfclient.anim.AnimSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Efectos › ANIMACIONES por tipo de objeto (1.3.69): Punchy pregunta aquí si un objeto está en su lista negra (lo
 * consultan todas sus animaciones y su forma de pintar los brazos); un tipo apagado en el pad cuenta como bloqueado y
 * esa mano vuelve a Minecraft normal. Punchy es otro mod (va dentro del jar): @Pseudo y sin remapear, y si no está o
 * cambia, no se aplica (require = 0).
 */
@Pseudo
@Mixin(targets = "punchy.config.PunchyConfig", remap = false)
public abstract class PunchyConfigMixin {
    @Inject(method = "isItemBlacklisted(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true,
            remap = false, require = 0)
    private static void tfclient$tiposApagados(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (AnimSettings.blocked(stack)) cir.setReturnValue(true);
    }
}
