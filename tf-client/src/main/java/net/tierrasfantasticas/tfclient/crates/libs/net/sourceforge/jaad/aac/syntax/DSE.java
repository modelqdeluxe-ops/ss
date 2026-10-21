package net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.syntax;

import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.AACException;

class DSE extends Element {
   private byte[] dataStreamBytes;

   void decode(IBitStream _in) throws AACException {
      boolean byteAlign = _in.readBool();
      int count = _in.readBits(8);
      if (count == 255) {
         count += _in.readBits(8);
      }

      if (byteAlign) {
         _in.byteAlign();
      }

      this.dataStreamBytes = new byte[count];

      for (int i = 0; i < count; i++) {
         this.dataStreamBytes[i] = (byte)_in.readBits(8);
      }
   }
}
