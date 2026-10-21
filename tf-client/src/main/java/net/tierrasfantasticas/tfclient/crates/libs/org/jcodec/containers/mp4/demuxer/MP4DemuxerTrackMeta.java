package net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.demuxer;

import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.aac.AACUtils;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.h264.H264Utils;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.h264.io.model.SeqParameterSet;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.h264.mp4.AvcCBox;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.AudioCodecMeta;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.Codec;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.DemuxerTrackMeta;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.Ints;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.TrackType;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.VideoCodecMeta;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.ColorSpace;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.RationalLarge;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.MP4TrackType;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.AudioSampleEntry;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.Box;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.NodeBox;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.PixelAspectExt;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.SyncSamplesBox;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.TrackHeaderBox;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.TrakBox;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.containers.mp4.boxes.VideoSampleEntry;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.platform.Platform;
import java.nio.ByteBuffer;
import java.util.List;

public class MP4DemuxerTrackMeta {
   public static DemuxerTrackMeta fromTrack(AbstractMP4DemuxerTrack track) {
      TrakBox trak = track.getBox();
      SyncSamplesBox stss = NodeBox.findFirstPath(trak, SyncSamplesBox.class, Box.path("mdia.minf.stbl.stss"));
      int[] syncSamples = stss == null ? null : stss.getSyncSamples();
      int[] seekFrames;
      if (syncSamples == null) {
         seekFrames = new int[(int)track.getFrameCount()];
         int i = 0;

         while (i < seekFrames.length) {
            seekFrames[i] = i++;
         }
      } else {
         seekFrames = Platform.copyOfInt(syncSamples, syncSamples.length);

         for (int i = 0; i < seekFrames.length; i++) {
            seekFrames[i]--;
         }
      }

      MP4TrackType type = track.getType();
      TrackType t = type == MP4TrackType.VIDEO ? TrackType.VIDEO : (type == MP4TrackType.SOUND ? TrackType.AUDIO : TrackType.OTHER);
      VideoCodecMeta videoCodecMeta = null;
      AudioCodecMeta audioCodecMeta = null;
      if (type == MP4TrackType.VIDEO) {
         videoCodecMeta = VideoCodecMeta.createSimpleVideoCodecMeta(trak.getCodedSize(), getColorInfo(track));
         PixelAspectExt pasp = NodeBox.findFirst(track.getSampleEntries()[0], PixelAspectExt.class, "pasp");
         if (pasp != null) {
            videoCodecMeta.setPixelAspectRatio(pasp.getRational());
         }
      } else if (type == MP4TrackType.SOUND) {
         AudioSampleEntry ase = (AudioSampleEntry)track.getSampleEntries()[0];
         audioCodecMeta = AudioCodecMeta.fromAudioFormat(ase.getFormat());
      }

      RationalLarge duration = track.getDuration();
      double sec = (double)duration.getNum() / (double)duration.getDen();
      int frameCount = Ints.checkedCast(track.getFrameCount());
      DemuxerTrackMeta meta = new DemuxerTrackMeta(
         t, Codec.codecByFourcc(track.getFourcc()), sec, seekFrames, frameCount, getCodecPrivate(track), videoCodecMeta, audioCodecMeta
      );
      if (type == MP4TrackType.VIDEO) {
         TrackHeaderBox tkhd = NodeBox.findFirstPath(trak, TrackHeaderBox.class, Box.path("tkhd"));
         DemuxerTrackMeta.Orientation orientation;
         if (tkhd.isOrientation90()) {
            orientation = DemuxerTrackMeta.Orientation.D_90;
         } else if (tkhd.isOrientation180()) {
            orientation = DemuxerTrackMeta.Orientation.D_180;
         } else if (tkhd.isOrientation270()) {
            orientation = DemuxerTrackMeta.Orientation.D_270;
         } else {
            orientation = DemuxerTrackMeta.Orientation.D_0;
         }

         meta.setOrientation(orientation);
      }

      return meta;
   }

   protected static ColorSpace getColorInfo(AbstractMP4DemuxerTrack track) {
      Codec codec = Codec.codecByFourcc(track.getFourcc());
      if (codec == Codec.H264) {
         AvcCBox avcC = H264Utils.parseAVCC((VideoSampleEntry)track.getSampleEntries()[0]);
         List<ByteBuffer> spsList = avcC.getSpsList();
         if (spsList.size() > 0) {
            SeqParameterSet sps = SeqParameterSet.read(spsList.get(0).duplicate());
            return sps.getChromaFormatIdc();
         }
      }

      return null;
   }

   public static ByteBuffer getCodecPrivate(AbstractMP4DemuxerTrack track) {
      Codec codec = Codec.codecByFourcc(track.getFourcc());
      if (codec == Codec.H264) {
         AvcCBox avcC = H264Utils.parseAVCC((VideoSampleEntry)track.getSampleEntries()[0]);
         return H264Utils.avcCToAnnexB(avcC);
      } else {
         return codec == Codec.AAC ? AACUtils.getCodecPrivate(track.getSampleEntries()[0]) : null;
      }
   }
}
