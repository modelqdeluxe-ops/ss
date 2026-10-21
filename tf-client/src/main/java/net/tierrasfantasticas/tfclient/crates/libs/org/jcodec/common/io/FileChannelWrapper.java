package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.io;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class FileChannelWrapper implements SeekableByteChannel {
   private FileChannel ch;

   public FileChannelWrapper(FileChannel ch) throws FileNotFoundException {
      this.ch = ch;
   }

   @Override
   public int read(ByteBuffer arg0) throws IOException {
      return this.ch.read(arg0);
   }

   @Override
   public void close() throws IOException {
      this.ch.close();
   }

   @Override
   public boolean isOpen() {
      return this.ch.isOpen();
   }

   @Override
   public int write(ByteBuffer arg0) throws IOException {
      return this.ch.write(arg0);
   }

   @Override
   public long position() throws IOException {
      return this.ch.position();
   }

   @Override
   public SeekableByteChannel setPosition(long newPosition) throws IOException {
      this.ch.position(newPosition);
      return this;
   }

   @Override
   public long size() throws IOException {
      return this.ch.size();
   }

   @Override
   public SeekableByteChannel truncate(long size) throws IOException {
      this.ch.truncate(size);
      return this;
   }
}
