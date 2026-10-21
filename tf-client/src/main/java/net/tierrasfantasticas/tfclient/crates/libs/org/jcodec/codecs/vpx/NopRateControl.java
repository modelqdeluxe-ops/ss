package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.vpx;

public class NopRateControl implements RateControl {
   private int qp;

   public NopRateControl(int qp) {
      this.qp = qp;
   }

   @Override
   public int[] getSegmentQps() {
      return new int[]{this.qp};
   }

   @Override
   public int getSegment() {
      return 0;
   }

   @Override
   public void report(int bits) {
   }

   @Override
   public void reset() {
   }
}
