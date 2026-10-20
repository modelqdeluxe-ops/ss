package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.Packet;
import java.io.IOException;

public interface MuxerTrack {
   void addFrame(Packet var1) throws IOException;
}
