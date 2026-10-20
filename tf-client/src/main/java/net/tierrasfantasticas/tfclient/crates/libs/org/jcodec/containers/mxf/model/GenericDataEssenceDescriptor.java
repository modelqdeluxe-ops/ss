package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mxf.model;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.logging.Logger;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

public class GenericDataEssenceDescriptor extends FileDescriptor {
   private UL dataEssenceCoding;

   public GenericDataEssenceDescriptor(UL ul) {
      super(ul);
   }

   @Override
   protected void read(Map<Integer, ByteBuffer> tags) {
      super.read(tags);
      Iterator<Entry<Integer, ByteBuffer>> it = tags.entrySet().iterator();

      while (it.hasNext()) {
         Entry<Integer, ByteBuffer> entry = it.next();
         ByteBuffer _bb = entry.getValue();
         switch (entry.getKey()) {
            case 15873:
               this.dataEssenceCoding = UL.read(_bb);
               it.remove();
               break;
            default:
               Logger.warn(String.format("Unknown tag [ FileDescriptor: " + this.ul + "]: %04x", entry.getKey()));
         }
      }
   }

   public UL getDataEssenceCoding() {
      return this.dataEssenceCoding;
   }
}
