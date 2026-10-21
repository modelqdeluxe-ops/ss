package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common;

public class Ints {
   public static int checkedCast(long value) {
      int result = (int)value;
      if ((long)result != value) {
         throw new IllegalArgumentException("Out of range: " + value);
      } else {
         return result;
      }
   }
}
