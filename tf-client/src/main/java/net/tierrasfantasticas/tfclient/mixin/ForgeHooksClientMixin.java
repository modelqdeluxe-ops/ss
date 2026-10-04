package net.tierrasfantasticas.tfclient.mixin;

import net.minecraftforge.client.ForgeHooksClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sin los avisos de Forge (versión beta, actualización) dibujados encima del menú principal. */
@Mixin(value = ForgeHooksClient.class, remap = false)
public abstract class ForgeHooksClientMixin {
    @Inject(method = "renderMainMenu", at = @At("HEAD"), cancellable = true)
    private static void tfclient$hideMainMenuWarnings(CallbackInfo ci) {
        ci.cancel();
    }
}
