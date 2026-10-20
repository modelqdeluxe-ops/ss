package net.tierrasfantasticas.tfclient.crates.client.media;

public final class FrameScaler {
   private static final int WEIGHT_ONE = 1024;
   private static final float MIN_SCALE = 1.2F;
   private static final int MAX_OUTPUT_PIXELS = 2500000;

   private FrameScaler() {
   }

   public static boolean worthIt(int srcW, int srcH, int dstW, int dstH) {
      if (srcW <= 0 || srcH <= 0 || dstW <= 0 || dstH <= 0) {
         return false;
      } else if (dstW >= srcW && dstH >= srcH) {
         if ((long)dstW * (long)dstH > 2500000L) {
            return false;
         } else {
            float scale = Math.min((float)dstW / (float)srcW, (float)dstH / (float)srcH);
            return scale >= 1.2F;
         }
      } else {
         return false;
      }
   }

   public static FrameScaler.Taps taps(int srcSize, int dstSize) {
      int[] index = new int[dstSize * 4];
      int[] weight = new int[dstSize * 4];
      double ratio = (double)srcSize / (double)dstSize;

      for (int out = 0; out < dstSize; out++) {
         double center = ((double)out + 0.5) * ratio - 0.5;
         int base = (int)Math.floor(center);
         double frac = center - (double)base;
         int sum = 0;

         for (int tap = 0; tap < 4; tap++) {
            int src = base - 1 + tap;
            index[out * 4 + tap] = src < 0 ? 0 : (src >= srcSize ? srcSize - 1 : src);
            int w = (int)Math.round(catmullRom(frac - (double)(tap - 1)) * 1024.0);
            weight[out * 4 + tap] = w;
            sum += w;
         }

         if (sum != 1024) {
            int best = 0;

            for (int tap = 1; tap < 4; tap++) {
               if (weight[out * 4 + tap] > weight[out * 4 + best]) {
                  best = tap;
               }
            }

            weight[out * 4 + best] = weight[out * 4 + best] + (1024 - sum);
         }
      }

      FrameScaler.Taps t = new FrameScaler.Taps(index, weight, dstSize);
      t.srcSize = srcSize;
      return t;
   }

   private static double catmullRom(double x) {
      double t = Math.abs(x);
      if (t <= 1.0) {
         return 1.5 * t * t * t - 2.5 * t * t + 1.0;
      } else {
         return t < 2.0 ? -0.5 * t * t * t + 2.5 * t * t - 4.0 * t + 2.0 : 0.0;
      }
   }

   public static int midSize(int dstW, int srcH) {
      return dstW * srcH * 3;
   }

   public static void rowHorizontal(int[] src, int srcW, short[] mid, int dstW, int row, FrameScaler.Taps taps) {
      int srcRow = row * srcW;
      int midRow = row * dstW * 3;

      for (int out = 0; out < dstW; out++) {
         int at = out * 4;
         int r = 0;
         int g = 0;
         int b = 0;

         for (int tap = 0; tap < 4; tap++) {
            int px = src[srcRow + taps.index[at + tap]];
            int w = taps.weight[at + tap];
            r += (px & 0xFF) * w;
            g += (px >> 8 & 0xFF) * w;
            b += (px >> 16 & 0xFF) * w;
         }

         int out3 = midRow + out * 3;
         mid[out3] = (short)(r + 8 >> 4);
         mid[out3 + 1] = (short)(g + 8 >> 4);
         mid[out3 + 2] = (short)(b + 8 >> 4);
      }
   }

   public static void rowVertical(short[] mid, int width, int[] dst, int row, FrameScaler.Taps taps) {
      int at = row * 4;
      int stride = width * 3;
      int r0 = taps.index[at] * stride;
      int r1 = taps.index[at + 1] * stride;
      int r2 = taps.index[at + 2] * stride;
      int r3 = taps.index[at + 3] * stride;
      int w0 = taps.weight[at];
      int w1 = taps.weight[at + 1];
      int w2 = taps.weight[at + 2];
      int w3 = taps.weight[at + 3];
      int dstRow = row * width;

      for (int x = 0; x < width; x++) {
         int at3 = x * 3;
         int i0 = r0 + at3;
         int i1 = r1 + at3;
         int i2 = r2 + at3;
         int i3 = r3 + at3;
         int red = mid[i0] * w0 + mid[i1] * w1 + mid[i2] * w2 + mid[i3] * w3;
         int green = mid[i0 + 1] * w0 + mid[i1 + 1] * w1 + mid[i2 + 1] * w2 + mid[i3 + 1] * w3;
         int blue = mid[i0 + 2] * w0 + mid[i1 + 2] * w1 + mid[i2 + 2] * w2 + mid[i3 + 2] * w3;
         dst[dstRow + x] = 0xFF000000 | clamp(blue + 32768 >> 16) << 16 | clamp(green + 32768 >> 16) << 8 | clamp(red + 32768 >> 16);
      }
   }

   private static int clamp(int v) {
      return v < 0 ? 0 : (v > 255 ? 255 : v);
   }

   public static final class Taps {
      final int[] index;
      final int[] weight;
      final int outSize;
      int srcSize;

      private Taps(int[] index, int[] weight, int outSize) {
         this.index = index;
         this.weight = weight;
         this.outSize = outSize;
      }

      public boolean matches(int srcSize, int dstSize) {
         return this.outSize == dstSize && this.srcSize == srcSize;
      }
   }
}
