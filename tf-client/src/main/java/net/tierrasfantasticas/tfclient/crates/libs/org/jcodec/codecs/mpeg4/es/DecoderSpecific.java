package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.mpeg4.es;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.io.NIOUtils;
import java.nio.ByteBuffer;

public class DecoderSpecific extends Descriptor {
   private ByteBuffer data;

   public DecoderSpecific(ByteBuffer data) {
      super(tag(), 0);
      this.data = data;
   }

   @Override
   protected void doWrite(ByteBuffer out) {
      NIOUtils.write(out, this.data);
   }

   public static int tag() {
      return 5;
   }

   public ByteBuffer getData() {
      return this.data;
   }
}
