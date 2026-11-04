/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.mixin.items;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * (1.3.171) Para la lanza lanzada de los sets (items.TFThrownTrident): el objeto que lleva dentro el tridente de
 * Minecraft (de él salen el daño con Empalamiento, Canalización, la lealtad y el brillo) y los datos de lealtad y brillo.
 */
@Mixin(ThrownTrident.class)
public interface ThrownTridentAccessor {
    @Accessor("tridentItem")
    void tfclient$setTridentItem(ItemStack stack);

    @Accessor("ID_LOYALTY")
    static EntityDataAccessor<Byte> tfclient$loyalty() {
        throw new AssertionError();
    }

    @Accessor("ID_FOIL")
    static EntityDataAccessor<Boolean> tfclient$foil() {
        throw new AssertionError();
    }
}
