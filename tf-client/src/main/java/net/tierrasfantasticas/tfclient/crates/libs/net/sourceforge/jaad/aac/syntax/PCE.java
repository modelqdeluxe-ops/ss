package net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.syntax;

import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.AACException;
import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.Profile;
import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.SampleFrequency;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.logging.Logger;

public class PCE extends Element {
   private static final int MAX_FRONT_CHANNEL_ELEMENTS = 16;
   private static final int MAX_SIDE_CHANNEL_ELEMENTS = 16;
   private static final int MAX_BACK_CHANNEL_ELEMENTS = 16;
   private static final int MAX_LFE_CHANNEL_ELEMENTS = 4;
   private static final int MAX_ASSOC_DATA_ELEMENTS = 8;
   private static final int MAX_VALID_CC_ELEMENTS = 16;
   private Profile profile;
   private SampleFrequency sampleFrequency;
   private int frontChannelElementsCount;
   private int sideChannelElementsCount;
   private int backChannelElementsCount;
   private int lfeChannelElementsCount;
   private int assocDataElementsCount;
   private int validCCElementsCount;
   private boolean monoMixdown;
   private boolean stereoMixdown;
   private boolean matrixMixdownIDXPresent;
   private int monoMixdownElementNumber;
   private int stereoMixdownElementNumber;
   private int matrixMixdownIDX;
   private boolean pseudoSurround;
   private final PCE.TaggedElement[] frontElements = new PCE.TaggedElement[16];
   private final PCE.TaggedElement[] sideElements = new PCE.TaggedElement[16];
   private final PCE.TaggedElement[] backElements = new PCE.TaggedElement[16];
   private final int[] lfeElementTags = new int[4];
   private final int[] assocDataElementTags = new int[8];
   private final PCE.CCE[] ccElements = new PCE.CCE[16];
   private byte[] commentFieldData;

   public PCE() {
      this.sampleFrequency = SampleFrequency.SAMPLE_FREQUENCY_NONE;
   }

   public void decode(IBitStream _in) throws AACException {
      this.readElementInstanceTag(_in);
      this.profile = Profile.forInt(_in.readBits(2));
      this.sampleFrequency = SampleFrequency.forInt(_in.readBits(4));
      this.frontChannelElementsCount = _in.readBits(4);
      this.sideChannelElementsCount = _in.readBits(4);
      this.backChannelElementsCount = _in.readBits(4);
      this.lfeChannelElementsCount = _in.readBits(2);
      this.assocDataElementsCount = _in.readBits(3);
      this.validCCElementsCount = _in.readBits(4);
      if (this.monoMixdown = _in.readBool()) {
         Logger.warn("mono mixdown present, but not yet supported");
         this.monoMixdownElementNumber = _in.readBits(4);
      }

      if (this.stereoMixdown = _in.readBool()) {
         Logger.warn("stereo mixdown present, but not yet supported");
         this.stereoMixdownElementNumber = _in.readBits(4);
      }

      if (this.matrixMixdownIDXPresent = _in.readBool()) {
         Logger.warn("matrix mixdown present, but not yet supported");
         this.matrixMixdownIDX = _in.readBits(2);
         this.pseudoSurround = _in.readBool();
      }

      this.readTaggedElementArray(this.frontElements, _in, this.frontChannelElementsCount);
      this.readTaggedElementArray(this.sideElements, _in, this.sideChannelElementsCount);
      this.readTaggedElementArray(this.backElements, _in, this.backChannelElementsCount);

      for (int i = 0; i < this.lfeChannelElementsCount; i++) {
         this.lfeElementTags[i] = _in.readBits(4);
      }

      for (int var4 = 0; var4 < this.assocDataElementsCount; var4++) {
         this.assocDataElementTags[var4] = _in.readBits(4);
      }

      for (int var5 = 0; var5 < this.validCCElementsCount; var5++) {
         this.ccElements[var5] = new PCE.CCE(_in.readBool(), _in.readBits(4));
      }

      _in.byteAlign();
      int commentFieldBytes = _in.readBits(8);
      this.commentFieldData = new byte[commentFieldBytes];

      for (int var6 = 0; var6 < commentFieldBytes; var6++) {
         this.commentFieldData[var6] = (byte)_in.readBits(8);
      }
   }

   private void readTaggedElementArray(PCE.TaggedElement[] te, IBitStream _in, int len) throws AACException {
      for (int i = 0; i < len; i++) {
         te[i] = new PCE.TaggedElement(_in.readBool(), _in.readBits(4));
      }
   }

   public Profile getProfile() {
      return this.profile;
   }

   public SampleFrequency getSampleFrequency() {
      return this.sampleFrequency;
   }

   public int getChannelCount() {
      return this.frontChannelElementsCount
         + this.sideChannelElementsCount
         + this.backChannelElementsCount
         + this.lfeChannelElementsCount
         + this.assocDataElementsCount;
   }

   public static class CCE {
      private final boolean isIndSW;
      private final int tag;

      public CCE(boolean isIndSW, int tag) {
         this.isIndSW = isIndSW;
         this.tag = tag;
      }

      public boolean isIsIndSW() {
         return this.isIndSW;
      }

      public int getTag() {
         return this.tag;
      }
   }

   public static class TaggedElement {
      private final boolean isCPE;
      private final int tag;

      public TaggedElement(boolean isCPE, int tag) {
         this.isCPE = isCPE;
         this.tag = tag;
      }

      public boolean isIsCPE() {
         return this.isCPE;
      }

      public int getTag() {
         return this.tag;
      }
   }
}
