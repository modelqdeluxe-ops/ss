package net.tierrasfantasticas.tfclient.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.extensions.IForgeItemStack;
import net.tierrasfantasticas.tfclient.items.TFWings;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Alas de los sets puestas en el hueco de la espalda (Accessories / Curios): Minecraft solo mira si el objeto del pecho
 * puede planear (al saltar en el aire, en el servidor al empezar y en cada tick de vuelo). Aquí, si el del pecho no
 * puede, cuentan las alas de la espalda. Forge pone estos dos métodos en IForgeItemStack; al declararlos en ItemStack
 * se usan estos (hacen lo mismo que los de Forge y además miran {@link TFWings}).
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin implements IForgeItemStack {
    @Override
    public boolean canElytraFly(LivingEntity entity) {
        ItemStack self = (ItemStack) (Object) this;
        return self.getItem().canElytraFly(self, entity) || TFWings.fromBackSlot(self, entity);
    }

    @Override
    public boolean elytraFlightTick(LivingEntity entity, int flightTicks) {
        ItemStack self = (ItemStack) (Object) this;
        return self.getItem().elytraFlightTick(self, entity, flightTicks) || TFWings.fromBackSlot(self, entity);
    }
}
