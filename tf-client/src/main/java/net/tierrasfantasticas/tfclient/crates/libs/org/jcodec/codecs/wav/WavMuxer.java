package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.wav;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.AudioCodecMeta;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.AudioFormat;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.Codec;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.Muxer;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.MuxerTrack;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.VideoCodecMeta;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.io.NIOUtils;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.io.SeekableByteChannel;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.Packet;
import java.io.IOException;

public class WavMuxer implements Muxer, MuxerTrack {
   protected SeekableByteChannel out;
   protected WavHeader header;
   protected int written;
   private AudioFormat format;

   public WavMuxer(SeekableByteChannel out) throws IOException {
      this.out = out;
   }

   @Override
   public void addFrame(Packet outPacket) throws IOException {
      this.written = this.written + this.out.write(outPacket.getData());
   }

   public void close() throws IOException {
      this.out.setPosition(0L);
      WavHeader.createWavHeader(this.format, this.format.bytesToFrames(this.written)).write(this.out);
      NIOUtils.closeQuietly(this.out);
   }

   @Override
   public MuxerTrack addVideoTrack(Codec codec, VideoCodecMeta meta) {
      return null;
   }

   @Override
   public MuxerTrack addAudioTrack(Codec codec, AudioCodecMeta meta) {
      this.header = WavHeader.createWavHeader(meta.getFormat(), 0);
      this.format = meta.getFormat();

      try {
         this.header.write(this.out);
         return this;
      } catch (IOException var4) {
         throw new RuntimeException(var4);
      }
   }

   @Override
   public void finish() throws IOException {
   }
}
