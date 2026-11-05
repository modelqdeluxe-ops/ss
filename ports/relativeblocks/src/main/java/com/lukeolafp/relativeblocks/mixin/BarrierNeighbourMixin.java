package com.lukeolafp.relativeblocks.mixin;

import com.lukeolafp.relativeblocks.blocks.RelativePlacement;
import com.lukeolafp.relativeblocks.init.BlockInit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vallas, barrotes y muros se unen a los que están dentro de un grupo de al lado. */
@Mixin({FenceBlock.class, IronBarsBlock.class, WallBlock.class})
public abstract class BarrierNeighbourMixin {
   @Inject(method = "updateShape", at = @At("HEAD"), cancellable = true)
   private void relativeblocks$seeIntoCluster(
      BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos,
      CallbackInfoReturnable<BlockState> cir
   ) {
      if (BlockInit.BLOCK_CLUSTER != null && neighborState.is(BlockInit.BLOCK_CLUSTER) && direction.getAxis().isHorizontal()) {
         BlockState member = RelativePlacement.seenByPlain(world, state, direction, neighborPos);
         if (member != null) {
            cir.setReturnValue(state.updateShape(direction, member, world, pos, neighborPos));
         }
      }
   }

   @Inject(method = "updateShape", at = @At("RETURN"), cancellable = true)
   private void relativeblocks$rejoinClusters(
      BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos,
      CallbackInfoReturnable<BlockState> cir
   ) {
      BlockState result = cir.getReturnValue();
      if (BlockInit.BLOCK_CLUSTER != null && direction.getAxis().isHorizontal() && result.is(state.getBlock()) && world.getBlockState(pos).is(state.getBlock())) {
         BlockState rejoined = RelativePlacement.rejoinClusters(world, pos, result, direction);
         if (rejoined != result) {
            cir.setReturnValue(rejoined);
         }
      }
   }
}
