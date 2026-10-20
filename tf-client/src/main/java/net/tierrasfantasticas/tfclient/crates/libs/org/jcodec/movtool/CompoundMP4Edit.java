package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.movtool;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.MovieBox;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.MovieFragmentBox;
import java.util.List;

public class CompoundMP4Edit implements MP4Edit {
   private List<MP4Edit> edits;

   public CompoundMP4Edit(List<MP4Edit> edits) {
      this.edits = edits;
   }

   @Override
   public void applyToFragment(MovieBox mov, MovieFragmentBox[] fragmentBox) {
      for (MP4Edit command : this.edits) {
         command.applyToFragment(mov, fragmentBox);
      }
   }

   @Override
   public void apply(MovieBox mov) {
      for (MP4Edit command : this.edits) {
         command.apply(mov);
      }
   }
}
