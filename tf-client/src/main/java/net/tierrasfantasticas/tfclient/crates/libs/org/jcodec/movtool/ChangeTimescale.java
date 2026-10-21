package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.movtool;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.Box;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.MediaHeaderBox;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.MovieBox;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.MovieFragmentBox;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.NodeBox;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.TrakBox;
import java.io.File;

public class ChangeTimescale {
   public static void main1(String[] args) throws Exception {
      if (args.length < 2) {
         System.out.println("Syntax: chts <movie> <timescale>");
         System.exit(-1);
      }

      final int ts = Integer.parseInt(args[1]);
      if (ts < 600) {
         System.out.println("Could not set timescale < 600");
         System.exit(-1);
      }

      new InplaceMP4Editor().modify(new File(args[0]), new MP4Edit() {
         @Override
         public void apply(MovieBox mov) {
            TrakBox vt = mov.getVideoTrack();
            MediaHeaderBox mdhd = NodeBox.findFirstPath(vt, MediaHeaderBox.class, Box.path("mdia.mdhd"));
            int oldTs = mdhd.getTimescale();
            if (oldTs > ts) {
               throw new RuntimeException("Old timescale (" + oldTs + ") is greater then new timescale (" + ts + "), not touching.");
            } else {
               vt.fixMediaTimescale(ts);
               mov.fixTimescale(ts);
            }
         }

         @Override
         public void applyToFragment(MovieBox mov, MovieFragmentBox[] fragmentBox) {
            throw new RuntimeException("Unsupported");
         }
      });
   }
}
