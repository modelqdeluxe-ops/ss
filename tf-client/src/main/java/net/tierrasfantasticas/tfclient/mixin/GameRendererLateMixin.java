package net.tierrasfantasticas.tfclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.tierrasfantasticas.tfclient.vacio.AfterShaders;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Con paquete de shaders (Oculus), la grieta y el cielo del Void se dibujan después de la pasada final del paquete:
 * justo al volver de {@code LevelRenderer.renderLevel} (donde Oculus la hace) y antes de que Minecraft borre la
 * profundidad para la mano. Ver {@link AfterShaders}.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererLateMixin {
    private static final String LEVEL = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V";

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = LEVEL))
    private void tfclient$beforeLevel(float partial, long finishNanos, PoseStack pose, CallbackInfo ci) {
        AfterShaders.begin(pose);
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = LEVEL, shift = At.Shift.AFTER))
    private void tfclient$afterLevel(float partial, long finishNanos, PoseStack pose, CallbackInfo ci) {
        AfterShaders.end(partial);
    }
}
