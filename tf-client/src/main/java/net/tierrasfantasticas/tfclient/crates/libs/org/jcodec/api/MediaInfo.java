package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.api;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.Size;

public class MediaInfo {
   private Size dim;

   public MediaInfo(Size dim) {
      this.dim = dim;
   }

   public Size getDim() {
      return this.dim;
   }

   public void setDim(Size dim) {
      this.dim = dim;
   }
}
