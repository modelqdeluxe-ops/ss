package net.tierrasfantasticas.tfclient.mixin;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.tierrasfantasticas.tfclient.anim.AnimSettings;
import net.tierrasfantasticas.tfclient.anim.PunchyBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Punchy apagado (o el tipo del objeto apagado en el pad) = Minecraft normal (1.3.71): no oculta el gesto normal del cuerno. */
@Pseudo
@Mixin(targets = "punchy.client.state.UseItemStateMachine", remap = false)
public abstract class PunchyUseItemMixin {
    @Inject(method = "shouldMaskVanillaHornUse(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Z",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void tfclient$cuernoNormal(Player player, InteractionHand hand, CallbackInfoReturnable<Boolean> cir) {
        if (PunchyBridge.vanilla() || (player != null && hand != null && AnimSettings.blocked(player.getItemInHand(hand)))) {
            cir.setReturnValue(false);
        }
    }
}
