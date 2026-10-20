package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mxf.model;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.logging.Logger;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

public class GenericDescriptor extends MXFInterchangeObject {
   private UL[] locators;
   private UL[] subDescriptors;

   public GenericDescriptor(UL ul) {
      super(ul);
   }

   @Override
   protected void read(Map<Integer, ByteBuffer> tags) {
      Iterator<Entry<Integer, ByteBuffer>> it = tags.entrySet().iterator();

      while (it.hasNext()) {
         Entry<Integer, ByteBuffer> entry = it.next();
         ByteBuffer _bb = entry.getValue();
         switch (entry.getKey()) {
            case 12033:
               this.locators = readULBatch(_bb);
               break;
            case 16129:
               this.subDescriptors = readULBatch(_bb);
               break;
            default:
               Logger.warn(String.format("Unknown tag [ " + this.ul + "]: %04x", entry.getKey()));
               continue;
         }

         it.remove();
      }
   }

   public UL[] getLocators() {
      return this.locators;
   }

   public UL[] getSubDescriptors() {
      return this.subDescriptors;
   }
}
