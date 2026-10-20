package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.h264.mp4.AvcCBox;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.CleanApertureExtension;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.ColorExtension;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.FielExtension;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.GamaExtension;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.PixelAspectExt;

public class VideoBoxes extends Boxes {
   public VideoBoxes() {
      this.mappings.put(PixelAspectExt.fourcc(), PixelAspectExt.class);
      this.mappings.put(AvcCBox.fourcc(), AvcCBox.class);
      this.mappings.put(ColorExtension.fourcc(), ColorExtension.class);
      this.mappings.put(GamaExtension.fourcc(), GamaExtension.class);
      this.mappings.put(CleanApertureExtension.fourcc(), CleanApertureExtension.class);
      this.mappings.put(FielExtension.fourcc(), FielExtension.class);
   }
}
