package net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.transport;

import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.AACException;
import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.syntax.IBitStream;
import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.syntax.PCE;

public final class ADIFHeader {
   private static final long ADIF_ID = 1094994246L;
   private long id;
   private boolean copyrightIDPresent;
   private byte[] copyrightID = new byte[9];
   private boolean originalCopy;
   private boolean home;
   private boolean bitstreamType;
   private int bitrate;
   private int pceCount;
   private int[] adifBufferFullness;
   private PCE[] pces;

   public static boolean isPresent(IBitStream _in) throws AACException {
      return (long)_in.peekBits(32) == 1094994246L;
   }

   private ADIFHeader() {
   }

   public static ADIFHeader readHeader(IBitStream _in) throws AACException {
      ADIFHeader h = new ADIFHeader();
      h.decode(_in);
      return h;
   }

   private void decode(IBitStream _in) throws AACException {
      this.id = (long)_in.readBits(32);
      this.copyrightIDPresent = _in.readBool();
      if (this.copyrightIDPresent) {
         for (int i = 0; i < 9; i++) {
            this.copyrightID[i] = (byte)_in.readBits(8);
         }
      }

      this.originalCopy = _in.readBool();
      this.home = _in.readBool();
      this.bitstreamType = _in.readBool();
      this.bitrate = _in.readBits(23);
      this.pceCount = _in.readBits(4) + 1;
      this.pces = new PCE[this.pceCount];
      this.adifBufferFullness = new int[this.pceCount];

      for (int i = 0; i < this.pceCount; i++) {
         if (this.bitstreamType) {
            this.adifBufferFullness[i] = -1;
         } else {
            this.adifBufferFullness[i] = _in.readBits(20);
         }

         this.pces[i] = new PCE();
         this.pces[i].decode(_in);
      }
   }

   public PCE getFirstPCE() {
      return this.pces[0];
   }
}
