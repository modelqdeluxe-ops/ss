package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.AudioFormat;
import java.nio.ByteBuffer;

public class AudioFrame extends AudioBuffer {
   private long pts;
   private long duration;
   private long timescale;
   private int frameNo;

   public AudioFrame(ByteBuffer buffer, AudioFormat format, int nFrames, long pts, long duration, long timescale, int frameNo) {
      super(buffer, format, nFrames);
      this.pts = pts;
      this.duration = duration;
      this.timescale = timescale;
      this.frameNo = frameNo;
   }

   public long getPts() {
      return this.pts;
   }

   public long getDuration() {
      return this.duration;
   }

   public long getTimescale() {
      return this.timescale;
   }

   public int getFrameNo() {
      return this.frameNo;
   }
}
