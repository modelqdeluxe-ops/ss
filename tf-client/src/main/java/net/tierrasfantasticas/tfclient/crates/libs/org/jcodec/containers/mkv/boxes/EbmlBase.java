package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mkv.boxes;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.UsedViaReflection;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.io.SeekableByteChannel;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mkv.MKVType;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mkv.util.EbmlUtil;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.platform.Platform;
import java.io.IOException;
import java.nio.ByteBuffer;

public abstract class EbmlBase {
   protected EbmlMaster parent;
   public MKVType type;
   public byte[] id;
   public int dataLen = 0;
   public long offset;
   public long dataOffset;
   public int typeSizeLength;

   @UsedViaReflection
   public EbmlBase(byte[] id) {
      this.id = id;
   }

   public boolean equalId(byte[] typeId) {
      return Platform.arrayEqualsByte(this.id, typeId);
   }

   public abstract ByteBuffer getData();

   public long size() {
      return (long)(this.dataLen + EbmlUtil.ebmlLength((long)this.dataLen) + this.id.length);
   }

   public long mux(SeekableByteChannel os) throws IOException {
      ByteBuffer bb = this.getData();
      return (long)os.write(bb);
   }
}
