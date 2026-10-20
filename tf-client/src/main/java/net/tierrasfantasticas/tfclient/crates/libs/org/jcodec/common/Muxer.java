package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common;

import java.io.IOException;

public interface Muxer {
   MuxerTrack addVideoTrack(Codec var1, VideoCodecMeta var2);

   MuxerTrack addAudioTrack(Codec var1, AudioCodecMeta var2);

   void finish() throws IOException;
}
