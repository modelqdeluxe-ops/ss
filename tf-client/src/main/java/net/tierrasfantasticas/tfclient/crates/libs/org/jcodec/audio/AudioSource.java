package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.audio;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.AudioFormat;
import java.io.IOException;
import java.nio.FloatBuffer;

public interface AudioSource {
   AudioFormat getFormat();

   int readFloat(FloatBuffer var1) throws IOException;
}
