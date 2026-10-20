package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.h264.decode;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.h264.decode.aso.Mapper;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.Picture;

public class MBlockDecoderIPCM {
   private Mapper mapper;
   private DecoderState s;

   public MBlockDecoderIPCM(Mapper mapper, DecoderState decoderState) {
      this.mapper = mapper;
      this.s = decoderState;
   }

   public void decode(MBlock mBlock, Picture mb) {
      int mbX = this.mapper.getMbX(mBlock.mbIdx);
      MBlockDecoderUtils.collectPredictors(this.s, mb, mbX);
      MBlockDecoderUtils.saveVectIntra(this.s, this.mapper.getMbX(mBlock.mbIdx));
   }
}
