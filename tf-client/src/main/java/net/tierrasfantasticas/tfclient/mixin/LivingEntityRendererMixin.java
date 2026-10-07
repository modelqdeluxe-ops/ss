package net.tierrasfantasticas.tfclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.tierrasfantasticas.tfclient.vfx.VfxActor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Efectos de kill: cuando la víctima es humanoide (zombis, esqueletos, jugadores, piglins...), su cabeza, brazos y
 * piernas hacen la animación del efecto. Justo después de setupAnim, para ganar a las poses propias de cada mob (los
 * brazos estirados del zombi, el arco del esqueleto). Solo mientras VfxActor la dibuja; luego el modelo queda igual.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V",
                    shift = At.Shift.AFTER))
    private void tfclient$vfxPose(LivingEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers,
                                  int light, CallbackInfo ci) {
        if (VfxActor.isRendering(entity)
                && ((LivingEntityRenderer<?, ?>) (Object) this).getModel() instanceof HumanoidModel<?> model) {
            VfxActor.applyHumanoid(model, entity);
        }
    }
}
