/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.claims.util;

import net.tierrasfantasticas.tfclient.claims.data.Claim;
import net.tierrasfantasticas.tfclient.claims.data.ClaimManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;

public final class ExplosionGuard {
    private ExplosionGuard() {
    }

    public static boolean protects(BlockGetter blockgetter, BlockPos blockpos) {
        if (blockpos != null && blockgetter instanceof Level) {
            Level level = (Level)blockgetter;
            if (level.isClientSide) {
                return false;
            }
            Claim claim = ClaimManager.getInstance().getClaimAt(level, blockpos);
            return claim != null && (claim.getFlags().blockExplosions || claim.getFlags().publicMode);
        }
        return false;
    }
}

