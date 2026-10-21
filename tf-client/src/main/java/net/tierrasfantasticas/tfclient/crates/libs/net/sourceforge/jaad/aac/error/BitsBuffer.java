package net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.error;

import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.AACException;
import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.syntax.IBitStream;

public class BitsBuffer {
   int bufa;
   int bufb;
   int len = 0;
   static final int[] S = new int[]{1, 2, 4, 8, 16};
   static final int[] B = new int[]{1431655765, 858993459, 252645135, 16711935, 65535};

   public int getLength() {
      return this.len;
   }

   public int showBits(int bits) {
      if (bits == 0) {
         return 0;
      } else if (this.len <= 32) {
         return this.len >= bits ? this.bufa >> this.len - bits & -1 >> 32 - bits : this.bufa << bits - this.len & -1 >> 32 - bits;
      } else {
         return this.len - bits < 32
            ? (this.bufb & -1 >> 64 - this.len) << bits - this.len + 32 | this.bufa >> this.len - bits
            : this.bufb >> this.len - bits - 32 & -1 >> 32 - bits;
      }
   }

   public boolean flushBits(int bits) {
      this.len -= bits;
      boolean b;
      if (this.len < 0) {
         this.len = 0;
         b = false;
      } else {
         b = true;
      }

      return b;
   }

   public int getBits(int n) {
      int i = this.showBits(n);
      if (!this.flushBits(n)) {
         i = -1;
      }

      return i;
   }

   public int getBit() {
      int i = this.showBits(1);
      if (!this.flushBits(1)) {
         i = -1;
      }

      return i;
   }

   public void rewindReverse() {
      if (this.len != 0) {
         int[] i = rewindReverse64(this.bufb, this.bufa, this.len);
         this.bufb = i[0];
         this.bufa = i[1];
      }
   }

   public void concatBits(BitsBuffer a) {
      if (a.len != 0) {
         int al = a.bufa;
         int ah = a.bufb;
         int bl;
         int bh;
         if (this.len > 32) {
            bl = this.bufa;
            bh = this.bufb & (1 << this.len - 32) - 1;
            ah = al << this.len - 32;
            al = 0;
         } else {
            bl = this.bufa & (1 << this.len) - 1;
            bh = 0;
            ah = ah << this.len | al >> 32 - this.len;
            al <<= this.len;
         }

         this.bufa = bl | al;
         this.bufb = bh | ah;
         this.len = this.len + a.len;
      }
   }

   public void readSegment(int segwidth, IBitStream _in) throws AACException {
      this.len = segwidth;
      if (segwidth > 32) {
         this.bufb = _in.readBits(segwidth - 32);
         this.bufa = _in.readBits(32);
      } else {
         this.bufa = _in.readBits(segwidth);
         this.bufb = 0;
      }
   }

   static int rewindReverse32(int v, int len) {
      v = v >> S[0] & B[0] | v << S[0] & ~B[0];
      v = v >> S[1] & B[1] | v << S[1] & ~B[1];
      v = v >> S[2] & B[2] | v << S[2] & ~B[2];
      v = v >> S[3] & B[3] | v << S[3] & ~B[3];
      v = v >> S[4] & B[4] | v << S[4] & ~B[4];
      return v >> 32 - len;
   }

   static int[] rewindReverse64(int hi, int lo, int len) {
      int[] i = new int[2];
      if (len <= 32) {
         i[0] = 0;
         i[1] = rewindReverse32(lo, len);
      } else {
         lo = lo >> S[0] & B[0] | lo << S[0] & ~B[0];
         hi = hi >> S[0] & B[0] | hi << S[0] & ~B[0];
         lo = lo >> S[1] & B[1] | lo << S[1] & ~B[1];
         hi = hi >> S[1] & B[1] | hi << S[1] & ~B[1];
         lo = lo >> S[2] & B[2] | lo << S[2] & ~B[2];
         hi = hi >> S[2] & B[2] | hi << S[2] & ~B[2];
         lo = lo >> S[3] & B[3] | lo << S[3] & ~B[3];
         hi = hi >> S[3] & B[3] | hi << S[3] & ~B[3];
         lo = lo >> S[4] & B[4] | lo << S[4] & ~B[4];
         hi = hi >> S[4] & B[4] | hi << S[4] & ~B[4];
         i[1] = hi >> 64 - len | lo << len - 32;
         i[1] = lo >> 64 - len;
      }

      return i;
   }
}
