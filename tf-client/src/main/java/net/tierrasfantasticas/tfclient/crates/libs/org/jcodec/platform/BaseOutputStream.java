package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.platform;

import java.io.IOException;
import java.io.OutputStream;

public abstract class BaseOutputStream extends OutputStream {
   protected abstract void writeByte(int var1) throws IOException;

   @Override
   public void write(int b) throws IOException {
      this.writeByte(b);
   }
}
