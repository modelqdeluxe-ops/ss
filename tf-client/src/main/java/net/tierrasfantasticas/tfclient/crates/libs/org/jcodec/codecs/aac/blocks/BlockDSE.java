package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.aac.blocks;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.io.BitReader;

public class BlockDSE extends Block {
   @Override
   public void parse(BitReader _in) {
      int elemType = _in.readNBit(4);
      int byte_align = _in.read1Bit();
      int count = _in.readNBit(8);
      if (count == 255) {
         count += _in.readNBit(8);
      }

      if (byte_align != 0) {
         _in.align();
      }

      if (_in.skip(8 * count) != 8 * count) {
         throw new RuntimeException("Overread");
      }
   }
}
