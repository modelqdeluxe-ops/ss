package net.tierrasfantasticas.tfclient.mixin;

import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.tierrasfantasticas.tfclient.items.TFWardrobe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Armario de la web: la armadura elegida se dibuja en lugar de la que lleva puesta (solo apariencia). */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {
    @Redirect(method = "renderArmorPiece", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack tfclient$wardrobe(LivingEntity entity, EquipmentSlot slot) {
        return TFWardrobe.armorLayer(entity, slot, entity.getItemBySlot(slot));
    }
}
