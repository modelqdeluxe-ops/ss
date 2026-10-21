package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.api.specific;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.api.MediaInfo;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.VideoDecoder;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.ColorSpace;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.Packet;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.Picture;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.Size;

public class GenericAdaptor implements ContainerAdaptor {
   private VideoDecoder decoder;

   public GenericAdaptor(VideoDecoder detect) {
      this.decoder = detect;
   }

   @Override
   public Picture decodeFrame(Packet packet, byte[][] data) {
      return this.decoder.decodeFrame(packet.getData(), data);
   }

   @Override
   public boolean canSeek(Packet data) {
      return true;
   }

   @Override
   public MediaInfo getMediaInfo() {
      return new MediaInfo(new Size(0, 0));
   }

   @Override
   public byte[][] allocatePicture() {
      return Picture.create(1920, 1088, ColorSpace.YUV444).getData();
   }
}
