package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.pcmdvd;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.AudioCodecMeta;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.AudioDecoder;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.AudioFormat;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.io.NIOUtils;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.AudioBuffer;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class PCMDVDDecoder implements AudioDecoder {
   private static final int[] lpcm_freq_tab = new int[]{48000, 96000, 44100, 32000};

   @Override
   public AudioBuffer decodeFrame(ByteBuffer _frame, ByteBuffer _dst) throws IOException {
      ByteBuffer dst = _dst.duplicate();
      ByteBuffer frame = _frame.duplicate();
      frame.order(ByteOrder.BIG_ENDIAN);
      dst.order(ByteOrder.LITTLE_ENDIAN);
      int dvdaudioSubstreamType = frame.get() & 255;
      NIOUtils.skip(frame, 3);
      if ((dvdaudioSubstreamType & 224) != 160) {
         if ((dvdaudioSubstreamType & 224) == 128) {
            if ((dvdaudioSubstreamType & 248) == 136) {
               throw new RuntimeException("CODEC_ID_DTS");
            } else {
               throw new RuntimeException("CODEC_ID_AC3");
            }
         } else {
            throw new RuntimeException("MPEG DVD unknown coded");
         }
      } else {
         int b0 = frame.get() & 255;
         int b1 = frame.get() & 255;
         int b2 = frame.get() & 255;
         int freq = b1 >> 4 & 3;
         int sampleRate = lpcm_freq_tab[freq];
         int channelCount = 1 + (b1 & 7);
         int sampleSizeInBits = 16 + (b1 >> 6 & 3) * 4;
         int nFrames = frame.remaining() / (channelCount * (sampleSizeInBits >> 3));
         switch (sampleSizeInBits) {
            case 20:
               for (int n = 0; n < nFrames >> 1; n++) {
                  for (int c = 0; c < channelCount; c++) {
                     short s0 = frame.getShort();
                     dst.putShort(s0);
                     short s1 = frame.getShort();
                     dst.putShort(s1);
                  }

                  NIOUtils.skip(frame, channelCount);
               }
               break;
            case 24:
               for (int n = 0; n < nFrames >> 1; n++) {
                  for (int c = 0; c < channelCount; c++) {
                     short s0 = frame.getShort();
                     dst.putShort(s0);
                     short s1 = frame.getShort();
                     dst.putShort(s1);
                  }

                  NIOUtils.skip(frame, channelCount << 1);
               }
         }

         ((Buffer)dst).flip();
         return new AudioBuffer(dst, new AudioFormat(sampleRate, sampleSizeInBits, channelCount, true, false), nFrames);
      }
   }

   @Override
   public AudioCodecMeta getCodecMeta(ByteBuffer _frame) throws IOException {
      ByteBuffer frame = _frame.duplicate();
      frame.order(ByteOrder.BIG_ENDIAN);
      int dvdaudioSubstreamType = frame.get() & 255;
      NIOUtils.skip(frame, 3);
      if ((dvdaudioSubstreamType & 224) == 160) {
         int b0 = frame.get() & 255;
         int b1 = frame.get() & 255;
         int b2 = frame.get() & 255;
         int freq = b1 >> 4 & 3;
         int sampleRate = lpcm_freq_tab[freq];
         int channelCount = 1 + (b1 & 7);
         int sampleSizeInBits = 16 + (b1 >> 6 & 3) * 4;
         return AudioCodecMeta.fromAudioFormat(new AudioFormat(sampleRate, sampleSizeInBits, channelCount, true, false));
      } else if ((dvdaudioSubstreamType & 224) == 128) {
         if ((dvdaudioSubstreamType & 248) == 136) {
            throw new RuntimeException("CODEC_ID_DTS");
         } else {
            throw new RuntimeException("CODEC_ID_AC3");
         }
      } else {
         throw new RuntimeException("MPEG DVD unknown coded");
      }
   }
}
