package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mkv.boxes;

import java.nio.Buffer;
import java.nio.ByteBuffer;

public class EbmlUlong extends EbmlBin {
   public EbmlUlong(byte[] id) {
      super(id);
      this.data = ByteBuffer.allocate(8);
   }

   public static EbmlUlong createEbmlUlong(byte[] id, long value) {
      EbmlUlong e = new EbmlUlong(id);
      e.setUlong(value);
      return e;
   }

   public void setUlong(long value) {
      this.data.putLong(value);
      ((Buffer)this.data).flip();
   }

   public long getUlong() {
      return this.data.duplicate().getLong();
   }
}
