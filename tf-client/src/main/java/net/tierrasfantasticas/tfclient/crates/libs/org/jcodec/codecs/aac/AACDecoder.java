package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.aac;

import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.AACException;
import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.Decoder;
import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.SampleBuffer;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.AudioCodecMeta;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.AudioDecoder;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.AudioFormat;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.UsedViaReflection;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.io.NIOUtils;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.logging.Logger;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.AudioBuffer;
import java.io.IOException;
import java.nio.ByteBuffer;

public class AACDecoder implements AudioDecoder {
   private Decoder decoder;

   public AACDecoder(ByteBuffer decoderSpecific) throws AACException {
      if (decoderSpecific.remaining() >= 7) {
         ADTSParser.Header header = ADTSParser.read(decoderSpecific);
         if (header != null) {
            decoderSpecific = ADTSParser.adtsToStreamInfo(header);
         }

         Logger.info("Creating AAC decoder from ADTS header.");
      }

      this.decoder = new Decoder(NIOUtils.toArray(decoderSpecific));
   }

   @Override
   public AudioBuffer decodeFrame(ByteBuffer frame, ByteBuffer dst) throws IOException {
      ADTSParser.read(frame);
      SampleBuffer sampleBuffer = new SampleBuffer();
      this.decoder.decodeFrame(NIOUtils.toArray(frame), sampleBuffer);
      if (sampleBuffer.isBigEndian()) {
         sampleBuffer.setBigEndian(false);
      }

      return new AudioBuffer(ByteBuffer.wrap(sampleBuffer.getData()), this.toAudioFormat(sampleBuffer), 0);
   }

   private AudioFormat toAudioFormat(SampleBuffer sampleBuffer) {
      return new AudioFormat(sampleBuffer.getSampleRate(), sampleBuffer.getBitsPerSample(), sampleBuffer.getChannels(), true, sampleBuffer.isBigEndian());
   }

   @Override
   public AudioCodecMeta getCodecMeta(ByteBuffer data) throws IOException {
      SampleBuffer sampleBuffer = new SampleBuffer();
      this.decoder.decodeFrame(NIOUtils.toArray(data), sampleBuffer);
      sampleBuffer.setBigEndian(false);
      return AudioCodecMeta.fromAudioFormat(this.toAudioFormat(sampleBuffer));
   }

   @UsedViaReflection
   public static int probe(ByteBuffer data) {
      if (data.remaining() < 7) {
         return 0;
      } else {
         ADTSParser.Header header = ADTSParser.read(data);
         return header != null ? 100 : 0;
      }
   }
}
