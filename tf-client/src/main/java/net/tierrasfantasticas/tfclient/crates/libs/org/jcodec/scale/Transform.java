package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.scale;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.Picture;

public interface Transform {
   void transform(Picture var1, Picture var2);

   public static enum Levels {
      STUDIO,
      PC;
   }
}
