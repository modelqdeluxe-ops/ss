package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes;

public class MediaInfoBox extends NodeBox {
   public static String fourcc() {
      return "minf";
   }

   public static MediaInfoBox createMediaInfoBox() {
      return new MediaInfoBox(new Header(fourcc()));
   }

   public MediaInfoBox(Header atom) {
      super(atom);
   }

   public DataInfoBox getDinf() {
      return NodeBox.findFirst(this, DataInfoBox.class, "dinf");
   }

   public NodeBox getStbl() {
      return NodeBox.findFirst(this, NodeBox.class, "stbl");
   }
}
