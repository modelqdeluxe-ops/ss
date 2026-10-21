package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.raw;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.AudioCodecMeta;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.Codec;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.Muxer;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.MuxerTrack;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.VideoCodecMeta;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.io.SeekableByteChannel;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.Packet;
import java.io.IOException;

public class RawMuxer implements Muxer, MuxerTrack {
   private SeekableByteChannel ch;
   private boolean hasVideo;
   private boolean hasAudio;

   public RawMuxer(SeekableByteChannel destStream) {
      this.ch = destStream;
   }

   @Override
   public MuxerTrack addVideoTrack(Codec codec, VideoCodecMeta meta) {
      if (this.hasAudio) {
         throw new RuntimeException("Raw muxer supports either video or audio track but not both.");
      } else {
         this.hasVideo = true;
         return this;
      }
   }

   @Override
   public MuxerTrack addAudioTrack(Codec codec, AudioCodecMeta meta) {
      if (this.hasVideo) {
         throw new RuntimeException("Raw muxer supports either video or audio track but not both.");
      } else {
         this.hasAudio = true;
         return this;
      }
   }

   @Override
   public void finish() throws IOException {
   }

   @Override
   public void addFrame(Packet outPacket) throws IOException {
      this.ch.write(outPacket.getData().duplicate());
   }
}
