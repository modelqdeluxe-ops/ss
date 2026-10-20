package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.aac;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.aac.blocks.Block;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.io.BitWriter;

public class BlockWriter {
   public void nextBlock(BitWriter bits, Block block) {
      bits.writeNBit(block.getType().ordinal(), 3);
      if (block.getType() != BlockType.TYPE_END) {
         ;
      }
   }
}
