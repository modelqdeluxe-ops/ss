package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.h264;

public class H264Utils2 {
   public static int golomb2Signed(int val) {
      int sign = ((val & 1) << 1) - 1;
      return ((val >> 1) + (val & 1)) * sign;
   }
}
