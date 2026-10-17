package net.tierrasfantasticas.tfclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.tierrasfantasticas.tfclient.bosses.RiftSky;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class RiftCloudsMixin {
    /**
     * Con el cielo de la grieta entrado (más de la mitad), sin las nubes de Minecraft: teñidas se veían como manchas
     * oscuras y cuadradas sobre las nebulosas (prueba en el juego, 1.3.86).
     */
    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void tfclient$noCloudsInRiftSky(PoseStack pose, Matrix4f projection, float partial, double camX, double camY, double camZ,
                                           CallbackInfo ci) {
        if (RiftSky.strength(partial) > 0.5F) ci.cancel();
    }
}
