package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.api.transcode;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.ColorSpace;
import java.io.IOException;

public interface Sink {
   void init() throws IOException;

   void outputVideoFrame(VideoFrameWithPacket var1) throws IOException;

   void outputAudioFrame(AudioFrameWithPacket var1) throws IOException;

   void finish() throws IOException;

   ColorSpace getInputColor();

   void setOption(Options var1, Object var2);

   boolean isVideo();

   boolean isAudio();
}
