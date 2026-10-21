package net.tierrasfantasticas.tfclient.crates.client.media;

public final class LumaSharpen {
   private static final float MIN_SCALE = 1.05F;
   private static final float MAX_AMOUNT = 0.35F;

   private LumaSharpen() {
   }

   public static float amountFor(float scale) {
      if (!(scale <= 1.05F) && !Float.isNaN(scale)) {
         float amount = (scale - 1.0F) * 0.5F;
         return Math.min(0.35F, amount);
      } else {
         return 0.0F;
      }
   }

   public static void row(byte[] src, byte[] dst, int w, int h, int y, int amount8) {
      int row = y * w;
      int up = (y > 0 ? y - 1 : 0) * w;
      int down = (y < h - 1 ? y + 1 : h - 1) * w;
      int last = w - 1;

      for (int x = 0; x < w; x++) {
         int left = x > 0 ? x - 1 : 0;
         int right = x < last ? x + 1 : last;
         int center = src[row + x];
         int sum = src[up + left]
            + src[up + x]
            + src[up + right]
            + src[row + left]
            + center
            + src[row + right]
            + src[down + left]
            + src[down + x]
            + src[down + right];
         int blurred = (sum + 4) / 9;
         int sharpened = center + (amount8 * (center - blurred) >> 8);
         dst[row + x] = (byte)(sharpened < -128 ? -128 : (sharpened > 127 ? 127 : sharpened));
      }
   }
}
