package net.tierrasfantasticas.tfclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.tierrasfantasticas.tfclient.anim.AnimSettings;
import net.tierrasfantasticas.tfclient.anim.PunchyBridge;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Punchy apagado = Minecraft normal (1.3.71): su dibujante de brazos no pinta nada, y lo que guarda de cuando estaba
 * encendido no se usa: la transformación «de reposo» que pone al objeto mientras lo usas (comer, tensar el arco: la
 * aplica a cualquier objeto en ItemRenderer, esté o no encendido; tampoco a un tipo apagado en el pad) y el brazo con el
 * que calcula el sedal de la caña.
 */
@Pseudo
@Mixin(targets = "punchy.client.render.PunchyArmRenderer", remap = false)
public abstract class PunchyArmRendererMixin {
    @Inject(method = "renderFirstPerson(Lnet/minecraft/client/renderer/ItemInHandRenderer;Lnet/minecraft/client/player/LocalPlayer;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void tfclient$sinBrazos(ItemInHandRenderer renderer, LocalPlayer player, float partial, PoseStack pose,
                                           MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (PunchyBridge.vanilla()) ci.cancel();
    }

    @Inject(method = "getCachedIdleTransform(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;)Lnet/minecraft/client/renderer/block/model/ItemTransform;",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void tfclient$sinReposo(ItemStack stack, ItemDisplayContext ctx, CallbackInfoReturnable<ItemTransform> cir) {
        if (PunchyBridge.vanilla() || AnimSettings.blocked(stack)) cir.setReturnValue(null);
    }

    @Inject(method = "resolveBaselineUseTransform(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/client/renderer/block/model/ItemTransform;",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void tfclient$sinBase(ItemStack stack, ItemDisplayContext ctx, LivingEntity entity, CallbackInfoReturnable<ItemTransform> cir) {
        if (PunchyBridge.vanilla() || AnimSettings.blocked(stack)) cir.setReturnValue(null);
    }

    @Inject(method = "getCachedArmWorld(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/entity/HumanoidArm;)Lorg/joml/Matrix4f;",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void tfclient$sinBrazoGuardado(LocalPlayer player, HumanoidArm arm, CallbackInfoReturnable<Matrix4f> cir) {
        if (PunchyBridge.vanilla()) cir.setReturnValue(null);
    }
}
