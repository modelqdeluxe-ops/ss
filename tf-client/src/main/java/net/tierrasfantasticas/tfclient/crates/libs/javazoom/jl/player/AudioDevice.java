package net.tierrasfantasticas.tfclient.crates.libs.javazoom.jl.player;

import net.tierrasfantasticas.tfclient.crates.libs.javazoom.jl.decoder.Decoder;
import net.tierrasfantasticas.tfclient.crates.libs.javazoom.jl.decoder.JavaLayerException;

public interface AudioDevice {
   void open(Decoder var1) throws JavaLayerException;

   boolean isOpen();

   void write(short[] var1, int var2, int var3) throws JavaLayerException;

   void close();

   void flush();

   int getPosition();
}
