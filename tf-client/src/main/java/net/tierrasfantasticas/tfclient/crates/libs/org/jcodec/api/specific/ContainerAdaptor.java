package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.api.specific;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.api.MediaInfo;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.Packet;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.Picture;

public interface ContainerAdaptor {
   Picture decodeFrame(Packet var1, byte[][] var2);

   boolean canSeek(Packet var1);

   byte[][] allocatePicture();

   MediaInfo getMediaInfo();
}
