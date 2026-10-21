package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.h264.io.model;

import java.nio.ByteBuffer;

public class NALUnit {
   public NALUnitType type;
   public int nal_ref_idc;

   public NALUnit(NALUnitType type, int nal_ref_idc) {
      this.type = type;
      this.nal_ref_idc = nal_ref_idc;
   }

   public static NALUnit read(ByteBuffer _in) {
      int nalu = _in.get() & 255;
      int nal_ref_idc = nalu >> 5 & 3;
      int nb = nalu & 31;
      NALUnitType type = NALUnitType.fromValue(nb);
      return new NALUnit(type, nal_ref_idc);
   }

   public void write(ByteBuffer out) {
      int nalu = this.type.getValue() | this.nal_ref_idc << 5;
      out.put((byte)nalu);
   }
}
