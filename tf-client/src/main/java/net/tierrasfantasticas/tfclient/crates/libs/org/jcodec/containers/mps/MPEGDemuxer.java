package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mps;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.Demuxer;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.DemuxerTrack;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.DemuxerTrackMeta;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.Packet;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

public interface MPEGDemuxer extends Demuxer {
   @Override
   List<? extends MPEGDemuxer.MPEGDemuxerTrack> getTracks();

   @Override
   List<? extends MPEGDemuxer.MPEGDemuxerTrack> getVideoTracks();

   @Override
   List<? extends MPEGDemuxer.MPEGDemuxerTrack> getAudioTracks();

   public interface MPEGDemuxerTrack extends DemuxerTrack {
      Packet nextFrameWithBuffer(ByteBuffer var1) throws IOException;

      @Override
      DemuxerTrackMeta getMeta();

      int getSid();

      List<PESPacket> getPending();

      void ignore();
   }
}
