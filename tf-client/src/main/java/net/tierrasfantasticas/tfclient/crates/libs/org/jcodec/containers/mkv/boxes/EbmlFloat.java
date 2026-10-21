package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mkv.boxes;

import java.nio.Buffer;
import java.nio.ByteBuffer;

public class EbmlFloat extends EbmlBin {
   public EbmlFloat(byte[] id) {
      super(id);
   }

   public void setDouble(double value) {
      if (value < Float.MAX_VALUE) {
         ByteBuffer bb = ByteBuffer.allocate(4);
         bb.putFloat((float)value);
         ((Buffer)bb).flip();
         this.data = bb;
      } else if (value < Double.MAX_VALUE) {
         ByteBuffer bb = ByteBuffer.allocate(8);
         bb.putDouble(value);
         ((Buffer)bb).flip();
         this.data = bb;
      }
   }

   public double getDouble() {
      return this.data.limit() == 4 ? (double)this.data.duplicate().getFloat() : this.data.duplicate().getDouble();
   }
}
