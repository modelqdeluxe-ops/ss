package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mxf.model;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.logging.Logger;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

public class SourcePackage extends GenericPackage {
   private UL[] trackRefs;
   private UL descriptorRef;

   public SourcePackage(UL ul) {
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
            case 18177:
               this.descriptorRef = UL.read(_bb);
               it.remove();
               break;
            default:
               Logger.warn(String.format("Unknown tag [ " + this.ul + "]: %04x", entry.getKey()));
         }
      }
   }

   public UL[] getTrackRefs() {
      return this.trackRefs;
   }

   public UL getDescriptorRef() {
      return this.descriptorRef;
   }
}
