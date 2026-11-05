package com.lukeolafp.relativeblocks.blocks;

import com.lukeolafp.relativeblocks.init.BlockInit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

public final class MixedSlabs {
   private MixedSlabs() {
   }

   @Nullable
   public static InteractionResult place(Player player, Level world, BlockHitResult hit, ItemStack stack, BlockItem item) {
      Block block = item.getBlock();
      BlockPos clicked = hit.getBlockPos();
      Direction side = hit.getDirection();
      BlockPos target;
      BlockState there;
      BlockState candidate;
      if (block instanceof SlabBlock) {
         boolean upper = hit.getLocation().y - (double)clicked.getY() > 0.5;
         there = world.getBlockState(clicked);
         SlabType half;
         if (loneSlab(there) && fillsOtherHalf((SlabType)there.getValue(SlabBlock.TYPE), side, upper)) {
            target = clicked;
            half = open(there);
         } else {
            target = clicked.relative(side);
            there = world.getBlockState(target);
            if (!loneSlab(there)) {
               return null;
            }

            half = side == Direction.UP ? SlabType.BOTTOM : (side == Direction.DOWN ? SlabType.TOP : (upper ? SlabType.TOP : SlabType.BOTTOM));
            if (half != open(there)) {
               return null;
            }
         }

         candidate = (BlockState)block.defaultBlockState().setValue(SlabBlock.TYPE, half);
      } else {
         if (!(block instanceof VerticalSlabBlock)) {
            return null;
         }

         there = world.getBlockState(clicked);
         Direction open;
         if (there.getBlock() instanceof VerticalSlabBlock && VerticalSlabBlock.reachesOpenHalf(there, side, hit.getLocation(), clicked)) {
            target = clicked;
            open = ((Direction)there.getValue(VerticalSlabBlock.FACING)).getOpposite();
         } else {
            target = clicked.relative(side);
            there = world.getBlockState(target);
            if (!(there.getBlock() instanceof VerticalSlabBlock)) {
               return null;
            }

            open = ((Direction)there.getValue(VerticalSlabBlock.FACING)).getOpposite();
            if (VerticalSlabBlock.facingFor(side, hit.getLocation(), target, player.getDirection()) != open) {
               return null;
            }
         }

         candidate = (BlockState)block.defaultBlockState().setValue(VerticalSlabBlock.FACING, open);
      }

      if (there.is(block)) {
         return null;
      } else if (!world.isUnobstructed(candidate, target, CollisionContext.of(player))) {
         return null;
      } else if (world.isClientSide) {
         return InteractionResult.SUCCESS;
      } else {
         BlockState existing = dry(there);
         world.setBlock(target, BlockInit.BLOCK_CLUSTER.defaultBlockState(), 2);
         if (world.getBlockEntity(target) instanceof BlockClusterBlockEntity cluster) {
            cluster.setLayout(5);
            boolean var17 = secondHalf(candidate);
            cluster.setCell(0, var17 ? existing : candidate);
            cluster.setCell(1, var17 ? candidate : existing);
            if (!player.getAbilities().instabuild) {
               stack.shrink(1);
            }

            world.playSound(
               null,
               target,
               candidate.getSoundType().getPlaceSound(),
               SoundSource.BLOCKS,
               (candidate.getSoundType().getVolume() + 1.0F) / 2.0F,
               candidate.getSoundType().getPitch() * 0.8F
            );
            RelativePlacement.sync(world, target, cluster);
            world.getChunkSource().getLightEngine().checkBlock(target);
            return InteractionResult.SUCCESS;
         } else {
            return InteractionResult.PASS;
         }
      }
   }

   private static boolean secondHalf(BlockState slab) {
      return slab.getBlock() instanceof VerticalSlabBlock
         ? ((Direction)slab.getValue(VerticalSlabBlock.FACING)).getAxisDirection() == AxisDirection.POSITIVE
         : slab.getValue(SlabBlock.TYPE) == SlabType.TOP;
   }

   private static boolean loneSlab(BlockState state) {
      return state.getBlock() instanceof SlabBlock && state.getValue(SlabBlock.TYPE) != SlabType.DOUBLE;
   }

   private static SlabType open(BlockState slab) {
      return slab.getValue(SlabBlock.TYPE) == SlabType.BOTTOM ? SlabType.TOP : SlabType.BOTTOM;
   }

   private static boolean fillsOtherHalf(SlabType type, Direction side, boolean upper) {
      return type == SlabType.BOTTOM
         ? side == Direction.UP || upper && side.getAxis().isHorizontal()
         : side == Direction.DOWN || !upper && side.getAxis().isHorizontal();
   }

   private static BlockState dry(BlockState slab) {
      return slab.hasProperty(BlockStateProperties.WATERLOGGED) ? (BlockState)slab.setValue(BlockStateProperties.WATERLOGGED, false) : slab;
   }
}
