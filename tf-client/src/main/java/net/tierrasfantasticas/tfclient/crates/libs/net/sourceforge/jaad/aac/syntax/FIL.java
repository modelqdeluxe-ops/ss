package net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.syntax;

import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.AACException;
import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.SampleFrequency;

class FIL extends Element implements SyntaxConstants {
   private static final int TYPE_FILL = 0;
   private static final int TYPE_FILL_DATA = 1;
   private static final int TYPE_EXT_DATA_ELEMENT = 2;
   private static final int TYPE_DYNAMIC_RANGE = 11;
   private static final int TYPE_SBR_DATA = 13;
   private static final int TYPE_SBR_DATA_CRC = 14;
   private final boolean downSampledSBR;
   private FIL.DynamicRangeInfo dri;

   FIL(boolean downSampledSBR) {
      this.downSampledSBR = downSampledSBR;
   }

   void decode(IBitStream _in, Element prev, SampleFrequency sf, boolean sbrEnabled, boolean smallFrames) throws AACException {
      int count = _in.readBits(4);
      if (count == 15) {
         count += _in.readBits(8) - 1;
      }

      count *= 8;
      int cpy = count;
      int pos = _in.getPosition();

      while (count > 0) {
         count = this.decodeExtensionPayload(_in, count, prev, sf, sbrEnabled, smallFrames);
      }

      int pos2 = _in.getPosition() - pos;
      int bitsLeft = cpy - pos2;
      if (bitsLeft > 0) {
         _in.skipBits(pos2);
      } else if (bitsLeft < 0) {
         throw new AACException("FIL element overread: " + bitsLeft);
      }
   }

   private int decodeExtensionPayload(IBitStream _in, int count, Element prev, SampleFrequency sf, boolean sbrEnabled, boolean smallFrames) throws AACException {
      int type = _in.readBits(4);
      int ret = count - 4;
      switch (type) {
         case 11:
            ret = this.decodeDynamicRangeInfo(_in, ret);
            break;
         case 13:
         case 14:
            if (sbrEnabled) {
               if (!(prev instanceof SCE_LFE) && !(prev instanceof CPE) && !(prev instanceof CCE)) {
                  throw new AACException("SBR applied on unexpected element: " + prev);
               }

               prev.decodeSBR(_in, sf, ret, prev instanceof CPE, type == 14, this.downSampledSBR, smallFrames);
               ret = 0;
               break;
            } else {
               _in.skipBits(ret);
               ret = 0;
            }
         case 0:
         case 1:
         case 2:
         case 3:
         case 4:
         case 5:
         case 6:
         case 7:
         case 8:
         case 9:
         case 10:
         case 12:
         default:
            _in.skipBits(ret);
            ret = 0;
      }

      return ret;
   }

   private int decodeDynamicRangeInfo(IBitStream _in, int count) throws AACException {
      if (this.dri == null) {
         this.dri = new FIL.DynamicRangeInfo();
      }

      int ret = count;
      int bandCount = 1;
      if (this.dri.pceTagPresent = _in.readBool()) {
         this.dri.pceInstanceTag = _in.readBits(4);
         this.dri.tagReservedBits = _in.readBits(4);
      }

      if (this.dri.excludedChannelsPresent = _in.readBool()) {
         ret = count - this.decodeExcludedChannels(_in);
      }

      if (this.dri.bandsPresent = _in.readBool()) {
         this.dri.bandsIncrement = _in.readBits(4);
         this.dri.interpolationScheme = _in.readBits(4);
         ret -= 8;
         bandCount += this.dri.bandsIncrement;
         this.dri.bandTop = new int[bandCount];

         for (int i = 0; i < bandCount; i++) {
            this.dri.bandTop[i] = _in.readBits(8);
            ret -= 8;
         }
      }

      if (this.dri.progRefLevelPresent = _in.readBool()) {
         this.dri.progRefLevel = _in.readBits(7);
         this.dri.progRefLevelReservedBits = _in.readBits(1);
         ret -= 8;
      }

      this.dri.dynRngSgn = new boolean[bandCount];
      this.dri.dynRngCtl = new int[bandCount];

      for (int i = 0; i < bandCount; i++) {
         this.dri.dynRngSgn[i] = _in.readBool();
         this.dri.dynRngCtl[i] = _in.readBits(7);
         ret -= 8;
      }

      return ret;
   }

   private int decodeExcludedChannels(IBitStream _in) throws AACException {
      int exclChs = 0;

      do {
         for (int i = 0; i < 7; i++) {
            this.dri.excludeMask[exclChs] = _in.readBool();
            exclChs++;
         }
      } while (exclChs < 57 && _in.readBool());

      return exclChs / 7 * 8;
   }

   public static class DynamicRangeInfo {
      private static final int MAX_NBR_BANDS = 7;
      private final boolean[] excludeMask = new boolean[7];
      private final boolean[] additionalExcludedChannels = new boolean[7];
      private boolean pceTagPresent;
      private int pceInstanceTag;
      private int tagReservedBits;
      private boolean excludedChannelsPresent;
      private boolean bandsPresent;
      private int bandsIncrement;
      private int interpolationScheme;
      private int[] bandTop;
      private boolean progRefLevelPresent;
      private int progRefLevel;
      private int progRefLevelReservedBits;
      private boolean[] dynRngSgn;
      private int[] dynRngCtl;
   }
}
