/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.clans;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * (1.3.159) El cofre del banco no guarda nada (el banco es digital): esta entidad solo está para que ClanBankRenderer lo
 * dibuje a plena luz (brilla en la oscuridad, como las crates; el dueño: «haz que el cofre brille en la oscuridad como las
 * crates»). De quién es el cofre lo sabe el servidor (PadClanBank.ownerAt).
 */
public class ClanBankBlockEntity extends BlockEntity {
    public ClanBankBlockEntity(BlockPos pos, BlockState state) {
        super(ClanBlocks.BANK_BE.get(), pos, state);
    }

    /** El modelo sale un poco del bloque (es del tamaño de una crate): que no se deje de dibujar en los bordes. */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(0.5);
    }
}
