package net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.syntax;

import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.AACDecoderConfig;
import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.AACException;
import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.Profile;
import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.SampleFrequency;
import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.tools.MSMask;
import java.util.Arrays;

public class CPE extends Element implements SyntaxConstants {
   private MSMask msMask;
   private boolean[] msUsed = new boolean[128];
   private boolean commonWindow;
   ICStream icsL;
   ICStream icsR;

   CPE(int frameLength) {
      this.icsL = new ICStream(frameLength);
      this.icsR = new ICStream(frameLength);
   }

   void decode(IBitStream _in, AACDecoderConfig conf) throws AACException {
      Profile profile = conf.getProfile();
      SampleFrequency sf = conf.getSampleFrequency();
      if (sf.equals(SampleFrequency.SAMPLE_FREQUENCY_NONE)) {
         throw new AACException("invalid sample frequency");
      } else {
         this.readElementInstanceTag(_in);
         this.commonWindow = _in.readBool();
         ICSInfo info = this.icsL.getInfo();
         if (this.commonWindow) {
            info.decode(_in, conf, this.commonWindow);
            this.icsR.getInfo().setData(info);
            this.msMask = msMaskFromInt(_in.readBits(2));
            if (this.msMask.equals(MSMask.TYPE_USED)) {
               int maxSFB = info.getMaxSFB();
               int windowGroupCount = info.getWindowGroupCount();

               for (int idx = 0; idx < windowGroupCount * maxSFB; idx++) {
                  this.msUsed[idx] = _in.readBool();
               }
            } else if (this.msMask.equals(MSMask.TYPE_ALL_1)) {
               Arrays.fill(this.msUsed, true);
            } else {
               if (!this.msMask.equals(MSMask.TYPE_ALL_0)) {
                  throw new AACException("reserved MS mask type used");
               }

               Arrays.fill(this.msUsed, false);
            }
         } else {
            this.msMask = MSMask.TYPE_ALL_0;
            Arrays.fill(this.msUsed, false);
         }

         if (profile.isErrorResilientProfile() && info.isLTPrediction1Present() && (info.ltpData2Present = _in.readBool())) {
            info.getLTPrediction2().decode(_in, info, profile);
         }

         this.icsL.decode(_in, this.commonWindow, conf);
         this.icsR.decode(_in, this.commonWindow, conf);
      }
   }

   public ICStream getLeftChannel() {
      return this.icsL;
   }

   public ICStream getRightChannel() {
      return this.icsR;
   }

   public MSMask getMSMask() {
      return this.msMask;
   }

   public boolean isMSUsed(int off) {
      return this.msUsed[off];
   }

   public boolean isMSMaskPresent() {
      return !this.msMask.equals(MSMask.TYPE_ALL_0);
   }

   public boolean isCommonWindow() {
      return this.commonWindow;
   }

   public static MSMask msMaskFromInt(int i) throws AACException {
      MSMask[] values = MSMask.values();
      if (i >= values.length) {
         throw new AACException("unknown MS mask type");
      } else {
         return values[i];
      }
   }
}
