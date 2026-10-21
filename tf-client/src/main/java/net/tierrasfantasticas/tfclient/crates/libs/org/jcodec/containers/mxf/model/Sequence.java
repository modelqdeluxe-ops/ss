package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mxf.model;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.logging.Logger;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

public class Sequence extends MXFStructuralComponent {
   private UL[] structuralComponentsRefs;

   public Sequence(UL ul) {
      super(ul);
   }

   @Override
   protected void read(Map<Integer, ByteBuffer> tags) {
      super.read(tags);
      Iterator<Entry<Integer, ByteBuffer>> it = tags.entrySet().iterator();

      while (it.hasNext()) {
         Entry<Integer, ByteBuffer> entry = it.next();
         switch (entry.getKey()) {
            case 4097:
               this.structuralComponentsRefs = readULBatch(entry.getValue());
               it.remove();
               break;
            default:
               Logger.warn(String.format("Unknown tag [ " + this.ul + "]: %04x", entry.getKey()));
         }
      }
   }

   public UL[] getStructuralComponentsRefs() {
      return this.structuralComponentsRefs;
   }
}
