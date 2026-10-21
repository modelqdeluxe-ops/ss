package net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.tools;

import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.huffman.HCB;
import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.syntax.CPE;
import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.syntax.ICSInfo;
import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.syntax.ICStream;
import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.syntax.SyntaxConstants;

public final class MS implements SyntaxConstants, HCB {
   private MS() {
   }

   public static void process(CPE cpe, float[] specL, float[] specR) {
      ICStream ics = cpe.getLeftChannel();
      ICSInfo info = ics.getInfo();
      int[] offsets = info.getSWBOffsets();
      int windowGroups = info.getWindowGroupCount();
      int maxSFB = info.getMaxSFB();
      int[] sfbCBl = ics.getSfbCB();
      int[] sfbCBr = cpe.getRightChannel().getSfbCB();
      int groupOff = 0;
      int idx = 0;

      for (int g = 0; g < windowGroups; g++) {
         for (int i = 0; i < maxSFB; idx++) {
            if (cpe.isMSUsed(idx) && sfbCBl[idx] < 13 && sfbCBr[idx] < 13) {
               for (int w = 0; w < info.getWindowGroupLength(g); w++) {
                  int off = groupOff + w * 128 + offsets[i];

                  for (int j = 0; j < offsets[i + 1] - offsets[i]; j++) {
                     float t = specL[off + j] - specR[off + j];
                     specL[off + j] = specL[off + j] + specR[off + j];
                     specR[off + j] = t;
                  }
               }
            }

            i++;
         }

         groupOff += info.getWindowGroupLength(g) * 128;
      }
   }
}
