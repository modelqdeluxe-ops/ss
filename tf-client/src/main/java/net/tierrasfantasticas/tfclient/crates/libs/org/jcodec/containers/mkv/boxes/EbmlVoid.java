package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mkv.boxes;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.io.SeekableByteChannel;
import java.io.IOException;
import java.nio.ByteBuffer;

public class EbmlVoid extends EbmlBase {
   public EbmlVoid(byte[] id) {
      super(id);
   }

   @Override
   public ByteBuffer getData() {
      return null;
   }

   public void skip(SeekableByteChannel is) throws IOException {
      is.setPosition(this.dataOffset + (long)this.dataLen);
   }
}
