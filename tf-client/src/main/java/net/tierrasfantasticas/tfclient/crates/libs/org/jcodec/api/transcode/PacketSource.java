package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.api.transcode;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.Packet;
import java.io.IOException;

public interface PacketSource {
   Packet inputVideoPacket() throws IOException;

   Packet inputAudioPacket() throws IOException;
}
