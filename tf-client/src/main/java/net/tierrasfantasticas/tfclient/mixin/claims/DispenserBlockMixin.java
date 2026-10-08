package net.tierrasfantasticas.tfclient.mixin.claims;

import net.tierrasfantasticas.tfclient.claims.data.Claim;
import net.tierrasfantasticas.tfclient.claims.data.ClaimManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={DispenserBlock.class})
public abstract class DispenserBlockMixin {
    @Inject(method={"dispenseFrom"}, at={@At(value="HEAD")}, cancellable=true, require=0)
    private void claimblocks$blockCrossClaimDispense(ServerLevel serverlevel, BlockPos blockpos, CallbackInfo callbackinfo) {
        Direction direction;
        BlockState blockstate = serverlevel.getBlockState(blockpos);
        try {
            direction = (Direction)blockstate.getValue((Property)DispenserBlock.FACING);
        }
        catch (Exception exception) {
            return;
        }
        BlockPos blockpos1 = blockpos.relative(direction);
        ClaimManager claimmanager = ClaimManager.getInstance();
        Claim claim = claimmanager.getClaimAt((Level)serverlevel, blockpos);
        Claim claim1 = claimmanager.getClaimAt((Level)serverlevel, blockpos1);
        if (!DispenserBlockMixin.sameClaim(claim, claim1) && (DispenserBlockMixin.protectsBuilding(claim1) || DispenserBlockMixin.protectsBuilding(claim))) {
            callbackinfo.cancel();
        }
    }

    private static boolean sameClaim(Claim claim, Claim claim1) {
        if (claim == null && claim1 == null) {
            return true;
        }
        return claim != null && claim1 != null ? claim.getClaimId().equals(claim1.getClaimId()) : false;
    }

    private static boolean protectsBuilding(Claim claim) {
        return claim == null ? false : claim.getFlags().publicMode || claim.getFlags().blockBuilding;
    }
}

