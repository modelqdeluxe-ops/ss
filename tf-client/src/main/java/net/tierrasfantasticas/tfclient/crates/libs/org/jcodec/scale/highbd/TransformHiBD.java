package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.scale.highbd;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.PictureHiBD;

public interface TransformHiBD {
   void transform(PictureHiBD var1, PictureHiBD var2);

   public static enum Levels {
      STUDIO,
      PC;
   }
}
