package net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.syntax;

import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.AACDecoderConfig;
import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.AACException;

class SCE_LFE extends Element {
   private final ICStream ics;

   SCE_LFE(int frameLength) {
      this.ics = new ICStream(frameLength);
   }

   void decode(IBitStream _in, AACDecoderConfig conf) throws AACException {
      this.readElementInstanceTag(_in);
      this.ics.decode(_in, false, conf);
   }

   public ICStream getICStream() {
      return this.ics;
   }
}
