package net.tierrasfantasticas.tfclient.mixin;

import net.tierrasfantasticas.tfclient.anim.PunchyBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Punchy apagado = Minecraft normal (1.3.71): la cámara no se mueve con sus animaciones (aunque quedara una a medias). */
@Pseudo
@Mixin(targets = "punchy.client.render.CameraAnimationState", remap = false)
public abstract class PunchyCameraMixin {
    @Inject(method = "isActive()Z", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void tfclient$camaraNormal(CallbackInfoReturnable<Boolean> cir) {
        if (PunchyBridge.vanilla()) cir.setReturnValue(false);
    }
}
