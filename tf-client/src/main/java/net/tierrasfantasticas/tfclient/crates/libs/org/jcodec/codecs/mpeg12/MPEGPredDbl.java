package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.mpeg12;

public class MPEGPredDbl extends MPEGPredOct {
   public MPEGPredDbl(MPEGPred other) {
      super(other);
   }

   @Override
   public void predictPlane(
      byte[] ref, int refX, int refY, int refW, int refH, int refVertStep, int refVertOff, int[] tgt, int tgtY, int tgtW, int tgtH, int tgtVertStep
   ) {
      super.predictPlane(ref, refX << 1, refY << 1, refW, refH, refVertStep, refVertOff, tgt, tgtY, tgtW << 2, tgtH << 2, tgtVertStep);
   }
}
