package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.AudioBuffer;
import java.io.IOException;
import java.nio.ByteBuffer;

public interface AudioDecoder {
   AudioBuffer decodeFrame(ByteBuffer var1, ByteBuffer var2) throws IOException;

   AudioCodecMeta getCodecMeta(ByteBuffer var1) throws IOException;
}
