package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.prores;

public class Codebook {
   int riceOrder;
   int expOrder;
   int switchBits;
   int golombOffset;
   int golombBits;
   int riceMask;

   public Codebook(int riceOrder, int expOrder, int switchBits) {
      this.riceOrder = riceOrder;
      this.expOrder = expOrder;
      this.switchBits = switchBits;
      this.golombOffset = (1 << expOrder) - (switchBits + 1 << riceOrder);
      this.golombBits = expOrder - switchBits - 1;
      this.riceMask = (1 << riceOrder) - 1;
   }
}
