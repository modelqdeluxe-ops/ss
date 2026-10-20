package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.movtool;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.logging.Logger;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.RationalLarge;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.tools.MainUtils;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.MovieBox;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.MovieFragmentBox;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.TimeToSampleBox;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.TrakBox;
import java.io.File;

public class SetFPS {
   private static final int MIN_TIMESCALE_ALLOWED = 25;

   public static void main1(String[] args) throws Exception {
      MainUtils.Cmd cmd = MainUtils.parseArguments(args, new MainUtils.Flag[0]);
      if (cmd.argsLength() < 2) {
         MainUtils.printHelpNoFlags("movie", "num:den");
         System.exit(-1);
      }

      final RationalLarge newFPS = RationalLarge.parse(cmd.getArg(1));
      new InplaceMP4Editor().modify(new File(cmd.getArg(0)), new MP4Edit() {
         @Override
         public void apply(MovieBox mov) {
            TrakBox vt = mov.getVideoTrack();
            TimeToSampleBox stts = vt.getStts();
            TimeToSampleBox.TimeToSampleEntry[] entries = stts.getEntries();
            long nSamples = 0L;
            long totalDuration = 0L;

            for (TimeToSampleBox.TimeToSampleEntry e : entries) {
               nSamples += (long)e.getSampleCount();
               totalDuration += (long)(e.getSampleCount() * e.getSampleDuration());
            }

            int newTimescale = (int)newFPS.multiply(new RationalLarge(totalDuration, nSamples)).scalarClip();
            if (newTimescale >= 25) {
               vt.setTimescale(newTimescale);
            } else {
               double mul = new RationalLarge((long)vt.getTimescale() * totalDuration, nSamples).divideBy(newFPS).scalar();
               Logger.info("Applying multiplier to sample durations: " + mul);

               for (TimeToSampleBox.TimeToSampleEntry e : entries) {
                  e.setSampleDuration((int)((double)e.getSampleDuration() * mul * 100.0));
               }

               vt.setTimescale(vt.getTimescale() * 100);
            }

            if (newTimescale != vt.getTimescale()) {
               Logger.info("Changing timescale to: " + vt.getTimescale());
               long newDuration = totalDuration * (long)mov.getTimescale() / (long)vt.getTimescale();
               mov.setDuration(newDuration);
               vt.setDuration(newDuration);
            } else {
               Logger.info("Already at " + newFPS.toString() + "fps, not changing.");
            }
         }

         @Override
         public void applyToFragment(MovieBox mov, MovieFragmentBox[] fragmentBox) {
            throw new RuntimeException("Unsupported");
         }
      });
   }
}
