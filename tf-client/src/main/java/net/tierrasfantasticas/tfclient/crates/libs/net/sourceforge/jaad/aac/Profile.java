package net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac;

public final class Profile {
   public static final Profile UNKNOWN = new Profile(-1, "unknown", false);
   public static final Profile AAC_MAIN = new Profile(1, "AAC Main Profile", true);
   public static final Profile AAC_LC = new Profile(2, "AAC Low Complexity", true);
   public static final Profile AAC_SSR = new Profile(3, "AAC Scalable Sample Rate", false);
   public static final Profile AAC_LTP = new Profile(4, "AAC Long Term Prediction", false);
   public static final Profile AAC_SBR = new Profile(5, "AAC SBR", true);
   public static final Profile AAC_SCALABLE = new Profile(6, "Scalable AAC", false);
   public static final Profile TWIN_VQ = new Profile(7, "TwinVQ", false);
   public static final Profile AAC_LD = new Profile(11, "AAC Low Delay", false);
   public static final Profile ER_AAC_LC = new Profile(17, "Error Resilient AAC Low Complexity", true);
   public static final Profile ER_AAC_SSR = new Profile(18, "Error Resilient AAC SSR", false);
   public static final Profile ER_AAC_LTP = new Profile(19, "Error Resilient AAC Long Term Prediction", false);
   public static final Profile ER_AAC_SCALABLE = new Profile(20, "Error Resilient Scalable AAC", false);
   public static final Profile ER_TWIN_VQ = new Profile(21, "Error Resilient TwinVQ", false);
   public static final Profile ER_BSAC = new Profile(22, "Error Resilient BSAC", false);
   public static final Profile ER_AAC_LD = new Profile(23, "Error Resilient AAC Low Delay", false);
   private static final Profile[] ALL = new Profile[]{
      AAC_MAIN,
      AAC_LC,
      AAC_SSR,
      AAC_LTP,
      AAC_SBR,
      AAC_SCALABLE,
      TWIN_VQ,
      null,
      null,
      null,
      AAC_LD,
      null,
      null,
      null,
      null,
      null,
      ER_AAC_LC,
      ER_AAC_SSR,
      ER_AAC_LTP,
      ER_AAC_SCALABLE,
      ER_TWIN_VQ,
      ER_BSAC,
      ER_AAC_LD
   };
   private final int num;
   private final String descr;
   private final boolean supported;

   public static Profile forInt(int i) {
      Profile p;
      if (i > 0 && i <= ALL.length) {
         p = ALL[i - 1];
      } else {
         p = UNKNOWN;
      }

      return p;
   }

   private Profile(int num, String descr, boolean supported) {
      this.num = num;
      this.descr = descr;
      this.supported = supported;
   }

   public int getIndex() {
      return this.num;
   }

   public String getDescription() {
      return this.descr;
   }

   @Override
   public String toString() {
      return this.descr;
   }

   public boolean isDecodingSupported() {
      return this.supported;
   }

   public boolean isErrorResilientProfile() {
      return this.num > 16;
   }
}
