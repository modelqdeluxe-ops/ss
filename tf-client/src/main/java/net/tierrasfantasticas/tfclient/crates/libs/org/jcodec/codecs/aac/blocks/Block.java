package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.aac.blocks;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.aac.BlockType;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.io.BitReader;

public abstract class Block {
   private BlockType type;

   public BlockType getType() {
      return this.type;
   }

   public abstract void parse(BitReader var1);
}
