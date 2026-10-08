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

