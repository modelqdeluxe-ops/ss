package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common;

import java.io.Closeable;
import java.util.List;

public interface Demuxer extends Closeable {
   List<? extends DemuxerTrack> getTracks();

   List<? extends DemuxerTrack> getVideoTracks();

   List<? extends DemuxerTrack> getAudioTracks();
}
