package net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.syntax;

import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.AACDecoderConfig;
import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.AACException;
import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.huffman.Huffman;

class CCE extends Element implements SyntaxConstants {
   public static final int BEFORE_TNS = 0;
   public static final int AFTER_TNS = 1;
   public static final int AFTER_IMDCT = 2;
   private static final float[] CCE_SCALE = new float[]{1.0905077F, 1.1892071F, 1.4142135F, 2.0F};
   private final ICStream ics;
   private float[] iqData;
   private int couplingPoint;
   private int coupledCount;
   private final boolean[] channelPair;
   private final int[] idSelect;
   private final int[] chSelect;
   private final float[][] gain;

   CCE(int frameLength) {
      this.ics = new ICStream(frameLength);
      this.channelPair = new boolean[8];
      this.idSelect = new int[8];
      this.chSelect = new int[8];
      this.gain = new float[16][120];
   }

   int getCouplingPoint() {
      return this.couplingPoint;
   }

   int getCoupledCount() {
      return this.coupledCount;
   }

   boolean isChannelPair(int index) {
      return this.channelPair[index];
   }

   int getIDSelect(int index) {
      return this.idSelect[index];
   }

   int getCHSelect(int index) {
      return this.chSelect[index];
   }

   void decode(IBitStream _in, AACDecoderConfig conf) throws AACException {
      this.couplingPoint = 2 * _in.readBit();
      this.coupledCount = _in.readBits(3);
      int gainCount = 0;

      for (int i = 0; i <= this.coupledCount; i++) {
         gainCount++;
         this.channelPair[i] = _in.readBool();
         this.idSelect[i] = _in.readBits(4);
         if (this.channelPair[i]) {
            this.chSelect[i] = _in.readBits(2);
            if (this.chSelect[i] == 3) {
               gainCount++;
            }
         } else {
            this.chSelect[i] = 2;
         }
      }

      this.couplingPoint = this.couplingPoint + _in.readBit();
      this.couplingPoint = this.couplingPoint | this.couplingPoint >> 1;
      boolean sign = _in.readBool();
      double scale = (double)CCE_SCALE[_in.readBits(2)];
      this.ics.decode(_in, false, conf);
      ICSInfo info = this.ics.getInfo();
      int windowGroupCount = info.getWindowGroupCount();
      int maxSFB = info.getMaxSFB();
      int[][] sfbCB = new int[][]{new int[0]};

      for (int var20 = 0; var20 < gainCount; var20++) {
         int idx = 0;
         int cge = 1;
         int xg = 0;
         float gainCache = 1.0F;
         if (var20 > 0) {
            cge = this.couplingPoint == 2 ? 1 : _in.readBit();
            xg = cge == 0 ? 0 : Huffman.decodeScaleFactor(_in) - 60;
            gainCache = (float)Math.pow(scale, (double)(-xg));
         }

         if (this.couplingPoint == 2) {
            this.gain[var20][0] = gainCache;
         } else {
            for (int g = 0; g < windowGroupCount; g++) {
               for (int sfb = 0; sfb < maxSFB; idx++) {
                  if (sfbCB[g][sfb] != 0) {
                     if (cge == 0) {
                        int t = Huffman.decodeScaleFactor(_in) - 60;
                        if (t != 0) {
                           int s = 1;
                           t = xg += t;
                           if (!sign) {
                              s -= 2 * (t & 1);
                              t >>= 1;
                           }

                           gainCache = (float)(Math.pow(scale, (double)(-t)) * (double)s);
                        }
                     }

                     this.gain[var20][idx] = gainCache;
                  }

                  sfb++;
               }
            }
         }
      }
   }

   void process() throws AACException {
      this.iqData = this.ics.getInvQuantData();
   }

   void applyIndependentCoupling(int index, float[] data) {
      double g = (double)this.gain[index][0];

      for (int i = 0; i < data.length; i++) {
         data[i] = (float)((double)data[i] + g * (double)this.iqData[i]);
      }
   }

   void applyDependentCoupling(int index, float[] data) {
      ICSInfo info = this.ics.getInfo();
      int[] swbOffsets = info.getSWBOffsets();
      int windowGroupCount = info.getWindowGroupCount();
      int maxSFB = info.getMaxSFB();
      int[][] sfbCB = new int[][]{new int[0]};
      int srcOff = 0;
      int dstOff = 0;
      int idx = 0;

      for (int g = 0; g < windowGroupCount; g++) {
         int len = info.getWindowGroupLength(g);

         for (int sfb = 0; sfb < maxSFB; idx++) {
            if (sfbCB[g][sfb] != 0) {
               float x = this.gain[index][idx];

               for (int group = 0; group < len; group++) {
                  for (int k = swbOffsets[sfb]; k < swbOffsets[sfb + 1]; k++) {
                     data[dstOff + group * 128 + k] = data[dstOff + group * 128 + k] + x * this.iqData[srcOff + group * 128 + k];
                  }
               }
            }

            sfb++;
         }

         dstOff += len * 128;
         srcOff += len * 128;
      }
   }
}
