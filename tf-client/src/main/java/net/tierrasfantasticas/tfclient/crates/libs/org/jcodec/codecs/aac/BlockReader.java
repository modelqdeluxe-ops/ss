package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.aac;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.aac.blocks.Block;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.io.BitReader;

public class BlockReader {
   public Block nextBlock(BitReader bits) {
      BlockType type = BlockType.values()[(int)((long)bits.readNBit(3))];
      if (type == BlockType.TYPE_END) {
         return null;
      } else {
         int id = bits.readNBit(4);
         return null;
      }
   }
}
