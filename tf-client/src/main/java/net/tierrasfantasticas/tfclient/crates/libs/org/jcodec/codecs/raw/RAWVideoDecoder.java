package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.raw;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.VideoCodecMeta;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.VideoDecoder;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.ColorSpace;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.Picture;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.Size;
import java.nio.ByteBuffer;

public class RAWVideoDecoder extends VideoDecoder {
   private int width;
   private int height;

   public RAWVideoDecoder(int width, int height) {
      this.width = width;
      this.height = height;
   }

   @Override
   public Picture decodeFrame(ByteBuffer data, byte[][] buffer) {
      Picture create = Picture.createPicture(this.width, this.height, buffer, ColorSpace.YUV420);
      ByteBuffer pix = data.duplicate();
      this.copy(pix, create.getPlaneData(0), this.width * this.height);
      this.copy(pix, create.getPlaneData(1), this.width * this.height / 4);
      this.copy(pix, create.getPlaneData(2), this.width * this.height / 4);
      return create;
   }

   void copy(ByteBuffer b, byte[] ii, int size) {
      for (int i = 0; b.hasRemaining() && i < size; i++) {
         ii[i] = (byte)((b.get() & 255) - 128);
      }
   }

   @Override
   public VideoCodecMeta getCodecMeta(ByteBuffer data) {
      return VideoCodecMeta.createSimpleVideoCodecMeta(new Size(this.width, this.height), ColorSpace.YUV420);
   }
}
