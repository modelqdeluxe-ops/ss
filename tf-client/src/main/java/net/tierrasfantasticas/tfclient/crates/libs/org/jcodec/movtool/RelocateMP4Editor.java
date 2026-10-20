package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.movtool;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.io.NIOUtils;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.io.SeekableByteChannel;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.logging.Logger;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.BoxFactory;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.BoxUtil;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.MP4Util;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.Box;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.Header;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.MovieBox;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class RelocateMP4Editor {
   public void modifyOrRelocate(File src, MP4Edit edit) throws IOException {
      boolean modify = new InplaceMP4Editor().modify(src, edit);
      if (!modify) {
         this.relocate(src, edit);
      }
   }

   public void relocate(File src, MP4Edit edit) throws IOException {
      SeekableByteChannel f = null;

      try {
         f = NIOUtils.rwChannel(src);
         MP4Util.Atom moovAtom = this.getMoov(f);
         ByteBuffer moovBuffer = this.fetchBox(f, moovAtom);
         MovieBox moovBox = (MovieBox)this.parseBox(moovBuffer);
         edit.apply(moovBox);
         if (moovAtom.getOffset() + moovAtom.getHeader().getSize() < f.size()) {
            Logger.info("Relocating movie header to the end of the file.");
            f.setPosition(moovAtom.getOffset() + 4L);
            f.write(ByteBuffer.wrap(Header.FOURCC_FREE));
            f.setPosition(f.size());
         } else {
            f.setPosition(moovAtom.getOffset());
         }

         MP4Util.writeMovie(f, moovBox);
      } finally {
         NIOUtils.closeQuietly(f);
      }
   }

   private ByteBuffer fetchBox(SeekableByteChannel fi, MP4Util.Atom moov) throws IOException {
      fi.setPosition(moov.getOffset());
      return NIOUtils.fetchFromChannel(fi, (int)moov.getHeader().getSize());
   }

   private Box parseBox(ByteBuffer oldMov) {
      Header header = Header.read(oldMov);
      return BoxUtil.parseBox(oldMov, header, BoxFactory.getDefault());
   }

   private MP4Util.Atom getMoov(SeekableByteChannel f) throws IOException {
      for (MP4Util.Atom atom : MP4Util.getRootAtoms(f)) {
         if ("moov".equals(atom.getHeader().getFourcc())) {
            return atom;
         }
      }

      return null;
   }
}
