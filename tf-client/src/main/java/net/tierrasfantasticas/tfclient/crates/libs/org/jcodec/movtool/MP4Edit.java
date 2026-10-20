package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.movtool;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.MovieBox;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.MovieFragmentBox;

public interface MP4Edit {
   void applyToFragment(MovieBox var1, MovieFragmentBox[] var2);

   void apply(MovieBox var1);
}
