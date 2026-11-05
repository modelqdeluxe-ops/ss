package com.lukeolafp.relativeblocks.init;

import com.lukeolafp.relativeblocks.blocks.BlockClusterBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class BlockEntityInit {
   public static BlockEntityType<BlockClusterBlockEntity> BLOCK_CLUSTER;

   public static BlockEntityType<BlockClusterBlockEntity> create() {
      BLOCK_CLUSTER = BlockEntityType.Builder.of(BlockClusterBlockEntity::new, BlockInit.BLOCK_CLUSTER).build(null);
      return BLOCK_CLUSTER;
   }
}
