package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.api.transcode;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.AudioBuffer;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.Packet;

public class AudioFrameWithPacket {
   private AudioBuffer audio;
   private Packet packet;

   public AudioFrameWithPacket(AudioBuffer audio, Packet packet) {
      this.audio = audio;
      this.packet = packet;
   }

   public AudioBuffer getAudio() {
      return this.audio;
   }

   public Packet getPacket() {
      return this.packet;
   }
}
