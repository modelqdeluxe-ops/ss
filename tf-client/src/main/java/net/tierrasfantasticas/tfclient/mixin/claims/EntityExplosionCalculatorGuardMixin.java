package net.tierrasfantasticas.tfclient.mixin.claims;

import net.tierrasfantasticas.tfclient.claims.TFClaims;
import net.tierrasfantasticas.tfclient.claims.util.ExplosionGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EntityBasedExplosionDamageCalculator;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={EntityBasedExplosionDamageCalculator.class})
public abstract class EntityExplosionCalculatorGuardMixin {
    @Inject(method={"shouldBlockExplode(Lnet/minecraft/world/level/Explosion;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;F)Z"}, at={@At(value="HEAD")}, cancellable=true, require=0)
    private void claimblocks$keepClaimedBlocks(Explosion explosion, BlockGetter blockgetter, BlockPos blockpos, BlockState blockstate, float f, CallbackInfoReturnable<Boolean> callbackinforeturnable) {
        try {
            if (ExplosionGuard.protects(blockgetter, blockpos)) {
                callbackinforeturnable.setReturnValue(false);
            }
        }
        catch (Throwable throwable) {
            TFClaims.LOGGER.error("[FantasticClaims] Fallo protegiendo bloques de una explosion", throwable);
        }
    }
}

