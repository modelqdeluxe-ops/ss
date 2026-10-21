package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mxf.model;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.logging.Logger;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.Rational;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

public class TimelineTrack extends GenericTrack {
   private Rational editRate;
   private long origin;

   public TimelineTrack(UL ul) {
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
            case 19201:
               this.editRate = new Rational(_bb.getInt(), _bb.getInt());
               break;
            case 19202:
               this.origin = _bb.getLong();
               break;
            default:
               Logger.warn(String.format("Unknown tag [ " + this.ul + "]: %04x", entry.getKey()));
               continue;
         }

         it.remove();
      }
   }

   public Rational getEditRate() {
      return this.editRate;
   }

   public long getOrigin() {
      return this.origin;
   }
}
