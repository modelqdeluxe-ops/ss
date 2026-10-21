package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.movtool;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.Rational;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.Size;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.BoxUtil;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.Box;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.MovieBox;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.MovieFragmentBox;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.NodeBox;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.SampleDescriptionBox;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.TrakBox;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.VideoSampleEntry;
import java.io.File;

public class SetPAR {
   public static void main1(String[] args) throws Exception {
      if (args.length < 2) {
         System.out.println("Syntax: setpasp <movie> <num:den>");
         System.exit(-1);
      }

      final Rational newPAR = Rational.parse(args[1]);
      new InplaceMP4Editor().modify(new File(args[0]), new MP4Edit() {
         @Override
         public void apply(MovieBox mov) {
            TrakBox vt = mov.getVideoTrack();
            vt.setPAR(newPAR);
            Box box = NodeBox.findFirstPath(vt, SampleDescriptionBox.class, Box.path("mdia.minf.stbl.stsd")).getBoxes().get(0);
            if (box != null && box instanceof VideoSampleEntry) {
               VideoSampleEntry vs = (VideoSampleEntry)box;
               int codedWidth = vs.getWidth();
               int codedHeight = vs.getHeight();
               int displayWidth = codedWidth * newPAR.getNum() / newPAR.getDen();
               vt.getTrackHeader().setWidth((float)displayWidth);
               if (BoxUtil.containsBox(vt, "tapt")) {
                  vt.setAperture(new Size(codedWidth, codedHeight), new Size(displayWidth, codedHeight));
               }
            }
         }

         @Override
         public void applyToFragment(MovieBox mov, MovieFragmentBox[] fragmentBox) {
            throw new RuntimeException("Unsupported");
         }
      });
   }
}
