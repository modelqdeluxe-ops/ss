package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.aac.blocks;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.io.BitReader;

public class BlockFil extends Block {
   @Override
   public void parse(BitReader _in) {
      int num = _in.readNBit(4);
      if (num == 15) {
         num += _in.readNBit(8) - 1;
      }

      if (num > 0 && _in.skip(8 * num) != 8 * num) {
         throw new RuntimeException("Overread");
      }
   }
}
