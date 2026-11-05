package com.lukeolafp.relativeblocks.init;

import com.lukeolafp.relativeblocks.blocks.BlockClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.material.PushReaction;

public class BlockInit {
   /** El bloque que guarda varios bloques pequeños en un mismo hueco. Se crea en el RegisterEvent de los bloques. */
   public static Block BLOCK_CLUSTER;

   public static Block create() {
      BLOCK_CLUSTER = new BlockClusterBlock(
         Properties.of()
            .strength(0.2F)
            .noOcclusion()
            .forceSolidOff()
            .dynamicShape()
            .sound(SoundType.GRASS)
            .pushReaction(PushReaction.DESTROY)
            .randomTicks()
            .lightLevel(state -> state.getValue(BlockClusterBlock.LIGHT_LEVEL))
      );
      return BLOCK_CLUSTER;
   }
}
