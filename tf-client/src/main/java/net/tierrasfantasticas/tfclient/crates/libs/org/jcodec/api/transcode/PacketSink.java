package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.api.transcode;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.AudioCodecMeta;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.VideoCodecMeta;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.Packet;
import java.io.IOException;

public interface PacketSink {
   void outputVideoPacket(Packet var1, VideoCodecMeta var2) throws IOException;

   void outputAudioPacket(Packet var1, AudioCodecMeta var2) throws IOException;
}
