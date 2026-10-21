package net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.filterbank;

import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.AACException;

class FFT implements FFTTables {
   private final int length;
   private final float[][] roots;
   private final float[][] rev;
   private float[] a;
   private float[] b;
   private float[] c;
   private float[] d;
   private float[] e1;
   private float[] e2;

   FFT(int length) throws AACException {
      this.length = length;
      switch (length) {
         case 60:
            this.roots = FFT_TABLE_60;
            break;
         case 64:
            this.roots = FFT_TABLE_64;
            break;
         case 480:
            this.roots = FFT_TABLE_480;
            break;
         case 512:
            this.roots = FFT_TABLE_512;
            break;
         default:
            throw new AACException("unexpected FFT length: " + length);
      }

      this.rev = new float[length][2];
      this.a = new float[2];
      this.b = new float[2];
      this.c = new float[2];
      this.d = new float[2];
      this.e1 = new float[2];
      this.e2 = new float[2];
   }

   void process(float[][] _in, boolean forward) {
      int imOff = forward ? 2 : 1;
      int scale = forward ? this.length : 1;
      int ii = 0;

      for (int i = 0; i < this.length; i++) {
         this.rev[i][0] = _in[ii][0];
         this.rev[i][1] = _in[ii][1];

         int k;
         for (k = this.length >> 1; ii >= k && k > 0; k >>= 1) {
            ii -= k;
         }

         ii += k;
      }

      for (int i = 0; i < this.length; i++) {
         _in[i][0] = this.rev[i][0];
         _in[i][1] = this.rev[i][1];
      }

      for (int i = 0; i < this.length; i += 4) {
         this.a[0] = _in[i][0] + _in[i + 1][0];
         this.a[1] = _in[i][1] + _in[i + 1][1];
         this.b[0] = _in[i + 2][0] + _in[i + 3][0];
         this.b[1] = _in[i + 2][1] + _in[i + 3][1];
         this.c[0] = _in[i][0] - _in[i + 1][0];
         this.c[1] = _in[i][1] - _in[i + 1][1];
         this.d[0] = _in[i + 2][0] - _in[i + 3][0];
         this.d[1] = _in[i + 2][1] - _in[i + 3][1];
         _in[i][0] = this.a[0] + this.b[0];
         _in[i][1] = this.a[1] + this.b[1];
         _in[i + 2][0] = this.a[0] - this.b[0];
         _in[i + 2][1] = this.a[1] - this.b[1];
         this.e1[0] = this.c[0] - this.d[1];
         this.e1[1] = this.c[1] + this.d[0];
         this.e2[0] = this.c[0] + this.d[1];
         this.e2[1] = this.c[1] - this.d[0];
         if (forward) {
            _in[i + 1][0] = this.e2[0];
            _in[i + 1][1] = this.e2[1];
            _in[i + 3][0] = this.e1[0];
            _in[i + 3][1] = this.e1[1];
         } else {
            _in[i + 1][0] = this.e1[0];
            _in[i + 1][1] = this.e1[1];
            _in[i + 3][0] = this.e2[0];
            _in[i + 3][1] = this.e2[1];
         }
      }

      for (int ix = 4; ix < this.length; ix <<= 1) {
         int shift = ix << 1;
         int m = this.length / shift;

         for (int j = 0; j < this.length; j += shift) {
            for (int k = 0; k < ix; k++) {
               int km = k * m;
               float rootRe = this.roots[km][0];
               float rootIm = this.roots[km][imOff];
               float zRe = _in[ix + j + k][0] * rootRe - _in[ix + j + k][1] * rootIm;
               float zIm = _in[ix + j + k][0] * rootIm + _in[ix + j + k][1] * rootRe;
               _in[ix + j + k][0] = (_in[j + k][0] - zRe) * (float)scale;
               _in[ix + j + k][1] = (_in[j + k][1] - zIm) * (float)scale;
               _in[j + k][0] = (_in[j + k][0] + zRe) * (float)scale;
               _in[j + k][1] = (_in[j + k][1] + zIm) * (float)scale;
            }
         }
      }
   }
}
