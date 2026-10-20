package net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.syntax;

import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.AACDecoderConfig;
import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.AACException;
import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.Profile;
import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.SampleFrequency;
import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.filterbank.FilterBank;
import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.tools.ICPrediction;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.platform.Platform;

public class ICSInfo implements SyntaxConstants, ScaleFactorBands {
   public static final int WINDOW_SHAPE_SINE = 0;
   public static final int WINDOW_SHAPE_KAISER = 1;
   public static final int PREVIOUS = 0;
   public static final int CURRENT = 1;
   private final int frameLength;
   private ICSInfo.WindowSequence windowSequence;
   private int[] windowShape;
   private int maxSFB;
   private boolean predictionDataPresent;
   private ICPrediction icPredict;
   boolean ltpData1Present;
   boolean ltpData2Present;
   private ICSInfo.LTPrediction ltPredict1;
   private ICSInfo.LTPrediction ltPredict2;
   private int windowCount;
   private int windowGroupCount;
   private int[] windowGroupLength;
   private int swbCount;
   private int[] swbOffsets;

   public static ICSInfo.WindowSequence windowSequenceFromInt(int i) throws AACException {
      ICSInfo.WindowSequence[] values = ICSInfo.WindowSequence.values();
      if (i >= values.length) {
         throw new AACException("unknown window sequence type");
      } else {
         return values[i];
      }
   }

   public ICSInfo(int frameLength) {
      this.frameLength = frameLength;
      this.windowShape = new int[2];
      this.windowSequence = ICSInfo.WindowSequence.ONLY_LONG_SEQUENCE;
      this.windowGroupLength = new int[8];
      this.ltpData1Present = false;
      this.ltpData2Present = false;
   }

   public void decode(IBitStream _in, AACDecoderConfig conf, boolean commonWindow) throws AACException {
      SampleFrequency sf = conf.getSampleFrequency();
      if (sf.equals(SampleFrequency.SAMPLE_FREQUENCY_NONE)) {
         throw new AACException("invalid sample frequency");
      } else {
         _in.skipBit();
         this.windowSequence = windowSequenceFromInt(_in.readBits(2));
         this.windowShape[0] = this.windowShape[1];
         this.windowShape[1] = _in.readBit();
         this.windowGroupCount = 1;
         this.windowGroupLength[0] = 1;
         if (this.windowSequence.equals(ICSInfo.WindowSequence.EIGHT_SHORT_SEQUENCE)) {
            this.maxSFB = _in.readBits(4);

            for (int i = 0; i < 7; i++) {
               if (_in.readBool()) {
                  this.windowGroupLength[this.windowGroupCount - 1]++;
               } else {
                  this.windowGroupCount++;
                  this.windowGroupLength[this.windowGroupCount - 1] = 1;
               }
            }

            this.windowCount = 8;
            this.swbOffsets = SWB_OFFSET_SHORT_WINDOW[sf.getIndex()];
            this.swbCount = SWB_SHORT_WINDOW_COUNT[sf.getIndex()];
            this.predictionDataPresent = false;
         } else {
            this.maxSFB = _in.readBits(6);
            this.windowCount = 1;
            this.swbOffsets = SWB_OFFSET_LONG_WINDOW[sf.getIndex()];
            this.swbCount = SWB_LONG_WINDOW_COUNT[sf.getIndex()];
            this.predictionDataPresent = _in.readBool();
            if (this.predictionDataPresent) {
               this.readPredictionData(_in, conf.getProfile(), sf, commonWindow);
            }
         }
      }
   }

   private void readPredictionData(IBitStream _in, Profile profile, SampleFrequency sf, boolean commonWindow) throws AACException {
      if (Profile.AAC_MAIN == profile) {
         if (this.icPredict == null) {
            this.icPredict = new ICPrediction();
         }

         this.icPredict.decode(_in, this.maxSFB, sf);
      } else if (Profile.AAC_LTP == profile) {
         if (this.ltpData1Present = _in.readBool()) {
            if (this.ltPredict1 == null) {
               this.ltPredict1 = new ICSInfo.LTPrediction(this.frameLength);
            }

            this.ltPredict1.decode(_in, this, profile);
         }

         if (commonWindow && (this.ltpData2Present = _in.readBool())) {
            if (this.ltPredict2 == null) {
               this.ltPredict2 = new ICSInfo.LTPrediction(this.frameLength);
            }

            this.ltPredict2.decode(_in, this, profile);
         }
      } else {
         if (Profile.ER_AAC_LTP != profile) {
            throw new AACException("unexpected profile for LTP: " + profile);
         }

         if (!commonWindow && (this.ltpData1Present = _in.readBool())) {
            if (this.ltPredict1 == null) {
               this.ltPredict1 = new ICSInfo.LTPrediction(this.frameLength);
            }

            this.ltPredict1.decode(_in, this, profile);
         }
      }
   }

