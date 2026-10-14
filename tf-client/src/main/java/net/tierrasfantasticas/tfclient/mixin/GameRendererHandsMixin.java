package net.tierrasfantasticas.tfclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.tierrasfantasticas.tfclient.trail.TrailClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Estelas de arma con Punchy (1.3.70): mientras se pintan las manos en primera persona, todo lo que se dibuje del propio
 * jugador es primera persona, aunque Punchy pinte el arma con el modelo de tercera persona (la cuelga de su brazo 3D).
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererHandsMixin {
    @Inject(method = "renderItemInHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/Camera;F)V", at = @At("HEAD"))
    private void tfclient$handsStart(PoseStack pose, Camera camera, float partial, CallbackInfo ci) {
        TrailClient.handsPass(true);
    }

    @Inject(method = "renderItemInHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/Camera;F)V", at = @At("RETURN"))
    private void tfclient$handsEnd(PoseStack pose, Camera camera, float partial, CallbackInfo ci) {
        TrailClient.handsPass(false);
    }
}
