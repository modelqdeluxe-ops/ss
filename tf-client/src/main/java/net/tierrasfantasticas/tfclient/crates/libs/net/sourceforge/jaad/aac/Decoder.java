package net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac;

import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.filterbank.FilterBank;
import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.syntax.BitStream;
import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.syntax.IBitStream;
import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.syntax.PCE;
import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.syntax.SyntacticElements;
import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.syntax.SyntaxConstants;
import net.tierrasfantasticas.tfclient.crates.libs.net.sourceforge.jaad.aac.transport.ADIFHeader;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.logging.Logger;

public class Decoder implements SyntaxConstants {
   private final AACDecoderConfig config;
   private final SyntacticElements syntacticElements;
   private final FilterBank filterBank;
   private IBitStream _in;
   private ADIFHeader adifHeader;

   public static boolean canDecode(Profile profile) {
      return profile.isDecodingSupported();
   }

   public Decoder(byte[] decoderSpecificInfo) throws AACException {
      this.config = AACDecoderConfig.parseMP4DecoderSpecificInfo(decoderSpecificInfo);
      if (this.config == null) {
         throw new IllegalArgumentException("illegal MP4 decoder specific info");
      } else if (!canDecode(this.config.getProfile())) {
         throw new AACException("unsupported profile: " + this.config.getProfile().getDescription());
      } else {
         this.syntacticElements = new SyntacticElements(this.config);
         this.filterBank = new FilterBank(this.config.isSmallFrameUsed(), this.config.getChannelConfiguration().getChannelCount());
         this._in = new BitStream();
         Logger.debug("profile: {0}", this.config.getProfile());
         Logger.debug("sf: {0}", this.config.getSampleFrequency().getFrequency());
         Logger.debug("channels: {0}", this.config.getChannelConfiguration().getDescription());
      }
   }

   public AACDecoderConfig getConfig() {
      return this.config;
   }

   public void decodeFrame(byte[] frame, SampleBuffer buffer) throws AACException {
      if (frame != null) {
         this._in.setData(frame);
      }

      Logger.debug("bits left " + this._in.getBitsLeft());

      try {
         this.decode(buffer);
      } catch (AACException var4) {
         if (!var4.isEndOfStream()) {
            throw var4;
         }

         Logger.warn("unexpected end of frame");
      }
   }

   private void decode(SampleBuffer buffer) throws AACException {
      if (ADIFHeader.isPresent(this._in)) {
         this.adifHeader = ADIFHeader.readHeader(this._in);
         PCE pce = this.adifHeader.getFirstPCE();
         this.config.setProfile(pce.getProfile());
         this.config.setSampleFrequency(pce.getSampleFrequency());
         this.config.setChannelConfiguration(ChannelConfiguration.forInt(pce.getChannelCount()));
      }

      if (!canDecode(this.config.getProfile())) {
         throw new AACException("unsupported profile: " + this.config.getProfile().getDescription());
      } else {
         this.syntacticElements.startNewFrame();

         try {
            this.syntacticElements.decode(this._in);
            this.syntacticElements.process(this.filterBank);
            this.syntacticElements.sendToOutput(buffer);
         } catch (Exception var3) {
            buffer.setData(new byte[0], 0, 0, 0, 0);
            throw AACException.wrap(var3);
         }
      }
   }
}
