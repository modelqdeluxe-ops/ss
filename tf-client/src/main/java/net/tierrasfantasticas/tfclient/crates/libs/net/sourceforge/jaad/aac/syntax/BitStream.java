package net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.syntax;

import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.AACException;

public class BitStream implements IBitStream {
   private static final int WORD_BITS = 32;
   private static final int WORD_BYTES = 4;
   private static final int BYTE_MASK = 255;
   private byte[] buffer;
   private int pos;
   private int cache;
   protected int bitsCached;
   protected int position;

   public static BitStream createBitStream(byte[] data) {
      BitStream bs = new BitStream();
      bs.setData(data);
      return bs;
   }

   @Override
   public void destroy() {
      this.reset();
      this.buffer = null;
   }

   @Override
   public final void setData(byte[] data) {
      int size = 4 * ((data.length + 4 - 1) / 4);
      if (this.buffer == null || this.buffer.length != size) {
         this.buffer = new byte[size];
      }

      System.arraycopy(data, 0, this.buffer, 0, data.length);
      this.reset();
   }

   @Override
   public void byteAlign() throws AACException {
      int toFlush = this.bitsCached & 7;
      if (toFlush > 0) {
         this.skipBits(toFlush);
      }
   }

   @Override
   public final void reset() {
      this.pos = 0;
      this.bitsCached = 0;
      this.cache = 0;
      this.position = 0;
   }

   @Override
   public int getPosition() {
      return this.position;
   }

   @Override
   public int getBitsLeft() {
      return this.buffer != null ? 8 * (this.buffer.length - this.pos) + this.bitsCached : 0;
   }

   protected int readCache(boolean peek) throws AACException {
      if (this.pos > this.buffer.length - 4) {
         throw AACException.endOfStream();
      } else {
         int i = (this.buffer[this.pos] & 255) << 24
            | (this.buffer[this.pos + 1] & 255) << 16
            | (this.buffer[this.pos + 2] & 255) << 8
            | this.buffer[this.pos + 3] & 255;
         if (!peek) {
            this.pos += 4;
         }

         return i;
      }
   }

   @Override
   public int readBits(int n) throws AACException {
      int result;
      if (this.bitsCached >= n) {
         this.bitsCached -= n;
         result = this.cache >> this.bitsCached & this.maskBits(n);
         this.position += n;
      } else {
         this.position += n;
         int c = this.cache & this.maskBits(this.bitsCached);
         int left = n - this.bitsCached;
         this.cache = this.readCache(false);
         this.bitsCached = 32 - left;
         result = this.cache >> this.bitsCached & this.maskBits(left) | c << left;
      }

      return result;
   }

   @Override
   public int readBit() throws AACException {
      int i;
      if (this.bitsCached > 0) {
         this.bitsCached--;
         i = this.cache >> this.bitsCached & 1;
         this.position++;
      } else {
         this.cache = this.readCache(false);
         this.bitsCached = 31;
         this.position++;
         i = this.cache >> this.bitsCached & 1;
      }

      return i;
   }

   @Override
   public boolean readBool() throws AACException {
      return (this.readBit() & 1) != 0;
   }

   @Override
   public int peekBits(int n) throws AACException {
      int ret;
      if (this.bitsCached >= n) {
         ret = this.cache >> this.bitsCached - n & this.maskBits(n);
      } else {
         int c = this.cache & this.maskBits(this.bitsCached);
         n -= this.bitsCached;
         ret = this.readCache(true) >> 32 - n & this.maskBits(n) | c << n;
      }

      return ret;
   }

   @Override
   public int peekBit() throws AACException {
      int ret;
      if (this.bitsCached > 0) {
         ret = this.cache >> this.bitsCached - 1 & 1;
      } else {
         int word = this.readCache(true);
         ret = word >> 31 & 1;
      }

      return ret;
   }

   @Override
   public void skipBits(int n) throws AACException {
      this.position += n;
      if (n <= this.bitsCached) {
         this.bitsCached -= n;
      } else {
         n -= this.bitsCached;

         while (n >= 32) {
            n -= 32;
            this.readCache(false);
         }

         if (n > 0) {
            this.cache = this.readCache(false);
            this.bitsCached = 32 - n;
         } else {
            this.cache = 0;
            this.bitsCached = 0;
         }
      }
   }

   @Override
   public void skipBit() throws AACException {
      this.position++;
      if (this.bitsCached > 0) {
         this.bitsCached--;
      } else {
         this.cache = this.readCache(false);
         this.bitsCached = 31;
      }
   }

   @Override
   public int maskBits(int n) {
      int i;
      if (n == 32) {
         i = -1;
      } else {
         i = (1 << n) - 1;
      }

      return i;
   }
}
