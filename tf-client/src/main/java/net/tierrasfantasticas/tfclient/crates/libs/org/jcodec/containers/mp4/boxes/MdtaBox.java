package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.io.NIOUtils;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.platform.Platform;
import java.nio.ByteBuffer;

public class MdtaBox extends Box {
   private static final String FOURCC = "mdta";
   private String key;

   public MdtaBox(Header header) {
      super(header);
   }

   public static MdtaBox createMdtaBox(String key) {
      MdtaBox box = new MdtaBox(Header.createHeader("mdta", 0L));
      box.key = key;
      return box;
   }

   @Override
   public void parse(ByteBuffer buf) {
      this.key = Platform.stringFromBytes(NIOUtils.toArray(NIOUtils.readBuf(buf)));
   }

   public String getKey() {
      return this.key;
   }

   @Override
   protected void doWrite(ByteBuffer out) {
      out.put(this.key.getBytes());
   }

   @Override
   public int estimateSize() {
      return this.key.getBytes().length;
   }

   public static String fourcc() {
      return "mdta";
   }
}
