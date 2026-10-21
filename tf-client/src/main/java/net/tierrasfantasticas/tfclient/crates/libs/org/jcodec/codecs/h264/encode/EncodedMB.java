package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.h264.encode;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.h264.io.model.MBType;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.ColorSpace;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.Picture;

public class EncodedMB {
   private Picture pixels = Picture.create(16, 16, ColorSpace.YUV420J);
   private MBType type;
   private int qp;
   private int[] nc = new int[16];
   private int[] mx = new int[16];
   private int[] my = new int[16];

   public Picture getPixels() {
      return this.pixels;
   }

   public MBType getType() {
      return this.type;
   }

   public void setType(MBType type) {
      this.type = type;
   }

   public int getQp() {
      return this.qp;
   }

   public void setQp(int qp) {
      this.qp = qp;
   }

   public int[] getNc() {
      return this.nc;
   }

   public int[] getMx() {
      return this.mx;
   }

   public int[] getMy() {
      return this.my;
   }
}
