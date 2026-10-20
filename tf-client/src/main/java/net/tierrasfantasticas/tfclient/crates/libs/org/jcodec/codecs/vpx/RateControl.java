package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.vpx;

public interface RateControl {
   int[] getSegmentQps();

   int getSegment();

   void report(int var1);

   void reset();
}
