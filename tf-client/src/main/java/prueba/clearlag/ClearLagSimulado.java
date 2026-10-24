/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package prueba.clearlag;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

/**
 * (1.3.134) Solo para la prueba en el juego (BossTest «tienda»): borra los objetos del suelo como un plugin de clear lag
 * (desde una clase de fuera del mod con «ClearLag» en el nombre), para ver que TFWebDrops deja los de la web.
 */
public final class ClearLagSimulado {
    private ClearLagSimulado() {}

    /** Borra (discard) los objetos del suelo de esa zona. Devuelve cuántos intentó borrar. */
    public static int limpiar(ServerLevel level, AABB zona) {
        int n = 0;
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, zona)) {
            e.discard();
            n++;
        }
        return n;
    }
}
