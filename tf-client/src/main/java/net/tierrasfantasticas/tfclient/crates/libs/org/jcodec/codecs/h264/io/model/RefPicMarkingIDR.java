package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.h264.io.model;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.platform.Platform;

public class RefPicMarkingIDR {
   boolean discardDecodedPics;
   boolean useForlongTerm;

   public RefPicMarkingIDR(boolean discardDecodedPics, boolean useForlongTerm) {
      this.discardDecodedPics = discardDecodedPics;
      this.useForlongTerm = useForlongTerm;
   }

   public boolean isDiscardDecodedPics() {
      return this.discardDecodedPics;
   }

   public boolean isUseForlongTerm() {
      return this.useForlongTerm;
   }

   @Override
   public String toString() {
      return Platform.toJSON(this);
   }
}
