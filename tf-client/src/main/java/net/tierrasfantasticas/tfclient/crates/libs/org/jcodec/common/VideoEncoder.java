package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.ColorSpace;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.Picture;
import java.nio.ByteBuffer;

public abstract class VideoEncoder {
   public abstract VideoEncoder.EncodedFrame encodeFrame(Picture var1, ByteBuffer var2);

   public abstract ColorSpace[] getSupportedColorSpaces();

   public abstract int estimateBufferSize(Picture var1);

   public static class EncodedFrame {
      private ByteBuffer data;
      private boolean keyFrame;

      public EncodedFrame(ByteBuffer data, boolean keyFrame) {
         this.data = data;
         this.keyFrame = keyFrame;
      }

      public ByteBuffer getData() {
         return this.data;
      }

      public boolean isKeyFrame() {
         return this.keyFrame;
      }
   }
}
