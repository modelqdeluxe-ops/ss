package net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.syntax;

import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.AACException;
import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.SampleFrequency;
import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.sbr.SBR;

public abstract class Element implements SyntaxConstants {
   private int elementInstanceTag;
   private SBR sbr;

   protected void readElementInstanceTag(IBitStream _in) throws AACException {
      this.elementInstanceTag = _in.readBits(4);
   }

   public int getElementInstanceTag() {
      return this.elementInstanceTag;
   }

   void decodeSBR(IBitStream _in, SampleFrequency sf, int count, boolean stereo, boolean crc, boolean downSampled, boolean smallFrames) throws AACException {
      if (this.sbr == null) {
         this.sbr = new SBR(smallFrames, this.elementInstanceTag == 1, sf, downSampled);
      }

      this.sbr.decode(_in, count);
   }

   boolean isSBRPresent() {
      return this.sbr != null;
   }

   SBR getSBR() {
      return this.sbr;
   }
}
