package net.tierrasfantasticas.tfclient.items;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.extensions.IForgeItem;

/**
 * Lo que tienen en común todos los objetos de los sets: son permanentes. No se gastan ni se rompen (sin durabilidad,
 * tampoco las alas al planear) y tirados al suelo no desaparecen ni los destruye nada ({@link TFBinding#protect}).
 */
public interface TFItem extends IForgeItem {
    @Override
    default boolean isDamageable(ItemStack stack) {
        return false;
    }

    @Override
    default boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        TFBinding.protect(entity);
        return false;
    }
}
