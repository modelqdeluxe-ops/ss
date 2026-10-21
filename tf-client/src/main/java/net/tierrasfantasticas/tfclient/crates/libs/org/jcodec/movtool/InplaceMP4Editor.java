package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.movtool;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.Preconditions;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.Tuple;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.io.NIOUtils;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.io.SeekableByteChannel;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.BoxFactory;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.BoxUtil;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.MP4Util;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.Box;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.Header;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.MovieBox;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.MovieFragmentBox;
import java.io.File;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class InplaceMP4Editor {
   public boolean modify(File file, MP4Edit edit) throws IOException {
      SeekableByteChannel fi = null;

      boolean var5;
      try {
         fi = NIOUtils.rwChannel(file);
         List<Tuple._2<MP4Util.Atom, ByteBuffer>> fragments = this.doTheFix(fi, edit);
         if (fragments != null) {
            for (Tuple._2<MP4Util.Atom, ByteBuffer> fragment : fragments) {
               this.replaceBox(fi, fragment.v0, fragment.v1);
            }

            return true;
         }

         var5 = false;
      } finally {
         NIOUtils.closeQuietly(fi);
      }

      return var5;
   }

   public boolean copy(File src, File dst, MP4Edit edit) throws IOException {
      SeekableByteChannel fi = null;
      SeekableByteChannel fo = null;

      boolean fragOffsets;
      try {
         fi = NIOUtils.readableChannel(src);
         fo = NIOUtils.writableChannel(dst);
         List<Tuple._2<MP4Util.Atom, ByteBuffer>> fragments = this.doTheFix(fi, edit);
         if (fragments != null) {
            List<Tuple._2<Long, ByteBuffer>> fragOffsetsx = Tuple._2map0(fragments, new Tuple.Mapper<MP4Util.Atom, Long>() {
               public Long map(MP4Util.Atom t) {
                  return t.getOffset();
               }
            });
            Map<Long, ByteBuffer> rewrite = Tuple.asMap(fragOffsetsx);

            for (MP4Util.Atom atom : MP4Util.getRootAtoms(fi)) {
               ByteBuffer byteBuffer = rewrite.get(atom.getOffset());
               if (byteBuffer != null) {
                  fo.write(byteBuffer);
               } else {
                  atom.copy(fi, fo);
               }
            }

            return true;
         }

         fragOffsets = false;
      } finally {
         NIOUtils.closeQuietly(fi);
         NIOUtils.closeQuietly(fo);
      }

      return fragOffsets;
   }

   public boolean replace(File src, MP4Edit edit) throws IOException {
      File tmp = new File(src.getParentFile(), "." + src.getName());
      if (this.copy(src, tmp, edit)) {
         tmp.renameTo(src);
         return true;
      } else {
         return false;
      }
   }

   private List<Tuple._2<MP4Util.Atom, ByteBuffer>> doTheFix(SeekableByteChannel fi, MP4Edit edit) throws IOException {
      MP4Util.Atom moovAtom = this.getMoov(fi);
      Preconditions.checkNotNull(moovAtom);
      ByteBuffer moovBuffer = this.fetchBox(fi, moovAtom);
      MovieBox moovBox = (MovieBox)this.parseBox(moovBuffer);
      List<Tuple._2<MP4Util.Atom, ByteBuffer>> fragments = new LinkedList<>();
      if (BoxUtil.containsBox(moovBox, "mvex")) {
         List<Tuple._2<ByteBuffer, MovieFragmentBox>> temp = new LinkedList<>();

         for (MP4Util.Atom fragAtom : this.getFragments(fi)) {
            ByteBuffer fragBuffer = this.fetchBox(fi, fragAtom);
            fragments.add(Tuple.pair(fragAtom, fragBuffer));
            MovieFragmentBox fragBox = (MovieFragmentBox)this.parseBox(fragBuffer);
            fragBox.setMovie(moovBox);
            temp.add(Tuple.pair(fragBuffer, fragBox));
         }

         edit.applyToFragment(moovBox, Tuple._2_project1(temp).toArray(new MovieFragmentBox[0]));

         for (Tuple._2<ByteBuffer, ? extends Box> frag : temp) {
            if (!this.rewriteBox(frag.v0, frag.v1)) {
               return null;
            }
         }
      } else {
         edit.apply(moovBox);
      }

      if (!this.rewriteBox(moovBuffer, moovBox)) {
         return null;
      } else {
         fragments.add(Tuple.pair(moovAtom, moovBuffer));
         return fragments;
      }
   }

   private void replaceBox(SeekableByteChannel fi, MP4Util.Atom atom, ByteBuffer buffer) throws IOException {
      fi.setPosition(atom.getOffset());
      fi.write(buffer);
   }

   private boolean rewriteBox(ByteBuffer buffer, Box box) {
      try {
         ((Buffer)buffer).clear();
         box.write(buffer);
         if (buffer.hasRemaining()) {
            if (buffer.remaining() < 8) {
               return false;
            }

            buffer.putInt(buffer.remaining());
            buffer.put(new byte[]{102, 114, 101, 101});
         }

         ((Buffer)buffer).flip();
         return true;
      } catch (BufferOverflowException var4) {
         return false;
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

   private List<MP4Util.Atom> getFragments(SeekableByteChannel f) throws IOException {
      List<MP4Util.Atom> result = new LinkedList<>();

      for (MP4Util.Atom atom : MP4Util.getRootAtoms(f)) {
         if ("moof".equals(atom.getHeader().getFourcc())) {
            result.add(atom);
         }
      }

      return result;
   }
}
