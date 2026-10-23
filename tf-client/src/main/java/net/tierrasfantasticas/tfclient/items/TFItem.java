/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
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
