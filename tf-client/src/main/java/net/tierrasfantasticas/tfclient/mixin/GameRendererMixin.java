package net.tierrasfantasticas.tfclient.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.tierrasfantasticas.tfclient.trail.VividColors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Colores vivos (1.3.67): el mundo (con la mano) ya dibujado pasa por el filtro de saturación, antes de la interfaz. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "render(FJZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V",
            shift = At.Shift.AFTER))
    private void tfclient$vivid(float partial, long nanos, boolean renderLevel, CallbackInfo ci) {
        VividColors.apply(partial);
    }
}
