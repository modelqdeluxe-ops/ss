package net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.tools;

import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.AACException;
import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.SampleFrequency;
import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.syntax.IBitStream;
import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.syntax.ICSInfo;
import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.syntax.ICStream;
import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.syntax.SyntaxConstants;

public class TNS implements SyntaxConstants, TNSTables {
   private static final int TNS_MAX_ORDER = 20;
   private static final int[] SHORT_BITS = new int[]{1, 4, 3};
   private static final int[] LONG_BITS = new int[]{2, 6, 5};
   private int[] nFilt = new int[8];
   private int[][] length = new int[8][4];
   private int[][] order;
   private boolean[][] direction = new boolean[8][4];
   private float[][][] coef;

   public TNS() {
      this.order = new int[8][4];
      this.coef = new float[8][4][20];
   }

   public void decode(IBitStream _in, ICSInfo info) throws AACException {
      int windowCount = info.getWindowCount();
      int[] bits = info.isEightShortFrame() ? SHORT_BITS : LONG_BITS;

      for (int w = 0; w < windowCount; w++) {
         if ((this.nFilt[w] = _in.readBits(bits[0])) != 0) {
            int coefRes = _in.readBit();

            for (int filt = 0; filt < this.nFilt[w]; filt++) {
               this.length[w][filt] = _in.readBits(bits[1]);
               if ((this.order[w][filt] = _in.readBits(bits[2])) > 20) {
                  throw new AACException("TNS filter out of range: " + this.order[w][filt]);
               }

               if (this.order[w][filt] != 0) {
                  this.direction[w][filt] = _in.readBool();
                  int coefCompress = _in.readBit();
                  int coefLen = coefRes + 3 - coefCompress;
                  int tmp = 2 * coefCompress + coefRes;

                  for (int i = 0; i < this.order[w][filt]; i++) {
                     this.coef[w][filt][i] = TNS_TABLES[tmp][_in.readBits(coefLen)];
                  }
               }
            }
         }
      }
   }

   public void process(ICStream ics, float[] spec, SampleFrequency sf, boolean decode) {
   }
}