   public int getMaxSFB() {
      return this.maxSFB;
   }

   public int getSWBCount() {
      return this.swbCount;
   }

   public int[] getSWBOffsets() {
      return this.swbOffsets;
   }

   public int getSWBOffsetMax() {
      return this.swbOffsets[this.swbCount];
   }

   public int getWindowCount() {
      return this.windowCount;
   }

   public int getWindowGroupCount() {
      return this.windowGroupCount;
   }

   public int getWindowGroupLength(int g) {
      return this.windowGroupLength[g];
   }

   public ICSInfo.WindowSequence getWindowSequence() {
      return this.windowSequence;
   }

   public boolean isEightShortFrame() {
      return this.windowSequence.equals(ICSInfo.WindowSequence.EIGHT_SHORT_SEQUENCE);
   }

   public int getWindowShape(int index) {
      return this.windowShape[index];
   }

   public boolean isICPredictionPresent() {
      return this.predictionDataPresent;
   }

   public ICPrediction getICPrediction() {
      return this.icPredict;
   }

   public boolean isLTPrediction1Present() {
      return this.ltpData1Present;
   }

   public ICSInfo.LTPrediction getLTPrediction1() {
      return this.ltPredict1;
   }

   public boolean isLTPrediction2Present() {
      return this.ltpData2Present;
   }

   public ICSInfo.LTPrediction getLTPrediction2() {
      return this.ltPredict2;
   }

   public void unsetPredictionSFB(int sfb) {
      if (this.predictionDataPresent) {
         this.icPredict.setPredictionUnused(sfb);
      }

      if (this.ltpData1Present) {
         this.ltPredict1.setPredictionUnused(sfb);
      }

      if (this.ltpData2Present) {
         this.ltPredict2.setPredictionUnused(sfb);
      }
   }

   public void setData(ICSInfo info) {
      this.windowSequence = ICSInfo.WindowSequence.valueOf(info.windowSequence.name());
      this.windowShape[0] = this.windowShape[1];
      this.windowShape[1] = info.windowShape[1];
      this.maxSFB = info.maxSFB;
      this.predictionDataPresent = info.predictionDataPresent;
      if (this.predictionDataPresent) {
         this.icPredict = info.icPredict;
      }

      this.ltpData1Present = info.ltpData1Present;
      if (this.ltpData1Present) {
         this.ltPredict1.copy(info.ltPredict1);
         this.ltPredict2.copy(info.ltPredict2);
      }

      this.windowCount = info.windowCount;
      this.windowGroupCount = info.windowGroupCount;
      this.windowGroupLength = Platform.copyOfInt(info.windowGroupLength, info.windowGroupLength.length);
      this.swbCount = info.swbCount;
      this.swbOffsets = Platform.copyOfInt(info.swbOffsets, info.swbOffsets.length);
   }

   public static class LTPrediction implements SyntaxConstants {
      private static final float[] CODEBOOK = new float[]{0.570829F, 0.696616F, 0.813004F, 0.911304F, 0.9849F, 1.067894F, 1.194601F, 1.369533F};
      private final int frameLength;
      private final int[] states;
      private int coef;
      private int lag;
      private int lastBand;
      private boolean lagUpdate;
      private boolean[] shortUsed;
      private boolean[] shortLagPresent;
      private boolean[] longUsed;
      private int[] shortLag;

      public LTPrediction(int frameLength) {
         this.frameLength = frameLength;
         this.states = new int[4 * frameLength];
      }

