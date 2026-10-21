package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.vpx;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.AudioCodecMeta;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.Codec;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.Muxer;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.MuxerTrack;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.VideoCodecMeta;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.io.NIOUtils;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.io.SeekableByteChannel;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.Packet;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.Size;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class IVFMuxer implements Muxer, MuxerTrack {
   private SeekableByteChannel ch;
   private int nFrames;
   private Size dim;
   private int frameRate;
   private boolean headerWritten;

   public IVFMuxer(SeekableByteChannel ch) throws IOException {
      this.ch = ch;
   }

   @Override
   public void addFrame(Packet pkt) throws IOException {
      if (!this.headerWritten) {
         this.frameRate = pkt.getTimescale();
         this.writeHeader();
         this.headerWritten = true;
      }

      ByteBuffer fh = ByteBuffer.allocate(12);
      fh.order(ByteOrder.LITTLE_ENDIAN);
      ByteBuffer frame = pkt.getData();
      fh.putInt(frame.remaining());
      fh.putLong((long)this.nFrames);
      ((Buffer)fh).clear();
      this.ch.write(fh);
      this.ch.write(frame);
      this.nFrames++;
   }

   public void close() throws IOException {
      this.ch.setPosition(24L);
      NIOUtils.writeIntLE(this.ch, this.nFrames);
   }

   @Override
   public MuxerTrack addVideoTrack(Codec codec, VideoCodecMeta meta) {
      if (this.dim != null) {
         throw new RuntimeException("IVF can not have multiple video tracks.");
      } else {
         this.dim = meta.getSize();
         return this;
      }
   }

   private void writeHeader() throws IOException {
      ByteBuffer ivf = ByteBuffer.allocate(32);
      ivf.order(ByteOrder.LITTLE_ENDIAN);
      ivf.put((byte)68);
      ivf.put((byte)75);
      ivf.put((byte)73);
      ivf.put((byte)70);
      ivf.putShort((short)0);
      ivf.putShort((short)32);
      ivf.putInt(808996950);
      ivf.putShort((short)this.dim.getWidth());
      ivf.putShort((short)this.dim.getHeight());
      ivf.putInt(this.frameRate);
      ivf.putInt(1);
      ivf.putInt(1);
      ((Buffer)ivf).clear();
      this.ch.write(ivf);
   }

   @Override
   public MuxerTrack addAudioTrack(Codec codec, AudioCodecMeta meta) {
      throw new RuntimeException("Video-only container");
   }

   @Override
   public void finish() throws IOException {
   }
}
