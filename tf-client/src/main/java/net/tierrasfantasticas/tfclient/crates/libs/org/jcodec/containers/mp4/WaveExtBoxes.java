package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.EndianBox;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.FormatBox;

public class WaveExtBoxes extends Boxes {
   public WaveExtBoxes() {
      this.mappings.put(FormatBox.fourcc(), FormatBox.class);
      this.mappings.put(EndianBox.fourcc(), EndianBox.class);
   }
}
