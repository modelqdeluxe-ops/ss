package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.wav;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.AudioCodecMeta;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.AudioFormat;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.Codec;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.Demuxer;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.DemuxerTrack;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.DemuxerTrackMeta;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.TrackType;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.io.NIOUtils;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.io.SeekableByteChannel;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.Packet;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class WavDemuxer implements Demuxer, DemuxerTrack {
   private static final int FRAMES_PER_PKT = 1024;
   private SeekableByteChannel ch;
   private WavHeader header;
   private long dataSize;
   private short frameSize;
   private int frameNo;
   private long pts;

   public WavDemuxer(SeekableByteChannel ch) throws IOException {
      this.ch = ch;
      this.header = WavHeader.readChannel(ch);
      this.dataSize = ch.size() - ch.position();
      this.frameSize = this.header.getFormat().getFrameSize();
   }

   @Override
   public void close() throws IOException {
      this.ch.close();
   }

   @Override
   public Packet nextFrame() throws IOException {
      ByteBuffer data = NIOUtils.fetchFromChannel(this.ch, this.frameSize * 1024);
      if (!data.hasRemaining()) {
         return null;
      } else {
         long oldPts = this.pts;
         int duration = data.remaining() / this.frameSize;
         this.pts += (long)duration;
         return Packet.createPacket(
            data, oldPts, this.header.getFormat().getFrameRate(), (long)(data.remaining() / this.frameSize), (long)(this.frameNo++), Packet.FrameType.KEY, null
         );
      }
   }

   @Override
   public DemuxerTrackMeta getMeta() {
      AudioFormat format = this.header.getFormat();
      AudioCodecMeta audioCodecMeta = AudioCodecMeta.fromAudioFormat(format);
      long totalFrames = this.dataSize / (long)format.getFrameSize();
      return new DemuxerTrackMeta(
         TrackType.AUDIO, Codec.PCM, (double)totalFrames / (double)format.getFrameRate(), null, (int)totalFrames, null, null, audioCodecMeta
      );
   }

   @Override
   public List<? extends DemuxerTrack> getTracks() {
      List<DemuxerTrack> result = new ArrayList<>();
      result.add(this);
      return result;
   }

   @Override
   public List<? extends DemuxerTrack> getVideoTracks() {
      List<DemuxerTrack> result = new ArrayList<>();
      return result;
   }

   @Override
   public List<? extends DemuxerTrack> getAudioTracks() {
      return this.getTracks();
   }
}
