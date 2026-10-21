package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.h264.io.model;

public class AspectRatio {
   public static final AspectRatio Extended_SAR = new AspectRatio(255);
   private int value;

   private AspectRatio(int value) {
      this.value = value;
   }

   public static AspectRatio fromValue(int value) {
      return value == Extended_SAR.value ? Extended_SAR : new AspectRatio(value);
   }

   public int getValue() {
      return this.value;
   }
}
