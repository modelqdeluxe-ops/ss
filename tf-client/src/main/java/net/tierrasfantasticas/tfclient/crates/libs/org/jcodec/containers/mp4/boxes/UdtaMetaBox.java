package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes;

import java.nio.ByteBuffer;

public class UdtaMetaBox extends MetaBox {
   public UdtaMetaBox(Header atom) {
      super(atom);
   }

   public static UdtaMetaBox createUdtaMetaBox() {
      return new UdtaMetaBox(Header.createHeader(fourcc(), 0L));
   }

   @Override
   public void parse(ByteBuffer input) {
      input.getInt();
      super.parse(input);
   }

   @Override
   protected void doWrite(ByteBuffer out) {
      out.putInt(0);
      super.doWrite(out);
   }
}
