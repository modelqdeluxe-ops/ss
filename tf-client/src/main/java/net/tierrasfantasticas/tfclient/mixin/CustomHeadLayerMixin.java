package net.tierrasfantasticas.tfclient.mixin;

import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.tierrasfantasticas.tfclient.items.TFWardrobe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Armario de la web: el cosmético de cabeza elegido se dibuja en lugar de lo que lleva en la cabeza. */
@Mixin(CustomHeadLayer.class)
public abstract class CustomHeadLayerMixin {
    @Redirect(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack tfclient$wardrobe(LivingEntity entity, EquipmentSlot slot) {
        ItemStack real = entity.getItemBySlot(slot);
        return slot == EquipmentSlot.HEAD ? TFWardrobe.headLayer(entity, real) : real;
    }
}
