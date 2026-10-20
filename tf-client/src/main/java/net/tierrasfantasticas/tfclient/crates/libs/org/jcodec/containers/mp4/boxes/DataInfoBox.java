package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes;

public class DataInfoBox extends NodeBox {
   public static String fourcc() {
      return "dinf";
   }

   public static DataInfoBox createDataInfoBox() {
      return new DataInfoBox(new Header(fourcc()));
   }

   public DataInfoBox(Header atom) {
      super(atom);
   }

   public DataRefBox getDref() {
      return NodeBox.findFirst(this, DataRefBox.class, "dref");
   }
}
