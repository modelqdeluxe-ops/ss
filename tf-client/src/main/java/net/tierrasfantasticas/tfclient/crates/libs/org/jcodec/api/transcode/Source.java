package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.api.transcode;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.AudioCodecMeta;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.VideoCodecMeta;
import java.io.IOException;

public interface Source {
   void init(PixelStore var1) throws IOException;

   void seekFrames(int var1) throws IOException;

   VideoFrameWithPacket getNextVideoFrame() throws IOException;

   AudioFrameWithPacket getNextAudioFrame() throws IOException;

   void finish();

   boolean haveAudio();

   void setOption(Options var1, Object var2);

   VideoCodecMeta getVideoCodecMeta();

   AudioCodecMeta getAudioCodecMeta();

   boolean isVideo();

   boolean isAudio();
}
