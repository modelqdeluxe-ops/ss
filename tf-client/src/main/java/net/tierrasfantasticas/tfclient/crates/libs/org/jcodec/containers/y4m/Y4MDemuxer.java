package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.y4m;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.Codec;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.Demuxer;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.DemuxerTrack;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.DemuxerTrackMeta;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.StringUtils;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.TrackType;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.VideoCodecMeta;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.io.NIOUtils;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.io.SeekableByteChannel;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.ColorSpace;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.Packet;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.Rational;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.Size;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.platform.Platform;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class Y4MDemuxer implements DemuxerTrack, Demuxer {
   private SeekableByteChannel is;
   private int width;
   private int height;
   private String invalidFormat;
   private Rational fps;
   private int bufSize;
   private int frameNum;
   private int totalFrames;
   private int totalDuration;

   public Y4MDemuxer(SeekableByteChannel _is) throws IOException {
      this.is = _is;
      ByteBuffer buf = NIOUtils.fetchFromChannel(this.is, 2048);
      String[] header = StringUtils.splitC(readLine(buf), ' ');
      if (!"YUV4MPEG2".equals(header[0])) {
         this.invalidFormat = "Not yuv4mpeg stream";
      } else {
         String chroma = find(header, 'C');
         if (chroma != null && !chroma.startsWith("420")) {
            this.invalidFormat = "Only yuv420p is supported";
         } else {
            this.width = Integer.parseInt(find(header, 'W'));
            this.height = Integer.parseInt(find(header, 'H'));
            String fpsStr = find(header, 'F');
            if (fpsStr != null) {
               String[] numden = StringUtils.splitC(fpsStr, ':');
               this.fps = new Rational(Integer.parseInt(numden[0]), Integer.parseInt(numden[1]));
            }

            this.is.setPosition((long)buf.position());
            this.bufSize = this.width * this.height;
            this.bufSize = this.bufSize + this.bufSize / 2;
            long fileSize = this.is.size();
            this.totalFrames = (int)(fileSize / (long)(this.bufSize + 7));
            this.totalDuration = this.totalFrames * this.fps.getDen() / this.fps.getNum();
         }
      }
   }

   @Override
   public Packet nextFrame() throws IOException {
      if (this.invalidFormat != null) {
         throw new RuntimeException("Invalid input: " + this.invalidFormat);
      } else {
         ByteBuffer buf = NIOUtils.fetchFromChannel(this.is, 2048);
         String frame = readLine(buf);
         if (frame != null && frame.startsWith("FRAME")) {
            this.is.setPosition(this.is.position() - (long)buf.remaining());
            ByteBuffer pix = NIOUtils.fetchFromChannel(this.is, this.bufSize);
            Packet packet = new Packet(
               pix,
               (long)(this.frameNum * this.fps.getDen()),
               this.fps.getNum(),
               (long)this.fps.getDen(),
               (long)this.frameNum,
               Packet.FrameType.KEY,
               null,
               this.frameNum
            );
            this.frameNum++;
            return packet;
         } else {
            return null;
         }
      }
   }

   private static String find(String[] header, char c) {
      for (int i = 0; i < header.length; i++) {
         String string = header[i];
         if (string.charAt(0) == c) {
            return string.substring(1);
         }
      }

      return null;
   }

   private static String readLine(ByteBuffer y4m) {
      ByteBuffer duplicate = y4m.duplicate();

      while (y4m.hasRemaining() && y4m.get() != 10) {
      }

      if (y4m.hasRemaining()) {
         ((Buffer)duplicate).limit(y4m.position() - 1);
      }

      return Platform.stringFromBytes(NIOUtils.toArray(duplicate));
   }

   public Rational getFps() {
      return this.fps;
   }

   @Override
   public DemuxerTrackMeta getMeta() {
      return new DemuxerTrackMeta(
         TrackType.VIDEO,
         Codec.RAW,
         (double)this.totalDuration,
         null,
         this.totalFrames,
         null,
         VideoCodecMeta.createSimpleVideoCodecMeta(new Size(this.width, this.height), ColorSpace.YUV420),
         null
      );
   }

   @Override
   public void close() throws IOException {
      this.is.close();
   }

   @Override
   public List<? extends DemuxerTrack> getTracks() {
      List<DemuxerTrack> list = new ArrayList<>();
      list.add(this);
      return list;
   }

   @Override
   public List<? extends DemuxerTrack> getVideoTracks() {
      return this.getTracks();
   }

   @Override
   public List<? extends DemuxerTrack> getAudioTracks() {
      return new ArrayList<>();
   }
}
