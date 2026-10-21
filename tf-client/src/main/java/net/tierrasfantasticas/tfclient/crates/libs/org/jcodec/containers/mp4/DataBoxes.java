package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.AliasBox;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.UrlBox;

public class DataBoxes extends Boxes {
   public DataBoxes() {
      this.mappings.put(UrlBox.fourcc(), UrlBox.class);
      this.mappings.put(AliasBox.fourcc(), AliasBox.class);
      this.mappings.put("cios", AliasBox.class);
   }
}
