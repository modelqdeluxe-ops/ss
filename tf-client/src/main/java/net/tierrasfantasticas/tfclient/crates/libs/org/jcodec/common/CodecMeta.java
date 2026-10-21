package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common;

import java.nio.ByteBuffer;

public class CodecMeta {
   private String fourcc;
   private ByteBuffer codecPrivate;

   public CodecMeta(String fourcc, ByteBuffer codecPrivate) {
      this.fourcc = fourcc;
      this.codecPrivate = codecPrivate;
   }

   public String getFourcc() {
      return this.fourcc;
   }

   public ByteBuffer getCodecPrivate() {
      return this.codecPrivate;
   }
}
