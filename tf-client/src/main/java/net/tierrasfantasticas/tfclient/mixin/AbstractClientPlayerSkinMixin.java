package net.tierrasfantasticas.tfclient.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.tierrasfantasticas.tfclient.skins.SkinClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Skins del Armario: el jugador se dibuja con la skin que eligió en el pad (la textura y los brazos finos o normales),
 * también en primera persona. Sin skin del Armario, la de su cuenta (no se toca nada).
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerSkinMixin {
    @Inject(method = "getSkinTextureLocation", at = @At("HEAD"), cancellable = true)
    private void tfclient$skin(CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation loc = SkinClient.texture(((AbstractClientPlayer) (Object) this).getUUID());
        if (loc != null) cir.setReturnValue(loc);
    }

    @Inject(method = "getModelName", at = @At("HEAD"), cancellable = true)
    private void tfclient$model(CallbackInfoReturnable<String> cir) {
        String model = SkinClient.model(((AbstractClientPlayer) (Object) this).getUUID());
        if (model != null) cir.setReturnValue(model);
    }
}
