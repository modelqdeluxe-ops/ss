package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.imgseq;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.AudioCodecMeta;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.Codec;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.Muxer;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.MuxerTrack;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.VideoCodecMeta;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.io.NIOUtils;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.logging.Logger;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.Packet;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.tools.MainUtils;
import java.io.IOException;

public class ImageSequenceMuxer implements Muxer, MuxerTrack {
   private String fileNamePattern;
   private int frameNo;

   public ImageSequenceMuxer(String fileNamePattern) {
      this.fileNamePattern = fileNamePattern;
   }

   @Override
   public void addFrame(Packet packet) throws IOException {
      NIOUtils.writeTo(packet.getData(), MainUtils.tildeExpand(String.format(this.fileNamePattern, this.frameNo++)));
   }

   @Override
   public MuxerTrack addVideoTrack(Codec codec, VideoCodecMeta meta) {
      return this;
   }

   @Override
   public MuxerTrack addAudioTrack(Codec codec, AudioCodecMeta meta) {
      Logger.warn("Audio is not supported for image sequence muxer.");
      return null;
   }

   @Override
   public void finish() throws IOException {
   }
}
