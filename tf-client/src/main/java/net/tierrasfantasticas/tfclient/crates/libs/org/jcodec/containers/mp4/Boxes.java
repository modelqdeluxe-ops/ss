package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.Box;
import java.util.HashMap;
import java.util.Map;

public abstract class Boxes {
   protected final Map<String, Class<? extends Box>> mappings = new HashMap<>();

   public Class<? extends Box> toClass(String fourcc) {
      return this.mappings.get(fourcc);
   }

   public void override(String fourcc, Class<? extends Box> cls) {
      this.mappings.put(fourcc, cls);
   }

   public void clear() {
      this.mappings.clear();
   }
}