      public void decode(IBitStream _in, ICSInfo info, Profile profile) throws AACException {
         this.lag = 0;
         if (profile.equals(Profile.AAC_LD)) {
            this.lagUpdate = _in.readBool();
            if (this.lagUpdate) {
               this.lag = _in.readBits(10);
            }
         } else {
            this.lag = _in.readBits(11);
         }

         if (this.lag > this.frameLength << 1) {
            throw new AACException("LTP lag too large: " + this.lag);
         } else {
            this.coef = _in.readBits(3);
            int windowCount = info.getWindowCount();
            if (info.isEightShortFrame()) {
               this.shortUsed = new boolean[windowCount];
               this.shortLagPresent = new boolean[windowCount];
               this.shortLag = new int[windowCount];

               for (int w = 0; w < windowCount; w++) {
                  if (this.shortUsed[w] = _in.readBool()) {
                     this.shortLagPresent[w] = _in.readBool();
                     if (this.shortLagPresent[w]) {
                        this.shortLag[w] = _in.readBits(4);
                     }
                  }
               }
            } else {
               this.lastBand = Math.min(info.getMaxSFB(), 40);
               this.longUsed = new boolean[this.lastBand];

               for (int i = 0; i < this.lastBand; i++) {
                  this.longUsed[i] = _in.readBool();
               }
            }
         }
      }

      public void setPredictionUnused(int sfb) {
         if (this.longUsed != null) {
            this.longUsed[sfb] = false;
         }
      }

      public void process(ICStream ics, float[] data, FilterBank filterBank, SampleFrequency sf) {
         ICSInfo info = ics.getInfo();
         if (!info.isEightShortFrame()) {
            int samples = this.frameLength << 1;
            float[] _in = new float[2048];
            float[] out = new float[2048];

            for (int i = 0; i < samples; i++) {
               _in[i] = (float)this.states[samples + i - this.lag] * CODEBOOK[this.coef];
            }

            filterBank.processLTP(info.getWindowSequence(), info.getWindowShape(1), info.getWindowShape(0), _in, out);
            if (ics.isTNSDataPresent()) {
               ics.getTNS().process(ics, out, sf, true);
            }

            int[] swbOffsets = info.getSWBOffsets();
            int swbOffsetMax = info.getSWBOffsetMax();

            for (int sfb = 0; sfb < this.lastBand; sfb++) {
               if (this.longUsed[sfb]) {
                  int low = swbOffsets[sfb];
                  int high = Math.min(swbOffsets[sfb + 1], swbOffsetMax);

                  for (int bin = low; bin < high; bin++) {
                     data[bin] += out[bin];
                  }
               }
            }
         }
      }

      public void updateState(float[] time, float[] overlap, Profile profile) {
         if (profile.equals(Profile.AAC_LD)) {
            for (int i = 0; i < this.frameLength; i++) {
               this.states[i] = this.states[i + this.frameLength];
               this.states[this.frameLength + i] = this.states[i + this.frameLength * 2];
               this.states[this.frameLength * 2 + i] = Math.round(time[i]);
               this.states[this.frameLength * 3 + i] = Math.round(overlap[i]);
            }
         } else {
            for (int i = 0; i < this.frameLength; i++) {
               this.states[i] = this.states[i + this.frameLength];
               this.states[this.frameLength + i] = Math.round(time[i]);
               this.states[this.frameLength * 2 + i] = Math.round(overlap[i]);
            }
         }
      }

      public static boolean isLTPProfile(Profile profile) {
         return profile.equals(Profile.AAC_LTP) || profile.equals(Profile.ER_AAC_LTP) || profile.equals(Profile.AAC_LD);
      }

      public void copy(ICSInfo.LTPrediction ltp) {
         System.arraycopy(ltp.states, 0, this.states, 0, this.states.length);
         this.coef = ltp.coef;
         this.lag = ltp.lag;
         this.lastBand = ltp.lastBand;
         this.lagUpdate = ltp.lagUpdate;
         this.shortUsed = Platform.copyOfBool(ltp.shortUsed, ltp.shortUsed.length);
         this.shortLagPresent = Platform.copyOfBool(ltp.shortLagPresent, ltp.shortLagPresent.length);
         this.shortLag = Platform.copyOfInt(ltp.shortLag, ltp.shortLag.length);
         this.longUsed = Platform.copyOfBool(ltp.longUsed, ltp.longUsed.length);
      }
   }

   public static enum WindowSequence {
      ONLY_LONG_SEQUENCE,
      LONG_START_SEQUENCE,
      EIGHT_SHORT_SEQUENCE,
      LONG_STOP_SEQUENCE;
   }
}
